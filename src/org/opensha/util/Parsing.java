package org.opensha.util;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.opensha.data.DataUtils.validateWeights;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;

import org.opensha.data.DataUtils;
import org.w3c.dom.Element;
import org.xml.sax.Attributes;

import com.google.common.base.CharMatcher;
import com.google.common.base.Enums;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.primitives.Doubles;
import com.google.common.primitives.Ints;

/**
 * String, file, and XML parsing utilities.
 * 
 * @author Peter Powers
 */
public final class Parsing {

	private static final Joiner.MapJoiner MAP_JOIN = Joiner.on(',').withKeyValueSeparator(":");
	private static final Splitter.MapSplitter MAP_SPLIT = Splitter.on(',').trimResults()
		.withKeyValueSeparator(":");
	private static final Splitter.MapSplitter MAP_MAP_SPLIT = Splitter.on(';').trimResults()
		.withKeyValueSeparator("::");

	/**
	 * Converts a {@code Map<Enum, Double>} to a {@code String} with the form
	 * {@code [ENUM_1 : 0.8, ENUM_2 : 0.2]}.
	 * 
	 * @param map to convert
	 * @throws NullPointerException if {@code map} is {@code null}
	 * @return a {@code String} representation of the supplied {@code Map}
	 */
	public static <T extends Enum<T>> String enumValueMapToString(Map<T, Double> map) {
		Map<String, String> strMap = Maps.newHashMap();
		for (Entry<T, Double> entry : map.entrySet()) {
			strMap.put(entry.getKey().name(), Double.toString(entry.getValue()));
		}
		return addBrackets(MAP_JOIN.join(strMap));
	}

	/**
	 * Converts a {@code String} of the form
	 * {@code [ENUM_1 : 0.8, ENUM_2 : 0.2]} to a {@code Map<Enum, Double>}. The
	 * returned map is immutable.
	 * 
	 * @param s {@code String} to parse
	 * @param type {@code Class} to use as the key of the returned map
	 * @throws NullPointerException if {@code s} is {@code null}
	 * @throws IllegalArgumentException if {@code s} is malformed or empty, or
	 *         if weights do not sum to 1.0, within
	 *         {@link DataUtils#WEIGHT_TOLERANCE}
	 * @throws NumberFormatException if {@code s} does not contain parseable
	 *         {@code double} values
	 * @return a new {@code Map<Enum, Double>} of identifiers and weights
	 */
	public static <T extends Enum<T>> Map<T, Double> stringToEnumWeightMap(String s, Class<T> type) {
		Map<String, String> strMap = MAP_SPLIT.split(trimBrackets(checkNotNull(s)));
		EnumMap<T, Double> wtMap = Maps.newEnumMap(type);
		Function<String, T> keyFunc = Enums.stringConverter(type);
		for (Entry<String, String> entry : strMap.entrySet()) {
			wtMap.put(keyFunc.apply(entry.getKey().trim()),
				Doubles.stringConverter().convert(entry.getValue()));
		}
		validateWeights(wtMap.values());
		return wtMap;
	}

	/**
	 * Converts a {@code String} of the form
	 * {@code [6.5 :: [1.0 : 0.8, 5.0 : 0.2]; 10.0 :: [1.0 : 0.2, 5.0 : 0.8]]}
	 * to a {@code NavigableMap<Double, Map<Double, Double>}. The returned map
	 * and nested maps are immutable and should only be accessed via
	 * {@code Map.entrySet()} references or using keys derived from
	 * {@code Map.keySet()} as {@code Double} comparisons are inherently
	 * problematic.
	 * 
	 * @param s {@code String} to parse
	 * @throws NullPointerException if {@code s} is {@code null}
	 * @throws IllegalArgumentException if {@code s} is malformed or empty, or
	 *         if weights do not sum to 1.0, within
	 *         {@link DataUtils#WEIGHT_TOLERANCE}
	 * @throws NumberFormatException if {@code s} does not contain parseable
	 *         {@code double} values
	 * @return a new {@code Map<Double, Double>} of values and their weights
	 */
	public static NavigableMap<Double, Map<Double, Double>> stringToValueValueWeightMap(String s) {
		Map<String, String> strMap = MAP_MAP_SPLIT.split(trimBrackets(checkNotNull(s)));
		ImmutableSortedMap.Builder<Double, Map<Double, Double>> builder = ImmutableSortedMap
			.naturalOrder();
		for (Entry<String, String> entry : strMap.entrySet()) {
			double key = Doubles.stringConverter().convert(entry.getKey());
			builder.put(key, stringToValueWeightMap(entry.getValue()));
		}
		return builder.build();
	}

