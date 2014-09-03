package org.opensha.eq.fault.surface;

import org.opensha.geo.Location;
import org.opensha.geo.LocationList;


public interface GriddedSurface extends Container2D<Location>, RuptureSurface {
	
	/**
	 * Returns the grid spacing along strike
	 */
	public double getGridSpacingAlongStrike();

	/**
	 * returns the grid spacing down dip
	 */
	public double getGridSpacingDownDip();
	
//	/**
//	 * tells whether along-strike and down-dip grid spacings are the same
//	 * @return
//	 */
//	public Boolean isGridSpacingSame();
	
	/**
	 * gets the location from the 2D container
	 * @param row index
	 * @param column index
	 */
	public Location getLocation(int row, int column);
	
	/**
	 * This returns the average grid spacing used to define the discretization 
	 * used in what's returned by the methods here that contain "Discretized"
	 * in their names.
	 */
	public double getAveGridSpacing();

	/**
	 * This returns a list of locations that are evenly spread (at least 
	 * approximately) over the rupture surface, with a spacing given by
	 * what's returned by the getGridSpacing() method.  Further details 
	 * are specified by the implementing class.  Don't assume the locations 
	 * are ordered as one reads the words on a page in a book (not the case
	 * for CompoundGriddedSurface).
	 */
	public LocationList getEvenlyDiscritizedListOfLocsOnSurface();

	/**
	 * This returns a list of locations that are evenly spread (at least
	 * approximately) over the surface perimeter, with a spacing given by what's
	 * returned by the getGridSpacing() method. Further details are specified by
	 * the implementing class. These locations should be ordered starting along
	 * the top and moving along following the Aki and Richards convention.
	 */
	@Deprecated
	public LocationList getEvenlyDiscritizedPerimeter();

	/**
	 * This returns a list of locations that are evenly spread along the upper
	 * edge of the surface. Further details are specified by the implementing
	 * class. These locations should be ordered along the fault following the
	 * Aki and Richards convention.
	 */
	public LocationList getEvenlyDiscritizedUpperEdge();

	/**
	 * This returns a list of locations that are evenly spread along the lower
	 * edge of the surface. Further details are specified by the implementing
	 * class. These locations should be ordered along the fault following the
	 * Aki and Richards convention.
	 */
	public LocationList getEvenlyDiscritizedLowerEdge();

	/**
	 * This returns the upper edge of the rupture surface (where the locations
	 * are not necessarily equally spaced). This may be the original Fault Trace
	 * used to define the surface, but not necessarily.
	 */
	public LocationList getUpperEdge();

	/**
	 * Get a list of locations that constitutes the perimeter of the surface
	 * (not necessarily evenly spaced)
	 */
	public LocationList getPerimeter();

	/**
	 * This returns the first location on the upper edge of the surface
	 */
	@Deprecated
	// TODO getUpperEdgeLocationList().last()
	public Location getFirstLocOnUpperEdge();

	/**
	 * This returns the last location on the upper edge of the surface
	 */
	@Deprecated
	// TODO getUpperEdgeLocationList().last()
	public Location getLastLocOnUpperEdge();

}
