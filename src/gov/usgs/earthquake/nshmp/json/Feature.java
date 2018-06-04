package gov.usgs.earthquake.nshmp.json;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import gov.usgs.earthquake.nshmp.geo.Location;
import gov.usgs.earthquake.nshmp.geo.LocationList;

/**
 * Create a GeoJson {@code Feature} with a {@link Geometry} and
 *    {@link Properties}. See {@link #Feature(Properties, Geometry)} for an
 *    example. 
 * <br><br>
 * 
 * Static factory classes are provided to create a {@link Point} and a
 *    {@link Polygon} (see each for examples): 
 *  <ul> 
 *    <li> {@link Feature#createPoint(Properties, Location)} </li> 
 *    <li> {@link Feature#createPoint(Properties, double, double)} </li> 
 *    <li> {@link Feature#createPolygon(Properties, LocationList, LocationList...)} </li> 
 *    <li> {@link Feature#createMultiPolygon(Properties, List)} </li>
 *    <li> {@link Feature#createMultiPolygon(Properties, MultiPolygon)} </li>
 *  </ul>
 * 
 * @author Brandon Clayton
 */
public class Feature implements GeoJson {
  /** The {@link GeoJsonType} of GeoJson object: Feature */
  private final String type;
  /** An {@code Optional} id field */
  private JsonElement id = null; 
  /** The {@link Geometry} */
  private Geometry geometry;
  /** The {@link Properties} */
  private Map<String, JsonElement> properties;

  /**
   * Create a new instance of a {@code Feature}. 
   * <br><br>
   * 
   * Example:
   * <pre>
   *   Properties properties = Properties.builder().title("Golden").build();
   *   Location loc = Location.create(39.75, -105);
   *   Point point = new Geometry.Point(loc);
   *   String id = "featureId";
   *    
   *   Feature feature = new Feature(properties, point, Optional.of(id));
   * </pre>
   * 
   * @param properties The {@link Properties} for the {@code Feature}.
   * @param geometry The {@link geometry} for the {@code Feature}.
   * @param id An {@code Optional} ({@code String} or {@code int})
   *    id for the {@code Feature}.
   */
  public Feature(Properties properties, Geometry geometry, Optional<?> id) {
    checkNotNull(geometry, "Geometry cannot be null");
    checkNotNull(properties, "Properties cannot be null");
    
    this.type = GeoJsonType.FEATURE.toUpperCamelCase();
    this.geometry = geometry;
    this.properties = properties.getProperties();
    
    if (id.isPresent()) this.id = Util.GSON.toJsonTree(id.get()); 
  }

  /**
   * Static factory method to create a {@link Feature} with a 
   *    {@link MultiPolygon} {@link Geometry}.
   * <br>
   * 
   * See {@link MultiPolygon.Builder} for an example on creating a 
   *    {@code MultiPolygon}. 
   *    
   * @param properties The {@link Properties}.
   * @param multiPolygon The {@code MultiPolygon}.
   * @param featureId An {@code Optional} ({@code String} or {@code int})
   *    id for the {@code Feature}.
   * @return The {@code Feature}.
   */
  public static Feature createMultiPolygon(
      Properties properties,
      MultiPolygon multiPolygon,
      Optional<?> featureId) {
    return new Feature(properties, multiPolygon, featureId);
  }
  
  /**
   * Static factory method to create a {@link Feature} with a 
   *    {@link MultiPolygon} {@link Geometry} given a 
   *    {@code List<Polygon>}.
   * <br>
   * 
   * See {@link MultiPolygon.Builder} for an example on creating a 
   *    {@code MultiPolygon}. 
   *    
   * @param properties The {@link Properties}.
   * @param polygons The {@link Polygon}s.
   * @param featureId An {@code Optional} ({@code String} or {@code int})
   *    id for the {@code Feature}.
   * @return The {@code Feature}.
   */
  public static Feature createMultiPolygon(
      Properties properties, 
      List<Polygon> polygons,
      Optional<?> featureId) {
    MultiPolygon.Builder builder = MultiPolygon.builder();
    
    for (Polygon polygon : polygons) {
      builder.addPolygon(polygon);
    }
    
    return new Feature(properties, builder.build(), featureId);
  }
  