	/**
	 * Converts a {@code String} of the form {@code [1.0 : 0.4, 2.0 : 0.6]} to a
	 * {@code NavigableMap<Double, Double>}. The returned map is immutable and
	 * should only be accessed via {@code Map.entrySet()} references or using
	 * keys derived from {@code Map.keySet()} as {@code Double} comparisons are
	 * inherently problematic.
	 * 
	 * @param s {@code String} to parse
	 * @throws NullPointerException if {@code s} is {@code null}
	 * @throws IllegalArgumentException if {@code s} is malformed or if weights
	 *         do not sum to 1.0, within {@link DataUtils#WEIGHT_TOLERANCE}
	 * @throws NumberFormatException if {@code s} does not contain parseable
	 *         {@code double} values
	 * @return a new {@code Map<Double, Double>} of values and their weights
	 */
	public static NavigableMap<Double, Double> stringToValueWeightMap(String s) {
		Map<String, String> strMap = MAP_SPLIT.split(trimBrackets(checkNotNull(s)));
		ImmutableSortedMap.Builder<Double, Double> builder = ImmutableSortedMap.naturalOrder();
		for (Entry<String, String> entry : strMap.entrySet()) {
			double key = Doubles.stringConverter().convert(entry.getKey());
			double value = Doubles.stringConverter().convert(entry.getValue());
			builder.put(key, value);
		}
		NavigableMap<Double, Double> valMap = builder.build();
		validateWeights(valMap.values());
		return valMap;
	}

	/**
	 * Dumps SAX {@code Attributes} into a name-value
	 * {@code Map<String, String>} ({@code Attributes} are stateful when parsing
	 * XML).
	 * 
	 * @param atts to store in {@code Map}
	 * @return a {@code Map} of name-value XML attribute pairs
	 * @throws NullPointerException if supplied {@code atts} are {@code null}
	 */
	public static Map<String, String> toMap(Attributes atts) {
		Map<String, String> attMap = Maps.newHashMap();
		for (int i = 0; i < checkNotNull(atts).getLength(); i++) {
			attMap.put(atts.getQName(i), atts.getValue(i));
		}
		return attMap;
	}

	/**
	 * Add an attribute with an {@code Enum.toString()} name and {@code String}
	 * value to an XML {@link Element}.
	 * 
	 * @param id the name identifier of the attribute
	 * @param value the value of the attribute
	 * @param parent the {@code Element} to add attribute to
	 */
	public static void addAttribute(Enum<?> id, String value, Element parent) {
		addAttribute(id.toString(), value, parent);
	}

	/**
	 * Add an attribute with an {@code Enum.toString()} name and
	 * {@code Enum.name()} value to an XML {@link Element}.
	 * 
	 * @param id the name identifier of the attribute
	 * @param value the value of the attribute
	 * @param parent the {@code Element} to add attribute to
	 */
	public static void addAttribute(Enum<?> id, Enum<?> value, Element parent) {
		addAttribute(id.toString(), value.name(), parent);
	}

	/**
	 * Add an attribute with an {@code Enum.toString()} name and
	 * {@code Boolean.toString()} value to an XML {@link Element}.
	 * 
	 * @param id the name identifier of the attribute
	 * @param value the value of the attribute
	 * @param parent the {@code Element} to add attribute to
	 */
	public static void addAttribute(Enum<?> id, boolean value, Element parent) {
		addAttribute(id.toString(), Boolean.toString(value), parent);
	}

	/**
	 * Add an attribute with an {@code Enum.toString()} name and
	 * {@code Double.toString()} value to an XML {@link Element}.
	 * 
	 * @param id the name identifier of the attribute
	 * @param value the value of the attribute
	 * @param parent the {@code Element} to add attribute to
	 */
	public static void addAttribute(Enum<?> id, double value, Element parent) {
		addAttribute(id.toString(), Double.toString(value), parent);
	}

	/**
	 * Add an attribute with an {@code Enum.toString()} name and
	 * {@code Integer.toString()} value to an XML {@link Element}.
	 * 
	 * @param id the name identifier of the attribute
	 * @param value the value of the attribute
	 * @param parent the {@code Element} to add attribute to
	 */
	public static void addAttribute(Enum<?> id, int value, Element parent) {
		addAttribute(id.toString(), Integer.toString(value), parent);
	}

	/**
	 * Add an attribute with an {@code Enum.toString()} name and
	 * {@code Arrays.toString(double[])} value to an XML {@link Element}.
	 * 
	 * @param id the name identifier of the attribute
	 * @param values the value of the attribute
	 * @param parent the {@code Element} to add attribute to
	 */
	public static void addAttribute(Enum<?> id, double[] values, Element parent) {
		addAttribute(id.toString(), Arrays.toString(values), parent);
	}

