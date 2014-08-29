package org.opensha.calc;

import static com.google.common.base.Preconditions.checkState;

import java.util.EnumMap;
import java.util.Map;

import org.opensha.data.ArrayXY_Sequence;
import org.opensha.gmm.Gmm;

/**
 * Container class for the combined hazard curves derived from the individual
 * Sources in a ClusterSource, one for each {@code GroundMotionModel} used. The
 * curves will have been scaled by the source and rupture weights, but not by
 * {@code GroundMotionModel} weights.
 * 
 * @author Peter Powers
 */
final class ClusterCurves {

	final ClusterGroundMotions clusterGroundMotions;
	final Map<Gmm, ArrayXY_Sequence> curveMap;

	private ClusterCurves(ClusterGroundMotions clusterGroundMotions,
		Map<Gmm, ArrayXY_Sequence> curveMap) {
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
		private final Map<Gmm, ArrayXY_Sequence> curveMap;

		private Builder(ClusterGroundMotions clusterGroundMotions) {
			this.clusterGroundMotions = clusterGroundMotions;
			curveMap = new EnumMap<>(Gmm.class);
		}

		Builder addCurve(Gmm gmm, ArrayXY_Sequence curve) {
			curveMap.put(gmm, curve);
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
