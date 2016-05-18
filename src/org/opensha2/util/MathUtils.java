package org.opensha2.util;

import static java.lang.Math.sqrt;
import static java.math.BigDecimal.ROUND_HALF_UP;

import java.math.BigDecimal;

/**
 * Miscellaneous math and number utilities.
 * 
 * @author Peter Powers
 */
public final class MathUtils {

  private MathUtils() {}

  /**
   * Same as {@link Math#hypot(double, double)} without regard to intermediate
   * under/over flow.
   * 
   * @param v1 first value
   * @param v2 second value
   * @see Math#hypot(double, double)
   */
  public static double hypot(double v1, double v2) {
    return sqrt(v1 * v1 + v2 * v2);
  }

  /**
   * Round a double to a specified number of decimal places. Internally this
   * method uses the scaling and rounding capabilities of {@link BigDecimal}.
   * 
   * @param value to round
   * @param scale the number of decimal places in the result
   */
  public static double round(double value, int scale) {
    return BigDecimal.valueOf(value).setScale(scale, ROUND_HALF_UP).doubleValue();
  }

}
