package org.opensha2.gmm;

/**
 * Ground motion model (Gmm) result container.
 * 
 * @author Peter Powers
 */
public interface ScalarGroundMotion {

  /**
   * Returns the mean (natural log of the median) ground motion.
   * @return the mean
   */
  double mean();

  /**
   * Returns the standard deviation in natural log units.
   * @return the standard deviation
   */
  double sigma();

}