	/**
	 * Add an attribute with an {@code Enum.toString()} name and formatted
	 * {@code double} value to an XML {@link Element}. Note that extra leading
	 * and trailing zeros are removed from the formatted value per
	 * {@link #stripZeros(String)}.
	 * 
	 * @param id the attribute name identifier
	 * @param value the {@code double} value of the attribute
	 * @param format a format string
	 * @param parent element to add attribute to
	 * @see String#format(String, Object...)
	 */
	public static void addAttribute(Enum<?> id, double value, String format, Element parent) {
		addAttribute(id.toString(), stripZeros(String.format(format, value)), parent);
	}

	private static void addAttribute(String name, String value, Element parent) {
		parent.setAttribute(name, value);
	}

	/**
	 * Add a child {@link Element} to a parent and return a reference to the
	 * child.
	 * 
	 * @param id the {@code Element} name identifier
	 * @param parent
	 */
	public static Element addElement(Enum<?> id, Element parent) {
		return addElement(id.toString(), parent);
	}

	private static Element addElement(String name, Element parent) {
		Element e = parent.getOwnerDocument().createElement(name);
		parent.appendChild(e);
		return e;
	}

	/**
	 * For use by source XML parsers. Reads the attribute value associated with
	 * the attribute name given by {@code id} as a {@code boolean}. Method
	 * explicitely checks that a case-insensitive value of "true" or "false" is
	 * supplied (as opposed to simply defaulting to {@code false}.
	 * 
	 * @param id the {@code enum} attribute identifier
	 * @param atts a SAX {@code Attributes} container
	 * @return the value of the attribute as a {@code boolean}
	 * @throws NullPointerException if {@code id} or {@code atts} are
	 *         {@code null}, or no attribute for the specified {@code id} exists
	 * @throws IllegalArgumentException if the attribute value can not be parsed
	 *         to a {@code boolean}
	 */
	public static boolean readBoolean(Enum<?> id, Attributes atts) {
		String idStr = checkNotNull(id).toString();
		String valStr = checkNotNull(atts).getValue(idStr);
		checkNotNull(valStr, "Missing attribute '%s'", id.toString());
		checkArgument(valStr.equalsIgnoreCase("true") || valStr.equalsIgnoreCase("false"),
			"Unparseable attribute " + id.toString() + "=\"" + valStr + "\"");
		return Boolean.valueOf(valStr);
	}

	/**
	 * For use by source XML parsers. Reads the attribute value associated with
	 * the attribute name given by {@code id} as a {@code double}.
	 * 
	 * @param id the {@code enum} attribute identifier
	 * @param atts a SAX {@code Attributes} container
	 * @return the value of the attribute as a {@code double}
	 * @throws NullPointerException if {@code id} or {@code atts} are
	 *         {@code null}, or no attribute for the specified {@code id} exists
	 * @throws IllegalArgumentException if the attribute value can not be parsed
	 *         to a {@code double}
	 */
	public static double readDouble(Enum<?> id, Attributes atts) {
		String idStr = checkNotNull(id).toString();
		String valStr = checkNotNull(atts).getValue(idStr);
		try {
			return Double.valueOf(checkNotNull(valStr, "Missing attribute '%s'", id));
		} catch (NumberFormatException nfe) {
			throw new IllegalArgumentException("Unparseable attribute " + id + "=\"" +
				atts.getValue(idStr) + "\"");
		}
	}

	/**
	 * For use by source XML parsers. Reads the attribute value associated with
	 * the attribute name given by {@code id} as a {@code double[]}.
	 * 
	 * @param id the {@code enum} attribute identifier
	 * @param atts a SAX {@code Attributes} container
	 * @return the value of the attribute as a {@code double[]}
	 * @throws NullPointerException if {@code id} or {@code atts} are
	 *         {@code null}, or no attribute for the specified {@code id} exists
	 * @throws IllegalArgumentException if the attribute value can not be parsed
	 *         to a {@code double}
	 */
	public static double[] readDoubleArray(Enum<?> id, Attributes atts) {
		String idStr = checkNotNull(id).toString();
		String valStr = checkNotNull(atts).getValue(idStr);
		try {
			return toDoubleArray(checkNotNull(valStr, "Missing attribute '%s'", id));
		} catch (NumberFormatException nfe) {
			throw new IllegalArgumentException("Unparseable value in " + id + "=\"" +
				atts.getValue(idStr) + "\"");
		}
	}

	/**
	 * For use by source XML parsers. Reads the attribute value associated with
	 * the attribute name given by {@code id} as a {@code String}.
	 * 
	 * @param id the {@code enum} attribute identifier
	 * @param atts a SAX {@code Attributes} container
	 * @return the value of the attribute as a {@code String}
	 * @throws NullPointerException if {@code id} or {@code atts} are
	 *         {@code null}, or no attribute for the specified {@code id}
	 *         exsists
	 */
	public static String readString(Enum<?> id, Attributes atts) {
		String idStr = checkNotNull(id).toString();
		String valStr = checkNotNull(atts).getValue(idStr);
		return checkNotNull(valStr, "Missing attribute '%s'", id);
	}

