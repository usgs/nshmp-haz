package org.opensha2.data;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkPositionIndex;
import static com.google.common.math.DoubleMath.fuzzyEquals;
import static java.lang.Double.NaN;
import static java.lang.Double.POSITIVE_INFINITY;
import static java.lang.Double.isNaN;
import static java.math.BigDecimal.ROUND_HALF_UP;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.google.common.base.Function;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.ImmutableSortedSet.Builder;
import com.google.common.collect.Lists;
import com.google.common.collect.Range;
import com.google.common.collect.Table;
import com.google.common.math.DoubleMath;
import com.google.common.primitives.Doubles;
import com.google.common.primitives.Ints;

/**
 * Utilities for operating on {@code double}-valued data.
 * 
 * <p>The methods of this class generally operate on data in place and
 * universally throw a {@code NullPointerException} or
 * {@code IllegalArguementException} when supplied with a {@code null} or empty
 * data set. Those methods that combine multiple data sets throw an
 * {@code IllegalArguementException} if the data sets are not the same size.
 * Also, no checking for finiteness (e.g. see {@link Doubles#isFinite(double)}
 * is performed on supplied data, methods do not check for over/underflow, and
 * logarithm operations do not check for negative values. Buyer beware.</p>
 * 
 * <p>Methods that return a result or information about a supplied data set will
 * typically take a {@code Collection<Double>} as an argument, whereas methods
 * that transform data in place will only take {@code List<Double>}s.</p>
 * 
 * <p>For other useful {@code Double} utilities, see the Google Guava
 * {@link Doubles} class.</p>
 * 
 * @author Peter Powers
 * @see Doubles
 */
public final class DataUtils {

	/*
	 * NOTE: Transform Functions vs Pure Iteration
	 * 
	 * The original implementation of this class used the built-in transform()
	 * methods and math Functions to operate on data arrays. Tests showed the
	 * Function approach to be only marginally slower, but much more processor
	 * intensive suggesting there would be a performance penalty in
	 * multi-threaded applications.
	 */

	private DataUtils() {}

	/**
	 * Add a {@code term} to the elements of {@code data} in place without
	 * checking for over/underflow.
	 * 
	 * @param data to operate on
	 * @param term to add
	 * @return a reference to the supplied {@code data}
	 */
	public static double[] add(double term, double... data) {
		validateDataArray(data);
		return uncheckedAdd(term, data);
	}

	static double[] uncheckedAdd(double term, double[] data) {
		for (int i = 0; i < data.length; i++) {
			data[i] += term;
		}
		return data;
	}

	/**
	 * Add a {@code term} to the elements of {@code data} in place without
	 * checking for over/underflow.
	 * 
	 * @param data to operate on
	 * @param term to add
	 * @return a reference to the supplied {@code data}
	 */
	public static List<Double> add(double term, List<Double> data) {
		validateDataCollection(data);
		for (int i = 0; i < data.size(); i++) {
			data.set(i, data.get(i) + term);
		}
		return data;
	}

	/**
	 * Add the values of {@code data2} to {@code data1} in place without
	 * checking for over/underflow.
	 * 
	 * @param data1
	 * @param data2
	 * @return a reference to {@code data1}
	 */
	public static double[] add(double[] data1, double[] data2) {
		validateDataArrays(data1, data2);
		return uncheckedAdd(data1, data2);
	}

	static double[] uncheckedAdd(double[] data1, double[] data2) {
		for (int i = 0; i < data1.length; i++) {
			data1[i] += data2[i];
		}
		return data1;
	}

	/**
	 * Add the values of {@code data2} to {@code data1} in place without
	 * checking for over/underflow.
	 * 
	 * @param data1
	 * @param data2
	 * @return a reference to {@code data1}
	 */
	public static double[][] add(double[][] data1, double[][] data2) {
		validateDataArrays(data1, data2);
		for (int i = 0; i < data1.length; i++) {
			add(data1[i], data2[i]);
		}
		return data1;
	}

	/**
	 * Add the values of {@code data2} to {@code data1} in place without
	 * checking for over/underflow.
	 * 
	 * @param data1
	 * @param data2
	 * @return a reference to {@code data1}
	 */
	public static double[][][] add(double[][][] data1, double[][][] data2) {
		validateDataArrays(data1, data2);
		for (int i = 0; i < data1.length; i++) {
			add(data1[i], data2[i]);
		}
		return data1;
	}

	/**
	 * Add the values of {@code data2} to {@code data1} in place without
	 * checking for over/underflow.
	 * 
	 * @param data1
	 * @param data2
	 * @return a reference to {@code data1}
	 */
	public static List<Double> add(List<Double> data1, List<Double> data2) {
		validateDataCollections(data1, data2);
		for (int i = 0; i < data1.size(); i++) {
			data1.set(i, data1.get(i) + data2.get(i));
		}
		return data1;
	}

	/**
	 * Subtract the values of {@code data2} from {@code data1} in place without
	 * checking for over/underflow. To subtract a term from every value of a
	 * dataset, use {@link #add(double, double...)} with a negative addend.
	 * 
	 * @param data1
	 * @param data2
	 * @return a reference to {@code data1}
	 */
	public static double[] subtract(double[] data1, double[] data2) {
		validateDataArrays(data1, data2);
		return uncheckedSubtract(data1, data2);
	}

	static double[] uncheckedSubtract(double[] data1, double[] data2) {
		for (int i = 0; i < data1.length; i++) {
			data1[i] -= data2[i];
		}
		return data1;
	}

