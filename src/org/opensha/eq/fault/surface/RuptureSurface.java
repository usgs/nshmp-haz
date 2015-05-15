package org.opensha.eq.fault.surface;

import org.opensha.eq.model.Distance;
import org.opensha.geo.Location;

/**
 * This interface defines a rupture surfaces. This does not specify how a rupture 
 * surface is to be represented (in order to maintan flexibility), but rather 
 * specifies what information a rupture surface needs to provide (see method 
 * descriptions for details).
 * @author field
 * 
 * A rupture surface needs only provide that information (the physical properties
 * of the surface) required by GMMs. TODO there is currently more returned by
 * this interface than required
 *
 */
public interface RuptureSurface {
	
	/**
	 * The average strike of this surface in degrees [0°, 360°).
	 */
	public double strike();

	/**
	 * The average dip of this surface in degrees [0°, 90°].
	 */
	public double dip();
	
	
	/**
	 * The average dip of this surface in radians [0, π/2].
	 */
	public double dipRad();

	/**
	 * The average dip direction of this surface in degrees [0°, 360°).
	 */
	public double dipDirection();

	/**
	 * The average length of the upper edge or trace of this surface in km.
	 */
	public double length();

	/**
	 * The average down-dip width of this surface in km.
	 */
	public double width();

	/**
	 * The surface area of this surface in km<sup>2</sup>.
	 */
	public double area();
	
	/**
	 * The average depth to the top of this surface in km (always positive)
	 */
	public double depth();
	
	/**
	 * The centroid of this surface.
	 */
	public Location centroid();
	
	/**
	 * Returns the distance metrics commonly required by PSHA ground motion models
	 * (GMMs): rJB, rRup, and rX.
	 * @param loc {@code Location} to compute distances to
	 * @return a distance metric wrapper object
	 * @see Distance
	 */
	public Distance distanceTo(Location loc);	
	
}
