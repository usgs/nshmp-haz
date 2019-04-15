package gov.usgs.earthquake.nshmp.geo.json;

import static com.google.common.base.Preconditions.checkState;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.google.common.base.CaseFormat;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import gov.usgs.earthquake.nshmp.internal.TextUtils;

/**
 * Entry point for creating and parsing <a href="http://geojson.org"
 * target="_top">GeoJSON</a> features and feature collections.
 * 
 * <p>To create a GeoJSON feature collection string, use a builder and add
 * features per the example below:
 * 
 * <pre>
 * String geojson = GeoJson.builder()
 *     .add(Feature.point(Location.create(34, -117))
 *         .id("featureId")
 *         .properties(ImmutableMap.of(
 *             "title", "Feature Title",
 *             "color", "#ff0080"))
 *         .build())
 *     .add(Feature.polygon(
 *         LocationList.create(
 *             Location.create(34, -117),
 *             Location.create(35, -118),
 *             Location.create(37, -116),
 *             Location.create(38, -117)))
 *         .id(3)
 *         .build())
 *     .toJson();
 * </pre>
 * 
 * <p>Where the GeoJSON string created is:
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
 * A builder can also {@link Builder#write(Path) write} directly to a specified
 * path.
 * 
 * <p>Note in the example above that features have their own geometry-specific
 * builders. Feature builders also supply {@link Feature.Builder#toJson()
 * toJson()} and {@link Feature.Builder#write(Path) write(Path)} methods to
 * output single-feature GeoJSON directly.
 * 
 * <p>Parse GeoJSON to a feature or feature collection using static
 * {@code from*} methods as follows:
 * 
 * <pre>
 * Feature f = GeoJson.from(stringOrPathOrUrl).toFeature();
 * FeatureCollection fc = GeoJson.from(stringOrPathOrUrl).toFeatureCollection();
 * </pre>
 * 
 * <p>Once parsed, the feature geometry may be accessed as objects in the
 * {@code geo} package (e.g. {@code Location}, {@code LocationList}, etc...).
 * This requires some prior knowledge of the contents of the parsed GeoJSON.
 * 
 * <p>This class does not allow the creation of empty feature collections or
 * features with null geometry.
 * 
 * @author Peter Powers
 * @author Brandon Clayton
 */
public abstract class GeoJson {

  private GeoJson() {}

  /**
   * Return this GeoJson as a {@code Feature}.
   */
  public abstract Feature toFeature();

  /**
   * Return this GeoJson as a {@code FeatureCollection}.
   */
  public abstract FeatureCollection toFeatureCollection();

  /*
   * Developer notes:
   * 
   * TODO if depths are considered, should they be negative? Per the GeoJSON
   * spec: "Altitude or elevation MAY be included as an optional third element."
   * 
   * TODO: once Location has been been changed to 4-fields where we no longer
   * have radians to degrees conversion rounding errors, change the Geometry
   * converters to not round coordinates and record the original lat and lon
   * specified on creation.
   * 
   * Serialization note: Although we generally want to avoid null values in
   * GeoJSON properties members, we don't want to completely prevent their use.
   * However, when serializeNulls=true, all custom adapters are skipped so we
   * end up with optional GeoJSON member clutter, for example 'bbox' and 'id'.
   * The workaround is to use two distinct Gson instances (to avoid recursive
   * calls to delegate serializers) where we explicitely remove null fields
   * using JsonObject.remove().
   */

  /**
   * Create a GeoJSON from a string.
   * 
   * @param json string to read
   */
  public static GeoJson from(String json) {
    return new FromString(json);
  }

  /**
   * Create a GeoJSON from a file path.
   * 
   * @param json file path to read
   */
  public static GeoJson from(Path json) {
    return new FromPath(json);
  }

  /**
   * Create a GeoJSON from a URL. Use this method when reading resources from a
   * JAR.
   * 
   * @param json URL to read
   */
  public static GeoJson from(URL json) {
    return new FromUrl(json);
  }

  /**
   * A reusable GeoJSON builder.
   */
  public static Builder builder() {
    return new Builder();
  }

