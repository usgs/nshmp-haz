package org.opensha.data;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkPositionIndex;
import static com.google.common.math.DoubleMath.fuzzyEquals;
import static java.lang.Double.NaN;
import static java.lang.Double.POSITIVE_INFINITY;
import static java.lang.Double.isNaN;
import static org.opensha.data.DataUtils.validateWeights;

import java.lang.reflect.Array;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apache.commons.math3.stat.StatUtils;
import org.opensha.function.DefaultXY_DataSet;

import com.google.common.base.Function;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Range;
import com.google.common.collect.Table;
import com.google.common.math.DoubleMath;
import com.google.common.primitives.Doubles;
import com.google.common.primitives.Ints;

/**
 * Utilities for operating on {@code double}-valued data.
 * <p>See {@link Doubles}  minimum, maximum, sum,
 * mean, product, etc... of {@code double} arrays as well as other
 * properties</p>
 * 
 * <p>Transformations of {@code double} arrays or {@code List}s may be
 * performed on empty data sets; {@code null} data sets throw an exception.</p>
 * 
 * TODO isPositive throws exception for size==0, check for consistency with transformations
 * 
 * <p>Class designed to reduce data copying hence why List variants do not
 * call toArray() and delegate to varargs variant.</p>
 * 
 * <p>Methods that return a result or infomration about a supplied data set will
 * typically take a {@code Collection<Double>} as an argument, whereas methods that
 * transform data in place will only take {@code List<Double>}s.</p>
 * 
 * @author Peter Powers
 * @author Kevin Milner
 */
public final class DataUtils {
	
	private DataUtils() {}

	/**
	 * Acceptable tolerance when summing weights and comparing to 1.0. Currently
	 * set to 1e-8.
	 */
	public static final double WEIGHT_TOLERANCE = 1e-8;
	
	/*
	 * Some quick tests of abs() and scale() using a non-Function based approach
	 * and hence no autoboxing showed only marginal slowdown over a 10^8 sized
	 * array of random values. If profiling shows that in practice the function
	 * based approach of transforming arrays is slow, primitive implementations
	 * may be substituted. See DubblesTest for speed test.
	 * 
	 * Similarly, boolean tests such as isSorted() could be short-circuited to
	 * return at the first failure. However, there is more reusable code in the
	 * current implementation that is easier to follow. Again, this will may be
	 * changed if there is a demonstrable performance hit.
	 * 
	 * We could probably intern commonly used scale functions.
	 */
	
	private static final Range<Double> POS_RANGE = Range.open(0d, Double.POSITIVE_INFINITY);

	private static final Range<Double> WEIGHT_RANGE = Range.openClosed(0d, 1d);	

	/**
	 * Returns the difference between {@code test} and {@code target}, relative
	 * to {@code target}, as a percent. If {@code target} is 0, method returns 0
	 * if {@code test} is also 0, otherwise {@code Double.POSITIVE_INFINITY}. If
	 * either value is {@code Double.NaN}, method returns {@code Double.NaN}.
	 * @param test value
	 * @param target value
	 * @return the percent difference
	 */
	public static double getPercentDiff(double test, double target) {
		if (isNaN(target) || isNaN(test)) return NaN;
		if (target == 0) return test == 0 ? 0 : POSITIVE_INFINITY;
		return Math.abs(test - target) / target * 100d;
	}

	/**
	 * Returns whether the supplied {@code data} are all positive.
	 * @param data to check
	 * @return {@code true} if all values are &ge;0
	 */
	public static boolean isPositive(double... data) {
		checkNotNull(data);
		checkArgument(data.length > 0, "data is empty");
		for (double d : data) {
			if (d >= 0) continue;
			return false;
		}
		return true;
	}
	
	/**
	 * Returns whether the supplied {@code data} are all positive.
	 * @param data to check
	 * @throws IllegalArgumentException if {@code data.size() == 0}
	 * @return {@code true} if all values are &ge;0
	 */
	public static boolean isPositive(Collection<Double> data) {
		checkNotNull(data);
		checkArgument(data.size() > 0, "data is empty");
		for (double d : data) {
			if (d >= 0) continue;
			return false;
		}
		return true;
	}
	
	/**
	 * Ensures positivity of values by adding {@code Math.abs(min(data))} if
	 * {@code min < 0}. Operation performed in place on supplied array.
	 * @param data to positivize
	 * @return a reference to the supplied data, positivized if necessary
	 */
	public static double[] positivize(double[] data) {
		double min = Doubles.min(data);
		if (min >= 0) return data;
		min = Math.abs(min);
		return add(min, data);
	}
	
	/**
	 * Returns whether the elements of the supplied {@code data} increase or
	 * decrease monotonically, with a flag indicating if duplicate elements are
	 * permitted. The {@code repeats} flag could be {@code false} if checking
	 * the x-values of a function for any steps, or {@code true} if checking the
	 * y-values of a cumulative distribution function, which are commonly
	 * constant.
	 * @param ascending if {@code true}, descending if {@code false}
	 * @param repeats whether repeated adjacent elements are allowed
	 * @param data to validate
	 * @return {@code true} if monotonic, {@code false} otherwise
	 */
	public static boolean isMonotonic(boolean ascending, boolean repeats,
			double... data) {
		double[] diff = diff(data);
		if (!ascending) flip(diff);
		double min = Doubles.min(diff);
		return (repeats) ? min >= 0 : min > 0;
	}

