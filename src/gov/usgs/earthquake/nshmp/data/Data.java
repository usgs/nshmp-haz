package gov.usgs.earthquake.nshmp.data;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static gov.usgs.earthquake.nshmp.internal.TextUtils.NEWLINE;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleUnaryOperator;
import java.util.function.Function;

import com.google.common.base.Strings;
import com.google.common.collect.ContiguousSet;
import com.google.common.collect.DiscreteDomain;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.ImmutableSortedSet.Builder;
import com.google.common.collect.Lists;
import com.google.common.collect.Range;
import com.google.common.math.DoubleMath;
import com.google.common.primitives.Doubles;

import gov.usgs.earthquake.nshmp.util.Maths;

/**
 * Utilities for operating on {@code double}-valued data.
 *
 * <p>Unless otherwise noted, the methods of this class:
 *
 * <ul><li>Operate on data in place, returning a reference to the supplied
 * data.</li>
 * 
 * <li>Are not synchronized.</li>
 *
 * <li>Throw a {@code NullPointerException} when supplied with {@code null}
 * data.</li>
 *
 * <li>Return an empty array when attempting to transform each element of a
 * dataset for which no varargs elements have been supplied(e.g.
 * {@link #add(double, double...)}).
 *
 * <li>Throw an {@code IllegalArgumentException} if they operate on all elements
 * of a dataset to yield a singular numeric or boolean value, but the supplied
 * dataset is empty, with some documented exceptions.
 *
 * <li>Throw an {@code IllegalArguementException} if they operate on multiple
 * datasets and the datasets are not the same size.</li>
 *
 * <li>Do not check for finiteness (see {@link Doubles#isFinite(double)}). For
 * example, any method that operates on data containing {@code Double.NaN} or
 * infinite values will likely return {@code NaN}s or infinite values as a
 * result. See the Java {@link Math} class for details on the behavior of
 * individual functions referenced herein.</li>
 *
 * <li>Do not check for over/under flow.</li></ul>
 *
 * <p>Buyer beware.
 *
 * <p>Many methods in this class are overloaded with {@code Collection<Double>}
 * arguments. Those overloaded methods that return a single result or
 * information about a supplied data set typically require a
 * {@code Collection<Double>} as an argument, whereas methods that transform
 * data in place require a {@code List<Double>} subtype.
 *
 * <p>For other useful {@code Double} utilities, see the Google Guava
 * {@link Doubles} class.
 *
 * @author Peter Powers
 */
public final class Data {

  // TODO consider weighted sum
  
  /*
   * Developer notes:
   * -------------------------------------------------------------------------
   * Transform Functions vs Pure Iteration
   *
   * The original implementation of this class used the built-in transform()
   * methods and math Functions to operate on data arrays. Tests showed the
   * Function approach to be only marginally slower, but much more processor
   * intensive suggesting there would be a performance penalty in multi-threaded
   * applications.
   * 
   * Unchecked delegate methods for primitive arrays are supplied for package
   * level use where data integrity is assured.
   * -------------------------------------------------------------------------
   */

  private Data() {}

  /* * * * * * * * * * * * * OPERATORS * * * * * * * * * * * * */

  /**
   * Add a {@code term} to the elements of {@code data} in place.
   *
   * @param data to operate on
   * @param term to add
   * @return a reference to the supplied {@code data}
   */
  public static List<Double> add(double term, List<Double> data) {
    for (int i = 0; i < data.size(); i++) {
      data.set(i, data.get(i) + term);
    }
    return data;
  }

  /**
   * Add a {@code term} to the elements of {@code data} in place.
   *
   * @param data to operate on
   * @param term to add
   * @return a reference to the supplied {@code data}
   */
  public static double[] add(double term, double... data) {
    for (int i = 0; i < data.length; i++) {
      data[i] += term;
    }
    return data;
  }

  /**
   * Add a {@code term} to the elements of {@code data} in place.
   *
   * @param data to operate on
   * @param term to add
   * @return a reference to the supplied {@code data}
   */
  public static double[][] add(double term, double[][] data) {
    for (int i = 0; i < data.length; i++) {
      add(term, data[i]);
    }
    return data;
  }

  /**
   * Add a {@code term} to the elements of {@code data} in place.
   *
   * @param data to operate on
   * @param term to add
   * @return a reference to the supplied {@code data}
   */
  public static double[][][] add(double term, double[][][] data) {
    for (int i = 0; i < data.length; i++) {
      add(term, data[i]);
    }
    return data;
  }

  /**
   * Add the values of {@code data2} to {@code data1} in place.
   *
   * @param data1
   * @param data2
   * @return a reference to {@code data1}
   */
  public static List<Double> add(List<Double> data1, List<Double> data2) {
    checkSizes(data1, data2);
    for (int i = 0; i < data1.size(); i++) {
      data1.set(i, data1.get(i) + data2.get(i));
    }
    return data1;
  }

  /**
   * Add the values of {@code data2} to {@code data1} in place.
   *
   * @param data1
   * @param data2
   * @return a reference to {@code data1}
   */
  public static double[] add(double[] data1, double[] data2) {
    checkSizes(data1, data2);
    return uncheckedAdd(data1, data2);
  }

  static double[] uncheckedAdd(double[] data1, double[] data2) {
    for (int i = 0; i < data1.length; i++) {
      data1[i] += data2[i];
    }
    return data1;
  }

