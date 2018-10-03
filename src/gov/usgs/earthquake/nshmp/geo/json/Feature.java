package gov.usgs.earthquake.nshmp.geo.json;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Map;

import com.google.common.collect.ImmutableMap;

import gov.usgs.earthquake.nshmp.geo.Location;
import gov.usgs.earthquake.nshmp.geo.LocationList;

/**
 * GeoJSON feature factory.
 * 
 * <p>This class provides factory methods to create GeoJSON features with
 * different geometries. The {@link Feature} objects returned are stateful.
 * Instance methods are provided to set the GeoJSON {@code id} and
 * {@code properties} elements. These methods return the feature itself and are
 * chainable. Repeat calls to instance methods overwrite previous calls and
 * reusing feature instances is not recommended.
 * 
 * <p>See {@link GeoJson} for examples.
 * 
 * @author Peter Powers
 * @author Brandon Clayton
 */
public class Feature {

  /*
   * Developer notes: 'properties' is required and is therefore always
   * initialized with an empty map that may be replaced later.
   */

  final GeoJson.Type type = GeoJson.Type.FEATURE;
  Object id;
  double[] bbox;
  final Geometry<?> geometry;
  Map<String, Object> properties = ImmutableMap.of();

  Feature(Geometry<?> geometry) {
    this.geometry = geometry;
  }

  /**
   * Create a point feature.
   * 
   * @param location
   */
  public static Feature point(Location location) {
    return new Feature(Geometry.point(checkNotNull(location)));
  }

  /**
   * Create a line string feature.
   * 
   * @param line
   */
  public static Feature lineString(LocationList line) {
    return new Feature(Geometry.lineString(checkNotNull(line)));
  }

  /**
   * Create a polygon feature.
   * 
   * @param exterior linear ring boundary of polygon
   * @param interiors optional interior linear rings
   */
  public static Feature polygon(LocationList exterior, LocationList... interiors) {
    return new Feature(Geometry.polygon(checkNotNull(exterior), interiors));
  }

  /**
   * Set the optional {@code id} field of this feature as a number.
   * 
   * @param id to set
   * @return this feature
   */
  public Feature id(Number id) {
    this.id = id;
    return this;
  }

  /**
   * Set the optional {@code id} field of this feature as a string.
   * 
   * @param id to set
   * @return this feature
   */
  public Feature id(String id) {
    this.id = id;
    return this;
  }

  /**
   * Set the optional {@code bbox} (bounding box) field of this feature. See the
   * GeoJSON <a href="http://geojson.org" target="_top">specification</a for
   * details on bounding boxes.
   * 
   * @param bbox to set
   * @return this feature
   */
  public Feature bbox(double[] bbox) {
    this.bbox = bbox;
    return this;
  }

  /**
   * Set the properties of this feature.
   * 
   * @param properties to set
   * @return this feature
   */
  public Feature properties(Map<String, Object> properties) {
    this.properties = ImmutableMap.copyOf(properties);
    return this;
  }

  /**
   * Get the properties associated with this feature as {@code Properties}
   * helper object.
   */
  public Properties properties() {
    return new Properties(this.properties);
  }

}
