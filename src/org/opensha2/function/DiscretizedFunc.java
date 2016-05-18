package org.opensha2.function;

import java.awt.geom.Point2D;

/**
 * <b>Title:</b> DiscretizedFuncAPI<p>
 *
 * <b>Description:</b> Interface that all Discretized Functions must implement.
 * <P>
 *
 * A Discretized Function is a collection of x and y values grouped together as
 * the points that describe a function. A discretized form of a function is the
 * only ways computers can represent functions. Instead of having y=x^2, you
 * would have a sample of possible x and y values. <p>
 *
 * This functional framework is modeled after mathmatical functions such as
 * sin(x), etc. It assumes that there are no duplicate x values, and that if two
 * points have the same x value but different y values, they are still
 * considered the same point. The framework also sorts the points along the x
 * axis, so the first point contains the mimimum x-value and the last point
 * contains the maximum value.<p>
 *
 * Since this API represents the points in a list, alot of these API functions
 * are standard list access functions such as (paraphrasing) get(), set(),
 * delete(). numElements(), iterator(), etc.<p>
 *
 * There are three fields along with getXXX() and setXXX() matching the field
 * names. These javabean fields provide the basic information to describe a
 * function. All functions have a name, information string, and a tolerance
 * level that specifies how close two points have to be along the x axis to be
 * considered equal.<p>
 *
 * Point2D = (x,y)<p>
 *
 * Note: This interface defines a tolerance so that you can say two x-values are
 * the same within this tolerance limit. THERE IS NO TOLERANCE FOR THE Y-AXIS
 * VALUES. This may be useful to add in the future.<p>
 *
 * @author Steven W. Rock
 */

public interface DiscretizedFunc extends XY_DataSet {

  /** Sets the tolerance of this function. */
  public void setTolerance(double newTolerance);

  /** Returns the tolerance of this function. */
  public double getTolerance();

  /** returns the y-value given an x-value - within tolerance */
  public double getY(double x);

  /* ***************/
  /* INTERPOLATION */
  /* ***************/

  /**
   * Given the imput y value, finds the two sequential x values with the closest
   * y values, then calculates an interpolated x value for this y value, fitted
   * to the curve. <p>
   *
   * Since there may be multiple y values with the same value, this function
   * just matches the first found starting at the x-min point along the x-axis.
   */
  public double getFirstInterpolatedX(double y);

  /**
   * Given the input x value, finds the two sequential x values with the closest
   * x values, then calculates an interpolated y value for this x value, fitted
   * to the curve.
   */
  public double getInterpolatedY(double x);

  /**
   * This function interpolates the Y values in the log space between x and y
   * values. The Y value returned is in the linear space but the interpolation
   * is done in the log space.
   * @param x : X value in the linear space corresponding to which we are
   *        required to find the interpolated y value in log space.
   */
  public double getInterpolatedY_inLogXLogYDomain(double x);

  /**
   * This function interpolates the Y values in the log-Y space. The Y value
   * returned is in the linear space.
   * @param x : X value in the linear space corresponding to which we are
   *        required to find the interpolated y value in log space.
   */
  public double getInterpolatedY_inLogYDomain(double x);

  /**
   * Given the input y value, finds the two sequential x values with the closest
   * y values, then calculates an interpolated x value for this y value, fitted
   * to the curve. The interpolated Y value returned is in the linear space but
   * the interpolation is done in the log space. Since there may be multiple y
   * values with the same value, this function just matches the first found
   * starting at the x-min point along the x-axis.
   * @param y : Y value in the linear space corresponding to which we are
   *        required to find the interpolated x value in the log space.
   */
  public double getFirstInterpolatedX_inLogXLogYDomain(double y);

  /* ***************************/
  /* Index Getters From Points */
  /* ***************************/

  /**
   * Since the x-axis is sorted and points stored in a list, they can be
   * accessed by index. This function returns the index of the specified x value
   * if found within tolerance, else returns -1.
   */
  public int getXIndex(double x);

  /**
   * Since the x-axis is sorted and points stored in a list, they can be
   * accessed by index. This function returns the index of the specified x value
   * in the Point2D if found withing tolerance, else returns -1.
   */
  public int getIndex(Point2D point);

  /**
   * Scales (multiplies) the y-values of this function by the esupplied value.
   * @param scale
   */
  public void scale(double scale);

  public DiscretizedFunc deepClone();

}
