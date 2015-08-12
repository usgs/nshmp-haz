package org.opensha2.data;

/**
 * XY value container.
 * 
 * @author Peter Powers
 */
public interface XY_Point {

	/**
	 * Return the x-value of this point.
	 * @return x
	 */
	double x();

	/**
	 * Return the y-value of this point.
	 * @return x
	 */
	double y();
	
	
	/**
	 * Set the y-value of this point.
	 * @param y
	 */
	void set(double y);

}
