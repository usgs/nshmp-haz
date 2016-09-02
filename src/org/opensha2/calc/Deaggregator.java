package org.opensha2.calc;

import static org.opensha2.calc.DeaggDataset.SOURCE_CONSOLIDATOR;

import org.opensha2.calc.DeaggContributor.ClusterContributor;
import org.opensha2.calc.DeaggContributor.SectionSource;
import org.opensha2.calc.DeaggContributor.SourceContributor;
import org.opensha2.calc.DeaggContributor.SourceSetContributor;
import org.opensha2.calc.DeaggContributor.SystemContributor;
import org.opensha2.data.Data;
import org.opensha2.data.XySequence;
import org.opensha2.eq.model.ClusterSource;
import org.opensha2.eq.model.GmmSet;
import org.opensha2.eq.model.Source;
import org.opensha2.eq.model.SourceSet;
import org.opensha2.eq.model.SystemSourceSet;
import org.opensha2.geo.Location;
import org.opensha2.geo.Locations;
import org.opensha2.gmm.Gmm;
import org.opensha2.gmm.Imt;
import org.opensha2.util.Site;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Multimaps;
import com.google.common.primitives.Ints;

import java.util.BitSet;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Factory class that deaggregates the hazard for a single {@code SourceSet}
 * across all relevant {@code Gmm}s.
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

  private final Site site;

  private Deaggregator(HazardCurveSet curves, DeaggConfig config, Site site) {
    this.curves = curves;
    this.sources = curves.sourceSet;
    this.gmmSet = sources.groundMotionModels();

    this.imt = config.imt;
    this.model = config.model;
    this.iml = config.iml;
    this.probModel = config.probabilityModel;
    this.trunc = config.truncation;

    this.site = site;
  }

  static Map<Gmm, DeaggDataset> deaggregate(
      HazardCurveSet curves,
      DeaggConfig config,
      Site site) {

    Deaggregator deaggregator = new Deaggregator(curves, config, site);
    return Maps.immutableEnumMap(deaggregator.run());
  }

  private Map<Gmm, DeaggDataset> run() {
    switch (sources.type()) {
      case CLUSTER:
        return processClusterSources();
      case SYSTEM:
        return processSystemSources();
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

        /*
         * Fetch site-specific source attributes so that they don't need to be
         * recalculated multiple times downstream.
         */
        ClusterSource cluster = cgms.parent;
        Location location = cluster.location(site.location);
        double azimuth = Locations.azimuth(site.location, location);

        ClusterContributor.Builder clusterContributor = new ClusterContributor.Builder()
            .cluster(cluster, location, azimuth);
        datasetBuilder.setParentContributor(clusterContributor);
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

        /*
         * Scale, skipping clusters that do not contribute as their attendant
         * sources will also not contribute and 0/0 will yield NaNs.
         */
        Gmm gmm = entry.getKey();
        DeaggDataset.Builder clusterBuilder = entry.getValue();
        XySequence clusterCurve = clusterCurves.get(gmm);
        double clusterRate = Deaggregation.RATE_INTERPOLATER.findY(clusterCurve, iml);
        if (clusterBuilder.rate() > 0.0) {
          clusterBuilder.multiply(clusterRate / clusterBuilder.rate());
        }

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

    /*
     * Per-gmm data for the source being processed. The double[] arrays below
     * are [rate, residual, rScaled, mScaled, εScaled].
     */
    Map<Gmm, double[]> gmmData = createDataMap(gmmKeys);

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

        double rScaled = rRup * rate;
        double mScaled = Mw * rate;
        double εScaled = ε * rate;
        double[] data = gmmData.get(gmm);
        data[2] += rScaled;
        data[3] += mScaled;
        data[4] += εScaled;

        if (skipRupture) {
          data[1] += rate;
          builders.get(gmm).addResidual(rate);
          continue;
        }
        data[0] += rate;
        int εIndex = model.epsilonIndex(ε);

        builders.get(gmm).addRate(
            rIndex, mIndex, εIndex,
            rScaled, mScaled, εScaled,
            rate);
      }
    }

    /*
     * Fetch site-specific source attributes so that they don't need to be
     * recalculated multiple times downstream. Safe covariant cast assuming
     * switch handles variants.
     */
    Source source = ((SourceInputList) inputs).parent;
    Location location = source.location(site.location);
    double azimuth = Locations.azimuth(site.location, location);

    /* Add sources/contributors to builders. */
    for (Gmm gmm : gmmKeys) {
      double[] data = gmmData.get(gmm);
      DeaggContributor.Builder contributor = new SourceContributor.Builder()
          .source(source, location, azimuth)
          .add(data[0], data[1], data[2], data[3], data[4]);
      builders.get(gmm).addChildContributor(contributor);
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

  private static Map<Gmm, double[]> createDataMap(Set<Gmm> gmms) {
    Map<Gmm, double[]> rateMap = Maps.newEnumMap(Gmm.class);
    for (Gmm gmm : gmms) {
      rateMap.put(gmm, new double[5]);
    }
    return rateMap;
  }

  private static double epsilon(double μ, double σ, double iml) {
    return (iml - μ) / σ;
  }

  private Map<Gmm, DeaggDataset> processSystemSources() {

    /* Safe covariant cast assuming switch handles variants. */
    SystemSourceSet systemSources = (SystemSourceSet) sources;
    
    Map<Gmm, DeaggDataset.Builder> builders = createBuilders(gmmSet.gmms(), model);
    for (DeaggDataset.Builder builder : builders.values()) {
      SourceSetContributor.Builder parent = new SourceSetContributor.Builder();
      builder.setParentContributor(parent.sourceSet(sources));
    }

    /*
     * Subsequent to deaggregation we no longer need references to the source
     * bitsets so we drain it in place rather than making a copy to drain.
     */

    GroundMotions gms = curves.hazardGroundMotionsList.get(0);
    SystemInputList inputs = (SystemInputList) gms.inputs;
    List<BitSet> bitsets = inputs.bitsets;
    Map<Gmm, Double> gmms = gmmSet.gmmWeightMap(gms.inputs.minDistance);
    Map<Gmm, List<Double>> μLists = gms.μLists.get(imt);
    Map<Gmm, List<Double>> σLists = gms.σLists.get(imt);

    /* Local EnumSet based keys; gmms.keySet() is not an EnumSet. */
    final Set<Gmm> gmmKeys = EnumSet.copyOf(gmms.keySet());

    List<Integer> sourceIndices = new LinkedList<>(Ints.asList(Data.indices(bitsets.size())));

    for (int sectionIndex : inputs.sectionIndices) {

      /*
       * Fetch site-specific source attributes so that they don't need to be
       * recalculated multiple times downstream. Safe covariant cast assuming
       * switch handles variants.
       */
      SectionSource section = new SectionSource(sectionIndex);
      Location location = Locations.closestPoint(
          site.location,
          systemSources.section(sectionIndex).getUpperEdge());
      double azimuth = Locations.azimuth(site.location, location);

      /* Create system contributors for section and attach to parent. */
      Map<Gmm, SystemContributor.Builder> contributors = new EnumMap<>(Gmm.class);
      for (Gmm gmm : gmmKeys) {
        SystemContributor.Builder contributor = new SystemContributor.Builder()
            .section(section, location, azimuth);
        contributors.put(gmm, contributor);
        builders.get(gmm).addChildContributor(contributor);
      }

      Iterator<Integer> iter = sourceIndices.iterator();
      while (iter.hasNext()) {
        int sourceIndex = iter.next();

        /* Source includes section. */
        if (bitsets.get(sourceIndex).get(sectionIndex)) {

          HazardInput in = inputs.get(sourceIndex);
          double rRup = in.rRup;
          double Mw = in.Mw;

          int rIndex = model.distanceIndex(rRup);
          int mIndex = model.magnitudeIndex(Mw);
          boolean skipRupture = (rIndex == -1 || mIndex == -1);

          for (Gmm gmm : gmmKeys) {

            double gmmWeight = gmms.get(gmm);

            double μ = μLists.get(gmm).get(sourceIndex);
            double σ = σLists.get(gmm).get(sourceIndex);
            double ε = epsilon(μ, σ, iml);

            double probAtIml = probModel.exceedance(μ, σ, trunc, imt, iml);
            double rate = probAtIml * in.rate * sources.weight() * gmmWeight;

            SystemContributor.Builder contributor = contributors.get(gmm);

            double rScaled = rRup * rate;
            double mScaled = Mw * rate;
            double εScaled = ε * rate;

            if (skipRupture) {
              contributor.add(0.0, rate, rScaled, mScaled, εScaled);
              builders.get(gmm).addResidual(rate);
              continue;
            }
            contributor.add(rate, 0.0, rScaled, mScaled, εScaled);
            int εIndex = model.epsilonIndex(ε);

            builders.get(gmm).addRate(
                rIndex, mIndex, εIndex,
                rScaled, mScaled, εScaled,
                rate);
          }
          iter.remove();
        }
      }
    }
    return buildDatasets(builders);
  }

}
