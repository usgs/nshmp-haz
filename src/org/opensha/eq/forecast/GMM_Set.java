package org.opensha.eq.forecast;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.opensha.data.DataUtils.validate;
import static org.opensha.data.DataUtils.validateWeights;

import java.util.Map;

import org.opensha.gmm.Gmm;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Range;
import com.google.common.primitives.Doubles;

/**
 * Wrapper class for GroundMotionModel instances that will be matched against
 * different {@code Source} types in hazard calculations. The use of the word
 * 'Set' in the class name implies the {@code Gmm}s in a {@code GMM_Set} will be
 * unique; this is guaranteeed by the internal use of {@code EnumMap}s.
 * 
 * <p>A {@code GMM_Set} can not be created directly; it may only be created by a
 * private parser.</p>
 * 
 * @author Peter Powers
 */
class GMM_Set {

	final Map<Gmm, Double> weightMapLo;
	final double maxDistLo;
	private final Map<Gmm, Double> weightMapHi;
	private final double maxDistHi;

	private final double[] uncValues;
	private final double[] uncWeights;

	GMM_Set(Map<Gmm, Double> weightMapLo, double maxDistLo, Map<Gmm, Double> weightMapHi,
		double maxDistHi, double[] uncValues, double[] uncWeights) {
		this.weightMapLo = weightMapLo;
		this.maxDistLo = maxDistLo;
		this.weightMapHi = weightMapHi;
		this.maxDistHi = maxDistHi;
		this.uncValues = uncValues;
		this.uncWeights = uncWeights;
	}

	// TODO clean
	// static createInstanceMap(Map<Gmm, Double> weightMap) {
	// Map<Gmm, GroundMotionModel> instanceMap = Maps.newEnumMap(Gmm.class);
	// for (Gmm gmm : weightMap.keySet()) {
	// instanceMap.put(gmm, gmm.instance(imt));
	// }
	// }

	/**
	 * Returns the maximum distance for which this calculator is applicable
	 * @return
	 */
	// public double maxDistance() {
	// return maxDistance;
	// }

	static class Builder {

		static final String ID = "GMM_Set.Builder";
		boolean built = false;

		private static final Range<Double> MAX_DIST_RANGE = Range.closed(50.0, 1000.0);

		private Map<Gmm, Double> gmmWtMapLo;
		private Map<Gmm, Double> gmmWtMapHi;
		private Double maxDistanceLo;
		private double maxDistanceHi;

		// optional
		private double[] uncValues;
		private double[] uncWeights;

		// leave maxDistanceHi as primitive unless validation required
		// at some later date; GMM_Set throws NPE if Double used

		Builder primaryModelMap(Map<Gmm, Double> gmmWtMap) {
			checkArgument(checkNotNull(gmmWtMap, "Map is null").size() > 0, "Map is empty");
			validateWeights(gmmWtMap.values());
			gmmWtMapLo = ImmutableMap.copyOf(gmmWtMap);
			return this;
		}

		Builder primaryMaxDistance(double maxDistance) {
			maxDistanceLo = validate(MAX_DIST_RANGE, "Max distance", maxDistance);
			return this;
		}

		Builder secondaryModelMap(Map<Gmm, Double> gmmWtMap) {
			checkArgument(checkNotNull(gmmWtMap, "Map is null").size() > 0, "Map is empty");
			validateWeights(gmmWtMap.values());
			gmmWtMapLo = ImmutableMap.copyOf(gmmWtMap);
			return this;
		}

		Builder secondaryMaxDistance(double maxDistance) {
			maxDistanceHi = validate(MAX_DIST_RANGE, "Max distance", maxDistance);
			return this;
		}

		Builder uncertainty(double[] values, double[] weights) {
			checkNotNull(values, "Values is null");
			checkArgument(values.length == 9 || values.length == 1,
				"Values must contain 1 or 9 values");
			checkArgument(checkNotNull(weights, "Weights are null").length == 3,
				"Weights must contain 3 values");
			uncValues = values;
			uncWeights = weights;
			return this;
		}

		void validateState(String id) {
			// at a minimum the '*Lo' fields must be set
			checkState(!built, "This %s instance as already been used", id);
			checkState(gmmWtMapLo != null, "%s primary weight map not set", id);
			checkState(maxDistanceLo != null, "%s primary max distance not set", id);
			if (uncValues != null) checkNotNull(uncWeights, "%s uncertainty weights not set", id);
			if (uncWeights != null) checkNotNull(uncValues, "%s uncertainty values not set", id);
			built = true;
		}

		GMM_Set build() {

			validateState(ID);
			try {
				GMM_Set gmmSet = new GMM_Set(gmmWtMapLo, maxDistanceLo, gmmWtMapHi, maxDistanceHi,
					uncWeights, uncValues);
				return gmmSet;
			} catch (Exception e) {
				e.printStackTrace();
				return null;
			}
		}

	}

}
