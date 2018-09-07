package gov.usgs.earthquake.nshmp.geo.json;

import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;

import gov.usgs.earthquake.nshmp.geo.Location;
import gov.usgs.earthquake.nshmp.geo.LocationList;

import static com.google.common.base.Preconditions.checkState;

/**
 * Create a GeoJson {@code Feature} with a {@link Geometry} and
 *    {@link Properties}. 
 * 
 * @author Brandon Clayton
 */
public class Feature implements GeoJson {
  /** The {@link GeoJsonType} of GeoJson object: Feature */
  private final String type;
  /** An {@code Optional} id field */
  private JsonElement id;
  /** {@code Feature} bounding box */
  private double[] bbox;
  /** The {@link Geometry} */
  private Geometry geometry;
  /** The {@link Properties} */
  private Map<String, JsonElement> properties;

  private Feature(Builder builder) {
    this.type = GeoJsonType.FEATURE.toUpperCamelCase();
    this.id = builder.id;
    this.bbox = builder.bbox;
    this.geometry = builder.geometry;
    this.properties = builder.properties;
  }

  /**
   * Return a {@code double[]} representing the bounding
   *    box for the {@code Feature}. 
   * <br>
   * 
   * If not bounding box was set, returns {@code null}.
   * @return The bounding box.
   */
  public double[] getBbox() {
    return bbox != null ? bbox : null;
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
    return JsonUtil.cleanPoly(JsonUtil.GSON.toJson(this, Feature.class));
  }
  
  /**
   * {@code Feature} values.
   * @author Brandon Clayton
   */
  public static class Value {
    public static final String EXTENTS = "Extents";
  }

  /**
   * Return a new instance of {@link Builder}.
   * @return New {@code Builder}.
   */
  public static Builder builder() {
    return new Builder();
  }
  
  /**
   * Build a {@code Feature} with a {@link Geometry},
   *    {@link Properties}, an {@code Optional} id, and 
   *    and {@code Optional} bounding box.
   * <br><br>
   *    
   * Example:
   * <pre>
   *  // Build properties 
   *  Properties properties = Properties.builder()
   *      .title("Golden")
   *      .id("golden")
   *      .build();
   *       
   *  // Build a Feature
   *  Feature feature = Feature.builder()
   *      .addPoint(39.75, -105)
   *      .properties(properties)
   *      .build();
   * 
   * </pre>
   * @author Brandon Clayton
   */
  public static class Builder {
    private double[] bbox;
    private JsonElement id; 
    private Geometry geometry;
    private Map<String, JsonElement> properties;
  
    private Builder() {}
   
    /**
     * Return a new {@code Feature}.
     * @return The {@code Feature}.
     */
    public Feature build() {
      checkState(geometry != null, "Geometry must be set");
      checkState(properties != null, "Properties must be set");
      return new Feature(this);
    }
   
    /**
     * Set the {@code Feature} {@code Geometry}. 
     * @param geometry The {@code Geometry}.
     * @return The {@code Builder}.
     */
    public Builder addGeometry(Geometry geometry) {
      this.geometry = geometry;
      return this;
    }

    /**
     * Set the {@code Feature} {@code Geometry} with a {@code MultiPolygon}. 
     * @param multiPolygon The {@code MultiPolygon}.
     * @return The {@code Builder}.
     */
    public Builder addMultiPolygon(MultiPolygon multiPolygon) {
      checkGeometry();
      geometry = multiPolygon;
      return this;
    }
    
    /**
     * Set the {@code Feature} {@code Geometry} with a {@code MultiPolygon}. 
     * @param polygons The {@code List<Polygon>}. 
     * @return The {@code Builder}.
     */
    public Builder addMultiPolygon(List<Polygon> polygons) {
      checkGeometry();
      MultiPolygon.Builder multiPolygon = MultiPolygon.builder();
      polygons.stream().forEach(polygon -> multiPolygon.addPolygon(polygon));
      geometry = multiPolygon.build();
      return this;
    }
    
    /**
     * Set the {@code Feature} {@code Geometry} with a {@code Point}. 
     * @param point The {@code Point}. 
     * @return The {@code Builder}.
     */
    public Builder addPoint(Point point) {
      checkGeometry();
      geometry = point;
      return this;
    }
    
    /**
     * Set the {@code Feature} {@code Geometry} with a {@code Point}. 
     * @param loc The {@code Location} for the {@code Point}. 
     * @return The {@code Builder}.
     */
    public Builder addPoint(Location loc) {
      checkGeometry();
      geometry = new Point(loc);
      return this;
    }
    
    /**
     * Set the {@code Feature} {@code Geometry} with a {@code Point}. 
     * @param latitude The latitude of the {@code Point}. 
     * @param longitude The longitude of the {@code Point}. 
     * @return The {@code Builder}.
     */
    public Builder addPoint(double latitude, double longitude) {
      checkGeometry();
      geometry = new Point(latitude, longitude);
      return this;
    }
    
    /**
     * Set the {@code Feature} {@code Geometry} with a {@code Polygon}. 
     * @param polygon The {@code Polygon}. 
     * @return The {@code Builder}.
     */
    public Builder addPolygon(Polygon polygon) {
      checkGeometry();
      geometry = polygon;
      return this;
    }
    
    /**
     * Set the {@code Feature} {@code Geometry} with a {@code Polygon}. 
     * @param border The {@code Polygon} border.
     * @param interiors The {@code Polygon} interiors.
     * @return The {@code Builder}.
     */
    public Builder addPolygon(LocationList border, LocationList... interiors) {
      checkGeometry();
      geometry = new Polygon(border, interiors); 
      return this;
    }
   
    /**
     * Set the {@code Feature} bounding box.
     * <br>
     * This is an optional field and does not need set.
     * @param bbox The bounding box.
     * @return The {@code Builder}.
     */
    public Builder bbox(double[] bbox) {
      this.bbox = bbox;
      return this;
    }
    
    /**
     * Set the {@code Feature} id;
     * <br>
     * This is an optional field and does not need set.
     * @param id The id.
     * @return The {@code Builder}.
     */
    public Builder id(int id) {
      this.id = JsonUtil.GSON.toJsonTree(id, Integer.class);
      return this;
    }
    
    /**
     * Set the {@code Feature} id;
     * <br>
     * This is an optional field and does not need set.
     * @param id The id.
     * @return The {@code Builder}.
     */
    public Builder id(JsonElement id) {
      this.id = id;
      return this;
    }
    
    /**
     * Set the {@code Feature} id;
     * <br>
     * This is an optional field and does not need set.
     * @param id The id.
     * @return The {@code Builder}.
     */
    public Builder id(String id) {
      this.id = JsonUtil.GSON.toJsonTree(id, String.class);
      return this;
    }
   
    /**
     * Set the {@code Feature} {@link Properties}.
     * @param properties The {@code Properties}.
     * @return The {@code Builder}.
     */
    public Builder properties(Properties properties) {
      this.properties = properties.getProperties();
      return this;
    }
    
    private void checkGeometry() {
      checkState(geometry == null, "Geometry already set");
    }
  }
 
}
