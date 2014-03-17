package org.opensha.data;

/**
 * XY value container.
 * 
 * @author Peter Powers
 * @version $Id:$
 */
public interface XY_Point {

	/**
	 * Return the x-value of this point.
	 * @return x
	 */
	public double x();

	/**
	 * Return the y-value of this point.
	 * @return x
	 */
	public double y();
	
	
	/**
	 * Set the y-value of this point.
	 * @param y
	 */
	public void set(double y);

}
