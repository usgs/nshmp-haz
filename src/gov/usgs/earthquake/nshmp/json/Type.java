package gov.usgs.earthquake.nshmp.json;

import static com.google.common.base.CaseFormat.UPPER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_UNDERSCORE;

/**
 * The nine case-sensitive GeoJson types:
 * 		<ul>
 * 			<li> FeatureCollection </li>
 * 			<li> Feature </li>
 * 			<li> Point </li>
 * 			<li> MultiPoint </li>
 * 			<li> Polygon </li>
 * 			<li> MultiPolygon </li>
 * 			<li> LineString </li>
 * 			<li> MultiLineString </li>
 *			<li> GeometryCollection </li>
 *		</ul>
 * 
 * @author Brandon Clayton
 */
public enum Type {
	FEATURE,
	FEATURE_COLLECTION,
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
}
