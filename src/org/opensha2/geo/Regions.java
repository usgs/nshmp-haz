package org.opensha2.geo;

import static com.google.common.base.Preconditions.checkNotNull;

import static org.opensha2.geo.BorderType.MERCATOR_LINEAR;

import java.awt.Shape;

/**
 * Static utility methods pertaining to geographic regions.
 * 
 * <p>Note that intersection and union operations will discard any grid
 * information if {@code GriddedRegion}s are supplied as arguments.</p>
 * 
 * <p>To build complex gridded regions (e.g. circular or buffered, or via
 * intersections and unions) create a Region first and use
 * {@link Regions#toGridded(Region, double, double, Location)}.</p>
 * 
 * @author Peter Powers
 */
public class Regions {

  /**
   * Creates a {@code Region} from a list of border locations. The border type
   * specifies whether lat-lon values are treated as points in an orthogonal
   * coordinate system or as connecting great circles. The border
   * {@code LocationList} does not need to repeat the first {@code Location} at
   * the end of the list.
   * 
   * @param name of the {@code Region}; may be {@code null}
   * @param border {@code Locations}
   * @param type the {@link BorderType} to use when initializing; a {@code null}
   *        value defaults to {@code BorderType.MERCATOR_LINEAR}
   * @return a new {@code Region}
   * @throws IllegalArgumentException if the {@code border} does not have at
   *         least 3 points, defines a {@code Region} that is empty, or consists
   *         of more than a single closed path.
   * @throws NullPointerException if the {@code border} is {@code null}
   */
  public static Region create(String name, LocationList border, BorderType type) {
    Region r = new Region(name);
    r.initBordered(border, type);
    return r;
  }

  /**
   * <img style="padding: 30px 40px; float: right;" src=
   * "{@docRoot}/resources/gridded_regions_border.jpg"/>Creates a
   * {@code GriddedRegion} from a list of border locations.
   * 
   * <p>The border type specifies whether lat-lon values are treated as points
   * in an orthogonal coordinate system or as connecting great circles. The
   * border {@code LocationList} does not need to repeat the first
   * {@code Location} at the end of the list. If the supplied
   * {@code anchor Location} is {@code null}, it is automatically set to the
   * Location defined by the minimum latitude and longitude of the region's
   * border.
   * 
   * @param name of the {@code GriddedRegion}; may be {@code null}
   * @param border {@code Locations}
   * @param type the {@link BorderType} to use when initializing; a {@code null}
   *        value defaults to {@code BorderType.MERCATOR_LINEAR}
   * @param latSpacing of grid nodes
   * @param lonSpacing of grid nodes
   * @param anchor {@code Location} for grid; may be {@code null}
   * @return a new {@code GriddedRegion}
   * @throws IllegalArgumentException if the {@code border} does not have at
   *         least 3 points, defines a {@code Region} that is empty, or consists
   *         of more than a single closed path; or {@code spacing} is outside
   *         the range 0° < {@code spacing} ≤ 5°
   * @throws NullPointerException if the {@code border} is {@code null}
   */
  public static GriddedRegion createGridded(String name, LocationList border, BorderType type,
      double latSpacing, double lonSpacing, Location anchor) {
    GriddedRegion gr = new GriddedRegion(name);
    gr.initBordered(border, type);
    gr.initGrid(latSpacing, lonSpacing, anchor);
    return gr;
  }

  /**
   * Creates a {@code Region} from a pair of {@code Location }s. When viewed in
   * a Mercator projection, the {@code Region} will be a rectangle. If either
   * both latitude or both longitude values in the {@code Location}s are the
   * same, an exception is thrown.
   * 
   * <p><b>Note:</b> Internally, the size of the region is expanded by a very
   * small value (~1m) to ensure that calls to {@link Region#contains(Location)}
   * for any {@code Location} on the north or east border of the region will
   * return {@code true} and that any double precision rounding issues do not
   * clip the south and west borders (e.g. 45.0 may be interpreted as
   * 44.9999...). See also the rules governing insidedness in the {@link Shape}
   * interface.</p>
   * 
   * @param name of the {@code Region}; may be {@code null}
   * @param loc1 the first {@code Location}
   * @param loc2 the second {@code Location}
   * @return a new rectangular (broadly-defined) {@code Region}
   * @throws IllegalArgumentException if the latitude or longitude values in the
   *         {@code Location}s provided are the same
   * @throws NullPointerException if either {@code Location} argument is
   *         {@code null}
   */
  public static Region createRectangular(String name, Location loc1, Location loc2) {
    Region r = new Region(name);
    r.initRectangular(loc1, loc2);
    return r;
  }

