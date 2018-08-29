package gov.usgs.earthquake.nshmp.geo;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static gov.usgs.earthquake.nshmp.geo.BorderType.GREAT_CIRCLE;
import static gov.usgs.earthquake.nshmp.geo.BorderType.MERCATOR_LINEAR;

import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.primitives.Doubles;

import gov.usgs.earthquake.nshmp.data.Data;
import gov.usgs.earthquake.nshmp.util.Maths;
import gov.usgs.earthquake.nshmp.util.Named;

/**
 * A {@code Region} is a polygonal area on the surface of the earth. The
 * vertices comprising the border of each {@code Region} are stored internally
 * as latitude-longitude coordinate pairs in an {@link Area}, facilitating
 * operations such as union, intersect, and contains. Insidedness rules follow
 * those defined in the {@link Shape} interface.
 *
 * <p>New {@code Region}s are created exclusively through static factory
 * methods, some of which require the specification of a {@link BorderType}. If
 * one wishes to create a geographic {@code Region} that represents a rectangle
 * in a Mercator projection, {@link BorderType#MERCATOR_LINEAR} should be used,
 * otherwise, the border will follow a {@link BorderType#GREAT_CIRCLE} between
 * two points. Over small distances, great circle paths are approximately the
 * same as linear, Mercator paths. Over longer distances, a great circle is a
 * better representation of a line on a globe. Internally, great circles are
 * approximated by multiple straight line segments that have a maximum length of
 * 100km.
 *
 * <p>A {@code Region} may also have interior (or negative) areas. Any call to
 * {@link Region#contains(Location)} for a {@code Location} within or on the
 * border of such an interior area will return {@code false}, subject to the
 * rules of insidedness.
 *
 * <p><b>Note:</b> The current implementation does not support regions that are
 * intended to span ±180°. Any such regions will wrap the long way around the
 * earth and results are undefined. Regions that encircle either pole are not
 * supported either.
 *
 * <p><b>Note:</b> Due to rounding errors and the use of an {@link Area}
 * internally to define a {@code Region}'s border,
 * {@link Region#contains(Location)} may not always return the expected result
 * near a border. See {@link Region#contains(Location)} for further details.
 *
 * <p>Use the {@link Regions} factory class to create new regions.
 *
 * @author Peter Powers
 * @see Shape
 * @see Area
 * @see BorderType
 * @see Regions
 */
public class Region implements Named {

  // TODO allow Regions spanning the Int'l date line via Locations that
  // can be in the range -180 to 360
  // TODO is this equalsRegion() really necessary as public
  // TODO need to mention results are undefined for self-intersecting regions

  // although border vertices can be accessed by path-iterating over
  // area, an immutable list is stored for convenience
  private LocationList border;

  // interior region list; may remain null
  private List<LocationList> interiors;

  // Internal representation of region
  Area area;

  // Default angle used to subdivide a circular region: 10 deg
  private static final double WEDGE_WIDTH = 10;

  // Default segment length for great circle splitting: 100km
  private static final double GC_SEGMENT = 100;

  private final String name;

  /* for internal package use only */
  Region(String name) {
    this.name = Strings.isNullOrEmpty(name) ? "Unnamed Region" : name;
  }

  /**
   * Returns whether the given {@code Location} is inside this {@code Region}.
   * The determination follows the rules of insidedness defined in the
   * {@link Shape} interface.
   *
   * <p><b>Note:</b> By using an {@link Area} internally to manage this
   * {@code Region}'s geometry, there are instances where rounding errors may
   * cause this method to yield unexpected results. For instance, although a
   * {@code Region}'s southernmost point might be initially defined as 40.0°,
   * the internal {@code Area} may return 40.0000000000001 on a call to
   * {@code getMinLat()} and calls to {@code contains(new Location(40,*))} will
   * return false.
   *
   * @param loc the {@code Location} to test
   * @return {@code true} if the {@code Location} is inside the Region,
   *         {@code false} otherwise
   * @see java.awt.Shape
   */
  public boolean contains(Location loc) {
    return area.contains(loc.lon(), loc.lat());
  }

