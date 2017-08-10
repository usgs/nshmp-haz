package gov.usgs.earthquake.nshmp.function;

import java.awt.geom.Point2D;
import java.util.Comparator;

/**
 * <b>Title:</b> DataPoint2DComparatorAPI<p>
 *
 * <b>Description:</b> This interface must be implemented by all comparators of
 * DataPoint2D. The comparator uses a tolerance to specify when two values are
 * within tolerance of each other, they are equal<p>
 *
 * @author Steven W. Rock
 * @version 1.0
 */

public interface Point2DComparator extends Comparator<Point2D> {

  /**
   * Tolerance indicates the distance two values can be apart, but still
   * considered equal. This function sets the tolerance.
   *
   * @param newTolerance The new tolerance value
   */
  public void setTolerance(double newTolerance);

  /**
   * Tolerance indicates the distance two values can be apart, but still
   * considered equal. This function returns the tolerance.
   *
   * @return The tolerance value
   */
  public double getTolerance();

}