  /* Default Gson with FeatureCollection adapter. */
  static final Gson GSON_DEFAULT = new GsonBuilder()
      .registerTypeAdapter(
          FeatureCollection.class,
          new FeatureCollection.Serializer())
      .setPrettyPrinting()
      .disableHtmlEscaping()
      .serializeNulls()
      .create();

  /* Gson with Feature adapter. */
  static final Gson GSON_FEATURE = new GsonBuilder()
      .registerTypeAdapter(
          Feature.class,
          new Feature.Serializer())
      .setPrettyPrinting()
      .disableHtmlEscaping()
      .serializeNulls()
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
     * @return this builder
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
     * @return this builder
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
      String json = GSON_DEFAULT.toJson(new FeatureCollection(features, bbox));
      return cleanPoints(json);
    }

    /**
     * Serialize builder to its equivalent JSON representation and write to file
     * at {@code path}.
     * 
     * @throws IllegalStateException if builder is empty
     */
    public void write(Path path) throws IOException {
      Files.write(path, toJson().getBytes(StandardCharsets.UTF_8));
    }
  }

  /* Reformats point arays onto single line and appends newline character */
  static String cleanPoints(String s) {
    return s.replaceAll("\\[\\s+([-\\d])", "[$1")
        .replaceAll(",\\s+([-\\d])", ", $1")
        .replaceAll("(\\d)\\s+\\]", "$1]")
        .replaceAll("}\\Z", "}" + TextUtils.NEWLINE);
  }

  /** GeoJSON type identifier. */
  @JsonAdapter(TypeSerializer.class)
  public enum Type {

    /** GeoJSON {@code Feature} object. */
    FEATURE,

    /** GeoJSON {@code FeatureCollection} object. */
    FEATURE_COLLECTION,

    /** GeoJSON {@code Point} geometry. */
    POINT,

    /** GeoJSON {@code LineString} geometry. */
    LINE_STRING,

    /** GeoJSON {@code Polygon} geometry. */
    POLYGON;
  }

  /* Create a class with delegate to attach using JsonAdapter annotation. */
  static final class TypeSerializer extends TypeAdapter<Type> {

    TypeAdapter<Type> delegate = TextUtils.enumSerializer(
        TextUtils.enumStringConverter(Type.class, CaseFormat.UPPER_CAMEL));

    @Override
    public void write(JsonWriter out, Type value) throws IOException {
      delegate.write(out, value);
    }

    @Override
    public Type read(JsonReader in) throws IOException {
      return delegate.read(in);
    }
  }

  private static final class FromString extends GeoJson {

    final String json;

    FromString(String json) {
      this.json = json;
    }

    @Override
    public Feature toFeature() {
      return readString(Feature.class);
    }

    @Override
    public FeatureCollection toFeatureCollection() {
      return readString(FeatureCollection.class);
    }

    private <T> T readString(Class<T> classOfT) {
      return GSON_DEFAULT.fromJson(json, classOfT);
    }
  }

  private static final class FromUrl extends GeoJson {

    final URL json;

    FromUrl(URL json) {
      this.json = json;
    }

    @Override
    public Feature toFeature() {
      return readUrl(Feature.class);
    }

    @Override
    public FeatureCollection toFeatureCollection() {
      return readUrl(FeatureCollection.class);
    }

    private <T> T readUrl(Class<T> classOfT) {
      try (BufferedReader br = new BufferedReader(new InputStreamReader(json.openStream()))) {
        return GSON_DEFAULT.fromJson(br, classOfT);
      } catch (IOException ioe) {
        throw new JsonIOException(ioe);
      }
    }
  }

  private static final class FromPath extends GeoJson {

    final Path json;

    FromPath(Path json) {
      this.json = json;
    }

    @Override
    public Feature toFeature() {
      return readPath(Feature.class);
    }

    @Override
    public FeatureCollection toFeatureCollection() {
      return readPath(FeatureCollection.class);
    }

    private <T> T readPath(Class<T> classOfT) {
      try (BufferedReader br = Files.newBufferedReader(json)) {
        return GSON_DEFAULT.fromJson(br, classOfT);
      } catch (IOException ioe) {
        throw new JsonIOException(ioe);
      }
    }
  }

}