	/**
	 * For use by source XML parsers. Reads the attribute value associated with
	 * the attribute name given by {@code id} as an {@code Enum}.
	 * 
	 * @param id the {@code enum} attribute identifier
	 * @param atts a SAX {@code Attributes} container
	 * @param type {@code class} of {@code enum} to return
	 * @return the value of the attribute as a {@code String}
	 * @throws NullPointerException if {@code id} or {@code atts} are
	 *         {@code null}, or no attribute for the specified {@code id}
	 *         exsists
	 */
	public static <T extends Enum<T>> T readEnum(Enum<?> id, Attributes atts, Class<T> type) {
		return Enum.valueOf(type, readString(id, atts));
	}

	// public static List<String> toStringList(String s) {
	// return FluentIterable.from(SPLIT_SPACE.split(s)).toList();
	// }

	/**
	 * Converts a whitespace-delimited {@code String} to a {@code List<Double>}.
	 * @param s
	 * @return a {@code List} of {@code Double}s
	 * @throws NumberFormatException if any parts of {@code s} are unparseable
	 */
	// public static List<Double> toDoubleList(String s) {
	// return
	// FluentIterable.from(SPLIT_SPACE.split(s)).transform(doubleValueFunction()).toList();
	// }

	// TODO Joiners should take Object <?>
	// public static String joinOnSpaces(Iterable<String> parts) {
	// return JOIN_SPACE.join(parts);
	// }

	// public static String joinOnDashes(Iterable<String> parts) {
	// return JOIN_DASH.join(parts);
	// }

	/**
	 * Join the {@code String} representation of {@code parts} in a single
	 * comma-delimited {@code String}.
	 * @param parts to join
	 */
	// public static String joinOnCommas(Iterable<?> parts) {
	// return JOIN_COMMA.join(parts);
	// }

	/**
	 * Split a {@code String} into parts on commas (',').
	 * @param s {@code String} to split
	 */
	// public static Iterable<String> splitOnCommas(String s) {
	// return SPLIT_COMMA.split(s);
	// }

	/**
	 * Split a {@code String} into a {@code List<String>} on commas (',').
	 * @param s {@code String} to split
	 */
	// public static List<String> splitOnCommasToList(String s) {
	// return FluentIterable.from(splitOnCommas(s)).toList();
	// }

	/**
	 * Split a {@code String} into a {@code List<Double>} on commas (',').
	 * @param s {@code String} to split
	 */
	// public static List<Double> splitOnCommasToDoubleList(String s) {
	// return
	// FluentIterable.from(splitOnCommas(s)).transform(doubleValueFunction()).toList();
	// }

	/**
	 * Split a {@code String} into parts on whitespace.
	 * @param s {@code String} to split
	 * @see CharMatcher#WHITESPACE
	 */
	// public static Iterable<String> splitOnSpaces(String s) {
	// return SPLIT_SPACE.split(s);
	// }

	/**
	 * Split a {@code String} into parts on underscores ('_').
	 * @param s {@code String} to split
	 */
	// public static Iterable<String> splitOnUnderscore(String s) {
	// return SPLIT_UNDERSCORE.split(s);
	// }

	/**
	 * Split a {@code String} into parts on forward slashes ('/').
	 * @param s {@code String} to split
	 */
	// public static Iterable<String> splitOnSlash(String s) {
	// return SPLIT_SLASH.split(s);
	// }

	/**
	 * Split a {@code String} into a {@code List<String>} on forward slashes
	 * ('/').
	 * @param s {@code String} to split
	 */
	// public static List<String> splitOnSlashesToList(String s) {
	// return FluentIterable.from(splitOnSlash(s)).toList();
	// }

	/**
	 * Split a {@code String} into parts on dashes ('-').
	 * @param s {@code String} to split
	 */
	// public static Iterable<String> splitOnDash(String s) {
	// return SPLIT_DASH.split(s);
	// }

	/**
	 * Returns a {@code String} containing the string representation of each of
	 * {@code parts} joined with {@code delimiter}.
	 * 
	 * @param parts to join
	 * @param delimiter the {@link Delimiter} to join on
	 * @see Joiner
	 */
	public static String join(Iterable<?> parts, Delimiter delimiter) {
		return delimiter.joiner().join(parts);
	}

	/**
	 * Splits {@code sequence} into {@code String} components and makes them
	 * available through a (possibly-lazy) {@code Iterator}.
	 * 
	 * @param sequence the sequence of characters to split
	 * @param delimiter the {@link Delimiter} to split on
	 * @see Splitter
	 */
	public static Iterable<String> split(CharSequence sequence, Delimiter delimiter) {
		return delimiter.splitter().split(sequence);
	}

