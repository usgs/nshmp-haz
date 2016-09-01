package org.opensha2.calc;

import static com.google.common.base.Preconditions.checkState;

import static org.opensha2.data.Data.checkInRange;

import org.opensha2.calc.DeaggContributor.ClusterContributor;
import org.opensha2.calc.DeaggContributor.SectionSource;
import org.opensha2.calc.DeaggContributor.SourceContributor;
import org.opensha2.calc.DeaggContributor.SourceSetContributor;
import org.opensha2.calc.DeaggContributor.SystemContributor;
import org.opensha2.data.IntervalData;
import org.opensha2.data.IntervalTable;
import org.opensha2.data.IntervalVolume;
import org.opensha2.eq.Magnitudes;
import org.opensha2.eq.model.ClusterSource;
import org.opensha2.eq.model.Source;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Range;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A deaggregation dataset class that provides a variety of builder flavors to
 * create initial datasets for specific {@code SourceSet}s and {@code Gmm}s and
 * then recombine them in different ways. Some of the more complex operations in
 * this class involve the handling of ClusterSources and the recombining of
 * DeaggContributors.
 *
 * Binned deaggregation data and summary statistics are commonly weighted by the
 * rate of the contributing sources so the term 'weight' in a dataset is
 * synonymous with rate or a rate sum.
 *
 * @author Peter Powers
 */
final class DeaggDataset {

  /* Rate bins. */
  final IntervalVolume rmε;

  /* Bins of r, m, and ε values scaled by rate. */
  final IntervalVolume rScaled;
  final IntervalVolume mScaled;
  final IntervalVolume εScaled;

  /* Binned and unbinned rates. */
  final double binned;
  final double residual;

  /* Contributing source sets and sources. */
  final List<? extends DeaggContributor> contributors;

  /*
   * Fields derived at construction time.
   */

  /* Value bins collapsed to r-m tables. */
  final IntervalTable rmWeights;
  final IntervalTable rmrScaled;
  final IntervalTable rmmScaled;
  final IntervalTable rmεScaled;

  /* Mean values over all bins weighted by rate. */
  final double rBar, mBar, εBar;

  private DeaggDataset(
      IntervalVolume rmε,
      IntervalVolume rScaled,
      IntervalVolume mScaled,
      IntervalVolume εScaled,
      double binned,
      double residual,
      List<? extends DeaggContributor> contributors) {

    this.rmε = rmε;
    this.rScaled = rScaled;
    this.mScaled = mScaled;
    this.εScaled = εScaled;
    this.binned = binned;
    this.residual = residual;
    this.contributors = contributors;

    this.rmWeights = rmε.collapse();
    this.rmrScaled = this.rScaled.collapse();
    this.rmmScaled = this.mScaled.collapse();
    this.rmεScaled = this.εScaled.collapse();

    this.rBar = this.rmrScaled.collapse().sum() / binned;
    this.mBar = this.rmmScaled.collapse().sum() / binned;
    this.εBar = this.rmεScaled.collapse().sum() / binned;
  }

  /*
   * Index methods delegate to the same method used to initialize internal data
   * tables and volumes.
   */

  /**
   * Return the internal bin index of the supplied distance, {@code r}, or
   * {@code -1} if {@code r} is outside the range specified for the
   * deaggregation underway.
   *
   * @param r distance for which to compute index
   */
  int distanceIndex(double r) {
    try {
      return IntervalData.indexOf(rmε.rowMin(), rmε.rowΔ(), r, rmε.rows().size());
    } catch (IndexOutOfBoundsException e) {
      return -1;
    }
  }

  /**
   * Return the internal bin index of the supplied magnitude, {@code m}, or
   * {@code -1} if {@code m} is outside the range specified for the
   * deaggregation underway.
   *
   * @param m magnitude for which to compute index
   */
  int magnitudeIndex(double m) {
    try {
      return IntervalData.indexOf(rmε.columnMin(), rmε.columnΔ(), m, rmε.columns().size());
    } catch (IndexOutOfBoundsException e) {
      return -1;
    }
  }

  /**
   * Return the internal bin index of the supplied epsilon, {@code ε}. Epsilon
   * indexing behaves differently than distance and magnitude indexing. Whereas
   * distance and magnitudes, if out of range of a deaggregation, return -1, the
   * lowermost and uppermost epsilon bins are open ended and are used to collect
   * all values less than or greater than the upper and lower edges of those
   * bins, respectively.
   *
   * @param ε epsilon for which to compute index
   */
  int epsilonIndex(double ε) {
    return (ε < rmε.levelMin()) ? 0 : (ε >= rmε.levelMax()) ? rmε.levels().size() - 1
        : IntervalData.indexOf(rmε.levelMin(), rmε.levelΔ(), ε, rmε.levels().size());
  }

