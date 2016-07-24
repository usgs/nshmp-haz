package org.opensha2.calc;

import org.opensha2.calc.Deaggregation.SourceContribution;
import org.opensha2.data.XySequence;
import org.opensha2.eq.model.GmmSet;
import org.opensha2.eq.model.Source;
import org.opensha2.eq.model.SourceSet;
import org.opensha2.gmm.Gmm;
import org.opensha2.gmm.Imt;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Multimaps;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Factory class that deaggregates the hazard for a single
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

  private Deaggregator(HazardCurveSet curves, DeaggConfig config) {
    this.curves = curves;
    this.sources = curves.sourceSet;
    this.gmmSet = sources.groundMotionModels();

    this.imt = config.imt;
    this.model = config.model;
    this.iml = config.iml;
    this.probModel = config.probabilityModel;
    this.trunc = config.truncation;
  }

  private static Map<Gmm, DeaggDataset.Builder> createBuilders(Set<Gmm> gmms, DeaggDataset model) {
    Map<Gmm, DeaggDataset.Builder> map = Maps.newEnumMap(Gmm.class);
    for (Gmm gmm : gmms) {
      map.put(gmm, DeaggDataset.builder(model));
    }
    return map;
  }

  static Map<Gmm, DeaggDataset> deaggregate(HazardCurveSet curves, DeaggConfig config) {
    Deaggregator deaggregator = new Deaggregator(curves, config);
    return Maps.immutableEnumMap(deaggregator.run());
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
    Map<Gmm, DeaggDataset.Builder> builders = createBuilders(gmmSet.gmms(), model);
    for (GroundMotions gms : curves.hazardGroundMotionsList) {
      processSource(gms, builders);
    }
    for (DeaggDataset.Builder builder : builders.values()) {
      builder.sourceSet(sources);
    }
    return buildDatasets(builders);
  }

  /*
   * Cluster sources give the conditional POE of multiple contemporaneous
   * ruptures. If we deaggregate the contributing sources in a cluster using the
   * standard approach, we end up with a total contribution from those sources
   * that is different than the cluster contribution.
   * 
   * We therefore preserve cluster source curves in a HazardCurveSet and scale
   * the relative contributions from component sources in a cluster to sum to
   * the cluster contribution computed via interpolation.
   */
  private Map<Gmm, DeaggDataset> processClusterSources() {

    List<Map<Gmm, XySequence>> clusterCurveList = curves.clusterCurveLists.get(imt);

    ListMultimap<Gmm, DeaggDataset> datasets = MultimapBuilder
        .enumKeys(Gmm.class)
        .arrayListValues()
        .build();

    for (int i = 0; i < curves.clusterGroundMotionsList.size(); i++) {

      ClusterGroundMotions cgms = curves.clusterGroundMotionsList.get(i);
      Map<Gmm, DeaggDataset.Builder> builders = createBuilders(gmmSet.gmms(), model);

      // process the individual sources in a cluster
      for (GroundMotions gms : cgms) {
        processSource(gms, builders);
      }

      // scale deagg data builders to the rate/contribution of the cluster
      Map<Gmm, XySequence> clusterCurves = clusterCurveList.get(i);
      for (Entry<Gmm, DeaggDataset.Builder> entry : builders.entrySet()) {
        Gmm gmm = entry.getKey();
        DeaggDataset.Builder builder = entry.getValue();
        XySequence clusterCurve = clusterCurves.get(gmm);
        double clusterRate = Deaggregation.RATE_INTERPOLATER.findY(clusterCurve, iml);
        builder.multiply(clusterRate / builder.rate());
      }

      // combine the cluster datasets
      Map<Gmm, DeaggDataset> clusterDatasets = buildDatasets(builders);
      datasets.putAll(Multimaps.forMap(clusterDatasets));
    }

    return ImmutableMap.copyOf(Maps.transformValues(
        Multimaps.asMap(datasets),
        Deaggregation.DATASET_CONSOLIDATOR));
  }

  private void processSource(GroundMotions gms, Map<Gmm, DeaggDataset.Builder> builders) {

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
          builders.get(gmm).addResidual(rate);
          continue;
        }
        gmmSourceRates.put(gmm, gmmSourceRates.get(gmm) + rate);
        int εIndex = model.epsilonIndex(ε);

        builders.get(gmm).add(
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
      builders.get(gmm).add(source);
    }
  }

  private static final Function<DeaggDataset.Builder, DeaggDataset> DATASET_BUILDER =
      new Function<DeaggDataset.Builder, DeaggDataset>() {
        @Override
        public DeaggDataset apply(DeaggDataset.Builder builder) {
          return builder.build();
        }
      };

  /*
   * Implementation note: Maps.transformValues(map) returns a lazy view.
   * Builders are heavyweight and so to generate a map with concrete instances
   * we return a copy.
   */
  private static Map<Gmm, DeaggDataset> buildDatasets(Map<Gmm, DeaggDataset.Builder> builders) {
    return ImmutableMap.copyOf(Maps.transformValues(builders, DATASET_BUILDER));
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
