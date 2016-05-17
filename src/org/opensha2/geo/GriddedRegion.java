package org.opensha2.geo;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.opensha2.util.MathUtils;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Range;

/**
 * <img style="padding: 30px 40px; float: right;" src="
 * {@docRoot}/resources/gridded_regions_border.jpg"/>A {@code GriddedRegion} is
 * a {@link Region} that has been discretized in latitude and longitude. Each
 * node in a gridded region represents a small area that is some number of
 * degrees in width and height and is identified by a unique {@link Location} at
 * the geographic (lat-lon) center of the node. In the adjacent figure, the
 * heavy black line marks the border of the {@code Region}. The light gray dots
 * mark the {@code Location}s of nodes outside the region, and black dots those
 * inside the region. The dashed grey line marks the border, inside which, a
 * {@code Location} will be associated with a grid node. See
 * {@link GriddedRegion#indexForLocation(Location)} for more details on rules
 * governing whether a grid node is inside a region and whether a
 * {@code Location} will be associated with a grid node.
 * 
 * <p>The {@code Location}s of the grid nodes are indexed internally in order of
 * increasing longitude then latitude starting with the node at the lowest
 * latitude and longitude in the region. {@code GriddedRegion}s are iterable as
 * a shorthand for {@code getNodeList().iterator()}.
 * 
 * <p>Internally, {@code GriddedRegion}s use an anchor {@code Location} to
 * ensure grid nodes fall on specific lat-lon values. This location can be
 * anywhere in- or outside the region to be gridded. If the region contains the
 * anchor location, the anchor will coincide with a grid node. For example,
 * given a grid spacing of 1° and an anchor {@code Location} of 22.1°N -134.7°W,
 * grid nodes within any region will fall at whole valued latitudes + 0.1° and
 * longitudes - 0.7°.
 * 
 * <p><a name="note"></a> <b>Note:</b> Due to rounding errors and the use of an
 * {@link Area} internally to define a {@code Region}'s border,
 * {@link Region#contains(Location)} may not always return the expected result
 * near a border. See {@link Region#contains(Location)} for further details. For
 * a {@code GriddedRegion}, this results in values returned by calls
 * {@link #minGridLat()} etc. for which there may not be any grid nodes. To
 * guarantee node coverage for a {@code GriddedRegion}, say for eventual map
 * output, 'best-practice' dictates expanding a region slightly.
 * 
 * <p>Use the {@link Regions} factory class to create new gridded regions.
 * 
 * @author Nitin Gupta
 * @author Vipin Gupta
 * @author Peter Powers
 * @see Region
 * @see Regions
 */
public class GriddedRegion extends Region implements Iterable<Location> {

  // TODO centralize location based rounding to 5 or 8 decimal places
  // TODO build array to DataUtils with precision setting
  // TODO remove method argument reliznce on AWT classes; may not be possible
  // (or difficult) due to UC3 FaultPolyMgr implementation

  /** Convenience reference for an anchor at (0°, 0°). */
  public final static Location ANCHOR_0_0 = Location.create(0, 0);

  private static final Range<Double> SPACING_RANGE = Range.openClosed(0d, 5d);

  // grid range data
  private double minGridLat, minGridLon, maxGridLat, maxGridLon;
  private int latSize, lonSize;

  // the lat-lon arrays of node edges
  private double[] latEdges;
  private double[] lonEdges;

  // Location at lower left corner of region bounding rect
  private Location anchor;

  // lookup array for grid nodes; has length of master grid spanning
  // region bounding box; all nodes outside region have values of -1;
  // all valid nodes point to position in nodeList; gridIndices increase
  // across and then up
  private int[] gridIndices;

  // list of nodes
  private LocationList nodes;

  // grid data
  private double latSpacing;
  private double lonSpacing;
  private int nodeCount;

  /* for internal package use only */
  GriddedRegion(String name) {
    super(!Strings.isNullOrEmpty(name) ? name + " Gridded" : "Unnamed Gridded Region");
  }

  /**
   * Returns the longitudinal grid node spacing for this region.
   * @return the longitudinal grid node spacing (in degrees)
   */
  public double latSpacing() {
    return latSpacing;
  }

