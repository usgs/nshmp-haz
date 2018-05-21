package gov.usgs.earthquake.nshmp.calc;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static gov.usgs.earthquake.nshmp.data.XySequence.copyOf;
import static gov.usgs.earthquake.nshmp.data.XySequence.emptyCopyOf;
import static gov.usgs.earthquake.nshmp.data.XySequence.immutableCopyOf;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import gov.usgs.earthquake.nshmp.data.XySequence;
import gov.usgs.earthquake.nshmp.eq.model.Source;
import gov.usgs.earthquake.nshmp.eq.model.SourceSet;
import gov.usgs.earthquake.nshmp.eq.model.SourceType;
import gov.usgs.earthquake.nshmp.gmm.Gmm;
import gov.usgs.earthquake.nshmp.gmm.Imt;

/**
 * Container class for hazard curves derived from a {@code SourceSet}. Class
 * stores the {@code GroundMotions}s associated with each {@code Source} used in
 * a hazard calculation and the individual curves for each
 * {@code GroundMotionModel} used.
 *
 * <p>The {@code Builder} for this class is used to aggregate the HazardCurves
 * associated with each {@code Source} in a {@code SourceSet}, scaled by the
 * {@code SourceSet} weight. It also scales curves by their associated
 * {@code GroundMotionModel} weight, using distance-dependent weights when
 * appropriate. Note that this may lead to the dropping of some curves that are
 * not appropriate at large distances.
 *
 * <p>A HazardCurveSet is compatible with all types of {@code SourceSet}s,
 * including {@code ClusterSourceSet}s, which are handled differently in hazard
 * calculations. This container marks a point in the calculation pipeline where
 * results from cluster and other sources may be recombined into a single
 * {@code Hazard} result, regardless of {@code SourceSet.type()} for all
 * relevant {@code SourceSet}s.
 * 
 * <p>For reasons related to deaggregation, this class also retains the curves
 * for each {@code ClusterSource} in a {@code ClusterSourceSet}. Specifically,
 * deaggregation of cluster sources is not a straightforward decomposition of
 * contributions from individual sources and its helpful to have the total curve
 * for each {@code ClusterSource} available. Given the relatively specializaed
 * and infrequent use of {@code ClusterSource}s, this incurs little additional
 * overhead.
 *
 * @author Peter Powers
 */
final class HazardCurveSet {

  final SourceSet<? extends Source> sourceSet;
  final List<GroundMotions> hazardGroundMotionsList;
  final List<ClusterGroundMotions> clusterGroundMotionsList;
  final Map<Imt, List<Map<Gmm, XySequence>>> clusterCurveLists;
  final Map<Imt, Map<Gmm, XySequence>> curveMap;
  final Map<Imt, XySequence> totalCurves;

  // TODO separate references by what is needed for hazard vs deagg
  // deagg of cluster and system types requires us to hold onto some
  // objects created when generating hazard curves

  private HazardCurveSet(
      SourceSet<? extends Source> sourceSet,
      List<GroundMotions> hazardGroundMotionsList,
      List<ClusterGroundMotions> clusterGroundMotionsList,
      Map<Imt, List<Map<Gmm, XySequence>>> clusterCurveLists,
      Map<Imt, Map<Gmm, XySequence>> curveMap,
      Map<Imt, XySequence> totalCurves) {

    this.sourceSet = sourceSet;
    this.hazardGroundMotionsList = hazardGroundMotionsList;
    this.clusterGroundMotionsList = clusterGroundMotionsList;
    this.clusterCurveLists = clusterCurveLists;
    this.curveMap = curveMap;
    this.totalCurves = totalCurves;
  }

  static Builder builder(SourceSet<? extends Source> sourceSet,
      Map<Imt, XySequence> modelCurves) {
    return new Builder(sourceSet, modelCurves);
  }

  /*
   * A HazardCurveSet is relatively heavyweight wrt initializing its builder.
   * This provides a placeholder for functions where a HazardCurveSet is
   * required but for which there may be no HazardCurves because all sources
   * were out of range of a site. We only retain a reference to the original
   * source set.
   */
  static HazardCurveSet empty(SourceSet<? extends Source> sourceSet) {
    return new HazardCurveSet(sourceSet, null, null, null, null, null);
  }

  boolean isEmpty() {
    return totalCurves == null;
  }

  static class Builder {

    private static final String ID = "HazardCurveSet.Builder";
    private boolean built = false;

    private final Map<Imt, XySequence> modelCurves;

    private final SourceSet<? extends Source> sourceSet;
    private final List<GroundMotions> hazardGroundMotionsList;
    private final List<ClusterGroundMotions> clusterGroundMotionsList;
    private final Map<Imt, List<Map<Gmm, XySequence>>> clusterCurveLists;
    private final Map<Imt, Map<Gmm, XySequence>> curveMap;
    private final Map<Imt, XySequence> totalCurves;