  /**
   * Add the values of {@code data2} to {@code data1} in place.
   *
   * @param data1
   * @param data2
   * @return a reference to {@code data1}
   */
  public static double[][] add(double[][] data1, double[][] data2) {
    checkSizes(data1, data2);
    for (int i = 0; i < data1.length; i++) {
      add(data1[i], data2[i]);
    }
    return data1;
  }

  static double[][] uncheckedAdd(double[][] data1, double[][] data2) {
    for (int i = 0; i < data1.length; i++) {
      uncheckedAdd(data1[i], data2[i]);
    }
    return data1;
  }

  /**
   * Add the values of {@code data2} to {@code data1} in place.
   *
   * @param data1
   * @param data2
   * @return a reference to {@code data1}
   */
  public static double[][][] add(double[][][] data1, double[][][] data2) {
    checkSizes(data1, data2);
    for (int i = 0; i < data1.length; i++) {
      add(data1[i], data2[i]);
    }
    return data1;
  }

  static double[][][] uncheckedAdd(double[][][] data1, double[][][] data2) {
    for (int i = 0; i < data1.length; i++) {
      uncheckedAdd(data1[i], data2[i]);
    }
    return data1;
  }

  /**
   * Adds the entries of {@code map2} to {@code map1} in place. If a key from
   * {@code map2} exists in {@code map1}, then the value for that key is added
   * to the corresponding value in {@code map1}. If no such key exists in map 1,
   * then the key and value from map2 are transferred as is. Note that this
   * method is <i>not</i> synchronized.
   *
   * @param map1
   * @param map2
   * @return a reference to {@code map1}
   */
  public static <T> Map<T, Double> add(Map<T, Double> map1, Map<T, Double> map2) {
    for (T key : map2.keySet()) {
      Double v2 = map2.get(key);
      Double v1 = (map1.containsKey(key)) ? map1.get(key) + v2 : v2;
      map1.put(key, v1);
    }
    return map1;
  }

  /**
   * Subtract the values of {@code data2} from {@code data1} in place. To
   * subtract a term from every value of a dataset, use
   * {@link #add(double, List)} with a negative addend.
   *
   * @param data1
   * @param data2
   * @return a reference to {@code data1}
   */
  public static List<Double> subtract(List<Double> data1, List<Double> data2) {
    checkSizes(data1, data2);
    for (int i = 0; i < data1.size(); i++) {
      data1.set(i, data1.get(i) - data2.get(i));
    }
    return data1;
  }

  /**
   * Subtract the values of {@code data2} from {@code data1} in place. To
   * subtract a term from every value of a dataset, use
   * {@link #add(double, double...)} with a negative addend.
   *
   * @param data1
   * @param data2
   * @return a reference to {@code data1}
   */
  public static double[] subtract(double[] data1, double[] data2) {
    checkSizes(data1, data2);
    return uncheckedSubtract(data1, data2);
  }

  static double[] uncheckedSubtract(double[] data1, double[] data2) {
    for (int i = 0; i < data1.length; i++) {
      data1[i] -= data2[i];
    }
    return data1;
  }

  /**
   * Multiply ({@code scale}) the elements of {@code data} in place.
   *
   * @param data to operate on
   * @param scale factor
   * @return a reference to the supplied {@code data}
   */
  public static List<Double> multiply(double scale, List<Double> data) {
    for (int i = 0; i < data.size(); i++) {
      data.set(i, data.get(i) * scale);
    }
    return data;
  }

  /**
   * Multiply ({@code scale}) the elements of {@code data} in place.
   *
   * @param data to operate on
   * @param scale factor
   * @return a reference to the supplied {@code data}
   */
  public static double[] multiply(double scale, double... data) {
    for (int i = 0; i < data.length; i++) {
      data[i] *= scale;
    }
    return data;
  }

  /**
   * Multiply ({@code scale}) the elements of {@code data} in place.
   *
   * @param data to operate on
   * @param scale factor
   * @return a reference to the supplied {@code data}
   */
  public static double[][] multiply(double scale, double[][] data) {
    for (int i = 0; i < data.length; i++) {
      multiply(scale, data[i]);
    }
    return data;
  }

  /**
   * Multiply ({@code scale}) the elements of {@code data} in place.
   *
   * @param data to operate on
   * @param scale factor
   * @return a reference to the supplied {@code data}
   */
  public static double[][][] multiply(double scale, double[][][] data) {
    for (int i = 0; i < data.length; i++) {
      multiply(scale, data[i]);
    }
    return data;
  }

  /**
   * Multiply the elements of {@code data1} by the elements of {@code data2} in
   * place.
   *
   * @param data1
   * @param data2
   * @return a reference to {@code data1}
   */
  public static List<Double> multiply(List<Double> data1, List<Double> data2) {
    checkSizes(data1, data2);
    for (int i = 0; i < data1.size(); i++) {
      data1.set(i, data1.get(i) * data2.get(i));
    }
    return data1;
  }

  /**
   * Multiply the elements of {@code data1} by the elements of {@code data2} in
   * place.
   *
   * @param data1
   * @param data2
   * @return a reference to {@code data1}
   */
  public static double[] multiply(double[] data1, double[] data2) {
    checkSizes(data1, data2);
    return uncheckedMultiply(data1, data2);
  }