  /**
   * Tests whether another {@code Region} is entirely contained within this
   * {@code Region}.
   *
   * @param region to check
   * @return {@code true} if this contains the {@code Region}; {@code false}
   *         otherwise
   */
  public boolean contains(Region region) {
    Area areaUnion = (Area) area.clone();
    areaUnion.add(region.area);
    return area.equals(areaUnion);
  }

  /**
   * Returns whether this {@code Region} is rectangular in shape when
   * represented in a Mercator projection.
   *
   * @return {@code true} if rectangular, {@code false} otherwise
   */
  public boolean isRectangular() {
    return area.isRectangular();
  }

  /**
   * Adds an interior (donut-hole) to this {@code Region}. Any call to
   * {@link Region#contains(Location)} for a {@code Location} within this
   * interior area will return {@code false}. Any interior {@code Region} must
   * lie entirely inside this {@code Region}. Moreover, any interior may not
   * overlap or enclose any existing interior region. Internally, the border of
   * the supplied {@code Region} is copied and stored as an unmodifiable
   * {@code List}. No reference to the supplied {@code Region} is retained.
   *
   * @param region to use as an interior or negative space
   * @throws NullPointerException if the supplied {@code Region} is {@code null}
   * @throws IllegalArgumentException if the supplied {@code Region} is not
   *         entirly contained within this {@code Region}
   * @throws IllegalArgumentException if the supplied {@code Region} is not
   *         singular (i.e. already has an interior itself)
   * @throws IllegalArgumentException if the supplied {@code Region} overlaps
   *         any existing interior {@code Region}
   * @see Region#interiors()
   */
  public void addInterior(Region region) {
    validate(region); // test for non-singularity or null
    checkArgument(contains(region), "Region must completely contain supplied interior Region");

    LocationList newInterior = region.border;
    // ensure no overlap with existing interiors
    Area newArea = areaFromBorder(newInterior);
    if (interiors != null) {
      for (LocationList interior : interiors) {
        Area existing = areaFromBorder(interior);
        existing.intersect(newArea);
        checkArgument(existing.isEmpty(),
            "Supplied interior Region overlaps existing interiors");
      }
    } else {
      interiors = new ArrayList<LocationList>();
    }

    interiors.add(newInterior);
    area.subtract(region.area);
  }

  /**
   * Returns an unmodifiable {@link List} view of the internal
   * {@code LocationList}s (also unmodifiable) of points that decribe the
   * interiors of this {@code Region}, if such exist. If no interior is defined,
   * the method returns {@code null}.
   *
   * @return a {@code List} the interior {@code LocationList}s or {@code null}
   *         if no interiors are defined
   */
  public List<LocationList> interiors() {
    return (interiors != null) ? Collections.unmodifiableList(interiors) : null;
  }

  /**
   * Returns a reference to the internal, immutable {@code LocationList} of
   * points that decribe the border of this {@code Region}.
   *
   * @return the immutable border {@code LocationList}
   */
  public LocationList border() {
    return border;
  }

  /**
   * Returns a deep copy of the internal {@link Area} used to manage this
   * {@code Region}.
   *
   * @return a copy of the {@code Area} used by this {@code Region}
   */
  public Area area() {
    return (Area) area.clone();
  }

  /**
   * Returns a flat-earth estimate of the area of this region in km<sup>2</sup>.
   * Method uses the center of this {@code Region}'s bounding polygon as the
   * origin of an orthogonal coordinate system. This method is not appropriate
   * for use with very large {@code Region}s where the curvature of the earth is
   * more significant.
   * @return the area of this region in km<sup>2</sup>
   */
  public double extent() {
    // set origin as center of region bounds
    Rectangle2D rRect = area.getBounds2D();
    Location origin = Location.create(rRect.getCenterY(), rRect.getCenterX());
    // compute orthogonal coordinates in km
    List<Double> xs = Lists.newArrayList();
    List<Double> ys = Lists.newArrayList();
    for (Location loc : border) {
      LocationVector v = LocationVector.create(origin, loc);
      double az = v.azimuth();
      double d = v.horizontal();
      xs.add(Math.sin(az) * d);
      ys.add(Math.cos(az) * d);
    }
    // repeat first point
    xs.add(xs.get(0));
    ys.add(ys.get(0));
    return computeArea(Doubles.toArray(xs), Doubles.toArray(ys));
  }

