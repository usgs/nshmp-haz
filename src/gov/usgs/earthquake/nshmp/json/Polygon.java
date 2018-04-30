package gov.usgs.earthquake.nshmp.json;

import com.google.common.collect.ImmutableList;

import gov.usgs.earthquake.nshmp.geo.Location;
import gov.usgs.earthquake.nshmp.geo.LocationList;

/**
 * Create a GeoJson {@code Polygon} {@link Geometry} with a {@link LocationList}.
 * <br>
 * 
 * See {@link #Polygon(LocationList)} for an example.
 * <br><br>
 * 
 * The {@code LocationList} should have the same values for the first and last
 *    locations, if not it is added.
 * <br><br> 
 * 
 * NOTE: The {@code LocationList} is assumed to be defined in
 *    counterclockwise direction as per GeoJson specification.
 * 
 * @author Brandon Clayton
 */
public class Polygon implements Geometry  {
  /** The {@link GeoJsonType} of GeoJson {@code Geometry}: Polygon */
  public final String type;
  /** The coordinates of the polygon */
  public ImmutableList<double[][]> coordinates;

  /**
   * Create a {@code Polygon} GeoJson {@code Geometry} with a {@link LocationList}.
   * <br><br>
   * 
   * Example:
   * <pre>
   * {@code
   *   LocationList locs = LocationList.builder()
   *      .add(40, -120)
   *      .add(38, -120)
   *      .add(38, -122)
   *      .add(40, -120)
   *      .build();
   *   Polygon polygon = new Polygon(locs);
   * }
   * </pre>
   * 
   * @param locs The {@link LocationList} for the polygon.
   */
  public Polygon(LocationList locs) {
    locs = checkPolygonCoordinates(locs);
    this.type = GeoJsonType.POLYGON.toUpperCamelCase();
    this.coordinates = ImmutableList.of(Util.toCoordinates(locs));
  }
  
  /**
   * Return the coordinates as a {@code ImmutableList<double[][]>} from 
   *    a GeoJson {@code Polygon}.
   * @return The coordinates.
   */
  public ImmutableList<double[][]> getCoordinates() {
    return this.coordinates;
  }
  
  /**
   * Return the {@code String} representing the {@link GeoJsonType} {@code Polygon}.
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
   * Return the coordinates as a {@link LocationList}.
   * @return The {@code LocationList}.
   */
  public LocationList toLocationList() {
    double[][] coords = this.coordinates.get(0);
    
    LocationList.Builder builder = LocationList.builder();
    
    for (double[] xy : coords) {
      Location loc = Location.create(xy[1], xy[0]);
      builder.add(loc);
    }
    
    return builder.build();
  }
  
  /**
   * Check whether the first and last {@code Location}s are the same in the
   *    {@code LocationList}, if not add the first {@code Location} to the last
   *    spot.
   * 
   * @param locs The {@code LocationList} to check.
   * @return The updated {@code LocationList}.
   */
  private static LocationList checkPolygonCoordinates(LocationList locs) {
    Location firstLoc = locs.first();
    Location lastLoc = locs.last();

    if (!firstLoc.equals(lastLoc)) {
      LocationList updatedLocs = LocationList.builder()
          .addAll(locs)
          .add(firstLoc)
          .build();

      return updatedLocs;
    }

    return locs;
  }

}
