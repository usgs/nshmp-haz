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
 * A wrapper around a 3D table (or volume) of double-valued data that is
 * arranged according to strictly increasing and uniformly spaced double-valued
 * keys. Data tables are almost always used to represent binned data, and so
 * while row and column keys are bin centers, indexing is managed internally
 * using bin edges. This simplifies issues related to rounding/precision errors
 * that occur when indexing according to explicit double values.
 * 
 * <p>To create a {@code Data3D} instance, use a {@link Builder}.
 * 
 * <p>Internally, a {@code Data3D} is backed by a {@code double[][][]} array
 * where 'row' refers to the 1st dimension, 'column' the second dimension, and
 * 'level' the 3rd.
 * 
 * <p>Note that data tables are not intended for use with very high precision
 * data and keys are currently limited to a precision of 4 decimal places. This
 * may be changed or improved in the future.
 *
 * @author Peter Powers
 * @see Data2D
 */
public interface Data3D {

	/**
	 * Return a value corresponding to the supplied {@code row}, {@code column},
	 * and {@code level}.
	 * 
	 * @param row to retrieve (may not explicitely exist as a key)
	 * @param column to retrieve (may not explicitely exist as a key)
	 * @param level to retrieve (may not explicitely exist as a key)
	 */
	double get(double row, double column, double level);

	/**
	 * Lazily return an immutable list of row keys.
	 */
	List<Double> rows();

	/**
	 * Lazily return an immutable list of column keys.
	 */
	List<Double> columns();

	/**
	 * Lazily return an immutable list of level keys.
	 */
	List<Double> levels();

	/**
	 * A supplier of values with which to fill a {@code Data3D} table.
	 */
	interface Loader {

		/**
		 * Compute the value corresponding to the supplied row and column keys.
		 * 
		 * @param row value
		 * @param column value
		 * @param level value
		 */
		public double compute(double row, double column, double level);
	}

	/**
	 * A builder of immutable {@code Data3D} tables.
	 * 
	 * <p>See {@link #create()} to initialize a new builder. Rows, columns, and
	 * levels must be specified before any data can be added.
	 */
	public final static class Builder {

		private double[][][] data;

		private double[] rows;
		private double[] columns;
		private double[] levels;

		private double rowMin;
		private double rowMax;
		private double rowΔ;

		private double columnMin;
		private double columnMax;
		private double columnΔ;

		private double levelMin;
		private double levelMax;
		private double levelΔ;

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
			if (columns != null && levels != null) init();
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
			if (rows != null && levels != null) init();
			return this;
		}

		/**
		 * Define the data table columns.
		 * 
		 * @param min value of lower edge of lowest column bin
		 * @param max value of upper edge of highest column bin
		 * @param Δ step size
		 */
		public Builder levels(double min, double max, double Δ) {
			levelMin = min;
			levelMax = max;
			levelΔ = validateDelta(min, max, Δ);
			levels = keyArray(levelMin, levelMax, levelΔ);
			if (rows != null && columns != null) init();
			return this;
		}

		private void init() {
			data = initTable(
				rowMin, rowMax, rowΔ,
				columnMin, columnMax, columnΔ,
				levelMin, levelMax, levelΔ);
		}

		/**
		 * Set the value at the specified row and column.
		 * 
		 * @param row value
		 * @param column value
		 * @param level value
		 * @param value to set
		 */
		public Builder set(double row, double column, double level, double value) {
			int iRow = indexOf(rowMin, rowΔ, row);
			int iColumn = indexOf(columnMin, columnΔ, column);
			int iLevel = indexOf(levelMin, levelΔ, level);
			data[iRow][iColumn][iLevel] = value;
			return this;
		}

		/**
		 * Add to the existing value at the specified row and column.
		 * 
		 * @param row value
		 * @param column value
		 * @param value to add
		 */
		public Builder add(double row, int column, double level, double value) {
			int iRow = indexOf(rowMin, rowΔ, row);
			int iColumn = indexOf(columnMin, columnΔ, column);
			int iLevel = indexOf(levelMin, levelΔ, level);
			data[iRow][iColumn][iLevel] += value;
			return this;
		}

		/**
		 * Set all values using a copy of the supplied data.
		 * 
		 * @param data to set
		 */
		public Builder setAll(double[][][] data) {
			checkNotNull(data);
			checkArgument(data.length > 0 && data[0].length > 0 && data[0][0].length > 0,
				"At least one data dimension is empty");
			checkDataState(rows, columns);
			checkDataSize(
				size(rowMin, rowMax, rowΔ),
				size(columnMin, columnMax, columnΔ),
				size(levelMin, levelMax, levelΔ),
				data);
			this.data = DataUtils.copyOf(data);
			return this;
		}

		/**
		 * Build a new immutable 3D data container populated with values
		 * computed by the supplied loader. Note that calling this method will
		 * overwrite any values already supplied via {@code set*} or
		 * {@code add*} methods.
		 * 
		 * @param loader that will compute values
		 */
		public Data3D build(Loader loader) {
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
		 * Build a new immutable 3D data container.
		 */
		public Data3D build() {
			checkState(built != true, "This builder has already been used");
			checkDataState(rows, columns, levels);
			return new Table(
				rowMin, rowMax, rowΔ,
				columnMin, columnMax, columnΔ,
				levelMin, levelMax, levelΔ,
				data);
		}
	}

	/**
	 * Concrete implementation of a {@code Data3D} table. Users should have no
	 * need for this class.
	 * 
	 * @see Builder
	 */
	public final static class Table implements Data3D {

		private final double rowMin;
		private final double rowMax;
		private final double rowΔ;

		private final double columnMin;
		private final double columnMax;
		private final double columnΔ;

		private final double levelMin;
		private final double levelMax;
		private final double levelΔ;

		private final double[][][] data;

		private Table(
				double rowMin, double rowMax, double rowΔ,
				double columnMin, double columnMax, double columnΔ,
				double levelMin, double levelMax, double levelΔ,
				double[][][] data) {

			this.rowMin = rowMin;
			this.rowMax = rowMax;
			this.rowΔ = rowΔ;

			this.columnMin = columnMin;
			this.columnMax = columnMax;
			this.columnΔ = columnΔ;

			this.levelMin = levelMin;
			this.levelMax = levelMax;
			this.levelΔ = levelΔ;

			this.data = data;
		}

		@Override public double get(final double row, final double column, final double level) {
			int iRow = indexOf(rowMin, rowΔ, row);
			int iColumn = indexOf(columnMin, columnΔ, column);
			int iLevel = indexOf(levelMin, levelΔ, level);
			return data[iRow][iColumn][iLevel];
		}

		@Override public List<Double> rows() {
			return createKeys(rowMin, rowMax, rowΔ);
		}

		@Override public List<Double> columns() {
			return createKeys(columnMin, columnMax, columnΔ);
		}

		@Override public List<Double> levels() {
			return createKeys(levelMin, levelMax, levelΔ);
		}
	}

}