	/**
	 * Splits {@code sequence} into {@code String} components and makes them
	 * available through an immutable {@code List}.
	 * 
	 * @param sequence the sequence of characters to split
	 * @param delimiter the {@link Delimiter} to split on
	 */
	public static List<String> splitToList(CharSequence sequence, Delimiter delimiter) {
		return delimiter.splitter().splitToList(sequence);
	}

	/**
	 * Splits {@code sequence} into {@code Double} components and makes them
	 * available through an immutable {@code List}.
	 * 
	 * @param sequence the sequence of characters to split
	 * @param delimiter the {@link Delimiter} to split on
	 */
	public static List<Double> splitToDoubleList(CharSequence sequence, Delimiter delimiter) {
		return FluentIterable.from(split(sequence, delimiter)).transform(Doubles.stringConverter())
			.toList();
	}

	/**
	 * Delimiter identifiers, each of which can provide a {@link Joiner} and
	 * {@link Splitter}.
	 */
	public enum Delimiter {

		/** Colon (':') delimiter. */
		COLON(':'),

		/** Comma (',') delimiter. */
		COMMA(','),

		/** Dash ('-') delimiter. */
		DASH('-'),

		/** Forward-slash ('/') delimiter. */
		SLASH('/'),

		/**
		 * Whitespace (' ') delimiter.
		 * @see CharMatcher#WHITESPACE
		 */
		SPACE(' ', CharMatcher.WHITESPACE),

		/** Underscore ('_') delimiter. */
		UNDERSCORE('_');

		private Joiner joiner;
		private Splitter splitter;

		private Delimiter(char separator) {
			joiner = Joiner.on(separator).skipNulls();
			splitter = Splitter.on(separator).omitEmptyStrings().trimResults();
		}

		private Delimiter(char joinSeparator, CharMatcher splitMatcher) {
			joiner = Joiner.on(joinSeparator).skipNulls();
			splitter = Splitter.on(splitMatcher).omitEmptyStrings().trimResults();
		}

		/**
		 * Returns a null-skipping {@link Joiner} on this {@code Delimiter}.
		 * @see Joiner#skipNulls()
		 */
		public Joiner joiner() {
			return joiner;
		}

		/**
		 * Returns an empty-string-omitting and result-trimming {@link Splitter}
		 * on this {@code Delimiter}.
		 * 
		 * @see Splitter#omitEmptyStrings()
		 * @see Splitter#trimResults()
		 */
		public Splitter splitter() {
			return splitter;
		}
	}

	/**
	 * Convert an {@code Enum.name()} to a presentation friendly {@code String}
	 * appropriate for presentation (e.g. a UI control label). Method
	 * substitutes underscores with spaces.
	 * 
	 * @param e {@code Enum} to generate label for
	 * @return the label
	 */
	public static String enumLabelWithSpaces(Enum<? extends Enum<?>> e) {
		return join(splitEnum(e), Delimiter.SPACE);
	}

	/**
	 * Convert an {@code Enum.name()} to a presentation friendly {@code String}
	 * appropriate for presentation (e.g. a UI control label). Method
	 * substitutes underscores with dashes.
	 * 
	 * @param e {@code Enum} to generate label for
	 * @return the label
	 */
	public static String enumLabelWithDashes(Enum<? extends Enum<?>> e) {
		return join(splitEnum(e), Delimiter.DASH);
	}

	/* Splits and capitalizes an enum.name() */
	private static List<String> splitEnum(Enum<? extends Enum<?>> e) {
		Iterable<String> sources = split(e.name(), Delimiter.UNDERSCORE);
		List<String> results = Lists.newArrayList();
		for (String s : sources) {
			results.add(capitalize(s));
		}
		return results;
	}

	/**
	 * Capitalizes supplied {@code String} converting the first {@code char} to
	 * uppercase and all subsequent {@code char}s to lowercase.
	 * 
	 * @param s {@code String} to convert
	 * @return the capitalized {@code String}
	 */
	public static String capitalize(String s) {
		return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
	}

	/**
	 * Converts a bracketed and comma-delimited string of {@code Number}s (e.g.
	 * [1.0, 2.0, 3.0] to a {@code double[]}. This is the reverse of
	 * {@code Arrays.toString(double[])} and {@code List<Double>.toString()}
	 * 
	 * @param s {@code String} to convert
	 * @return a {@code double} array
	 */
	public static double[] toDoubleArray(String s) {
		return Doubles.toArray(FluentIterable.from(split(trimBrackets(s), Delimiter.COMMA))
			.transform(Doubles.stringConverter()).toList());
	}

