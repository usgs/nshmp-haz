package gov.usgs.earthquake.nshmp.geo;

import gov.usgs.earthquake.nshmp.internal.Parsing;
import gov.usgs.earthquake.nshmp.util.Maths;

/**
 * Identifiers for basic geographic directions.
 * @author Peter Powers
 */
enum Direction {

  /** North */
  NORTH,
  /** Northeast */
  NORTHEAST,
  /** East */
  EAST,
  /** Southeast */
  SOUTHEAST,
  /** South */
  SOUTH,
  /** Southwest */
  SOUTHWEST,
  /** West */
  WEST,
  /** Northwest */
  NORTHWEST;

  @Override
  public final String toString() {
    return Parsing.enumLabelWithSpaces(this, true);
  }

  /**
   * Returns the bearing in degrees [0°, 360°) associated with this
   * {@code Direction} (e.g. {@code NORTHEAST} = 45°).
   * @return the bearing in degrees
   */
  double bearing() {
    return ordinal() * 45;
  }

  /**
   * Returns the numeric bearing in radians [0, 2π) associated with this
   * {@code Direction} (e.g. {@code NORTHEAST} = π/4).
   * @return the bearing in radians
   */
  double bearingRad() {
    return bearing() * Maths.TO_RADIANS;
  }

  /**
   * Returns the {@code Direction} opposite this {@code Direction}
   * @return the opposite {@code Direction}
   */
  Direction opposite() {
    return valueOf((ordinal() + 4) % 8);
  }

  /**
   * Returns the {@code Direction} encountered moving clockwise from this
   * {@code Direction}.
   * @return the next (moving clockwise) {@code Direction}
   */
  Direction next() {
    return valueOf((ordinal() + 1) % 8);
  }

  /**
   * Returns the {@code Direction} encountered moving anti-clockwise from this
   * {@code Direction}.
   * @return the previous (moving anti-clockwise) {@code Direction}
   */
  Direction prev() {
    return valueOf((ordinal() + 7) % 8);
  }

  /**
   * Returns whether a move in this {@code Direction} will result in a positive,
   * negative or no change to the latitude of some geographic location.
   * @return the sign of a latitude change corresponding to a move in this
   *         {@code Direction}
   */
  int signLatMove() {
    return dLat[ordinal()];
  }

  /**
   * Returns whether a move in this {@code Direction} will result in a positive,
   * negative or no change to the longitude of some geographic location.
   * @return the sign of a longitude change corresponding to a move in this
   *         {@code Direction}
   */
  int signLonMove() {
    return dLon[ordinal()];
  }

  private static final int[] dLat = { 1, 1, 0, -1, -1, -1, 0, 1 };
  private static final int[] dLon = { 0, 1, 1, 1, 0, -1, -1, -1 };
  private static final Direction[] values = values();

  private final Direction valueOf(int ordinal) {
    return values[ordinal];
  }

}
