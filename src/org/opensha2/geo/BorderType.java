package org.opensha2.geo;

/**
 * A {@code BorderType} specifies how lines connecting two points on the earth's
 * surface should be represented. A {@code BorderType} is required for the
 * initialization of some Regions.
 *
 * <p><img style="padding: 0px 80px; float: right;" src=
 * "{@docRoot}/resources/border_differences.jpg"/>The adjacent figure shows that
 * a {@code MERCATOR_LINEAR} border between two {@code Location}s with the same
 * latitude will follow the corresponding parallel (solid line). The equivalent
 * {@code GREAT_CIRCLE} border segment will follow the shortest path between the
 * two {@code Location}s (dashed line).
 *
 * @author Peter Powers
 * @see Region
 * @see Location
 */
public enum BorderType {

  /**
   * Defines a {@link Region} border as following a straight line in a Mercator
   * projection
   */
  MERCATOR_LINEAR,

  /**
   * Defines a {@link Region} border as following a great circle.
   */
  GREAT_CIRCLE;

}