  /**
   * Creates a {@code GriddedRegion} from a pair of {@code Location }s. When
   * viewed in a Mercator projection, the {@code Region} will be a rectangle. If
   * either both latitude or both longitude values in the {@code Location}s are
   * the same, an exception is thrown. If the supplied {@code anchor Location}
   * is {@code null}, it is automatically set to the Location defined by the
   * minimum latitude and longitude of the region's border.
   * 
   * <p><b>Note:</b> Internally, the size of the region is expanded by a very
   * small value (~1m) to ensure that calls to {@link Region#contains(Location)}
   * for any {@code Location} on the north or east border of the region will
   * return {@code true} and that any double precision rounding issues do not
   * clip the south and west borders (e.g. 45.0 may be interpreted as
   * 44.9999...). See also the rules governing insidedness in the {@link Shape}
   * interface.</p>
   * 
   * @param name of the {@code GriddedRegion}; may be {@code null}
   * @param loc1 the first {@code Location}
   * @param loc2 the second {@code Location}
   * @param latSpacing of grid nodes
   * @param lonSpacing of grid nodes
   * @param anchor {@code Location} for grid; may be {@code null}
   * @return a new rectangular (broadly-defined) {@code GriddedRegion}
   * @throws IllegalArgumentException if the latitude or longitude values in the
   *         {@code Location}s provided are the same or {@code spacing} is
   *         outside the range 0° < {@code spacing} ≤ 5°
   * @throws NullPointerException if either {@code Location} argument is
   *         {@code null}
   */
  public static GriddedRegion createRectangularGridded(String name, Location loc1, Location loc2,
      double latSpacing, double lonSpacing, Location anchor) {
    GriddedRegion gr = new GriddedRegion(name);
    gr.initRectangular(loc1, loc2);
    gr.initGrid(latSpacing, lonSpacing, anchor);
    return gr;
  }

  /**
   * <img style="padding: 30px 40px; float: right;" src=
   * "{@docRoot}/resources/gridded_regions_circle.jpg"/>Creates a circular
   * {@code Region}. Internally, the centerpoint and radius are used to create a
   * circular region composed of straight line segments that span 10° wedges.
   * 
   * <p>Passing the resultant region
   * {@link #toGridded(Region, double, double, Location)} yields a
   * {@link GriddedRegion} like that in the adjacent figure. The heavy black
   * line marks the border of the {@code Region}. The light gray dots mark the
   * {@code Location}s of nodes outside the region, and black dots those inside
   * the region. The dashed grey line marks the border, inside which, a
   * {@code Location} will be associated with a grid node. See
   * {@link GriddedRegion#indexForLocation(Location)} for more details on rules
   * governing whether a grid node is inside a region and whether a
   * {@code Location} will be associated with a grid node.
   * 
   * @param name of the {@code Region}; may be {@code null}
   * @param center of the circle
   * @param radius of the circle
   * @return a new circular {@code Region}
   * @throws IllegalArgumentException if {@code radius} is outside the range 0
   *         km < {@code radius} ≤ 1000 km
   * @throws NullPointerException if {@code center} is {@code null}
   */
  public static Region createCircular(String name, Location center, double radius) {
    Region r = new Region(name);
    r.initCircular(center, radius);
    return r;
  }

  /**
   * <img style="padding: 30px 40px; float: right;" src=
   * "{@docRoot}/resources/gridded_regions_buffer.jpg"/>Creates a {@code Region}
   * as a buffered area around a line.
   * 
   * <p>Passing the resultant region
   * {@link #toGridded(Region, double, double, Location)} yields a
   * {@link GriddedRegion} like that in the adjacent figure. The light gray dots
   * mark the {@code Location}s of nodes outside the region, and black dots
   * those inside the region. The dashed grey line marks the border, inside
   * which, a {@code Location} will be associated with a grid node. See
   * {@link GriddedRegion#indexForLocation(Location)} for more details on rules
   * governing whether a grid node is inside a region and whether a
   * {@code Location} will be associated with a grid node.
   * 
   * @param name of the {@code Region}; may be {@code null}
   * @param line at center of buffered {@code Region}
   * @param buffer distance from line
   * @return a new buffered {@code Region} around a line
   * @throws NullPointerException if {@code line} is {@code null}
   * @throws IllegalArgumentException if {@code buffer} is outside the range 0
   *         km < {@code buffer} ≤ 500 km
   */
  public static Region createBuffered(String name, LocationList line, double buffer) {
    Region r = new Region(name);
    r.initBuffered(line, buffer);
    return r;
  }

