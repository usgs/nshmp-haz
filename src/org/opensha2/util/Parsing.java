package org.opensha2.util;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.opensha2.data.DataUtils.validateWeights;

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

import org.opensha2.data.DataUtils;
import org.w3c.dom.Comment;
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
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * String, file, and XML parsing utilities.
 * 
 * @author Peter Powers
 */
public final class Parsing {

	public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static final Joiner.MapJoiner MAP_JOIN = Joiner.on(',').withKeyValueSeparator(":");
	private static final Splitter.MapSplitter MAP_SPLIT = Splitter.on(',').trimResults()
		.withKeyValueSeparator(":");
	private static final Splitter.MapSplitter MAP_MAP_SPLIT = Splitter.on(';').trimResults()
		.withKeyValueSeparator("::");

	// TODO refactor as Converters

	/**
	 * Convert a {@code Map<Enum, Double>} to a string with the form
	 * {@code [ENUM_1 : 0.8, ENUM_2 : 0.2]}.
	 * 
	 * @param map the {@code map} to convert
	 * @return a string representation of the supplied {@code Map}
	 */
	public static <T extends Enum<T>> String enumValueMapToString(Map<T, Double> map) {
		Map<String, String> strMap = Maps.newHashMap();
		for (Entry<T, Double> entry : map.entrySet()) {
			strMap.put(entry.getKey().name(), Double.toString(entry.getValue()));
		}
		return addBrackets(MAP_JOIN.join(strMap));
	}

