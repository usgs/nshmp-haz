package org.opensha.eq.fault.surface;

import org.opensha.eq.model.Distances;
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
	 * @return the strike of this surface
	 */
	public double strike();

	/**
	 * The average dip of this surface in degrees [0°, 90°].
	 * @return the dip of this surface
	 */
	public double dip();
	
	/**
	 * The average dip direction of this surface in degrees [0°, 360°).
	 * @return the dip direction of this surface
	 */
	public double dipDirection();

	/**
	 * The average length of the upper edge or trace of this surface in km.
	 * @return the length of this surface
	 */
	public double length();

	/**
	 * The average down-dip width of this surface in km.
	 * @return the down-dip width of this surface
	 */
	public double width();

	/**
	 * The surface area of this surface in km<sup>2</sup>.
	 * @return the area of this surface
	 */
	public double area();
	
	/**
	 * The average depth to the top of this surface in km (always positive)
	 * @return the depth to the top of this surface
	 */
	public double depth();
	
	/**
	 * The centroid of this surface.
	 * @return the centroid of this surface
	 */
	public Location centroid();
	
	/**
	 * Returns the distance metrics commonly required by PSHA ground motion models
	 * (GMMs): rJB, rRup, and rX.
	 * @param loc {@code Location} to compute distances to
	 * @return a distance metric wrapper object
	 * @see Distances
	 */
	public Distances distanceTo(Location loc);	
	
}
