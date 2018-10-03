package gov.usgs.earthquake.nshmp.geo.json;

import static com.google.common.base.Preconditions.checkState;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.google.common.base.CaseFormat;
import com.google.common.base.Converter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;

/**
 * Entry point for creating and parsing <a href="http://geojson.org"
 * target="_top">GeoJSON</a> feature collections.
 * 
 * <p>This class will not create or parse JSON containing a single feature (even
 * though this is valid GeoJSON), nor will it allow the creation of empty
 * feature collections. To create a GeoJSON string, use a builder and add
 * features per the example below:
 * 
 * <pre>
 * String geojson = GeoJson.builder()
 *     .add(Feature.point(Location.create(34, -117))
 *         .id("featureId")
 *         .properties(ImmutableMap.of(
 *             "title", "Feature Title",
 *             "color", "#ff0080")))
 *     .add(Feature.polygon(
 *         LocationList.create(
 *             Location.create(34, -117),
 *             Location.create(35, -118),
 *             Location.create(37, -116),
 *             Location.create(38, -117)))
 *         .id(3))
 *     .toJson();
 * </pre>
 * 
 * <p>where the GeoJSON string created is:
 * 
 * <pre>
* {
 *   "type": "FeatureCollection",
 *   "features": [
 *     {
 *       "type": "Feature",
 *       "id": "featureId",
 *       "geometry": {
 *         "type": "Point",
 *         "coordinates": [-117.0, 34.0]
 *       },
 *       "properties": {
 *         "title": "Feature Title",
 *         "color": "#ff0080"
 *       }
 *     },
 *     {
 *       "type": "Feature",
 *       "id": 3,
 *       "geometry": {
 *         "type": "Polygon",
 *         "coordinates": [
 *           [
 *             [-117.0, 34.0],
 *             [-118.0, 35.0],
 *             [-116.0, 37.0],
 *             [-117.0, 38.0]
 *           ]
 *         ]
 *       },
 *       "properties": {}
 *     }
 *   ]
 * }
 * </pre>
 * 
 * @author Peter Powers
 * @author Brandon Clayton
 */
public final class GeoJson {

  /**
   * Return a GeoJSON {@code FeatureCollection} builder.
   */
  static Builder builder() {
    return new Builder();
  }

  static Gson GSON = new GsonBuilder()
      .registerTypeAdapter(
          GeoJson.Type.class,
          new Util.Serializer<>(GeoJson.Type.converter()))
      .registerTypeAdapter(
          GeoJson.Type.class,
          new Util.Deserializer<>(GeoJson.Type.converter()))
      .registerTypeAdapterFactory(
          new Util.ArrayAdapterFactory())
      .setPrettyPrinting()
      .create();

  /**
   * A GeoJSON builder.
   */
  public static class Builder {

    private double[] bbox;
    private List<Feature> features;

    private Builder() {
      features = new ArrayList<>();
    }

    /**
     * Add a feature. Use static factory methods in {@link Feature} to create
     * new features.
     * 
     * @param feature to add
     * @see Feature
     */
    public Builder add(Feature feature) {
      features.add(feature);
      return this;
    }

    /**
     * Set the optional {@code bbox} (bounding box) field of this feature
     * collection. See the GeoJSON <a href="http://geojson.org"
     * target="_top">specification</a for details on bounding boxes.
     * 
     * @param bbox to set
     * @return
     */
    public Builder bbox(double[] bbox) {
      this.bbox = bbox;
      return this;
    }

    /**
     * Serialize builder to its equivalent JSON representation.
     * 
     * @throws IllegalStateException if builder is empty
     */
    public String toJson() {
      checkState(features.size() > 0, "GeoJSON is empty");
      return GSON.toJson(new FeatureCollection(features, bbox));
    }
  }

  /**
   * Deserialize a GeoJSON {@code FeatureCollection} from a string.
   * 
   * @param json string to deserialize
   */
  public static FeatureCollection fromJson(String json) {
    return GSON.fromJson(json, FeatureCollection.class);
  }

  /**
   * Deserialize a GeoJSON {@code FeatureCollection} from a reader. For
   * 
   * @param json reader from which to deserialize
   */
  public static FeatureCollection fromJson(Reader json) {
    return GSON.fromJson(json, FeatureCollection.class);
  }

  /**
   * Deserialize a GeoJSON {@code FeatureCollection} from a file path.
   * 
   * @param json file path from which to deserialize
   */
  public static FeatureCollection fromJson(Path json) {
    // rethrow IOException as Runtime
    try (BufferedReader br = Files.newBufferedReader(json)) {
      return fromJson(Files.newBufferedReader(json));
    } catch (IOException ioe) {
      throw new JsonIOException(ioe);
    }
  }

  /**
   * Deserialize a GeoJSON {@code FeatureCollection} from a URL. Use this for
   * reading from Java resources that may be packaged in a JAR.
   * 
   * @param json URL from which to deserialize
   */
  public static FeatureCollection fromJson(URL json) {
    // rethrow IOException as Runtime
    try (BufferedReader br = new BufferedReader(new InputStreamReader(json.openStream()))) {
      return fromJson(br);
    } catch (IOException ioe) {
      throw new JsonIOException(ioe);
    }
  }

  /* GeoJSON type identifier. */
  enum Type {

    /* object */
    FEATURE,
    FEATURE_COLLECTION,

    /* geometry */
    POINT,
    MULTI_POINT,
    LINE_STRING,
    MULTI_LINE_STRING,
    POLYGON,
    MULTI_POLYGON,
    GEOMETRY_COLLECTION;

    private static final Converter<Type, String> STRING_CONVERTER =
        Util.enumStringConverter(Type.class, CaseFormat.UPPER_CAMEL);

    @Override
    public String toString() {
      return STRING_CONVERTER.convert(this);
    }

    /* Reverse of toString case format conversion. */
    Type fromString(String s) {
      return STRING_CONVERTER.reverse().convert(s);
    }

    /* Case format converter. */
    static Converter<Type, String> converter() {
      return STRING_CONVERTER;
    }
  }

}
