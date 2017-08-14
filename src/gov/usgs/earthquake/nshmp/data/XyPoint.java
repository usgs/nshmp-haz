package gov.usgs.earthquake.nshmp.data;

/**
 * Wrapper interface for xy-value data pairs. Implementations that permit
 * setting the y-value will propogate through to any backing data structure
 * (e.g. a {@link XySequence}).
 *
 * @author Peter Powers
 * @see XySequence
 */
public interface XyPoint {

  /**
   * Return the x-value of this point.
   * @return x
   */
  double x();

  /**
   * Return the y-value of this point.
   * @return y
   */
  double y();

  /**
   * Set the y-value of this point.
   * @param y the y-value to set
   */
  void set(double y);

}
