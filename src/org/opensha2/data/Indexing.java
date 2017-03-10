package org.opensha2.data;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkElementIndex;

import java.util.BitSet;
import java.util.Collection;
import java.util.List;

/**
 * Utilities for creating, calculating, and deriving data indices.
 *
 * @author Peter Powers
 */
public final class Indexing {

  private Indexing() {}

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
