package org.opensha2.data;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkElementIndex;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import static org.opensha2.data.IntervalData.checkDataState;
import static org.opensha2.data.IntervalData.indexOf;
import static org.opensha2.data.IntervalData.keys;

import org.opensha2.data.IntervalData.AbstractArray;
import org.opensha2.data.IntervalData.DefaultArray;

import com.google.common.primitives.Doubles;

import java.util.Arrays;
import java.util.List;

/**
 * An array of immutable, double-valued data that is arranged according to
 * increasing and uniformly spaced double-valued keys. Interval arrays are used
 * to represent binned data, and so while row keys are bin centers, indexing is
 * managed internally using bin edges. This simplifies issues related to
 * rounding/precision errors that occur when indexing according to explicit
 * double values.
 *
 * <p>To create an instance of an {@code IntervalArray}, use a {@link Builder}.
 *
 * <p>Internally, an {@code IntervalArray} is backed by a {@code double[]} where
 * a 'row' maps to an index in the backing array.
 *
 * <p>Note that interval arrays are not intended for use with very high
 * precision data and keys are currently limited to a precision of 4 decimal
 * places. This may change in the future.
 *
 * @author Peter Powers
 * @see IntervalData
 * @see IntervalTable
 * @see IntervalVolume
 */
public interface IntervalArray {

  /**
   * Return the value of the bin that maps to the supplied row value. Do not
   * confuse this method with {@link #get(int)} by row index.
   *
   * @param rowValue of bin to retrieve
   * @throws IndexOutOfBoundsException if value is out of range
   */
  double get(double rowValue);

  /**
   * Return the value of the bin that maps to the supplied row index. Do not
   * confuse this method with {@link #get(double)} by row value.
   *
   * @param rowIndex of bin to retrieve
   * @throws IndexOutOfBoundsException if index is out of range
   */
  double get(int rowIndex);

  /**
   * Return an immutable view of {@code this} as an {@link XySequence}.
   */
  XySequence values();

  /**
   * Return the lower edge of the lowermost bin.
   */
  double rowMin();

  /**
   * Return the upper edge of the uppermost bin.
   */
  double rowMax();

  /**
   * Return the row bin discretization.
   */
  double rowΔ();

  /**
   * Return an immutable list <i>view</i> of the row keys (bin centers).
   */
  List<Double> rows();

  /**
   * Return the sum of the values in this array.
   */
  double sum();

  /**
   * Return the index of the bin with smallest value.
   */
  int minIndex();

  /**
   * Return the index of the bin with largest value.
   */
  int maxIndex();

  /**
   * A supplier of values with which to fill an {@code IntervalArray}.
   */
  interface Loader {

    /**
     * Compute the value corresponding to the supplied row key (bin center).
     *
     * @param row value
     */
    public double compute(double row);
  }

  /**
   * A builder of immutable {@code IntervalArray}s.
   *
   * <p>Rows and columns must be specified before any data can be added. Note
   * that any supplied {@code max} values may not correspond to the final upper
   * edge of the uppermost bins if {@code max - min} is not evenly divisible by
   * {@code Δ} .
   */
  public static final class Builder {

    private double[] data;

    private double rowMin;
    private double rowMax;
    private double rowΔ;
    private double[] rows;

    private boolean built = false;
    private boolean initialized = false;

    /**
     * Create a new builder.
     */
    public Builder() {}

    /**
     * Create a new builder with the structure and content identical to that of
     * the supplied array.
     *
     * @param array to copy
     */
    public static Builder copyOf(IntervalArray array) {
      /* Safe covariant cast. */
      DefaultArray defaultArray = (DefaultArray) array;
      Builder builder = copyStructure(defaultArray);
      builder.data = Arrays.copyOf(
          defaultArray.data,
          defaultArray.data.length);
      builder.init();
      return builder;
    }

    /**
     * Create a new builder with a structure identical to that of the supplied
     * model.
     *
     * @param model interval array
     */
    public static Builder fromModel(IntervalArray model) {
      /* Safe covariant cast. */
      Builder builder = copyStructure((AbstractArray) model);
      builder.init();
      return builder;
    }

    private static Builder copyStructure(AbstractArray from) {
      Builder to = new Builder();
      to.rowMin = from.rowMin;
      to.rowMax = from.rowMax;
      to.rowΔ = from.rowΔ;
      to.rows = from.rows;
      return to;
    }

    /**
     * Define the array intervals.
     *
     * @param min lower edge of lowermost row bin
     * @param max upper edge of uppermost row bin
     * @param Δ bin discretization
     */
    public static Builder withRows(double min, double max, double Δ) {
      Builder b = new Builder();
      b.rowMin = min;
      b.rowMax = max;
      b.rowΔ = Δ;
      b.rows = keys(min, max, Δ);
      b.init();
      return b;
    }

    private void init() {
      checkState(!initialized, "Builder has already been initialized");
      if (data == null) {
        data = new double[rows.length];
      }
      initialized = true;
    }

    /**
     * Return the index of the row that would contain the supplied value.
     * @param row value
     */
    public int rowIndex(double row) {
      return indexOf(rowMin, rowΔ, row, rows.length);
    }