	/**
	 * Converts a {@code Collection} of {@code Double}s to a {@code String} of
	 * the same format returned by {@code Arrays.toString(double[])} and
	 * {@code List<Double>>.toString()}, but will format the values using the
	 * supplied {@code format} {@code String}. The supplied {@code format}
	 * should match that expected by {@code String.format(String, Object...)}
	 * 
	 * @param values to convert
	 * @param format a format string
	 * @return a formatted {@code String} representation of the supplied values
	 */
	public static String toString(Collection<Double> values, String format) {
		return addBrackets(join(Iterables.transform(values, new FormatDoubleFunction(format)),
			Delimiter.COMMA));
	}

	/**
	 * Converts an ordered list of non-repeating {@code Integer}s to a compact
	 * {@code String} form. For example, the
	 * {@code List<Integer>.toString() = "[1, 2, 3, 4, 10, 19, 18, 17, 16]"}
	 * would instead be written as {@code "[[1:4],10,[19:16]]"}
	 * @param values to convert
	 * @return a compact {@code String} representation of the supplied values
	 * @throws IllegalArgumentException if {@code values} contains adjacent
	 *         repeating values
	 * @throws IllegalArgumentException if {@code values} is empty
	 * @see #rangeStringToIntList(String)
	 */
	public static String intListToRangeString(List<Integer> values) {
		checkArgument(checkNotNull(values).size() > 0, "values may not be empty");
		// TODO do test for list containing single value
		List<int[]> ranges = Lists.newArrayList();
		int start = values.get(0);
		int end = values.get(0);
		boolean buildingRange = false;
		boolean dir = true;

		for (int i = 1; i < values.size(); i++) {
			int current = values.get(i);
			checkArgument(current != end, "repeating value %s in %s", current, values);
			boolean currentDir = current > end;
			boolean terminateRange =
			// step > 1
			(Math.abs(current - end) != 1) ||
			// direction change
				(buildingRange && currentDir != dir) ||
				// end of list
				(i == values.size() - 1);

			if (terminateRange) {
				ranges.add((i == values.size() - 1) ? new int[] { start, current }
					: new int[] { start, end });
				start = current;
				end = current;
				buildingRange = false;
				continue;
			}

			// starting or continuing new range
			buildingRange = true;
			dir = currentDir;
			end = current;
		}

		return addBrackets(join(Iterables.transform(ranges, IntArrayToString.INSTANCE),
			Delimiter.COMMA));
	}

	/**
	 * Complement of {@link #intListToRangeString} converts a {@code String} of
	 * the form {@code "[[1:4],10,[19:16]]"} to an ordered {@code List<Integer>}
	 * (e.g. {@code [1, 2, 3, 4, 10, 19, 18, 17, 16]}). This method should
	 * really only be called with {@code String}s created by
	 * {@link #intListToRangeString}, otherwise results are undefined.
	 * @param s {@code String} to convert
	 * @return the List<Integer> imlied by the supplied {@code String}
	 * @see #intListToRangeString
	 */
	public static List<Integer> rangeStringToIntList(String s) {
		Iterable<int[]> values = Iterables.transform(split(trimBrackets(s), Delimiter.COMMA),
			StringToIntArray.INSTANCE);
		return Ints.asList(Ints.concat(Iterables.toArray(values, int[].class)));
	}

	// internal use only - no argument checking
	// writes 2-element int[]s as '[a:b]' or just 'a' if a==b
	private enum IntArrayToString implements Function<int[], String> {
		INSTANCE;
		@Override public String apply(int[] ints) {
			return (ints[0] == ints[1]) ? Integer.toString(ints[0]) : addBrackets(ints[0] + ":" +
				ints[1]);
		}
	}

	// close complement of above, converts IntArrayToStrings to List<Integer>s
	private enum StringToIntArray implements Function<String, int[]> {
		INSTANCE;
		@Override public int[] apply(String s) {
			if (s.startsWith("[")) {
				Iterator<Integer> rangeIt = Iterators.transform(
					split(trimBrackets(s), Delimiter.COLON).iterator(), Ints.stringConverter());
				return DataUtils.indices(rangeIt.next(), rangeIt.next());
			}
			return new int[] { Integer.valueOf(s) };
		}
	}

	/**
	 * Strip trailing zeros of a decimal number that has already been formatted
	 * as a {@code String}. Method will leave a single zero after a decimal.
	 * Method will not alter exponential forms, which may have a critical '0' in
	 * last position.
	 * 
	 * @param s {@code String} to clean
	 * @return the cleaned {@code String}
	 */
	public static String stripZeros(String s) {
		if (s.charAt(1) == '.') return s;
		s = s.replaceAll("0*$", "");
		if (s.endsWith(".")) s += "0";
		return s;
	}

