package org.opensha2.data;

import java.util.List;

/**
 * A wrapper around 2-dimensional {@code double}-valued data that is arranged
 * according to strictly increasing {@code double}-valued keys.
 * 
 * <p>Implementations are backed by a {@code double[][]}, but provide different
 * ways to access values. For example, some implementations
 * {@link #get(double, double)} a value corresponding to the next lowest
 * {@code row} and {@code column} keys, as the supplied keys commonly will not
 * exist. Other implementations interpolate between the closest bounding
 * rows and columns in the table. All implementations currently clamp to row and
 * column minima and maxima for values outside the range supported by the
 * table.</p>
 * 
 * @see DataTableBuilder
 */
interface DataTable {

	/**
	 * Return a value corresponding to the supplied {@code row} and
	 * {@code column}. How the value is retrieved or computed from a backing
	 * data store is implementation specific.
	 * 
	 * @param row to retrieve (may not explicitely exist in table)
	 * @param column to retrieve (may not explicitely exist in table)
	 */
	double get(double row, double column);

	/**
	 * Return an immutable list of row keys.
	 */
	List<Double> rowKeys();

	/**
	 * Return an immutable list of column keys.
	 * @return
	 */
	List<Double> columnKeys();
}
