package org.opensha2.data;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkElementIndex;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkPositionIndex;
import static com.google.common.base.Strings.repeat;
import static com.google.common.math.DoubleMath.fuzzyEquals;
import static java.lang.Double.NaN;
import static java.lang.Double.POSITIVE_INFINITY;
import static java.lang.Double.isNaN;

import static org.opensha2.internal.TextUtils.NEWLINE;

import org.opensha2.internal.MathUtils;

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

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Utilities for operating on {@code double}-valued data.
 *
 * <p>The methods of this class:
 *
 * <ul><li>Operate on data in place, ruturning a reference to the supplied
 * data.</li>
 *
 * <li>Throw a {@code NullPointerException} when supplied with {@code null}
 * data.</li>
 *
 * <li>Return an empty array when attempting to transform each element of a
 * dataset for which no varargs elements have been supplied(e.g.
 * {@link #add(double, double...)}).
 *
 * <li>Throw an {@code IllegalArgumentException} if they operate on all elements
 * of a dataset and yield a singular result, and the supplied dataset is empty,
 * with some documented exceptions.
 *
 * <li>Throw an {@code IllegalArguementException} if they operate on multiple
 * datasets and the datasets are not the same size.</li>
 *
 * <li>Do not check for finiteness (see {@link Doubles#isFinite(double)}). See
 * {@link Math} for details on the behavior of individual functions referenced
 * herein.</li>
 *
 * <li>Do not check for over/underflow.</li></ul>
 *
 * <p>Buyer beware.
 *
 * <p>Furthermore, methods that return a result or information about a supplied
 * data set will typically take a {@code Collection<Double>} as an argument,
 * whereas methods that transform data in place will only take
 * {@code List<Double>}s.
 *
 * <p>For other useful {@code Double} utilities, see the Google Guava
 * {@link Doubles} class.
 *
 * @author Peter Powers
 * @see Doubles
 */
public final class Data {

  // TODO note behavior of NaN, any method that operates on data containing NaN
  // will likely return NaN as a result

  // TODO should allow empty varargs (see minINdex maxIndex, normalize)

  /*
   * TODO verify that 'unchecked' variants actually improve performance; in most
   * cases all that's being done is an array.length comparison
   */

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
   * -------------------------------------------------------------------------
   */

  private Data() {}

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
  public static double[] add(double[] data1, double[] data2) {
    checkArgument(data1.length == data2.length);
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
    checkArgument(data1.length == data2.length);
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
    checkArgument(data1.length == data2.length);
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
   * Add the values of {@code data2} to {@code data1} in place.
   *
   * @param data1
   * @param data2
   * @return a reference to {@code data1}
   */
  public static List<Double> add(List<Double> data1, List<Double> data2) {
    checkArgument(data1.size() == data2.size());
    for (int i = 0; i < data1.size(); i++) {
      data1.set(i, data1.get(i) + data2.get(i));
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
    checkArgument(data1.size() == data2.size());
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
    checkArgument(data1.length == data2.length);
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
  public static double[] multiply(double[] data1, double[] data2) {
    checkArgument(data1.length == data2.length);
    return uncheckedMultiply(data1, data2);
  }

  static double[] uncheckedMultiply(double[] data1, double[] data2) {
    for (int i = 0; i < data1.length; i++) {
      data1[i] *= data2[i];
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
  public static List<Double> multiply(List<Double> data1, List<Double> data2) {
    checkArgument(data1.size() == data2.size());
    for (int i = 0; i < data1.size(); i++) {
      data1.set(i, data1.get(i) * data2.get(i));
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
    checkArgument(data1.length == data2.length);
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
    checkArgument(data1.size() == data2.size());
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
  public static double[] abs(double... data) {
    for (int i = 0; i < data.length; i++) {
      data[i] = Math.abs(data[i]);
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
  public static List<Double> abs(List<Double> data) {
    for (int i = 0; i < data.size(); i++) {
      data.set(i, Math.abs(data.get(i)));
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
   * Take the natural logarithm of the elements of {@code data} in place.
   *
   * @param data to operate on
   * @return a reference to the supplied {@code data}
   * @see Math#log(double)
   */
  public static double[] ln(double... data) {
    for (int i = 0; i < data.length; i++) {
      data[i] = Math.log(data[i]);
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
   * Flip the sign of the elements of {@code data} in place.
   *
   * @param data to operate on
   * @return a reference to the supplied {@code data}
   */
  public static double[] flip(double... data) {
    return multiply(-1, data);
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
   * Sum the elements of {@code data}. Method returns {@code Double.NaN} or
   * infinite values if {@code data} contains {@code Double.NaN} or infinite
   * values, respectively. Method returns zero for empty {@code data} argument
   * or no varargs.
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
   * Sum the elements of {@code data}. Method returns {@code Double.NaN} or
   * infinite values if {@code data} contains {@code Double.NaN} or infinite
   * values, respectively. Method returns zero for an empty {@code data}
   * argument.
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
   * Transform {@code data} by a {@code function} in place.
   *
   * @param function to apply
   * @param data to operate on
   * @return a reference to the supplied {@code data}
   */
  public static double[] transform(Function<Double, Double> function, double... data) {
    checkNotNull(function);
    return uncheckedTransform(function, data);
  }

  // TODO behavior test for empty data; can we get rid of null check
  static double[] uncheckedTransform(Function<Double, Double> function, double... data) {
    for (int i = 0; i < data.length; i++) {
      data[i] = function.apply(data[i]);
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
  public static List<Double> transform(Function<Double, Double> function, List<Double> data) {
    checkNotNull(function);
    for (int i = 0; i < data.size(); i++) {
      data.set(i, function.apply(data.get(i)));
    }
    return data;
  }

  /**
   * Find the index of the minimum value in {@code data}. For equivalent minima,
   * method returns the index of the first minimum encountered. If the supplied
   * array is empty, method returns {@code -1}.
   *
   * @param data to evaluate
   * @return the index of the minimum value or {@code -1} if the array is empty
   */
  public static int minIndex(double... data) {
    int index = -1;
    double min = Double.POSITIVE_INFINITY;
    for (int i = 1; i < data.length; i++) {
      if (data[i] < min) {
        index = i;
        min = data[i];
      }
    }
    return index;
  }

  /**
   * Find the indices of the minimum value in {@code data}. For equivalent
   * maxima, method returns the indices of the first minimum encountered. If the
   * 1st dimension of the supplied array is empty or all arrays in the 2nd
   * dimension are empty, method returns {@code [-1, -1]}.
   *
   * @param data to evaluate
   * @return the indices of the minimum value or {@code [-1, -1]} for empty
   *         arrays
   */
  public static int[] minIndex(double[][] data) {
    int index0 = -1;
    int index1 = -1;
    double max = Double.POSITIVE_INFINITY;
    for (int i = 0; i < data.length; i++) {
      double[] data1 = data[i];
      for (int j = 0; j < data1.length; j++) {
        if (data1[j] < max) {
          index0 = i;
          index1 = j;
          max = data1[j];
        }
      }
    }
    return new int[] { index0, index1 };
  }

  /**
   * Find the indices of the minimum value in {@code data}. For equivalent
   * minima, method returns the indices of the first minimum encountered. If the
   * 1st dimension of the supplied array is empty or all arrays in the 2nd or
   * 3rd dimensions are empty, method returns {@code [-1, -1, -1]}.
   *
   * @param data to evaluate
   * @return the indices of the minimum value or {@code [-1, -1, -1]} for empty
   *         arrays
   */
  public static int[] minIndex(double[][][] data) {
    int index0 = -1;
    int index1 = -1;
    int index2 = -1;
    double max = Double.POSITIVE_INFINITY;
    for (int i = 0; i < data.length; i++) {
      double[][] data1 = data[i];
      for (int j = 0; j < data1.length; j++) {
        double[] data2 = data1[j];
        for (int k = 0; k < data2.length; k++) {
          if (data2[k] < max) {
            index0 = i;
            index1 = j;
            index2 = k;
            max = data2[k];
          }
        }
      }
    }
    return new int[] { index0, index1, index2 };
  }

  /**
   * Find the index of the maximum value in {@code data}. For equivalent maxima,
   * method returns the index of the first maximum encountered. If the supplied
   * array is empty, method returns {@code -1}.
   *
   * @param data to evaluate
   * @return the index of the maximum value or -1 if the array is empty
   */
  public static int maxIndex(double... data) {
    int index = -1;
    double max = Double.NEGATIVE_INFINITY;
    for (int i = 1; i < data.length; i++) {
      if (data[i] > max) {
        index = i;
        max = data[i];
      }
    }
    return index;
  }

  /**
   * Find the indices of the maximum value in {@code data}. For equivalent
   * maxima, method returns the indices of the first maximum encountered. If the
   * 1st dimension of the supplied array is empty or all arrays in the 2nd
   * dimension are empty, method returns {@code [-1, -1]}.
   *
   * @param data to evaluate
   * @return the indices of the maximum value or {@code [-1, -1]} for empty
   *         arrays
   */
  public static int[] maxIndex(double[][] data) {
    int index0 = -1;
    int index1 = -1;
    double max = Double.NEGATIVE_INFINITY;
    for (int i = 0; i < data.length; i++) {
      double[] data1 = data[i];
      for (int j = 0; j < data1.length; j++) {
        if (data1[j] > max) {
          index0 = i;
          index1 = j;
          max = data1[j];
        }
      }
    }
    return new int[] { index0, index1 };
  }

  /**
   * Find the indices of the maximum value in {@code data}. For equivalent
   * maxima, method returns the indices of the first maximum encountered. If the
   * 1st dimension of the supplied array is empty or all arrays in the 2nd or
   * 3rd dimensions are empty, method returns {@code [-1, -1, -1]}.
   *
   * @param data to evaluate
   * @return the indices of the maximum value or {@code [-1, -1, -1]} for empty
   *         arrays
   */
  public static int[] maxIndex(double[][][] data) {
    int index0 = -1;
    int index1 = -1;
    int index2 = -1;
    double max = Double.NEGATIVE_INFINITY;
    for (int i = 0; i < data.length; i++) {
      double[][] data1 = data[i];
      for (int j = 0; j < data1.length; j++) {
        double[] data2 = data1[j];
        for (int k = 0; k < data2.length; k++) {
          if (data2[k] > max) {
            index0 = i;
            index1 = j;
            index2 = k;
            max = data2[k];
          }
        }
      }
    }
    return new int[] { index0, index1, index2 };
  }

  /**
   * Determine whether {@code data} are all positive. Method returns
   * {@code true} if data is empty or no varargs are supplied.
   *
   * @param data to evaluate
   * @return {@code true} if all values are ≥0; {@code false} otherwise
   */
  public static boolean arePositive(double... data) {
    for (double d : data) {
      if (d >= 0) {
        continue;
      }
      return false;
    }
    return true;
  }

  /**
   * Determine whether {@code data} are all positive. Method returns
   * {@code true} if data is empty.
   *
   * @param data to evaluate
   * @return {@code true} if all values are ≥0
   */
  public static boolean arePositive(Collection<Double> data) {
    for (double d : data) {
      if (d >= 0) {
        continue;
      }
      return false;
    }
    return true;
  }

  /**
   * Ensures positivity of values by adding {@code Math.abs(min(data))} in place
   * if {@code min < 0}. Method returns an empty array if {@code data} is empty
   * or no varargs are supplied.
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
   * Return whether all the elements of {@code data} are equal to 0.
   * @param data to evaluate
   */
  public static boolean isZeroValued(double... data) {
    for (double d : data) {
      if (d != 0.0) {
        return false;
      }
    }
    return true;
  }

  /**
   * Determine whether the elements of {@code data} increase or decrease
   * monotonically, with a {@code strict} flag indicating if identical adjacent
   * elements are forbidden. The {@code strict} flag could be {@code true} if
   * checking the x-values of a function for any steps, or {@code false} if
   * checking the y-values of a cumulative distribution function, which are
   * commonly constant.
   *
   * @param increasing if {@code true}, descending if {@code false}
   * @param strict {@code true} if data must always increase or decrease,
   *        {@code false} if identical adjacent values are permitted
   * @param data to evaluate
   * @return {@code true} if monotonic, {@code false} otherwise
   * @throws IllegalArgumentException if fewer than two data elements are
   *         supplied
   */
  public static boolean isMonotonic(boolean increasing, boolean strict, double... data) {
    double[] diff = diff(data);
    if (!increasing) {
      flip(diff);
    }
    double min = Doubles.min(diff);
    return (strict) ? min > 0 : min >= 0;
  }

  /**
   * Compute the difference between {@code test} and {@code target}, relative to
   * {@code target}, as a percent. If {@code target} is 0, method returns 0 if
   * {@code test} is also 0, otherwise {@code Double.POSITIVE_INFINITY}. If
   * either value is {@code Double.NaN}, method returns {@code Double.NaN}.
   *
   * @param test value
   * @param target value
   * @return the percent difference
   */
  public static double percentDiff(double test, double target) {
    if (isNaN(target) || isNaN(test)) {
      return NaN;
    }
    if (target == 0) {
      return test == 0 ? 0 : POSITIVE_INFINITY;
    }
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
   * @throws IllegalArgumentException if {@code data.length < 2}
   */
  public static double[] diff(double... data) {
    checkArgument(data.length > 1);
    int size = data.length - 1;
    double[] diff = new double[size];
    for (int i = 0; i < size; i++) {
      diff[i] = data[i + 1] - data[i];
    }
    return diff;
  }

  private static final Range<Double> POS_RANGE = Range.open(0d, Double.POSITIVE_INFINITY);

  /**
   * Normalize the elements of {@code data} in place such that they sum to 1.
   *
   * @param data to normalize
   * @return a reference to the supplied {@code data}
   * @throws IllegalArgumentException if {@code data} is empty, contains any
   *         {@code Double.NaN} or negative values, or sums to a value outside
   *         the range {@code (0..Double.POSITIVE_INFINITY), exclusive}
   */
  public static double[] normalize(double... data) {
    checkArgument(data.length > 0);
    checkArgument(arePositive(data));
    double sum = sum(data);
    checkArgument(POS_RANGE.contains(sum));
    double scale = 1.0 / sum;
    return multiply(scale, data);
  }

  /**
   * Normalize the elements of {@code data} in place such that they sum to 1.
   *
   * @param data to normalize
   * @return a reference to the supplied {@code data}
   * @throws IllegalArgumentException if {@code data} is empty, contains any
   *         {@code Double.NaN} or negative values, or sums to a value outside
   *         the range {@code (0..Double.POSITIVE_INFINITY), exclusive}
   */
  public static List<Double> normalize(List<Double> data) {
    checkArgument(data.size() > 0);
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

  /* Plural form of Guava's checkElementIndex(). */
  private static void checkElementIndices(Collection<Integer> indices, int size) {
    for (int index : indices) {
      checkElementIndex(index, size);
    }
  }

  /**
   * Verify that a value falls within a specified {@link Range}. Method returns
   * the supplied value for use inline.
   *
   * @param range of allowable values
   * @param value to validate
   * @param label indicating type of value being checked; used in exception
   *        message; may be {@code null}
   * @return the supplied value for use inline
   * @throws IllegalArgumentException if value is {@code NaN}
   * @see Range
   */
  public static double checkInRange(Range<Double> range, String label, double value) {
    checkArgument(!Double.isNaN(value), "NaN not allowed");
    checkArgument(range.contains(value),
        "%s value %s is not in range %s",
        Strings.nullToEmpty(label), value, range);
    return value;
  }

  /**
   * Verify that the domain of a {@code double[]} does not exceed that of the
   * supplied {@link Range}. Method returns the supplied values for use inline.
   *
   * @param range of allowable values
   * @param values to validate
   * @param label indicating type of value being checked; used in exception
   *        message; may be {@code null}
   * @return the supplied values for use inline
   * @throws IllegalArgumentException if any value is {@code NaN}
   * @see Range
   */
  public static double[] checkInRange(Range<Double> range, String label, double... values) {
    for (int i = 0; i < values.length; i++) {
      checkInRange(range, label, values[i]);
    }
    return values;
  }

  private static final Range<Double> WEIGHT_RANGE = Range.openClosed(0.0, 1.0);

  /**
   * Confirm that a weight value is {@code 0.0 < weight ≤ 1.0}. Method returns
   * the supplied value for use inline.
   *
   * @param weight to validate
   * @return the supplied {@code weight} value
   */
  public static double checkWeight(double weight) {
    checkInRange(WEIGHT_RANGE, "Weight", weight);
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
   * @return the supplied weights for use inline
   * @see #WEIGHT_TOLERANCE
   */
  public static Collection<Double> checkWeightSum(Collection<Double> weights) {
    double sum = sum(weights);
    checkArgument(fuzzyEquals(sum, 1.0, WEIGHT_TOLERANCE),
        "Weights Σ %s = %s ≠ 1.0", weights, sum);
    return weights;
  }

  /**
   * Validate series discretization parameters. Confirms that for a specified
   * range {@code [min, max]} and {@code Δ} that:
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
    checkFiniteness(min, "min");
    checkFiniteness(max, "max");
    checkFiniteness(Δ, "Δ");
    checkArgument(max >= min, "min [%s] >= max [%s]", min, max);
    checkArgument(Δ >= 0.0, "Invalid Δ [%s]", Δ);
    if (max > min) {
      checkArgument(Δ > 0.0, "Invalid Δ [%s] for max > min", Δ);
    }
    checkArgument(Δ <= max - min, "Δ [%s] > max - min [%s]", max - min);
    return Δ;
  }

  /**
   * Checks that a value is finite.
   *
   * @param value to check
   * @param label for value if check fails
   * @return the supplied value for use inline
   * @see Doubles#isFinite(double)
   */
  public static double checkFiniteness(double value, String label) {
    checkArgument(Doubles.isFinite(value), "Non-finite %s value: %s", label, value);
    return value;
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
    return clean(scale, seq);
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

  /**
   * 'Clean' the elements of {@code data} in place to be double values of a
   * specified scale/precision. Internally, this method uses the rounding and
   * precision functionality of {@link BigDecimal}.
   *
   * @param data to operate on
   * @param scale decimal precision
   * @return a reference to the 'cleaned', supplied {@code data}
   */
  public static double[] clean(int scale, double... data) {
    // TODO should check that scale is > 0
    return transform(new Clean(scale), data);
  }

  private static class Clean implements Function<Double, Double> {
    private final int scale;

    private Clean(int scale) {
      this.scale = scale;
    }

    @Override
    public Double apply(Double d) {
      return MathUtils.round(d, scale);
    }
  }

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
        sb.append(",").append(NEWLINE).append(repeat(" ", indent));
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
        sb.append(",").append(NEWLINE).append(repeat(" ", indent));
      }
      sb.append(toString(data[i], indent + 1));
    }
    sb.append("]");
    return sb.toString();
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
   * {@code data} and use the returned indices in a custom iterator, leaving all
   * original data in place.
   *
   * <p><b>Notes:</b><ul><li>The supplied data should not be sorted.</li>
   * <li>This method does not modify the supplied {@code data} in any
   * way.</li><li>Any {@code NaN}s in {@code data} are placed at the start of
   * the sort order, regardless of sort direction.</li><ul>
   *
   * @param data to provide sort indices for
   * @param ascending if {@code true}, descending if {@code false}
   * @return an index {@code List}
   */
  public static List<Integer> sortedIndices(List<Double> data, boolean ascending) {
    checkArgument(data.size() > 0);
    List<Integer> indices = Ints.asList(indices(data.size()));
    Collections.sort(indices, new IndexComparator(data, ascending));
    return indices;
  }

  /*
   * A comparator for ascending sorting of an index array based on the supplied
   * double array of data.
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
   * Returns the key associated with the minimum value in the supplied dataset.
   * One use case for this method might be to find the column key associated
   * with a particular data set (or row) in a {@link Table}.
   * @param keys to lookup in {@code Map<K, Double>}; if {@code keys == null}
   *        then all values in the data set are evaluated
   * @param data Map<K, Double> to operate on
   * @throws IllegalArgumentException if {@code data} or {@code keys} are empty
   * @return the key corresponding to the minimum value
   * @see Table
   */
  public static <K> K minKey(Map<K, Double> data, Collection<K> keys) {
    checkArgument(data.size() > 0, "data map is empty");
    if (keys == null) {
      keys = data.keySet();
    }
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
   * Return an index array corresponding to the 'set' bits of the supplied
   * {@code BitSet}.
   *
   * @param bits to operate on
   * @return the indices of 'set' bits
   */
  public static int[] bitsToIndices(BitSet bits) {
    int[] indices = new int[bits.cardinality()];
    int index = 0;
    for (int i = bits.nextSetBit(0); i >= 0; i = bits.nextSetBit(i + 1)) {
      indices[index++] = i;
    }
    return indices;
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
    checkElementIndices(indices, capacity);
    BitSet bits = new BitSet(capacity);
    for (int index : indices) {
      bits.set(index);
    }
    return bits;
  }

  /**
   * Nearest neighbor binning algorithm after Silverman, B. W. (1986),
   * <em>Density Estimation for Statistics and Data Analysis</em>, Chapman &
   * Hall, New York. This method is a density estimator that uses variable width
   * binning with a fixed sample size per bin that better reflects the
   * distribution of the underlying data. It is particularly useful when workgin
   * with power-law distributed data. Bin widths are computed as the difference
   * between the last values in adjacent bins. In the case of the 1st bin, the
   * supplied origin is taken as the "last value" of the previous bin. Bin
   * positions are set from the median value in each bin. Note that the supplied
   * {@code data} is not modified; this method uses a copy internally. In most
   * cases, data will be fairly continuous in X, however, for small {@code size}
   * s it's possible to have bins of identical values such that corresponding
   * bin value is Infinity. Such values are not included in the resultant data
   * set.
   *
   * @param data to be binned
   * @param origin for binning
   * @param size of each bin
   * @return an {@code XY_DataGroup} of the binned distribution or {@code null}
   *         if the binned distribution is empty
   * @throws NullPointerException if the supplied {@code data} is {@code null}
   * @throws IllegalArgumentException if supplied {@code data} is empty, the bin
   *         {@code size} is <1, or the {@code origin} is greater than all
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
  // int startIndex = Arrays.binarySearch(localData, origin);
  // checkArgument(startIndex < localData.length,
  // "Origin is greater than all data values");
  // startIndex = (startIndex > 0) ? startIndex : -startIndex - 1;
  // // for multipe identical values, binary search may not return
  // // the lowest index so walk down
  // while (startIndex > 0 && origin == localData[startIndex - 1])
  // startIndex--;
  // // trim data
  // localData = Arrays.copyOfRange(localData, startIndex, localData.length);
  // int binCount = (int) Math.floor(localData.length / size);
  // // bail on an empty distribution
  // if (binCount == 0) return null;
  // List<Double> x = new ArrayList<Double>();
  // List<Double> y = new ArrayList<Double>();
  // double binLo, binHi, binDelta;
  // for (int i = 0; i < binCount; i++) {
  // int datIndex = i * size;
  // binLo = (i == 0) ? origin : localData[datIndex - 1];
  // binHi = localData[datIndex + size - 1];
  // binDelta = binHi - binLo;
  // // bail on intervals of identical values
  // if (binDelta == 0) continue;
  // y.add(size / (binHi - binLo));
  // x.add(StatUtils.percentile(localData, datIndex, size, 50.0));
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
   * @throws IllegalArgumentException if data object is not an array or if data
   *         array is empty
   * @throws IndexOutOfBoundsException if any indices are out of range
   */
  @Deprecated
  public static Object arraySelect(Object array, int[] indices) {
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

}
