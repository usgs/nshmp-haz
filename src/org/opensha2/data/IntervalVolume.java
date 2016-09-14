package org.opensha2.data;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import static org.opensha2.data.IntervalData.checkDataState;
import static org.opensha2.data.IntervalData.indexOf;
import static org.opensha2.data.IntervalData.keys;

import org.opensha2.data.IntervalData.AbstractVolume;
import org.opensha2.data.IntervalData.DefaultVolume;

import java.util.Arrays;
import java.util.List;

/**
 * A 3-dimensional volume of immutable, double-valued data that is arranged
 * according to increasing and uniformly spaced double-valued keys. Data volumes
 * are used to represent binned data, and so while row, column, and level keys
 * are bin centers, indexing is managed internally using bin edges. This
 * simplifies issues related to rounding/precision errors that occur when
 * indexing according to explicit double values.
 *
 * <p>To create an instance of an {@code IntervalVolume}, use a {@link Builder}.
 *
 * <p>Internally, an {@code IntervalVolume} is backed by a {@code double[][][]}
 * where 'row' maps to the 1st dimension, 'column' the 2nd dimension, and
 * 'level' the 3rd.
 *
 * <p>Note that interval volumes are not intended for use with very high
 * precision data and keys are currently limited to a precision of 4 decimal
 * places. This may change in the future.
 *
 * @author Peter Powers
 * @see IntervalData
 * @see IntervalArray
 * @see IntervalTable
 */
public interface IntervalVolume {

  /**
   * Return the value of the bin that maps to the supplied row, column, and
   * level values. Do not confuse this method with {@link #get(int, int, int)}
   * by row index.
   *
   * @param rowValue of bin to retrieve
   * @param columnValue of bin to retrieve
   * @param levelValue of bin to retrieve
   * @throws IndexOutOfBoundsException if any value is out of range
   */
  double get(double rowValue, double columnValue, double levelValue);

  /**
   * Return the value of the bin that maps to the supplied row, column, and
   * level indices. Do not confuse this method with
   * {@link #get(double, double, double)} by row value.
   *
   * @param rowIndex of bin to retrieve
   * @param columnIndex of bin to retrieve
   * @param levelIndex of bin to retrieve
   * @throws IndexOutOfBoundsException if any index is out of range
   */
  double get(int rowIndex, int columnIndex, int levelIndex);

  /**
   * Return an immutable view of the values that map to the supplied row and
   * column values. Do not confuse with {@link #column(int, int)} retrieval by
   * index.
   *
   * @param rowValue of bin to retrieve
   * @param columnValue of bin to retrieve
   */
  XySequence column(double rowValue, double columnValue);

  /**
   * Return an immutable view of the values that map to the supplied row and
   * column values. Do not confuse with {@link #column(double, double)}
   * retrieval by index.
   *
   * @param rowIndex of bin to retrieve
   * @param columnIndex of bin to retrieve
   */
  XySequence column(int rowIndex, int columnIndex);

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
   * Return the lower edge of the lowermost level bin.
   */
  double levelMin();

  /**
   * Return the upper edge of the uppermost level bin.
   */
  double levelMax();

  /**
   * Return the level bin discretization.
   */
  double levelΔ();

  /**
   * Return an immutable list <i>view</i> of the level keys (bin centers).
   */
  List<Double> levels();

  /**
   * Return a new {@code IntervalTable} created by summing the levels of this
   * volume.
   */
  IntervalTable collapse();

  /**
   * Return the indices of the bin with smallest value in the form
   * {@code [rowIndex, columnIndex, levelIndex]}.
   */
  int[] minIndex();

  /**
   * Return the indices of the bin with largest value in the form
   * {@code [rowIndex, columnIndex, levelIndex]}.
   */
  int[] maxIndex();

  /**
   * A supplier of values with which to fill a {@code IntervalVolume}.
   */
  interface Loader {

    /**
     * Compute the value corresponding to the supplied row, column, and level
     * keys.
     *
     * @param row value
     * @param column value
     * @param level value
     */
    public double compute(double row, double column, double level);
  }

  /**
   * A builder of immutable {@code IntervalVolume}s.
   *
   * <p>See {@link #create()} to initialize a new builder. Rows, columns, and
   * levels must be specified before any data can be added. Note that any
   * supplied {@code max} values may not correspond to the final upper edge of
   * the uppermost bins if {@code max - min} is not evenly divisible by
   * {@code Δ}.
   */
  public static final class Builder {

    private double[][][] data;

    private double rowMin;
    private double rowMax;
    private double rowΔ;
    private double[] rows;

    private double columnMin;
    private double columnMax;
    private double columnΔ;
    private double[] columns;

