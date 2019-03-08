package gov.usgs.earthquake.nshmp.geo.json;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.common.collect.ImmutableMap;

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
@SuppressWarnings("unused")
public class Feature {

  private final GeoJson.Type type = GeoJson.Type.FEATURE;
  private final Object id;
  private final double[] bbox;
  private final Geometry<?> geometry;
  private final Map<String, Object> properties;

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
   * The 'id' of this feature as a string.
   */
  public String idAsString() {
    return (String) id;
  }

  /**
   * The 'id' of this feature as an integer.
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
     * Although the properties field can be serialized as null, we're generally
     * not serializing nulls so an empty map is a better solution.
     */

    private Object id;
    private double[] bbox;
    private Geometry<?> geometry;
    private Map<String, Object> properties;// = ImmutableMap.of();

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
     * @param bbox to set
     * @return this builder
     */
    public Builder bbox(double[] bbox) {
      this.bbox = bbox;
      return this;
    }

    /**
     * Set the properties of this feature.
     * 
     * @param properties to set
     * @return this builder
     */
    public Builder properties(Map<String, Object> properties) {
      this.properties = Collections.unmodifiableMap(new LinkedHashMap<String, Object>(properties)); 
      return this;
    }

    /**
     * Reuturn a new GeoJSON feature.
     */
    public Feature build() {
      checkState(!built, "This builder has already been used");
      built = true;
      return new Feature(this);
    }
  }

}
