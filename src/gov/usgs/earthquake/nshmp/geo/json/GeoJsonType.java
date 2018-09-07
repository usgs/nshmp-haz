package gov.usgs.earthquake.nshmp.json;

import static com.google.common.base.CaseFormat.UPPER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_UNDERSCORE;

/**
 * The nine case-sensitive GeoJson types: 
 *  <ul> 
 *    <li> FeatureCollection </li> 
 *    <li> Feature </li> 
 *    <li> Point </li> 
 *    <li> MultiPoint </li> 
 *    <li> Polygon </li>
 *    <li> MultiPolygon </li> 
 *    <li> LineString </li> 
 *    <li> MultiLineString </li> 
 *    <li> GeometryCollection </li> 
 *  </ul>
 * 
 * @author Brandon Clayton
 */
public enum GeoJsonType {
  FEATURE,
  FEATURE_COLLECTION,
  
  /* GeoJson geometry type */
  GEOMETRY_COLLECTION,
  LINE_STRING,
  MULTI_LINE_STRING,
  MULTI_POINT,
  MULTI_POLYGON,
  POINT,
  POLYGON;

  /**
   * Return a upper camel case {@code String}.
   * @return The upper camel case {@code String}.
   */
  public String toUpperCamelCase() {
    return UPPER_UNDERSCORE.to(UPPER_CAMEL, name());
  }
 
  /**
   * Return a {@code GeoJsonType} from a {@code String}.
   * The {@code String} should be in upper camel case as per GeoJson spec.
   * 
   * @param type The string representing the {@code GeoJsonType}.
   * @return The {@code GeoJsonType}.
   */
  public static GeoJsonType getEnum(String type) {
    for (GeoJsonType jsonType : GeoJsonType.values()) {
      if (jsonType.toUpperCamelCase().equals(type)) {
        return jsonType;
      }
    }
    throw new IllegalStateException("Could not find type: " + type);
  }

}