  /**
   * Returns the latitudinal grid node spacing for this region.
   * @return the latitudinal grid node spacing (in degrees)
   */
  public double lonSpacing() {
    return lonSpacing;
  }

  /**
   * Returns the total number of grid nodes in this region.
   * @return the number of grid nodes
   */
  public int size() {
    return nodeCount;
  }

  /**
   * Returns whether this region contains any grid nodes. If a regions
   * dimensions are smaller than the grid spacing, it may be empty.
   * @return {@code true} if region has no grid nodes; {@code false} otherwise
   */
  public boolean isEmpty() {
    return nodeCount == 0;
  }

  /**
   * Returns the index of the node at the supplied {@code Direction} from the
   * node at the supplied index.
   * @param index to move from
   * @param dir to move
   * @return index at {@code Direction} or -1 if no node exists
   * @throws NullPointerException if supplied index is not a valid grid index
   */
  public int move(int index, Direction dir) {
    Location start = locationForIndex(index);
    checkNotNull(start, "Invalid start index");
    Location end = Location.create(start.lat() + latSpacing * dir.signLatMove(), start.lon() +
      lonSpacing * dir.signLonMove());
    return indexForLocation(end);
  }

  /**
   * Compares this {@code GriddedRegion} to another and returns {@code true} if
   * they are the same with respect to aerial extent (both exterior and interior
   * borders), grid node spacing, and location. This method ignores the names of
   * the {@code GriddedRegion}s. Use {@code GriddedRegion.equals(Object)} to
   * include name comparison.
   * 
   * @param gr the {@code Regions} to compare
   * @return {@code true} if this {@code Region} has the same geometry as the
   *         supplied {@code Region}, {@code false} otherwise
   * @see GriddedRegion#equals(Object)
   */
  public boolean equalsRegion(GriddedRegion gr) {
    if (!super.equalsRegion(gr)) return false;
    if (!gr.anchor.equals(anchor)) return false;
    if (gr.latSpacing != latSpacing) return false;
    if (gr.lonSpacing != lonSpacing) return false;
    return true;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof GriddedRegion)) return false;
    GriddedRegion gr = (GriddedRegion) obj;
    if (!name().equals(gr.name())) return false;
    return equalsRegion(gr);
  }

  @Override
  public int hashCode() {
    return super.hashCode() ^ anchor.hashCode() ^ Double.valueOf(latSpacing).hashCode() ^
      Double.valueOf(lonSpacing).hashCode();
  }

  /**
   * Overridden to throw an {@code UnsupportedOperationException} when called.
   * The border of a {@code GriddedRegion} may only be set on initialization. To
   * create a {@code GriddedRegion} that has interiors (donut-holes), first
   * create a {@code Region} with the required border and interiors using
   * {@link Region#addInterior(Region)} and then use it to initialize a
   * {@code GriddedRegion}.
   * 
   * @throws UnsupportedOperationException
   * @see Region#addInterior(Region)
   */
  @Override
  public void addInterior(Region region) {
    throw new UnsupportedOperationException(
      "A GriddedRegion may not have an interior Region set");
  }

  @Override
  public Iterator<Location> iterator() {
    return nodes.iterator();
  }

  /**
   * Returns the locations of all the nodes in the region as a
   * {@code LocationList}.
   * @return a list of all the node locations in the region.
   */
  public LocationList nodes() {
    return nodes;
  }

  /**
   * Returns the {@code Location} at a given grid index. This method is intended
   * for random access of nodes in this gridded region; to cycle over all nodes,
   * iterate over the region.
   * 
   * @param index of location to retrieve
   * @return the {@code Location} or {@code null} if index is out of range
   */
  public Location locationForIndex(int index) {
    try {
      return nodes.get(index);
    } catch (IndexOutOfBoundsException e) {
      return null;
    }
  }

  /**
   * Returns the list of grid indices spanned by the bounds of the supplied
   * region. Bounds refers to the rectangle that completely encloses a region.
   * Note that this is a list of all nodes for which any part, however small,
   * overlaps the supplied rectangle and is not restricted to grid centers.
   * @param rect to process
   * @return the list of grid indices that intersect the bounding box of the
   *         supplied region
   * @throws IllegalArgumentException if the supplied rectangle is not
   *         completely enclosed by thei {@code Region}
   */
  public List<Integer> indicesForBounds(Rectangle2D rect) {
    try {
      // fast lookups MAY fail as intial contains(rect) test may
      // pass when a corner vertix is in fact absent
      return indexLookupFast(rect);
    } catch (Exception e) {
      return indexLookupSlow(rect);
    }
  }

  /* Efficiently finds relevant indices without intersection testing */
  private List<Integer> indexLookupFast(Rectangle2D rect) {
    checkArgument(area.contains(rect));
    Location pLL = Location.create(rect.getMinY(), rect.getMinX());
    Location pUL = Location.create(rect.getMaxY(), rect.getMinX());
    Location pLR = Location.create(rect.getMinY(), rect.getMaxX());
    int indexLL = indexForLocation(pLL);
    int indexUL = indexForLocation(pUL);
    int indexLR = indexForLocation(pLR);

    // indices of row starts
    List<Integer> rowStarts = Lists.newArrayList();
    int rowStartIndex = indexLL;
    int lastRowStartIndex = indexUL;
    while (rowStartIndex <= lastRowStartIndex) {
      rowStarts.add(rowStartIndex);
      Location currLoc = locationForIndex(rowStartIndex);
      Location nextLoc = Location.create(currLoc.lat() + latSpacing, currLoc.lon());
      rowStartIndex = indexForLocation(nextLoc);
    }

    // row length
    int len = indexLR - indexLL + 1;

    // build list
    List<Integer> indices = Lists.newArrayList();
    for (Integer index : rowStarts) {
      addRange(indices, index, len);
    }
    return indices;

  }

  /* Brute force approach does intersect test for all region node polys */
  private List<Integer> indexLookupSlow(Rectangle2D rect) {
    // System.out.println("Sloooooooow");
    List<Integer> indices = Lists.newArrayList();
    for (int i = 0; i < nodeCount; i++) {
      Area area = areaForIndex(i);
      if (area.intersects(rect)) indices.add(i);
    }
    return indices;
  }

  /* Adds an inclusive range of ints to a list */
  private static void addRange(List<Integer> ints, int start, int num) {
    for (int i = start; i < start + num; i++) {
      ints.add(i);
    }
  }

  /**
   * Returns the {@code Region} that bounds a node
   * @param index of the node of interest
   * @return the bounding region of the specified node
   */
  public Area areaForIndex(int index) {
    Location p = locationForIndex(index);
    return RegionUtils.getNodeShape(p, lonSpacing, latSpacing);
  }

  /**
   * Returns the index of the grid node associated with a given {@code Location}
   * or -1 if the associated grid node is ouside this gridded region. For a
   * {@code Location} to be associated with a node, it must fall within the
   * square region on which the node is centered. Note that this allows for some
   * {@code Location}s that are outside the region border to still be associated
   * with a node. Conversely, a {@link Region#contains(Location)} may return
   * {@code true} while this method returns -1. Users interested in node
   * association should always use this method alone and test for -1 return
   * value. {@link Region#contains(Location)} should <i>NOT</i> be used a as a
   * test prior to calling this method. <br/> <br/> The figure and table below
   * indicate the results produced by calling {@code contains()} or
   * {@code indexForLocation()}. The arrows in the figure point towards the
   * interior of the {@code Region}. The dots mark the centered {@code Location}
   * of each grid node and the numbers indicate the index value of each.
   * Remember that both methods test for insidedness according to the rules
   * defined in the {@link Shape} interface. <br/> <img style="padding: 20px;
   * display: block; margin-left:auto; margin-right:auto;" src="
   * {@docRoot}/resources/node_association.jpg"/> <br/> <table id="table-a">
   * <thead> <tr> <th>Location</th> <th> {@code contains(Location)}</th> <th>
   * {@code indexForLocation(Location)} </th> </tr> <thead> <tbody> <tr>
   * <td><b>A</b></td> <td>{@code true}</td> <td>-1</td> </tr> <tr>
   * <td><b>B</b></td> <td>{@code false}</td> <td>3</td> </tr> <tr>
   * <td><b>C</b></td> <td>{@code false}</td> <td>3</td> </tr> <tr>
   * <td><b>D</b></td> <td>{@code false}</td> <td>-1</td> </tr> <tr>
   * <td><b>E</b></td> <td>{@code true}</td> <td>3</td> </tr> <tr>
   * <td><b>F</b></td> <td>{@code true}</td> <td>3</td> </tr> <tr>
   * <td><b>G</b></td> <td>{@code true}</td> <td>4</td> </tr> </tbody> </table>
   * 
   * @param loc the {@code Location} to match to a grid node index
   * @return the index of the associated node or -1 if no such node exists
   */
  public int indexForLocation(Location loc) {
    int lonIndex = nodeIndex(lonEdges, loc.lon());
    if (lonIndex == -1) return -1;
    int latIndex = nodeIndex(latEdges, loc.lat());
    if (latIndex == -1) return -1;
    int gridIndex = ((latIndex) * lonSize) + lonIndex;
    return gridIndices[gridIndex];
  }

  /**
   * Returns the minimum grid latitude. Note that there may not actually be any
   * nodes at this latitude. See class <a href="#note">note</a>. If the region
   * is devoid of nodes, method will return {@code Double.NaN}.
   * 
   * @return the minimum grid latitude
   * @see Region#contains(Location)
   */
  public double minGridLat() {
    return minGridLat;
  }

  /**
   * Returns the maximum grid latitude. Note that there may not actually be any
   * nodes at this latitude. See class <a href="#note">note</a>. If the region
   * is devoid of nodes, method will return {@code Double.NaN}.
   * 
   * @return the maximum grid latitude
   * @see Region#contains(Location)
   */
  public double maxGridLat() {
    return maxGridLat;
  }

  /**
   * Returns the minimum grid longitude. Note that there may not actually be any
   * nodes at this longitude. See class <a href="#note">note</a>. If the region
   * is devoid of nodes, method will return {@code Double.NaN}.
   * 
   * @return the minimum grid longitude
   * @see Region#contains(Location)
   */
  public double minGridLon() {
    return minGridLon;
  }

  /**
   * Returns the maximum grid longitude. Note that there may not actually be any
   * nodes at this longitude. See class <a href="#note">note</a>. If the region
   * is devoid of nodes, method will return {@code Double.NaN}.
   * 
   * @return the maximum grid longitude
   * @see Region#contains(Location)
   */
  public double maxGridLon() {
    return maxGridLon;
  }

  /*
   * Returns the node index of the value or -1 if the value is out of range.
   * Expects the array of edge values.
   */
  private static int nodeIndex(double[] edgeVals, double value) {
    // If a value exists in an array, binary search returns the index
    // of the value. If the value is less than the lowest array value,
    // binary search returns -1. If the value is within range or
    // greater than the highest array value, binary search returns
    // (-insert_point-1). The SHA rule of thumb follows the java rules
    // of insidedness, so any exact node edge value is associated with
    // the node above. Therefore, the negative within range values are
    // adjusted to the correct node index with (-index-2). Below range
    // values are already -1; above range values are corrected to -1.
    int i = Arrays.binarySearch(edgeVals, value);
    i = (i < -1) ? (-i - 2) : i;
    return (i == edgeVals.length - 1) ? -1 : i;
  }

  /*
   * Initializes a region via copy.
   */
  void initCopy(GriddedRegion region) {
    super.initCopy(region);
    // all fields are private or immutable objects
    minGridLat = region.minGridLat;
    minGridLon = region.minGridLon;
    maxGridLat = region.maxGridLat;
    maxGridLon = region.maxGridLon;
    latSize = region.latSize;
    lonSize = region.lonSize;
    latEdges = region.latEdges;
    lonEdges = region.lonEdges;
    anchor = region.anchor;
    gridIndices = region.gridIndices;
    nodes = region.nodes;
    latSpacing = region.latSpacing;
    lonSpacing = region.lonSpacing;
    nodeCount = region.nodeCount;
  }

  /* grid setup */
  void initGrid(double latSpacing, double lonSpacing, Location anchor) {
    setSpacing(latSpacing, lonSpacing);
    setAnchor(anchor);
    initNodes();
  }

  /* Sets the gid node spacing. */
  private void setSpacing(double lat, double lon) {
    String mssg = "[%s] must be 0° > spacing ≥ 5°";
    checkArgument(SPACING_RANGE.contains(lat), "Latitude" + mssg, lat);
    checkArgument(SPACING_RANGE.contains(lon), "Latitude" + mssg, lon);
    latSpacing = lat;
    lonSpacing = lon;
  }

  /*
   * Sets the grid anchor value. The Location provided is adjusted to be the
   * lower left corner (min lat-lon) of the region bounding grid. If the region
   * grid extended infinitely, both the input and adjusted anchor Locations
   * would coincide with grid nodes.
   */
  private void setAnchor(Location anchor) {
    Bounds bounds = bounds();
    if (anchor == null) anchor = Location.create(bounds.min().lat(), bounds.min().lon());
    double lat = computeAnchor(bounds.min().lat(), anchor.lat(), latSpacing);
    double lon = computeAnchor(bounds.min().lon(), anchor.lon(), lonSpacing);
    this.anchor = Location.create(lat, lon);
  }

  /* Computes adjusted anchor values. */
  private static double computeAnchor(double min, double anchor, double spacing) {
    double delta = anchor - min;
    double num_div = Math.floor(delta / spacing);
    double offset = delta - num_div * spacing;
    double newAnchor = min + offset;
    newAnchor = (newAnchor < min) ? newAnchor + spacing : newAnchor;
    // round to cleaner values: e.g. 1.0 vs. 0.999999999997
    return MathUtils.round(newAnchor, 8);
  }

  /* Initilize the grid index, node edge, and Location arrays */
  private void initNodes() {

    Bounds bounds = bounds();

    // temp node center arrays
    double[] lonNodes = initNodeCenters(anchor.lon(), bounds.max().lon(), lonSpacing);
    double[] latNodes = initNodeCenters(anchor.lat(), bounds.max().lat(), latSpacing);

    // node edge arrays
    lonEdges = initNodeEdges(anchor.lon(), bounds.max().lon(), lonSpacing);
    latEdges = initNodeEdges(anchor.lat(), bounds.max().lat(), latSpacing);

    // range data
    latSize = latNodes.length;
    lonSize = lonNodes.length;
    minGridLat = (latSize != 0) ? latNodes[0] : Double.NaN;
    maxGridLat = (latSize != 0) ? latNodes[latSize - 1] : Double.NaN;
    minGridLon = (lonSize != 0) ? lonNodes[0] : Double.NaN;
    maxGridLon = (lonSize != 0) ? lonNodes[lonSize - 1] : Double.NaN;
    int gridSize = lonSize * latSize;

    // node data
    gridIndices = new int[gridSize];
    List<Location> nodeList = Lists.newArrayList();
    int nodeIndex = 0;
    int gridIndex = 0;
    Location loc;
    for (double lat : latNodes) {
      for (double lon : lonNodes) {
        loc = Location.create(lat, lon);
        if (contains(loc)) {
          nodeList.add(loc);
          gridIndices[gridIndex] = nodeIndex++;
        } else {
          gridIndices[gridIndex] = -1;
        }
        gridIndex++;
      }
    }
    nodes = LocationList.create(nodeList);
    nodeCount = nodeIndex;
  }

  /*
   * Initializes an array of node centers. The first (lowest) bin is centered on
   * the min value.
   */
  private static double[] initNodeCenters(double min, double max, double width) {
    // nodeCount is num intervals between min and max + 1
    int nodeCount = (int) Math.floor((max - min) / width) + 1;
    double firstCenterVal = min;
    return buildArray(firstCenterVal, nodeCount, width);
  }

  /*
   * Initializes an array of node edges which can be used to associate a value
   * with a particular node using binary search.
   */
  private static double[] initNodeEdges(double min, double max, double width) {
    // edges is binCount + 1
    int edgeCount = (int) Math.floor((max - min) / width) + 2;
    // offset first bin edge half a binWidth
    double firstEdgeVal = min - (width / 2);
    return buildArray(firstEdgeVal, edgeCount, width);
  }

  /* Node edge and center array builder. */
  private static double[] buildArray(double startVal, int count, double step) {

    double[] values = new double[count];
    double val = startVal;
    for (int i = 0; i < count; i++) {
      // round to cleaner values: e.g. 1.0 vs. 0.999999999997
      values[i] = MathUtils.round(val, 8);
      val += step;
    }
    return values;
  }

}
