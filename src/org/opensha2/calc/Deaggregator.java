package org.opensha2.calc;

import static org.opensha2.calc.DeaggDataset.SOURCE_CONSOLIDATOR;

import org.opensha2.calc.DeaggContributor.ClusterContributor;
import org.opensha2.calc.DeaggContributor.SourceContributor;
import org.opensha2.calc.DeaggContributor.SourceSetContributor;
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
 * Factory class that deaggregates the hazard for a single {@code SourceSet} by
 * {@code Gmm}.
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

  private static Map<Gmm, DeaggDataset.Builder> createBuilders(Set<Gmm> gmms, DeaggDataset model) {
    Map<Gmm, DeaggDataset.Builder> map = Maps.newEnumMap(Gmm.class);
    for (Gmm gmm : gmms) {
      map.put(gmm, DeaggDataset.builder(model));
    }
    return map;
  }

  private Map<Gmm, DeaggDataset> processSources() {
    Map<Gmm, DeaggDataset.Builder> builders = createBuilders(gmmSet.gmms(), model);
    for (DeaggDataset.Builder builder : builders.values()) {
      SourceSetContributor.Builder parent = new SourceSetContributor.Builder();
      builder.setParentContributor(parent.sourceSet(sources));
    }
    for (GroundMotions gms : curves.hazardGroundMotionsList) {
      processSource(gms, builders);
    }
    return buildDatasets(builders);
  }

  private Map<Gmm, DeaggDataset> processClusterSources() {

    List<Map<Gmm, XySequence>> clusterCurveList = curves.clusterCurveLists.get(imt);

    ListMultimap<Gmm, DeaggDataset> datasets = MultimapBuilder
        .enumKeys(Gmm.class)
        .arrayListValues(clusterCurveList.size())
        .build();

    for (int i = 0; i < curves.clusterGroundMotionsList.size(); i++) {
      ClusterGroundMotions cgms = curves.clusterGroundMotionsList.get(i);

      /* ClusterSource level builders. */
      Map<Gmm, DeaggDataset.Builder> datasetBuilders = createBuilders(gmmSet.gmms(), model);
      for (DeaggDataset.Builder datasetBuilder : datasetBuilders.values()) {
        ClusterContributor.Builder clusterContributor = new ClusterContributor.Builder();
        datasetBuilder.setParentContributor(clusterContributor.cluster(cgms.parent));
      }

      /* Process the individual sources in a cluster. */
      for (GroundMotions gms : cgms) {
        processSource(gms, datasetBuilders);
      }

      /*
       * Scale builders to the rate/contribution of the cluster and attach
       * ClusterContributors to parent SourceSetContributors and swap.
       */
      Map<Gmm, XySequence> clusterCurves = clusterCurveList.get(i);
      for (Entry<Gmm, DeaggDataset.Builder> entry : datasetBuilders.entrySet()) {
        
        /* Scale. */
        Gmm gmm = entry.getKey();
        DeaggDataset.Builder clusterBuilder = entry.getValue();
        XySequence clusterCurve = clusterCurves.get(gmm);
        double clusterRate = Deaggregation.RATE_INTERPOLATER.findY(clusterCurve, iml);
        clusterBuilder.multiply(clusterRate / clusterBuilder.rate());
        
        /* Swap parents. */
        DeaggContributor.Builder sourceSetContributor = new SourceSetContributor.Builder()
            .sourceSet(curves.sourceSet)
            .addChild(clusterBuilder.parent);
        clusterBuilder.setParentContributor(sourceSetContributor);
      }

      /* Combine cluster datasets. */
      Map<Gmm, DeaggDataset> clusterDatasets = buildDatasets(datasetBuilders);
      datasets.putAll(Multimaps.forMap(clusterDatasets));
    }

    return ImmutableMap.copyOf(Maps.transformValues(
        Multimaps.asMap(datasets),
        SOURCE_CONSOLIDATOR));
  }

  private void processSource(GroundMotions gms, Map<Gmm, DeaggDataset.Builder> builders) {

    /* Local references from argument. */
    InputList inputs = gms.inputs;
    Map<Gmm, Double> gmms = gmmSet.gmmWeightMap(gms.inputs.minDistance);
    Map<Gmm, List<Double>> μLists = gms.μLists.get(imt);
    Map<Gmm, List<Double>> σLists = gms.σLists.get(imt);

    /* Local EnumSet based keys; gmms.keySet() is not an EnumSet. */
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

        builders.get(gmm).addRate(
            rIndex, mIndex, εIndex,
            rRup * rate, Mw * rate, ε * rate,
            rate);
      }
    }

    /* Add sources/contributors to builders. */
    for (Gmm gmm : gmmKeys) {
      // TODO bad cast; how to provide access to parent reference
      // TODO will likely cause problem with SystemInputList
      DeaggContributor.Builder source = new SourceContributor.Builder()
          .source(((SourceInputList) inputs).parent)
          .add(gmmSourceRates.get(gmm), gmmSkipRates.get(gmm));
      builders.get(gmm).addChildContributor(source);
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
  private static Map<Gmm, DeaggDataset> buildDatasets(
      Map<Gmm, DeaggDataset.Builder> builders) {
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