    private double levelMin;
    private double levelMax;
    private double levelΔ;
    private double[] levels;

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
     * volume as a model.
     *
     * @param model data volume
     */
    public static Builder fromModel(IntervalVolume model) {

      AbstractVolume v = (AbstractVolume) model;
      Builder b = new Builder();

      b.rowMin = v.rowMin;
      b.rowMax = v.rowMax;
      b.rowΔ = v.rowΔ;
      b.rows = v.rows;

      b.columnMin = v.columnMin;
      b.columnMax = v.columnMax;
      b.columnΔ = v.columnΔ;
      b.columns = v.columns;

      b.levelMin = v.levelMin;
      b.levelMax = v.levelMax;
      b.levelΔ = v.levelΔ;
      b.levels = v.levels;

      b.init();
      return b;
    }

    /**
     * Define the data volume rows.
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
     * Define the data volume columns.
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

    /**
     * Define the data volume levels.
     *
     * @param min lower edge of lowermost column bin
     * @param max upper edge of uppermost column bin
     * @param Δ bin discretization
     */
    public Builder levels(double min, double max, double Δ) {
      levelMin = min;
      levelMax = max;
      levelΔ = Δ;
      levels = keys(min, max, Δ);
      init();
      return this;
    }

    private void init() {
      checkState(!initialized, "Builder has already been initialized");
      if (rows != null && columns != null && levels != null) {
        data = new double[rows.length][columns.length][levels.length];
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
     * Return the index of the level that would contain the supplied value.
     * @param level value
     */
    public int levelIndex(double level) {
      return indexOf(levelMin, levelΔ, level, levels.length);
    }

    /**
     * Set the value at the specified row, column, and level. Be careful not to
     * confuse this with {@link #set(int, int, int, double)}.
     *
     * @param row key
     * @param column key
     * @param level key
     * @param value to set
     */
    public Builder set(double row, double column, double level, double value) {
      return set(rowIndex(row), columnIndex(column), levelIndex(level), value);
    }

    /**
     * Set the value at the specified row, column, and level indices. Be careful
     * not to confuse this with {@link #set(double, double, double, double)}.
     *
     * @param row index
     * @param column index
     * @param level index
     * @param value to set
     */
    public Builder set(int row, int column, int level, double value) {
      data[row][column][level] = value;
      return this;
    }

    /**
     * Add to the existing value at the specified row, column, and level. Be
     * careful not to confuse this with {@link #add(int, int, int, double)}.
     *
     * @param row key
     * @param column key
     * @param level key
     * @param value to add
     */
    public Builder add(double row, double column, double level, double value) {
      return add(rowIndex(row), columnIndex(column), levelIndex(level), value);
    }

    /**
     * Add to the existing value at the specified row, column, and level
     * indices. Be careful not to confuse this with
     * {@link #add(double, double, double, double)}.
     *
     * @param row index
     * @param column index
     * @param level index
     * @param value
     */
    public Builder add(int row, int column, int level, double value) {
      data[row][column][level] += value;
      return this;
    }

    /**
     * Add the values in the supplied volume to this builder. This operation is
     * very efficient if this builder and the supplied volume are sourced from
     * the same model.
     *
     * @param volume to add
     * @throws IllegalArgumentException if the rows, columns, and levels of the
     *         supplied volume do not match those of this volume
     * @see #fromModel(IntervalVolume)
     */
    public Builder add(IntervalVolume volume) {
      // safe covariant cast
      validateVolume((AbstractVolume) volume);
      // safe covariant cast until other concrete implementations exist
      Data.uncheckedAdd(data, ((DefaultVolume) volume).data);
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
     * Check hash codes of row, column, and level arrays in case fromModel has
     * been used, otherwise check array equality.
     */
    AbstractVolume validateVolume(AbstractVolume that) {
      checkArgument((this.rows.hashCode() == that.rows.hashCode() &&
          this.columns.hashCode() == that.columns.hashCode() &&
          this.levels.hashCode() == that.levels.hashCode()) ||
          (Arrays.equals(this.rows, that.rows) &&
              Arrays.equals(this.columns, that.columns) &&
              Arrays.equals(this.levels, that.levels)));
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
      levels = null;
    }

    /**
     * Return a newly-created, immutable, 3-dimensional interval data container
     * populated with values computed by the supplied loader. Calling this
     * method will overwrite any values already supplied via {@code set*} or
     * {@code add*} methods.
     *
     * @param loader that will compute values
     */
    public IntervalVolume build(Loader loader) {
      checkNotNull(loader);
      for (int i = 0; i < rows.length; i++) {
        double row = rows[i];
        for (int j = 0; j < columns.length; j++) {
          double column = columns[j];
          for (int k = 0; k < levels.length; k++) {
            data[i][j][k] = loader.compute(row, column, levels[k]);
          }
        }
      }
      return build();
    }

    /**
     * Return a newly-created, immutable 3-dimensional interval data container
     * populated with the contents of this {@code Builder}.
     */
    public IntervalVolume build() {
      checkState(built != true, "This builder has already been used");
      checkDataState(rows, columns, levels);
      IntervalVolume volume = new DefaultVolume(
          rowMin, rowMax, rowΔ, rows,
          columnMin, columnMax, columnΔ, columns,
          levelMin, levelMax, levelΔ, levels,
          data);
      dereference();
      return volume;
    }
  }

}