	/**
	 * Convert a string of the form {@code [ENUM_1 : 0.8, ENUM_2 : 0.2]} to a
	 * {@code Map<Enum, Double>}. The returned map is immutable.
	 * 
	 * @param s the string to parse
	 * @param type {@code Class} to use as the key of the returned map
	 * @throws IllegalArgumentException if supplied string is malformed or
	 *         empty, or if weights do not sum to 1.0, within
	 *         {@link DataUtils#WEIGHT_TOLERANCE}
	 * @throws NumberFormatException if supplied string does not contain
	 *         parseable {@code double} values
	 * @return a new immutable {@code Map<Enum, Double>} of identifiers and
	 *         weights
	 */
	public static <T extends Enum<T>> Map<T, Double> stringToEnumWeightMap(String s, Class<T> type) {
		Map<String, String> strMap = MAP_SPLIT.split(trimEnds(checkNotNull(s)));
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
	 * Convert a string of the form
	 * {@code [6.5 :: [1.0 : 0.8, 5.0 : 0.2]; 10.0 :: [1.0 : 0.2, 5.0 : 0.8]]}
	 * to a {@code NavigableMap<Double, Map<Double, Double>}. The returned map
	 * and nested maps are immutable and should only be accessed via
	 * {@code Map.entrySet()} references or using keys derived from
	 * {@code Map.keySet()} as {@code Double} comparisons are inherently
	 * problematic.
	 * 
	 * @param s the string to parse
	 * @throws IllegalArgumentException if {@code s} is malformed or empty, or
	 *         if weights do not sum to 1.0, within
	 *         {@link DataUtils#WEIGHT_TOLERANCE}
	 * @throws NumberFormatException if {@code s} does not contain parseable
	 *         {@code double} values
	 * @return a new immutable {@code Map<Double, Double>} of values and their
	 *         weights
	 */
	public static NavigableMap<Double, Map<Double, Double>> stringToValueValueWeightMap(String s) {
		Map<String, String> strMap = MAP_MAP_SPLIT.split(trimEnds(checkNotNull(s)));
		ImmutableSortedMap.Builder<Double, Map<Double, Double>> builder = ImmutableSortedMap
			.naturalOrder();
		for (Entry<String, String> entry : strMap.entrySet()) {
			double key = Doubles.stringConverter().convert(entry.getKey());
			builder.put(key, stringToValueWeightMap(entry.getValue()));
		}
		return builder.build();
	}

	/**
	 * Convert a string of the form {@code [1.0 : 0.4, 2.0 : 0.6]} to a
	 * {@code NavigableMap<Double, Double>}. The returned map is immutable and
	 * should only be accessed via {@code Map.entrySet()} references or using
	 * keys derived from {@code Map.keySet()} as {@code Double} comparisons are
	 * inherently problematic.
	 * 
	 * @param s the string to parse
	 * @throws IllegalArgumentException if {@code s} is malformed or if weights
	 *         do not sum to 1.0, within {@link DataUtils#WEIGHT_TOLERANCE}
	 * @throws NumberFormatException if {@code s} does not contain parseable
	 *         {@code double} values
	 * @return a new immutable {@code Map<Double, Double>} of values and their
	 *         weights
	 */
	public static NavigableMap<Double, Double> stringToValueWeightMap(String s) {
		Map<String, String> strMap = MAP_SPLIT.split(trimEnds(checkNotNull(s)));
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
	 * Put SAX {@code Attributes} into a name-value {@code Map<String, String>}
	 * ({@code Attributes} are stateful when parsing XML).
	 * 
	 * @param atts the {@code Attributes} to store in {@code Map}
	 * @return a {@code Map} of name-value XML attribute pairs
	 */
	public static Map<String, String> toMap(Attributes atts) {
		Map<String, String> map = Maps.newHashMap();
		for (int i = 0; i < checkNotNull(atts).getLength(); i++) {
			map.put(atts.getQName(i), atts.getValue(i));
		}
		return map;
	}

	/**
	 * Add an attribute with an {@code Enum.toString()} name and string value to
	 * an XML {@link Element}.
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
	 * @param parent {@code Element} for child
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
	 * Add a child {@link Comment} to a parent and return a reference to the
	 * comment.
	 * 
	 * @param comment to add
	 * @param parent {@code Element} for comment
	 */
	public static Comment addComment(String comment, Element parent) {
		Comment c = parent.getOwnerDocument().createComment(comment);
		parent.appendChild(c);
		return c;
	}

	/**
	 * Read and return the value associated with an {@code Enum.toString()} name
	 * from an {@link Attributes} container as a {@code boolean}. Method
	 * explicitely checks that a case-insensitive value of "true" or "false" is
	 * supplied (as opposed to simply defaulting to {@code false}.
	 * 
	 * @param id the name identifier of the attribute
	 * @param atts a SAX {@code Attributes} container
	 * @throws NullPointerException if no attribute with the name
	 *         {@code id.toString()} exists
	 */
	public static boolean readBoolean(Enum<?> id, Attributes atts) {
		String name = checkNotNull(id).toString();
		String value = checkNotNull(atts).getValue(name);
		validateAttribute(name, value);
		checkArgument(value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false"),
			"Unparseable attribute " + id.toString() + "=\"" + value + "\"");
		return Boolean.valueOf(value);
	}

	/**
	 * Read and return the value associated with an {@code Enum.toString()} name
	 * from an {@link Attributes} container as an {@code int}.
	 * 
	 * @param id the name identifier of the attribute
	 * @param atts a SAX {@code Attributes} container
	 * @throws NullPointerException if no attribute with the name
	 *         {@code id.toString()} exists
	 */
	public static int readInt(Enum<?> id, Attributes atts) {
		String name = checkNotNull(id).toString();
		String value = checkNotNull(atts).getValue(name);
		try {
			return Integer.valueOf(validateAttribute(name, value));
		} catch (NumberFormatException nfe) {
			throw createAttributeException(name, value);
		}
	}

	/**
	 * Read and return the value associated with an {@code Enum.toString()} name
	 * from an {@link Attributes} container as a {@code double}.
	 * 
	 * @param id the name identifier of the attribute
	 * @param atts a SAX {@code Attributes} container
	 * @throws NullPointerException if no attribute with the name
	 *         {@code id.toString()} exists
	 */
	public static double readDouble(Enum<?> id, Attributes atts) {
		String name = checkNotNull(id).toString();
		String value = checkNotNull(atts).getValue(name);
		try {
			return Double.valueOf(validateAttribute(name, value));
		} catch (NumberFormatException nfe) {
			throw createAttributeException(name, value);
		}
	}

	/**
	 * Read and return the value associated with an {@code Enum.toString()} name
	 * from an {@link Attributes} container as a {@code double[]}.
	 * 
	 * @param id the name identifier of the attribute
	 * @param atts a SAX {@code Attributes} container
	 * @throws NullPointerException if no attribute with the name
	 *         {@code id.toString()} exists
	 */
	public static double[] readDoubleArray(Enum<?> id, Attributes atts) {
		String name = checkNotNull(id).toString();
		String value = checkNotNull(atts).getValue(name);
		try {
			return toDoubleArray(validateAttribute(name, value));
		} catch (NumberFormatException nfe) {
			throw createAttributeException(name, value);
		}
	}

	/**
	 * Read and return the value associated with an {@code Enum.toString()} name
	 * from an {@link Attributes} container as a {@code String}.
	 * 
	 * @param id the name identifier of the attribute
	 * @param atts a SAX {@code Attributes} container
	 * @throws NullPointerException if no attribute with the name
	 *         {@code id.toString()} exists
	 */
	public static String readString(Enum<?> id, Attributes atts) {
		String name = checkNotNull(id).toString();
		String value = checkNotNull(atts).getValue(name);
		return validateAttribute(name, value);
	}

	/**
	 * Read and return the value associated with an {@code Enum.toString()} name
	 * from an {@link Attributes} container as an {@code Enum}.
	 * 
	 * @param id the name identifier of the attribute
	 * @param atts a SAX {@code Attributes} container
	 * @param type the {@code class} of {@code enum} to return
	 * @throws NullPointerException if no attribute with the name
	 *         {@code id.toString()} exists
	 */
	public static <T extends Enum<T>> T readEnum(Enum<?> id, Attributes atts, Class<T> type) {
		return Enum.valueOf(checkNotNull(type), readString(id, atts));
	}

	private static String validateAttribute(String name, String value) {
		return checkNotNull(value, "Missing attribute '%s'", name);
	}

	private static IllegalArgumentException createAttributeException(String name, String value) {
		return new IllegalArgumentException("Unparseable attribute: " + name + "=\"" + value + "\"");
	}

	/**
	 * Returns a string containing the string representation of each of
	 * {@code parts} joined with {@code delimiter}.
	 * 
	 * @param parts the objects to join
	 * @param delimiter the {@link Delimiter} to join on
	 * @see Joiner
	 */
	public static String join(Iterable<?> parts, Delimiter delimiter) {
		return delimiter.joiner().join(parts);
	}

	/**
	 * Split a {@code sequence} into string components and make them available
	 * through a (possibly-lazy) {@code Iterator}.
	 * 
	 * @param sequence the sequence of characters to split
	 * @param delimiter the {@link Delimiter} to split on
	 * @see Splitter
	 */
	public static Iterable<String> split(CharSequence sequence, Delimiter delimiter) {
		return delimiter.splitter().split(sequence);
	}

	/**
	 * Split a {@code sequence} into string components and make them available
	 * through an immutable {@code List}.
	 * 
	 * @param sequence the sequence of characters to split
	 * @param delimiter the {@link Delimiter} to split on
	 */
	public static List<String> splitToList(CharSequence sequence, Delimiter delimiter) {
		return delimiter.splitter().splitToList(sequence);
	}

	/**
	 * Split {@code sequence} into {@code Double} components and make them
	 * available through an immutable {@code List}.
	 * 
	 * @param sequence the sequence of characters to split
	 * @param delimiter the {@link Delimiter} to split on
	 */
	public static List<Double> splitToDoubleList(CharSequence sequence, Delimiter delimiter) {
		return FluentIterable
			.from(split(sequence, delimiter))
			.transform(Doubles.stringConverter())
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

		/** Period ('.') delimiter. */
		PERIOD('.'),

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
	 * Return a {@code String} representation of an {@code Iterable<Enum>} where
	 * {@code Enum.name()} is used instead of {@code Enum.toString()}.
	 * 
	 * @param iterable to process
	 * @param enumClass
	 */
	public static <E extends Enum<E>> String enumsToString(Iterable<E> iterable, Class<E> enumClass) {
		return addBrackets(FluentIterable
			.from(iterable)
			.transform(Enums.stringConverter(enumClass).reverse())
			.join(Delimiter.COMMA.joiner()));
	}

	/**
	 * Convert an {@code Enum.name()} to a space-delimited presentation-friendly
	 * string.
	 * 
	 * @param e the {@code Enum} to generate label for
	 */
	public static String enumLabelWithSpaces(Enum<? extends Enum<?>> e) {
		return join(splitEnum(e), Delimiter.SPACE);
	}

	/**
	 * Convert an {@code Enum.name()} to a dash-delimited presentation-friendly
	 * string.
	 * 
	 * @param e the {@code Enum} to generate label for
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
	 * Capitalize supplied string by converting the first {@code char} to
	 * uppercase and all subsequent {@code char}s to lowercase.
	 * 
	 * @param s the string to capitalize
	 */
	public static String capitalize(String s) {
		return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
	}

	/**
	 * Convert a bracketed and comma-delimited string of numbers (e.g. [1.0,
	 * 2.0, 3.0] to a {@code double[]}. This is the reverse of
	 * {@link Arrays#toString(double[])} and {@link List#toString()}
	 * 
	 * @param s the string to convert
	 */
	public static double[] toDoubleArray(String s) {
		return Doubles.toArray(FluentIterable.from(split(trimEnds(s), Delimiter.COMMA))
			.transform(Doubles.stringConverter()).toList());
	}

	/**
	 * Convert a {@code Collection<Double>} to a string of the same format
	 * returned by {@link Arrays#toString(double[])} and {@link List#toString()}
	 * , but will format the values using the supplied format string. The
	 * supplied {@code format} should match that expected by
	 * {@code String.format(String, Object...)}
	 * 
	 * @param values the values to convert
	 * @param format a format string
	 */
	public static String toString(Collection<Double> values, String format) {
		return addBrackets(join(Iterables.transform(values, new FormatDoubleFunction(format)),
			Delimiter.COMMA));
	}

	/**
	 * Convert an ordered list of non-repeating {@code Integer}s to a more
	 * compact string form. For example,
	 * {@code List<Integer>.toString() = "[1, 2, 3, 4, 10, 19, 18, 17, 16]"}
	 * would instead be written as {@code "1:4,10,19:16"}.
	 * 
	 * @param values to convert
	 * @throws IllegalArgumentException if {@code values} is empty or if
	 *         {@code values} contains adjacent repeating values
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
			int next = values.get(i);
			checkArgument(next != end, "repeating value %s in %s", next, values);
			boolean currentDir = next > end;
			boolean terminateRange =
				// step > 1
				(Math.abs(next - end) != 1) ||
					// direction change
					(buildingRange && currentDir != dir) ||
					// end of list
					(i == values.size() - 1);
			
			if (terminateRange) {
				if (i == values.size() - 1) {
					// singleton trailing value
					if (Math.abs(next - end) == 1 && currentDir == dir) {
						ranges.add(new int[] { start, next });
					} else {
						ranges.add(new int[] { start, end });
						ranges.add(new int[] { next, next});
					}
				} else {
					ranges.add(new int[] { start, end });
				}
				start = next;
				end = next;
				buildingRange = false;
				continue;
			}

			// starting or continuing new range
			buildingRange = true;
			dir = currentDir;
			end = next;
		}
		return join(Iterables.transform(ranges, IntArrayToString.INSTANCE), Delimiter.COMMA);
	}

	/**
	 * Complement of {@link #intListToRangeString} converts a string of the form
	 * {@code "[[1:4],10,[19:16]]"} to an ordered {@code List<Integer>} (e.g.
	 * {@code [1, 2, 3, 4, 10, 19, 18, 17, 16]}). This method should only be
	 * called with strings created by {@link #intListToRangeString}, otherwise
	 * results are undefined.
	 * 
	 * @param s the string to convert
	 * @see #intListToRangeString
	 */
	public static List<Integer> rangeStringToIntList(String s) {
		Iterable<int[]> values = Iterables.transform(split(s, Delimiter.COMMA),
			StringToIntArray.INSTANCE);
		return Ints.asList(Ints.concat(Iterables.toArray(values, int[].class)));
	}

	// internal use only - no argument checking
	// writes 2-element int[]s as 'a:b' or just 'a' if a==b
	private enum IntArrayToString implements Function<int[], String> {
		INSTANCE;
		@Override public String apply(int[] ints) {
			return (ints[0] == ints[1]) ? Integer.toString(ints[0]) : ints[0] + ":" + ints[1];
		}
	}

	// close complement of above, converts IntArrayToStrings to List<Integer>s
	private enum StringToIntArray implements Function<String, int[]> {
		INSTANCE;
		@Override public int[] apply(String s) {
			if (s.contains(":")) {
				Iterator<Integer> rangeIt = Iterators.transform(
					split(s, Delimiter.COLON).iterator(), Ints.stringConverter());
				return DataUtils.indices(rangeIt.next(), rangeIt.next());
			}
			return new int[] { Integer.valueOf(s) };
		}
	}

	/**
	 * Strip trailing zeros of a decimal number that has already been formatted
	 * as a string. Method will leave a single zero after a decimal. Method will
	 * not alter exponential forms, which may have a critical '0' in last
	 * position.
	 * 
	 * @param s the string to clean
	 */
	public static String stripZeros(String s) {
		if (s.charAt(1) == '.' && s.contains("e-")) return s;
		s = s.replaceAll("0*$", "");
		if (s.endsWith(".")) s += "0";
		return s;
	}

	/**
	 * Returns a new {@code String} created by first trimming the supplied
	 * {@code String} of leading and trailing whitespace (via
	 * {@code String.trim()}), and then removing the first and last characters.
	 * At a minimum, this method will return an empty string.
	 * 
	 * @param s {@code String} to trim
	 * @throws IllegalArgumentException if the size of the supplied string is
	 *         &lt;2 after leading and trailing whitespace has been removed
	 */
	public static String trimEnds(String s) {
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
	 * Convert a {@code Map} to a {@code URL} name-value pair query string.
	 * @param params the parameter map to convert
	 */
	public static String mapToQuery(Map<String, String> params) {
		return "?" + QUERY_JOINER.join(params);
	}

	/**
	 * Convert a {@code URL} name-value pair query string to a {@code Map}.
	 * @param query the string to convert
	 */
	public static Map<String, String> queryToMap(String query) {
		if (query.startsWith("?")) query = query.substring(1);
		return QUERY_SPLITTER.split(query);
	}

	/**
	 * Read and return the {@code int} at {@code position} in a space-delimited
	 * string.
	 * 
	 * @param s the string to read from
	 * @param position the position to read
	 */
	public static int readInt(String s, int position) {
		return Integer.valueOf(Iterables.get(split(s, Delimiter.SPACE), position));
	}

	/**
	 * Read and return the {@code double} at {@code position} in a
	 * space-delimited string.
	 * 
	 * @param s the string to read from
	 * @param position the position to read
	 */
	public static double readDouble(String s, int position) {
		return Double.valueOf(Iterables.get(split(s, Delimiter.SPACE), position));
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
	 * Read the specified number of strings from an {@code Iterator} and return
	 * them as a {@code List}.
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

	/**
	 * Returns a {@link Function} for converting {@code double}s to formatted
	 * strings.
	 * 
	 * @param format a format string
	 * @see String#format(String, Object...)
	 */
	public static Function<Double, String> formatDoubleFunction(String format) {
		return new FormatDoubleFunction(format);
	}

	private static class FormatDoubleFunction implements Function<Double, String> {
		private String format;

		private FormatDoubleFunction(String format) {
			this.format = format;
		}

		@Override public String apply(Double value) {
			return String.format(format, value);
		}
	}

	/**
	 * Reads a binary {@code byte} stream as a sequence of {@code double}s.
	 * Method closes the supplied {@code InputStream} before returning.
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
	 * Reads a binary {@code byte} stream as a sequence of {@code int}s. Method
	 * closes the supplied {@code InputStream} before returning.
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

	/**
	 * This method is currently unused and slated for removal.
	 * 
	 * @param in
	 * @param bitSetSize
	 * @throws IOException
	 */
	@Deprecated public static List<BitSet> readBinaryIntBitSets(InputStream in, int bitSetSize)
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
