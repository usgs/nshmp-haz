package org.opensha2.calc;

import org.opensha2.calc.Deaggregation.SourceContribution;
import org.opensha2.eq.model.GmmSet;
import org.opensha2.eq.model.Source;
import org.opensha2.eq.model.SourceSet;
import org.opensha2.gmm.Gmm;
import org.opensha2.gmm.Imt;

import com.google.common.base.Function;
import com.google.common.collect.Maps;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Factory class that deaggregates the hazard for an individual
 * {@code SourceSet} by {@code Gmm}.
 *
 * @author Peter Powers
 */
final class Deaggregator {

  private final HazardCurveSet curves;
  private final SourceSet<? extends Source> sources;
  private final GmmSet gmmSet;

  private final Imt imt;
  private final DeaggDataset model;
  private final double iml;
  private final ExceedanceModel probModel;
  private final double trunc;

  private final Map<Gmm, DeaggDataset.Builder> datasetBuilders;

  private Deaggregator(HazardCurveSet curves, DeaggConfig config) {
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

  private static Map<Gmm, DeaggDataset.Builder> initDataBuilders(Set<Gmm> gmms,
      DeaggDataset model) {
    Map<Gmm, DeaggDataset.Builder> map = Maps.newEnumMap(Gmm.class);
    for (Gmm gmm : gmms) {
      map.put(gmm, DeaggDataset.builder(model));
    }
    return map;
  }

  static Map<Gmm, DeaggDataset> deaggregate(HazardCurveSet curves, DeaggConfig config) {
    Deaggregator deaggregator = new Deaggregator(curves, config);
    return deaggregator.run();
  }

  private Map<Gmm, DeaggDataset> run() {
    switch (sources.type()) {
      case CLUSTER:
        return processClusterSources();
      default:
        return processSources();
    }
  }

  private Map<Gmm, DeaggDataset> processSources() {
    for (GroundMotions gms : curves.hazardGroundMotionsList) {
      processSource(gms);
    }
    for (DeaggDataset.Builder builder : datasetBuilders.values()) {
      builder.sourceSet(sources);
    }
    return createDataMap();
  }

  private Map<Gmm, DeaggDataset> createDataMap() {
    return Maps.immutableEnumMap(
        Maps.transformValues(
            datasetBuilders,
            new Function<DeaggDataset.Builder, DeaggDataset>() {
              @Override
              public DeaggDataset apply(DeaggDataset.Builder builder) {
                return builder.build();
              }
            }));
  }

  private void processSource(GroundMotions gms) {

    /* Local references from argument. */
    InputList inputs = gms.inputs;
    Map<Gmm, Double> gmms = gmmSet.gmmWeightMap(gms.inputs.minDistance);
    Map<Gmm, List<Double>> μLists = gms.μLists.get(imt);
    Map<Gmm, List<Double>> σLists = gms.σLists.get(imt);

    /* Local EnumSet based keys; we know gmms.keySet() is not an EnumSet. */
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

  private Map<Gmm, DeaggDataset> processClusterSources() {
    for (ClusterGroundMotions cgms : curves.clusterGroundMotionsList) {
      processClusterSource(cgms);
    }
    for (DeaggDataset.Builder builder : datasetBuilders.values()) {
      builder.sourceSet(sources);
    }
    return createDataMap();
  }

  /*
   * Cluster sources give the conditional POE of multiple contemporaneous
   * ruptures. To deaggregate, we can either (1) recomute the cluster curve and
   * then get a contribution value via interpolation, or (2)
   * 
   * In the case of clusters we'll carry the computed curve forward for the sake
   * of deaggregation.
   * 
   */
  private void processClusterSource(ClusterGroundMotions cgms) {
    System.out.println(cgms.parent.name());
//    System.out.println(cgms.delegate);
    System.out.println(curves.clusterCurveLists);
    // /* Local references from argument. */
    // InputList inputs = gms.inputs;
    // Map<Gmm, Double> gmms = gmmSet.gmmWeightMap(gms.inputs.minDistance);
    // Map<Gmm, List<Double>> μLists = gms.μLists.get(imt);
    // Map<Gmm, List<Double>> σLists = gms.σLists.get(imt);
    //
    // /* Local EnumSet based keys. */
    // final Set<Gmm> gmmKeys = EnumSet.copyOf(gmms.keySet());
    //
    // /* Per-gmm rates for the source being processed. */
    // Map<Gmm, Double> gmmSourceRates = createRateMap(gmmKeys);
    // Map<Gmm, Double> gmmSkipRates = createRateMap(gmmKeys);
    //
    // /* Add rupture data to builders */
    // for (int i = 0; i < inputs.size(); i++) {
    //
    // HazardInput in = inputs.get(i);
    // double rRup = in.rRup;
    // double Mw = in.Mw;
    //
    // int rIndex = model.distanceIndex(rRup);
    // int mIndex = model.magnitudeIndex(Mw);
    // boolean skipRupture = (rIndex == -1 || mIndex == -1);
    //
    // for (Gmm gmm : gmmKeys) {
    //
    // double gmmWeight = gmms.get(gmm);
    //
    // double μ = μLists.get(gmm).get(i);
    // double σ = σLists.get(gmm).get(i);
    // double ε = epsilon(μ, σ, iml);
    //
    // double probAtIml = probModel.exceedance(μ, σ, trunc, imt, iml);
    // double rate = probAtIml * in.rate * sources.weight() * gmmWeight;
    //
    // if (skipRupture) {
    // gmmSkipRates.put(gmm, gmmSkipRates.get(gmm) + rate);
    // datasetBuilders.get(gmm).addResidual(rate);
    // continue;
    // }
    // gmmSourceRates.put(gmm, gmmSourceRates.get(gmm) + rate);
    // int εIndex = model.epsilonIndex(ε);
    //
    // datasetBuilders.get(gmm).add(
    // rIndex, mIndex, εIndex,
    // rRup * rate, Mw * rate, ε * rate,
    // rate);
    // }
    // }
    //
    // /* Add sources/contributors to builders. */
    // for (Gmm gmm : gmmKeys) {
    // SourceContribution source = new SourceContribution(
    // inputs.parentName(),
    // gmmSourceRates.get(gmm),
    // gmmSkipRates.get(gmm));
    // datasetBuilders.get(gmm).add(source);
    // }
  }

  private static Map<Gmm, Double> createRateMap(Set<Gmm> gmms) {
    Map<Gmm, Double> rateMap = Maps.newEnumMap(Gmm.class);
    for (Gmm gmm : gmms) {
      rateMap.put(gmm, 0.0);
    }
    return rateMap;
  }

  private static double epsilon(double μ, double σ, double iml) {
    return (μ - iml) / σ;
  }

}
