package gov.usgs.earthquake.nshmp.json;

import gov.usgs.earthquake.nshmp.geo.Location;

/**
 * Create a GeoJson {@code Point} {@link Geometry}.
 * <br>
 * 
 * See {@link #Point(Location)} and {@link #Point(double, double)} for examples.
 * 
 * @author Brandon Clayton
 */
public class Point implements Geometry {
  /** The {@link GeoJsonType} of GeoJson {@code Geometry}: Point */
  public final String type;
  /** The coordinates of the point */
  public final double[] coordinates;

  /**
   * Create a Point GeoJson {@code Geometry} with a {@link Location}. 
   * <br><br>
   * 
   * Example:
   * <pre>
   * {@code
   *  Location loc = Location.create(40, -120);
   *  Point point = new Point(loc);
   * }
   * </pre>
   * 
   * @param loc The {@code Location} ({@link Location}).
   */
  public Point(Location loc) {
    this.type = GeoJsonType.POINT.toUpperCamelCase();
    this.coordinates = Util.toCoordinates(loc);
  }

  /**
   * Create a Point GeoJson {@code Geometry} with a latitude and longitude.
   * <br><br>
   * 
   * Example:
   * <pre>
   * {@code
   *   Point point = new Point(40, -120);
   * }
   * </pre>
   * 
   * @param latitude The latitude in degrees
   * @param longitude The longitude in degrees
   */
  public Point(double latitude, double longitude) {
    Location loc = Location.create(latitude, longitude);
    this.type = GeoJsonType.POINT.toUpperCamelCase();
    this.coordinates = Util.toCoordinates(loc);
  }
 
  /** 
   * Return the coordinates as a {@code double[]} ([longitude, latitude])
   *    for a GeoJson {@code Point}.
   * @return The longitude and latitude, [longitude, latitude].
   */
  public double[] getCoordinates() {
    return this.coordinates;
  }
  
  /**
   * Return the {@code String} representing the {@link GeoJsonType} {@code Point}.
   * @return The {@code String} of the {@code GeoJsonType}.
   */
  public String getType() {
    return this.type;
  }
 
  /**
   * Return a {@code String} in JSON format.
   */
  public String toJsonString() {
    return Util.GSON.toJson(this);
  }
  
  /**
   * Return the coordinates as a {@link Location}.
   * @return The {@code Location}.
   */
  public Location toLocation() {
    return Location.create(this.coordinates[1], this.coordinates[0]);
  }
  
}