	/**
	 * Returns the difference of adjacent elements in the supplied {@code data}.
	 * Method returns results in a new array that has {@code data.length - 1}
	 * where differences are computed per {@code data[i+1] - data[i]}.
	 * @param data to difference
	 * @return the differences between adjacent values
	 */
	public static double[] diff(double... data) {
		checkNotNull(data);
		checkArgument(data.length > 1);
		int size = data.length - 1;
		double[] diff = new double[size];
		for (int i = 0; i < size; i++) {
			diff[i] = data[i + 1] - data[i];
		}
		return diff;
	}
	
	/**
	 * Creates a sequence of evenly spaced values starting at {@code min} and
	 * ending at {@code max}. If {@code (max - min) / step} is not equivalent to
	 * an integer, the last step in the sequence will be {@code &lt;step}.
	 * Unlike {@link #buildSequence(double, double, double, boolean)}, this
	 * method returns a sequence where any 'odd' values due to rounding errors
	 * have been removed, at least within the range of the specified
	 * {@code scale} (precision or number of decimal places).
	 * @param min sequence value
	 * @param max sequence value
	 * @param step sequence spacing
	 * @param ascending if {@code true}, descending if {@code false}
	 * @param scale the number of decimal places to preserve
	 * @return a monotonically increasing or decreasing sequence of values
	 * @throws IllegalArgumentException if {@code min >= max}, {@code step <= 0}
	 *         , or any arguments are {@code Double.NaN},
	 *         {@code Double.POSITIVE_INFINITY}, or
	 *         {@code Double.NEGATIVE_INFINITY}
	 */
	public static double[] buildCleanSequence(double min, double max,
			double step, boolean ascending, int scale) {
		String format = "%." + scale + "f"; 
		double[] seq = buildSequence(min, max, step, ascending);
		for (int i=0; i<seq.length; i++) {
			seq[i] = Double.parseDouble(String.format(format, seq[i]));
		}
		return seq;
	}
	
	/**
	 * Creates a sequence of values starting at {@code min} and ending at
	 * {@code max}, the log of which are evenly spaced.
	 * @param min sequence value
	 * @param max sequence value
	 * @param step sequence spacing
	 * @param ascending if {@code true}, descending if {@code false}
	 * @return a monotonically increasing or decreasing sequence where the log
	 *         of the values are evenly spaced
	 * @throws IllegalArgumentException if {@code min >= max}, {@code step <= 0}
	 *         , or any arguments are {@code Double.NaN},
	 *         {@code Double.POSITIVE_INFINITY}, or
	 *         {@code Double.NEGATIVE_INFINITY}
	 * 
	 */
	public static double[] buildLogSequence(double min, double max,
			double step, boolean ascending) {
		double[] seq = buildSequence(Math.log(min), Math.log(max),
			Math.log(step), ascending);
		return exp(seq);
	}

	/**
	 * Creates a sequence of evenly spaced values starting at {@code min} and
	 * ending at {@code max}. If {@code (max - min) / step} is not integer
	 * valued, the last step in the sequence will be {@code &lt;step}.
	 * @param min sequence value
	 * @param max sequence value
	 * @param step sequence spacing
	 * @param ascending if {@code true}, descending if {@code false}
	 * @return a monotonically increasing or decreasing sequence of values
	 * @throws IllegalArgumentException if {@code min >= max}, {@code step <= 0}
	 *         , or any arguments are {@code Double.NaN},
	 *         {@code Double.POSITIVE_INFINITY}, or
	 *         {@code Double.NEGATIVE_INFINITY}
	 */
	public static double[] buildSequence(double min, double max, double step,
			boolean ascending) {
		// if passed in arguments are NaN, +Inf, or -Inf, and step <= 0,
		// then capacity [c] will end up 0 because (int) NaN = 0, or outside the
		// range 1:10000
		checkArgument(min <= max, "min-max reversed");
		int c = (int) ((max - min) / step);
		checkArgument(c > 0 && c < MAX_SEQ_LEN, "sequence size");
		if (ascending) return buildSequence(min, max, step, c + 2);
		double[] descSeq = buildSequence(-max, -min, step, c + 2);
		return flip(descSeq);
		
		// TODO
		// 		double[] mags = DataUtils.buildSequence(5.05, 7.85, 0.1, true);
		//      System.out.println(Arrays.toString(mags));
		// produces crummy values 2.449999999999999999 etc...
	}

	private static final int MAX_SEQ_LEN = 10001;
	private static final double SEQ_MAX_VAL_TOL = 0.000000000001;

