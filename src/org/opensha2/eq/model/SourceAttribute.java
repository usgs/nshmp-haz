package org.opensha2.eq.model;

import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_UNDERSCORE;

import com.google.common.base.CaseFormat;

/**
 * HazardModel source XML attributes.
 * @author Peter Powers
 */
@SuppressWarnings("javadoc")
public enum SourceAttribute {
	NAME,
	ID,
	WEIGHT,
	INDEX,

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
	C_MAG,
	FLOATS,
	MAGS,
	RATES,
	
	/* Mag uncertainty specific */
	CUTOFF,
	DELTAS,
	WEIGHTS,
	COUNT,
	MO_BALANCE,
	SIGMA,
	
	/* Source model specific */
	MAG_DEPTH_MAP,
	MAX_DEPTH,
	FOCAL_MECH_MAP,
	RUPTURE_SCALING,
	RUPTURE_FLOATING,
	SURFACE_SPACING;
	

	/**
	 * Returns an {@code CaseFormat#LOWER_CAMEL} {@code String} representation
	 * of this {@code SourceAttribute}.
	 * @see CaseFormat
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