	/*
	 * Trims the supplied string and then removes the first and last chars. At a
	 * minimum, this method will return an empty string
	 */
	static String trimBrackets(String s) {
		String trimmed = s.trim();
		checkArgument(trimmed.length() > 1, "\"%s\" is too short", s);
		return trimmed.substring(1, trimmed.length() - 1);
	}

	/*
	 * Adds brackets to the supplied string.
	 */
	static String addBrackets(String s) {
		return '[' + s + ']';
	}

	private static final Splitter.MapSplitter QUERY_SPLITTER = Splitter.on('&').trimResults()
		.withKeyValueSeparator('=');

	private static final Joiner.MapJoiner QUERY_JOINER = Joiner.on('&').withKeyValueSeparator("=");

	/**
	 * Convert a {@code Map} to a {@code URL} name-value pair query
	 * {@code String}.
	 * @param params parameter map to convert
	 */
	public static String mapToQuery(Map<String, String> params) {
		return "?" + QUERY_JOINER.join(params);
	}

	/**
	 * Convert a {@code URL} name-value pair query {@code String} to a
	 * {@code Map}.
	 * @param query {@code String} to convert
	 */
	public static Map<String, String> queryToMap(String query) {
		if (query.startsWith("?")) query = query.substring(1);
		return QUERY_SPLITTER.split(query);
	}

	/**
	 * Read and return the {@code int} at {@code position} in a space-delimited
	 * string.
	 * 
	 * @param s the {@code String} to read from
	 * @param position the position to read
	 */
	public static int readInt(String s, int position) {
		return Integer.valueOf(splitToList(s, Delimiter.SPACE).get(position));
	}

	/**
	 * Read and return the {@code double} at {@code position} in a
	 * space-delimited string.
	 * 
	 * @param s the {@code String} to read from
	 * @param position the position to read
	 */
	public static double readDouble(String s, int position) {
		return Double.valueOf(splitToList(s, Delimiter.SPACE).get(position));
	}

	/**
	 * Strip a trailing comment from a string that starts with the supplied
	 * character and return the cleaned string.
	 * 
	 * @param s the string to process
	 * @param c the comment indicator character
	 */
	public static String stripComment(String s, char c) {
		int idx = s.indexOf(c);
		return idx != -1 ? s.substring(0, idx) : s;
	}

	/**
	 * Read the specified number of strings from an {@code Iterator}
	 * and return them as a {@code List}.
	 * 
	 * @param it the string {@code Iterator} to read from
	 * @param n the number of strings to read
	 */
	public static List<String> toLineList(Iterator<String> it, int n) {
		List<String> lines = new ArrayList<String>();
		for (int i = 0; i < n; i++) {
			lines.add(it.next());
		}
		return lines;
	}

	// /**
	// * Instance of a {@code Function} that parses a {@code String} to
	// * {@code Double} using {@link Double#valueOf(String)} throwing
	// * {@code NumberFormatException}s and {@code NullPointerException}s for
	// * invalid and {@code null} arguments. The returned {@code Function}s
	// * {@code apply(String)} method first {@code trim()s} the supplied
	// * {@code String}.
	// * @return a new {@code String} to {@code Double} conversion
	// * {@code Function}
	// */
	// // public static Function<String, Double> doubleValueFunction() {
	// // return DoubleValueOfFunction.INSTANCE;
	// // }
	//
	// /**
	// * Instance of a {@code Function} that parses a {@code String} to
	// * {@code Integer} using {@link Integer#valueOf(String)} throwing
	// * {@code NumberFormatException}s and {@code NullPointerException}s for
	// * invalid and {@code null} arguments. The returned {@code Function}s
	// * {@code apply(String)} method first {@code trim()}s the supplied
	// * {@code String}.
	// * @return a new {@code String} to {@code Double} conversion
	// * {@code Function}
	// */
	// public static Function<String, Integer> intValueFunction() {
	// return IntegerValueOfFunction.INSTANCE;
	// }
	//

	// public static Function<Double, String> formatDoubleFunction(String
	// format) {
	// return new FormatDoubleFunction(format);
	// }

	//
	// private enum DoubleValueOfFunction implements Function<String, Double> {
	// INSTANCE;
	// @Override public Double apply(String s) {
	// return Double.valueOf(s.trim());
	// }
	// }
	//
	// private enum IntegerValueOfFunction implements Function<String, Integer>
	// {
	// INSTANCE;
	// @Override public Integer apply(String s) {
	// return Integer.valueOf(s.trim());
	// }
	// }

	private static class FormatDoubleFunction implements Function<Double, String> {
		private String format;

		private FormatDoubleFunction(String format) {
			this.format = format;
		}

		@Override public String apply(Double value) {
			return String.format(format, value);
		}
	}