	private static double[] buildSequence(double min, double max, double step,
			int capacity) {
		List<Double> seq = Lists.newArrayListWithCapacity(capacity);
		for (double val = min; val < max; val += step) {
			seq.add(val);
		}
		// do not add max if current max is equal to max wihthin tolerance
		if (!DoubleMath.fuzzyEquals(seq.get(seq.size() - 1), max,
			SEQ_MAX_VAL_TOL)) seq.add(max);
		return Doubles.toArray(seq);
	}

	/**
	 * Scales (multiplies) the elements of the supplied {@code data} in place
	 * by {@code value}.
	 * 
	 * <p><b>Note:</b> This method does not check for over/underflow.</p>
	 * @param data to scale
	 * @param value to scale by
	 * @return a reference to the supplied data
	 */
	public static double[] scale(double value, double... data) {
		return transform(new Scale(value), data);
	}

	/**
	 * Scales (multiplies) the elements of the supplied {@code List} in place
	 * by {@code value}.
	 * 
	 * <p><b>Note:</b> This method does not check for over/underflow.</p>
	 * @param list of {@code Double}s to scale
	 * @param value to scale by
	 * @return a reference to the supplied data
	 */
	public static List<Double> scale(double value, List<Double> list) {
		return transform(new Scale(value), list);
	}

	/**
	 * Adds the {@code value} to the supplied {@code data} in place.
	 * 
	 * <p><b>Note:</b> This method does not check for over/underrun.</p>
	 * @param data to add to
	 * @param value to add
	 * @return a reference to the supplied data
	 */
	public static double[] add(double value, double... data) {
		return transform(new Add(value), data);
	}
	
	/**
	 * Adds the values of {@code data2} to {@code data1} and returns a reference
	 * to {@code data1}.
	 * @param data1 
	 * @param data2
	 * @return a reference to {@code data1}
	 * @throws NullPointerException if either array is {@code null}
	 * @throws IllegalArgumentException if the arrays are not the same size
	 */
	public static double[] add(double[] data1, double[] data2) {
		checkArgument(checkNotNull(data1).length == checkNotNull(data2).length);
		return uncheckedAdd(data1, data2);
	}
	
	/*
	 * Adds the values of {@code data2} to {@code data1} and returns a reference
	 * to {@code data1} without any argument checking.
	 */
	static double[] uncheckedAdd(double[] data1, double[] data2) {
		for (int i=0; i<data1.length; i++) {
			data1[i] += data2[i];
		}
		return data1; 
	}
	
	/**
	 * Adds the values of {@code data2} to {@code data1} and returns a reference
	 * to {@code data1}.
	 * @param data1 
	 * @param data2
	 * @return a reference to {@code data1}
	 */
	public static List<Double> add(List<Double> data1, List<Double> data2) {
		checkArgument(checkNotNull(data1).size() == checkNotNull(data2).size());
		for (int i=0; i<data1.size(); i++) {
			data1.set(i, data1.get(i) + data2.get(i));
		}
		return data1;
	}

	/**
	 * Subtracts the values of {@code data2} from {@code data1} and returns a
	 * reference to {@code data1}.
	 * @param data1
	 * @param data2
	 * @return a reference to {@code data1}
	 */
	public static double[] subtract(double[] data1, double[] data2) {
		checkArgument(checkNotNull(data1).length == checkNotNull(data2).length);
		for (int i = 0; i < data1.length; i++) {
			data1[i] -= data2[i];
		}
		return data1;
	}
	
	/*
	 * Subtracts the values of {@code data2} from {@code data1} and returns a
	 * reference to {@code data1} without any argument checking.
	 */
	static double[] uncheckedSubtract(double[] data1, double[] data2) {
		for (int i = 0; i < data1.length; i++) {
			data1[i] -= data2[i];
		}
		return data1;
	}
	
	/**
	 * Subtracts the values of {@code data2} from {@code data1} and returns a
	 * reference to {@code data1}.
	 * @param data1
	 * @param data2
	 * @return a reference to {@code data1}
	 */
	public static List<Double> subtract(List<Double> data1, List<Double> data2) {
		checkArgument(checkNotNull(data1).size() == checkNotNull(data2).size());
		for (int i = 0; i < data1.size(); i++) {
			data1.set(i, data1.get(i) - data2.get(i));
		}
		return data1;
	}

	/**
	 * Multiplies the values of {@code data1} by those in {@code data2} and
	 * returns a reference to {@code data1}.
	 * @param data1
	 * @param data2
	 * @return a reference to {@code data1}
	 */
	public static double[] multiply(double[] data1, double[] data2) {
		checkArgument(checkNotNull(data1).length == checkNotNull(data2).length);
		for (int i = 0; i < data1.length; i++) {
			data1[i] *= data2[i];
		}
		return data1;
	}

	/*
	 * Multiplies the values of {@code data1} by those in {@code data2} and
	 * returns a reference to {@code data1} without any argument checking.
	 */
	static double[] uncheckedMultiply(double[] data1, double[] data2) {
		for (int i = 0; i < data1.length; i++) {
			data1[i] *= data2[i];
		}
		return data1;
	}

