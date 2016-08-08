package org.opensha2.data;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.primitives.Doubles;

import java.util.AbstractList;
import java.util.Arrays;
import java.util.List;

/**
 * An immutable array of {@code double} values. This class is similar to the
 * list returned by {@link Doubles#asList(double...)} in that it provides a thin
 * {@link List} wrapper around a primitive {@code double[]} array, except all
 * mutation operations throw an {@code UnsupportedOperationException}. This
 * class provides {@link #getValue(int)} for direct access to primitive value
 * types to avoid autoboxing overhead.
 *
 * @author Peter Powers
 */
public abstract class DataArray extends AbstractList<Double> {

  /**
   * Return the primitive value at {@code index}.
   * 
   * @param index of value to retrieve
   * @throws ArrayIndexOutOfBoundsException if {@code index} is out of range
   *         (index < 0 || index >= size())
   */
  public abstract double getValue(int index);

  @Override
  public Double get(int index) {
    return getValue(index);
  }

  /**
   * Return a new single-use {@code DataArray} builder with a backing array
   * initialized to {@code size}. This builder is not thread safe.
   * 
   * @param size of the backing array
   */
  public static Builder builder(int size) {
    return new Builder(size);
  }

  /**
   * Create a new {@code DataArray}, making a defensive copy of the supplied
   * {@code data}.
   * @param data to copy
   */
  public static DataArray copyOf(double... data) {
    return new RegularDataArray(Arrays.copyOf(data, data.length));
  }

  /**
   * A single-use DataArray builder. Use {@link DataArray#builder(int)} to
   * create a new builder.
   */
  public static class Builder {

    private double[] data;

    private Builder(int size) {
      checkArgument(size >= 0);
      data = new double[size];
    }

    /**
     * Set the {@code value} at index.
     * 
     * @param index
     * @param value
     * @return
     */
    public Builder set(int index, double value) {
      data[index] = value;
      return this;
    }

    /**
     * Return a newly created {@code DataArray}.
     */
    public DataArray build() {
      DataArray dataArray = new RegularDataArray(data);
      data = null; // dereference
      return dataArray;
    }
  }

  private static class RegularDataArray extends DataArray {

    final double[] data;

    RegularDataArray(double[] data) {
      this.data = data;
    }

    @Override
    public double getValue(int index) {
      return data[index];
    }

    @Override
    public int size() {
      return data.length;
    }
  }

}
