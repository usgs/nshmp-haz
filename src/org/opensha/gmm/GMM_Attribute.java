package org.opensha.gmm;

import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_UNDERSCORE;

/**
 * Ground motion model (GMM) XML attributes.
 * @author Peter Powers
 */
@SuppressWarnings("javadoc")
public enum GMM_Attribute {
	
	NAME,
	WEIGHT,
	
	MAX_DISTANCE;
	
//	NAME,
//	WEIGHT,
//
//	DEPTH_MAP,
//	MECH_MAP,
//
//	/* Geometry specific */
//	STRIKE,
//	DIP,
//	WIDTH,
//	RAKE,
//	DEPTH,
//
//	/* MFD specific */
//	TYPE,
//	A,
//	M,
//	B,
//	M_MIN,
//	M_MAX,
//	D_MAG,
//	FLOATS,
//	MAG_SCALING,
//	MAGS,
//	RATES;

	/**
	 * Returns an {@code CaseFormat#LOWER_CAMEL} {@code String} representation
	 * of this {@code GMM_Attribute}.
	 */
	@Override
	public String toString() {
		return UPPER_UNDERSCORE.to(LOWER_CAMEL, name());
	}

	/**
	 * Converts supplied {@code String} to equivalent {@code GMM_Attribute}.
	 * Method expects a {@code String} with {@code CaseFormat#LOWER_CAMEL}
	 * @param s {@code String} to convert
	 * @return the corresponding {@code GMM_Attribute}
	 * @see CaseFormat
	 * @throws IllegalArgumentException if supplied {@code String} is
	 *         incorrectly formatted or no matching {@code GMM_Attribute} exists
	 */
	public static GMM_Attribute fromString(String s) {
		return valueOf(LOWER_CAMEL.to(UPPER_UNDERSCORE, s));
	}

}
