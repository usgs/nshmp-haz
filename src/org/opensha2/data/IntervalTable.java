package org.opensha2.data;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkElementIndex;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import static org.opensha2.data.IntervalData.checkDataState;
import static org.opensha2.data.IntervalData.indexOf;
import static org.opensha2.data.IntervalData.keys;

import org.opensha2.data.IntervalData.AbstractTable;
import org.opensha2.data.IntervalData.DefaultTable;

import com.google.common.primitives.Doubles;

import java.util.Arrays;
import java.util.List;

/**
 * A 2-dimensional table of immutable, double-valued data that is arranged
 * according to increasing and uniformly spaced double-valued keys. Interval
 * tables are used to represent binned data, and so while row and column keys
 * are bin centers, indexing is managed internally using bin edges. This
 * simplifies issues related to rounding/precision errors that occur when
 * indexing according to explicit double values.
 *
 * <p>To create an instance of an {@code IntervalTable}, use a {@link Builder}.
 *
 * <p>Internally, an {@code IntervalTable} is backed by a {@code double[][]}
 * where a 'row' maps to the 1st dimension and a 'column' the 2nd.
 *
 * <p>Note that interval tables are not intended for use with very high
 * precision data and keys are currently limited to a precision of 4 decimal
 * places. This may change in the future.
 *
 * @author Peter Powers
 * @see IntervalData
 * @see IntervalArray
 * @see IntervalVolume
 */
public interface IntervalTable {

  /**
   * Return the value of the bin that maps to the supplied row and column
   * values. Do not confuse this method with {@link #get(int, int)} by row
   * index.
   *
   * @param rowValue of bin to retrieve
   * @param columnValue of bin to retrieve
   * @throws IndexOutOfBoundsException if either value is out of range
   */
  double get(double rowValue, double columnValue);

  /**
   * Return the value of the bin that maps to the supplied row and column
   * indices. Do not confuse this method with {@link #get(double, double)} by
   * row value.
   * 
   * @param rowIndex of bin to retrieve
   * @param columnIndex of bin to retrieve
   * @throws IndexOutOfBoundsException if either index is out of range
   */
  double get(int rowIndex, int columnIndex);

  /**
   * Return an immutable view of the values that map to the supplied row value.
   * Do not confuse with {@link #row(int)} retrieval by index.
   *
   * @param rowValue of bin to retrieve
   */
  XySequence row(double rowValue);

  /**
   * Return an immutable view of the values that map to the supplied row index.
   * Do not confuse with {@link #row(double)} retrieval by value.
   *
   * @param rowIndex of bin to retrieve
   */
  XySequence row(int rowIndex);

  /**
   * Return the lower edge of the lowermost row bin.
   */
  double rowMin();

  /**
   * Return the upper edge of the uppermost row bin.
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
   * Return the lower edge of the lowermost column bin.
   */
  double columnMin();

  /**
   * Return the upper edge of the uppermost column bin.
   */
  double columnMax();

  /**
   * Return the column bin discretization.
   */
  double columnΔ();

  /**
   * Return an immutable list <i>view</i> of the column keys (bin centers).
   */
  List<Double> columns();

  /**
   * Return a new {@code IntervalArray} created by summing the columns of this
   * table.
   */
  IntervalArray collapse();

  /**
   * Return the indices of the bin with smallest value in the form
   * {@code [rowIndex, columnIndex]}.
   */
  int[] minIndex();

  /**
   * Return the indices of the bin with largest value in the form
   * {@code [rowIndex, columnIndex]}.
   */
  int[] maxIndex();

  /**
   * A supplier of values with which to fill a {@code IntervalTable}.
   */
  interface Loader {

    /**
     * Compute the value corresponding to the supplied row and column keys (bin
     * centers).
     *
     * @param row value
     * @param column value
     */
    public double compute(double row, double column);
  }

