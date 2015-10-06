package org.opensha2.data;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkElementIndex;
import static com.google.common.base.Preconditions.checkState;
import static org.opensha2.util.TextUtils.NEWLINE;

import java.util.List;

import org.opensha2.util.Parsing;

import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Doubles;

/**
 * Static utilities for working with and concrete implementations of 2D and 3D
 * data containers.
 *
 * @author Peter Powers
 */
final class DataTables {

	/*
	 * Create clean sequence of keys. Precision is curently set to 4 decimal
	 * places.
	 */
	static double[] keyArray(double min, double max, double Δ) {
		double Δby2 = Δ / 2.0;
		return DataUtils.buildCleanSequence(
			min + Δby2,
			max - Δby2,
			Δ, true, 4);
	}

	/*
	 * Create an immutable list of keys.
	 */
	static List<Double> createKeys(double min, double max, double Δ) {
		double[] keys = keyArray(min, max, Δ);
		return ImmutableList.copyOf(Doubles.asList(keys));
	}

	/*
	 * Compute the size of a DataTable dimension. Min and max define lowermost
	 * and uppermost bin edges, respectively.
	 */
	static int size(double min, double max, double Δ) {
		return (int) Math.round((max - min) / Δ);
	}

	/*
	 * Compute an index from a minimum value, a value and an interval. Casting
	 * to int floors value. No argument checking is performed.
	 */
	static int indexOf(double min, double delta, double value, int size) {
		return checkElementIndex((int) ((value - min) / delta), size);
	}

	/*
	 * Initialize an empty 2D table.
	 */
	static double[][] initTable(
			double rowMin, double rowMax, double rowΔ,
			double columnMin, double columnMax, double columnΔ) {
		int rowSize = size(rowMin, rowMax, rowΔ);
		int columnSize = size(columnMin, columnMax, columnΔ);
		return new double[rowSize][columnSize];
	}

	/*
	 * Initialize an empty 3D table.
	 */
	static double[][][] initTable(
			double rowMin, double rowMax, double rowΔ,
			double columnMin, double columnMax, double columnΔ,
			double levelMin, double levelMax, double levelΔ) {
		int rowSize = size(rowMin, rowMax, rowΔ);
		int columnSize = size(columnMin, columnMax, columnΔ);
		int levelSize = size(levelMin, levelMax, levelΔ);
		return new double[rowSize][columnSize][levelSize];
	}

	static void checkDataState(double[] data, String label) {
		checkState(data != null, "%s data have not yet been fully specified", label);
	}

	/*
	 * Ensure rows and columns have been specified
	 */
	static void checkDataState(double[] rows, double[] columns) {
		checkDataState(rows, "Row");
		checkDataState(columns, "Column");
	}

	/*
	 * Ensure rows and columns have been specified
	 */
	static void checkDataState(double[] rows, double[] columns, double[] levels) {
		checkDataState(rows, columns);
		checkDataState(levels, "Level");
	}

	/*
	 * Confirm that data array conforms to the row and column sizes already
	 * configured.
	 */
	static void checkDataSize(int rowSize, int columnSize, double[][] data) {
		checkArgument(
			data.length == rowSize,
			"Expected %s rows of data but only %s were supplied",
			rowSize, data.length);
		for (int i = 0; i < data.length; i++) {
			double[] column = data[i];
			checkArgument(
				column.length == columnSize,
				"Expected %s columns but only %s were supplied on row %s",
				columnSize, column.length, i);
		}
	}

	/*
	 * Confirm that data array conforms to the row and column sizes already
	 * configured.
	 */
	static void checkDataSize(int rowSize, int columnSize, int levelSize, double[][][] data) {
		checkArgument(
			data.length == rowSize,
			"Expected %s rows of data but only %s were supplied",
			rowSize, data.length);
		for (int i = 0; i < data.length; i++) {
			double[][] column = data[i];
			checkArgument(
				column.length == columnSize,
				"Expected %s columns but only %s were supplied on row %s",
				columnSize, column.length, i);
			for (int j = 0; j < column.length; j++) {
				double[] level = column[j];
				checkArgument(
					level.length == levelSize,
					"Expected %s levels but only %s were supplied on row %s, column %s",
					levelSize, level.length, i, j);
			}
		}
	}

	private static abstract class AbstractTable2D implements Data2D {

		final double rowMin;
		final double rowMax;
		final double rowΔ;
		final int rowSize;

		final double columnMin;
		final double columnMax;
		final double columnΔ;
		final int columnSize;