	/**
	 * Diveds the values of {@code data1} by those in {@code data2} and
	 * returns a reference to {@code data1}.
	 * @param data1
	 * @param data2
	 * @return a reference to {@code data1}
	 */
	public static double[] divide(double[] data1, double[] data2) {
		checkArgument(checkNotNull(data1).length == checkNotNull(data2).length);
		for (int i = 0; i < data1.length; i++) {
			data1[i] *= data2[i];
		}
		return data1;
	}

	/*
	 * Multiplies the values of {@code data1} by those in {@code data2} and
	 * returns a reference to {@code data1} without any argument checking.
	 */
	static double[] uncheckedDivide(double[] data1, double[] data2) {
		for (int i = 0; i < data1.length; i++) {
			data1[i] *= data2[i];
		}
		return data1;
	}

	/**
	 * Sets every element of the supplied {@code data} to its absolute value.
	 * @param data to operate on
	 * @return a reference to the data
	 */
	public static double[] abs(double... data) {
		return transform(ABS, data);
	}
	
	/**
	 * Applies the exponential function to every element of the supplied 
	 * {@code data}.
	 * 
	 * <p><b>Note:</b> This method does not check for over/underflow.</p>
	 * @param data to operate on
	 * @return a reference to the data
	 */
	public static double[] exp(double... data) {
		return transform(EXP, data);
	}

	/**
	 * Applies the natural log function to every element of the supplied 
	 * {@code data}.
	 * 
	 * @param data to operate on
	 * @return a reference to the data
	 */
	public static double[] ln(double... data) {
		return transform(LN, data);
	}

	/**
	 * Applies the base-10 log function to every element of the supplied 
	 * {@code data}.
	 * 
	 * @param data to operate on
	 * @return a reference to the data
	 */
	public static double[] log(double... data) {
		return transform(LOG, data);
	}

	/**
	 * Flips the sign of every element in the supplied {@code data}.
	 * @param data to operate on
	 * @return a reference to the data
	 */
	public static double[] flip(double... data) {
		return transform(new Scale(-1), data);
	}
	
	/**
	 * Returns the minimum of the supplied values. Method delegates to
	 * {@link Doubles#min(double...)}. Method returns {@code Double.NaN} if
	 * {@code data} contains {@code Double.NaN}.
	 * 
	 * @param data array to search
	 * @return the minimum of the supplied values
	 * @throws IllegalArgumentException if {@code data} is empty
	 * @see Doubles#min(double...)
	 */
	public static double min(double... data) {
		return Doubles.min(data);
	}
		
	/**
	 * Returns the maximum of the supplied values. Method delegates to
	 * {@link Doubles#max(double...)}. Method returns {@code Double.NaN} if
	 * {@code data} contains {@code Double.NaN}.
	 * 
	 * @param data array to search
	 * @return the maximum of the supplied values
	 * @throws IllegalArgumentException if {@code data} is empty
	 * @see Doubles#max(double...)
	 */
	public static double max(double... data) {
		return Doubles.max(data);
	}

	/**
	 * Returns the sum of the supplied values. Method returns {@code Double.NaN}
	 * if {@code data} contains {@code Double.NaN}.
	 * 
	 * <p><b>Note:</b> This method does not check for over/underflow.</p>
	 * @param data to add together
	 * @return the sum of the supplied values
	 */
	public static double sum(double... data) {
		checkNotNull(data);
		double sum = 0;
		for (double d : data) {
			sum += d;
		}
		return sum;
	}
	
	/**
	 * Returns the sum of the supplied values. Method returns {@code Double.NaN}
	 * if {@code data} contains {@code Double.NaN}.
	 * 
	 * <p><b>Note:</b> This method does not check for over/underflow.</p>
	 * @param data to add together
	 * @return the sum of the supplied values
	 */
	public static double sum(Collection<Double> data) {
		checkNotNull(data);
		double sum = 0;
		for (double d : data) {
			sum += d;
		}
		return sum;
	}
	
	/**
	 * Converts the elements of {@code data} to weights, in place, such that
	 * they sum to 1.
	 * @param data to convert
	 * @return a reference to the supplied array
	 * @throws IllegalArgumentException if {@code data} is empty, contains any
	 *         {@code Double.NaN} or negative values, or sums to a value outside
	 *         the range {@code (0..Double.POSITIVE_INFINITY), exclusive}
	 */
	public static double[] asWeights(double... data) {
		// TODO possible rename to normalize()
		checkArgument(isPositive(data));
		double sum = sum(data);
		checkArgument(POS_RANGE.contains(sum));
		double scale = 1d / sum;
		return scale(scale, data);
	}

	/**
	 * Converts the elements of {@code data} to weights, in place, such that
	 * they sum to 1.
	 * @param data to convert
	 * @return a reference to the supplied array
	 * @throws IllegalArgumentException if {@code data} is empty, contains any
	 *         {@code Double.NaN} or negative values, or sums to a value outside
	 *         the range {@code (0..Double.POSITIVE_INFINITY), exclusive}
	 */
	public static List<Double> asWeights(List<Double> data) {
		checkArgument(isPositive(data));
		double sum = sum(data);
		checkArgument(POS_RANGE.contains(sum));
		double scale = 1d / sum;
		return scale(scale, data);
	}
	
