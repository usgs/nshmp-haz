package org.opensha2.data;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkElementIndex;

import com.google.common.primitives.Ints;

import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Utilities for creating, calculating, and deriving data indices.
 *
 * @author Peter Powers
 */
public final class Indexing {

  private Indexing() {}

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

  /* Plural form of Guava's checkElementIndex(). */
  private static void checkElementIndices(Collection<Integer> indices, int size) {
    for (int index : indices) {
      checkElementIndex(index, size);
    }
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

}