    /**
     * Set the value at the specified row. Be careful not to confuse this with
     * {@link #set(int, double)}.
     *
     * @param row key
     * @param value to set
     */
    public Builder set(double row, double value) {
      return set(rowIndex(row), value);
    }

    /**
     * Set the value at the specified row. Be careful not to confuse this with
     * {@link #set(double, double)}.
     *
     * @param row index
     * @param value to set
     */
    public Builder set(int row, double value) {
      data[row] = value;
      return this;
    }

    /**
     * Add to the existing value at the specified row. Be careful not to confuse
     * this with {@link #add(int, double)}.
     *
     * @param row key
     * @param value to add
     */
    public Builder add(double row, double value) {
      return add(rowIndex(row), value);
    }

    /**
     * Add to the existing value at the specified row. Be careful not to confuse
     * this with {@link #add(double, double)}.
     *
     * @param row index
     * @param value to add
     */
    public Builder add(int row, double value) {
      data[row] += value;
      return this;
    }

    /**
     * Add to the array being built.
     *
     * @param values to add
     * @throws IndexOutOfBoundsException if values overrun array
     */
    public Builder add(double[] values) {
      checkElementIndex(values.length - 1, rows.length,
          "Supplied values overrun array");
      for (int i = 0; i < values.length; i++) {
        data[i] += values[i];
      }
      return this;
    }

    /**
     * Add to the array being built.
     *
     * @param values to add
     * @throws IndexOutOfBoundsException if values overrun array
     */
    public Builder add(List<Double> values) {
      return add(Doubles.toArray(values));
    }

    /**
     * Add the y-values of the supplied sequence to the array being built.
     *
     * @param sequence to add
     * @throws IndexOutOfBoundsException if values overrun array
     */
    public Builder add(XySequence sequence) {
      // safe covariant cast
      return add(((ImmutableXySequence) sequence).ys);
    }

    /**
     * Add to the array being built starting at the specified row.
     *
     * @param row key from which to start adding values
     * @param values to add
     * @throws IndexOutOfBoundsException if values overrun array
     */
    public Builder add(double row, double[] values) {
      int rowIndex = rowIndex(row);
      checkElementIndex(rowIndex + values.length - 1, rows.length,
          "Supplied values overrun end of row");
      for (int i = 0; i < values.length; i++) {
        data[rowIndex + i] = values[i];
      }
      return this;
    }

    /**
     * Add to the array being built starting at the specified row.
     *
     * @param row key from which to start adding values
     * @param values to add
     * @throws IndexOutOfBoundsException if values will overrun array
     */
    public Builder add(double row, List<Double> values) {
      return add(row, Doubles.toArray(values));
    }

    /**
     * Add the values in the supplied array to this builder. This operation is
     * very efficient if this builder and the supplied array are sourced from
     * the same model.
     *
     * @param array to add
     * @throws IllegalArgumentException if the rows of the supplied array do not
     *         match those of this array
     * @see #fromModel(IntervalArray)
     */
    public Builder add(IntervalArray array) {
      // safe covariant cast
      validateArray((AbstractArray) array);
      // safe covariant cast until other concrete implementations exist
      Data.uncheckedAdd(data, ((DefaultArray) array).data);
      return this;
    }

    /**
     * Add each value-pair of the supplied sequence to the appropriate interval.
     * 
     * @param sequence to add
     */
    public Builder addEach(XySequence sequence) {
      for (XyPoint xy : sequence) {
        add(xy.x(), xy.y());
      }
      return this;
    }

    /**
     * Multiply ({@code scale}) all values in this builder.
     * @param scale factor
     */
    public Builder multiply(double scale) {
      Data.multiply(scale, data);
      return this;
    }

    /*
     * Check hash codes of row arrays in case fromModel or copyOf has been used,
     * otherwise check array equality.
     */
    AbstractArray validateArray(AbstractArray that) {
      checkArgument(this.rows.hashCode() == that.rows.hashCode() ||
          Arrays.equals(this.rows, that.rows));
      return that;
    }

    /*
     * Data is not copied on build() so we dereference data arrays to prevent
     * lingering builders from further modifying data.
     */
    private void dereference() {
      data = null;
      rows = null;
    }

    /**
     * Return a newly-created, immutable, 2-dimensional data container populated
     * with values computed by the supplied loader. Calling this method will
     * overwrite any values already supplied via {@code set*} or {@code add*}
     * methods.
     *
     * @param loader that will compute values
     */
    public IntervalArray build(Loader loader) {
      checkNotNull(loader);
      for (int i = 0; i < rows.length; i++) {
        data[i] = loader.compute(rows[i]);
      }
      return build();
    }

    /**
     * Return a newly-created, immutable, interval data array populated with the
     * contents of this {@code Builder}.
     */
    public IntervalArray build() {
      checkState(built != true, "This builder has already been used");
      checkDataState(rows);
      IntervalArray array = new DefaultArray(
          rowMin, rowMax, rowΔ, rows,
          data);
      dereference();
      return array;
    }
  }

}