    private Builder(
        SourceSet<? extends Source> sourceSet,
        Map<Imt, XySequence> modelCurves) {

      this.sourceSet = sourceSet;
      this.modelCurves = modelCurves;

      boolean cluster = sourceSet.type() == SourceType.CLUSTER;

      if (cluster) {
        hazardGroundMotionsList = null;
        clusterGroundMotionsList = new ArrayList<>();
        clusterCurveLists = new EnumMap<>(Imt.class);
        curveMap = new EnumMap<>(Imt.class);
      } else {
        hazardGroundMotionsList = new ArrayList<>();
        clusterGroundMotionsList = null;
        clusterCurveLists = null;
        curveMap = new EnumMap<>(Imt.class);
      }

      Set<Gmm> gmms = sourceSet.groundMotionModels().gmms();
      Set<Imt> imts = modelCurves.keySet();

      for (Imt imt : imts) {

        Map<Gmm, XySequence> gmmMap = new EnumMap<>(Gmm.class);
        for (Gmm gmm : gmms) {
          XySequence emptyCurve = emptyCopyOf(modelCurves.get(imt));
          gmmMap.put(gmm, emptyCurve);
        }
        curveMap.put(imt, gmmMap);

        if (cluster) {
          List<Map<Gmm, XySequence>> clusterCurveList = new ArrayList<>();
          clusterCurveLists.put(imt, clusterCurveList);

        }
      }
      totalCurves = new EnumMap<>(Imt.class);
    }

    Builder addCurves(HazardCurves curvesIn) {
      checkNotNull(hazardGroundMotionsList, "%s only supports ClusterCurves", ID);
      hazardGroundMotionsList.add(curvesIn.groundMotions);
      double distance = curvesIn.groundMotions.inputs.minDistance;
      Map<Gmm, Double> gmmWeightMap = sourceSet.groundMotionModels().gmmWeightMap(distance);
      // loop Imts based on what's been calculated
      for (Imt imt : curvesIn.curveMap.keySet()) {
        Map<Gmm, XySequence> curveMapIn = curvesIn.curveMap.get(imt);
        Map<Gmm, XySequence> curveMapBuild = curveMap.get(imt);
        // loop Gmms based on what's supported at this distance
        for (Gmm gmm : gmmWeightMap.keySet()) {
          double weight = gmmWeightMap.get(gmm) * sourceSet.weight();
          curveMapBuild.get(gmm).add(copyOf(curveMapIn.get(gmm)).multiply(weight));
        }
      }
      return this;
    }

    Builder addCurves(ClusterCurves curvesIn) {
      checkNotNull(clusterGroundMotionsList, "%s only supports HazardCurves", ID);
      clusterGroundMotionsList.add(curvesIn.clusterGroundMotions);
      double clusterWeight = curvesIn.clusterGroundMotions.parent.weight();
      double distance = curvesIn.clusterGroundMotions.minDistance;
      Map<Gmm, Double> gmmWeightMap = sourceSet.groundMotionModels().gmmWeightMap(distance);
      // loop Imts based on what's been calculated
      for (Imt imt : curvesIn.curveMap.keySet()) {
        Map<Gmm, XySequence> curveMapIn = curvesIn.curveMap.get(imt);
        Map<Gmm, XySequence> curveMapBuild = curveMap.get(imt);

        /*
         * Retain references to the total curve for each cluster source for
         * deaggregation in clusterCurveLists. These lists of maps by GMM will
         * contain only those curves for the GMMs approprate for the source-site
         * distance. When deaggreagting, the same distance cutoff will be
         * considered so retrieval of curves from the maps should never thrwo an
         * NPE.
         */

        Map<Gmm, XySequence> clusterCurves = new EnumMap<>(Gmm.class);
        // loop Gmms based on what's supported at this distance
        for (Gmm gmm : gmmWeightMap.keySet()) {
          double weight = gmmWeightMap.get(gmm) * sourceSet.weight() * clusterWeight;
          XySequence clusterCurve = copyOf(curveMapIn.get(gmm)).multiply(weight);
          curveMapBuild.get(gmm).add(clusterCurve);
          clusterCurves.put(gmm, clusterCurve);
        }
        clusterCurveLists.get(imt).add(clusterCurves);
      }
      return this;
    }

    HazardCurveSet build() {
      checkState(!built, "This %s instance has already been used", ID);
      built = true;
      computeFinal();
      return new HazardCurveSet(
          sourceSet,
          hazardGroundMotionsList,
          clusterGroundMotionsList,
          clusterCurveLists,
          curveMap,
          totalCurves);
    }

    /*
     * Create the final wieghted (Gmm) combined curve. The Gmm curves were
     * scaled by their weights while building (above).
     */
    private void computeFinal() {
      for (Entry<Imt, Map<Gmm, XySequence>> entry : curveMap.entrySet()) {
        Imt imt = entry.getKey();
        XySequence totalCurve = emptyCopyOf(modelCurves.get(imt));
        for (XySequence curve : entry.getValue().values()) {
          totalCurve.add(curve);
        }
        totalCurves.put(imt, immutableCopyOf(totalCurve));
      }
    }
  }

}
