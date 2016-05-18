package org.opensha2.calc;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.primitives.Doubles.toArray;

import static org.opensha2.data.Data.checkInRange;
import static org.opensha2.data.Data.multiply;
import static org.opensha2.util.TextUtils.LOG_INDENT;
import static org.opensha2.util.TextUtils.NEWLINE;

import org.opensha2.data.Data;
import org.opensha2.data.DataTable;
import org.opensha2.data.DataTables;
import org.opensha2.data.DataVolume;
import org.opensha2.data.Interpolator;
import org.opensha2.data.XySequence;
import org.opensha2.eq.Magnitudes;
import org.opensha2.eq.model.GmmSet;
import org.opensha2.eq.model.Source;
import org.opensha2.eq.model.SourceSet;
import org.opensha2.gmm.Gmm;
import org.opensha2.gmm.Imt;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Ordering;
import com.google.common.collect.Range;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
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
   * Revisit precision issues associated with integre based return period;
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
    Config.Builder cb = Config.builder(hazard);
    double rate = 1.0 / returnPeriod;

    for (Entry<Imt, XySequence> entry : hazard.totalCurves.entrySet()) {
      Imt imt = entry.getKey();
      double iml = IML_INTERPOLATER.findX(entry.getValue(), rate);
      Config config = cb.imt(imt).iml(iml, rate, returnPeriod).build();
      System.out.println(config);
      ImtDeagg imtDeagg = ImtDeagg.create(hazard, config);
      imtDeaggMap.put(imt, imtDeagg);
    }

    return new Deaggregation(Maps.immutableEnumMap(imtDeaggMap));
  }

  /* Hazard curves are already in log-x space. */
  private static final Interpolator IML_INTERPOLATER = Interpolator.builder()
    .logy()
    .decreasingX()
    .build();

  /**
   * Deaggregate {@code hazard} at the supplied intensity measure level.
   *
   * @param hazard to deaggregate.
   * @param iml intensity measure level at which to deaggregate {@code hazard}
   */
  public static Deaggregation atIml(Hazard hazard, double iml) {

    Map<Imt, ImtDeagg> imtDeaggMap = Maps.newEnumMap(Imt.class);
    Config.Builder cb = Config.builder(hazard);

    for (Entry<Imt, XySequence> entry : hazard.totalCurves.entrySet()) {
      Imt imt = entry.getKey();
      double rate = RATE_INTERPOLATER.findY(entry.getValue(), iml);
      double returnPeriod = 1.0 / rate;
      Config config = cb.imt(imt).iml(iml, rate, returnPeriod).build();
      ImtDeagg imtDeagg = ImtDeagg.create(hazard, config);
      imtDeaggMap.put(imt, imtDeagg);
    }

    return new Deaggregation(Maps.immutableEnumMap(imtDeaggMap));
  }

  /* Hazard curves are already in log-x space. */
  private static final Interpolator RATE_INTERPOLATER = Interpolator.builder()
    .logy()
    .build();

  /*
   * A deaggregation configuration container. This class provides a reusable
   * builder that comes in handly when iterating over IMTs and only the return
   * period and iml require updating. A unique config is required for each
   * deaggregation performed.
   * 
   * Note that this is a class of convenience and assumes that return period and
   * IML are in agreement for the IMT of interest, i.e. one or the other has
   * been correctly derived from the total curve in a hazard object.
   */
  static class Config {

    final Imt imt;
    final Dataset model;
    final double iml;
    final double rate;
    final double returnPeriod;
    final ExceedanceModel probabilityModel;
    final double truncation;

    private Config(
        Imt imt,
        Dataset model,
        double iml,
        double rate,
        double returnPeriod,
        ExceedanceModel probabilityModel,
        double truncation) {

      this.imt = imt;
      this.model = model;
      this.iml = iml;
      this.rate = rate;
      this.returnPeriod = returnPeriod;
      this.probabilityModel = probabilityModel;
      this.truncation = truncation;
    }

    @Override
    public String toString() {
      return new StringBuilder("Deagg config:")
        .append(LOG_INDENT)
        .append("imt: ").append(imt.name()).append(" [").append(imt).append("]")
        .append(LOG_INDENT)
        .append("iml: ").append(iml).append(" ").append(imt.units())
        .append(LOG_INDENT)
        .append("rate: ").append(rate).append(" yr⁻¹")
        .append(LOG_INDENT)
        .append("returnPeriod: ").append(returnPeriod).append(" yrs")
        .append(LOG_INDENT)
        .append("probabilityModel: ").append(probabilityModel)
        .append(" [trunc = ").append(truncation).append("]")
        .toString();
    }

    static Builder builder(Hazard hazard) {
      return new Builder()
        .dataModel(
          Dataset.builder(hazard.config).build())
        .probabilityModel(
          hazard.config.curve.exceedanceModel,
          hazard.config.curve.truncationLevel);
    }

    /* Reusable builder */
    static class Builder {

      private Imt imt;
      private Dataset model;
      private Double iml;
      private Double rate;
      private Double returnPeriod;
      private ExceedanceModel probabilityModel;
      private Double truncation;

      Builder imt(Imt imt) {
        this.imt = imt;
        return this;
      }

      Builder dataModel(Dataset model) {
        this.model = model;
        return this;
      }

      /*
       * Supply the target iml along with corresponding annual rate and return
       * period for the IMT of interest.
       */
      Builder iml(double iml, double rate, double returnPeriod) {
        this.iml = iml;
        this.rate = rate;
        this.returnPeriod = returnPeriod;
        return this;
      }

      Builder probabilityModel(ExceedanceModel probabilityModel, double truncation) {
        this.probabilityModel = probabilityModel;
        this.truncation = truncation;
        return this;
      }

      Config build() {
        return new Config(
          checkNotNull(imt),
          checkNotNull(model),
          checkNotNull(iml),
          checkNotNull(rate),
          checkNotNull(returnPeriod),
          checkNotNull(probabilityModel),
          checkNotNull(truncation));
      }
    }

  }

  /* One per Imt in supplied Hazard. */
  static class ImtDeagg {

    final Config config;
    final Dataset totalDataset;
    final Map<Gmm, Dataset> gmmDatasets;

    static ImtDeagg create(Hazard hazard, Config config) {
      return new ImtDeagg(hazard, config);
    }

    private ImtDeagg(Hazard hazard, Config config) {
      this.config = config;

      ListMultimap<Gmm, Dataset> datasets = MultimapBuilder
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

        Map<Gmm, Dataset> sourceSetDatasets = Deaggregator.deaggregate(curveSet, config);

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

      gmmDatasets = Maps.immutableEnumMap(
        Maps.transformValues(
          Multimaps.asMap(datasets),
          DATASET_CONSOLIDATOR));

      totalDataset = DATASET_CONSOLIDATOR.apply(gmmDatasets.values());

      // for (Dataset d : gmmDatasets.values()) {
      // System.out.println("BarWt: " + d.barWeight);
      //
      // }
    }

    private static final Function<Collection<Dataset>, Dataset> DATASET_CONSOLIDATOR =
      new Function<Collection<Dataset>, Dataset>() {
        @Override
        public Dataset apply(Collection<Dataset> datasets) {
          Dataset.Builder builder = Dataset.builder(datasets.iterator().next());
          for (Dataset dataset : datasets) {
            builder.add(dataset);
          }
          return builder.build();
        }
      };

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
        residualSourceRate += source.skipRate;
      }
      sb.append(NEWLINE);
      sb.append("Rate from sources").append(NEWLINE);
      sb.append("  Binned:   " + binnedSourceRate).append(NEWLINE);
      sb.append("  Residual: " + residualSourceRate).append(NEWLINE);
      double totalSourceRate = binnedSourceRate + residualSourceRate;
      sb.append("  Total:    " + totalSourceRate).append(NEWLINE);

      double binnedDeaggRate = totalDataset.barWeight;
      double residualDeaggRate = totalDataset.residualWeight;
      sb.append(NEWLINE);
      sb.append("Rate from deagg data").append(NEWLINE);
      sb.append("  Binned:   " + binnedDeaggRate).append(NEWLINE);
      sb.append("  Residual: " + residualDeaggRate).append(NEWLINE);
      double totalDeaggRate = binnedDeaggRate + residualDeaggRate;
      sb.append("  Total:    " + totalDeaggRate).append(NEWLINE);

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

    // TODO rename skip to residual
    // TODO track total, or just sum as necessary
    // TODO are ther einstances where part of gr source falls outside deagg
    // ranges?

    final String source;
    final double rate;
    final double skipRate;

    private SourceContribution(String source, double sourceRate, double skipRate) {
      this.source = source;
      this.rate = sourceRate;
      this.skipRate = skipRate;
    }

    @Override
    public int compareTo(SourceContribution other) {
      return Double.compare(rate, other.rate);
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append(rate).append(" ");
      sb.append(skipRate).append(" ");
      sb.append(rate + skipRate).append(" ");
      sb.append(source);
      return sb.toString();
    }
  }

  /* One deaggregator per source set. */
  private static class Deaggregator {

    private final HazardCurveSet curves;
    private final SourceSet<? extends Source> sources;
    private final GmmSet gmmSet;

    private final Imt imt;
    private final Dataset model;
    private final double iml;
    private final ExceedanceModel probModel;
    private final double trunc;

    private final Map<Gmm, Dataset.Builder> datasetBuilders;

    private Deaggregator(HazardCurveSet curves, Config config) {
      this.curves = curves;
      this.sources = curves.sourceSet;
      this.gmmSet = sources.groundMotionModels();

      this.imt = config.imt;
      this.model = config.model;
      this.iml = config.iml;
      this.probModel = config.probabilityModel;
      this.trunc = config.truncation;

      this.datasetBuilders = initDataBuilders(gmmSet.gmms(), config.model);
    }

    private static Map<Gmm, Dataset> deaggregate(HazardCurveSet curves, Config config) {
      Deaggregator deaggregator = new Deaggregator(curves, config);
      return deaggregator.deaggregate();
    }

    private static Map<Gmm, Dataset.Builder> initDataBuilders(Set<Gmm> gmms, Dataset model) {
      Map<Gmm, Dataset.Builder> map = Maps.newEnumMap(Gmm.class);
      for (Gmm gmm : gmms) {
        map.put(gmm, Dataset.builder(model));
      }
      return map;
    }

    private Map<Gmm, Dataset> deaggregate() {
      for (GroundMotions gms : curves.hazardGroundMotionsList) {
        InputList inputs = gms.inputs;
        double minDistance = inputs.minDistance;
        Map<Gmm, List<Double>> μLists = gms.means.get(imt);
        Map<Gmm, List<Double>> σLists = gms.sigmas.get(imt);
        Map<Gmm, Double> gmms = gmmSet.gmmWeightMap(minDistance);
        processSource(inputs, gmms, μLists, σLists);
      }
      for (Dataset.Builder builder : datasetBuilders.values()) {
        builder.sourceSet(sources);
      }
      return createDataMap();
    }

    private Map<Gmm, Dataset> createDataMap() {
      return Maps.immutableEnumMap(
        Maps.transformValues(
          datasetBuilders,
          new Function<Dataset.Builder, Dataset>() {
            @Override
            public Dataset apply(Dataset.Builder builder) {
              return builder.build();
            }
          }));
    }

    private void processSource(
        InputList inputs,
        Map<Gmm, Double> gmms,
        Map<Gmm, List<Double>> μLists,
        Map<Gmm, List<Double>> σLists) {

      /* Local EnumSet based keys. */
      final Set<Gmm> gmmKeys = EnumSet.copyOf(gmms.keySet());

      /* Per-gmm rates for the source being processed. */
      Map<Gmm, Double> gmmSourceRates = createRateMap(gmmKeys);
      Map<Gmm, Double> gmmSkipRates = createRateMap(gmmKeys);

      /* Add rupture data to builders */
      for (int i = 0; i < inputs.size(); i++) {

        HazardInput in = inputs.get(i);
        double rRup = in.rRup;
        double Mw = in.Mw;

        int rIndex = model.distanceIndex(rRup);
        int mIndex = model.magnitudeIndex(Mw);
        boolean skipRupture = (rIndex == -1 || mIndex == -1);

        for (Gmm gmm : gmmKeys) {

          double gmmWeight = gmms.get(gmm);

          double μ = μLists.get(gmm).get(i);
          double σ = σLists.get(gmm).get(i);
          double ε = epsilon(μ, σ, iml);

          double probAtIml = probModel.exceedance(μ, σ, trunc, imt, iml);
          double rate = probAtIml * in.rate * sources.weight() * gmmWeight;

          if (skipRupture) {
            gmmSkipRates.put(gmm, gmmSkipRates.get(gmm) + rate);
            datasetBuilders.get(gmm).addResidual(rate);
            continue;
          }
          gmmSourceRates.put(gmm, gmmSourceRates.get(gmm) + rate);
          int εIndex = model.epsilonIndex(ε);

          datasetBuilders.get(gmm).add(
            rIndex, mIndex, εIndex,
            rRup * rate, Mw * rate, ε * rate,
            rate);
        }
      }

      /* Add sources/contributors to builders. */
      for (Gmm gmm : gmmKeys) {
        SourceContribution source = new SourceContribution(
          inputs.parentName(),
          gmmSourceRates.get(gmm),
          gmmSkipRates.get(gmm));
        datasetBuilders.get(gmm).add(source);
      }
    }

    private static Map<Gmm, Double> createRateMap(Set<Gmm> gmms) {
      Map<Gmm, Double> rateMap = Maps.newEnumMap(Gmm.class);
      for (Gmm gmm : gmms) {
        rateMap.put(gmm, 0.0);
      }
      return rateMap;
    }
  }

  private static double epsilon(double μ, double σ, double iml) {
    return (μ - iml) / σ;
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

    Exporter(Dataset data, String component) {
      this.component = component;
      List<RmBin> rmBins = new ArrayList<>();
      // iterate magnitudes descending, distances ascending
      DataVolume binData = data.rmε;
      List<Double> magnitudes = Lists.reverse(binData.columns());
      List<Double> distances = binData.rows();
      double toPercent = 100.0 / data.barWeight;
      // System.out.println(data.barWeight);
      for (double r : distances) {
        for (double m : magnitudes) {
          XySequence εColumn = binData.column(r, m);
          if (εColumn.isEmpty()) continue;
          // double[] εValues = clean(2, multiply(toPercent,
          // toArray(εColumn.yValues())));
          double[] εValues = multiply(toPercent, toArray(εColumn.yValues()));
          List<εData> εDataList = new ArrayList<>();
          for (int i = 0; i < εValues.length; i++) {
            double εValue = εValues[i];
            if (εValue <= 0.0) continue;
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

    /* Epsilon data for a distnace-magnitude bin. */
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

  private static final Range<Double> rRange = Range.closed(0.0, 1000.0);
  private static final Range<Double> εRange = Range.closed(-3.0, 3.0);

  /*
   * Deaggregation dataset that stores deaggregation results of individual
   * SourceSets and Gmms. Datasets may be recombined via add().
   * 
   * Binned deaggregation data and summary statistics are commonly weighted by
   * the rate of the contributing sources so the term 'weight' in a dataset is
   * synonymous with rate or a rate sum.
   */
  static class Dataset {

    private final DataVolume rmε;

    /* Weighted mean contributions */
    private final double rBar, mBar, εBar;

    /* Total rate for a dataset and summed weight for *Bar fields */
    private final double barWeight;

    /* r and m position data already weighted by rate */
    private final DataTable rPositions;
    private final DataTable mPositions;

    /* Total weight (rate) in each r and m position bin */
    private final DataTable positionWeights;

    /* Unbinned weight (rate) */
    private final double residualWeight;

    /* Contributors */
    private final Map<SourceSet<? extends Source>, Double> sourceSets;
    private final List<SourceContribution> sources;

    private Dataset(
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
     * Index methods delegate to the same method used to initialize internal
     * data tables and volumes.
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
     * indexing behaves differently than distance and magnitude indexing.
     * Whereas distance and magnitudes, if out of range of a deaggregation,
     * return -1, the lowermost and uppermost epsilon bins are open ended and
     * are used to collect all values less than or greater than the upper and
     * lower edges of those bins, respectively.
     * 
     * @param ε epsilon for which to compute index
     */
    int epsilonIndex(double ε) {
      return (ε < rmε.levelMin()) ? 0 : (ε >= rmε.levelMax()) ? rmε.levels().size() - 1
        : DataTables.indexOf(rmε.levelMin(), rmε.levelΔ(), ε, rmε.levels().size());
    }

    /**
     * Initialize a deaggregation dataset builder using an existing dataset
     * whose immutable structural properties will be shared (e.g. row and column
     * arrays of data tables).
     * 
     * @param model to mirror
     */
    static Builder builder(Dataset model) {
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
       * Dataset fields (data tables and volumes) validate deltas relative to
       * min and max supplied; we only check ranges here.
       */
      return new Builder(
        rMin, rMax, Δr,
        mMin, mMax, Δm,
        εMin, εMax, Δε);
    }

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
      private ImmutableList.Builder<SourceContribution> sources;

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

        sourceSets = Maps.newHashMap();
        sources = ImmutableList.builder();
      }

      private Builder(Dataset model) {
        rmε = DataVolume.Builder.fromModel(model.rmε);
        rPositions = DataTable.Builder.fromModel(model.rPositions);
        mPositions = DataTable.Builder.fromModel(model.mPositions);
        positionWeights = DataTable.Builder.fromModel(model.positionWeights);
        sourceSets = Maps.newHashMap();
        sources = ImmutableList.builder();
      }

      /*
       * Populate dataset with rupture data. Supply DataTable and DataVolume
       * indices, distance, magnitude, and epsilon (weighted by rate), and the
       * rate of the rupture.
       * 
       * Although we could work with the raw distance, magnitude and epsilon
       * values, deaggregation is being performed across each Gmm, so
       * precomputing indices and weighted values in the calling method brings
       * some efficiency.
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

      /* Combine values */
      Builder add(Dataset other) {

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

      Dataset build() {
        if (sourceSets.size() == 1) {
          Entry<SourceSet<? extends Source>, Double> entry =
            Iterables.getOnlyElement(sourceSets.entrySet());
          sourceSets.put(entry.getKey(), barWeight + residualWeight);
        }

        return new Dataset(
          rmε.build(),
          rBar, mBar, εBar,
          barWeight,
          rPositions.build(),
          mPositions.build(),
          positionWeights.build(),
          residualWeight,
          ImmutableMap.copyOf(sourceSets),
          sources.build());
      }
    }
  }

  /**
   * Returns a list of objects that define the ε bins used in this deaggregation
   * when serialized to JSON.
   */
  public List<?> εBins() {
    ImmutableList.Builder<εBin> bins = ImmutableList.builder();
    Dataset model = deaggs.values().iterator().next().config.model;
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

  private static SummaryElement element(
      String name,
      boolean display,
      List<SummaryElement.Item> items) {
    return new SummaryElement(name, display, items);
  }

  private static SummaryElement.Item item(
      String name,
      double value,
      String units) {
    return new SummaryElement.Item(name, value, units);
  }

  @SuppressWarnings("unused")
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