	/**
	 * Confirms that a {@code Collection<Double>} of weights sums to 1.0 within
	 * {@link #WEIGHT_TOLERANCE}.
	 * @param weights to validate
	 * @see #WEIGHT_TOLERANCE
	 */
	public static void validateWeights(Collection<Double> weights) {
		checkArgument(fuzzyEquals(sum(weights), 1.0, WEIGHT_TOLERANCE),
			"Weights do not sum to 1: %s", weights);
	}
	
	/**
	 * Confirms that a weight value is {@code 0.0 < weight <= 1.0}. Method
	 * returns the supplied value and can be used inline.
	 * @param weight to validate
	 * @return the supplied {@code weight} value
	 */
	public static double validateWeight(double weight) {
		uncheckedValidate(WEIGHT_RANGE, weight, "Weight");
		return weight;
	}

	/**
	 * Transforms the supplied {@code data} in place as per the supplied
	 * {@code function}'s {@link Function#apply(Object)} method.
	 * @param function to apply to data elements
	 * @param data to operate on
	 * @return a reference to the supplied {@code data} array
	 */
	private static double[] transform(Function<Double, Double> function,
			double... data) {
		checkNotNull(data);
		for (int i = 0; i < data.length; i++) {
			data[i] = function.apply(data[i]);
		}
		return data;
	}	
	
	/**
	 * Transforms the supplied {@code data} in place as per the supplied
	 * {@code function}'s {@link Function#apply(Object)} method.
	 * @param function to apply to data elements
	 * @param data to operate on
	 * @return a reference to the supplied {@code data} array
	 */
	private static List<Double> transform(Function<Double, Double> function,
			List<Double> data) {
		checkNotNull(data);
		for (int i = 0; i < data.size(); i++) {
			data.set(i, function.apply(data.get(i)));
		}
		return data;
	}	
	
	// @formatter:off
	// TODO group the four below (and others) in a single MathFunction enum??
	
	private static final Function<Double, Double> ABS = new Function<Double, Double>() {
		@Override public Double apply(Double in) { return Math.abs(in); }
	}; 
	
	private static final Function<Double, Double> EXP = new Function<Double, Double>() {
		@Override public Double apply(Double in) { return Math.exp(in); }
	}; 

	private static final Function<Double, Double> LN = new Function<Double, Double>() {
		@Override public Double apply(Double in) { return Math.log(in); }
	}; 

	private static final Function<Double, Double> LOG = new Function<Double, Double>() {
		@Override public Double apply(Double in) { return Math.log10(in); }
	}; 

	private static class Scale implements Function<Double, Double> {
		private final double scale;
		private Scale(final double scale) { this.scale = scale; }
		@Override public Double apply(Double d) { return d * scale; }
	}

	private static class Add implements Function<Double, Double> {
		private final double term;
		private Add(final double term) { this.term = term; }
		@Override public Double apply(Double d) { return d + term; }
	}
	
		
	// @formatter:on
	
	// TODO clean
//	/**
//	 * Validates the domain of a {@code double} data set. Method verifies
//	 * that data values all fall between {@code min} and {@code max} range
//	 * (inclusive). Empty arrays are ignored. If {@code min} is
//	 * {@code Double.NaN}, no lower limit is imposed; the same holds true
//	 * for {@code max}. {@code Double.NaN} values in {@code array}
//	 * will validate.
//	 * 
//	 * @param min minimum range value
//	 * @param max maximum range value
//	 * @param array to validate
//	 * @throws IllegalArgumentException if {@code min > max}
//	 * @throws IllegalArgumentException if any {@code array} value is out of
//	 *         range
//	 * @deprecated Ranges should be used instead with NaNs throwing an exception
//	 */
//	@Deprecated
//	public final static void validate(double min, double max, double... array) {
//		checkNotNull(array, "array");
//		for (int i = 0; i < array.length; i++) {
//			validate(min, max, array[i]);
//		}
//	}
//		
//	/**
//	 * Verifies that a {@code double} data value falls within a specified
//	 * minimum and maximum range (inclusive). If {@code min} is 
//	 * {@code Double.NaN}, no lower limit is imposed; the same holds true
//	 * for {@code max}. A value of {@code Double.NaN} will always
//	 * validate.
//	 * 
//	 * @param min minimum range value
//	 * @param max minimum range value
//	 * @param value to check
//	 * @throws IllegalArgumentException if {@code min > max}
//	 * @throws IllegalArgumentException if value is out of range
//	 * @deprecated Ranges should be used instead with NaNs throwing an exception
//	 */
//	@Deprecated
//	public final static void validate(double min, double max, double value) {
//		boolean valNaN = isNaN(value);
//		boolean minNaN = isNaN(min);
//		boolean maxNaN = isNaN(max);
//		boolean both = minNaN && maxNaN;
//		boolean neither = !(minNaN || maxNaN);
//		if (neither) checkArgument(min <= max, "min-max reversed");
//		boolean expression = valNaN || both ? true : minNaN
//			? value <= max : maxNaN ? value >= min : value >= min &&
//				value <= max;
//		checkArgument(expression, "value");
//	}
	
