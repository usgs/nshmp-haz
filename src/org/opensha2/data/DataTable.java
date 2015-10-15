package org.opensha2.data;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkElementIndex;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.opensha2.data.DataTables.checkDataSize;
import static org.opensha2.data.DataTables.checkDataState;
import static org.opensha2.data.DataTables.indexOf;
import static org.opensha2.data.DataTables.keys;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.opensha2.data.DataTables.DefaultTable;
import org.opensha2.data.DataTables.SingularTable;

import com.google.common.primitives.Doubles;

/**
 * A 2-dimensional table of immutable, double-valued data that is arranged
 * according to increasing and uniformly spaced double-valued keys. Data tables
 * are almost always used to represent binned data, and so while row and column
 * keys are bin centers, indexing is managed internally using bin edges. This
 * simplifies issues related to rounding/precision errors that occur when
 * indexing according to explicit double values.
 * 
 * <p>To create a {@code DataTable} instance, use a {@link Builder}.
 * 
 * <p>Internally, a {@code DataTable} is backed by a {@code double[][]} array
 * where 'row' refers to the 1st dimension and 'column' the 2nd.
 * 
 * <p>Note that data tables are not intended for use with very high precision
 * data and keys are currently limited to a precision of 4 decimal places. This
 * may be changed or improved in the future.
 *
 * @author Peter Powers
 * @see DataVolume
 */
public interface DataTable {

	/**
	 * Return a value corresponding to the supplied {@code row} and
	 * {@code column} keys.
	 * 
	 * @param row of value to retrieve (may not explicitely exist as a key)
	 * @param column of value to retrieve (may not explicitely exist as a key)
	 */
	double get(double row, double column);

	/**
	 * Return an immutable view of a row of values.
	 * 
	 * @param row to retrieve
	 */
	XySequence row(double row);

	/**
	 * Return an immutable list of row keys.
	 */
	List<Double> rows();

	/**
	 * Return an immutable list of column keys.
	 */
	List<Double> columns();

	/**
	 * A supplier of values with which to fill a {@code DataTable}.
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
	 * A builder of immutable {@code DataTable}s.
	 * 
	 * <p>See {@link #create()} to initialize a new builder. Rows and columns
	 * must be specified before any data can be added.
	 */
	public static final class Builder {

		// TODO data is not copied on build() so we need to dereference
		// data arrays on build() to prevent lingering builders from
		// further modifying data

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
		 * Define the data table rows.
		 * 
		 * @param min value of lower edge of lowest row bin
		 * @param max value of upper edge of highest row bin
		 * @param Δ step size
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
		 * Define the data table columns.
		 * 
		 * @param min value of lower edge of lowest column bin
		 * @param max value of upper edge of highest column bin
		 * @param Δ step size
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
		 * Set the value at the specified row and column. Be careful not to
		 * confuse this with {@link #set(int, int, double)}.
		 * 
		 * @param row key
		 * @param column key
		 * @param value to set
		 */
		public Builder set(double row, double column, double value) {
			return set(rowIndex(row), columnIndex(column), value);
		}

		/**
		 * Set the value at the specified row and column indices. Be careful not
		 * to confuse this with {@link #set(double, double, double)}.
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
		 * Add to the existing value at the specified row and column. Be careful
		 * not to confuse this with {@link #add(int, int, double)}.
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
		 * careful not to confuse this with {@link #add(int, int, double)}.
		 * 
		 * @param row index
		 * @param column index
		 * @param value to set
		 */
		public Builder add(int row, int column, double value) {
			data[row][column] += value;
			return this;
		}

		/**
		 * Add to the values in the specified row.
		 *
		 * @param row key
		 * @param values to set
		 * @throws IndexOutOfBoundsException if values overrun row
		 */
		private Builder add(double row, double[] values) {
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
		 * @param values to set
		 * @throws IndexOutOfBoundsException if values overrun row
		 */
		public Builder add(double row, List<Double> values) {
			return add(row, Doubles.toArray(values));
		}

		/**
		 * Add the y-values of the supplied sequence to the values in the
		 * specified row.
		 *
		 * @param row key
		 * @param values to set
		 * @throws IndexOutOfBoundsException if values overrun row
		 */
		public Builder add(double row, XySequence sequence) {
			// safe covariant cast
			return add(row, ((ImmutableXySequence) sequence).ys);
		}

		/**
		 * Add to the values in the specified row starting at the specified
		 * column.
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
		 * Add to the values in the specified row starting at the specified
		 * column.
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
		 * Set all values using a copy of the supplied data.
		 * 
		 * TODO replace with add(DataTable)
		 * @param data to set
		 */
		@Deprecated public Builder setAll(double[][] data) {
			checkNotNull(data);
			checkArgument(data.length > 0 && data[0].length > 0,
				"At least one data dimension is empty");
			checkDataState(rows, columns);
			checkDataSize(rows.length, columns.length, data);
			this.data = DataUtils.copyOf(data);
			return this;
		}

		public Builder add(DataTable table) {
			// safe covariant cast
			DataUtils.uncheckedAdd(data, validateTable((DefaultTable) table).data);
			return this;
		}

		/*
		 * Check hash codes of row and column arrays in case copyOf has been
		 * used, otherwise check array equality
		 */
		DefaultTable validateTable(DefaultTable that) {
			checkArgument((this.rows.hashCode() == that.rows.hashCode() &&
				this.columns.hashCode() == that.columns.hashCode()) ||
				(Arrays.equals(this.rows, that.rows) &&
				Arrays.equals(this.columns, that.columns)));
			return that;
		}

		/**
		 * Return a newly-created, immutable, 2-dimensional data container
		 * populated with values computed by the supplied loader. Calling this
		 * method will overwrite any values already supplied via {@code set*} or
		 * {@code add*} methods.
		 * 
		 * @param loader that will compute values
		 */
		public DataTable build(Loader loader) {
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
		 * Return a newly-created, immutable, 2-dimensional data container
		 * populated with the single value supplied. Calling this method will
		 * ignore any values already supplied via {@code set*} or {@code add*}
		 * methods and will create a DataTable holding only the single value,
		 * similar to {@link Collections#nCopies(int, Object)}.
		 * 
		 * @param value which which to fill data container
		 */
		public DataTable build(double value) {
			checkState(built != true, "This builder has already been used");
			checkDataState(rows, columns);
			return new SingularTable(
				rowMin, rowMax, rowΔ, rows,
				columnMin, columnMax, columnΔ, columns,
				value);
		}

		/**
		 * Return a newly-created, immutable, 2-dimensional data container
		 * populated with the contents of this {@code Builder}.
		 */
		public DataTable build() {
			checkState(built != true, "This builder has already been used");
			checkDataState(rows, columns);
			return new DefaultTable(
				rowMin, rowMax, rowΔ, rows,
				columnMin, columnMax, columnΔ, columns,
				data);
		}
	}

}
