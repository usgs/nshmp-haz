package org.opensha2.data;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.opensha2.data.DataTables.checkDataSize;
import static org.opensha2.data.DataTables.checkDataState;
import static org.opensha2.data.DataTables.createKeys;
import static org.opensha2.data.DataTables.indexOf;
import static org.opensha2.data.DataTables.initTable;
import static org.opensha2.data.DataTables.keyArray;
import static org.opensha2.data.DataTables.size;
import static org.opensha2.data.DataUtils.validateDelta;

import java.util.List;

/**
 * A wrapper around a 2D table of double-valued data that is arranged according
 * to strictly increasing and uniformly spaced double-valued keys. Data tables
 * are almost always used to represent binned data, and so while row and column
 * keys are bin centers, indexing is managed internally using bin edges. This
 * simplifies issues related to rounding/precision errors that occur when
 * indexing according to explicit double values.
 * 
 * <p>To create a {@code Data2D} instance, use a {@link Builder}.
 * 
 * <p>Internally, a {@code Data2D} is backed by a {@code double[][]} array where
 * 'row' refers to the 1st dimension and 'column' the second.
 * 
 * <p>Note that data tables are not intended for use with very high precision
 * data and keys are currently limited to a precision of 4 decimal places. This
 * may be changed or improved in the future.
 *
 * @author Peter Powers
 * @see Data3D
 */
public interface Data2D {

	/**
	 * Return a value corresponding to the supplied {@code row} and
	 * {@code column}.
	 * 
	 * @param row to retrieve (may not explicitely exist as a key)
	 * @param column to retrieve (may not explicitely exist as a key)
	 */
	double get(double row, double column);

	/**
	 * Lazily return an immutable list of row keys.
	 */
	List<Double> rows();

	/**
	 * Lazily return an immutable list of column keys.
	 */
	List<Double> columns();

	/**
	 * A supplier of values with which to fill a {@code Data2D} table.
	 */
	interface Loader {

		/**
		 * Compute the value corresponding to the supplied row and column keys.
		 * 
		 * @param row value
		 * @param column value
		 */
		public double compute(double row, double column);
	}

	/**
	 * A builder of immutable {@code Data2D} tables.
	 * 
	 * <p>See {@link #create()} to initialize a new builder. Rows and columns
	 * must be specified before any data can be added.
	 */
	public final static class Builder {

		private double[][] data;

		private double[] rows;
		private double[] columns;

		private Double rowMin;
		private Double rowMax;
		private Double rowΔ;

		private Double columnMin;
		private Double columnMax;
		private Double columnΔ;

		private boolean built = false;

		private Builder() {}

		/**
		 * Create a new builder.
		 */
		public static Builder create() {
			return new Builder();
		}

		/**
		 * Define the data table rows.
		 * 
		 * @param min value of lower edge of lowest row bin
		 * @param max value of upper edge of highest row bin
		 * @param Δ step size
		 */
		public Builder rows(double min, double max, double Δ) {
			rowMin = min;
			rowMax = max;
			rowΔ = validateDelta(min, max, Δ);
			rows = keyArray(rowMin, rowMax, rowΔ);
			if (columns != null) init();
			return this;
		}

		/**
		 * Define the data table columns.
		 * 
		 * @param min value of lower edge of lowest column bin
		 * @param max value of upper edge of highest column bin
		 * @param Δ step size
		 */
		public Builder columns(double min, double max, double Δ) {
			columnMin = min;
			columnMax = max;
			columnΔ = validateDelta(min, max, Δ);
			columns = keyArray(columnMin, columnMax, columnΔ);
			if (rows != null) init();
			return this;
		}

		private void init() {
			data = initTable(
				rowMin, rowMax, rowΔ,
				columnMin, columnMax, columnΔ);
		}

		/**
		 * Set the value at the specified row and column.
		 * 
		 * @param row value
		 * @param column value
		 * @param value to set
		 */
		public Builder set(double row, double column, double value) {
			int rowIndex = indexOf(rowMin, rowΔ, row);
			int columnIndex = indexOf(columnMin, columnΔ, column);
			data[rowIndex][columnIndex] = value;
			return this;
		}

		/**
		 * Add to the existing value at the specified row and column.
		 * 
		 * @param row value
		 * @param column value
		 * @param value to add
		 */
		public Builder add(double row, int column, double value) {
			int rowIndex = indexOf(rowMin, rowΔ, row);
			int columnIndex = indexOf(columnMin, columnΔ, column);
			data[rowIndex][columnIndex] += value;
			return this;
		}

		/**
		 * Set all values using a copy of the supplied data.
		 * 
		 * @param data to set
		 */
		public Builder setAll(double[][] data) {
			checkNotNull(data);
			checkArgument(data.length > 0 && data[0].length > 0,
				"At least one data dimension is empty");
			checkDataState(rows, columns);
			checkDataSize(
				size(rowMin, rowMax, rowΔ),
				size(columnMin, columnMax, columnΔ),
				data);
			this.data = DataUtils.copyOf(data);
			return this;
		}

		/**
		 * Build a new immutable 2D data container populated with values
		 * computed by the supplied loader. Note that calling this method will
		 * overwrite any values already supplied via {@code set*} or
		 * {@code add*} methods.
		 * 
		 * @param loader that will compute values
		 */
		public Data2D build(Loader loader) {
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
		 * Build a new immutable 2D data container.
		 */
		public Data2D build() {
			checkState(built != true, "This builder has already been used");
			checkDataState(rows, columns);
			return new Table(
				rowMin, rowMax, rowΔ,
				columnMin, columnMax, columnΔ,
				data);
		}
	}

	/**
	 * Concrete implementation of a {@code Data2D} table. Users should have no
	 * need for this class.
	 * 
	 * @see Builder
	 */
	public final static class Table implements Data2D {

		private final double rowMin;
		private final double rowMax;
		private final double rowΔ;

		private final double columnMin;
		private final double columnMax;
		private final double columnΔ;

		private final double[][] data;

		private Table(
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
		}

		@Override public double get(final double row, final double column) {
			int iRow = indexOf(rowMin, rowΔ, row);
			int iColumn = indexOf(columnMin, columnΔ, column);
			return data[iRow][iColumn];
		}

		@Override public List<Double> rows() {
			return createKeys(rowMin, rowMax, rowΔ);
		}

		@Override public List<Double> columns() {
			return createKeys(columnMin, columnMax, columnΔ);
		}
	}

}