  /*
   * Computes the area of a simple polygon; no data validation is performed
   * except ensuring that all coordinates are positive.
   */
  private static double computeArea(double[] xs, double[] ys) {
    Data.positivize(xs);
    Data.positivize(ys);
    double area = 0;
    for (int i = 0; i < xs.length - 1; i++) {
      area += xs[i] * ys[i + 1] - xs[i + 1] * ys[i];
    }
    return Math.abs(area) / 2;
  }

  /**
   * Compares the geometry of this {@code Region} to another and returns
   * {@code true} if they are the same, ignoring any differences in name. Use
   * {@code Region.equals(Object)} to include name comparison.
   *
   * @param r the {@code Regions} to compare
   * @return {@code true} if this {@code Region} has the same geometry as the
   *         supplied {@code Region}, {@code false} otherwise
   * @see Region#equals(Object)
   */
  public boolean equalsRegion(Region r) {
    // note that Area.equals() does not override Object.equals()
    return area.equals(r.area);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof Region)) {
      return false;
    }
    Region r = (Region) obj;
    if (!name().equals(r.name())) {
      return false;
    }
    return equalsRegion(r);
  }

  @Override
  public int hashCode() {
    return border.hashCode() ^ name.hashCode();
  }

  /**
   * Lazily create the bounds of this region.
   */
  public Bounds bounds() {
    Rectangle2D bounds = area.getBounds2D();
    return new Bounds(
        bounds.getMinY(),
        bounds.getMinX(),
        bounds.getMaxY(),
        bounds.getMaxX());
  }

  /**
   * Returns the minimum horizonatal distance (in km) between the border of this
   * {@code Region} and the {@code Location} specified. If the given
   * {@code Location} is inside the {@code Region}, the method returns 0. The
   * distance algorithm used only works well at short distances (e.g. ≤250 km).
   *
   * @param loc the Location to compute a distance to
   * @return the minimum distance between this {@code Region} and a point
   * @throws NullPointerException if supplied location is {@code null}
   * @see Locations#minDistanceToLine(Location, LocationList)
   * @see Locations#distanceToSegmentFast(Location, Location, Location)
   */
  public double distanceToLocation(Location loc) {
    checkNotNull(loc, "Supplied location is null");
    if (contains(loc)) {
      return 0;
    }
    double min = Locations.minDistanceToLine(loc, border);
    // check the segment defined by the last and first points
    // take abs because value may be negative; i.e. value to left of line
    double temp = Math.abs(Locations.distanceToSegmentFast(border.get(border.size() - 1),
        border.get(0), loc));
    return Math.min(temp, min);
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public String toString() {
    Bounds b = bounds();
    String str = "Region\n" + "\tMinLat: " + b.min().lat() + "\n" + "\tMinLon: " +
        b.min().lon() + "\n" + "\tMaxLat: " + b.max().lat() + "\n" + "\tMaxLon: " +
        b.max().lon();
    return str;
  }

  /*
   * Region intersection.
   */
  static Region intersect(String name, Region r1, Region r2) {
    validate(r1);
    validate(r2);
    Area newArea = (Area) r1.area.clone();
    newArea.intersect(r2.area);
    if (newArea.isEmpty()) {
      return null;
    }
    if (!Strings.isNullOrEmpty(name)) {
      name = "Intersection of " + r1.name() + " and " + r2.name();
    }
    Region rIntersect = new Region(name);
    rIntersect.area = newArea;
    rIntersect.border = borderFromArea(newArea, true);
    return rIntersect;
  }

  /*
   * Region union.
   */
  static Region union(String name, Region r1, Region r2) {
    validate(r1);
    validate(r2);
    Area newArea = (Area) r1.area.clone();
    newArea.add(r2.area);
    if (!newArea.isSingular()) {
      return null;
    }
    if (!Strings.isNullOrEmpty(name)) {
      name = "Union of " + r1.name() + " and " + r2.name();
    }
    Region rUnion = new Region(name);
    rUnion.area = newArea;
    rUnion.border = borderFromArea(newArea, true);
    return rUnion;
  }

  /*
   * Initializes a region via copy.
   */
  void initCopy(Region region) {
    border = region.border;
    area = (Area) region.area.clone();
    // internal regions
    if (region.interiors != null) {
      interiors = Lists.newArrayList(region.interiors);
    }
  }

  /*
   * Initialize a region from a list of border locations. Internal
   * java.awt.geom.Area is generated from the border.
   */
  void initBordered(LocationList border, BorderType type) {
    checkNotNull(border, "Supplied border is null");
    checkArgument(border.size() >= 3, "Supplied border must have at least 3 vertices");
    if (type == null) {
      type = MERCATOR_LINEAR;
    }

    // first remove last point in list if it is the same as
    // the first point
    if (border.get(border.size() - 1).equals(border.get(0))) {
      border = LocationList.create(Iterables.limit(border, border.size() - 1));
      // border.locs.remove(border.size() - 1); TODO test/clean locList
      // refactor
    }

    if (type.equals(GREAT_CIRCLE)) {
      List<Location> gcBorder = Lists.newArrayList();
      // process each border pair [start end]; so that the entire
      // border is traversed, set the first 'start' Location as the
      // last point in the gcBorder
      Location start = border.get(border.size() - 1);
      for (int i = 0; i < border.size(); i++) {
        gcBorder.add(start);
        Location end = border.get(i);
        double distance = Locations.horzDistance(start, end);
        // subdivide as necessary
        while (distance > GC_SEGMENT) {
          // find new Location, GC_SEGMENT km away from start
          double azRad = Locations.azimuthRad(start, end);
          Location segLoc = Locations.location(start, azRad, GC_SEGMENT);
          gcBorder.add(segLoc);
          start = segLoc;
          distance = Locations.horzDistance(start, end);
        }
        start = end;
      }
      this.border = LocationList.create(gcBorder);
    } else {
      this.border = border;
    }
    area = areaFromBorder(border);
  }

  /*
   * Initialize a rectangular region from two opposing corners expanding north
   * and east border slightly to satisfy constains operations
   */
  void initRectangular(Location loc1, Location loc2) {

    checkNotNull(loc1, "Supplied location (1) is null");
    checkNotNull(loc1, "Supplied location (2) is null");

    double lat1 = loc1.lat();
    double lat2 = loc2.lat();
    double lon1 = loc1.lon();
    double lon2 = loc2.lon();

    checkArgument(lat1 != lat2, "Input lats cannot be the same");
    checkArgument(lon1 != lon2, "Input lons cannot be the same");

    double minLat = Math.min(lat1, lat2);
    double minLon = Math.min(lon1, lon2);
    double maxLat = Math.max(lat1, lat2);
    double maxLon = Math.max(lon1, lon2);
    double offset = Locations.TOLERANCE;

    // ternaries prevent exceedance of max lat-lon values
    maxLat += (maxLat <= 90.0 - offset) ? offset : 0.0;
    maxLon += (maxLon <= 180.0 - offset) ? offset : 0.0;
    minLat -= (minLat >= -90.0 + offset) ? offset : 0.0;
    minLon -= (minLon >= -180.0 + offset) ? offset : 0.0;

    LocationList locs = LocationList.create(Location.create(minLat, minLon),
        Location.create(minLat, maxLon), Location.create(maxLat, maxLon),
        Location.create(maxLat, minLon));

    initBordered(locs, MERCATOR_LINEAR);
  }

  /*
   * Initialize a circular region by creating an circular border of shorter
   * straight line segments. Internal java.awt.geom.Area is generated from the
   * border.
   */
  void initCircular(Location center, double radius) {
    checkNotNull(center, "Supplied center Location is null");
    checkArgument((radius > 0 && radius <= 1000), "Radius [%s] is out of [0 1000] km range",
        radius);
    border = locationCircle(center, radius);
    area = areaFromBorder(border);
  }

  /*
   * Initialize a buffered region by creating box areas of 2x buffer width
   * around each line segment and circle areas around each vertex and union all
   * of them. The border is then be derived from the Area.
   */
  void initBuffered(LocationList line, double buffer) {
    checkNotNull(line, "Supplied LocationList is null");
    checkArgument((buffer > 0 && buffer <= 500), "Buffer [%s] is out of [0 500] km range",
        buffer);

    // init an Area with first point
    Area area = areaFromBorder(locationCircle(line.first(), buffer));
    // for each subsequent segment, create a box
    // for each subsequent point, create a circle
    Location prevLoc = line.first();
    for (Location loc : Iterables.skip(line, 1)) {
      area.add(areaFromBorder(locationBox(prevLoc, loc, buffer)));
      area.add(areaFromBorder(locationCircle(loc, buffer)));
      prevLoc = loc;
    }
    this.area = area;
    this.border = borderFromArea(area, true);
  }

  /*
   * Creates a java.awt.geom.Area from a LocationList border. This method throw
   * exceptions if the generated Area is empty or not singular
   */
  private static Area areaFromBorder(LocationList border) {
    Area area = new Area(Locations.toPath(border));
    // final checks on area generated, this is redundant for some
    // constructors that perform other checks on inputs
    checkArgument(!area.isEmpty(), "Internally computed Area is empty");
    checkArgument(area.isSingular(), "Internally computed Area is not a single closed path");

    return area;
  }

  /*
   * Creates a LocationList border from a java.awt.geom.Area. The clean flag is
   * used to post-process list to remove repeated identical locations, which are
   * common after intersect and union operations.
   */
  private static LocationList borderFromArea(Area area, boolean clean) {
    PathIterator pi = area.getPathIterator(null);
    List<Location> locs = Lists.newArrayList();
    // placeholder vertex for path iteration
    double[] vertex = new double[6];
    while (!pi.isDone()) {
      int type = pi.currentSegment(vertex);
      double lon = vertex[0];
      double lat = vertex[1];
      // skip the final closing segment which just repeats
      // the previous vertex but indicates SEG_CLOSE
      if (type != PathIterator.SEG_CLOSE) {
        locs.add(Location.create(lat, lon));
      }
      pi.next();
    }

    if (clean) {
      List<Location> cleanLocs = Lists.newArrayList();
      Location prev = locs.get(locs.size() - 1);
      for (Location loc : locs) {
        if (loc.equals(prev)) {
          continue;
        }
        cleanLocs.add(loc);
        prev = loc;
      }
      locs = cleanLocs;
    }
    return LocationList.create(locs);
  }

  /*
   * Utility method returns a LocationList that approximates the circle
   * represented by the center location and radius provided.
   */
  private static LocationList locationCircle(Location center, double radius) {
    List<Location> locs = Lists.newArrayList();
    for (double angle = 0; angle < 360; angle += WEDGE_WIDTH) {
      locs.add(Locations.location(center, angle * Maths.TO_RADIANS, radius));
    }
    return LocationList.create(locs);
  }

  /*
   * Utility method returns a LocationList representing a box that is as long as
   * the line between p1 and p2 and extends on either side of that line some
   * 'distance'.
   */
  private static LocationList locationBox(Location p1, Location p2, double distance) {

    // get the azimuth and back-azimuth between the points
    double az12 = Locations.azimuthRad(p1, p2);
    double az21 = Locations.azimuthRad(p2, p1); // back azimuth

    // add the four corners
    LocationList ll = LocationList.create(
        // corner 1 is azimuth p1 to p2 - 90 from p1
        Locations.location(p1, az12 - Maths.PI_BY_2, distance),
        // corner 2 is azimuth p1 to p2 + 90 from p1
        Locations.location(p1, az12 + Maths.PI_BY_2, distance),
        // corner 3 is azimuth p2 to p1 - 90 from p2
        Locations.location(p2, az21 - Maths.PI_BY_2, distance),
        // corner 4 is azimuth p2 to p1 + 90 from p2
        Locations.location(p2, az21 + Maths.PI_BY_2, distance));
    return ll;
  }

  /* Validator for geometry operations */
  private static void validate(Region r) {
    checkNotNull(r, "Supplied Region is null");
    checkArgument(r.area.isSingular(), "Region must be singular");
  }

}
