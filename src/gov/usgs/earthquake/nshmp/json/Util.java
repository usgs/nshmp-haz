package gov.usgs.earthquake.nshmp.json;

import java.lang.reflect.Type;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import gov.usgs.earthquake.nshmp.geo.Location;
import gov.usgs.earthquake.nshmp.geo.LocationList;
import gov.usgs.earthquake.nshmp.util.Maths;

/**
 * Utility methods for the json package.
 * 
 * @author Brandon Clayton
 */
public class Util {
  /** {@link Gson} for converting objects to JSON {@code String} */
  static final Gson GSON;
  /** Precision for rounding */
  private static final int ROUND = 5;

  static {
    GSON = new GsonBuilder()
        .registerTypeAdapter(Geometry.class, new GeometryDeserializer())
        .registerTypeAdapter(Geometry.class, new GeometrySerializer())
        .disableHtmlEscaping()
        .serializeNulls()
        .setPrettyPrinting()
        .create();
  }

  /**
   * Convert a {@link Location} to a {@code double[]}.
   * @param loc The {@code Location}.
   * @return A {@code double[]}.
   */
  static double[] toCoordinates(Location loc) {
    return new double[] { Maths.round(loc.lon(), ROUND), Maths.round(loc.lat(), ROUND) };
  }

  /**
   * Convert a {@link LocationList} to a {@code double[][]}.
   * @param locs The {@code LocationList}.
   * @return A {@code double[][]}.
   */
  static double[][] toCoordinates(LocationList locs) {
    double[][] coords = new double[locs.size()][2];

    for (int jl = 0; jl < locs.size(); jl++) {
      coords[jl] = toCoordinates(locs.get(jl));
    }

    return coords;
  }

  /**
   * Brute force compaction of coordinate array onto single line.
   * 
   * @param s The {@code String} to clean up.
   * @return The cleaned up {@code String}.
   */
  public static String cleanPoints(String s) {
    return s.replace(": [\n          ", ": [")
        .replace(",\n          ", ", ")
        .replace("\n        ]", "]") + "\n";
  }

  /**
   * Brute force compaction of coordinate array onto single line.
   * 
   * @param s The {@code String} to clean up.
   * @return The cleaned up {@code String}.
   */
  public static String cleanPoly(String s) {
    return s
        .replace("\n          [", "[")
        .replace("[\n              ", "[ ")
        .replace(",\n              ", ", ")
        .replace("\n            ]", " ]")
        .replace("\n        ]", "]") + "\n";
  }

  /**
   * {@link Geometry} serializer.
   *  
   * @author Brandon Clayton
   */
  private static final class GeometrySerializer 
      implements JsonSerializer<Geometry> {

    @Override
    public JsonElement serialize(
        Geometry geometry, 
        Type typeOfSrc, 
        JsonSerializationContext context) {
      
      JsonObject json = new JsonObject();
      
      json.addProperty(JsonKey.TYPE.toLowerCase(), geometry.getType());
      JsonElement coords = GSON.toJsonTree(geometry.getCoordinates());
      json.add(JsonKey.COORDINATES.toLowerCase(), coords);
      
      return json;
    }
  }

  /**
   * {@link Geometry} deserializer.
   * 
   * @author Brandon Clayton
   */
  private static final class GeometryDeserializer
      implements JsonDeserializer<Geometry> {

    @Override
    public Geometry deserialize(
        JsonElement json, 
        Type typeOfT, 
        JsonDeserializationContext context)
        throws JsonParseException {
      
      JsonObject jsonObject = json.getAsJsonObject();
      String type = jsonObject.get(JsonKey.TYPE.toLowerCase()).getAsString();
      JsonArray coords = jsonObject.get(JsonKey.COORDINATES.toLowerCase())
          .getAsJsonArray();
      
      Geometry geom = null;
      GeoJsonType jsonType = GeoJsonType.getEnum(type);
      
      switch (jsonType) {
        case POLYGON: 
          LocationList locs = fromCoordinates(coords);
          geom = (Polygon) new Polygon(locs);
          break;
        case POINT:
          Location loc = Location.create(
              coords.get(1).getAsDouble(), 
              coords.get(0).getAsDouble());
          geom = (Point) new Point(loc);
          break;
        default:
          throw new IllegalStateException("GeoJson geometry type not supported: " + type);
      }
      
      return geom;
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
  private static LocationList fromCoordinates(JsonArray coordsArray) {
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
   * GeoJson keys for a {@link Geometry}.
   * 
   * @author Brandon Clayton
   */
  private enum JsonKey {
    COORDINATES,
    TYPE;
    
    public String toLowerCase() {
      return name().toLowerCase();
    }
  }

}