  /**
   * A builder of immutable {@code IntervalTable}s.
   *
   * <p>Use {@link #create()} to initialize a new builder. Rows and columns must
   * be specified before any data can be added. Note that any supplied
   * {@code max} values may not correspond to the final upper edge of the
   * uppermost bins if {@code max - min} is not evenly divisible by {@code Δ} .
   */
  public static final class Builder {

    private double[][] data;

    private double rowMin;
    private double rowMax;
    private double rowΔ;
    private double[] rows;

    private double columnMin;
    private double columnMax;
    private double columnΔ;
    private double[] columns;

    private boolean built = false;
    private boolean initialized = false;

    private Builder() {}

    /**
     * Create a new builder.
     */
    public static Builder create() {
      return new Builder();
    }

    /**
     * Create a new builder with a structure identical to that of the supplied
     * table as a model.
     *
     * @param model data table
     */
    public static Builder fromModel(IntervalTable model) {

      AbstractTable t = (AbstractTable) model;
      Builder b = new Builder();

      b.rowMin = t.rowMin;
      b.rowMax = t.rowMax;
      b.rowΔ = t.rowΔ;
      b.rows = t.rows;

      b.columnMin = t.columnMin;
      b.columnMax = t.columnMax;
      b.columnΔ = t.columnΔ;
      b.columns = t.columns;

      b.init();
      return b;
    }

    /**
     * Define the table row intervals.
     *
     * @param min lower edge of lowermost row bin
     * @param max upper edge of uppermost row bin
     * @param Δ bin discretization
     */
    public Builder rows(double min, double max, double Δ) {
      rowMin = min;
      rowMax = max;
      rowΔ = Δ;
      rows = keys(min, max, Δ);
      init();
      return this;
    }

    /**
     * Define the table column intervals.
     *
     * @param min lower edge of lowermost column bin
     * @param max upper edge of uppermost column bin
     * @param Δ bin discretization
     */
    public Builder columns(double min, double max, double Δ) {
      columnMin = min;
      columnMax = max;
      columnΔ = Δ;
      columns = keys(min, max, Δ);
      init();
      return this;
    }

    private void init() {
      checkState(!initialized, "Builder has already been initialized");
      if (rows != null && columns != null) {
        data = new double[rows.length][columns.length];
        initialized = true;
      }
    }

    /**
     * Return the index of the row that would contain the supplied value.
     * @param row value
     */
    public int rowIndex(double row) {
      return indexOf(rowMin, rowΔ, row, rows.length);
    }

    /**
     * Return the index of the column that would contain the supplied value.
     * @param column value
     */
    public int columnIndex(double column) {
      return indexOf(columnMin, columnΔ, column, columns.length);
    }

    /**
     * Set the value at the specified row and column. Be careful not to confuse
     * this with {@link #set(int, int, double)}.
     *
     * @param row key
     * @param column key
     * @param value to set
     */
    public Builder set(double row, double column, double value) {
      return set(rowIndex(row), columnIndex(column), value);
    }

    /**
     * Set the value at the specified row and column indices. Be careful not to
     * confuse this with {@link #set(double, double, double)}.
     *
     * @param row index
     * @param column index
     * @param value to set
     */
    public Builder set(int row, int column, double value) {
      data[row][column] = value;
      return this;
    }

    /**
     * Add to the existing value at the specified row and column. Be careful not
     * to confuse this with {@link #add(int, int, double)}.
     *
     * @param row key
     * @param column key
     * @param value to add
     */
    public Builder add(double row, double column, double value) {
      return add(rowIndex(row), columnIndex(column), value);
    }

    /**
     * Add to the existing value at the specified row and column indices. Be
     * careful not to confuse this with {@link #add(double, double, double)} .
     *
     * @param row index
     * @param column index
     * @param value to add
     */
    public Builder add(int row, int column, double value) {
      data[row][column] += value;
      return this;
    }