  /**
   * Initialize a deaggregation dataset builder using an existing dataset whose
   * immutable structural properties will be shared (e.g. row and column arrays
   * of data tables).
   *
   * @param model to mirror
   */
  static Builder builder(DeaggDataset model) {
    return new Builder(model);
  }

  /**
   * Initialize a deaggregation dataset builder from the settings in a
   * calculation configuration.
   *
   * @param config to process
   * @see CalcConfig
   */
  static Builder builder(CalcConfig config) {
    CalcConfig.Deagg d = config.deagg;
    return builder(
        d.rMin, d.rMax, d.Δr,
        d.mMin, d.mMax, d.Δm,
        d.εMin, d.εMax, d.Δε);
  }

  /**
   * Initialize a deaggregation dataset builder.
   *
   * @param rMin lower edge of lowermost distance bin
   * @param rMax upper edge of uppermost distance bin
   * @param Δr distance bin discretization
   * @param mMin lower edge of lowermost magnitude bin
   * @param mMax upper edge of uppermost magnitude bin
   * @param Δm magnitude bin discretization
   * @param εMin lower edge of lowermost epsilon bin
   * @param εMax upper edge of uppermost epsilon bin
   * @param Δε epsilon bin discretization
   */
  static Builder builder(
      double rMin, double rMax, double Δr,
      double mMin, double mMax, double Δm,
      double εMin, double εMax, double Δε) {

    /*
     * Dataset fields (data tables and volumes) validate deltas relative to min
     * and max supplied; we only check ranges here.
     */
    return new Builder(
        rMin, rMax, Δr,
        mMin, mMax, Δm,
        εMin, εMax, Δε);
  }

  private static final Range<Double> rRange = Range.closed(0.0, 1000.0);
  private static final Range<Double> εRange = Range.closed(-3.0, 3.0);

  private static class AbstractBuilder {

    /* Rate bin builder. */
    IntervalVolume.Builder rmε;

    /* Bin builders for r, m, and ε values scaled by rate. */
    IntervalVolume.Builder rScaled;
    IntervalVolume.Builder mScaled;
    IntervalVolume.Builder εScaled;

    /* Binned and unbinned rate. */
    double binned;
    double residual;

    private AbstractBuilder(
        double rMin, double rMax, double Δr,
        double mMin, double mMax, double Δm,
        double εMin, double εMax, double Δε) {

      rmε = IntervalVolume.Builder.create()
          .rows(
              checkInRange(rRange, "Min distance", rMin),
              checkInRange(rRange, "Max distance", rMax),
              Δr)
          .columns(
              Magnitudes.checkMagnitude(mMin),
              Magnitudes.checkMagnitude(mMax),
              Δm)
          .levels(
              checkInRange(εRange, "Min epsilon", εMin),
              checkInRange(εRange, "Max epsilon", εMax),
              Δε);

      rScaled = IntervalVolume.Builder.create()
          .rows(rMin, rMax, Δr)
          .columns(mMin, mMax, Δm)
          .levels(εMin, εMax, Δε);
      mScaled = IntervalVolume.Builder.create()
          .rows(rMin, rMax, Δr)
          .columns(mMin, mMax, Δm)
          .levels(εMin, εMax, Δε);
      εScaled = IntervalVolume.Builder.create()
          .rows(rMin, rMax, Δr)
          .columns(mMin, mMax, Δm)
          .levels(εMin, εMax, Δε);
    }

    private AbstractBuilder(DeaggDataset model) {
      rmε = IntervalVolume.Builder.fromModel(model.rmε);
      rScaled = IntervalVolume.Builder.fromModel(model.rScaled);
      mScaled = IntervalVolume.Builder.fromModel(model.mScaled);
      εScaled = IntervalVolume.Builder.fromModel(model.εScaled);
    }

    /*
     * Return the current total rate of ruptures added to this builder thus far.
     */
    double rate() {
      return binned + residual;
    }
  }

  /*
   * Base DeaggDataset builder for which parent contributor can be of any type.
   */
  static class Builder extends AbstractBuilder {

    /* Primary contributor for this dataset. */
    DeaggContributor.Builder parent;

    private Builder(
        double rMin, double rMax, double Δr,
        double mMin, double mMax, double Δm,
        double εMin, double εMax, double Δε) {
      super(rMin, rMax, Δr, mMin, mMax, Δm, εMin, εMax, Δε);
    }

    private Builder(DeaggDataset model) {
      super(model);
    }

