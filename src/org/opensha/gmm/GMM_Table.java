package org.opensha.gmm;

/**
 * Method to retrieve ground motion values from ground motion model (GMM) tables.
 * 
 * @author Peter Powers
 */
interface GMM_Table {

	/**
	 * Default behavior is to return the interpolated value from the table.
	 * Values outside the range supported by the table are generally constrained
	 * to min or max values, although implementations may behave differently.
	 * 
	 * @param m magnitude to consider
	 * @param r distance to consider, whether this is rRup or rJB is
	 *        implementaion specific
	 * @return the natural log of the ground motion for the supplied {@code m}
	 *         and {@code r}
	 */
	double get(double r, double m);
	
	

}
