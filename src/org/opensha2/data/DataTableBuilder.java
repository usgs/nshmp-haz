package org.opensha2.data;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.opensha2.data.DataUtils.isMonotonic;
import static org.opensha2.data.DataUtils.validateDelta;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Doubles;

/**
 * A single-use builder of immutable {@link DataTable} instances. If builder is
 * configured with row and column keys, the resultant table computes lookup
 * indices via binary search. If builder is configured with row and column
 * ranges and deltas, the resultant table computes lookup indices directly.
 * 
 * <p>Note that table initialization and bulk data operations perform checks on
 * data consistency and validity, but, for performance reasons, individual set
 * operations do not.</p>
 * 
 * <p>Also note that non-interpolating tables currently return vlues
 * corresponding to the next lowest actual row or column relative to the values
 * supplied by {@link DataTable#get(double, double)}. This applies <i>even
 * if</i> a maximum row or column key value has been supplied and is due to the
 * fact that most uses of these tables are used to store values corresponding to
 * ranges (or bins) and so the data are indexed according to the lower edges of
 * each bin.</p>
 * 
 * <p>This builder is not thread safe.</p>
 * 
 * @author Peter Powers
 */
public class DataTableBuilder {

	/*
	 * TODO Table with log keys could easily have negative values; does this
	 * work?
	 */

	private double[][] data;

	private double[] rowKeys;
	private double[] columnKeys;

	private Double rowMin;
	private Double rowMax;
	private Double rowΔ;

	private Double columnMin;
	private Double columnMax;
	private Double columnΔ;

	private boolean interpolates = false;

	private boolean built = false;

	private DataTableBuilder() {}

	/**
	 * Create a new instance of this builder.
	 */
	public static DataTableBuilder create() {
		return new DataTableBuilder();
	}

	/*
	 * NOTE: Once row and column data are set, an empty data array is
	 * initialized. Any call to setAll will cause this array to be dereferenced
	 * and discarded.
	 */

	/**
	 * Set the values corresponding to the rows in the table. Using this method
	 * requires that {@link #columnKeys(double[])} or
	 * {@link #columnKeys(Collection)} also be called.
	 * @param keys to set
	 */
	public DataTableBuilder rowKeys(double[] keys) {
		rowKeys = validateAndCopyKeys(keys);
		if (columnKeys != null) initTableForKeys();
		return this;
	}

	/**
	 * Set the values corresponding to the rows in the table. Using this method
	 * requires that {@link #columnKeys(double[])} or
	 * {@link #columnKeys(Collection)} also be called.
	 * @param keys to set
	 */
	public DataTableBuilder rowKeys(Collection<? extends Number> keys) {
		return rowKeys(Doubles.toArray(keys));
	}

	/**
	 * Set the values corresponding to the columns in the table. Using this
	 * method requires that {@link #rowKeys(double[])} or
	 * {@link #rowKeys(Collection)} also be called.
	 * @param keys to set
	 */
	public DataTableBuilder columnKeys(double[] keys) {
		columnKeys = validateAndCopyKeys(keys);
		if (rowKeys != null) initTableForKeys();
		return this;
	}

	/**
	 * Set the values corresponding to the columns in the table. Using this
	 * method requires that {@link #rowKeys(double[])} or
	 * {@link #rowKeys(Collection)} also be called.
	 * @param keys to set
	 */
	public DataTableBuilder columnKeys(Collection<? extends Number> keys) {
		return columnKeys(Doubles.toArray(keys));
	}

	private double[] validateAndCopyKeys(double[] keys) {
		checkKeyState();
		checkArgument(isMonotonic(true, true, keys));
		return Arrays.copyOf(keys, keys.length);
	}

	private void initTableForKeys() {
		data = new double[rowKeys.length][columnKeys.length];
	}

	/**
	 * Set the values corresponding to the rows in the table. Using this method
	 * requires that {@link #columnRangeAndDelta(double, double, double)} also
	 * be called.
	 * @param min value
	 * @param max value
	 * @param Δ step size
	 */
	public DataTableBuilder rowRangeAndDelta(double min, double max, double Δ) {
		validateRangeAndDelta(min, max, Δ);
		rowMin = min;
		rowMax = max;
		rowΔ = Δ;
		if (columnKeys != null) initTableForRangeAndDelta();
		return this;
	}

	/**
	 * Set the values corresponding to the columns in the table. Using this
	 * method requires that {@link #rowRangeAndDelta(double, double, double)}
	 * also be called.
	 * @param min value
	 * @param max value
	 * @param Δ step size
	 */
	public DataTableBuilder columnRangeAndDelta(double min, double max, double Δ) {
		validateRangeAndDelta(min, max, Δ);
		columnMin = min;
		columnMax = max;
		columnΔ = Δ;
		if (rowKeys != null) initTableForRangeAndDelta();
		return this;
	}

