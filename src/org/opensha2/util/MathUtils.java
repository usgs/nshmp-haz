package org.opensha.util;

import static java.lang.Math.sqrt;

/**
 * Miscellaneous utils that should be more appropriately located (TODO)
 * possibly rename to NSHMP_Utils
 * These are mostly ported from gov.usgs.earthquake.nshm.util
 * @author Peter Powers
 */
public final class MathUtils {

	// no instantiation
	private MathUtils() {}
	
	/** Multiplicative conversion from log (base-10) to natural log (base-e). */
	public static final double LOG_BASE_10_TO_E = 1.0 / 0.434294;
	// TODO this should really be more precise 1.0/Math.log10(Math.E)

	/** Precomputed square-root of 2. */
	public static final double SQRT_2 = Math.sqrt(2);

	/**
	 * Same as {@link Math#hypot(double, double)} without regard to under/over flow.
	 * @param v1 first value
	 * @param v2 second value
	 * @return the hypotenuse
	 * @see Math#hypot(double, double)
	 */
	public static double hypot(double v1, double v2) {
		return sqrt(v1 * v1 + v2 * v2);
	}

}