	/**
	 * Verifies that a {@code double} data value falls within a specified
	 * {@link Range}. Method returns the supplied value and can be used inline.
	 * @param range of allowable values
	 * @param value to validate
	 * @param label indicating type of value being checked; used in exception
	 *        message
	 * @return the supplied value
	 * @see Range
	 */
	public static double validate(Range<Double> range, String label,
			double value) {
		uncheckedValidate(checkNotNull(range), value, label);
		return value;
	}

	/**
	 * Verifies that the domain of a {@code double} data set does not exceed
	 * that of the supplied {@link Range}. Method returns the supplied values
	 * and can be used inline.
	 * @param range of allowable values
	 * @param array of values
	 * @param label indicating type of value being checked; used in exception
	 *        message
	 * @return the supplied values
	 * @see Range
	 */
	public static double[] validate(Range<Double> range, String label, double... array) {
		checkNotNull(range);
		checkNotNull(array);
		for (int i = 0; i < array.length; i++) {
			uncheckedValidate(range, array[i], label);
		}
		return array;
	}
	
	/* Does not check if range is null for more performant array checking */
	private static void uncheckedValidate(Range<Double> range, double value,
			String label) {
		checkArgument(!Double.isNaN(value), "NaN not allowed");
		checkArgument(range.contains(value), "%s value %s is not in range %s",
			Strings.nullToEmpty(label), value, range);
	}
	
	/**
	 * Creates a new array from the values in a source array at the specified
	 * indices. Returned array is of same type as source.
	 * 
	 * @param array array source
	 * @param indices index values of items to select
	 * @return a new array of values at indices in source
	 * @throws NullPointerException if {@code array} or
	 *         {@code indices} are {@code null}
	 * @throws IllegalArgumentException if data object is not an array or if
	 *         data array is empty
	 * @throws IndexOutOfBoundsException if any indices are out of range
	 */
	public static Object arraySelect(Object array, int[] indices) {
		checkNotNull(array, "Supplied data array is null");
		checkNotNull(indices, "Supplied index array is null");
		checkArgument(array.getClass().isArray(),
			"Data object supplied is not an array");
		int arraySize = Array.getLength(array);
		checkArgument(arraySize != 0, "Supplied data array is empty");

		// validate indices
		for (int i = 0; i < indices.length; i++) {
			checkPositionIndex(indices[i], arraySize, "Supplied index");
		}

		Class<? extends Object> srcClass = array.getClass().getComponentType();
		Object out = Array.newInstance(srcClass, indices.length);
		for (int i = 0; i < indices.length; i++) {
			Array.set(out, i, Array.get(array, indices[i]));
		}
		return out;
	}

	/**
	 * Sorts the supplied data array in place and returns an {@code int[]}
	 * array of the original indices of the data values. For example, if the
	 * supplied array is [3, 1, 8], the supplied array will be sorted to [1, 3,
	 * 8] and the array [2, 1, 3] will be returned.
	 * 
	 * @param data array to sort
	 * @return the inidices of the unsorted array values
	 * @throws NullPointerException if source array is {@code null}
	 */
	@Deprecated
	public static int[] indexAndSort(final double[] data) {
		checkNotNull(data, "Source array is null");
		List<Integer> indices = Ints.asList(new int[data.length]);
		for (int i = 0; i < indices.size(); i++) {
			indices.set(i, i);
		}
		Collections.sort(indices, new Comparator<Integer>() {
			@Override
			public int compare(Integer i1, Integer i2) {
				double d1 = data[i1];
				double d2 = data[i2];
				return (d1 < d2) ? -1 : (d1 == d2) ? 0 : 1;
			}
		});
		Arrays.sort(data);
		return Ints.toArray(indices);
	}
	
	/**
	 * Returns an index {@code List} that provides a pointers to sorted
	 * {@code data}. Let's say you have a number of {@code List<Double>}s and
	 * want to sort them all according to one of your choosing. Supply this
	 * method with the desired {@code data} and use the returned indices view
	 * any of your arrays according to the sort order of the supplied
	 * {@code data}.
	 * 
	 * <p> <b>Notes:</b> <ul> <li>The supplied data should not be sorted</li>
	 * <li>This method does not modify the supplied {@code data} in any way</li>
	 * <li>Any {@code NaN}s in {@code data} are placed at the start of the sort
	 * order, regardless of sort direction</li> <ul> </p>
	 * 
	 * @param data to provide sort indices for
	 * @param ascending if {@code true}, descending if {@code false}
	 * @return an index {@code List}
	 */
	public static List<Integer> sortedIndices(List<Double> data,
			boolean ascending) {
		checkNotNull(data);
		List<Integer> indices = Ints.asList(indices(data.size()));
		Collections.sort(indices, new IndexComparator(data, ascending));
		return indices;
	}
	
