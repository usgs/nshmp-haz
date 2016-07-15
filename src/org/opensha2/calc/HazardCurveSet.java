package org.opensha2.calc;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import static org.opensha2.data.XySequence.copyOf;
import static org.opensha2.data.XySequence.emptyCopyOf;
import static org.opensha2.data.XySequence.immutableCopyOf;

import org.opensha2.data.XySequence;
import org.opensha2.eq.model.Source;
import org.opensha2.eq.model.SourceSet;
import org.opensha2.eq.model.SourceType;
import org.opensha2.gmm.Gmm;
import org.opensha2.gmm.Imt;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

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
  final Map<Imt, Map<Gmm, List<XySequence>>> clusterCurveLists;
  final Map<Imt, Map<Gmm, XySequence>> curveMap;
  final Map<Imt, XySequence> totalCurves;

  private HazardCurveSet(
      SourceSet<? extends Source> sourceSet,
      List<GroundMotions> hazardGroundMotionsList,
      List<ClusterGroundMotions> clusterGroundMotionsList,
      Map<Imt, Map<Gmm, List<XySequence>>> clusterCurveLists,
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
    private final Map<Imt, Map<Gmm, List<XySequence>>> clusterCurveLists;
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
        curveMap.put(imt, gmmMap);
        for (Gmm gmm : gmms) {
          XySequence emptyCurve = emptyCopyOf(modelCurves.get(imt));
          gmmMap.put(gmm, emptyCurve);
        }

        if (cluster) {
          Map<Gmm, List<XySequence>> clusterCurveGmmMap = new EnumMap<>(Gmm.class);
          clusterCurveLists.put(imt, clusterCurveGmmMap);
          for (Gmm gmm : gmms) {
            List<XySequence> emptyCurveList = new ArrayList<>();
            clusterCurveGmmMap.put(gmm, emptyCurveList);
          }
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
      // loop Imts based on what's coming in
      for (Imt imt : curvesIn.curveMap.keySet()) {
        Map<Gmm, XySequence> curveMapIn = curvesIn.curveMap.get(imt);
        Map<Gmm, XySequence> curveMapBuild = curveMap.get(imt);

        /*
         * In standard curve aggregation, we're only concerned with the GMMs
         * applicable to the source given it's distance from a site. However,
         * when keeping track of the cluster source curves, we ultimately need
         * the cluster curve list to match the length of the cluster ground
         * motion list. If a source is beyond minDistance, some of the curves in
         * the list will be zero-valued given that some GMMs are not used
         * (i.e. weight = 0.0).
         */

        Map<Gmm, List<XySequence>> curveLists = clusterCurveLists.get(imt);
        Set<Gmm> gmmsForDistance = gmmWeightMap.keySet();
        
        for (Gmm gmm : curveLists.keySet()) {
          boolean includeGmm = gmmsForDistance.contains(gmm);
          double weight = clusterWeight * sourceSet.weight();
          weight = includeGmm ? weight * gmmWeightMap.get(gmm) : 0.0;
          XySequence curve = copyOf(curveMapIn.get(gmm)).multiply(weight);
          curveLists.get(gmm).add(curve);
          if (includeGmm) {
            curveMapBuild.get(gmm).add(curve);
          }
        }
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