	/**
	 * Subtract the values of {@code data2} from {@code data1} in place without
	 * checking for over/underflow. To subtract a term from every value of a
	 * dataset, use {@link #add(double, List)} with a negative addend.
	 * 
	 * @param data1
	 * @param data2
	 * @return a reference to {@code data1}
	 */
	public static List<Double> subtract(List<Double> data1, List<Double> data2) {
		validateDataCollections(data1, data2);
		for (int i = 0; i < data1.size(); i++) {
			data1.set(i, data1.get(i) - data2.get(i));
		}
		return data1;
	}

	/**
	 * Multiply ({@code scale}) the elements of {@code data} in place without
	 * checking for over/underflow.
	 * 
	 * @param data to operate on
	 * @param scale factor
	 * @return a reference to the supplied {@code data}
	 */
	public static double[] multiply(double scale, double... data) {
		validateDataArray(data);
		return uncheckedMultiply(scale, data);
	}

	static double[] uncheckedMultiply(double scale, double... data) {
		for (int i = 0; i < data.length; i++) {
			data[i] *= scale;
		}
		return data;
	}

	/**
	 * Multiply ({@code scale}) the elements of {@code data} in place without
	 * checking for over/underflow.
	 * 
	 * @param data to operate on
	 * @param scale factor
	 * @return a reference to the supplied {@code data}
	 */
	public static List<Double> multiply(double scale, List<Double> data) {
		validateDataCollection(data);
		for (int i = 0; i < data.size(); i++) {
			data.set(i, data.get(i) * scale);
		}
		return data;
	}

	/**
	 * Multiply the elements of {@code data1} by the elements of {@code data2}
	 * in place without checking for over/underflow.
	 * 
	 * @param data1
	 * @param data2
	 * @return a reference to {@code data1}
	 */
	public static double[] multiply(double[] data1, double[] data2) {
		validateDataArrays(data1, data2);
		return uncheckedMultiply(data1, data2);
	}

	static double[] uncheckedMultiply(double[] data1, double[] data2) {
		for (int i = 0; i < data1.length; i++) {
			data1[i] *= data2[i];
		}
		return data1;
	}

	/**
	 * Multiply the elements of {@code data1} by the elements of {@code data2}
	 * in place without checking for over/underflow.
	 * 
	 * @param data1
	 * @param data2
	 * @return a reference to {@code data1}
	 */
	public static List<Double> multiply(List<Double> data1, List<Double> data2) {
		validateDataCollections(data1, data2);
		for (int i = 0; i < data1.size(); i++) {
			data1.set(i, data1.get(i) * data2.get(i));
		}
		return data1;
	}

	/**
	 * Divide the elements of {@code data1} by the elements of {@code data2} in
	 * place without checking for over/underflow. To divide every value of a
	 * dataset by some term, use {@link #multiply(double, double...)} with
	 * 1/divisor.
	 * 
	 * @param data1
	 * @param data2
	 * @return a reference to {@code data1}
	 */
	public static double[] divide(double[] data1, double[] data2) {
		validateDataArrays(data1, data2);
		return uncheckedDivide(data1, data2);
	}

	static double[] uncheckedDivide(double[] data1, double[] data2) {
		for (int i = 0; i < data1.length; i++) {
			data1[i] /= data2[i];
		}
		return data1;
	}

	/**
	 * Divide the elements of {@code data1} by the elements of {@code data2} in
	 * place without checking for over/underflow. To divide every value of a
	 * dataset by some term, use {@link #multiply(double, List)} with 1/divisor.
	 * 
	 * @param data1
	 * @param data2
	 * @return a reference to {@code data1}
	 */
	public static List<Double> divide(List<Double> data1, List<Double> data2) {
		validateDataCollections(data1, data2);
		for (int i = 0; i < data1.size(); i++) {
			data1.set(i, data1.get(i) / data2.get(i));
		}
		return data1;
	}

	/**
	 * Set every element of {@code data} to its absolute value in place.
	 * 
	 * @param data to operate on
	 * @return a reference to the supplied {@code data}
	 * @see Math#abs(double)
	 */
	public static double[] abs(double... data) {
		validateDataArray(data);
		for (int i = 0; i < data.length; i++) {
			data[i] = Math.abs(data[i]);
		}
		return data;
	}

	/**
	 * Set every element of {@code data} to its absolute value in place.
	 * 
	 * @param data to operate on
	 * @return a reference to the supplied {@code data}
	 * @see Math#abs(double)
	 */
	public static List<Double> abs(List<Double> data) {
		validateDataCollection(data);
		for (int i = 0; i < data.size(); i++) {
			data.set(i, Math.abs(data.get(i)));
		}
		return data;
	}

	/**
	 * Apply the exponential function to every element of {@code data} in place.
	 * 
	 * @param data to operate on
	 * @return a reference to the supplied {@code data}
	 * @see Math#exp(double)
	 */
	public static double[] exp(double... data) {
		validateDataArray(data);
		for (int i = 0; i < data.length; i++) {
			data[i] = Math.exp(data[i]);
		}
		return data;
	}

	/**
	 * Apply the exponential function to every element of {@code data} in place.
	 * 
	 * @param data to operate on
	 * @return a reference to the supplied {@code data}
	 * @see Math#exp(double)
	 */
	public static List<Double> exp(List<Double> data) {
		validateDataCollection(data);
		for (int i = 0; i < data.size(); i++) {
			data.set(i, Math.exp(data.get(i)));
		}
		return data;
	}