	/*
	 * A comparator for ascending sorting of an index array based on the
	 * supplied double array of data.
	 */
	private static class IndexComparator implements Comparator<Integer> {
		List<Double> data;
		boolean ascending;
		IndexComparator(List<Double> data, boolean ascending) {
			this.data = data;
			this.ascending = ascending;
		}
		@Override
		public int compare(Integer i1, Integer i2) {
			double d1 = data.get(ascending ? i1 : i2);
			double d2 = data.get(ascending ? i2 : i1);
			return (d1 < d2) ? -1 : (d1 == d2) ? 0 : 1;
		}
	}

	public static void main(String[] args) {
		
		int len = 5;
		int[] idx = indices(0,len-1);
		System.out.println(Arrays.toString(idx));
		int[] pp = indices(len);
		System.out.println(Arrays.toString(pp));
//		BitSet bs1 = new BitSet(65);
//		System.out.println(bs1.size());
		
	}

	/**
	 * Returns an {@code int[]} of all values spanning {@code from} to
	 * {@code to}, inclusive. Sequence will be descending if {@code from} is
	 * greater than {@code to}.
	 * @param from start value
	 * @param to end value
	 * @return an int[] sequence 
	 */
	public static int[] indices(int from, int to) {
		int size = Math.abs(from - to) + 1;
		int[] indices = new int[size];
		int step = from < to ? 1 : -1;
		for (int i=0; i<size; i++ ) {
			indices[i] = from + i * step;
		}
		return indices;
	}

	/**
	 * Returns an {@code int[]} of values ascending from {@code 0} to
	 * {@code 1-length} that can be used for sorting.
	 * @param length
	 * @return an index array
	 * @see DataUtils#sortedIndices(List, boolean)
	 */
	public static int[] indices(int length) {
		return indices(0,length - 1);
	}

    /**
     * Creates an array of random {@code double} values.
     * @param length of output array
     * @return the array of random {@code double}s
     */
    public static double[] randomValues(int length) {
    	Random random = new Random();
        double[] values = new double[length];
        for (int i=0; i<length; i++) {
        	values[i] = random.nextDouble();
        }
        return values;
    }
    
	/**
	 * Returns the index of the minimum value in {@code data}.
	 * @param data
	 * @throws IllegalArgumentException if {@code data} is empty
	 * @return the index of the minimum value
	 */
	public static int minIndex(double... data) {
		checkArgument(checkNotNull(data).length > 0, "data is empty");
		int idx = 0;
		double min = data[0];
		for (int i = 1; i < data.length; i++)
			if (data[i] < min) {
				min = data[i];
				idx = i;
			}
		return idx;
	}

	/**
	 * Returns the index of the maximum value in {@code data}.
	 * @param data
	 * @throws IllegalArgumentException if {@code data} is empty
	 * @return the index of the maximum value
	 */
	public static int maxIndex(double... data) {
		checkArgument(checkNotNull(data).length > 0, "data is empty");
		int idx = 0;
		double max = data[0];
		for (int i = 1; i < data.length; i++)
			if (data[i] > max) {
				max = data[i];
				idx = i;
			}
		return idx;
	}
	
	/**
	 * Returns the key associated with the minimum value in the supplied
	 * dataset. One use case for this method might be to find the column key
	 * associated with a particular data set (or row) in a {@link Table}.
	 * @param keys to lookup in {@code Map<K, Double>}; if {@code keys == null}
	 *        then all values in the data set are evaluated
	 * @param data Map<K, Double> to operate on
	 * @throws IllegalArgumentException if {@code data} or {@code keys} are
	 *         empty
	 * @return the key corresponding to the minimum value
	 * @see Table
	 */
	public static <K> K minKey(Map<K, Double> data, Collection<K> keys) {
		checkArgument(checkNotNull(data).size() > 0, "data map is empty");
		if (keys == null) keys = data.keySet();
		checkArgument(keys.size() > 0, "keys are empty");
		K minKey = null;
		double minVal = Double.MAX_VALUE;
		for (K key : keys) {
			Double val = checkNotNull(data.get(key), "no value for key in map");
			if (val < minVal) {
				minVal = val;
				minKey = key;
			}
		}
		return minKey;
	}
	
	/**
	 * Returns an mutable {@code List<Integer>} indices corresponding to the
	 * 'set' bits of the supplied {@code BitSet}.
	 * @param bits to operate on
	 * @return the indices of 'set' bits
	 */
	public static List<Integer> bitsToIndices(BitSet bits) {
		int[] indices = new int[checkNotNull(bits).cardinality()];
		int idx = 0;
		 for (int i = bits.nextSetBit(0); i >= 0; i = bits.nextSetBit(i+1)) {
			 indices[idx++] = i;
		 }
		 return Ints.asList(indices);
	}
	
