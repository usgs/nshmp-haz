package org.opensha2.eq;

import static org.opensha2.data.Data.checkInRange;

import com.google.common.collect.Range;

/**
 * Constants and utility methods pertaining to properties of earthquakes.
 * 
 * <p><strong>Note:</strong> The various fields and methods of this class that
 * relate to earthquake depths follow the positive-down convention of
 * seismology.
 *
 * @author Peter Powers
 */
public final class Earthquakes {

  /**
   * Minimum supported earthquake depth: {@code -5 km}.
   */
  public static final double MIN_DEPTH = -5.0;

  /**
   * Maximum supported earthquake depth: {@code 700 km}.
   */
  public static final double MAX_DEPTH = 700.0;

  /**
   * Supported earthquake depths: {@code [-5..700] km}.
   */
  public static final Range<Double> DEPTH_RANGE = Range.closed(MIN_DEPTH, MAX_DEPTH);

  /*
   * TODO possibly adjust Faults.CRUSTAL_DEPTH_RANGE CB14 restricts to 20 km and
   * the PEER database is pretty comprehensive. Perhaps implement active and
   * stable crust depth range.
   */

  /** Supported crustal earthquake depths: {@code [0..40] km}. */
  public static final Range<Double> CRUSTAL_DEPTH_RANGE = Range.closed(0.0, 40.0);

  /** Supported crustal earthquake widths: {@code [0..60] km}. */
  public static final Range<Double> CRUSTAL_WIDTH_RANGE = Range.openClosed(0.0, 60.0);

  /** Supported subduction interface earthquake depths: {@code [0..60] km}. */
  public static final Range<Double> INTERFACE_DEPTH_RANGE = Range.closed(0.0, 60.0);

  /** Supported subduction interface earthquake widths: {@code [0..200] km}. */
  public static final Range<Double> INTERFACE_WIDTH_RANGE = Range.openClosed(0.0, 200.0);

  /** Supported subduction intraslab earthquake depths: {@code [20..700] km}. */
  public static final Range<Double> SLAB_DEPTH_RANGE = Range.closed(20.0, 700.0);

  /**
   * Minimum supported earthquake magnitude: {@code -2.0}. This numeric value is
   * used for range checking and is not bound to any particular magnitude scale.
   */
  public static final double MIN_MAG = -2.0;

  /**
   * Maximum supported earthquake magnitude: {@code 9.7}. This numeric value is
   * used for range checking and is not bound to any particular magnitude scale.
   */
  public static final double MAX_MAG = 9.7;

  /**
   * Supported earthquake magnitude range: {@code [-2.0..9.7]}. This range of
   * values is not bound to any particular magnitude scale.
   */
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
   * Ensure {@code -5 ≤ depth ≤ 700 km}.
   * 
   * @param depth to validate
   * @return the validated depth
   * @throws IllegalArgumentException if {@code depth} is outside the range
   *         {@code [-5..700] km}
   */
  public static double checkDepth(double depth) {
    return checkInRange(DEPTH_RANGE, "Depth", depth);
  }

  /**
   * Ensure {@code 0 ≤ depth ≤ 40 km}.
   *
   * @param depth to validate
   * @return the validated depth for use inline
   * @throws IllegalArgumentException if {@code depth} is outside the range
   *         {@code [0..40] km}
   */
  public static double checkCrustalDepth(double depth) {
    return checkInRange(CRUSTAL_DEPTH_RANGE, "Depth", depth);
  }

  /**
   * Ensure {@code 0 ≤ width ≤ 60 km}.
   *
   * @param width to validate
   * @return the validated width
   * @throws IllegalArgumentException if {@code width} is outside the range
   *         {@code [0..60] km}
   */
  public static double checkCrustalWidth(double width) {
    return checkInRange(CRUSTAL_WIDTH_RANGE, "Width", width);
  }

  /**
   * Ensure {@code 0 ≤ depth ≤ 60 km}.
   *
   * @param depth to validate
   * @return the validated depth
   * @throws IllegalArgumentException if {@code depth} is outside the range
   *         {@code [0..60] km}
   */
  public static double checkInterfaceDepth(double depth) {
    return checkInRange(INTERFACE_DEPTH_RANGE, "Subduction Interface Depth", depth);
  }

  /**
   * Ensure {@code 0 ≤ width ≤ 200 km}.
   *
   * @param width to validate
   * @return the validated width
   * @throws IllegalArgumentException if {@code width} is outside the range
   *         {@code [0..200] km}
   */
  public static double checkInterfaceWidth(double width) {
    return checkInRange(INTERFACE_WIDTH_RANGE, "Subduction Interface Width", width);
  }

  /**
   * Ensure {@code 20 ≤ depth ≤ 700 km}.
   *
   * @param depth to validate
   * @return the validated depth
   * @throws IllegalArgumentException if {@code depth} is outside the range
   *         {@code [20..700] km}
   */
  public static double checkSlabDepth(double depth) {
    return checkInRange(SLAB_DEPTH_RANGE, "Subduction Slab Depth", depth);
  }

  /**
   * Ensure {@code -2.0 ≤ magnitude ≤ 9.7}.
   *
   * @param magnitude to validate
   * @return the validated magnitude
   * @throws IllegalArgumentException if {@code magnitude} value is outside the
   *         range {@code [-2.0..9.7]}
   */
  public static double checkMagnitude(double magnitude) {
    return checkInRange(MAG_RANGE, "Magnitude", magnitude);
  }

  /**
   * Convert moment magnitude, <em>M</em><sub>W</sub>, to seismic moment,
   * <em>M</em>₀, following the equation of Hanks and Kanamori (1997).
   *
   * @param magnitude to convert
   * @return the equivalent seismic moment in N·m
   */
  public static double magToMoment(double magnitude) {
    return Math.pow(10, 1.5 * magnitude + SCALE_N_M);
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
    return (Math.log10(moment) - SCALE_N_M) / 1.5;
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
