package org.opensha2.calc;

import static com.google.common.primitives.Doubles.toArray;

import static org.opensha2.data.Data.multiply;
import static org.opensha2.internal.TextUtils.NEWLINE;

import org.opensha2.data.Data;
import org.opensha2.data.IntervalVolume;
import org.opensha2.data.Interpolator;
import org.opensha2.data.XySequence;
import org.opensha2.eq.model.ClusterSourceSet;
import org.opensha2.eq.model.Source;
import org.opensha2.eq.model.SourceSet;
import org.opensha2.gmm.Gmm;
import org.opensha2.gmm.Imt;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Ordering;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Hazard deaggregation. Given a {@link Hazard} result, this class will
 * deaggregate the results at all spectral periods supplied in the result at an
 * intensity measure level or return period of interest.
 *
 * @author Peter Powers
 */
public final class Deaggregation {

  /*
   * Developer notes and TODO
   * -------------------------------------------------------------------------
   * Consider auto-scaling of results (dataset bounds).
   * -------------------------------------------------------------------------
   * Consider warnings if config does not span source set range.
   * -------------------------------------------------------------------------
   * Deaggregate on probability of occurrence instead of exceedance.
   * -------------------------------------------------------------------------
   * Revisit precision issues associated with integer based return period;
   * 2%in50 years os really 0.00040405414, not 1/2475 = 0.0004040404
   *
   *
   * -------------------------------------------------------------------------
   * One of the difficulties with deaggregation is deciding how to specify
   * magnitude and distance ranges, and respective discretizations, over which
   * to deaggregate, given the broad ranges of distances and magnitudes
   * supported by various models (e.g. 300km in the WUS NSHMP vs 1000km in the
   * CEUS). Invariably, situations will arise where some sources are outside a
   * user-specified range and the sum of contributions in a deaggregation data
   * matrix will not equal the target rate specified at the outset of a
   * calculation. One could query the model(s) being used (set broad limits) or
   * create lists of sources and their contributions in advance before building
   * deaggregation datasets (set calculation specific limits). The former may
   * make sense as a default setting in the absence of any user specified
   * settings, the latter complicates the code considerably.
   *
   * For the time being we require user-specified ranges and encourage high
   * resolution deaggregation data bins that can be preserved if the
   * contributing sources span only a small part of a deaggregation result
   * matrix. If the deaggregation result matrix is heavily populated, bins could
   * be consolidated prior to output.
   *
   * For data outside the defined ranges, we track the 'un-binned' or residual
   * rate. This is needed to compute mean r, m, and ε.
   *
   * Note that in a webservice environment, only relevant data will be returned
   * (zero-contribution bins are omitted) and the client will render plots based
   * on the data supplied, not based on the ranges specified for the calculation
   * itelf.
   * -------------------------------------------------------------------------
   * Issues related to deaggreagtion targets.
   *
   * Because hazard is computed at specific intensity measure levels, only when
   * a deaggregation is computed at one of those levels will the contributions
   * of the relevant sources equal the target rate specified by the total mean
   * hazard curve. Because of the convexity of the hazard curve in log space,
   * the 'true' total as derived from the relevant sources will be slightly
   * higher.
   */

  private final Map<Imt, ImtDeagg> deaggs;

  private Deaggregation(Map<Imt, ImtDeagg> deaggs) {
    this.deaggs = deaggs;
  }

  /**
   * Deaggregate {@code hazard} at the intensity measure level corresponding to
   * the supplied {@code returnPeriod}.
   *
   * @param hazard to deaggregate.
   * @param returnPeriod at which to deaggregate {@code hazard}
   */
  public static Deaggregation atReturnPeriod(Hazard hazard, double returnPeriod) {
    Map<Imt, ImtDeagg> imtDeaggMap = Maps.newEnumMap(Imt.class);
    DeaggConfig.Builder cb = DeaggConfig.builder(hazard);
    double rate = 1.0 / returnPeriod;

    for (Entry<Imt, XySequence> entry : hazard.totalCurves.entrySet()) {
      Imt imt = entry.getKey();
      double iml = IML_INTERPOLATER.findX(entry.getValue(), rate);
      DeaggConfig config = cb.imt(imt).iml(iml, rate, returnPeriod).build();
      System.out.println(config);
      ImtDeagg imtDeagg = new ImtDeagg(hazard, config);
      imtDeaggMap.put(imt, imtDeagg);
    }

    return new Deaggregation(Maps.immutableEnumMap(imtDeaggMap));
  }

