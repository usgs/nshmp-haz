package gov.usgs.earthquake.nshmp.json;

import static com.google.common.base.Preconditions.checkNotNull;

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
  private final String type;
  /** The coordinates of the point */
  private final double[] coordinates;

  /**
   * Create a Point GeoJson {@code Geometry} with a {@link Location}. 
   * <br><br>
   * 
   * Example:
   * <pre>
   *  Location loc = Location.create(40, -120);
   *  Point point = new Point(loc);
   * </pre>
   * 
   * @param loc The {@code Location} ({@link Location}).
   */
  public Point(Location loc) {
    checkNotNull(loc, "Location cannot be null");
    
    this.type = GeoJsonType.POINT.toUpperCamelCase();
    this.coordinates = JsonUtil.toCoordinates(loc);
  }

  /**
   * Create a Point GeoJson {@code Geometry} with a latitude and longitude.
   * <br><br>
   * 
   * Example:
   * <pre>
   *   Point point = new Point(40, -120);
   * </pre>
   * 
   * @param latitude The latitude in degrees
   * @param longitude The longitude in degrees
   */
  public Point(double latitude, double longitude) {
    Location loc = Location.create(latitude, longitude);
    this.type = GeoJsonType.POINT.toUpperCamelCase();
    this.coordinates = JsonUtil.toCoordinates(loc);
  }

  @Override
  /** 
   * Return the coordinates as a {@code double[]} ([longitude, latitude])
   *    for a GeoJson {@code Point}.
   * @return The longitude and latitude, [longitude, latitude].
   */
  public double[] getCoordinates() {
    return coordinates;
  }
  
  /**
   * Return the coordinates as a {@link Location}.
   * @return The {@code Location}.
   */
  public Location getLocation() {
    return Location.create(coordinates[1], coordinates[0]);
  }
 
  @Override
  /**
   * Return the {@link GeoJsonType} representing the {@code Point}.
   * @return The {@code GeoJsonType}.
   */
  public GeoJsonType getType() {
    return GeoJsonType.getEnum(type);
  }
 
  @Override
  /**
   * Return a {@code String} in JSON format.
   */
  public String toJsonString() {
    return JsonUtil.cleanPoints(JsonUtil.GSON.toJson(this, Point.class));
  }
  
}
