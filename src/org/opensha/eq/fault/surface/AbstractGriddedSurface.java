package org.opensha.eq.fault.surface;

import java.util.List;
import java.util.ListIterator;

import org.opensha.eq.forecast.Distances;
import org.opensha.geo.Location;
import org.opensha.geo.LocationList;
import org.opensha.geo.Region;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

/**
 * <b>Title:</b> GriddedSurface<p>
 * <b>Description:</b>
 *
 * This represents 2D container of Location objects defining a geographical surface.
 * There are no constraints on what locations are put where (this is specified by subclasses), 
 * but the presumption is that the the grid of locations map out the surface in some evenly 
 * discretized way.  It is also presumed that the zeroeth row represent the top edge (or trace). <p>
 * 
 * There are also methods for getting info about the surface (e.g., ave dip, ave strike, and various distance metrics). <p>
 *
 * @author revised by field
 */
public abstract class AbstractGriddedSurface  extends Container2DImpl<Location> implements GriddedSurface {

	protected double gridSpacingAlong;
	protected double gridSpacingDown;
	protected Boolean sameGridSpacing;
	
	// for distance measures
//	Location siteLocForDistCalcs = null; //Location.create(Double.NaN,Double.NaN);
//	Location siteLocForDistXCalc = null; //Location.create(Double.NaN,Double.NaN);
//	double distanceJB, distanceSeis, distanceRup, distanceX;
	
	
	// no argument constructor needed by subclasses
	public AbstractGriddedSurface() {}
	
	
	/**
	 *  Constructor for the GriddedSurface object; this sets both the grid spacing along
	 *  and down dip to the value passed in
	 *
	 * @param  numRows  Number of grid points along width of fault
	 * @param  numCols  Number of grid points along length of fault
	 * @param  gridSpacing  Grid Spacing
	 */
	public AbstractGriddedSurface( int numRows, int numCols,double gridSpacing ) {
		super( numRows, numCols );
		gridSpacingAlong = gridSpacing;
		gridSpacingDown = gridSpacing;
		sameGridSpacing = true;
	}
	
	/**
	 *  Constructor for the GriddedSurface object; this sets both the grid spacing along
	 *  and down dip to the value passed in
	 *
	 * @param  numRows  Number of grid points along width of fault
	 * @param  numCols  Number of grid points along length of fault
	 * @param  gridSpacing  Grid Spacing
	 */
	public AbstractGriddedSurface( int numRows, int numCols,double gridSpacingAlong, double gridSpacingDown) {
		super( numRows, numCols );
		this.gridSpacingAlong = gridSpacingAlong;
		this.gridSpacingDown = gridSpacingDown;
		if(gridSpacingAlong == gridSpacingDown)
			sameGridSpacing = true;
		else
			sameGridSpacing = false;
	}



	@Override
	public LocationList getEvenlyDiscritizedListOfLocsOnSurface() {
		return LocationList.create(this);
//		LocationList locList = new LocationList();
//		Iterator<Location> it = listIterator();
//		while(it.hasNext()) locList.add((Location)it.next());
//		return locList;
	}



	/**
	 * Returns the grid spacing along strike
	 * @return
	 */
	public double getGridSpacingAlongStrike() {
		return this.gridSpacingAlong;
	}

	/**
	 * returns the grid spacing down dip
	 * @return
	 */
	public double getGridSpacingDownDip() {
		return this.gridSpacingDown;
	}
	
	/**
	 * tells whether along-strike and down-dip grid spacings are the same
	 * @return
	 */
	public Boolean isGridSpacingSame() {
		return this.sameGridSpacing;
	}
	
	@Override
	@Deprecated
	public LocationList getEvenlyDiscritizedPerimeter() {
		throw new UnsupportedOperationException("to be removed");
//		return GriddedSurfaceUtils.getEvenlyDiscritizedPerimeter(this);
	}
	
	@Override
	/**
	 * Default is to return the evenly discretized version
	 */
	public LocationList getPerimeter() {
		return getEvenlyDiscritizedPerimeter();
	}

	/**
	 * gets the location from the 2D container
	 * @param row
	 * @param column
	 * @return
	 */
	public Location getLocation(int row, int column) {
		return get(row, column);
	}