	private void validateRangeAndDelta(double min, double max, double Δ) {
		checkRangeState();
		validateDelta(min, max, Δ);
	}

	private void initTableForRangeAndDelta() {
		int rowSize = size(rowMin, rowMax, rowΔ);
		int columnSize = size(columnMin, columnMax, columnΔ);
		data = new double[rowSize][columnSize];
	}

	/**
	 * Indicate whether the resultant table should interpolate values from the
	 * nearest cells on calls to {@link DataTable#get(double, double)}. This
	 * builder creates tables that <b>do not</b> interpolate by default.
	 * @param interpolates
	 */
	public DataTableBuilder interpolating(boolean interpolates) {
		this.interpolates = interpolates;
		return this;
	}

	/**
	 * Set the internal data array using a copy of the supplied data
	 * @param data to set
	 */
	public DataTableBuilder setAll(double[][] data) {
		checkNotNull(data);
		checkArgument(data.length > 0 && data[0].length > 0,
			"At least one data dimension is empty");
		checkDataState();
		checkDataSize(data);
		this.data = DataUtils.copyOf(data);
		return this;
	}

	/**
	 * Set the value at the specified row and column.
	 * @param rowIndex
	 * @param columnIndex
	 * @param value to set
	 */
	public DataTableBuilder set(int rowIndex, int columnIndex, double value) {
		data[rowIndex][columnIndex] = value;
		return this;
	}

	/**
	 * Add the value to the existing value at the specified row and column.
	 * @param rowIndex
	 * @param columnIndex
	 * @param value to add
	 */
	public DataTableBuilder add(int rowIndex, int columnIndex, double value) {
		data[rowIndex][columnIndex] += value;
		return this;
	}

	/**
	 * Build a new immutable {@code DataTable}.
	 */
	public DataTable build() {
		checkState(built != true, "This builder has already been used");
		checkDataState();
		if (rowKeys != null) {
			return interpolates ?
				new InterpolatingArrayKeyDataTable(rowKeys, columnKeys, data) :
				new ArrayKeyDataTable(rowKeys, columnKeys, data);
		}
		return interpolates ?
			new InterpolatingIndexedDataTable(
				rowMin, rowMax, rowΔ,
				columnMin, columnMax, columnΔ,
				data) :
			new IndexedDataTable(
				rowMin, rowMax, rowΔ,
				columnMin, columnMax, columnΔ,
				data);
	}

	/*
	 * Confirm that builder has not already been configured with indexing data.
	 */
	private void checkKeyState() {
		checkState(rowMin == null, "Rows are already configured with range data");
		checkState(columnMin == null, "Columns are already configured with range data");
	}

	/*
	 * Confirm that builder has not already been configured with array key data.
	 */
	private void checkRangeState() {
		checkState(rowKeys == null, "Rows are already configured with key data");
		checkState(columnKeys == null, "Columns are already configured with key data");
	}

	/*
	 * Confirm that row and column data has been set so that data can be added.
	 */
	private void checkDataState() {
		if (rowKeys != null && columnKeys != null) return;
		if (rowMin != null && columnMin != null) return;
		throw new IllegalStateException("Row and column data have not yet been fully specified");
	}

	/*
	 * Confirm that data array conforms to the row and column sizes already
	 * configured.
	 */
	private void checkDataSize(double[][] data) {
		int rowSize = (rowKeys != null) ?
			rowKeys.length : size(rowMin, rowMax, rowΔ);
		checkArgument(
			data.length == rowSize,
			"Expected %s rows of data but only %s were supplied",
			rowSize, data.length);
		int columnSize = (columnKeys != null) ?
			columnKeys.length : size(columnMin, columnMax, columnΔ);
		for (int i = 0; i < data.length; i++) {
			checkArgument(
				data[i].length == columnSize,
				"Expected %s columns but only %s were supplied on row %s",
				columnSize, data[i].length, i);
		}
	}

	private static class ArrayKeyDataTable implements DataTable {

		final double[] rowKeys;
		final double[] columnKeys;

		final double[][] data;

		private ArrayKeyDataTable(
				double[] rowKeys,
				double[] columnKeys,
				double[][] data) {
			this.data = data;
			this.rowKeys = rowKeys;
			this.columnKeys = columnKeys;
		}

		@Override public double get(final double row, final double column) {
			int iRow = lowerIndexOf(rowKeys, row);
			int iColumn = lowerIndexOf(columnKeys, column);
			return data[iRow][iColumn];
		}

		@Override public List<Double> rowKeys() {
			return ImmutableList.copyOf(Doubles.asList(rowKeys));
		}

		@Override public List<Double> columnKeys() {
			return ImmutableList.copyOf(Doubles.asList(columnKeys));
		}
	}

	private static final class InterpolatingArrayKeyDataTable extends ArrayKeyDataTable {

