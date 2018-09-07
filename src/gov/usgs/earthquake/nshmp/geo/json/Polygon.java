package gov.usgs.earthquake.nshmp.geo.json;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ArrayList;
import java.util.List;

import com.google.common.collect.ImmutableList;

import gov.usgs.earthquake.nshmp.geo.BorderType;
import gov.usgs.earthquake.nshmp.geo.Location;
import gov.usgs.earthquake.nshmp.geo.LocationList;
import gov.usgs.earthquake.nshmp.geo.Region;
import gov.usgs.earthquake.nshmp.geo.Regions;;

/**
 * Create a GeoJson {@code Polygon} {@link Geometry} with a border and
 *    interiors.
 * <br>
 * 
 * See {@link #Polygon(LocationList, LocationList...)} for an example.
 * <br><br>
 * 
 * The {@code LocationList} should have the same values for the first and last
 *    locations, if not it is added.
 * <br><br> 
 * 
 * NOTE: The border is assumed to be defined in
 *    counterclockwise direction while any interior is assumed to 
 *    be defiend clockwise as per GeoJson specification.
 * 
 * @author Brandon Clayton
 */
public class Polygon implements Geometry  {
  /** The {@link GeoJsonType} of GeoJson {@code Geometry}: Polygon */
  private final String type;
  /** The coordinates of the polygon */
  private ImmutableList<double[][]> coordinates;
  /** The border of the {@code Polygon} */
  transient private LocationList border;
  /** The interiors of the {@code Polygon} */
  transient private LocationList[] interiors;

  /**
   * Create a {@code Polygon} GeoJson {@code Geometry} with a {@link LocationList}
   *    for a border and a {@code LocationList...} for the interiors.
   * <br><br>
   * 
   * Example:
   * <pre>
   *  // Create a polygon border 
   *  LocationList border = LocationList.builder()
   *      .add(0.0, 100.0)
   *      .add(0.0, 101.0)
   *      .add(1.0, 101.0)
   *      .add(1.0, 100.0)
   *      .add(0.0, 100.0)
   *      .build();
   *      
   *  // Create a polygon interior
   *  LocationList interior = LocationList.builder()
   *      .add(0.8, 100.8)
   *      .add(0.2, 100.8)
   *      .add(0.2, 100.2)
   *      .add(0.8, 100.2)
   *      .add(0.8, 100.8)
   *      .build();    
   *      
   *  // Create a polygon    
   *  Polygon polygon = new Polygon(border, interior);
   * </pre>
   * 
   * @param border The {@link LocationList} for the {@code Polygon} border.
   * @param interiors The {@code LocationList...} for the {@code Polygon} interiors.
   */
  public Polygon(LocationList border, LocationList... interiors) {
    this.type = GeoJsonType.POLYGON.toUpperCamelCase();
    
    checkNotNull(border, "Border cannot be null");
    for (LocationList locs : interiors) {
      checkNotNull(locs, "Interiors cannot be null");
    }
    
    this.border = checkPolygonCoordinates(border);
    this.interiors = checkPolygonCoordinates(interiors);
    checkInteriors(this.border, this.interiors);
    
    this.coordinates = new ImmutableList.Builder<double[][]>()
        .add(JsonUtil.toCoordinates(this.border))
        .addAll(JsonUtil.toCoordinates(this.interiors))
        .build();
  }
  
  /**
   * Return the {@link LocationList} representing the border of the {@code Polygon}.
   * 
   * @return The border of the {@code Polygon}.
   */
  public LocationList getBorder() {
    return border; 
  }
 
  @Override
  /**
   * Return the coordinates as a {@code ImmutableList<double[][]>} from 
   *    a GeoJson {@code Polygon}.
   * @return The coordinates.
   */
  public ImmutableList<double[][]> getCoordinates() {
    return coordinates;
  }
  
  /**
   * Return a {@code ImmutableList<LocationList>} representing the
   *    interiors of the {@code Polygon}.
   * <br>
   *
   * If no interiors are defiend an empty {@code ImmutableList} is returned.
   * 
   * @return The interiors of the {@code Polygon}. 
   */
  public ImmutableList<LocationList> getInteriors() {
    return ImmutableList.copyOf(interiors);
  }
 
  @Override
  /**
   * Return the {@link GeoJsonType} representing the {@code Polygon}.
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
    return JsonUtil.cleanPoly(JsonUtil.GSON.toJson(this, Polygon.class));
  }
 
  /**
   * Create and return a {@link Region} representing the {@code Polygon}.
   * <br>
   * 
   * If any interiors are defined in the {@code Polygon}, interiors
   *    are added to the {@code Region} with {@link Region#addInterior(Region)}.
   * 
   * @param name The {@code Region} name; can be empty string.
   * @return The {@code Region} representing the {@code Polygon}.
   */
  public Region toRegion(String name) {
    LocationList border = getBorder();
    ImmutableList<LocationList> interiors = getInteriors();
    
    Region region = Regions.create(name, border, BorderType.MERCATOR_LINEAR);
    
    for (LocationList interior : interiors) {
      Region interiorRegion = Regions.create(name, interior, BorderType.MERCATOR_LINEAR);
      region.addInterior(interiorRegion);
    }
    
    return region;
  }

  /**
   * Using {@link Region#addInterior(Region)} check the interiors 
   *    of the {@code Polygon} does not overlap each other.
   *    
   * @param border The {@code Polygon} border.
   * @param interiors The {@code Polygon} interiors.
   */
  private static void checkInteriors(LocationList border, LocationList... interiors) {
    Region region = Regions.create("", border, BorderType.MERCATOR_LINEAR);
    
    for (LocationList interior : interiors) {
      Region inteiorRegion = Regions.create("", interior, BorderType.MERCATOR_LINEAR);
      region.addInterior(inteiorRegion);
    }
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
    
    LocationList.Builder builder = LocationList.builder().addAll(locs);
    if (!firstLoc.equals(lastLoc)) builder.add(firstLoc);
    LocationList updatedLocs = builder.build();  
    
    checkArgument(updatedLocs.size() > 3, "Polygon must have 3 unique positions");
    
    return updatedLocs;
  }
 
  /**
   * Check weather the first and last {@code Location}s are the same in the
   *    {@code LocationList...}, if not add the first {@code Location} to the 
   *    last index;
   *    
   * @param listLocs The {@code LocationList...}
   * @return A {@code LocationList[]} 
   */
  private static LocationList[] checkPolygonCoordinates(LocationList... listLocs) {
    List<LocationList> newLocsList = new ArrayList<>();
    
    for (LocationList locs : listLocs) {
      newLocsList.add(checkPolygonCoordinates(locs));
    }
    
    return newLocsList.toArray(new LocationList[0]);
  }

}
