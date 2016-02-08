package org.opensha2.util;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Strings.padStart;
import static com.google.common.base.Strings.repeat;

import java.util.Map;

import com.google.common.base.Joiner;
import com.google.common.base.StandardSystemProperty;
import com.google.common.base.Strings;

/**
 * Miscellaneous {@code String} utilities.
 * 
 * @author Peter Powers
 */
public class TextUtils {

	public static final String NEWLINE = StandardSystemProperty.LINE_SEPARATOR.value();
	public static final int ALIGN_COL = 24;
	private static final int MAX_COL = 100;
	private static final int DELTA_COL = MAX_COL - ALIGN_COL - 2;
	private static final String INDENT = NEWLINE + repeat(" ", ALIGN_COL + 2);
	
	
	private static final Joiner.MapJoiner MAP_JOIN = Joiner.on(", ").withKeyValueSeparator(": ");

	private String toString(Map<?, ?> map) {
		return Parsing.addBrackets(MAP_JOIN.join(map));
	}
	
	public static <E extends Enum<E>> String format(E id) {
		return format(id.toString());
	}

	public static String format(String s) {
		return NEWLINE + padStart(s, ALIGN_COL, ' ') + ": ";
	}
	
	public static String wrap(String s) {
		return wrap(s, false);
	}
	
	/*
	 * Used internally; pad flag indents lines consistent with format(s)
	 */
	private static String wrap(String s, boolean pad) {
		if (s.length() <= DELTA_COL) return pad ? INDENT + s : s;
		StringBuilder sb = new StringBuilder();
		int lastCommaIndex = s.substring(0, DELTA_COL).lastIndexOf(',') + 1;
		if (pad) sb.append(INDENT);
		sb.append(s.substring(0, lastCommaIndex));
		sb.append(wrap(s.substring(lastCommaIndex).trim(), true));
		return sb.toString();
	}

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