  /**
   * Creates an exact copy of a {@code Region}.
   * 
   * @return a copy of the supplied {@code Region}
   * @param region to copy
   * @throws NullPointerException if the supplied {@code Region} is {@code null}
   */
  public static Region copyOf(Region region) {
    checkNotNull(region, "Supplied Region is null");
    Region r = new Region(region.name());
    r.initCopy(region);
    return r;
  }

  /**
   * Creates a copy of a {@code GriddedRegion}.
   * 
   * @return a copy of the supplied {@code GriddedRegion}
   * @param region to copy
   * @throws NullPointerException if the supplied {@code GriddedRegion} is
   *         {@code null}
   */
  public static GriddedRegion copyOf(GriddedRegion region) {
    checkNotNull(region, "Supplied Region is null");
    GriddedRegion gr = new GriddedRegion(region.name());
    gr.initCopy(region);
    return gr;
  }

  /**
   * Creates a {@code GriddedRegion} with the same shape as the supplied
   * {@code Region}. If the supplied {@code anchor Location} is {@code null}, it
   * is automatically set to the Location defined by the minimum latitude and
   * longitude of the region's border.
   * 
   * @param region to use as basis for new {@code GriddedRegion}
   * @param latSpacing of grid nodes
   * @param lonSpacing of grid nodes
   * @param anchor {@code Location} for grid; may be {@code null}
   * @return a new {@code GriddedRegion}
   * @throws IllegalArgumentException if {@code spacing} is outside the range 0°
   *         < {@code spacing} ≤ 5°
   * @throws NullPointerException if the supplied {@code Region} is {@code null}
   */
  public static GriddedRegion toGridded(Region region, double latSpacing, double lonSpacing,
      Location anchor) {
    checkNotNull(region, "Supplied Region is null");
    GriddedRegion gr = new GriddedRegion(region.name());
    // TODO if supplied region is in fact GR, check that still going to
    // Region.initCopy()
    gr.initCopy(region);
    gr.initGrid(latSpacing, lonSpacing, anchor);
    return gr;
  }

  /**
   * Returns the intersection of two {@code Region}s. If the {@code Region}s do
   * not overlap, the method returns {@code null}.
   * 
   * @param name of the {@code Region}; may be {@code null}
   * @param r1 the first {@code Region}
   * @param r2 the second {@code Region}
   * @return a new {@code Region} defined by the intersection of {@code r1} and
   *         {@code r2} or {@code null} if they do not overlap
   * @throws IllegalArgumentException if either supplied {@code Region} is not a
   *         single closed {@code Region}
   * @throws NullPointerException if either supplied {@code Region} is
   *         {@code null}
   */
  public static Region intersectionOf(String name, Region r1, Region r2) {
    return Region.intersect(name, r1, r2);
  }

  /**
   * Returns the union of two {@code Region}s. If the {@code Region}s do not
   * overlap, the method returns {@code null}.
   * 
   * @param name of the {@code Region}; may be {@code null}
   * @param r1 the first {@code Region}
   * @param r2 the second {@code Region}
   * @return a new {@code Region} defined by the union of {@code r1} and
   *         {@code r2} or {@code null} if they do not overlap
   * @throws IllegalArgumentException if either supplied {@code Region} is not a
   *         single closed {@code Region}
   * @throws NullPointerException if either supplied {@code Region} is
   *         {@code null}
   */
  public static Region unionOf(String name, Region r1, Region r2) {
    return Region.union(name, r1, r2);
  }

  /**
   * Convenience method to return a {@code Region} spanning the entire globe.
   * @return a {@code Region} extending from -180° to +180° longitude and -90°
   *         to +90° latitude
   */
  public static Region global() {
    LocationList locs = LocationList.create(
        Location.create(-90, -180),
        Location.create(-90, 180),
        Location.create(90, 180),
        Location.create(90, -180));
    return create("Global Region", locs, MERCATOR_LINEAR);
  }

}