  /**
   * Static factory method to create a {@code Feature} with a
   *    {@link Point} {@link Geometry} given a latitude and longitude in degrees. 
   * <br><br> 
   * 
   * Example:
   * <pre>
   *   Properties properties = Properties.builder()
   *      .title("Golden")
   *      .id("golden")
   *      .build();
   *      
   *   String featureId = "id";
   *    
   *   Feature feature = Feature.createPoint(
   *      properties, latitude, longitude, Optional.of(featureId));
   * </pre>
   * 
   * @param properties The {@code Properties} ({@link Properties}).
   * @param latitude The latitude for the point.
   * @param longitude The longitude for the point.
   * @param featureId An {@code Optional} ({@code String} or {@code int})
   *    id for the {@code Feature}.
   * @return A new GeoJson {@code Feature}.
   */
  public static Feature createPoint(
      Properties properties,
      double latitude,
      double longitude,
      Optional<?> featureId) {
    Point point = new Point(latitude, longitude);
    return new Feature(properties, point, featureId);
  }

  /**
   * Static factory method to create a {@code Feature} with a
   *    {@link Point} {@link Geometry} given a {@code Location}. 
   * <br><br>
   * 
   * Example:
   * <pre>
   *   Location loc = Location.create(39.75, -105);
   *   
   *   Properties properties = Properties.builder()
   *      .title("Golden")
   *      .id("golden")
   *      .build();
   *      
   *   String featureId = "id";
   *   
   *   Feature feature = Feature.createPoint(
   *      properties, loc, Optional.of(featureId));
   * </pre>
   * 
   * @param properties - The {@code Properties} ({@link Properties}).
   * @param loc - The {@code Location} ({@link Location}).
   * @param featureId An {@code Optional} ({@code String} or {@code int})
   *    id for the {@code Feature}.
   * @return A new GeoJson {@code Feature} ({@link Feature}).
   */
  public static Feature createPoint(
      Properties properties, 
      Location loc, 
      Optional<?> featureId) {
    Point point = new Point(loc);
    return new Feature(properties, point, featureId);
  }

  /**
   * Static factory method to create a {@code Feature} with a
   *    {@link Polygon} {@link Geometry} given a {@link LocationList}.
   * <br><br> 
   * 
   * Example:
   * <pre>
   *   LocationList border = LocationList.builder()
   *       .add(40, -120)
   *       .add(38, -120)
   *       .add(38, -122)
   *       .add(40, -120)
   *       .build();
   *       
   *   Properties properties = Properties.builder()
   *      .title("Golden")
   *      .id("golden")
   *      .build();
   *      
   *   String id = "id";
   *      
   *   Feature feature = Feature.createPolygon(
   *      properties, Optional.of(featureId), border);
   * </pre>
   * 
   * @param properties The {@code Properties} 
   * @param featureId An {@code Optional} ({@code String} or {@code int}) 
   *    id for the {@code Feature}.
   * @param border The border for the {@code Polygon}
   * @param interiors The interiors for the {@code Polygon}  
   * @return A new GeoJson {@code Feature} 
   */
  public static Feature createPolygon(
      Properties properties,
      Optional<?> featureId,
      LocationList border,
      LocationList... interiors) {
    Polygon polygon = new Polygon(border, interiors);
    return new Feature(properties, polygon, featureId);
  }

  /**
   * Return the {@link Geometry}.
   * <br>
   * 
   * It is best to get the specific {@code Geometry} by using:
   *    <ul>
   *      <li> {@link Geometry#asMultiPolygon()} </li>
   *      <li> {@link Geometry#asPoint()} </li>
   *      <li> {@link Geometry#asPolygon()} </li>
   *    </ul> 
   * 
   * Example:
   * <pre>
   *  Feature feature = Feature.createPoint(Properties, 40, -120);
   *  
   *  Point pointGeom = feature.getGeometry().asPoint();
   * </pre>
   * 
   * @return The {@code Geometry}.
   */
  public Geometry getGeometry() {
    return geometry;
  }
 
  /**
   * Return the {@code Optional} {@code Feature} id field as a {@code String}.
   * @return The {@code Feature} id.
   */
  public String getId() {
    return id != null ? id.getAsString() : null;
  }
  
  /**
   * Return the {@code Optional} {@code Feature} id field as an {@code int}.
   * @return The {@code Feature} id.
   */
  public int getNumericId() {
    return id != null ? id.getAsInt() : null;
  }
  
  /**
   * Return a {@link Properties} object.
   * @return The {@code Properties}
   */
  public Properties getProperties() {
    return Properties.builder().putAll(properties).build();
  }
 
  @Override
  /**
   * Return the {@link GeoJsonType} representing the {@code Feature}.
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
    return Util.cleanPoly(Util.GSON.toJson(this, Feature.class));
  }
  
  /**
   * {@code Feature} values.
   * @author Brandon Clayton
   */
  public static class Value {
    public static final String EXTENTS = "Extents";
  }

}
