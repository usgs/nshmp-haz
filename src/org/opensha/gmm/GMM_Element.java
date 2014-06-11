package org.opensha.gmm;

import static com.google.common.base.CaseFormat.UPPER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_UNDERSCORE;

import com.google.common.base.CaseFormat;

/**
 * Ground motion model (GMM) XML elements.
 * @author Peter Powers
 */
@SuppressWarnings("javadoc")
public enum GMM_Element {

	GROUND_MOTION_MODELS,
	MODEL_SET,
	MODEL;
			
	/**
	 * Returns a {@code CaseFormat#UPPER_CAMEL} {@code String} representation
	 * of this {@code GMM_Element}.
	 */
	@Override
	public String toString() { 
		return UPPER_UNDERSCORE.to(UPPER_CAMEL, name());
	}
	
	/**
	 * Converts supplied {@code String} to equivalent {@code GMM_Element}.
	 * Method expects a {@code String} with {@code CaseFormat#UPPER_CAMEL}
	 * @param s {@code String} to convert
	 * @return the corresponding {@code GMM_Element}
	 * @see CaseFormat
	 * @throws IllegalArgumentException if supplied {@code String} is
	 *         incorrectly formatted or no matching {@code GMM_Element} exists
	 */
	public static GMM_Element fromString(String s) {
		return valueOf(UPPER_CAMEL.to(UPPER_UNDERSCORE, s));
	}
		
}
