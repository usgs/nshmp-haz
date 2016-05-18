package org.opensha2.calc;

import static com.google.common.base.Preconditions.checkState;

import static org.opensha2.data.XySequence.immutableCopyOf;

import org.opensha2.data.XySequence;
import org.opensha2.gmm.Gmm;
import org.opensha2.gmm.Imt;

import java.util.EnumMap;
import java.util.Map;

/**
 * Container class for the combined hazard curves derived from the individual
 * {@code Source}s in a {@code ClusterSource}, one for each
 * {@code GroundMotionModel} used. The curves will have been scaled by source
 * and rupture weights, but not by {@code GroundMotionModel} weights.
 *
 * @author Peter Powers
 */
final class ClusterCurves {

  final ClusterGroundMotions clusterGroundMotions;
  final Map<Imt, Map<Gmm, XySequence>> curveMap;

  private ClusterCurves(ClusterGroundMotions clusterGroundMotions,
      Map<Imt, Map<Gmm, XySequence>> curveMap) {
    this.clusterGroundMotions = clusterGroundMotions;
    this.curveMap = curveMap;
  }

  static Builder builder(ClusterGroundMotions clusterGroundMotions) {
    return new Builder(clusterGroundMotions);
  }

  static class Builder {

    private static final String ID = "ClusterCurves.Builder";
    private boolean built = false;

    private final ClusterGroundMotions clusterGroundMotions;
    private final Map<Imt, Map<Gmm, XySequence>> curveMap;

    private Builder(ClusterGroundMotions clusterGroundMotions) {
      this.clusterGroundMotions = clusterGroundMotions;
      // look at first HazardGM to determine curve table dimensions
      GroundMotions model = clusterGroundMotions.get(0);
      curveMap = new EnumMap<>(Imt.class);
      for (Imt imt : model.means.keySet()) {
        Map<Gmm, XySequence> gmmMap = new EnumMap<>(Gmm.class);
        curveMap.put(imt, gmmMap);
      }
    }

    /* Makes an immutable copy of the supplied curve. */
    Builder addCurve(Imt imt, Gmm gmm, XySequence curve) {
      curveMap.get(imt).put(gmm, immutableCopyOf(curve));
      return this;
    }

    ClusterCurves build() {
      checkState(!built, "This %s instance has already been used", ID);
      // TODO check that all gmms have been set? it'll be difficult to
      // track whether all curves for all inputs have been added
      built = true;
      return new ClusterCurves(clusterGroundMotions, curveMap);
    }

  }

}
