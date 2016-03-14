package org.opensha2.util;

import static com.google.common.base.Preconditions.checkState;

import java.util.Objects;

import org.opensha2.geo.LocationList;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * GeoJSON serialization keys, values, and utilities.
 *
 * @author Peter Powers
 */
@SuppressWarnings("javadoc")
public final class GeoJson {

	public static final class Key {
		public static final String TYPE = "type";
		public static final String FEATURES = "features";
		public static final String ID = "id";
		public static final String GEOMETRY = "geometry";
		public static final String COORDINATES = "coordinates";
		public static final String PROPERTIES = "properties";
	}

	public static final class Value {
		public static final String FEATURE_COLLECTION = "FeatureCollection";
		public static final String FEATURE = "Feature";
		public static final String POINT = "Point";
		public static final String POLYGON = "Polygon";
		public static final String EXTENTS = "Extents";
	}

	public static final class Properties {
		public static final class Key {
			public static final String TITLE = "title";
			public static final String DESCRIPTION = "description";
			public static final String MARKER_SIZE = "marker-size";
			public static final String MARKER_SYMBOL = "marker-symbol";
			public static final String MARKER_COLOR = "marker-color";
			public static final String STROKE = "stroke";
			public static final String STROKE_OPACITY = "stroke-opacity";
			public static final String STROKE_WIDTH = "stroke-width";
			public static final String FILL = "fill";
			public static final String FILL_OPACITY = "fill-opacity";
			public static final String SPACING = "spacing";
		}
	}

	/**
	 * Returns the {@code LocationList} represented by the values in the
	 * supplied JSON array. {@code coordsElement} is expected to contain only
	 * a single, nested array of coordinates that define a polygonal border with
	 * no holes per the GeoJSON <a
	 * href="http://geojson.org/geojson-spec.html#id4">polygon</a> spec.
	 * 
	 * @param coordsArray to process
	 */
	public static LocationList fromCoordinates(JsonArray coordsArray) {
		JsonArray coords = coordsArray.get(0).getAsJsonArray();
		LocationList.Builder builder = LocationList.builder();
		for (JsonElement element : coords) {
			JsonArray coord = element.getAsJsonArray();
			builder.add(
				coord.get(1).getAsDouble(),
				coord.get(0).getAsDouble());
		}
		return builder.build();
	}

	/**
	 * Checks that the property in the supplied object with the name {@code key}
	 * matches an expected {@code value}.
	 * 
	 * @param json object to check for property
	 * @param key property name
	 * @param value of property
	 */
	public static void validateProperty(JsonObject json, String key, String value) {
		String actual = json.get(key).getAsString();
		checkState(
			Objects.equals(value, actual),
			"Expected \"%s\" : \"%s\", but found \"$s\"",
			key, value, actual);
	}
}