		private AbstractTable2D(
				double rowMin, double rowMax, double rowΔ,
				double columnMin, double columnMax, double columnΔ) {

			this.rowMin = rowMin;
			this.rowMax = rowMax;
			this.rowΔ = rowΔ;
			this.rowSize = size(rowMin, rowMax, rowΔ);

			this.columnMin = columnMin;
			this.columnMax = columnMax;
			this.columnΔ = columnΔ;
			this.columnSize = size(columnMin, columnMax, columnΔ);
		}

		@Override public List<Double> rows() {
			return createKeys(rowMin, rowMax, rowΔ);
		}

		@Override public List<Double> columns() {
			return createKeys(columnMin, columnMax, columnΔ);
		}

	}

	static final class DefaultTable2D extends AbstractTable2D {

		private final double[][] data;

		DefaultTable2D(double rowMin, double rowMax, double rowΔ,
				double columnMin, double columnMax, double columnΔ,
				double[][] data) {

			super(
				rowMin, rowMax, rowΔ,
				columnMin, columnMax, columnΔ);
			this.data = data;
		}

		@Override public double get(final double row, final double column) {
			int iRow = indexOf(rowMin, rowΔ, row, rowSize);
			int iColumn = indexOf(columnMin, columnΔ, column, columnSize);
			return data[iRow][iColumn];
		}

		private static final String ROW_COL_FORMAT = "% 8.2f";
		private static final String DATA_FORMAT = "%7.2e";
		private static final String ZEROS_IN = "0.00e+00";
		private static final String ZEROS_OUT = "     0.0";
		private static final String DELIMITER = ", ";

		@Override public String toString() {
			StringBuilder sb = new StringBuilder();
			List<Double> rows = rows();
			sb.append("           ");
			sb.append(Parsing.toString(columns(), ROW_COL_FORMAT, DELIMITER, true));
			sb.append(NEWLINE);
			for (int i = 0; i < data.length; i++) {
				sb.append("[");
				sb.append(String.format(ROW_COL_FORMAT, rows.get(i)));
				sb.append("] ");
				// format as scientific but replace zeros
				List<Double> dataRow = Doubles.asList(data[i]);
				String dataLine = Parsing.toString(dataRow, DATA_FORMAT, DELIMITER, true);
				dataLine = dataLine.replace(ZEROS_IN, ZEROS_OUT);
				sb.append(dataLine);
				sb.append(NEWLINE);
			}
			return sb.toString();
		}
	}

	static final class SingularTable2D extends AbstractTable2D {

		private final double data;

		SingularTable2D(
				double rowMin, double rowMax, double rowΔ,
				double columnMin, double columnMax, double columnΔ,
				double data) {

			super(
				rowMin, rowMax, rowΔ,
				columnMin, columnMax, columnΔ);
			this.data = data;
		}

		@Override public double get(final double row, final double column) {
			return data;
		}
	}

	private static abstract class AbstractTable3D implements Data3D {

		final double rowMin;
		final double rowMax;
		final double rowΔ;
		final int rowSize;

		final double columnMin;
		final double columnMax;
		final double columnΔ;
		final int columnSize;

		final double levelMin;
		final double levelMax;
		final double levelΔ;
		final int levelSize;

		private AbstractTable3D(
				double rowMin, double rowMax, double rowΔ,
				double columnMin, double columnMax, double columnΔ,
				double levelMin, double levelMax, double levelΔ) {

			this.rowMin = rowMin;
			this.rowMax = rowMax;
			this.rowΔ = rowΔ;
			this.rowSize = size(rowMin, rowMax, rowΔ);

			this.columnMin = columnMin;
			this.columnMax = columnMax;
			this.columnΔ = columnΔ;
			this.columnSize = size(columnMin, columnMax, columnΔ);

			this.levelMin = levelMin;
			this.levelMax = levelMax;
			this.levelΔ = levelΔ;
			this.levelSize = size(levelMin, levelMax, levelΔ);
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

	static final class DefaultTable3D extends AbstractTable3D {

		final double[][][] data;

		DefaultTable3D(
				double rowMin, double rowMax, double rowΔ,
				double columnMin, double columnMax, double columnΔ,
				double levelMin, double levelMax, double levelΔ,
				double[][][] data) {

			super(
				rowMin, rowMax, rowΔ,
				columnMin, columnMax, columnΔ,
				levelMin, levelMax, levelΔ);
			this.data = data;
		}

		@Override public double get(final double row, final double column, final double level) {
			int iRow = indexOf(rowMin, rowΔ, row, rowSize);
			int iColumn = indexOf(columnMin, columnΔ, column, columnSize);
			int iLevel = indexOf(levelMin, levelΔ, level, levelSize);
			return data[iRow][iColumn][iLevel];
		}
	}

}