		private InterpolatingArrayKeyDataTable(
				double[] rowKeys,
				double[] columnKeys,
				double[][] data) {
			super(rowKeys, columnKeys, data);
		}

		@Override public double get(final double row, final double col) {
			int iRow = lowerIndexOf(rowKeys, row);
			int iCol = lowerIndexOf(columnKeys, col);
			double rFrac = fraction(rowKeys[iRow], rowKeys[iRow + 1], row);
			double mFrac = fraction(columnKeys[iCol], columnKeys[iCol + 1], col);
			return interpolate(
				data[iRow][iCol],
				data[iRow][iCol + 1],
				data[iRow + 1][iCol],
				data[iRow + 1][iCol + 1],
				mFrac,
				rFrac);
		}
	}

	/*
	 * NOTE this was lifted from the interpolate class and could parhaps benefit
	 * from checking the size of 'data' and then doing linear instead of binary
	 * search.
	 * 
	 * This is a clamping index search algorithm; it will always return an index
	 * in the range [0, data.length - 2]; it is always used to get some value at
	 * index and index+1
	 */
	private static final int lowerIndexOf(final double[] data, final double value) {
		int i = Arrays.binarySearch(data, value);
		// adjust index for low value (-1) and in-sequence insertion pt
		i = (i == -1) ? 0 : (i < 0) ? -i - 2 : i;
		// adjust hi index to next to last index
		return (i >= data.length - 1) ? --i : i;
	}

	/*
	 * @formatter:off
	 * 
	 * Basic bilinear interpolation:
	 * 
	 *    c11---i1----c12
	 *     |     |     |
	 *     |-----o-----| < f2
	 *     |     |     |
	 *    c21---i2----c22
	 *           ^
	 *          f1
	 *          
	 */
	private static final double interpolate(
			double c11,
			double c12,
			double c21,
			double c22,
			double f1,
			double f2) {

		double i1 = c11 + f1 * (c12 - c11);
		double i2 = c21 + f1 * (c22 - c21);
		return i1 + f2 * (i2 - i1);
	}
	// @formatter:on

	private static final double fraction(double lo, double hi, double value) {
		return value < lo ? 0.0 : value > hi ? 1.0 : (value - lo) / (hi - lo);
	}

	private static class IndexedDataTable implements DataTable {

		final double rowMin;
		final double rowMax;
		final double rowΔ;

		final double columnMin;
		final double columnMax;
		final double columnΔ;

		final double[][] data;

		/* currently set to size-2 */
		final int maxRow;
		final int maxColumn;

		private IndexedDataTable(
				double rowMin, double rowMax, double rowΔ,
				double columnMin, double columnMax, double columnΔ,
				double[][] data) {

			this.rowMin = rowMin;
			this.rowMax = rowMax;
			this.rowΔ = rowΔ;

			this.columnMin = columnMin;
			this.columnMax = columnMax;
			this.columnΔ = columnΔ;

			this.data = data;

			maxRow = size(rowMin, rowMax, rowΔ) - 2;
			maxColumn = size(columnMin, columnMax, columnΔ) - 2;
		}

		@Override public double get(final double row, final double column) {
			int iRow = lowerIndexOf(rowMin, rowΔ, maxRow, row);
			int iColumn = lowerIndexOf(columnMin, columnΔ, maxColumn, column);
			return data[iRow][iColumn];
		}

		@Override public List<Double> rowKeys() {
			return createKeyList(rowMin, rowMax, rowΔ);
		}

		@Override public List<Double> columnKeys() {
			return createKeyList(columnMin, columnMax, columnΔ);
		}
	}

	private static final class InterpolatingIndexedDataTable extends IndexedDataTable {

		InterpolatingIndexedDataTable(
				double rowMin, double rowMax, double rowΔ,
				double columnMin, double columnMax, double columnΔ,
				double[][] data) {

			super(rowMin, rowMax, rowΔ,
				columnMin, columnMax, columnΔ,
				data);
		}

		@Override public double get(final double row, final double column) {
			int iRow = lowerIndexOf(rowMin, rowΔ, maxRow, row);
			int iColumn = lowerIndexOf(columnMin, columnΔ, maxColumn, column);
			return data[iRow][iColumn];
		}
	}

	private static final int size(double min, double max, double Δ) {
		return (int) Math.round((max - min) / Δ) + 1;
	}

	/*
	 * Compute an index from a value and interval. Casting to int floors value.
	 */
	private static final int lowerIndexOf(
			double min,
			double delta,
			int maxIndex,
			double value) {

		return Math.min(maxIndex, (int) ((value - min) / delta));
	}

	private static List<Double> createKeyList(double[] keys) {
		return ImmutableList.copyOf(Doubles.asList(keys));
	}

	private static List<Double> createKeyList(double min, double max, double Δ) {
		double[] keys = DataUtils.buildCleanSequence(min, max, Δ, true, 4);
		return createKeyList(keys);
	}
}
