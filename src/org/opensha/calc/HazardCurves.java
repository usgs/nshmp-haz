package org.opensha.calc;

import static com.google.common.base.Preconditions.checkState;

import java.util.EnumMap;
import java.util.Map;

import org.opensha.data.ArrayXY_Sequence;
import org.opensha.gmm.Gmm;

/**
 * Container class for the combined hazard curves derived from the
 * {@code Rupture}s in an individual {@code Source}, one for each
 * {@code GroundMotionModel} used. The curves will have been scaled by the
 * source and rupture weights, but not by {@code GroundMotionModel} weights.
 * 
 * @author Peter Powers
 */
class HazardCurves {

	final HazardGroundMotions groundMotions;
	final Map<Gmm, ArrayXY_Sequence> curveMap;

	private HazardCurves(HazardGroundMotions groundMotions, Map<Gmm, ArrayXY_Sequence> curveMap) {
		this.groundMotions = groundMotions;
		this.curveMap = curveMap;
	}

	static Builder builder(HazardGroundMotions groundMotions) {
		return new Builder(groundMotions);
	}

	static class Builder {

		private static final String ID = "HazardCurves.Builder";
		private boolean built = false;

		private final HazardGroundMotions groundMotions;
		private final Map<Gmm, ArrayXY_Sequence> curveMap;

		private Builder(HazardGroundMotions groundMotions) {
			this.groundMotions = groundMotions;
			curveMap = new EnumMap<>(Gmm.class);
		}
		
		Builder addCurve(Gmm gmm, ArrayXY_Sequence curve) {
			curveMap.put(gmm, curve);
			return this;
		}

		HazardCurves build() {
			checkState(!built, "This %s instance has already been used", ID);
			// TODO check that all gmms have been set? it'll be difficult to track whether
			// all curves for all inputs have been added
			built = true;
			return new HazardCurves(groundMotions, curveMap);
		}

	}

}
