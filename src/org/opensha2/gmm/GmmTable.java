package org.opensha2.gmm;

/**
 * Interface implemented by table based ground motion models (GMMs) to retrieve
 * ground motion values.
 * 
 * @author Peter Powers
 */
interface GmmTable {

	/**
	 * Return an interpolated ground motion value from the table. Values outside
	 * the range supported by the table are generally constrained to min or max
	 * values, although implementations may behave differently.
	 * 
	 * @param m magnitude to consider
	 * @param r distance to consider, whether this is rRup or rJB is
	 *        implementaion specific
	 * @return the natural log of the ground motion for the supplied {@code r}
	 *         and {@code m}
	 */
	double get(double r, double m);

}
