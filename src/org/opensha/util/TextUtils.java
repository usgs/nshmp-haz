package org.opensha.util;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Strings;

/**
 * Miscellaneous {@code String} utilities.
 * 
 * @author Peter Powers
 */
public class TextUtils {

	/**
	 * Verifies that the supplied {@code String} is neither {@code null} or
	 * empty. Method returns the supplied value and can be used inline.
	 * @param name to verify
	 * @throws IllegalArgumentException if name is {@code null} or empty
	 */
	public static String validateName(String name) {
		checkArgument(!Strings.nullToEmpty(name).trim().isEmpty(), "Name may not be empty or null");
		return name;
	}
}