  static double[] uncheckedMultiply(double[] data1, double[] data2) {
    for (int i = 0; i < data1.length; i++) {
      data1[i] *= data2[i];
    }
    return data1;
  }

  /**
   * Divide the elements of {@code data1} by the elements of {@code data2} in
   * place. To divide every value of a dataset by some term, use
   * {@link #multiply(double, double...)} with 1/divisor.
   *
   * @param data1
   * @param data2
   * @return a reference to {@code data1}
   */
  public static double[] divide(double[] data1, double[] data2) {
    checkSizes(data1, data2);
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
   * place. To divide every value of a dataset by some term, use
   * {@link #multiply(double, List)} with 1/divisor.
   *
   * @param data1
   * @param data2
   * @return a reference to {@code data1}
   */
  public static List<Double> divide(List<Double> data1, List<Double> data2) {
    checkSizes(data1, data2);
    for (int i = 0; i < data1.size(); i++) {
      data1.set(i, data1.get(i) / data2.get(i));
    }
    return data1;
  }

  /**
   * Set the elements of {@code data} to their absolute value in place.
   *
   * @param data to operate on
   * @return a reference to the supplied {@code data}
   * @see Math#abs(double)
   */
  public static List<Double> abs(List<Double> data) {
    for (int i = 0; i < data.size(); i++) {
      data.set(i, Math.abs(data.get(i)));
    }
    return data;
  }

  /**
   * Set the elements of {@code data} to their absolute value in place.
   *
   * @param data to operate on
   * @return a reference to the supplied {@code data}
   * @see Math#abs(double)
   */
  public static double[] abs(double... data) {
    for (int i = 0; i < data.length; i++) {
      data[i] = Math.abs(data[i]);
    }
    return data;
  }

  /**
   * Raise Euler's number {@code e} to each of the elements of {@code data} in
   * place.
   *
   * @param data to operate on
   * @return a reference to the supplied {@code data}
   * @see Math#exp(double)
   */
  public static List<Double> exp(List<Double> data) {
    for (int i = 0; i < data.size(); i++) {
      data.set(i, Math.exp(data.get(i)));
    }
    return data;
  }

  /**
   * Raise Euler's number {@code e} to each of the elements of {@code data} in
   * place.
   *
   * @param data to operate on
   * @return a reference to the supplied {@code data}
   * @see Math#exp(double)
   */
  public static double[] exp(double... data) {
    for (int i = 0; i < data.length; i++) {
      data[i] = Math.exp(data[i]);
    }
    return data;
  }

  /**
   * Take the natural logarithm of the elements of {@code data} in place.
   *
   * @param data to operate on
   * @return a reference to the supplied {@code data}
   * @see Math#log(double)
   */
  public static List<Double> ln(List<Double> data) {
    for (int i = 0; i < data.size(); i++) {
      data.set(i, Math.log(data.get(i)));
    }
    return data;
  }

  /**
   * Take the natural logarithm of the elements of {@code data} in place.
   *
   * @param data to operate on
   * @return a reference to the supplied {@code data}
   * @see Math#log(double)
   */
  public static double[] ln(double[] data) {
    for (int i = 0; i < data.length; i++) {
      data[i] = Math.log(data[i]);
    }
    return data;
  }

  /**
   * Raise the elements of {@code data} to the power of 10 in place.
   *
   * @param data to operate on
   * @return a reference to the supplied {@code data}
   * @see Math#pow(double, double)
   */
  public static List<Double> pow10(List<Double> data) {
    for (int i = 0; i < data.size(); i++) {
      data.set(i, Math.pow(10, data.get(i)));
    }
    return data;
  }

  /**
   * Raise the elements of {@code data} to the power of 10 in place.
   *
   * @param data to operate on
   * @return a reference to the supplied {@code data}
   * @see Math#pow(double, double)
   */
  public static double[] pow10(double... data) {
    for (int i = 0; i < data.length; i++) {
      data[i] = Math.pow(10, data[i]);
    }
    return data;
  }

  /**
   * Take the base-10 logarithm of the elements of {@code data} in place.
   *
   * @param data to operate on
   * @return a reference to the supplied {@code data}
   * @see Math#log10(double)
   */
  public static List<Double> log(List<Double> data) {
    for (int i = 0; i < data.size(); i++) {
      data.set(i, Math.log10(data.get(i)));
    }
    return data;
  }

  /**
   * Take the base-10 logarithm of the elements of {@code data} in place.
   *
   * @param data to operate on
   * @return a reference to the supplied {@code data}
   * @see Math#log10(double)
   */
  public static double[] log(double... data) {
    for (int i = 0; i < data.length; i++) {
      data[i] = Math.log10(data[i]);
    }
    return data;
  }

  /**
   * Flip the sign of the elements of {@code data} in place.
   *
   * @param data to operate on
   * @return a reference to the supplied {@code data}
   */
  public static List<Double> flip(List<Double> data) {
    return multiply(-1, data);
  }

  /**
   * Flip the sign of the elements of {@code data} in place.
   *
   * @param data to operate on
   * @return a reference to the supplied {@code data}
   */
  public static double[] flip(double... data) {
    return multiply(-1, data);
  }

  /**
   * Sum the elements of {@code data}. Method returns zero for an empty
   * {@code data} argument.
   *
   * @param data to sum
   * @return the sum of the supplied values
   */
  public static double sum(Collection<Double> data) {
    double sum = 0;
    for (double d : data) {
      sum += d;
    }
    return sum;
  }

  /**
   * Sum the elements of {@code data}. Method returns zero for empty
   * {@code data} argument or no varargs.
   *
   * @param data to sum
   * @return the sum of the supplied values
   */
  public static double sum(double... data) {
    double sum = 0;
    for (double d : data) {
      sum += d;
    }
    return sum;
  }

  /**
   * Sum the arrays in the 2nd dimension of {@code data} into a new 1-D array.
   * 
   * @param data to collapse
   * @return a new array with the sums of the second dimension of {@code data}
   */
  public static double[] collapse(double[][] data) {
    double[] collapsed = new double[data.length];
    for (int i = 0; i < data.length; i++) {
      collapsed[i] = sum(data[i]);
    }
    return collapsed;
  }

  /**
   * Sum the arrays in the 3rd dimension of {@code data} into the 2nd dimension
   * of a new 2-D array.
   * 
   * @param data to collapse
   * @return a new 2-D array with the sums of the third dimension of
   *         {@code data}
   */
  public static double[][] collapse(double[][][] data) {
    double[][] collapsed = new double[data.length][];
    for (int i = 0; i < data.length; i++) {
      collapsed[i] = collapse(data[i]);
    }
    return collapsed;
  }

  /**
   * Transform {@code data} by a {@code DoubleUnaryOperator} in place.
   * 
   * @param function to apply to {@code data}
   * @param data to operate on
   * @return a reference to the supplied {@code data}
   */
  public static double[] transform(DoubleUnaryOperator function, double... data) {
    return transform(Range.closedOpen(0, data.length), function, data);
  }
  
  /**
   * Transform {@code data} by a {@code DoubleUnaryOperator} in place.
   *
   * @param function to apply
   * @param data to operate on
   * @return a reference to the supplied {@code data}
   */
  public static List<Double> transform(DoubleUnaryOperator function, List<Double> data) {
    checkNotNull(function);
    for (int i = 0; i < data.size(); i++) {
      data.set(i, function.applyAsDouble(data.get(i)));
    }
    return data;
  }

  /**
   * Transform {@code data} by a {@code function} in place.
   *
   * @param function to apply
   * @param data to operate on
   * @return a reference to the supplied {@code data}
   */
  public static double[] transform(Function<Double, Double> function, double... data) {
    checkNotNull(function);
    for (int i = 0; i < data.length; i++) {
      data[i] = function.apply(data[i]);
    }
    return data;
  }
 
  /**
   * Transform {@code data} in a given {@code Range} by a 
   *    {@code DoubleUnaryOperator} in place.
   *    
   * @param range to apply the {@code function}
   * @param function to apply to the {@code data}
   * @param data to operate on
   * @return a reference to the supplied {@code data}
   */
  public static double[] transform(
      Range<Integer> range, 
      DoubleUnaryOperator function, 
      double... data) {
    checkNotNull(function);
    checkArgument(!range.isEmpty());
    
    ContiguousSet<Integer> rangeSet = ContiguousSet
        .create(range, DiscreteDomain.integers());

    checkArgument(rangeSet.first() >= 0, "Invalid range: %s", range.toString());

    for (int index : rangeSet) {
      if (index >= data.length) break;
      data[index] = function.applyAsDouble(data[index]);
    }

    return data;
  }
  
  /**
   * Transform {@code data} in a given range, [{@code minIndex}, {@code maxIndex}), 
   *    by a {@code DoubleUnaryOperator} in place.
   *    
   * @param lower inclusive index 
   * @param upper exclusive index
   * @param function to apply to the {@code data}
   * @param data to operate on 
   * @return a reference to the supplied {@code data}
   */
  public static double[] transform(
      int lower, 
      int upper, 
      DoubleUnaryOperator 
      function, double... data) {
    return transform(Range.closedOpen(lower, upper), function, data);
  }

  private static final String NORM_DATA_ERROR = "Normalize: Data outside range [0..+Inf)";
  private static final String NORM_SUM_ERROR = "Normalize: Sum outside range (0..+Inf)";

  /**
   * Normalize the elements of {@code data} in place such that they sum to 1.
   *
   * @param data to normalize
   * @return a reference to the supplied {@code data}
   * @throws IllegalArgumentException if {@code data} is empty or no varargs are
   *         supplied, contains values outside the range {@code [0..+Inf)}, or
   *         sums to a value outside the range {@code (0..+Inf)}
   */
  public static List<Double> normalize(List<Double> data) {
    checkArgument(arePositiveAndRealOrZero(data), NORM_DATA_ERROR);
    double sum = sum(data);
    checkArgument(isPositiveAndReal(sum), NORM_SUM_ERROR);
    double scale = 1.0 / sum;
    return multiply(scale, data);
  }

  /**
   * Normalize the elements of {@code data} in place such that they sum to 1.
   *
   * @param data to normalize
   * @return a reference to the supplied {@code data}
   * @throws IllegalArgumentException if {@code data} is empty or no varargs are
   *         supplied, contains values outside the range {@code [0..+Inf)}, or
   *         sums to a value outside the range {@code (0..+Inf)}
   */
  public static double[] normalize(double... data) {
    checkArgument(arePositiveAndRealOrZero(data), NORM_DATA_ERROR);
    double sum = sum(data);
    checkArgument(isPositiveAndReal(sum), NORM_SUM_ERROR);
    double scale = 1.0 / sum;
    return multiply(scale, data);
  }

  /**
   * Round the elements of {@code data} in place to double values of a specified
   * scale (precision). Internally, this method uses the rounding and precision
   * functionality of {@link BigDecimal}.
   *
   * @param data to operate on
   * @param scale decimal precision
   * @return a reference to the supplied {@code data}
   * @see Maths#round(double, int)
   */
  public static List<Double> round(int scale, List<Double> data) {
    for (int i = 0; i < data.size(); i++) {
      data.set(i, Maths.round(data.get(i), scale));
    }
    return data;
  }

  /**
   * Round the elements of {@code data} in place to double values of a specified
   * scale (precision). Internally, this method uses the rounding and precision
   * functionality of {@link BigDecimal}.
   *
   * @param data to operate on
   * @param scale decimal precision
   * @return a reference to the supplied {@code data}
   * @see Maths#round(double, int)
   */
  public static double[] round(int scale, double... data) {
    for (int i = 0; i < data.length; i++) {
      data[i] = Maths.round(data[i], scale);
    }
    return data;
  }

  /**
   * Ensures positivity of values by adding {@code Math.abs(min(data))} in place
   * if {@code min < 0}.
   *
   * @param data to operate on
   * @return a reference to the supplied data, positivized if necessary
   */
  public static double[] positivize(double... data) {
    if (data.length == 0) {
      return data;
    }
    double min = Doubles.min(data);
    if (min >= 0) {
      return data;
    }
    min = Math.abs(min);
    return add(min, data);
  }

  /**
   * Build an array of the differences between the adjacent elements of
   * {@code data}. Method returns results in a new array that has
   * {@code data.length - 1} where differences are computed per
   * {@code data[i+1] - data[i]}.
   *
   * @param data to difference
   * @return the differences between adjacent values
   * @throws IllegalArgumentException if {@code data.length < 2}
   */
  public static double[] diff(double... data) {
    int size = checkSize(2, data).length - 1;
    double[] diff = new double[size];
    for (int i = 0; i < size; i++) {
      diff[i] = data[i + 1] - data[i];
    }
    return diff;
  }

  /**
   * Compute the difference between {@code test} and {@code target}, relative to
   * {@code target}, as a percent. If {@code target == 0}, method returns
   * {@code 0} if {@code test == 0}, otherwise {@code Double.POSITIVE_INFINITY}.
   *
   * @param test value
   * @param target value
   * @return {@code 100 * abs(test - target) / target}
   * @throws IllegalArgumentException if {@code test} or {@code target} are not
   *         finite.
   */
  public static double percentDiff(double test, double target) {
    checkFinite("test", test);
    checkFinite("target", target);
    if (target == 0 && test == 0) {
      return 0;
    }
    return Math.abs(test - target) / target * 100.0;
  }

  /* * * * * * * * * * * * * * STATE * * * * * * * * * * * * * */

  /**
   * Determine whether {@code value} is a positive, real number in the range
   * {@code (0..+Inf)}.
   * 
   * @param value to validate
   */
  static boolean isPositiveAndReal(double value) {
    return value > 0.0 && value < Double.POSITIVE_INFINITY;
  }

  /**
   * Determine whether the elements of {@code data} are all positive, real
   * numbers.
   *
   * @param data to validate
   * @return {@code true} if all data are in the range {@code (0..+Inf)}; {@code
   * false} otherwise
   * @throws IllegalArgumentException if {@code data} is empty or no varargs are
   *         supplied
   */
  static boolean arePositiveAndReal(double... data) {
    checkSize(1, data);
    for (double d : data) {
      if (!isPositiveAndReal(d)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Determine whether the elements of {@code data} are all positive, real
   * numbers.
   *
   * @param data to validate
   * @return {@code true} if all data are in the range {@code (0..+Inf)}; {@code
   * false} otherwise
   * @throws IllegalArgumentException if {@code data} is empty
   */
  static boolean arePositiveAndReal(Collection<Double> data) {
    checkSize(1, data);
    for (double d : data) {
      if (!isPositiveAndReal(d)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Determine whether {@code value} is a positive, real number in the range
   * {@code [0..+Inf)}.
   * 
   * @param value to validate
   */
  static boolean isPositiveAndRealOrZero(double value) {
    return value >= 0.0 && value < Double.POSITIVE_INFINITY;
  }

  /**
   * Determine whether the elements of {@code data} are all positive, real
   * numbers, or 0.
   *
   * @param data to validate
   * @return {@code true} if all data are in the range {@code [0..+Inf)}; {@code
   * false} otherwise
   * @throws IllegalArgumentException if {@code data} is empty or no varargs are
   *         supplied
   */
  static boolean arePositiveAndRealOrZero(double... data) {
    checkSize(1, data);
    for (double d : data) {
      if (!isPositiveAndRealOrZero(d)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Determine whether the elements of {@code data} are all positive, real
   * numbers, or 0.
   *
   * @param data to validate
   * @return {@code true} if all data are in the range {@code [0..+Inf)}; {@code
   * false} otherwise
   * @throws IllegalArgumentException if {@code data} is empty
   */
  static boolean arePositiveAndRealOrZero(Collection<Double> data) {
    checkSize(1, data);
    for (double d : data) {
      if (!isPositiveAndRealOrZero(d)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Determine whether the elements of {@code data} are all equal to 0.
   * 
   * @param data to validate
   * @return {@code true} if all values = 0; {@code false} otherwise
   * @throws IllegalArgumentException if {@code data} is empty or no varargs are
   *         supplied
   */
  static boolean areZeroValued(double... data) {
    checkSize(1, data);
    for (double d : data) {
      if (d != 0.0) {
        return false;
      }
    }
    return true;
  }

  /**
   * Determine whether all the elements of {@code data} are equal to 0.
   * 
   * @param data to validate
   * @return {@code true} if all values = 0; {@code false} otherwise
   * @throws IllegalArgumentException if {@code data} is empty
   */
  static boolean areZeroValued(Collection<Double> data) {
    checkSize(1, data);
    for (double d : data) {
      if (d != 0.0) {
        return false;
      }
    }
    return true;
  }

  /**
   * Determine whether the elements of {@code data} increase or decrease
   * monotonically.The {@code strict} flag indicates if identical adjacent
   * elements are forbidden. The {@code strict} flag could be {@code true} if
   * checking the x-values of a sequence for any steps, or {@code false} if
   * checking the y-values of a cumulative distribution, which are commonly
   * constant.
   *
   * @param increasing if {@code true}, descending if {@code false}
   * @param strict {@code true} if data must always increase or decrease, {@code
   * false} if identical adjacent values are permitted
   * @param data to validate
   * @return {@code true} if monotonic, {@code false} otherwise
   * @throws IllegalArgumentException if fewer than two data elements are
   *         supplied
   */
  static boolean areMonotonic(boolean increasing, boolean strict, double... data) {
    double[] diff = diff(data);
    if (!increasing) {
      flip(diff);
    }
    double min = Doubles.min(diff);
    return (strict) ? min > 0 : min >= 0;
  }

  /* * * * * * * * * * * * PRECONDITIONS * * * * * * * * * * * */

  private static final String DATA_ERROR = "Data value";

  /**
   * Ensure validity of sequence discretization parameters. Confirms that for a
   * specified range {@code [min, max]} and {@code Δ} that:
   *
   * <ul><li>{@code min}, {@code max}, and {@code Δ} are finite</li>
   *
   * <li>{@code max > min}</li>
   *
   * <li>{@code Δ ≥ 0}</li>
   *
   * <li>{@code Δ > 0} for {@code max > min}</li>
   *
   * <li>{@code Δ ≤ max - min}</li></ul>
   *
   * @param min value
   * @param max value
   * @param Δ discretization delta
   * @return the supplied {@code Δ} for use inline
   */
  public static double checkDelta(double min, double max, double Δ) {
    checkFinite("Sequence minimum", min);
    checkFinite("Sequence maximum", max);
    checkFinite("Sequence Δ", Δ);
    checkArgument(max >= min, "min [%s] >= max [%s]", min, max);
    checkArgument(Δ >= 0.0, "Invalid Δ [%s]", Δ);
    if (max > min) {
      checkArgument(Δ > 0.0, "Invalid Δ [%s] for max > min", Δ);
    }
    checkArgument(Δ <= max - min, "Δ [%s] > max - min [%s]", Δ, max - min);
    return Δ;
  }

  /**
   * Ensure {@code value} is finite.
   *
   * @param label for value if check fails
   * @param value to check
   * @return the validated value
   * @throws IllegalArgumentException if {@code value} is outside the range
   *         {@code (-Inf..+Inf)}
   * @see Doubles#isFinite(double)
   */
  public static double checkFinite(String label, double value) {
    checkArgument(Doubles.isFinite(value), "%s [%s] not finite", label, value);
    return value;
  }

  /**
   * Ensure the elements of {@code data} are finite.
   *
   * @param data to validate
   * @return a reference to the supplied {@code data}
   * @throws IllegalArgumentException if {@code data} is empty, or any elements
   *         of {@code data} are outside the range {@code (-Inf..+Inf)}
   * @see Doubles#isFinite(double)
   */
  public static Collection<Double> checkFinite(Collection<Double> data) {
    checkSize(1, data);
    for (double d : data) {
      checkFinite(DATA_ERROR, d);
    }
    return data;
  }

  /**
   * Ensure the elements of {@code data} are finite.
   *
   * @param data to validate
   * @return a reference to the supplied {@code data}
   * @throws IllegalArgumentException if {@code data} is empty or no varargs are
   *         supplied, or any elements of {@code data} are outside the range
   *         {@code (-Inf..+Inf)}
   * @see Doubles#isFinite(double)
   */
  public static double[] checkFinite(double... data) {
    checkSize(1, data);
    for (double d : data) {
      checkFinite(DATA_ERROR, d);
    }
    return data;
  }

  /**
   * Ensure {@code value} falls within the specified {@link Range}.
   *
   * @param range of allowable values
   * @param label for value if check fails
   * @param value to validate
   * @return the supplied {@code value}
   * @throws IllegalArgumentException if either range endpoint is {@code NaN}. A
   *         range where both enpoints are {@code NaN} and at least one enpoint
   *         is closed is permitted; only when <em>both</em> endpoints are
   *         closed is the value {@code NaN} permitted to the exclusion of all
   *         other values.
   */
  public static double checkInRange(Range<Double> range, String label, double value) {
    checkArgument(range.contains(value), "%s [%s] not in range %s", label, value, range);
    return value;
  }

  /**
   * Ensure the elements of {@code data} fall within the specified {@link Range}
   * .
   *
   * @param range of allowable values
   * @param data to validate
   * @return a reference to the supplied {@code data}
   * @see #checkInRange(Range, String, double) for exception notes
   */
  public static Collection<Double> checkInRange(Range<Double> range, Collection<Double> data) {
    for (double d : data) {
      checkInRange(range, DATA_ERROR, d);
    }
    return data;
  }

  /**
   * Ensure the elements of {@code data} fall within the specified {@link Range}
   * .
   *
   * @param range of allowable values
   * @param data to validate
   * @return a reference to the supplied {@code data}
   * @see #checkInRange(Range, String, double) for exception notes
   */
  public static double[] checkInRange(Range<Double> range, double... data) {
    for (int i = 0; i < data.length; i++) {
      checkInRange(range, DATA_ERROR, data[i]);
    }
    return data;
  }

  /**
   * Ensure {@code data.size() ≥ min}.
   * 
   * @return a reference to the supplied {@code data}
   */
  public static Collection<Double> checkSize(int min, Collection<Double> data) {
    checkSize(min, data.size());
    return data;
  }

  /**
   * Ensure {@code data.length ≥ min}.
   * 
   * @return a reference to the supplied {@code data}
   */
  public static double[] checkSize(int min, double[] data) {
    checkSize(min, data.length);
    return data;
  }

  private static void checkSize(int min, int size) {
    checkArgument(size >= min, "Data size[%s] < minimum[%s]", size, min);
  }

  /**
   * Ensure the supplied datasets are the same size.
   */
  public static void checkSizes(Collection<Double> data1, Collection<Double> data2) {
    checkSizes(data1.size(), data2.size());
  }

  /**
   * Ensure the supplied datasets are the same size.
   */
  public static void checkSizes(double[] data1, double[] data2) {
    checkSizes(data1.length, data2.length);
  }

  /**
   * Ensure the 1<sup>st</sup> dimensions of the supplied datasets are the same
   * size.
   */
  public static void checkSizes(double[][] data1, double[][] data2) {
    checkSizes(data1.length, data2.length);
  }

  /**
   * Ensure the 1<sup>st</sup> dimensions of the supplied datasets are the same
   * size.
   */
  public static void checkSizes(double[][][] data1, double[][][] data2) {
    checkSizes(data1.length, data2.length);
  }

  private static void checkSizes(int s1, int s2) {
    checkArgument(s1 == s2, "Data1.size[%s] ≠ Data2.size[%s]", s1, s2);
  }

  private static final Range<Double> WEIGHT_RANGE = Range.openClosed(0.0, 1.0);
  private static final Range<Double> WEIGHT_RANGE_0 = Range.closed(0.0, 1.0);
  private static final double WEIGHT_TOLERANCE = 1e-4;

  private static double checkWeight(double weight, boolean allowZero) {
    checkInRange(allowZero ? WEIGHT_RANGE_0 : WEIGHT_RANGE, "Weight", weight);
    return weight;
  }

  /**
   * Ensure {@code 0.0 < weight ≤ 1.0}.
   *
   * @return the supplied {@code weight}
   */
  public static double checkWeight(double weight) {
    return checkWeight(weight, false);
  }

  /**
   * Ensure each {@code 0.0 ≤ weight ≤ 1.0} and
   * {@code sum(weights) = 1.0 ± 0.0001}.
   *
   * @param weights to validate
   * @return a reference to the supplied {@code weights}
   */
  public static Collection<Double> checkWeights(Collection<Double> weights) {
    for (double weight : weights) {
      checkWeight(weight, true);
    }
    double sum = sum(weights);
    checkArgument(DoubleMath.fuzzyEquals(sum, 1.0, WEIGHT_TOLERANCE),
        "Weights Σ %s = %s ≠ 1.0", weights, sum);
    return weights;
  }

  /* * * * * * * * 2D & 3D ARRAYS EXTENSIONS * * * * * * * * */

  /**
   * Create a deep copy of a two-dimensional data array.
   *
   * @param data to copy
   * @return a new two-dimensional array populated with the values of
   *         {@code data}
   */
  public static double[][] copyOf(double[][] data) {
    double[][] out = new double[data.length][];
    for (int i = 0; i < data.length; i++) {
      out[i] = Arrays.copyOf(data[i], data[i].length);
    }
    return out;
  }

  /**
   * Create a deep copy of a three-dimensional data array.
   *
   * @param data to copy
   * @return a new three-dimensional array populated with the values of
   *         {@code data}
   */
  public static double[][][] copyOf(double[][][] data) {
    double[][][] out = new double[data.length][][];
    for (int i = 0; i < data.length; i++) {
      out[i] = copyOf(data[i]);
    }
    return out;
  }

  /**
   * Format a two-dimensional data array for printing.
   *
   * @param data to format
   * @return a string representation of the supplied {@code data}
   */
  public static String toString(double[][] data) {
    return toString(data, 1);
  }

  /* To support indenting of multidimensional arrays */
  private static String toString(double[][] data, int indent) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < data.length; i++) {
      if (i > 0) {
        sb.append(",").append(NEWLINE);
        sb.append(Strings.repeat(" ", indent));
      }
      sb.append(Arrays.toString(data[i]));
    }
    sb.append("]");
    return sb.toString();
  }

  /**
   * Format a three-dimensional data array for printing
   *
   * @param data to format
   * @return a string representation of the supplied {@code data}
   */
  public static String toString(double[][][] data) {
    return toString(data, 1);
  }

  /* To support indenting of multidimensional arrays */
  private static String toString(double[][][] data, int indent) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < data.length; i++) {
      if (i > 0) {
        sb.append(",").append(NEWLINE);
        sb.append(Strings.repeat(" ", indent));
      }
      sb.append(toString(data[i], indent + 1));
    }
    sb.append("]");
    return sb.toString();
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

  /**
   * Creates a sequence of evenly spaced values starting at {@code min} and
   * ending at {@code max}. If {@code (max - min) / step} is not equivalent to
   * an integer, the last step in the sequence will be {@code <step}. Unlike
   * {@link #buildSequence(double, double, double, boolean)}, this method
   * returns a sequence where any 'odd' values due to rounding errors have been
   * removed, at least within the range of the specified {@code scale}
   * (precision or number of decimal places).
   * @param min sequence value
   * @param max sequence value
   * @param step sequence spacing
   * @param ascending if {@code true}, descending if {@code false}
   * @param scale the number of decimal places to preserve
   * @return a monotonically increasing or decreasing sequence of values
   * @throws IllegalArgumentException if {@code min >= max}, {@code step <= 0} ,
   *         or any arguments are {@code Double.NaN},
   *         {@code Double.POSITIVE_INFINITY}, or
   *         {@code Double.NEGATIVE_INFINITY}
   */
  public static double[] buildCleanSequence(double min, double max, double step,
      boolean ascending, int scale) {
    double[] seq = buildSequence(min, max, step, ascending);
    return round(scale, seq);
  }

  /**
   * Creates a sequence of evenly spaced values starting at {@code min} and
   * ending at {@code max}. If {@code (max - min) / step} is not integer valued,
   * the last step in the sequence will be {@code <step}. If {@code min == max},
   * then an array containing a single value is returned.
   * @param min sequence value
   * @param max sequence value
   * @param step sequence spacing
   * @param ascending if {@code true}, descending if {@code false}
   * @return a monotonically increasing or decreasing sequence of values
   * @throws IllegalArgumentException if {@code min >= max}, {@code step <= 0} ,
   *         or any arguments are {@code Double.NaN},
   *         {@code Double.POSITIVE_INFINITY}, or
   *         {@code Double.NEGATIVE_INFINITY}
   */
  public static double[] buildSequence(double min, double max, double step, boolean ascending) {
    // if passed in arguments are NaN, +Inf, or -Inf, and step <= 0,
    // then capacity [c] will end up 0 because (int) NaN = 0, or outside the
    // range 1:10000
    checkArgument(min <= max, "min-max reversed");
    if (min == max) {
      return new double[] { min };
    }
    int c = (int) ((max - min) / step);
    checkArgument(c > 0 && c < MAX_SEQ_LEN, "sequence size");
    if (ascending) {
      return buildSequence(min, max, step, c + 2);
    }
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
    if (!DoubleMath.fuzzyEquals(seq.get(seq.size() - 1), max, SEQ_MAX_VAL_TOL)) {
      seq.add(max);
    }
    return Doubles.toArray(seq);
  }

  /**
   * Combine the supplied {@code sequences}. The y-values returned are the set
   * of all supplied y-values. The x-values returned are the sum of the supplied
   * x-values. When summing, x-values for points outside the original domain of
   * a sequence are set to 0, while those inside the original domain are sampled
   * via linear interpolation.
   *
   *
   * @param sequences to combine
   * @return a combined sequence
   */
  @Deprecated
  public static XySequence combine(Iterable<XySequence> sequences) {

    // TODO I think we want to have interpolating and non-interpolating
    // flavors. Interpolating for visual presentation, non-interpolating
    // for re-use as MFD

    // create master x-value sequence
    Builder<Double> builder = ImmutableSortedSet.naturalOrder();
    for (XySequence sequence : sequences) {
      builder.addAll(sequence.xValues());
    }
    double[] xMaster = Doubles.toArray(builder.build());

    // resample and combine sequences
    XySequence combined = XySequence.create(xMaster, null);
    for (XySequence sequence : sequences) {
      // TODO need to disable extrapolation in Interpolation
      if (true) {
        throw new UnsupportedOperationException();
      }
      XySequence resampled = XySequence.resampleTo(sequence, xMaster);
      combined.add(resampled);
    }

    return combined;
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

  static int firstNonZeroIndex(double... data) {
    for (int i = 0; i < data.length; i++) {
      if (data[i] != 0.0) {
        return i;
      }
    }
    return -1;
  }

  static int lastNonZeroIndex(double... data) {
    for (int i = data.length - 1; i >= 0; i--) {
      if (data[i] != 0.0) {
        return i;
      }
    }
    return -1;
  }

}
