package org.opensha2.calc;

import static com.google.common.base.Preconditions.checkState;

import static org.opensha2.data.Data.checkInRange;

import org.opensha2.calc.Deaggregation.SourceContribution;
import org.opensha2.data.Data;
import org.opensha2.data.DataTable;
import org.opensha2.data.DataTables;
import org.opensha2.data.DataVolume;
import org.opensha2.eq.Magnitudes;
import org.opensha2.eq.model.Source;
import org.opensha2.eq.model.SourceSet;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Range;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Deaggregation dataset that stores deaggregation results of individual
 * SourceSets and Gmms. Datasets may be recombined via add().
 *
 * Binned deaggregation data and summary statistics are commonly weighted by the
 * rate of the contributing sources so the term 'weight' in a dataset is
 * synonymous with rate or a rate sum.
 *
 * @author Peter Powers
 */
final class DeaggDataset {

  final DataVolume rmε;

  /* Weighted mean contributions */
  final double rBar, mBar, εBar;

  /* Total rate for a dataset and summed weight for *Bar fields */
  final double barWeight;

  /* r and m position data already weighted by rate */
  final DataTable rPositions;
  final DataTable mPositions;

  /* Total weight (rate) in each r and m position bin */
  final DataTable positionWeights;

  /* Unbinned weight (rate) */
  final double residualWeight;

  /* Contributors */
  final Map<SourceSet<? extends Source>, Double> sourceSets;
  final List<SourceContribution> sources;

  private DeaggDataset(
      DataVolume rmε,
      double rBar, double mBar, double εBar,
      double barWeight,
      DataTable rPositions,
      DataTable mPositions,
      DataTable positionWeights,
      double residualWeight,
      Map<SourceSet<? extends Source>, Double> sourceSets,
      List<SourceContribution> sources) {

    this.rmε = rmε;

    this.rBar = rBar;
    this.mBar = mBar;
    this.εBar = εBar;
    this.barWeight = barWeight;

    this.rPositions = rPositions;
    this.mPositions = mPositions;
    this.positionWeights = positionWeights;
    this.residualWeight = residualWeight;

    this.sources = sources;
    this.sourceSets = sourceSets;

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
      return DataTables.indexOf(rmε.rowMin(), rmε.rowΔ(), r, rmε.rows().size());
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
      return DataTables.indexOf(rmε.columnMin(), rmε.columnΔ(), m, rmε.columns().size());
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
        : DataTables.indexOf(rmε.levelMin(), rmε.levelΔ(), ε, rmε.levels().size());
  }
  
