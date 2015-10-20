package org.opensha2.eq.model;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.opensha2.data.DataUtils.checkInRange;
import static org.opensha2.data.DataUtils.checkWeightSum;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.opensha2.gmm.Gmm;
import org.opensha2.gmm.GroundMotionModel;

import com.google.common.collect.Maps;
import com.google.common.collect.Range;

/**
 * Wrapper class for {@link GroundMotionModel}s associated with a
 * {@link SourceSet}. In addition to carrying {@code Map}s of
 * {@code GroundMotionModel}s and their associated weights, this class
 * encapsulates any additional epistemic uncertainty that should be applied to
 * the ground motions computed from these models, as well as model weight
 * variants associated with different distance scales.
 * 
 * <p>A {@code GmmSet} can not be created directly; it may only be created by a
 * private parser. Currently, although {@code hashCode} and {@code equals} are
 * consistent, they do not consider any supplied uncertainty model.</p>
 * 
 * @author Peter Powers
 */
public final class GmmSet {

	/*
	 * TODO There is a lot of null checking and handling in this class that
	 * would be better served by Optional
	 */

	private final Map<Gmm, Double> weightMapLo;
	private final double maxDistLo;
	private final Map<Gmm, Double> weightMapHi; // may be null
	private final double maxDistHi; // used by distance filters
	private final boolean singular;
	private final int hashCode;

	private final UncertType uncertainty;
	private final double epiValue;
	private final double[][] epiValues;
	private final double[] epiWeights;

	GmmSet(Map<Gmm, Double> weightMapLo, double maxDistLo, Map<Gmm, Double> weightMapHi,
			double maxDistHi, double[] epiValues, double[] epiWeights) {
		this.weightMapLo = weightMapLo;
		this.maxDistLo = maxDistLo;
		this.weightMapHi = weightMapHi;
		this.maxDistHi = (weightMapHi != null) ? maxDistHi : maxDistLo;
		this.singular = weightMapHi == null;

		this.hashCode = Objects.hash(
			this.weightMapLo, this.maxDistLo,
			this.weightMapHi, this.maxDistHi);

		uncertainty = (epiValues == null) ? UncertType.NONE : (epiValues.length == 1)
			? UncertType.SINGLE : UncertType.MULTI;

		this.epiWeights = epiWeights;
		if (uncertainty == UncertType.NONE) {
			this.epiValue = Double.NaN;
			this.epiValues = null;
		} else if (uncertainty == UncertType.SINGLE) {
			this.epiValue = epiValues[0];
			this.epiValues = null;
		} else {
			this.epiValue = Double.NaN;
			this.epiValues = initEpiValues(epiValues);
		}
	}

	/**
	 * The {@code Set} of {@link GroundMotionModel} identifiers.
	 */
	public Set<Gmm> gmms() {
		return weightMapLo.keySet();
	}

	/**
	 * The {@code Map} of {@link GroundMotionModel} identifiers and associated
	 * weights to use at a given {@code distance} from a {@code Site}.
	 * @param distance
	 */
	public Map<Gmm, Double> gmmWeightMap(double distance) {
		/*
		 * Note that below (maxDistance) is used by all distance filters, so
		 * here, as long as distance isn't < maxDistLo, weightMapHi will be
		 * used.
		 */
		return singular ? weightMapLo : distance <= maxDistLo ? weightMapLo : weightMapHi;
	}

	/**
	 * The maximum distance for which the contained {@link GroundMotionModel}s
	 * are applicable.
	 */
	public double maxDistance() {
		return maxDistHi;
	}

	@Override public int hashCode() {
		return hashCode;
	}

	@Override public boolean equals(Object obj) {
		if (this == obj) return true;
		if (!(obj instanceof GmmSet)) return false;
		GmmSet that = (GmmSet) obj;
		return Objects.equals(this.weightMapLo, that.weightMapLo) &&
			this.maxDistLo == that.maxDistLo &&
			Objects.equals(this.weightMapHi, that.weightMapHi) &&
			this.maxDistHi == that.maxDistHi;
	}

	private static double[][] initEpiValues(double[] v) {
		return new double[][] {
			{ v[0], v[1], v[2] },
			{ v[3], v[4], v[5] },
			{ v[6], v[7], v[8] }
		};
	}

	/*
	 * Returns the epistemic uncertainty for the supplied magnitude (M) and
	 * distance (D) that
	 */
	private double getUncertainty(double M, double D) {
		switch (uncertainty) {
			case MULTI:
				int mi = (M < 6) ? 0 : (M < 7) ? 1 : 2;
				int di = (D < 10) ? 0 : (D < 30) ? 1 : 2;
				return epiValues[di][mi];
			case SINGLE:
				return epiValue;
			default:
				return 0.0;
		}
	}

	static enum UncertType {
		NONE,
		SINGLE,
		MULTI;
	}

	/* Single use builder */
	static class Builder {

		static final String ID = "GmmSet.Builder";
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
		// at some later date; GmmSet throws NPE if Double used

		Builder primaryModelMap(Map<Gmm, Double> gmmWtMap) {
			checkArgument(checkNotNull(gmmWtMap, "Map is null").size() > 0, "Map is empty");
			checkWeightSum(gmmWtMap.values());
			gmmWtMapLo = Maps.immutableEnumMap(gmmWtMap);
			return this;
		}

		Builder primaryMaxDistance(double maxDistance) {
			maxDistanceLo = checkInRange(MAX_DIST_RANGE, "Max distance", maxDistance);
			return this;
		}

		Builder secondaryModelMap(Map<Gmm, Double> gmmWtMap) {
			checkArgument(checkNotNull(gmmWtMap, "Map is null").size() > 0, "Map is empty");
			checkWeightSum(gmmWtMap.values());
			gmmWtMapHi = Maps.immutableEnumMap(gmmWtMap);
			return this;
		}

		Builder secondaryMaxDistance(double maxDistance) {
			maxDistanceHi = checkInRange(MAX_DIST_RANGE, "Max distance", maxDistance);
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

			if (gmmWtMapHi != null) {
				// hi gmms must be same as or subset of lo gmms
				checkState(gmmWtMapLo.keySet().containsAll(gmmWtMapHi.keySet()),
					"%s secondary models must be a subset of primary models", id);
				// maxDistanceHi must also be set and greater than maxDistanceLo
				checkNotNull(maxDistanceHi,
					"%s secondary distance must be set for secondary models", id);
				checkState(maxDistanceHi > maxDistanceLo,
					"%s secondary distance [%s] ≤ primary distance [%s]", id, maxDistanceHi,
					maxDistanceLo);
			}

			if (uncValues != null) checkNotNull(uncWeights, "%s uncertainty weights not set", id);
			if (uncWeights != null) checkNotNull(uncValues, "%s uncertainty values not set", id);

			built = true;
		}

		GmmSet build() {

			validateState(ID);
			try {
				GmmSet gmmSet = new GmmSet(
					gmmWtMapLo, maxDistanceLo,
					gmmWtMapHi, maxDistanceHi,
					uncValues, uncWeights);
				return gmmSet;
			} catch (Exception e) {
				e.printStackTrace();
				return null;
			}
		}

	}

}
