package gov.usgs.earthquake.nshmp.json;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

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
  /** {@link Gson} for converting objects to a JSON {@code String} */
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
   * Return a {@code JsonElement} corresponding to a {@code String} 
   *    path in dot notation inside a {@code JsonElement}.
   * 
   * <pre> 
   *  // Example class with structure parameters.width.value
   *  static class Spectra {
   *    Parameters parameters;
   *  }
   *  
   *  static class Parameters {
   *    Width width;
   *  }
   * 
   *  static class Width {
   *    double value = 25.0;
   *  }
   *  
   *  // Create a new instance of Spectra 
   *  Spectra spectra = new Spectra();
   *  
   *  // Convert spectra into a JsonElement
   *  JsonElement jsonEl = Util.GSON.toJsonTree(spectra, Spectra.class);
   *  
   *  // The path in dot notation to find the value
   *  String jsonPath = "parameters.width.value";
   *  
   *  // Get the value as a JsonElement
   *  JsonElement widthValueEl = Util.getJsonObject(jsonEl, jsonPath);
   *  
   *  // Get the value as a double.
   *  // This is same as spectra.parameters.width.value
   *  double width = widthValueEl.getAsDouble();
   * </pre>
   * 
   * @param jsonEl The {@code JsonElement} to search.
   * @param jsonPath The {@code String} path in dot notation to find in the 
   *    {@code JsonElement}.
   * @return A {@code JsonElement} of the found object. 
   * @throws IllegalArgumentException If object is not found.
   */
  static public JsonElement getJsonObject(JsonElement jsonEl, String jsonPath) {
    JsonObject jsonObject = jsonEl.getAsJsonObject();
    List<String> pathList = Arrays.stream(jsonPath.split("\\."))
        .collect(Collectors.toList());
   
    try {
      for (String path : pathList.subList(0, pathList.size() - 1)) {
        jsonObject = jsonObject.get(path).getAsJsonObject();
      }
     
      JsonElement resultEl = jsonObject.get(pathList.get(pathList.size() - 1));
      if (resultEl == null) throw new NullPointerException();
      
      return resultEl;
    } catch (NullPointerException e) {
      throw new IllegalArgumentException("Object not found in path: " + jsonPath);
    }
  }
  
  /**
   * Return a {@code JsonElement} corresponding to a {@code String[]} 
   *    path inside a {@code JsonElement}.
   * 
   * <pre> 
   *  // Example class with structure parameters.width.value
   *  static class Spectra {
   *    Parameters parameters;
   *  }
   *  
   *  static class Parameters {
   *    Width width;
   *  }
   * 
   *  static class Width {
   *    double value = 25.0;
   *  }
   *  
   *  // Create a new instance of Spectra 
   *  Spectra spectra = new Spectra();
   *  
   *  // Convert spectra into a JsonElement
   *  JsonElement jsonEl = Util.GSON.toJsonTree(spectra, Spectra.class);
   *  
   *  // Get the value as a JsonElement
   *  JsonElement widthValueEl = Util.getJsonObject(
   *      jsonEl,
   *      "parameters",
   *      "width",
   *      "value");
   *  
   *  // Get the value as a double.
   *  // This is same as spectra.parameters.width.value
   *  double width = widthValueEl.getAsDouble();
   * </pre>
   * 
   * @param jsonEl The {@code JsonElement} to search.
   * @param jsonPath The {@code String} path in dot notation to find in the 
   *    {@code JsonElement}.
   * @return A {@code JsonElement} of the found object. 
   * @throws IllegalArgumentException If object is not found.
   */
  static public JsonElement getJsonObject(JsonElement jsonEl, String... jsonPath) {
    String path = Arrays.stream(jsonPath)
        .collect(Collectors.joining("."));
    
    return getJsonObject(jsonEl, path); 
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
   * Convert a {@code LocationList...} to a {@code List<double[][]>}
   * 
   * @param listLocs The {@code LocationList...}
   * @return The {@code List<double[][]>}
   */
  static List<double[][]> toCoordinates(LocationList... listLocs) {
    List<double[][]> coords = new ArrayList<>();
    
    for (LocationList locs : listLocs) {
      coords.add(toCoordinates(locs));
    }
    
    return coords;
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
      
      json.addProperty(JsonKey.TYPE.toLowerCase(), geometry.getType().toUpperCamelCase());
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
          PolygonLocations polygonLocs = fromPolygonCoordinates(coords);
          geom = (Polygon) new Polygon(polygonLocs.border, polygonLocs.interiors);
          break;
        case MULTI_POLYGON:
          MultiPolygon.Builder multiPolyBuilder = MultiPolygon.builder();
          
          for (JsonElement coordsEl : coords) {
            PolygonLocations multiPolygonLocs = fromPolygonCoordinates(
                coordsEl.getAsJsonArray());
            
            multiPolyBuilder.addPolygon(
                multiPolygonLocs.border, 
                multiPolygonLocs.interiors);
          }
          
          geom = (MultiPolygon) multiPolyBuilder.build();
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
   * Returns the {@link PolygonLocation} represented by the 
   *    values in the supplied JSON array. 
   * <br>
   *    
   * The {@code JsonArray} can contain nested array of coordinates 
   *    that define a polygonal border with interior holes.
   *
   * @param coordsArray to process
   */
  private static PolygonLocations fromPolygonCoordinates(JsonArray coordsArray) {
    JsonArray borderCoords = coordsArray.get(0).getAsJsonArray();
    LocationList border = fromCoordinates(borderCoords);
    
    List<LocationList> interiors = new ArrayList<>();
    
    for (int jl = 1; jl < coordsArray.size(); jl++) {
      interiors.add(fromCoordinates(coordsArray.get(jl).getAsJsonArray()));
    }
    
    return new PolygonLocations(border, interiors);
  }
 
  /**
   * Returns the {@code LocationList} represented by the values in the supplied
   * JSON array. 
   *
   * @param coords to process
   */
  private static LocationList fromCoordinates(JsonArray coords) {
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
   * Container for holding a {@link Polygon} border and interiors.
   * @author Brandon Clayton
   */
  private static class PolygonLocations {
    LocationList border;
    LocationList[] interiors;
    
    PolygonLocations(LocationList border, List<LocationList> interiors) {
      this.border = border;
      this.interiors = interiors.toArray(new LocationList[0]);
    }
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
