package org.opensha.eq.model;

import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_UNDERSCORE;

/**
 * HazardModel source XML attributes.
 * @author Peter Powers
 */
@SuppressWarnings("javadoc")
public enum SourceAttribute {
	NAME,
	WEIGHT,
	INDEX,

	MAG_DEPTH_MAP,
	FOCAL_MECH_MAP,

	/* Geometry specific */
	STRIKE,
	DIP,
	DIP_DIR,
	WIDTH,
	RAKE,
	DEPTH,
	LOWER_DEPTH,
	ASEIS,
	INDICES,

	/* MFD specific */
	TYPE,
	A,
	M,
	B,
	M_MIN,
	M_MAX,
	D_MAG,
	FLOATS,
	MAG_SCALING,
	MAGS,
	RATES,
	
	/* Mag uncertainty specific */
	CUTOFF,
	DELTAS,
	WEIGHTS,
	COUNT,
	MO_BALANCE,
	SIGMA;

	/**
	 * Returns an {@code CaseFormat#LOWER_CAMEL} {@code String} representation
	 * of this {@code SourceAttribute}.
	 */
	@Override
	public String toString() {
		return UPPER_UNDERSCORE.to(LOWER_CAMEL, name());
	}

	/**
	 * Converts supplied {@code String} to equivalent {@code SourceAttribute}.
	 * Method expects a {@code String} with {@code CaseFormat#LOWER_CAMEL}
	 * @param s {@code String} to convert
	 * @return the corresponding {@code SourceAttribute}
	 * @see CaseFormat
	 * @throws IllegalArgumentException if supplied {@code String} is
	 *         incorrectly formatted or no matching {@code SourceAttribute}
	 *         exists
	 */
	public static SourceAttribute fromString(String s) {
		return valueOf(LOWER_CAMEL.to(UPPER_UNDERSCORE, s));
	}

}