	/**
	 * Apply the natural log function to every element of {@code data} in place.
	 * 
	 * @param data to operate on
	 * @return a reference to the supplied {@code data}
	 * @see Math#log(double)
	 */
	public static double[] ln(double... data) {
		validateDataArray(data);
		for (int i = 0; i < data.length; i++) {
			data[i] = Math.log(data[i]);
		}
		return data;
	}

	/**
	 * Apply the natural log function to every element of {@code data} in place.
	 * 
	 * @param data to operate on
	 * @return a reference to the supplied {@code data}
	 * @see Math#log(double)
	 */
	public static List<Double> ln(List<Double> data) {
		validateDataCollection(data);
		for (int i = 0; i < data.size(); i++) {
			data.set(i, Math.log(data.get(i)));
		}
		return data;
	}

	/**
	 * Apply the power of 10 function to every element {@code data} in place.
	 * 
	 * @param data to operate on
	 * @return a reference to the supplied {@code data}
	 * @see Math#pow(double, double)
	 */
	public static double[] pow10(double... data) {
		validateDataArray(data);
		for (int i = 0; i < data.length; i++) {
			data[i] = Math.pow(10, data[i]);
		}
		return data;
	}

	/**
	 * Apply the power of 10 function to every element {@code data} in place.
	 * 
	 * @param data to operate on
	 * @return a reference to the supplied {@code data}
	 * @see Math#pow(double, double)
	 */
	public static List<Double> pow10(List<Double> data) {
		validateDataCollection(data);
		for (int i = 0; i < data.size(); i++) {
			data.set(i, Math.pow(10, data.get(i)));
		}
		return data;
	}

	/**
	 * Apply the base-10 log function to every element of {@code data} in place.
	 * 
	 * @param data to operate on
	 * @return a reference to the supplied {@code data}
	 * @see Math#log10(double)
	 */
	public static double[] log(double... data) {
		validateDataArray(data);
		for (int i = 0; i < data.length; i++) {
			data[i] = Math.log10(data[i]);
		}
		return data;
	}

	/**
	 * Apply the base-10 log function to every element of {@code data} in place.
	 * 
	 * @param data to operate on
	 * @return a reference to the supplied {@code data}
	 * @see Math#log10(double)
	 */
	public static List<Double> log(List<Double> data) {
		validateDataCollection(data);
		for (int i = 0; i < data.size(); i++) {
			data.set(i, Math.log10(data.get(i)));
		}
		return data;
	}

	/**
	 * Flip the sign of every element of {@code data} in place.
	 * 
	 * @param data to operate on
	 * @return a reference to the supplied {@code data}
	 */
	public static double[] flip(double... data) {
		return multiply(-1, data);
	}

	static double[] uncheckedFlip(double... data) {
		return uncheckedMultiply(-1, data);
	}

	/**
	 * Flip the sign of every element of {@code data} in place.
	 * 
	 * @param data to operate on
	 * @return a reference to the supplied {@code data}
	 */
	public static List<Double> flip(List<Double> data) {
		return multiply(-1, data);
	}

	/**
	 * Sum of the elements of {@code data} without checking for over/underflow.
	 * Method returns {@code Double.NaN} if {@code data} contains
	 * {@code Double.NaN}.
	 * 
	 * @param data to sum
	 * @return the sum of the supplied values
	 */
	public static double sum(double... data) {
		validateDataArray(data);
		double sum = 0;
		for (double d : data) {
			sum += d;
		}
		return sum;
	}

	/**
	 * Sum of the elements of {@code data} without checking for over/underflow.
	 * Method returns {@code Double.NaN} if {@code data} contains
	 * {@code Double.NaN}.
	 * 
	 * @param data to sum
	 * @return the sum of the supplied values
	 */
	public static double sum(Collection<Double> data) {
		validateDataCollection(data);
		double sum = 0;
		for (double d : data) {
			sum += d;
		}
		return sum;
	}

	/**
	 * Transform {@code data} by a {@code function} in place without checking
	 * for over/underflow.
	 * 
	 * @param function to apply
	 * @param data to operate on
	 * @return a reference to the supplied {@code data}
	 */
	public static double[] transform(Function<Double, Double> function, double... data) {
		checkNotNull(function);
		validateDataArray(data);
		return uncheckedTransform(function, data);
	}

	static double[] uncheckedTransform(Function<Double, Double> function, double... data) {
		for (int i = 0; i < data.length; i++) {
			data[i] = function.apply(data[i]);
		}
		return data;
	}

	/**
	 * Transform {@code data} by a {@code function} in place without checking
	 * for over/underflow.
	 * 
	 * @param function to apply
	 * @param data to operate on
	 * @return a reference to the supplied {@code data}
	 */
	public static List<Double> transform(Function<Double, Double> function, List<Double> data) {
		checkNotNull(function);
		validateDataCollection(data);
		for (int i = 0; i < data.size(); i++) {
			data.set(i, function.apply(data.get(i)));
		}
		return data;
	}

