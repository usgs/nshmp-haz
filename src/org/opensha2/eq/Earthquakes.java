package org.opensha2.eq;

import static java.lang.Math.log10;
import static java.lang.Math.pow;

import static org.opensha2.data.Data.checkInRange;

import com.google.common.collect.Range;

/**
 * Utility constants and methods pertaining to properties of earthquakes.
 *
 * @author Peter Powers
 */
public final class Earthquakes {

  /**
   * Minimum supported earthquake magnitude (-2.0). This numeric value is used for
   * range checking and is not bound to any particular magnitude scale.
   */
  public static final double MIN_MAG = -2.0;

  /**
   * Maximum supported earthquake magnitude (9.7). This numeric value is used for
   * range checking and is not bound to any particular magnitude scale.
   */
  public static final double MAX_MAG = 9.7;

  /** The minimum and maximum supported magnitude as a range. */
  public static final Range<Double> MAG_RANGE = Range.closed(MIN_MAG, MAX_MAG);

  /** Shear modulus {@code μ = 3·10¹⁰ N·m⁻²}. */
  public static final double MU = 3e10;

  private static final double SCALE_N_M = 9.05;

  /*
   * Equivalent dyn/cm values for MU = 3e11 dyn/cm²
   * 
   * Equivalent dyn/cm values for SCALE_ = 16.05 dyn·cm
   */

  /**
   * Ensure {@code -2.0 ≤ magnitude ≤ 9.7}.
   *
   * @param magnitude to validate
   * @return the validated magnitude
   * @throws IllegalArgumentException if {@code magnitude} value is outside the
   *         range {@code (-2.0..9.7)}
   */
  public static double checkMagnitude(double magnitude) {
    return checkInRange(MAG_RANGE, "Magnitude", magnitude);
  }

  /**
   * Convert moment magnitude, <em>M</em><sub>W</sub>, to seismic moment,
   * <em>M</em>₀, following the equation of Hanks and Kanamori
   * (1997).
   *
   * @param magnitude to convert
   * @return the equivalent seismic moment in N·m
   */
  public static double magToMoment(double magnitude) {
    return pow(10, 1.5 * magnitude + SCALE_N_M);
  }

  /**
   * Convert seismic moment, <em>M</em>₀, to moment magnitude,
   * <em>M</em><sub>w</sub>, following the equation of Hanks and Kanamori
   * (1997).
   *
   * @param moment to convert (in N·m)
   * @return the equivalent moment magnitude
   */
  public static double momentToMag(double moment) {
    return (log10(moment) - SCALE_N_M) / 1.5;
  }

  /**
   * Calculate (in SI units) the seismic moment of a fault area and average
   * slip, assuming a shear modulus of {@link #MU}. If slip <em>rate</em> is
   * supplied, moment <em>rate</em> is returned.
   *
   * @param area in m²
   * @param slip in m (or slip rate in m·t⁻¹)
   * @return moment (<em>M</em>₀) in N·m (or moment rate in N·m·t⁻¹)
   */
  public static double moment(double area, double slip) {
    return MU * area * slip;
  }

  /**
   * Calculate (in SI units) the average slip across a fault area with the
   * supplied moment, assuming a shear modulus of {@link #MU}. If moment
   * <em>rate</em> is supplied, slip <em>rate</em> is returned.
   *
   * @param area in m²
   * @param moment <em>M</em>₀ in N·m (or moment rate in N·m·t⁻¹)
   * @return slip in m (or slip rate in m·t⁻¹)
   */
  public static double slip(double area, double moment) {
    return moment / (area * MU);
  }

}