  /**
   * Deaggregate {@code hazard} at the supplied intensity measure level.
   *
   * @param hazard to deaggregate.
   * @param iml intensity measure level at which to deaggregate {@code hazard}
   */
  public static Deaggregation atIml(Hazard hazard, double iml) {

    Map<Imt, ImtDeagg> imtDeaggMap = Maps.newEnumMap(Imt.class);
    DeaggConfig.Builder cb = DeaggConfig.builder(hazard);

    for (Entry<Imt, XySequence> entry : hazard.totalCurves.entrySet()) {
      Imt imt = entry.getKey();
      double rate = RATE_INTERPOLATER.findY(entry.getValue(), iml);
      double returnPeriod = 1.0 / rate;
      DeaggConfig config = cb.imt(imt).iml(iml, rate, returnPeriod).build();
      ImtDeagg imtDeagg = new ImtDeagg(hazard, config);
      imtDeaggMap.put(imt, imtDeagg);
    }

    return new Deaggregation(Maps.immutableEnumMap(imtDeaggMap));
  }

  /* Hazard curves are already in log-x space. */
  static final Interpolator IML_INTERPOLATER = Interpolator.builder()
      .logy()
      .decreasingX()
      .build();

  /* Hazard curves are already in log-x space. */
  static final Interpolator RATE_INTERPOLATER = Interpolator.builder()
      .logy()
      .build();

  static final DatasetConsolidator DEFAULT_DATASET_CONSOLIDATOR =
      new DatasetConsolidator(Optional.<ClusterSourceSet> absent());

  /*
   * Dataset merger that uses first dataset as a model. Because cluster sources
   * consolidate data from individual sources within a ClusterSourceSet an
   * optional ClusterSourceSet is provided.
   */
  static final class DatasetConsolidator
      implements Function<Collection<DeaggDataset>, DeaggDataset> {

    final Optional<ClusterSourceSet> clusterSources;

    DatasetConsolidator(Optional<ClusterSourceSet> clusterSources) {
      this.clusterSources = clusterSources;
    }

    @Override
    public DeaggDataset apply(Collection<DeaggDataset> datasets) {
      DeaggDataset.Builder builder = DeaggDataset.builder(datasets.iterator().next());
      for (DeaggDataset dataset : datasets) {
        builder.add(dataset);
      }
      if (clusterSources.isPresent()) {
        builder.sourceSet(clusterSources.get());
      }
      return builder.build();
    }
  }

  /* One per Imt in supplied Hazard. */
  static class ImtDeagg {

    final DeaggConfig config;
    final DeaggDataset totalDataset;
    final Map<Gmm, DeaggDataset> gmmDatasets;

