package org.opensha2.eq.fault.surface;

import org.opensha2.eq.model.Distance;
import org.opensha2.geo.Location;

/**
 * A parameterization of an earthquake rupture surface.
 *
 * @author Ned Field
 * @author Peter Powers
 */
public interface RuptureSurface {

  /*
   * TODO there is currently more returned by this interface than required by
   * GMMs
   */

  /**
   * The average strike of this surface in degrees [0°, 360°).
   */
  double strike();

  /**
   * The average dip of this surface in degrees [0°, 90°].
   */
  double dip();

  /**
   * The average dip of this surface in radians [0, π/2].
   */
  double dipRad();

  /**
   * The average dip direction of this surface in degrees [0°, 360°).
   */
  double dipDirection();

  /**
   * The average length of the upper edge or trace of this surface in km.
   */
  double length();

  /**
   * The average down-dip width of this surface in km.
   */
  double width();

  /**
   * The surface area of this surface in km<sup>2</sup>.
   */
  double area();

  /**
   * The average depth to the top of this surface in km (always positive)
   */
  double depth();

  /**
   * The centroid of this surface.
   */
  Location centroid();

  /**
   * Returns the distance metrics commonly required by PSHA ground motion models
   * (GMMs): rJB, rRup, and rX.
   * @param loc {@code Location} to compute distances to
   * @return a distance metric wrapper object
   * @see Distance
   */
  Distance distanceTo(Location loc);

}