	/**
	 * Gets a specified row as a fault trace
	 * @param row
	 * @return
	 */
	public LocationList getRowAsTrace(int row) {
		List<Location> locs = Lists.newArrayList();
		for(int col=0; col<getNumCols(); col++) {
			locs.add(get(row, col));
		}
		return LocationList.create(locs);
	}
		
	@Override
	public Distances distanceTo(Location loc) {
		return Distances.compute(this, loc);
	}


//	@Deprecated
//	private void setPropagationDistances() {
//		throw new UnsupportedOperationException("to be removed");
////		double[] dists = GriddedSurfaceUtils.getPropagationDistances(this, siteLocForDistCalcs);
////		distanceRup = dists[0];
////		distanceJB = dists[1];
////		distanceSeis = dists[2];
//	}
	


//	/**
//	 * This returns rupture distance (kms to closest point on the 
//	 * rupture surface), assuming the location has zero depth (for numerical 
//	 * expediency).
//	 * @return 
//	 */
//	public synchronized double getDistanceRup(Location siteLoc){
//		if(!siteLocForDistCalcs.equals(siteLoc)) {
//			siteLocForDistCalcs = siteLoc;
//			setPropagationDistances();
//		}
//		return distanceRup;
//	}
//
//	/**
//	 * This returns distance JB (shortest horz distance in km to surface projection 
//	 * of rupture), assuming the location has zero depth (for numerical 
//	 * expediency).
//	 * @return
//	 */
//	public synchronized double getDistanceJB(Location siteLoc){
//		if(!siteLocForDistCalcs.equals(siteLoc)) {
//			siteLocForDistCalcs = siteLoc;
//			setPropagationDistances();
//		}
//		return distanceJB;
//	}
//
//	/**
//	 * This returns "distance seis" (shortest distance in km to point on rupture 
//	 * deeper than 3 km), assuming the location has zero depth (for numerical 
//	 * expediency).
//	 * @return
//	 */
//	public synchronized double getDistanceSeis(Location siteLoc){
//		if(!siteLocForDistCalcs.equals(siteLoc)) {
//			siteLocForDistCalcs = siteLoc;
//			setPropagationDistances();
//		}
//		return distanceSeis;
//	}
//
//	/**
//	 * This returns distance X (the shortest distance in km to the rupture 
//	 * trace extended to infinity), where values >= 0 are on the hanging wall
//	 * and values < 0 are on the foot wall.  The location is assumed to be at zero
//	 * depth (for numerical expediency).
//	 * @return
//	 */
//	@Deprecated
//	public synchronized double getDistanceX(Location siteLoc){
//		throw new UnsupportedOperationException("to be removed");
////		if(!siteLocForDistXCalc.equals(siteLoc)) {
////			siteLocForDistXCalc = siteLoc;
////			distanceX = GriddedSurfaceUtils.getDistanceX(getEvenlyDiscritizedUpperEdge(), siteLocForDistXCalc);
////		}
////		return distanceX;
//	}
	
	

	@Override
	public LocationList getEvenlyDiscritizedUpperEdge() {
		return getRowAsTrace(0);
	}
	
	@Override
	public LocationList getEvenlyDiscritizedLowerEdge() {
		return getRowAsTrace(getNumRows()-1);
	}
	
	@Override
	/**
	 * Default is to return the evenly discretized version
	 */
	public LocationList getUpperEdge() {
		return getEvenlyDiscritizedUpperEdge();
	}

	/**
	 * This returns the first location on row zero
	 * (which should be the same as the first loc of the FaultTrace)
	 */
	@Override
	public Location getFirstLocOnUpperEdge() {
		return get(0,0);
	}
	
	/**
	 * This returns the last location on row zero (which may not be the 
	 * same as the last loc of the FaultTrace depending on the discretization)
	 */
	@Override
	public Location getLastLocOnUpperEdge() {
		return get(0,getNumCols()-1);
	}

	@Override
	public double length() {
		return getGridSpacingAlongStrike() * (getNumCols()-1);
	}

	@Override
	public double width() {
		return getGridSpacingDownDip() * (getNumRows()-1);
	}

	@Override
	public double area() {
		return width()*length();
	}
	
	@Override
	public double getAveGridSpacing() {
		return (gridSpacingAlong+gridSpacingDown)/2;
	}
	
	@Override
	public String toString() {
	      return Surfaces.getSurfaceInfo(this) + super.toString();
	}
		
}
