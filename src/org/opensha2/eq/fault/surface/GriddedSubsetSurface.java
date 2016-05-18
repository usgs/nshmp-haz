package org.opensha2.eq.fault.surface;

import org.opensha2.eq.model.Distance;
import org.opensha2.geo.Location;
import org.opensha2.geo.LocationList;
import org.opensha2.geo.Locations;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import java.util.List;

/**
 * <b>Title:</b> GriddedSubsetSurface<p>
 *
 * <b>Description:</b> This represents a subset of an GriddedSurface (as a
 * pointer, not duplicated in memory)
 *
 * <b>Note:</b> This class is purely a convinience class that translates indexes
 * so the user can deal with a smaller window than the full GriddedSurface.
 * Think of this as a "ZOOM" function into a GriddedSurface.<p>
 *
 * @author Steven W. Rock & revised by Ned Field
 */
class GriddedSubsetSurface extends ContainerSubset2D<Location> implements GriddedSurface {

  // for distance measures
  // Location siteLocForDistCalcs= null; //new
  // Location(Double.NaN,Double.NaN);
  // Location siteLocForDistXCalc= null; //new
  // Location(Double.NaN,Double.NaN);
  // double distanceJB, distanceSeis, distanceRup, distanceX;
  GriddedSurface parentSurface;

  /**
   * Constructor for the GriddedSubsetSurface object
   *
   * @param numRows Specifies the length of the window.
   * @param numCols Specifies the height of the window
   * @param startRow Start row into the main GriddedSurface.
   * @param startCol Start column into the main GriddedSurface.
   * @param data The main GriddedSurface this is a window into
   * @exception ArrayIndexOutOfBoundsException Thrown if window indexes exceed
   *            the main GriddedSurface indexes.
   */
  public GriddedSubsetSurface(int numRows, int numCols, int startRow, int startCol,
      GriddedSurface data)
      throws ArrayIndexOutOfBoundsException {
    super(numRows, numCols, startRow, startCol, data);
    parentSurface = data;
  }

  /**
   * Add a Location to the grid. This method throws
   * UnsupportedOperationException as it is disabled.
   */
  public void setLocation(int row, int col, Location location) {
    throw new java.lang.UnsupportedOperationException(
      "This function is not implemented in this subclass");
  }

  /**
   * Proxy method that returns the number of rows in the main GriddedSurface.
   */
  public int getMainNumRows() {
    return data.getNumRows();
  }

  /**
   * Proxy method that returns the number of colums in the main GriddedSurface.
   */
  public int getMainNumCols() {
    return data.getNumCols();
  }

  @Override
  public double strike() {
    LocationList locs = getEvenlyDiscritizedUpperEdge();
    return Locations.azimuth(locs.first(), locs.last());
  }

  @Override
  public LocationList getEvenlyDiscritizedListOfLocsOnSurface() {
    return LocationList.create(this);
    // List<Location> locs = Lists.newArrayList();
    // Iterator<Location> it = listIterator();
    // while(it.hasNext()) locs.add(it.next());
    // return LocationList.create(locs);
  }

  /**
   * Proxy method that returns the rupDip of the main GriddedSurface. <P>
   *
   * This should actually be recomputed if the main surface is a
   * SimpleListricGriddedSurface.
   */
  @Override
  public double dip() {
    return ((AbstractGriddedSurface) data).dip();
  }

  @Override
  public double dipRad() {
    return ((AbstractGriddedSurface) data).dipRad();
  }

  /** Debug string to represent a tab. Used by toString(). */
  final static char TAB = '\t';

  @Override
  public double length() {
    return getGridSpacingAlongStrike() * (getNumCols() - 1);
  }

  @Override
  public double width() {
    return getGridSpacingDownDip() * (getNumRows() - 1);
  }

  @Override
  public LocationList getEvenlyDiscritizedPerimeter() {
    throw new UnsupportedOperationException("do be deleted");
    // return GriddedSurfaceUtils.getEvenlyDiscritizedPerimeter(this);
  }

  /**
   * returns the grid spacing along strike
   */
  public double getGridSpacingAlongStrike() {
    return ((AbstractGriddedSurface) data).getGridSpacingAlongStrike();
  }

  /**
   * returns the grid spacing down dip
   */
  public double getGridSpacingDownDip() {
    return ((AbstractGriddedSurface) data).getGridSpacingDownDip();
  }

  // /**
  // * this tells whether along-strike and down-dip grid spacings are the same
  // * @return
  // */
  // public Boolean isGridSpacingSame() {
  // return ((AbstractGriddedSurface)data).isGridSpacingSame();
  // }

  @Override
  public double area() {
    return width() * length();
  }

  public Location getLocation(int row, int column) {
    return get(row, column);
  }

  public LocationList getRow(int row) {
    List<Location> locs = Lists.newArrayList();
    for (int col = 0; col < getNumCols(); col++)
      locs.add(get(row, col));
    return LocationList.create(locs);
  }