  @Override
  public String toString() {
    return rmε.toString();
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

  static class Builder {

    private DataVolume.Builder rmε;

    /* Weighted mean contributions */
    private double rBar, mBar, εBar;
    private double barWeight;

    /* Weighted r and m position data */
    private DataTable.Builder rPositions;
    private DataTable.Builder mPositions;
    private DataTable.Builder positionWeights;

    /* Unbinned weight (rate) */
    private double residualWeight;

    private Map<SourceSet<? extends Source>, Double> sourceSets;
    private List<SourceContribution> sources;

    private Builder(
        double rMin, double rMax, double Δr,
        double mMin, double mMax, double Δm,
        double εMin, double εMax, double Δε) {

      rmε = DataVolume.Builder.create()
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

      rPositions = DataTable.Builder.create()
          .rows(rMin, rMax, Δr)
          .columns(mMin, mMax, Δm);
      mPositions = DataTable.Builder.create()
          .rows(rMin, rMax, Δr)
          .columns(mMin, mMax, Δm);
      positionWeights = DataTable.Builder.create()
          .rows(rMin, rMax, Δr)
          .columns(mMin, mMax, Δm);

      sourceSets = new HashMap<>();
      sources = new ArrayList<>();
    }

    private Builder(DeaggDataset model) {
      rmε = DataVolume.Builder.fromModel(model.rmε);
      rPositions = DataTable.Builder.fromModel(model.rPositions);
      mPositions = DataTable.Builder.fromModel(model.mPositions);
      positionWeights = DataTable.Builder.fromModel(model.positionWeights);
      sourceSets = new HashMap<>();
      sources = new ArrayList<>();
    }

    /*
     * Populate dataset with rupture data. Supply DataTable and DataVolume
     * indices, distance, magnitude, and epsilon (weighted by rate), and the
     * rate of the rupture.
     *
     * Although we could work with the raw distance, magnitude and epsilon
     * values, deaggregation is being performed across each Gmm, so precomputing
     * indices and weighted values in the calling method brings some efficiency.
     */
    Builder add(
        int ri, int mi, int εi,
        double rw, double mw, double εw,
        double rate) {

      rmε.add(ri, mi, εi, rate);

      rBar += rw;
      mBar += mw;
      εBar += εw;
      barWeight += rate;

      rPositions.add(ri, mi, rw);
      mPositions.add(ri, mi, mw);
      positionWeights.add(ri, mi, rate);

      return this;
    }

    /*
     * Add residual rate for events falling outside distance and magnitude
     * ranges supported by this deaggregation.
     */
    Builder addResidual(double rate) {
      residualWeight += rate;
      return this;
    }

    Builder sourceSet(SourceSet<? extends Source> sourceSet) {
      checkState(sourceSets.isEmpty(), "SourceSet for dataset has already been set");
      sourceSets.put(sourceSet, 0.0);
      return this;
    }

    /* Add a contributing source to a dataset. */
    Builder add(SourceContribution source) {
      sources.add(source);
      return this;
    }

    /*
     * Scale all values. This will usually be called just before build(). This
     * is a relatively heavyweight operation in that it will cause the source
     * contribution list to be rebuilt.
     */
    Builder multiply(double scale) {

      rmε.multiply(scale);

      rBar *= scale;
      mBar *= scale;
      εBar *= scale;
      barWeight *= scale;

      rPositions.multiply(scale);
      mPositions.multiply(scale);
      positionWeights.multiply(scale);
      residualWeight *= scale;

      List<SourceContribution> oldSources = sources;
      sources = new ArrayList<>();
      for (SourceContribution source : oldSources) {
        sources.add(new SourceContribution(
            source.name,
            source.rate * scale,
            source.residualRate * scale));
      }

      for (Entry<SourceSet<? extends Source>, Double> entry : sourceSets.entrySet()) {
        entry.setValue(entry.getValue() * scale);
      }

      return this;
    }

    /* Combine values */
    Builder add(DeaggDataset other) {

      rmε.add(other.rmε);

      rBar += other.rBar;
      mBar += other.mBar;
      εBar += other.εBar;
      barWeight += other.barWeight;

      rPositions.add(other.rPositions);
      mPositions.add(other.mPositions);
      positionWeights.add(other.positionWeights);
      residualWeight += other.residualWeight;

      sources.addAll(other.sources);
      Data.add(sourceSets, other.sourceSets);

      return this;
    }

    DeaggDataset build() {
      if (sourceSets.size() == 1) {
        Entry<SourceSet<? extends Source>, Double> entry =
            Iterables.getOnlyElement(sourceSets.entrySet());
        sourceSets.put(entry.getKey(), barWeight + residualWeight);
      }

      return new DeaggDataset(
          rmε.build(),
          rBar, mBar, εBar,
          barWeight,
          rPositions.build(),
          mPositions.build(),
          positionWeights.build(),
          residualWeight,
          ImmutableMap.copyOf(sourceSets),
          ImmutableList.copyOf(sources));
    }

    /*
     * Utility method to return the current total rate of ruptures added to this
     * builder, i.e. barWeight + residualWeight.
     */
    double rate() {
      return barWeight + residualWeight;
    }
  }

}
