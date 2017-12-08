package gov.usgs.earthquake.nshmp.internal;

import static com.google.common.base.Preconditions.checkState;
import static gov.usgs.earthquake.nshmp.util.Maths.round;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.Objects;

import gov.usgs.earthquake.nshmp.geo.Location;
import gov.usgs.earthquake.nshmp.geo.LocationList;
import gov.usgs.earthquake.nshmp.util.NamedLocation;

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
   * Returns the {@code LocationList} represented by the values in the supplied
   * JSON array. {@code coordsElement} is expected to contain only a single,
   * nested array of coordinates that define a polygonal border with no holes
   * per the GeoJSON <a
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

  /* GeoJSON objects for standard GSON serialization */

  public static class FeatureCollection<T> {
    String type = "FeatureCollection";
    public Object properties;
    public List<T> features;
  }

  public static class Feature {
    String type = "Feature";
    String id;
    Geometry geometry = new Geometry();
    PropertiesObject properties;
  }

  public static Feature createPoint(NamedLocation loc) {
    Feature f = new Feature();
    f.geometry.type = "Point";
    f.geometry.coordinates = toCoordinates(loc.location());
    f.properties = new PointProperties();
    f.properties.title = loc.toString();
    return f;
  }

  public static Feature createPoint(NamedLocation loc, String id) {
    Feature f = new Feature();
    f.geometry.type = "Point";
    f.geometry.coordinates = toCoordinates(loc.location());
    f.properties = new PropertiesObject();
    f.properties.location = loc.toString();
    f.properties.locationId = id;
    return f;
  }

  private static final String EXTENTS_COLOR = "#AA0078";

  static Feature createPolygon(
      String name,
      LocationList coords,
      Optional<String> id,
      Optional<Double> spacing) {

    Feature f = new Feature();

    PolyProperties properties = new PolyProperties();
    properties.title = name;
    if (spacing.isPresent()) {
      properties.spacing = spacing.get();
    }

    if (id.isPresent()) {
      f.id = id.get();
      coords = coords.bounds().toList();
      properties.strokeColor = EXTENTS_COLOR;
      properties.fillColor = EXTENTS_COLOR;
    }

    f.geometry.type = "Polygon";
    f.geometry.coordinates = ImmutableList.of(toCoordinates(coords));
    f.properties = properties;
    return f;
  }

  static class Geometry {
    String type;
    Object coordinates;
  }

  static class PropertiesObject {
    String title;
    String location;
    String locationId;
  }

  static class PointProperties extends PropertiesObject {
    @SerializedName("marker-size")
    String markerSize = "small";
  }

  static class PolyProperties extends PropertiesObject {
    Double spacing;
    @SerializedName("fill")
    String fillColor;
    @SerializedName("marker-color")
    String strokeColor;
  }
  
  public static Feature regionBounds(double lon,double lat) {
    double[] coordinates = {lon,lat};
    Feature f = new Feature();
    f.geometry.type = "Point";
    f.geometry.coordinates = coordinates;
    return f;
  }

  static double[] toCoordinates(Location loc) {
    return new double[] { round(loc.lon(), 5), round(loc.lat(), 5) };
  }

  static double[][] toCoordinates(LocationList locs) {
    double[][] coords = new double[locs.size()][2];
    for (int i = 0; i < locs.size(); i++) {
      coords[i] = toCoordinates(locs.get(i));
    }
    return coords;
  }

  /* brute force compaction of coordinate array onto single line */
  public static String cleanPoints(String s) {
    return s.replace(": [\n          ", ": [")
        .replace(",\n          ", ", ")
        .replace("\n        ]", "]") + "\n";
  }

  /* brute force compaction of coordinate array onto single line */
  static String cleanPoly(String s) {
    return s
        .replace("\n          [", "[")
        .replace("[\n              ", "[ ")
        .replace(",\n              ", ", ")
        .replace("\n            ]", " ]")
        .replace("\n        ]", "]") + "\n";
  }
}
