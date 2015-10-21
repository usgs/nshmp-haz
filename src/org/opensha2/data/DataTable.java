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

import org.opensha2.data.DataTables.AbstractTable;
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
 * <p>To create a {@code DataTable} instance, use a {@link Builder}.</p>
 * 
 * <p>Internally, a {@code DataTable} is backed by a {@code double[][]} array
 * where 'row' refers to the 1st dimension and 'column' the 2nd.</p>
 * 
 * <p>Note that data tables are not intended for use with very high precision
 * data and keys are currently limited to a precision of 4 decimal places. This
 * may be changed or improved in the future.</p>
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
	 * Return the number of rows.
	 */
	int rowCount();
	
	/**
	 * Return an immutable list of row keys. This method creates a copy of the
	 * keys on each call, so only use this method if the actual row keys are
	 * required. {@link #rowCount()} should be used in lieu of
	 * {@code rows().size()}.
	 * 
	 * @see #rowCount()
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
	 * Return the number of columns.
	 */
	int columnCount();

	/**
	 * Return an immutable list of column keys. This method creates a copy of the
	 * keys on each call, so only use this method if the actual column keys are
	 * required. {@link #columnCount()} should be used in lieu of
	 * {@code columns().size()}.
	 * 
	 * @see #columnCount()
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
	 * <p>Use {@link #create()} to initialize a new builder. Rows and columns
	 * must be specified before any data can be added. Note that any supplied
	 * {@code max} values may not correspond to the final upper edge of the
	 * uppermost bins if {@code max - min} is not evenly divisible by {@code Δ}
	 * .</p>
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
		 * Create a new builder with a structure identical to that of the
		 * supplied table as a model.
		 * 
		 * @param model data table
		 */
		public static Builder fromModel(DataTable model) {

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
		 * Define the data table rows.
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
		 * Define the data table columns.
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
		 * careful not to confuse this with {@link #add(double, double, double)}.
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
			checkArgument(data.length > 0 && data[0].length > 0,
				"At least one data dimension is empty");
			checkDataState(rows, columns);
			checkDataSize(rows.length, columns.length, data);
			this.data = Data.copyOf(data);
			return this;
		}

		/**
		 * Add the values in the supplied table to this builder. This operation
		 * is very efficient if this builder and the supplied table are sourced
		 * from the same model.
		 * 
		 * @param table to add
		 * @throws IllegalArgumentException if the rows and columns of the
		 *         supplied table do not match those of this table
		 * @see #fromModel(DataTable)
		 */
		public Builder add(DataTable table) {
			// safe covariant casts
			validateTable((AbstractTable) table);
			if (table instanceof SingularTable) {
				Data.uncheckedAdd(((SingularTable) table).value, data);
			} else {
				Data.uncheckedAdd(data, ((DefaultTable) table).data);
			}
			return this;
		}

		/*
		 * Check hash codes of row and column arrays in case copyOf has been
		 * used, otherwise check array equality.
		 */
		AbstractTable validateTable(AbstractTable that) {
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
