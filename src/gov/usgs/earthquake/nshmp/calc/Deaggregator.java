package gov.usgs.earthquake.nshmp.calc;

import static gov.usgs.earthquake.nshmp.calc.DeaggDataset.SOURCE_CONSOLIDATOR;

import java.math.RoundingMode;
import java.util.BitSet;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Multimaps;
import com.google.common.primitives.Ints;

import gov.usgs.earthquake.nshmp.calc.DeaggContributor.ClusterContributor;
import gov.usgs.earthquake.nshmp.calc.DeaggContributor.SectionSource;
import gov.usgs.earthquake.nshmp.calc.DeaggContributor.SourceContributor;
import gov.usgs.earthquake.nshmp.calc.DeaggContributor.SourceSetContributor;
import gov.usgs.earthquake.nshmp.calc.DeaggContributor.SystemContributor;
import gov.usgs.earthquake.nshmp.data.Indexing;
import gov.usgs.earthquake.nshmp.data.IntervalArray;
import gov.usgs.earthquake.nshmp.data.XySequence;
import gov.usgs.earthquake.nshmp.eq.model.ClusterSource;
import gov.usgs.earthquake.nshmp.eq.model.GmmSet;
import gov.usgs.earthquake.nshmp.eq.model.Source;
import gov.usgs.earthquake.nshmp.eq.model.SourceSet;
import gov.usgs.earthquake.nshmp.eq.model.SystemSourceSet;
import gov.usgs.earthquake.nshmp.geo.Location;
import gov.usgs.earthquake.nshmp.geo.Locations;
import gov.usgs.earthquake.nshmp.gmm.Gmm;
import gov.usgs.earthquake.nshmp.gmm.Imt;
import gov.usgs.earthquake.nshmp.gmm.ScalarGroundMotion;
import gov.usgs.earthquake.nshmp.util.Maths;

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
         * Due to Gmm variations with distance, cluster curves for some GMMs may
         * not have been calculated. Skip non-participating clusters (curve will
         * be absent). Scale to total cluster rate. Builder rate > 0.0 check
         * assures no 0/0 --> NaN and is necessary for curves that are present
         * but that end below the target deagg iml.
         */
        Gmm gmm = entry.getKey();
        DeaggDataset.Builder clusterBuilder = entry.getValue();
        if (clusterCurves.containsKey(gmm)) {
          XySequence clusterCurve = clusterCurves.get(gmm);
          double clusterRate = Deaggregation.RATE_INTERPOLATER.findY(clusterCurve, iml);
          if (clusterBuilder.rate() > 0.0) {
            clusterBuilder.multiply(clusterRate / clusterBuilder.rate());
          }
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
        SOURCE_CONSOLIDATOR::apply));
  }

  private void processSource(GroundMotions gms, Map<Gmm, DeaggDataset.Builder> builders) {

    /* Local references from argument. */
    InputList inputs = gms.inputs;
    Map<Gmm, Double> gmms = gmmSet.gmmWeightMap(gms.inputs.minDistance);
    Map<Gmm, List<ScalarGroundMotion>> gmLists = gms.gmMap.get(imt);

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

        ScalarGroundMotion sgm = gmLists.get(gmm).get(i);
        double μ = sgm.mean();
        double σ = sgm.sigma();
        double ε = Maths.epsilon(μ, σ, iml);

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
    return ImmutableMap.copyOf(Maps.transformValues(
        builders, 
        DATASET_BUILDER::apply));
  }

  private static Map<Gmm, double[]> createDataMap(Set<Gmm> gmms) {
    Map<Gmm, double[]> rateMap = Maps.newEnumMap(Gmm.class);
    for (Gmm gmm : gmms) {
      rateMap.put(gmm, new double[5]);
    }
    return rateMap;
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
     * bitsets so we drain it in place rather than making a copy.
     */

    GroundMotions gms = curves.hazardGroundMotionsList.get(0);
    SystemInputList inputs = (SystemInputList) gms.inputs;
    List<BitSet> bitsets = inputs.bitsets;
    Map<Gmm, Double> gmms = gmmSet.gmmWeightMap(gms.inputs.minDistance);
    Map<Gmm, List<ScalarGroundMotion>> gmLists = gms.gmMap.get(imt);

    /* Local EnumSet based keys; gmms.keySet() is not an EnumSet. */
    final Set<Gmm> gmmKeys = EnumSet.copyOf(gmms.keySet());

    /*
     * Set up generic IntervalArray to be copied and used to aggregate magnitude
     * contributions by section. We also create a utility builder for magnitude
     * indexing.
     */
    IntervalArray mfdModel = IntervalArray.Builder.withRows(
        Maths.round(systemSources.stats.mMin, 1, RoundingMode.FLOOR),
        Maths.round(systemSources.stats.mMax, 1, RoundingMode.CEILING),
        0.1).build();
    IntervalArray.Builder mfdIndexer = IntervalArray.Builder.fromModel(mfdModel);

    List<Integer> sourceIndices = new LinkedList<>(Ints.asList(Indexing.indices(bitsets.size())));

    for (int sectionIndex : inputs.sectionIndices) {

      /*
       * Init section and fetch site-specific source attributes so that they
       * don't need to be recalculated multiple times downstream. Safe covariant
       * cast assuming switch in deaggregate() correctly handles variants.
       */
      SectionSource section = new SectionSource(
          sectionIndex,
          systemSources.sectionName(sectionIndex));
      Location location = Locations.closestPoint(
          site.location,
          systemSources.section(sectionIndex).getUpperEdge());
      double azimuth = Locations.azimuth(site.location, location);

      /*
       * Init sectionMfds, create system contributors for section and attach to
       * parent.
       */
      Map<Gmm, SystemContributor.Builder> contributors = new EnumMap<>(Gmm.class);
      for (Gmm gmm : gmmKeys) {
        IntervalArray.Builder mfdBuilder = IntervalArray.Builder.fromModel(mfdModel);
        SystemContributor.Builder contributor = new SystemContributor.Builder()
            .section(section, location, azimuth, mfdBuilder);
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

          int mfdIndex = -1;
          try {
            mfdIndex = mfdIndexer.rowIndex(Mw);
          } catch (IndexOutOfBoundsException iobe) {
            System.out.println(mfdModel.rowMax() + " " + Mw);
          }

          for (Gmm gmm : gmmKeys) {

            double gmmWeight = gmms.get(gmm);

            ScalarGroundMotion sgm = gmLists.get(gmm).get(sourceIndex);
            double μ = sgm.mean();
            double σ = sgm.sigma();
            double ε = Maths.epsilon(μ, σ, iml);

            double probAtIml = probModel.exceedance(μ, σ, trunc, imt, iml);
            double rate = probAtIml * in.rate * sources.weight() * gmmWeight;

            SystemContributor.Builder contributor = contributors.get(gmm);

            contributor.addToMfd(mfdIndex, rate);

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