    /**
     * Add to the values in the specified row.
     *
     * @param row key
     * @param values to add
     * @throws IndexOutOfBoundsException if values overrun row
     */
    public Builder add(double row, double[] values) {
      checkElementIndex(values.length - 1, columns.length,
          "Supplied values overrun end of row");
      double[] rowData = data[rowIndex(row)];
      for (int i = 0; i < values.length; i++) {
        rowData[i] += values[i];
      }
      return this;
    }

    /**
     * Add to the values in the specified row.
     *
     * @param row key
     * @param values to add
     * @throws IndexOutOfBoundsException if values overrun row
     */
    public Builder add(double row, List<Double> values) {
      return add(row, Doubles.toArray(values));
    }

    /**
     * Add the y-values of the supplied sequence to the values in the specified
     * row.
     *
     * @param row key
     * @param sequence to add
     * @throws IndexOutOfBoundsException if values overrun row
     */
    public Builder add(double row, XySequence sequence) {
      // safe covariant cast
      return add(row, ((ImmutableXySequence) sequence).ys);
    }

    /**
     * Add to the values in the specified row starting at the specified column.
     *
     * @param row key
     * @param column key from which to start adding values
     * @param values to add
     * @throws IndexOutOfBoundsException if values overrun row
     */
    public Builder add(double row, double column, double[] values) {
      int columnIndex = columnIndex(column);
      checkElementIndex(columnIndex + values.length - 1, columns.length,
          "Supplied values overrun end of row");
      double[] rowData = data[rowIndex(row)];
      for (int i = 0; i < values.length; i++) {
        rowData[columnIndex + i] = values[i];
      }
      return this;
    }

    /**
     * Add to the values in the specified row starting at the specified column.
     *
     * @param row key
     * @param column key from which to start adding values
     * @param values to add
     * @throws IndexOutOfBoundsException if values will overrun row
     */
    public Builder add(double row, double column, List<Double> values) {
      return add(row, column, Doubles.toArray(values));
    }

    /**
     * Add the values in the supplied table to this builder. This operation is
     * very efficient if this builder and the supplied table are sourced from
     * the same model.
     *
     * @param table to add
     * @throws IllegalArgumentException if the rows and columns of the supplied
     *         table do not match those of this table
     * @see #fromModel(IntervalTable)
     */
    public Builder add(IntervalTable table) {
      // safe covariant cast
      validateTable((AbstractTable) table);
      // safe covariant cast until other concrete implementations exist
      Data.uncheckedAdd(data, ((DefaultTable) table).data);
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
     * Check hash codes of row and column arrays in case copyOf has been used,
     * otherwise check array equality.
     */
    AbstractTable validateTable(AbstractTable that) {
      checkArgument((this.rows.hashCode() == that.rows.hashCode() &&
          this.columns.hashCode() == that.columns.hashCode()) ||
          (Arrays.equals(this.rows, that.rows) &&
              Arrays.equals(this.columns, that.columns)));
      return that;
    }

    /*
     * Data is not copied on build() so we dereference data arrays to prevent
     * lingering builders from further modifying data.
     */
    private void dereference() {
      data = null;
      rows = null;
      columns = null;
    }

    /**
     * Return a newly-created, immutable, 2-dimensional data container populated
     * with values computed by the supplied loader. Calling this method will
     * overwrite any values already supplied via {@code set*} or {@code add*}
     * methods.
     *
     * @param loader that will compute values
     */
    public IntervalTable build(Loader loader) {
      checkNotNull(loader);
      for (int i = 0; i < rows.length; i++) {
        double row = rows[i];
        for (int j = 0; j < columns.length; j++) {
          data[i][j] = loader.compute(row, columns[j]);
        }
      }
      return build();
    }

    /**
     * Return a newly-created, immutable, 2-dimensional data container populated
     * with the contents of this {@code Builder}.
     */
    public IntervalTable build() {
      checkState(built != true, "This builder has already been used");
      checkDataState(rows, columns);
      IntervalTable table = new DefaultTable(
          rowMin, rowMax, rowΔ, rows,
          columnMin, columnMax, columnΔ, columns,
          data);
      dereference();
      return table;
    }
  }

}
