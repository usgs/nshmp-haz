package org.opensha2.data;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import java.util.List;

import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Doubles;

/**
 * Static utilities for working with {@code DataTables}.
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
	static int indexOf(double min, double delta, double value) {
		return (int) ((value - min) / delta);
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

	/*
	 * Ensure rows and columns have been specified
	 */
	static void checkDataState(double[] rows, double[] columns) {
		checkState(rows != null && columns != null,
			"Row and column data have not yet been fully specified");
	}

	/*
	 * Ensure rows and columns have been specified
	 */
	static void checkDataState(double[] rows, double[] columns, double[] levels) {
		checkState(rows != null && columns != null && levels != null,
			"Row, column, and level data have not yet been fully specified");
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
			for(int j=0; j<column.length; j++) {
				double[] level = column[j];
				checkArgument(
					level.length == levelSize,
					"Expected %s levels but only %s were supplied on row %s, column %s",
					levelSize, level.length, i, j);
			}
		}
	}


}