    /*
     * Populate dataset with rupture data. Supply data indices of distance,
     * magnitude, and epsilon values, scaled by rate, and the rate of the
     * rupture.
     *
     * Although we could work with the raw distance, magnitude and epsilon
     * values, deaggregation is being performed across each Gmm, so precomputing
     * indices and scaled values in the calling method brings some efficiency.
     */
    Builder addRate(
        int ri, int mi, int εi,
        double rw, double mw, double εw,
        double rate) {

      rmε.add(ri, mi, εi, rate);
      rScaled.add(ri, mi, εi, rw);
      mScaled.add(ri, mi, εi, mw);
      εScaled.add(ri, mi, εi, εw);
      binned += rate;
      return this;
    }

    /*
     * Add residual rate for events falling outside distance and magnitude
     * ranges supported by this deaggregation.
     */
    Builder addResidual(double residual) {
      this.residual += residual;
      return this;
    }

    Builder setParentContributor(DeaggContributor.Builder parent) {
      this.parent = parent;
      return this;
    }

    /*
     * Add a contributing source to a dataset. A contributor will likely be
     * added after multiple calls to add(data...) for the ruptures it represents
     * wioth the total rate for the source having been tracked externally.
     */
    Builder addChildContributor(DeaggContributor.Builder contributor) {
      checkState(parent != null, "Parent contributor has not been set");
      parent.addChild(contributor);
      return this;
    }

    /*
     * Scale all values. This will usually be called just before build(). At
     * this point, the total parent rate and residual will not have been set,
     * but the call to parent.multiply() will cascade down to child
     * contributors. Parent rate is set on build().
     */
    Builder multiply(double scale) {
      rmε.multiply(scale);
      rScaled.multiply(scale);
      mScaled.multiply(scale);
      εScaled.multiply(scale);
      binned *= scale;
      residual *= scale;
      parent.multiply(scale);
      return this;
    }

    DeaggDataset build() {

      /*
       * A dataset may not have any contributors if it was created from a
       * DeaggConfig for use as a base DeaggDataset model. See the empty
       * ImmutableList below. A Gmm dataset may also not have any contributors
       * when the total contribution for a source set is low and one or more
       * Gmm's do not make up any part of the contribution. (e.g. 2008 NSHM,
       * site=Seattle, Imt=PGA, rp=2475: AB_03_GLOB_INTER contribution = 0.0)
       */
      ImmutableList<DeaggContributor> contributorList = (parent == null)
          ? ImmutableList.<DeaggContributor> of() : ImmutableList.of(parent.build());

      return new DeaggDataset(
          rmε.build(),
          rScaled.build(),
          mScaled.build(),
          εScaled.build(),
          binned,
          residual,
          contributorList);
    }
  }

  private abstract static class AbstractCombiner extends AbstractBuilder {

    private AbstractCombiner(DeaggDataset model) {
      super(model);
    }

    /* Add values from other datasets. */
    AbstractCombiner add(DeaggDataset other) {
      rmε.add(other.rmε);
      rScaled.add(other.rScaled);
      mScaled.add(other.mScaled);
      εScaled.add(other.εScaled);
      binned += other.binned;
      residual += other.residual;
      return this;
    }

    protected DeaggDataset build(List<? extends DeaggContributor> contributors) {
      return new DeaggDataset(
          rmε.build(),
          rScaled.build(),
          mScaled.build(),
          εScaled.build(),
          binned,
          residual,
          contributors);
    }
  }

  static final SourceSetConsolidator SOURCE_SET_CONSOLIDATOR = new SourceSetConsolidator();

  static final class SourceSetConsolidator
      implements Function<Collection<DeaggDataset>, DeaggDataset> {
    @Override
    public DeaggDataset apply(Collection<DeaggDataset> datasets) {
      SourceSetCombiner combiner = new SourceSetCombiner(datasets.iterator().next());
      for (DeaggDataset dataset : datasets) {
        combiner.add(dataset);
      }
      return combiner.build();
    }
  }

  /*
   * Specialized builder that combines datasets across multiple SourceSets.
   */
  static class SourceSetCombiner extends AbstractCombiner {

    ArrayList<DeaggContributor> contributors;

    SourceSetCombiner(DeaggDataset model) {
      super(model);
      contributors = new ArrayList<>();
    }

    @Override
    SourceSetCombiner add(DeaggDataset other) {
      super.add(other);
      contributors.addAll(other.contributors);
      return this;
    }

    DeaggDataset build() {
      return super.build(DeaggContributor.SORTER.immutableSortedCopy(contributors));
    }
  }

  static final SourceConsolidator SOURCE_CONSOLIDATOR = new SourceConsolidator();

  static final class SourceConsolidator
      implements Function<Collection<DeaggDataset>, DeaggDataset> {
    @Override
    public DeaggDataset apply(Collection<DeaggDataset> datasets) {
      SourceCombiner combiner = new SourceCombiner(datasets.iterator().next());
      for (DeaggDataset dataset : datasets) {
        combiner.add(dataset);
      }
      return combiner.build();
    }
  }

