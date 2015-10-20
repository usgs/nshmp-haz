package org.opensha2.data;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.opensha2.data.DataTables.checkDataSize;
import static org.opensha2.data.DataTables.checkDataState;
import static org.opensha2.data.DataTables.indexOf;
import static org.opensha2.data.DataTables.keys;

import java.util.Arrays;
import java.util.List;

import org.opensha2.data.DataTable.Builder;
import org.opensha2.data.DataTables.AbstractTable;
import org.opensha2.data.DataTables.AbstractVolume;
import org.opensha2.data.DataTables.DefaultTable;
import org.opensha2.data.DataTables.DefaultVolume;
import org.opensha2.data.DataTables.SingularTable;

/**
 * A 3-dimensional volume of immutable, double-valued data that is arranged
 * according to increasing and uniformly spaced double-valued keys. Data tables
 * are almost always used to represent binned data, and so while row and column
 * keys are bin centers, indexing is managed internally using bin edges. This
 * simplifies issues related to rounding/precision errors that occur when
 * indexing according to explicit double values.
 * 
 * <p>To create a {@code DataVolume} instance, use a {@link Builder}.</p>
 * 
 * <p>Internally, a {@code DataVolume} is backed by a {@code double[][][]} array
 * where 'row' refers to the 1st dimension, 'column' the 2nd dimension, and
 * 'level' the 3rd.</p>
 * 
 * <p>Note that data tables are not intended for use with very high precision
 * data and keys are currently limited to a precision of 4 decimal places. This
 * may be changed or improved in the future.</p>
 *
 * @author Peter Powers
 * @see DataTable
 */
public interface DataVolume {

	/**
	 * Return a value corresponding to the supplied {@code row}, {@code column},
	 * and {@code level}.
	 * 
	 * @param row of value to retrieve (may not explicitely exist as a key)
	 * @param column of value to retrieve (may not explicitely exist as a key)
	 * @param level of value to retrieve (may not explicitely exist as a key)
	 */
	double get(double row, double column, double level);

	/**
	 * Return an immutable view of a column of values.
	 * 
	 * @param row of column to retrieve
	 * @param column to retrieve
	 */
	XySequence column(double row, double column);

	/**
	 * Return an immutable list of row keys.
	 */
	List<Double> rows();

	/**
	 * Return an immutable list of column keys.
	 */
	List<Double> columns();

	/**
	 * Return an immutable list of level keys.
	 */
	List<Double> levels();

	/**
	 * A supplier of values with which to fill a {@code DataVolume}.
	 */
	interface Loader {

		/**
		 * Compute the value corresponding to the supplied row, column, and
		 * level keys.
		 * 
		 * @param row value
		 * @param column value
		 * @param level value
		 */
		public double compute(double row, double column, double level);
	}

	/**
	 * A builder of immutable {@code DataVolume}s.
	 * 
	 * <p>See {@link #create()} to initialize a new builder. Rows, columns, and
	 * levels must be specified before any data can be added.
	 */
	public static final class Builder {

		// TODO data is not copied on build() so we need to dereference
		// data arrays on build() to prevent lingering builders from
		// further modifying data

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
		 * Create a new builder with a structure identical to that of the
		 * supplied volume as a model.
		 * 
		 * @param model data volume
		 */
		public static Builder fromModel(DataVolume model) {

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
		 * Set the value at the specified row, column, and level. Be careful not
		 * to confuse this with {@link #set(int, int, int, double)}.
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
		 * Set the value at the specified row, column, and level indices. Be
		 * careful not to confuse this with
		 * {@link #set(double, double, double, double)}.
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
		 * {@link #add(int, int, int, double)}.
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
		 * Set all values using a copy of the supplied data.
		 * 
		 * TODO replace with add(DataVolume)
		 * 
		 * @param data to set
		 */
		@Deprecated public Builder setAll(double[][][] data) {
			checkNotNull(data);
			checkArgument(data.length > 0 && data[0].length > 0 && data[0][0].length > 0,
				"At least one data dimension is empty");
			checkDataState(rows, columns, levels);
			checkDataSize(rows.length, columns.length, levels.length, data);
			this.data = Data.copyOf(data);
			return this;
		}

		/**
		 * Add the values in the supplied volume to this builder. This operation
		 * is very efficient if this builder and the supplied volume are sourced
		 * from the same model.
		 * 
		 * @param volume to add
		 * @throws IllegalArgumentException if the rows, columns, and levels of
		 *         the supplied volume do not match those of this volume
		 * @see #fromModel(DataVolume)
		 */
		public Builder add(DataVolume volume) {
			// safe covariant casts
			validateVolume((AbstractVolume) volume);
			Data.uncheckedAdd(data, ((DefaultVolume) volume).data);
			return this;
		}

		/*
		 * Check hash codes of row, column, and level arrays in case copyOf has
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

		/**
		 * Return a newly-created, immutable, 3-dimensional data container
		 * populated with values computed by the supplied loader. Calling this
		 * method will overwrite any values already supplied via {@code set*} or
		 * {@code add*} methods.
		 * 
		 * @param loader that will compute values
		 */
		public DataVolume build(Loader loader) {
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
		 * Return a newly-created, immutable 3-dimensional data container
		 * populated with the contents of this {@code Builder}.
		 */
		public DataVolume build() {
			checkState(built != true, "This builder has already been used");
			checkDataState(rows, columns, levels);
			return new DefaultVolume(
				rowMin, rowMax, rowΔ, rows,
				columnMin, columnMax, columnΔ, columns,
				levelMin, levelMax, levelΔ, levels,
				data);
		}
	}

}