	// TODO clean
	public static void main(String[] args) {

		// int[] ints = {1, 2, 3, 4, 10, 19, 18, 17, 16};
		int[] ints = { 620, 619, 618, 617, 616, 615, 614, 613, 612, 611, 610, 609, 608, 607, 606, 605, 604, 603, 602, 601, 600, 599, 598, 597, 596, 595, 594, 635, 634, 633, 632, 631, 630, 629, 628, 627, 626, 625, 624, 623, 622, 1832, 1833, 1834, 1835, 1836, 1837, 1838, 1839, 1840, 1841, 1842, 1843, 1844, 1845, 1846, 1847, 1848, 1849, 1850, 1851, 1944, 1945, 1946, 1947, 1948, 1949, 1950, 1951, 1952, 1953 };
		List<Integer> intList = Ints.asList(ints);
		String rangeString = intListToRangeString(intList);
		System.out.println(rangeString);

		List<Integer> out = rangeStringToIntList(rangeString);
		System.out.println(out);
		System.out.println(intList.equals(out));

		// String megaMapStr =
		// "[6.5 :: [1.0 : 0.8, 5.0 : 0.2]; 10.0 :: [1.0 : 0.2, 5.0 : 0.8]]";
		// String megaMapStr = "[]";
		// Map<Double, Map<Double, Double>> megaMap =
		// stringToValueValueWeightMap(megaMapStr);
		// System.out.println(megaMap);

		// String enumWtStr = "[STRIKE_SLIP:0.5,NORMAL:0.0,  REVERSE : 0.5]";
		// System.out.println(stringToEnumWeightMap(enumWtStr,
		// FocalMech.class));
		// double[] dd = new double[] {1.2, 3.4, 5.6, Double.NaN};
		// System.out.println(Arrays.toString(dd));
		// System.out.println(Doubles.asList(dd));
		// String dds = Doubles.asList(dd).toString();
		// System.out.println(Arrays.toString(toDoubleArray(dds)));
	}

	/**
	 * Reads a binary {@code byte} stream that consists of a list (array) of
	 * double values. Method closes the supplied {@code InputStream} before
	 * returning.
	 * 
	 * @param in the {@code InputStream} to read from
	 * @param byteCount number of bytes to read
	 * @return a {@code List<Double>}
	 * @throws IOException if there is a problem reading the supplied stream
	 */
	public static List<Double> readBinaryDoubleList(InputStream in, int byteCount)
			throws IOException {
		checkArgument(byteCount % 8 == 0, "byte count incompatible with doubles");
		checkArgument(byteCount > 0, "byte count too small");
		if (!(checkNotNull(in) instanceof BufferedInputStream)) {
			in = new BufferedInputStream(in);
		}
		DataInputStream din = new DataInputStream(in);
		int size = byteCount / 8;
		double[] vals = new double[size];
		for (int i = 0; i < size; i++) {
			vals[i] = din.readDouble();
		}
		in.close();
		return Doubles.asList(vals);
	}

	/**
	 * Reads a binary {@code byte} stream that contains a list of {@code int}
	 * lists (arrays). Method closes the supplied {@code InputStream} before
	 * returning.
	 * 
	 * @param in the {@code InputStream} to read from
	 * @return a {@code List} of {@code List<Integer>}s
	 * @throws IOException if there is a problem reading the supplied
	 *         {@code InputStream}
	 */
	public static List<List<Integer>> readBinaryIntLists(InputStream in) throws IOException {
		if (!(checkNotNull(in) instanceof BufferedInputStream)) {
			in = new BufferedInputStream(in);
		}
		DataInputStream din = new DataInputStream(in);
		int count = din.readInt();
		checkState(count > 0, "Number of lists must be > 0");
		List<List<Integer>> list = Lists.newArrayList();
		for (int i = 0; i < count; i++) {
			int[] vals = new int[din.readInt()];
			for (int j = 0; j < vals.length; j++) {
				vals[j] = din.readInt();
			}
			list.add(Ints.asList(vals));
		}
		in.close();
		return list;
	}

	public static List<BitSet> readBinaryIntBitSets(InputStream in, int bitSetSize)
			throws IOException {
		if (!(checkNotNull(in) instanceof BufferedInputStream)) {
			in = new BufferedInputStream(in);
		}
		DataInputStream din = new DataInputStream(in);
		int count = din.readInt();
		checkState(count > 0, "Number of lists must be > 0");
		List<BitSet> list = Lists.newArrayList();
		for (int i = 0; i < count; i++) {
			int size = din.readInt();
			BitSet bits = new BitSet(bitSetSize);
			for (int j = 0; j < size; j++) {
				bits.set(din.readInt());
			}
			list.add(bits);
		}
		in.close();
		return list;
	}

}