    ImtDeagg(Hazard hazard, DeaggConfig config) {
      this.config = config;

      ListMultimap<Gmm, DeaggDataset> datasets = MultimapBuilder
          .enumKeys(Gmm.class)
          .arrayListValues()
          .build();

      for (HazardCurveSet curveSet : hazard.sourceSetCurves.values()) {

        XySequence sourceSetCurve = curveSet.totalCurves.get(config.imt);
        double sourceSetRate = RATE_INTERPOLATER.findY(sourceSetCurve, config.iml);
        if (Double.isNaN(sourceSetRate) || sourceSetRate == 0.0) {
          System.out.println("Skipping: " + curveSet.sourceSet.name());
          continue;
        }

        Map<Gmm, DeaggDataset> sourceSetDatasets = Deaggregator.deaggregate(curveSet, config);

        /*
         * Each dataset (above) contains the contributing sources (rate and
         * skipped rate)
         *
         * barWeight = sourceSet rate
         */
        // TODO clean
        // for (Entry<Gmm, Dataset> entry :
        // sourceSetDatasets.entrySet()) {
        // Dataset d = entry.getValue();
        // String g = entry.getKey().name();
        //
        // double srcSetRate = 0.0;
        // for (SourceContribution c : d.sources) {
        // srcSetRate += c.rate;
        // }
        // System.out.println(g + " " + d);
        // System.out.println(d.barWeight + " " + srcSetRate);
        //
        // }

        // TODO run post-deagg rate validation; see toString()

        datasets.putAll(Multimaps.forMap(sourceSetDatasets));
      }

      gmmDatasets = Maps.immutableEnumMap(Maps.transformValues(
          Multimaps.asMap(datasets),
          DEFAULT_DATASET_CONSOLIDATOR));

      totalDataset = DEFAULT_DATASET_CONSOLIDATOR.apply(gmmDatasets.values());

      // for (Dataset d : gmmDatasets.values()) {
      // System.out.println("BarWt: " + d.barWeight);
      //
      // }
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();

      double sourceSetRate = Data.sum(totalDataset.sourceSets.values());
      sb.append(NEWLINE);
      sb.append("Rate from source sets").append(NEWLINE);
      sb.append("  Total:    " + sourceSetRate).append(NEWLINE);

      double binnedSourceRate = 0.0;
      double residualSourceRate = 0.0;
      for (SourceContribution source : totalDataset.sources) {
        binnedSourceRate += source.rate;
        residualSourceRate += source.residualRate;
      }
      sb.append(NEWLINE);
      sb.append("Rate from sources").append(NEWLINE);
      sb.append("  Binned:   " + binnedSourceRate).append(NEWLINE);
      sb.append("  Residual: " + residualSourceRate).append(NEWLINE);
      double totalSourceRate = binnedSourceRate + residualSourceRate;
      sb.append("  Total:    " + totalSourceRate).append(NEWLINE);

      double binnedDeaggRate = totalDataset.binned;
      double residualDeaggRate = totalDataset.residual;
      sb.append(NEWLINE);
      sb.append("Rate from deagg data").append(NEWLINE);
      sb.append("  Binned:   " + binnedDeaggRate).append(NEWLINE);
      sb.append("  Residual: " + residualDeaggRate).append(NEWLINE);
      double totalDeaggRate = binnedDeaggRate + residualDeaggRate;
      sb.append("  Total:    " + totalDeaggRate).append(NEWLINE);

      sb.append(NEWLINE);
      
      DeaggExport export = new DeaggExport(totalDataset, config);
      sb.append(export.toString());

      sb.append(NEWLINE);
      

      /* SourceSet map ordering by descending contribution */
      Ordering<Entry<SourceSet<? extends Source>, Double>> sourceSetOrdering = Ordering
          .natural()
          .onResultOf(
              new Function<Entry<SourceSet<? extends Source>, Double>, Double>() {
                @Override
                public Double apply(
                    Entry<SourceSet<? extends Source>, Double> entry) {
                  return entry.getValue();
                }
              })
          .reverse();

      Ordering<SourceSet<? extends Source>> keyOrdering = Ordering.natural()
          .onResultOf(Functions.forMap(totalDataset.sourceSets))
          .reverse();

      Set<SourceSet<? extends Source>> keys = totalDataset.sourceSets.keySet();
      for (SourceSet<? extends Source> sourceSet : keyOrdering.immutableSortedCopy(keys)) {
        double contribution = totalDataset.sourceSets.get(sourceSet);
        sb.append(sourceSet);
        sb.append("Contrib: ").append(contribution);
        sb.append(NEWLINE);
        // sb.append(" Id:
        // ").append(padEnd(Integer.toString(sourceSet.id()),
        // 8, ' '));
        // sb.append("Name: ").append(padEnd(name(), 38, ' '));
        // sb.append("Size: ").append(padEnd(Integer.toString(size()),
        // 8, ' '));
        // sb.append("Weight: ").append(padEnd(Double.toString(weight),
        // 12, ' '));
      }

      // sb.append(totalDataset.sourceSets).append(NEWLINE).append(NEWLINE);
      // sb.append(totalDataset.sources).append(NEWLINE).append(NEWLINE);
      // sb.append(NEWLINE);
      // sb.append(totalDataset.rmε);
      // sb.append(NEWLINE);
      return sb.toString();
    }
  }

  // do we want to track the relative location in each distance bin:
  // i.e. the bin plots at the contribution weighted distance
  // private Comparator<ContributingRupture> comparator = Ordering.natural();

  // private Queue<Contribution> contribQueue = MinMaxPriorityQueue
  // .orderedBy(Ordering.natural())
  // .maximumSize(20)
  // .create();

  @Deprecated
  static class SourceSetContribution implements Comparable<SourceSetContribution> {
    final SourceSet sourceSet;
    final double rate;

