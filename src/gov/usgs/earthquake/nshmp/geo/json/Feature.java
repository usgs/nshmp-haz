package gov.usgs.earthquake.nshmp.geo.json;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import gov.usgs.earthquake.nshmp.geo.Location;
import gov.usgs.earthquake.nshmp.geo.LocationList;
import gov.usgs.earthquake.nshmp.geo.json.GeoJson.Type;

/**
 * A GeoJSON feature.
 * 
 * <p>This class provides factory methods to create single-use GeoJSON feature
 * builders for different geometries.
 * 
 * <p>See {@link GeoJson} for examples.
 * 
 * @author Peter Powers
 * @author Brandon Clayton
 */
public class Feature {

  @SuppressWarnings("unused") // for serialization only
  private final GeoJson.Type type = GeoJson.Type.FEATURE;
  private final Object id;
  private final double[] bbox;
  private final Geometry<?> geometry;
  private final Map<String, JsonElement> properties;

  private Feature(Builder builder) {
    this.id = builder.id;
    this.bbox = builder.bbox;
    this.geometry = builder.geometry;
    this.properties = builder.properties;
  }

  /**
   * Create a single-use point feature builder.
   * 
   * @param location of point
   */
  public static Builder point(Location location) {
    return new Builder(Geometry.point(checkNotNull(location)));
  }

  /**
   * Create a single-use line string feature builder.
   * 
   * @param line locations
   */
  public static Builder lineString(LocationList line) {
    return new Builder(Geometry.lineString(checkNotNull(line)));
  }

  /**
   * Create a single-use polygon feature builder.
   * 
   * @param exterior linear ring boundary of polygon
   * @param interiors optional interior linear rings
   */
  public static Builder polygon(LocationList exterior, LocationList... interiors) {
    return new Builder(Geometry.polygon(checkNotNull(exterior), interiors));
  }

  /**
   * Return whether this feature has a non-null 'id' member.
   */
  public boolean hasId() {
    return id != null;
  }

  /**
   * The 'id' of this feature as a string.
   * 
   * @throws NullPointerException if this feature does not contain an 'id'
   *         member. Use {@link #hasId()} to test whether 'id' member exists.
   */
  public String idAsString() {
    /*
     * Cast to string will return null; throw exception for consistency with
     * idAsInt().
     */
    return (String) checkNotNull(id);
  }

  /**
   * The 'id' of this feature as an integer.
   * 
   * @throws NullPointerException if this feature does not contain an 'id'
   *         member. Use {@link #hasId()} to test whether 'id' member exists.
   */
  public int idAsInt() {
    return ((Double) id).intValue();
  }

  /**
   * The bounding box value array; may be {@code null}.
   */
  public double[] bbox() {
    return bbox;
  }

  /**
   * Get the property map associated with this feature as a {@code Properties}
   * helper object. May be null or empty.
   */
  public Properties properties() {
    return new Properties(this.properties);
  }

  /**
   * The GeoJSON geometry type of this feature, one of:
   * {@code [POINT, LINE_STRING, POLYGON]}.
   * 
   * @see Type
   */
  public Type type() {
    return geometry.type;
  }

  /**
   * Return the geometry of this feature as a point.
   * 
   * @throws UnsupportedOperationException if feature is not a point.
   */
  public Location asPoint() {
    return geometry.toPoint();
  }

  /**
   * Return the geometry of this feature as a line string.
   * 
   * @throws UnsupportedOperationException if feature is not a line string.
   */
  public LocationList asLineString() {
    return geometry.toLineString();
  }

  /**
   * Return the border of this polygon feature.
   * 
   * @throws UnsupportedOperationException if feature is not a polygon.
   */
  public LocationList asPolygonBorder() {
    /*
     * TODO need to handle interiors; the method below is temporary before
     * implementing to Region conversions
     */
    return geometry.toPolygonBorder();
  }

  /**
   * A single-use feature builder. Repeat calls to builder methods overwrite
   * previous calls; reusing feature instances is not recommended.
   */
  public static class Builder {

    /*
     * Developer notes: 'properties' is always required in the GeoJSON spec and
     * is therefore initialized with an empty map that may be replaced later.
     * Although the properties field can be serialized as null, we prefer to
     * limit null serialization to the elements of the properties map.
     * 
     * 'id' and 'bbox' serialization will be skipped if null. See developer
     * notes in GeoJson for details on how this is handled.
     */

    private Object id;
    private double[] bbox;
    private Geometry<?> geometry;
    private Map<String, JsonElement> properties = ImmutableMap.of();

    private boolean built = false;

    private Builder(Geometry<?> geometry) {
      this.geometry = geometry;
    }

    /**
     * Set the optional {@code id} field of this feature as an integer.
     * 
     * @param id to set
     * @return this feature
     */
    public Builder id(int id) {
      this.id = id;
      return this;
    }

    /**
     * Set the optional {@code id} field of this feature as a string.
     * 
     * @param id to set
     * @return this builder
     */
    public Builder id(String id) {
      this.id = id;
      return this;
    }

    /**
     * Set the optional {@code bbox} (bounding box) field of this feature. See
     * the GeoJSON <a href="http://geojson.org" target="_top">specification</a
     * for details on bounding boxes.
     * 
     * @throws IllegalArgument
     * @param bbox to set
     * @return this builder
     */
    public Builder bbox(double[] bbox) {
      checkArgument(bbox.length == 4, "'bbox' array must have 4 values");
      this.bbox = bbox;
      return this;
    }

    /**
     * Set the properties of this feature.
     * 
     * @param properties to set
     * @return this builder
     */
    public Builder properties(Map<String, ?> properties) {
      this.properties = properties.entrySet().stream()
          .collect(Collectors.toMap(
              Map.Entry::getKey,
              e -> GeoJson.GSON_DEFAULT.toJsonTree(e.getValue())));
      return this;
    }

    /**
     * Return a new GeoJSON feature.
     */
    public Feature build() {
      checkState(!built, "This builder has already been used");
      built = true;
      return new Feature(this);
    }

    /**
     * Return the serialized form of a new GeoJSON feature.
     * 
     * @throws IllegalStateException if builder is empty
     */
    public String toJson() {
      String json = GeoJson.GSON_FEATURE.toJson(build());
      return GeoJson.cleanPoints(json);
    }

    /**
     * Write the serialized form of a new GeoJSON feature to the file at
     * {@code path}.
     * 
     * @throws IllegalStateException if builder is empty
     */
    public void write(Path path) throws IOException {
      Files.write(path, toJson().getBytes(StandardCharsets.UTF_8));
    }
  }

  static final class Serializer implements JsonSerializer<Feature> {

    @Override
    public JsonElement serialize(
        Feature feature,
        java.lang.reflect.Type typeOfSrc,
        JsonSerializationContext context) {

      /*
       * Can use default Gson because we'll never encounter any nested
       * FeatureCollections that would trigger recursive calls to this
       * serializer.
       */
      JsonObject jObj = GeoJson.GSON_DEFAULT
          .toJsonTree(feature)
          .getAsJsonObject();

      if (feature.bbox() == null) {
        jObj.remove("bbox");
      }
      return jObj;
    }
  }

}