  /*
   * Specialized builder that combines datasets across Gmms for a single
   * SourceSet. The model supplied must be one of the datasets to be combined,
   * and all additions must reference the same singleton SourceSet of the model.
   */
  static class SourceCombiner extends AbstractCombiner {

    SourceSetContributor.Builder contributor;
    Map<Source, DeaggContributor.Builder> childMap;

    SourceCombiner(DeaggDataset model) {
      super(model);
      SourceSetContributor ssc = (SourceSetContributor) Iterables
          .getOnlyElement(model.contributors);
      contributor = new SourceSetContributor.Builder().sourceSet(ssc.sourceSet);
      childMap = new HashMap<>();
    }

    @Override
    SourceCombiner add(DeaggDataset other) {
      super.add(other);
      SourceSetContributor sourceSetContributor = (SourceSetContributor) Iterables
          .getOnlyElement(other.contributors);
      for (DeaggContributor deaggContributor : sourceSetContributor.children) {
        switch (sourceSetContributor.sourceSet.type()) {
          case CLUSTER:
            putOrAddCluster((ClusterContributor) deaggContributor);
            break;
          case SYSTEM:
            putOrAddSystem((SystemContributor) deaggContributor);
            break;
          default:
            putOrAddSource((SourceContributor) deaggContributor);
        }
      }
      return this;
    }

    private void putOrAddSource(SourceContributor sc) {
      Source source = sc.source;

      /* Add to existing. */
      if (childMap.containsKey(source)) {
        SourceContributor.Builder child = (SourceContributor.Builder) childMap.get(source);
        child.add(sc.rate, sc.residual, sc.rScaled, sc.mScaled, sc.εScaled);
        return;
      }
      /* Put new. */
      DeaggContributor.Builder sourceContributor = new SourceContributor.Builder()
          .source(source)
          .add(sc.rate, sc.residual, sc.rScaled, sc.mScaled, sc.εScaled);
      childMap.put(source, sourceContributor);
    }

    private void putOrAddCluster(ClusterContributor cc) {
      ClusterSource cluster = cc.cluster;

      /* Add to existing. */
      if (childMap.containsKey(cluster)) {

        /* Processs faults. */
        Map<Source, SourceContributor.Builder> sourceMap = createFaultSourceMap(
            (ClusterContributor.Builder) childMap.get(cluster));
        for (DeaggContributor child : cc.faults) {
          SourceContributor sc = (SourceContributor) child;
          sourceMap.get(sc.source).add(sc.rate, sc.residual, sc.rScaled, sc.mScaled, sc.εScaled);
        }
        return;
      }

      /* Put new. */
      DeaggContributor.Builder cb = new ClusterContributor.Builder().cluster(cluster);

      /* Add faults. */
      for (DeaggContributor child : cc.faults) {
        SourceContributor sc = (SourceContributor) child;
        DeaggContributor.Builder sourceContributor = new SourceContributor.Builder()
            .source(sc.source)
            .add(sc.rate, sc.residual, sc.rScaled, sc.mScaled, sc.εScaled);
        cb.addChild(sourceContributor);
      }
      childMap.put(cluster, cb);
    }

    /*
     * This is a relatively heavyweight operation that is called with each
     * addition of a ClusterContributor, however it does not need to be called
     * that often. It is the only way to merge the contributions from child
     * FaultSources in a ClusterContributor that are added in unknown order.
     */
    private static Map<Source, SourceContributor.Builder> createFaultSourceMap(
        ClusterContributor.Builder clusterBuilder) {
      HashMap<Source, SourceContributor.Builder> sourceMap = new HashMap<>();
      for (DeaggContributor.Builder child : clusterBuilder.faults) {
        SourceContributor.Builder sourceBuilder = (SourceContributor.Builder) child;
        sourceMap.put(sourceBuilder.source, sourceBuilder);
      }
      return sourceMap;
    }

    private void putOrAddSystem(SystemContributor sc) {

      SectionSource section = sc.section;

      /* Add to existing. */
      if (childMap.containsKey(section)) {
        SystemContributor.Builder child = (SystemContributor.Builder) childMap.get(section);
        child.add(sc.rate, sc.residual, sc.rScaled, sc.mScaled, sc.εScaled);
        return;
      }

      /* Put new. */
      DeaggContributor.Builder sourceContributor = new SystemContributor.Builder()
          .section(section)
          .add(sc.rate, sc.residual, sc.rScaled, sc.mScaled, sc.εScaled);
      childMap.put(section, sourceContributor);
    }

    DeaggDataset build() {
      for (DeaggContributor.Builder child : childMap.values()) {
        contributor.addChild(child);
      }
      return super.build(ImmutableList.of(contributor.build()));
    }
  }

}