    private SourceSetContribution(SourceSet sourceSet, double rate) {
      this.sourceSet = sourceSet;
      this.rate = rate;
    }

    @Override
    public int compareTo(SourceSetContribution other) {
      return Double.compare(rate, other.rate);
    }

  }

  /* Wrapper class for a Source and it's contribution to hazard. */
  static class SourceContribution implements Comparable<SourceContribution> {

    // TODO need better way to identify source
    // point source are created on the fly so they would need to be
    // compared/summed by location

    // TODO track total, or just sum as necessary

    final String name;
    final double rate;
    final double residualRate;

    SourceContribution(String source, double sourceRate, double residualRate) {
      this.name = source;
      this.rate = sourceRate;
      this.residualRate = residualRate;
    }

    @Override
    public int compareTo(SourceContribution other) {
      return Double.compare(rate, other.rate);
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append(rate).append(" ");
      sb.append(residualRate).append(" ");
      sb.append(rate + residualRate).append(" ");
      sb.append(name);
      return sb.toString();
    }
  }

  @Override
  public String toString() {
    // Entry<Imt, Deagg> entry = deaggs.entrySet().iterator().next();
    StringBuilder sb = new StringBuilder();
    for (Entry<Imt, ImtDeagg> entry : deaggs.entrySet()) {
      sb.append("Deagg for IMT: ").append(entry.getKey()).append(NEWLINE);
      sb.append(entry.getValue());
    }
    return sb.toString();
  }

  public Exporter export(Imt imt) {
    System.out.println(this);
    return new Exporter(deaggs.get(imt).totalDataset, "Total");
  }

  public Exporter export(Imt imt, Gmm gmm) {
    return new Exporter(deaggs.get(imt).gmmDatasets.get(gmm), gmm.toString());
  }

  public static class Exporter {

    final String component;
    final List<RmBin> data;
    // final double sum;
    final List<Contributor> sources;
    final List<SummaryElement> summary;

    Exporter(DeaggDataset data, String component) {
      this.component = component;
      List<RmBin> rmBins = new ArrayList<>();
      // iterate magnitudes descending, distances ascending
      IntervalVolume binData = data.rmε;
      List<Double> magnitudes = Lists.reverse(binData.columns());
      List<Double> distances = binData.rows();
      double toPercent = 100.0 / data.binned;
      // System.out.println(data.barWeight);
      for (double r : distances) {
        for (double m : magnitudes) {
          XySequence εColumn = binData.column(r, m);
          if (εColumn.isClear()) {
            continue;
          }
          // double[] εValues = clean(2, multiply(toPercent,
          // toArray(εColumn.yValues())));
          double[] εValues = multiply(toPercent, toArray(εColumn.yValues()));
          List<εData> εDataList = new ArrayList<>();
          for (int i = 0; i < εValues.length; i++) {
            double εValue = εValues[i];
            if (εValue <= 0.0) {
              continue;
            }
            εDataList.add(new εData(i, εValue));
          }
          rmBins.add(new RmBin(r, m, εDataList));
        }
      }
      this.data = rmBins;

      this.sources = ImmutableList.of(
          new Contributor(
              "California B-Faults CH", Contributor.Type.MULTI, 28.5, -1,
              5.0, 7.4, 0.4,
              null, null, null),
          new Contributor(
              "California B-Faults GR", Contributor.Type.MULTI, 22.0, -1,
              6.2, 6.7, 0.15,
              null, null, null),
          new Contributor(
              "CA Crustal Gridded", Contributor.Type.MULTI, 15.0, -1,
              7.0, 6.7, -0.2,
              null, null, null),
          new Contributor(
              "Puente Hills", Contributor.Type.SINGLE, 5.2, 521,
              3.2, 7.6, 0.5,
              160.1, 33.5, -118.5),
          new Contributor(
              "Elysian Park", Contributor.Type.SINGLE, 4.0, 431,
              5.6, 6.8, 0.7,
              340.0, 33.6, -118.4),
          new Contributor(
              "San Andreas (Mojave)", Contributor.Type.SINGLE, 1.2, 44,
              32.1, 8.2, 1.5,
              22.3, 34.0, -117.5),
          new Contributor(
              "Grid Source ", Contributor.Type.GRID, 7.4, null,
              22.5, 6.2, -1.2,
              345.0, 33.7, -118.6));

      this.summary = ImmutableList.of(
          element("Deaggregation targets", true, ImmutableList.of(
              item("Return period", 2475, "yrs"),
              item("Exceedance rate", 4.0404e-4, "yr⁻¹"),
              item("Exceedance IML", 0.6085, "g"))),
          element("Recovered targets", false, ImmutableList.of(
              item("Return period", 2521, "yrs"),
              item("Exceedance rate", 3.9315e-4, "yr⁻¹"),
              item("Exceedance IML", 0.6085, "g"))),
          element("Mean", true, ImmutableList.of(
              item("r", 11.2, "km"),
              item("m", 6.98, null),
              item("ε₀", 0.34, null))),
          element("Mode (largest r-m bin)", true, ImmutableList.of(
              item("r", 9.4, "km"),
              item("m", 6.78, null),
              item("ε₀", 0.79, null),
              item("Contribution", 27.3, "%"))),
          element("Mode (largest ε₀ bin)", true, ImmutableList.of(
              item("r", 9.4, "km"),
              item("m", 6.78, null),
              item("ε interval", 0.5, "σ"), // report middle of bin
              item("Contribution", 15.2, "%"))));
    }