  @Override
  public double dipDirection() {
    return ((AbstractGriddedSurface) data).dipDirection();
  }

  @Override
  public double getAveGridSpacing() {
    return parentSurface.getAveGridSpacing();
  }

  @Override
  public LocationList getEvenlyDiscritizedUpperEdge() {
    return getRow(0);
  }

  @Override
  public LocationList getEvenlyDiscritizedLowerEdge() {
    return getRow(getNumRows() - 1);
  }

  @Override
  public Location getFirstLocOnUpperEdge() {
    return get(0, 0);
  }

  @Override
  public Location getLastLocOnUpperEdge() {
    // TODO Auto-generated method stub
    return get(0, getNumCols() - 1);
  }

  @Override /**
             * This assumes the lateral edges are straight lines
             */
  public LocationList getPerimeter() {
    LocationList topTr = getRow(0);
    LocationList botTr = LocationList.create(getRow(getNumRows() - 1)).reverse();
    Iterable<Location> locs = Iterables.concat(topTr, botTr,
      Lists.newArrayList(topTr.get(0)));
    return LocationList.create(locs);
  }

  /**
   * Returns same as getEvenlyDiscritizedUpperEdge()
   */
  @Override
  public LocationList getUpperEdge() {
    return getEvenlyDiscritizedUpperEdge();
  }

  @Override
  public Distance distanceTo(Location loc) {
    return Distance.compute(this, loc);
  }

  // @Deprecated
  // private void setPropagationDistances() {
  // throw new UnsupportedOperationException("to be deleted");
  // // double[] dists = GriddedSurfaceUtils.getPropagationDistances(this,
  // siteLocForDistCalcs);
  // // distanceRup = dists[0];
  // // distanceJB = dists[1];
  // // distanceSeis = dists[2];
  // }
  //
  // /**
  // * This returns rupture distance (kms to closest point on the
  // * rupture surface), assuming the location has zero depth (for numerical
  // * expediency).
  // * @return
  // */
  // public synchronized double getDistanceRup(Location siteLoc){
  // if(!siteLocForDistCalcs.equals(siteLoc)) {
  // siteLocForDistCalcs = siteLoc;
  // setPropagationDistances();
  // }
  // return distanceRup;
  // }
  //
  // /**
  // * This returns distance JB (shortest horz distance in km to surface
  // projection
  // * of rupture), assuming the location has zero depth (for numerical
  // * expediency).
  // * @return
  // */
  // public synchronized double getDistanceJB(Location siteLoc){
  // if(!siteLocForDistCalcs.equals(siteLoc)) {
  // siteLocForDistCalcs = siteLoc;
  // setPropagationDistances();
  // }
  // return distanceJB;
  // }
  //
  // /**
  // * This returns "distance seis" (shortest distance in km to point on
  // rupture
  // * deeper than 3 km), assuming the location has zero depth (for numerical
  // * expediency).
  // * @return
  // */
  // public synchronized double getDistanceSeis(Location siteLoc){
  // if(!siteLocForDistCalcs.equals(siteLoc)) {
  // siteLocForDistCalcs = siteLoc;
  // setPropagationDistances();
  // }
  // return distanceSeis;
  // }
  //
  // /**
  // * This returns distance X (the shortest distance in km to the rupture
  // * trace extended to infinity), where values >= 0 are on the hanging wall
  // * and values < 0 are on the foot wall. The location is assumed to be at
  // zero
  // * depth (for numerical expediency).
  // * @return
  // */
  // @Deprecated
  // public synchronized double getDistanceX(Location siteLoc){
  // throw new UnsupportedOperationException("to be removed");
  // // if(!siteLocForDistXCalc.equals(siteLoc)) {
  // // siteLocForDistXCalc = siteLoc;
  // // distanceX =
  // GriddedSurfaceUtils.getDistanceX(getEvenlyDiscritizedUpperEdge(),
  // siteLocForDistXCalc);
  // // }
  // // return distanceX;
  // }
  //

  @Override
  public double depth() {

    // TODO depth could be computed more efficiently
    // for traces with uniform dpeths, will we ever know
    // this to be absolutely true?

    // if (this.data instanceof GriddedSurfFromSimpleFaultData) {// all
    // depths
    // // are the
    // // same on
    // // the top
    // // row
    // return getLocation(0, 0).depth();
    // }
    double depth = 0;
    LocationList topTrace = getRow(0);
    for (Location loc : topTrace)
      depth += loc.depth();
    return depth / topTrace.size();
  }

  @Override
  public String toString() {
    return Surfaces.getSurfaceInfo(this) + super.toString();
  }

  /**
   * This returns the parent surface
   */
  public GriddedSurface getParentSurface() {
    return parentSurface;
  }

  @Override
  public Location centroid() {
    /*
     * This would have to be a slow lazy implementation and perhaps the racy
     * single-check idiom.
     */
    throw new UnsupportedOperationException("SubsetSurface does not support centroid()");
  }

}
