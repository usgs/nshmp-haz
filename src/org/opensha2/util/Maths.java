package org.opensha2.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Miscellaneous math utilities that generally obviate the need for
 * 3<sup>rd</sup> party imports.
 *
 * @author Peter Powers
 */
public final class Maths {

  private Maths() {}

  /** Convenience constant for π/2. */
  public static final double PI_BY_2 = Math.PI / 2;

  /** Convenience constant for 2π. */
  public static final double TWOPI = 2 * Math.PI;

  /** Conversion multiplier for degrees to radians */
  public static final double TO_RAD = Math.toRadians(1.0);

  /** Conversion multiplier for radians to degrees */
  public static final double TO_DEG = Math.toDegrees(1.0);

  /**
   * The precomputed √<span style="border-top:1px solid; padding:0 0.1em;"
   * >2</span>.
   */
  public static final double SQRT_2 = Math.sqrt(2);

  /**
   * The precomputed √<span style="border-top:1px solid; padding:0 0.1em;"
   * >2π</span>.
   */
  public static final double SQRT_2PI = Math.sqrt(2 * Math.PI);

  /**
   * Standardized normal variate {@code ε = (x - μ) / σ}.
   * 
   * @param μ mean
   * @param σ standard deviation
   * @param x random variable
   * @return
   */
  public static double epsilon(double μ, double σ, double x) {
    return (x - μ) / σ;
  }

  /**
   * Error function approximation of Abramowitz and Stegun, formula 7.1.26 in
   * the <em>Handbook of Mathematical Functions with Formulas, Graphs, and
   * Mathematical Tables</em>. Although the approximation is only valid for
   * {@code x ≥ 0}, because {@code erf(x)} is an odd function,
   * {@code erf(x) = −erf(−x)} and negative values are supported.
   */
  public static double erf(double x) {
    return x < 0.0 ? -erfBase(-x) : erfBase(x);
  }

  private static final double P = 0.3275911;
  private static final double A1 = 0.254829592;
  private static final double A2 = -0.284496736;
  private static final double A3 = 1.421413741;
  private static final double A4 = -1.453152027;
  private static final double A5 = 1.061405429;

  private static double erfBase(double x) {
    double t = 1 / (1 + P * x);
    double tsq = t * t;
    return 1 - (A1 * t +
        A2 * tsq +
        A3 * tsq * t +
        A4 * tsq * tsq +
        A5 * tsq * tsq * t) * Math.exp(-x * x);
  }

  /**
   * Same as {@link Math#hypot(double, double)} without regard to intermediate
   * under/over flow.
   *
   * @param v1 first value
   * @param v2 second value
   * @see Math#hypot(double, double)
   */
  public static double hypot(double v1, double v2) {
    return Math.sqrt(v1 * v1 + v2 * v2);
  }

  /**
   * Normal complementary cumulative distribution function.
   * 
   * @param μ mean
   * @param σ standard deviation
   * @param x variate
   */
  public static double normalCcdf(double μ, double σ, double x) {
    return (1.0 + erf((μ - x) / (σ * SQRT_2))) * 0.5;
  }

  /**
   * Normal probability density function.
   * 
   * @param μ mean
   * @param σ standard deviation
   * @param x variate
   */
  public static double normalPdf(double μ, double σ, double x) {
    return Math.exp((μ - x) * (x - μ) / (2 * σ * σ)) / (σ * SQRT_2PI);
  }

  /**
   * Step function for which {@code f(x) = }&#123;
   * {@code 1 if x ≤ μ; 0 if x > μ }&#125;.
   * 
   * @param μ mean
   * @param x variate
   */
  public static double stepFunction(double μ, double x) {
    return x < μ ? 1.0 : 0.0;
  }

  /**
   * Round a double to a specified number of decimal places according to
   * {@link RoundingMode#HALF_UP}. Internally this method uses the scaling and
   * rounding capabilities of {@link BigDecimal}. Note that a negative
   * {@code scale} will round {@code value} to the specified number of places
   * above the decimal.
   *
   * @param value to round
   * @param scale the number of decimal places in the result
   */
  public static double round(double value, int scale) {
    return round(value, scale, RoundingMode.HALF_UP);
  }

  /**
   * Round a double to a specified number of decimal places according to the
   * supplied {@link RoundingMode}. Internally this method uses the scaling and
   * rounding capabilities of {@link BigDecimal}. Note that a negative
   * {@code scale} will round {@code value} to the specified number of places
   * above the decimal.
   *
   * @param value to round
   * @param scale the number of decimal places in the result
   */
  public static double round(double value, int scale, RoundingMode mode) {
    return BigDecimal.valueOf(value).setScale(scale, mode).doubleValue();
  }

}