    /* Distance-magnitude bin container. */
    @SuppressWarnings("unused")
    private static final class RmBin {

      final double r;
      final double m;
      final List<εData> εdata;

      private RmBin(
          double r,
          double m,
          List<εData> εdata) {
        this.r = r;
        this.m = m;
        this.εdata = εdata;
      }
    }

    /* Epsilon data for a distance-magnitude bin. */
    @SuppressWarnings("unused")
    private static final class εData {
      final int εbin;
      final double value;

      // TODO may add this; requires more upstream data tracking
      // final double εbar;

      private εData(int εbin, double value) {
        this.εbin = εbin;
        this.value = value;
      }
    }
  }

  /**
   * Returns a list of objects that define the ε bins used in this deaggregation
   * when serialized to JSON.
   */
  public List<?> εBins() {
    ImmutableList.Builder<εBin> bins = ImmutableList.builder();
    DeaggDataset model = deaggs.values().iterator().next().config.model;
    List<Double> εs = model.rmε.levels();
    for (int i = 0; i < εs.size() - 1; i++) {
      Double min = (i == 0) ? null : εs.get(i);
      Double max = (i == εs.size() - 2) ? null : εs.get(i + 1);
      bins.add(new εBin(i, min, max));
    }
    return bins.build();
  }

  @SuppressWarnings("unused")
  private static class εBin {
    final int id;
    final Double min;
    final Double max;

    εBin(int id, Double min, Double max) {
      this.id = id;
      this.min = min;
      this.max = max;
    }
  }

  /* Primitive object fields may be null. */
  @SuppressWarnings("unused")
  private static class Contributor {

    final String name;
    final Type type;
    final double contribution;
    final Integer id;
    final double r;
    final double m;
    final double ε;
    final Double azimuth;
    final Double latitude;
    final Double longitude;

    Contributor(
        String name,
        Type type,
        double contribution,
        Integer id,
        double r,
        double m,
        double ε,
        Double azimuth,
        Double latitude,
        Double longitude) {

      this.name = name;
      this.type = type;
      this.contribution = contribution;
      this.id = id;
      this.m = m;
      this.r = r;
      this.ε = ε;
      this.azimuth = azimuth;
      this.latitude = latitude;
      this.longitude = longitude;
    }

    private static enum Type {
      SINGLE,
      MULTI,
      GRID;
    }

  }

  @Deprecated
  private static SummaryElement element(
      String name,
      boolean display,
      List<SummaryElement.Item> items) {
    return new SummaryElement(name, display, items);
  }

  @Deprecated
  private static SummaryElement.Item item(
      String name,
      double value,
      String units) {
    return new SummaryElement.Item(name, value, units);
  }

  @SuppressWarnings("unused")
  @Deprecated
  private static class SummaryElement {

    final String name;
    final boolean display;
    final List<Item> data;

    SummaryElement(
        String name,
        boolean display,
        List<Item> data) {

      this.name = name;
      this.display = display;
      this.data = data;
    }

    static class Item {

      final String name;
      final double value;
      final String units;

      Item(String name, double value, String units) {
        this.name = name;
        this.value = value;
        this.units = units;
      }
    }
  }

}
