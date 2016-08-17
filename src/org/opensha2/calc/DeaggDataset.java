package org.opensha2.calc;

import static com.google.common.base.Preconditions.checkState;

import static org.opensha2.data.Data.checkInRange;

import org.opensha2.calc.Deaggregation.SourceContribution;
import org.opensha2.data.Data;
import org.opensha2.data.IntervalData;
import org.opensha2.data.IntervalTable;
import org.opensha2.data.IntervalVolume;
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
 * Deaggregation dataset that holds deaggregation results of individual
 * {@code SourceSet}s and {@code Gmm}s. Datasets may be recombined via add().
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
  final Map<SourceSet<? extends Source>, Double> sourceSets;
  final List<SourceContribution> sources;

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
      Map<SourceSet<? extends Source>, Double> sourceSets,
      List<SourceContribution> sources) {

    this.rmε = rmε;
    this.rScaled = rScaled;
    this.mScaled = mScaled;
    this.εScaled = εScaled;
    this.binned = binned;
    this.residual = residual;
    this.sources = sources;
    this.sourceSets = sourceSets;

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

  static class Builder {

    /* Rate bin builder. */
    private IntervalVolume.Builder rmε;

    /* Bin builders for r, m, and ε values scaled by rate. */
    private IntervalVolume.Builder rScaled;
    private IntervalVolume.Builder mScaled;
    private IntervalVolume.Builder εScaled;

    /* Binned and unbinned rate. */
    private double binned;
    private double residual;

    /* Contributing source sets and sources. */
    private Map<SourceSet<? extends Source>, Double> sourceSets;
    private List<SourceContribution> sources;

    private Builder(
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

      sourceSets = new HashMap<>();
      sources = new ArrayList<>();
    }

    private Builder(DeaggDataset model) {
      rmε = IntervalVolume.Builder.fromModel(model.rmε);
      rScaled = IntervalVolume.Builder.fromModel(model.rScaled);
      mScaled = IntervalVolume.Builder.fromModel(model.mScaled);
      εScaled = IntervalVolume.Builder.fromModel(model.εScaled);
      sourceSets = new HashMap<>();
      sources = new ArrayList<>();
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
    Builder add(
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
    Builder addResidual(double rate) {
      residual += rate;
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
      rScaled.multiply(scale);
      mScaled.multiply(scale);
      εScaled.multiply(scale);
      binned *= scale;
      residual *= scale;

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

    /* Add values from other datasets. */
    Builder add(DeaggDataset other) {
      rmε.add(other.rmε);
      rScaled.add(other.rScaled);
      mScaled.add(other.mScaled);
      εScaled.add(other.εScaled);
      binned += other.binned;
      residual += other.residual;
      sources.addAll(other.sources);
      Data.add(sourceSets, other.sourceSets);
      return this;
    }

    DeaggDataset build() {
      if (sourceSets.size() == 1) {
        Entry<SourceSet<? extends Source>, Double> entry =
            Iterables.getOnlyElement(sourceSets.entrySet());
        sourceSets.put(entry.getKey(), binned + residual);
      }

      return new DeaggDataset(
          rmε.build(),
          rScaled.build(),
          mScaled.build(),
          εScaled.build(),
          binned,
          residual,
          ImmutableMap.copyOf(sourceSets),
          ImmutableList.copyOf(sources));
    }

    /*
     * Utility method to return the current total rate of ruptures added to this
     * builder thus far.
     */
    double rate() {
      return binned + residual;
    }
  }

}
