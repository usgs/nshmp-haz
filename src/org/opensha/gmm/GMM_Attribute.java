package org.opensha.gmm;

import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_UNDERSCORE;

/**
 * Ground motion model (GMM) XML attributes.
 * @author Peter Powers
 */
@SuppressWarnings("javadoc")
public enum GMM_Attribute {
	
	ID,
	WEIGHT,
	MAX_DISTANCE;

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
