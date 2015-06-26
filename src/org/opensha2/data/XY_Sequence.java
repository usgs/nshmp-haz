package org.opensha2.data;

import java.util.List;

/**
 * Sequence of xy values that is iterable ascending in x. Once created, the
 * sequence x-values should be immutable.
 * 
 * @author Peter Powers
 */
public interface XY_Sequence extends Iterable<XY_Point> {

	/**
	 * Returns the x-value at {@code index}.
	 * @param index to retrieve
	 * @return the x-value at {@code index}
	 * @throws IndexOutOfBoundsException if the index is out of range (
	 *         {@code index < 0 || index >= size()})
	 */
	double x(int index);

	/**
	 * Returns the y-value at {@code index}.
	 * @param index to retrieve
	 * @return the y-value at {@code index}
	 * @throws IndexOutOfBoundsException if the index is out of range (
	 *         {@code index < 0 || index >= size()})
	 */
	double y(int index);
	
	/**
	 * Sets the y-{@code value} at {@code index}.
	 * @param index of y-{@code value} to set.
	 * @param value to set
	 * @throws IndexOutOfBoundsException if the index is out of range (
	 *         {@code index < 0 || index >= size()})
	 */
	void set(int index, double value);
	
	/**
	 * Returns the number or points in this sequence.
	 * @return the sequence size
	 */
	int size();
	
	/**
	 * Returns an immutable {@code List} of the sequence x-values.
	 * @return the {@code List} of x-values 
	 */
	List<Double> xValues();

	/**
	 * Returns an immutable {@code List} of the sequence y-values.
	 * @return the {@code List} of y-values 
	 */
	List<Double> yValues();
	
}