	/**
	 * Find the index of the minimum value in {@code data}.
	 * 
	 * @param data to evaluate
	 * @return the index of the minimum value
	 */
	public static int minIndex(double... data) {
		validateDataArray(data);
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
	 * Find the index of the maximum value in {@code data}.
	 * 
	 * @param data to evaluate
	 * @return the index of the maximum value
	 */
	public static int maxIndex(double... data) {
		validateDataArray(data);
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
	 * Determine whether {@code data} are all positive.
	 * 
	 * @param data to evaluate
	 * @return {@code true} if all values are ≥0; {@code false} otherwise
	 */
	public static boolean arePositive(double... data) {
		validateDataArray(data);
		for (double d : data) {
			if (d >= 0) continue;
			return false;
		}
		return true;
	}

	/**
	 * Determine whether {@code data} are all positive.
	 * 
	 * @param data to evaluate
	 * @return {@code true} if all values are ≥0
	 */
	public static boolean arePositive(Collection<Double> data) {
		validateDataCollection(data);
		for (double d : data) {
			if (d >= 0) continue;
			return false;
		}
		return true;
	}

	/**
	 * Ensures positivity of values by adding {@code Math.abs(min(data))} if
	 * {@code min < 0}. Operation performed in place on supplied array.
	 * 
	 * @param data to operate on
	 * @return a reference to the supplied data, positivized if necessary
	 */
	public static double[] positivize(double... data) {
		validateDataArray(data);
		double min = Doubles.min(data);
		if (min >= 0) return data;
		min = Math.abs(min);
		return add(min, data);
	}

	/**
	 * Determine whether the elements of {@code data} increase or decrease
	 * monotonically, with a {@code strict} flag indicating if identical
	 * adjacent elements are forbidden. The {@code strict} flag could be
	 * {@code true} if checking the x-values of a function for any steps, or
	 * {@code false} if checking the y-values of a cumulative distribution
	 * function, which are commonly constant.
	 * 
	 * @param increasing if {@code true}, descending if {@code false}
	 * @param strict {@code true} if data must always increase or decrease,
	 *        {@code false} if identical adjacent values are permitted
	 * @param data to evaluate
	 * @return {@code true} if monotonic, {@code false} otherwise
	 */
	public static boolean isMonotonic(boolean increasing, boolean strict, double... data) {
		validateDataArray(data);
		double[] diff = diff(data);
		if (!increasing) flip(diff);
		double min = Doubles.min(diff);
		return (strict) ? min > 0 : min >= 0;
	}

	/**
	 * Compute difference between {@code test} and {@code target}, relative to
	 * {@code target}, as a percent. If {@code target} is 0, method returns 0 if
	 * {@code test} is also 0, otherwise {@code Double.POSITIVE_INFINITY}. If
	 * either value is {@code Double.NaN}, method returns {@code Double.NaN}.
	 * 
	 * @param test value
	 * @param target value
	 * @return the percent difference
	 */
	public static double percentDiff(double test, double target) {
		if (isNaN(target) || isNaN(test)) return NaN;
		if (target == 0) return test == 0 ? 0 : POSITIVE_INFINITY;
		return Math.abs(test - target) / target * 100d;
	}

	/**
	 * Build an array of the differences between the adjacent elements of
	 * {@code data}. Method returns results in a new array that has
	 * {@code data.length - 1} where differences are computed per
	 * {@code data[i+1] - data[i]}.
	 * 
	 * @param data to difference
	 * @return the differences between adjacent values
	 * @throws IllegalArgumentException if {@code data.legth < 2}
	 */
	public static double[] diff(double... data) {
		checkArgument(checkNotNull(data).length > 1);
		int size = data.length - 1;
		double[] diff = new double[size];
		for (int i = 0; i < size; i++) {
			diff[i] = data[i + 1] - data[i];
		}
		return diff;
	}

	private static final Range<Double> POS_RANGE = Range.open(0d, Double.POSITIVE_INFINITY);

	/**
	 * Normalize the elements of {@code data} to weights, in place, such that
	 * they sum to 1.
	 * 
	 * @param data to normalize
	 * @return a reference to the supplied array
	 * @throws IllegalArgumentException if {@code data} is empty, contains any
	 *         {@code Double.NaN} or negative values, or sums to a value outside
	 *         the range {@code (0..Double.POSITIVE_INFINITY), exclusive}
	 */
	public static double[] normalize(double... data) {
		validateDataArray(data);
		checkArgument(arePositive(data));
		double sum = sum(data);
		checkArgument(POS_RANGE.contains(sum));
		double scale = 1d / sum;
		return multiply(scale, data);
	}

	/**
	 * Normalize the elements of {@code data} to weights, in place, such that
	 * they sum to 1.
	 * 
	 * @param data to normalize
	 * @return a reference to the supplied array
	 * @throws IllegalArgumentException if {@code data} is empty, contains any
	 *         {@code Double.NaN} or negative values, or sums to a value outside
	 *         the range {@code (0..Double.POSITIVE_INFINITY), exclusive}
	 */
	public static List<Double> normalize(List<Double> data) {
		validateDataCollection(data);
		checkArgument(arePositive(data));
		double sum = sum(data);
		checkArgument(POS_RANGE.contains(sum));
		double scale = 1d / sum;
		return multiply(scale, data);
	}

	/**
	 * Create a {@code double[]} of pseudorandom values.
	 * 
	 * @param size of the output array
	 * @param seed for random number generator; may be {@code null}
	 * @return an array of random {@code double}s
	 */
	public static double[] randomValues(int size, Long seed) {
		Random random = (seed != null) ? new Random(seed) : new Random();
		double[] values = new double[size];
		for (int i = 0; i < size; i++) {
			values[i] = random.nextDouble();
		}
		return values;
	}

	/* * * * * * * * * * * * VALIDATION * * * * * * * * * * * */

	private static void validateDataArray(double... data) {
		checkArgument(checkNotNull(data).length > 0);
	}

	private static void validateDataCollection(Collection<? extends Number> data) {
		checkArgument(checkNotNull(data).size() > 0);
	}

	private static void validateDataArrays(double[] data1, double[] data2) {
		checkArgument(checkNotNull(data1).length == checkNotNull(data2).length);
	}

	private static void validateDataArrays(double[][] data1, double[][] data2) {
		/* Only checks outer array; operations check inners. */
		checkArgument(checkNotNull(data1).length == checkNotNull(data2).length);
	}

	private static void validateDataArrays(double[][][] data1, double[][][] data2) {
		/* Only checks outer array; operations check inners. */
		checkArgument(checkNotNull(data1).length == checkNotNull(data2).length);
	}

	private static void validateDataCollections(Collection<? extends Number> data1,
			Collection<? extends Number> data2) {
		checkArgument(checkNotNull(data1).size() == checkNotNull(data2).size());
	}

	private static void validateIndices(Collection<Integer> indices, int size) {
		validateDataCollection(indices);
		for (int index : indices) {
			checkPositionIndex(index, size);
		}
	}

	/**
	 * Verify that a value falls within a specified {@link Range}. Method
	 * returns the supplied value and can be used inline.
	 * 
	 * @param range of allowable values
	 * @param value to validate
	 * @param label indicating type of value being checked; used in exception
	 *        message; may be {@code null}
	 * @return the supplied value
	 * @throws IllegalArgumentException if value is {@code NaN}
	 * @see Range
	 */
	public static double validate(Range<Double> range, String label, double value) {
		uncheckedValidate(checkNotNull(range), value, label);
		return value;
	}

	/**
	 * Verify that the domain of a {@code double[]} does not exceed that of the
	 * supplied {@link Range}. Method returns the supplied values and can be
	 * used inline.
	 * 
	 * @param range of allowable values
	 * @param values to validate
	 * @param label indicating type of value being checked; used in exception
	 *        message; may be {@code null}
	 * @return the supplied values
	 * @throws IllegalArgumentException if any value is {@code NaN}
	 * @see Range
	 */
	public static double[] validate(Range<Double> range, String label, double... values) {
		checkNotNull(range);
		validateDataArray(values);
		for (int i = 0; i < values.length; i++) {
			uncheckedValidate(range, values[i], label);
		}
		return values;
	}

	/* Does not check if range is null for more performant array checking */
	private static void uncheckedValidate(Range<Double> range, double value, String label) {
		checkArgument(!Double.isNaN(value), "NaN not allowed");
		checkArgument(range.contains(value), "%s value %s is not in range %s",
			Strings.nullToEmpty(label), value, range);
	}

	private static final Range<Double> WEIGHT_RANGE = Range.openClosed(0d, 1d);

	/**
	 * Confirm that a weight value is {@code 0.0 < weight ≤ 1.0}. Method returns
	 * the supplied value and can be used inline.
	 * 
	 * @param weight to validate
	 * @return the supplied {@code weight} value
	 */
	public static double validateWeight(double weight) {
		uncheckedValidate(WEIGHT_RANGE, weight, "Weight");
		return weight;
	}

	/**
	 * Acceptable tolerance when summing weights and comparing to 1.0. Currently
	 * set to 1e-8.
	 */
	public static final double WEIGHT_TOLERANCE = 1e-8;

	/**
	 * Confirm that a {@code Collection<Double>} of weights sums to 1.0 within
	 * {@link #WEIGHT_TOLERANCE}.
	 * 
	 * @param weights to validate
	 * @see #WEIGHT_TOLERANCE
	 */
	public static void validateWeights(Collection<Double> weights) {
		double sum = sum(weights);
		checkArgument(fuzzyEquals(sum, 1.0, WEIGHT_TOLERANCE),
			"Weights Σ %s = %s ≠ 1.0", weights, sum);
	}

	/**
	 * Confirm that for a specified range {@code [min, max]} that
	 * {@code max > min}, {@code Δ > 0.0}, & {@code Δ < max - min}. Use this
	 * prior to creating a set of values discretized in {@code Δ}. Returns
	 * {@code Δ} for use inline.
	 * @param min value
	 * @param max value
	 * @param Δ discretization delta
	 */
	public static double validateDelta(double min, double max, double Δ) {
		checkArgument(max > min, "min [%s] > max [%s]", min, max);
		checkArgument(Δ > 0.0, "Invalid Δ [%s]", Δ);
		checkArgument(Δ <= max - min, "Δ [%s] > max - min [%s]", max - min);
		return Δ;
	}

	/*
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * Everything below needs review
	 */

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

	// private static final Range<Double> WEIGHT_RANGE = Range.openClosed(0d,
	// 1d);

	// /**
	// * Return the index of the first array element that is equal than
	// * {@code value}.
	// *
	// * @param data to examine
	// * @param value to search for
	// * @throws IllegalArgumentException all elements of {@code data} are less
	// than {@code value}. Exception is thrown lazily
	// * when iteration completes without any {@code data} element exceeding
	// {@code value}.
	// */
	// public static int firstGreaterThanIndex(int[] data, double value) {
	// checkNotNull(data);
	// for (int i = 0; i < data.length; i++) {
	// int cf = DoubleMath.fuzzyCompare(data[i], value, 1e-8);
	// if (cf > 0) return i;
	// }
	// String mssg =
	// String.format("Value [%s] is larger than maximum array value [%s].",
	// value,
	// Doubles.max(data));
	// throw new IllegalArgumentException(mssg);
	// }

	/**
	 * Creates a sequence of evenly spaced values starting at {@code min} and
	 * ending at {@code max}. If {@code (max - min) / step} is not equivalent to
	 * an integer, the last step in the sequence will be {@code <step}. Unlike
	 * {@link #buildSequence(double, double, double, boolean)}, this method
	 * returns a sequence where any 'odd' values due to rounding errors have
	 * been removed, at least within the range of the specified {@code scale}
	 * (precision or number of decimal places).
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
	public static double[] buildCleanSequence(double min, double max, double step,
			boolean ascending, int scale) {
		double[] seq = buildSequence(min, max, step, ascending);
		return clean(scale, seq);
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
	public static double[] buildLogSequence(double min, double max, double step, boolean ascending) {
		double[] seq = buildSequence(Math.log(min), Math.log(max), Math.log(step), ascending);
		return exp(seq);
	}

	/**
	 * Creates a sequence of evenly spaced values starting at {@code min} and
	 * ending at {@code max}. If {@code (max - min) / step} is not integer
	 * valued, the last step in the sequence will be {@code <step}. If
	 * {@code min == max}, then an array containing a single value is returned.
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
	public static double[] buildSequence(double min, double max, double step, boolean ascending) {
		// if passed in arguments are NaN, +Inf, or -Inf, and step <= 0,
		// then capacity [c] will end up 0 because (int) NaN = 0, or outside the
		// range 1:10000
		checkArgument(min <= max, "min-max reversed");
		if (min == max) return new double[] { min };
		int c = (int) ((max - min) / step);
		checkArgument(c > 0 && c < MAX_SEQ_LEN, "sequence size");
		if (ascending) return buildSequence(min, max, step, c + 2);
		double[] descSeq = buildSequence(-max, -min, step, c + 2);
		return flip(descSeq);

		// TODO
		// double[] mags = DataUtils.buildSequence(5.05, 7.85, 0.1, true);
		// System.out.println(Arrays.toString(mags));
		// produces crummy values 2.449999999999999999 etc...
	}

	private static final int MAX_SEQ_LEN = 10001;
	private static final double SEQ_MAX_VAL_TOL = 0.000000000001;

	private static double[] buildSequence(double min, double max, double step, int capacity) {
		List<Double> seq = Lists.newArrayListWithCapacity(capacity);
		for (double val = min; val < max; val += step) {
			seq.add(val);
		}
		// do not add max if current max is equal to max wihthin tolerance
		if (!DoubleMath.fuzzyEquals(seq.get(seq.size() - 1), max, SEQ_MAX_VAL_TOL)) seq.add(max);
		return Doubles.toArray(seq);
	}

	/**
	 * Combine the supplied {@code sequences}. The y-values returned are the set
	 * of all supplied y-values. The x-values returned are the sum of the
	 * supplied x-values. When summing, x-values for points outside the original
	 * domain of a sequence are set to 0, while those inside the original domain
	 * are sampled via linear interpolation.
	 * 
	 * 
	 * @param sequences to combine
	 * @return a combined sequence
	 */
	@Deprecated public static XY_Sequence combine(Iterable<XY_Sequence> sequences) {

		// TODO I think we want to have interpolating and non-interpolating
		// flavors. Interpolating for visual presentation, non-interpolating
		// for re-use as MFD

		// create master x-value sequence
		Builder<Double> builder = ImmutableSortedSet.naturalOrder();
		for (XY_Sequence sequence : sequences) {
			builder.addAll(sequence.xValues());
		}
		double[] xMaster = Doubles.toArray(builder.build());

		// resample and combine sequences
		ArrayXY_Sequence combined = ArrayXY_Sequence.create(xMaster, null);
		for (XY_Sequence sequence : sequences) {
			// TODO need to disable extrapolation in Interpolation
			if (true) throw new UnsupportedOperationException();
			ArrayXY_Sequence resampled = ArrayXY_Sequence.resampleTo(sequence, xMaster);
			combined.add(resampled);
		}

		return combined;
	}

	/**
	 * A crude utility to clean double values to a specified scale/precision
	 * using {@code String.format(%.'scale'f)}.
	 * 
	 * @param data to operate on
	 * @param scale decimal precision
	 * @return a cleaned array
	 */
	public static double[] clean(int scale, double... data) {
		return transform(new Clean(scale), data);
	}

	private static class Clean implements Function<Double, Double> {
		private final String format;

		private Clean(int scale) {
			format = "%." + scale + "f";
		}

		@Override public Double apply(Double d) {
			return Double.parseDouble(String.format(format, d));
		}
	}

	// TODO clean
	// /**
	// * Validates the domain of a {@code double} data set. Method verifies
	// * that data values all fall between {@code min} and {@code max} range
	// * (inclusive). Empty arrays are ignored. If {@code min} is
	// * {@code Double.NaN}, no lower limit is imposed; the same holds true
	// * for {@code max}. {@code Double.NaN} values in {@code array}
	// * will validate.
	// *
	// * @param min minimum range value
	// * @param max maximum range value
	// * @param array to validate
	// * @throws IllegalArgumentException if {@code min > max}
	// * @throws IllegalArgumentException if any {@code array} value is out of
	// * range
	// * @deprecated Ranges should be used instead with NaNs throwing an
	// exception
	// */
	// @Deprecated
	// public final static void validate(double min, double max, double...
	// array) {
	// checkNotNull(array, "array");
	// for (int i = 0; i < array.length; i++) {
	// validate(min, max, array[i]);
	// }
	// }
	//
	// /**
	// * Verifies that a {@code double} data value falls within a specified
	// * minimum and maximum range (inclusive). If {@code min} is
	// * {@code Double.NaN}, no lower limit is imposed; the same holds true
	// * for {@code max}. A value of {@code Double.NaN} will always
	// * validate.
	// *
	// * @param min minimum range value
	// * @param max minimum range value
	// * @param value to check
	// * @throws IllegalArgumentException if {@code min > max}
	// * @throws IllegalArgumentException if value is out of range
	// * @deprecated Ranges should be used instead with NaNs throwing an
	// exception
	// */
	// @Deprecated
	// public final static void validate(double min, double max, double value) {
	// boolean valNaN = isNaN(value);
	// boolean minNaN = isNaN(min);
	// boolean maxNaN = isNaN(max);
	// boolean both = minNaN && maxNaN;
	// boolean neither = !(minNaN || maxNaN);
	// if (neither) checkArgument(min <= max, "min-max reversed");
	// boolean expression = valNaN || both ? true : minNaN
	// ? value <= max : maxNaN ? value >= min : value >= min &&
	// value <= max;
	// checkArgument(expression, "value");
	// }

	/**
	 * Create an {@code int[]} of values ascending from {@code 0} to
	 * {@code 1-size}.
	 * 
	 * @param size of output array
	 * @return an index array
	 */
	public static int[] indices(int size) {
		return indices(0, size - 1);
	}

	/**
	 * Create an {@code int[]} of values spanning {@code from} to {@code to},
	 * inclusive. Sequence will be descending if {@code from} is greater than
	 * {@code to}.
	 * 
	 * @param from start value
	 * @param to end value
	 * @return an int[] sequence
	 */
	public static int[] indices(int from, int to) {
		int size = Math.abs(from - to) + 1;
		int[] indices = new int[size];
		int step = from < to ? 1 : -1;
		for (int i = 0; i < size; i++) {
			indices[i] = from + i * step;
		}
		return indices;
	}

	/**
	 * Create an index {@code List} of pointers to sorted {@code data}. Say you
	 * have a number of {@code List<Double>}s and want to iterate them according
	 * to the sort order of one of them. Supply this method with the desired
	 * {@code data} and use the returned indices in a custom iterator, leaving
	 * all original data in place.
	 * 
	 * <p><b>Notes:</b><ul><li>The supplied data should not be sorted.</li>
	 * <li>This method does not modify the supplied {@code data} in any
	 * way.</li><li>Any {@code NaN}s in {@code data} are placed at the start of
	 * the sort order, regardless of sort direction.</li><ul></p>
	 * 
	 * @param data to provide sort indices for
	 * @param ascending if {@code true}, descending if {@code false}
	 * @return an index {@code List}
	 */
	public static List<Integer> sortedIndices(List<Double> data, boolean ascending) {
		validateDataCollection(data);
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

		@Override public int compare(Integer i1, Integer i2) {
			double d1 = data.get(ascending ? i1 : i2);
			double d2 = data.get(ascending ? i2 : i1);
			return (d1 < d2) ? -1 : (d1 == d2) ? 0 : 1;
		}
	}

	/*
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * TODO clean below
	 */

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
	 * Return an index {@code List<Integer>} corresponding to the 'set' bits of
	 * the supplied {@code BitSet}. The returned {@code List} is mutable.
	 * 
	 * @param bits to operate on
	 * @return the indices of 'set' bits
	 */
	public static List<Integer> bitsToIndices(BitSet bits) {
		int[] indices = new int[checkNotNull(bits).cardinality()];
		int idx = 0;
		for (int i = bits.nextSetBit(0); i >= 0; i = bits.nextSetBit(i + 1)) {
			indices[idx++] = i;
		}
		return Ints.asList(indices);
	}

	/**
	 * Return a {@code BitSet} with {@code capacity} and with all bits at
	 * {@code indices} 'set'.
	 * 
	 * @param indices to operate on
	 * @param capacity of returned {@code BitSet}
	 */
	public static BitSet indicesToBits(List<Integer> indices, int capacity) {
		checkArgument(capacity > 0, "BitSet capacity [%s] must be > 0", capacity);
		validateIndices(indices, capacity);
		BitSet bits = new BitSet(capacity);
		for (int index : indices) {
			bits.set(index);
		}
		return bits;
	}

	/**
	 * Nearest neighbor binning algorithm after Silverman, B. W. (1986),
	 * <em>Density Estimation for Statistics and Data Analysis</em>, Chapman &
	 * Hall, New York. This method is a density estimator that uses variable
	 * width binning with a fixed sample size per bin that better reflects the
	 * distribution of the underlying data. It is particularly useful when
	 * workgin with power-law distributed data. Bin widths are computed as the
	 * difference between the last values in adjacent bins. In the case of the
	 * 1st bin, the supplied origin is taken as the "last value" of the previous
	 * bin. Bin positions are set from the median value in each bin. Note that
	 * the supplied {@code data} is not modified; this method uses a copy
	 * internally. In most cases, data will be fairly continuous in X, however,
	 * for small {@code size}s it's possible to have bins of identical values
	 * such that corresponding bin value is Infinity. Such values are not
	 * included in the resultant data set.
	 * 
	 * @param data to be binned
	 * @param origin for binning
	 * @param size of each bin
	 * @return an {@code XY_DataGroup} of the binned distribution or
	 *         {@code null} if the binned distribution is empty
	 * @throws NullPointerException if the supplied {@code data} is {@code null}
	 * @throws IllegalArgumentException if supplied {@code data} is empty, the
	 *         bin {@code size} is <1, or the {@code origin} is greater than all
	 *         {@code data} values
	 */
	// NOTE commented out because unused; is probably useful and should be
	// archived
	// dependency on commons-math StatUtils.percentile
	// @Deprecated public static DefaultXY_DataSet nearestNeighborHist(double[]
	// data, double origin,
	// int size) {
	// checkNotNull(data, "Supplied data is null");
	// checkArgument(data.length > 0, "Supplied data is empty");
	// checkArgument(size > 0, "Bin size can't be less than 1");
	// double[] localData = Arrays.copyOf(data, data.length);
	// Arrays.sort(localData);
	// int startIdx = Arrays.binarySearch(localData, origin);
	// checkArgument(startIdx < localData.length,
	// "Origin is greater than all data values");
	// startIdx = (startIdx > 0) ? startIdx : -startIdx - 1;
	// // for multipe identical values, binary search may not return
	// // the lowest index so walk down
	// while (startIdx > 0 && origin == localData[startIdx - 1])
	// startIdx--;
	// // trim data
	// localData = Arrays.copyOfRange(localData, startIdx, localData.length);
	// int binCount = (int) Math.floor(localData.length / size);
	// // bail on an empty distribution
	// if (binCount == 0) return null;
	// List<Double> x = new ArrayList<Double>();
	// List<Double> y = new ArrayList<Double>();
	// double binLo, binHi, binDelta;
	// for (int i = 0; i < binCount; i++) {
	// int datIdx = i * size;
	// binLo = (i == 0) ? origin : localData[datIdx - 1];
	// binHi = localData[datIdx + size - 1];
	// binDelta = binHi - binLo;
	// // bail on intervals of identical values
	// if (binDelta == 0) continue;
	// y.add(size / (binHi - binLo));
	// x.add(StatUtils.percentile(localData, datIdx, size, 50.0));
	// }
	// // bail on empty distribution
	// return (x.isEmpty()) ? null : new DefaultXY_DataSet(x, y);
	// }

	/**
	 * Creates a new array from the values in a source array at the specified
	 * indices. Returned array is of same type as source.
	 * 
	 * @param array array source
	 * @param indices index values of items to select
	 * @return a new array of values at indices in source
	 * @throws NullPointerException if {@code array} or {@code indices} are
	 *         {@code null}
	 * @throws IllegalArgumentException if data object is not an array or if
	 *         data array is empty
	 * @throws IndexOutOfBoundsException if any indices are out of range
	 */
	@Deprecated public static Object arraySelect(Object array, int[] indices) {
		// NOTE was this from Temblor??
		checkNotNull(array, "Supplied data array is null");
		checkNotNull(indices, "Supplied index array is null");
		checkArgument(array.getClass().isArray(), "Data object supplied is not an array");
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

	// NOTE: Transform vs Pure Iteration: are transforms worth it, as cool
	// as they may be?
	//
	// transformTest() shows that the speed difference between a transform
	// based add() and pure indexed iteration is negligable. What's interesting
	// though is transform based operations use many more system resources
	// (watch a CPU monitor) suggesting that in multithreaded situations
	// performance would suffer. TODO get rid of 'em, they're fluffy

	// static void transformTest() {
	// int warmup = 100000;
	// int run = 1000000000;
	// final double[] data = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 9, 8, 7, 6, 5, 4,
	// 3, 2, 1 };
	//
	// // transform performance test
	// Stopwatch sw = Stopwatch.createUnstarted();
	// double[] data1 = Arrays.copyOf(data, data.length);
	// Random rand1 = new Random(1L);
	// double[] result1 = null;
	// // warmup 1
	// for (int i = 0; i < warmup; i++) {
	// result1 = add(rand1.nextDouble(), data1);
	// }
	// // run 1
	// data1 = Arrays.copyOf(data, data.length);
	// sw.start();
	// for (int i = 0; i < run; i++) {
	// result1 = add(rand1.nextDouble(), data1);
	// }
	// sw.stop();
	// System.out.println("transform");
	// System.out.println(Arrays.toString(result1));
	// System.out.println(sw.elapsed(TimeUnit.SECONDS));
	// System.out.println();
	//
	// sw.reset();
	// double[] data2 = Arrays.copyOf(data, data.length);
	// Random rand2 = new Random(1L);
	// double[] result2 = null;
	// // warmup 2
	// for (int i = 0; i < warmup; i++) {
	// result2 = altAdd(rand2.nextDouble(), data2);
	// }
	// // run 2
	// data2 = Arrays.copyOf(data, data.length);
	// sw.start();
	// for (int i = 0; i < run; i++) {
	// result2 = altAdd(rand2.nextDouble(), data2);
	// }
	// sw.stop();
	// System.out.println("pure iterator");
	// System.out.println(Arrays.toString(result2));
	// System.out.println(sw.elapsed(TimeUnit.SECONDS));
	//
	// }

}
