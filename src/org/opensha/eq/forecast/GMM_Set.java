package org.opensha.eq.forecast;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.opensha.data.DataUtils.validate;
import static org.opensha.data.DataUtils.validateWeights;

import java.util.Map;

import org.opensha.gmm.GMM;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Range;

/**
 * Wrapper class for GroundMotionModel instances that will be matched against
 * different {@code Source} types in hazard calculations. The use of the word
 * 'Set' in the class name implies the {@code GMM}s in a {@code GMM_Set} will be
 * unique; this is guaranteeed by the internal use of {@code EnumMap}s.
 * 
 * <p>A {@code GMM_Set} can not be created directly; it may only be created
 * by a private parser.</p>
 * 
 * @author Peter Powers
 */
class GMM_Set {

	final Map<GMM, Double> weightMapLo;
	final double maxDistLo;
	private final Map<GMM, Double> weightMapHi;
	private final double maxDistHi;

	GMM_Set(Map<GMM, Double> weightMapLo, double maxDistLo, Map<GMM, Double> weightMapHi,
		double maxDistHi) {
		this.weightMapLo = weightMapLo;
		this.maxDistLo = maxDistLo;
		this.weightMapHi = weightMapHi;
		this.maxDistHi = maxDistHi;
	}
	
//	static createInstanceMap(Map<GMM, Double> weightMap) {
//		Map<GMM, GroundMotionModel> instanceMap = Maps.newEnumMap(GMM.class);
//		for (GMM gmm : weightMap.keySet()) {
//			instanceMap.put(gmm, gmm.instance(imt));
//		}
//	}

	/**
	 * Returns the maximum distance for which this calculator is applicable
	 * @return
	 */
//	public double maxDistance() {
//		return maxDistance;
//	}

	static class Builder {

		static final String ID = "GMM_Set.Builder";
		boolean built = false;

		private static final Range<Double> MAX_DIST_RANGE = Range.closed(50.0, 1000.0);

		private Map<GMM, Double> gmmWtMapLo;
		private Map<GMM, Double> gmmWtMapHi;
		private Double maxDistanceLo;
		private double maxDistanceHi;
		
		// leave maxDistanceHi as primitive unless validation required
		// at some later date; GMM_Set throws NPE if Double used

		Builder primaryModelMap(Map<GMM, Double> gmmWtMap) {
			checkArgument(checkNotNull(gmmWtMap, "Map is null").size() > 0, "Map is empty");
			validateWeights(gmmWtMap.values());
			gmmWtMapLo = ImmutableMap.copyOf(gmmWtMap);
			return this;
		}

		Builder primaryMaxDistance(double maxDistance) {
			maxDistanceLo = validate(MAX_DIST_RANGE, "Max distance", maxDistance);
			return this;
		}

		Builder secondaryModelMap(Map<GMM, Double> gmmWtMap) {
			checkArgument(checkNotNull(gmmWtMap, "Map is null").size() > 0, "Map is empty");
			validateWeights(gmmWtMap.values());
			gmmWtMapLo = ImmutableMap.copyOf(gmmWtMap);
			return this;
		}

		Builder secondaryMaxDistance(double maxDistance) {
			maxDistanceHi = validate(MAX_DIST_RANGE, "Max distance", maxDistance);
			return this;
		}

		void validateState(String mssgID) {
			// at a minimum the '*Lo' fields must be set
			checkState(!built, "This %s instance as already been used", mssgID);
			checkState(gmmWtMapLo != null, "%s primary weight map not set", mssgID);
			checkState(maxDistanceLo != null, "%s primary max distance not set", mssgID);
			built = true;
		}

		GMM_Set build() {
			
			validateState(ID);
			try {
				GMM_Set gmmSet = new GMM_Set(gmmWtMapLo, maxDistanceLo, gmmWtMapHi, maxDistanceHi);
				return gmmSet;
			} catch (Exception e) {
				e.printStackTrace();
				return null;
			}
		}

	}

}