    /**
	 * Nearest neighbor binning algorithm after Silverman, B. W. (1986),
	 * <em>Density Estimation for Statistics and Data Analysis</em>, Chapman
	 * &amp; Hall, New York. This method is a density estimator that uses
	 * variable width binning with a fixed sample size per bin that better
	 * reflects the distribution of the underlying data. It is particularly
	 * useful when workgin with power-law distributed data. Bin widths are
	 * computed as the difference between the last values in adjacent bins. In
	 * the case of the 1st bin, the supplied origin is taken as the "last value"
	 * of the previous bin. Bin positions are set from the median value in each
	 * bin. Note that the supplied {@code data} is not modified; this
	 * method uses a copy internally. In most cases, data will be fairly
	 * continuous in X, however, for small {@code size}s it's possible to
	 * have bins of identical values such that corresponding bin value is
	 * Infinity. Such values are not included in the resultant data set.
	 * 
	 * @param data to be binned
	 * @param origin for binning
	 * @param size of each bin
	 * @return an {@code XY_DataSet} of the binned distribution or
	 *         {@code null} if the binned distribution is empty
	 * @throws NullPointerException if the supplied {@code data} is
	 *         {@code null}
	 * @throws IllegalArgumentException if supplied {@code data} is empty,
	 *         the bin {@code size} is &lt;1, or the {@code origin} is
	 *         greater than all {@code data} values
	 */
    @Deprecated
	public static DefaultXY_DataSet nearestNeighborHist(double[] data, double origin,
			int size) {
		checkNotNull(data, "Supplied data is null");
		checkArgument(data.length > 0, "Supplied data is empty");
		checkArgument(size > 0, "Bin size can't be less than 1");
		double[] localData = Arrays.copyOf(data, data.length);
		Arrays.sort(localData);
		int startIdx = Arrays.binarySearch(localData, origin);
		checkArgument(startIdx < localData.length,
			"Origin is greater than all data values");
		startIdx = (startIdx > 0) ? startIdx : -startIdx - 1;
		// for multipe identical values, binary search may not return
		// the lowest index so walk down
		while (startIdx > 0 && origin == localData[startIdx-1]) startIdx--;
		// trim data
		localData = Arrays.copyOfRange(localData, startIdx, localData.length);
		int binCount = (int) Math.floor(localData.length / size);
		// bail on an empty distribution
		if (binCount == 0) return null;
		List<Double> x = new ArrayList<Double>();
		List<Double> y = new ArrayList<Double>();
		double binLo, binHi, binDelta;
		for (int i = 0; i < binCount; i++) {
			int datIdx = i * size;
			binLo = (i == 0) ? origin : localData[datIdx-1];
			binHi = localData[datIdx + size - 1];
			binDelta = binHi - binLo;
			// bail on intervals of identical values
			if (binDelta == 0) continue;
			y.add(size / (binHi - binLo));
			x.add(StatUtils.percentile(localData, datIdx, size, 50.0));
		}
		// bail on empty distribution
		return (x.isEmpty()) ? null : new DefaultXY_DataSet(x, y);
	}


	// TODO test; instances should be replaced with statistical summaries
	/**
	 * Class for tracking the minimum and maximum values of a set of data.
	 */
	public static class MinMaxAveTracker {
		private double min = Double.POSITIVE_INFINITY;
		private double max = Double.NEGATIVE_INFINITY;
		private double tot = 0;
		private int num = 0;

		/**
		 * Add a new value to the tracker. Min/Max/Average will be updated.
		 * 
		 * @param val value to be added
		 */
		public void addValue(double val) {
			if (val < min) min = val;
			if (val > max) max = val;
			tot += val;
			num++;
		}

		/**
		 * Returns the minimum value that has been added to this tracker, or positive infinity if
		 * no values have been added.
		 * 
		 * @return minimum value
		 */
		public double getMin() {
			return min;
		}

		/**
		 * Returns the maximum value that has been added to this tracker, or negative infinity if
		 * no values have been added.
		 * 
		 * @return maximum value
		 */
		public double getMax() {
			return max;
		}

		/**
		 * Computes the average of all values that have been added to this tracker.
		 * 
		 * @return the average of all values that have been added to this tracker.
		 */
		public double getAverage() {
			return tot / num;
		}

		/**
		 * 
		 * @return total number of values added to this tracker.
		 */
		public int getNum() {
			return num;
		}

		@Override
		public String toString() {
			return "min: " + min + ", max: " + max + ", avg: " + getAverage();
		}
	}
	
	
	// TODO wtf
	/**
	 * 
	 * @param unsorted
	 * @return median of the array of values. if values are already sorted, use median_sorted
	 */
	public static double median(double[] unsorted) {
		double[] sorted = Arrays.copyOf(unsorted, unsorted.length);
		Arrays.sort(sorted);
		return median_sorted(sorted);
	}
	
	/**
	 * @param sorted
	 * @return median of the sorted array of values
	 */
	public static double median_sorted(double[] sorted) {
		if (sorted.length % 2 == 1)
			return sorted[(sorted.length+1)/2-1];
		else
		{
			double lower = sorted[sorted.length/2-1];
			double upper = sorted[sorted.length/2];

			return (lower + upper) * 0.5;
		}	
	}

}
