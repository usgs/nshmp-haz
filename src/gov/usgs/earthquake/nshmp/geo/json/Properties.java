package gov.usgs.earthquake.nshmp.geo.json;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.LinkedHashMap;
import java.util.Map;

import com.google.common.base.CaseFormat;
import com.google.common.base.Converter;
import com.google.gson.JsonElement;

import gov.usgs.earthquake.nshmp.internal.TextUtils;

/**
 * GeoJSON properties helper class.
 * 
 * <p>GeoJSON properties serialize to/from a {@code Map<String, Object>}. To
 * simplify repeated property map creation where only one property might be
 * changing, this class provides a reusable builder. The class also provides
 * methods to help get and set <a
 * href="https://github.com/mapbox/simplestyle-spec/tree/master/1.1.0"
 * target="_top">simplestyle-spec</a> (v1.1.0) properties that may be considered
 * when rendering GeoJSON online.
 * 
 * <p>If a property map contains arrays or other nested objects as values, use
 * {@link #get(String)} and cast to the appropriate type:<ul><li>Arrays of any
 * type deserialize to {@code List<?>}</li><li>Objects deserialize to
 * {@code Map<String,?>}
 * 
 * @author Peter Powers
 * @author Brandon Clayton
 */
public final class Properties {

  // TODO tests: validate for the different styles
  // TODO tests: check that method is appropriate for style

  /* Property parsing. */

  private Map<String, JsonElement> source;

  Properties(Map<String, JsonElement> source) {
    this.source = source;
  }

  /**
   * Return whether the supplied key exists in the property map
   * @param key to check for
   */
  public boolean containsKey(String key) {
    return source.containsKey(key);
  }

  /**
   * Return whether the supplied key exists in the property map
   * @param key get value for
   */
  public boolean containsKey(Style key) {
    return containsKey(key.toString());
  }

  JsonElement getJsonElement(String key) {
    checkArgument(
        containsKey(key),
        "No key '%s' in map: %s",
        key, source.keySet());
    return source.get(key);
  }

  /**
   * Return the value for the specified key as an object. Note that this method
   * may return {@code null} as the property value.
   * 
   * @throws IllegalArgumentException if no such property key exists
   * @param key to get value for
   */
  public Object get(String key) {
    return GeoJson.GSON_DEFAULT.fromJson(getJsonElement(key), Object.class);
  }

  /**
   * Return the value for the specified key as an object of the specified class.
   * 
   * @throws IllegalArgumentException if no such property key exists
   * @param <T> the type of the desired object
   * @param key to get value for
   * @param classOfT the class of T
   */
  public <T> T get(String key, Class<T> classOfT) {
    return GeoJson.GSON_DEFAULT.fromJson(getJsonElement(key), classOfT);
  }

  /**
   * Return the value for the specified key as an {@code boolean}.
   * @throws IllegalArgumentException if no such property key exists
   */
  public boolean getBoolean(String key) {
    return getJsonElement(key).getAsBoolean();
  }

  /**
   * Return the value for the specified key as an {@code int}.
   * @throws IllegalArgumentException if no such property key exists
   */
  public int getInt(String key) {
    return getJsonElement(key).getAsInt();
  }

  /**
   * Return the value for specified key as an {@code double}.
   * @throws IllegalArgumentException if no such property key exists
   */
  public double getDouble(String key) {
    return getJsonElement(key).getAsDouble();
  }

  /**
   * Return the value for the specified simplestyle key as an {@code double}.
   * @throws NullPointerException if no such property key exists
   */
  public double getDouble(Style key) {
    return getDouble(key.toString());
  }

  /**
   * Return the value for specified key as an {@code String}.
   * @throws NullPointerException if no such property key exists
   */
  public String getString(String key) {
    return getJsonElement(key).getAsString();
  }

  /**
   * Return the value for the specified simplestyle key as an {@code String}.
   * @throws NullPointerException if no such property key exists
   */
  public String getString(Style key) {
    return getString(key.toString());
  }

  /* Property building. */

  /**
   * Return a reusable property map builder that preserves the order in which
   * properties are added. {@code build()} returns a copy of the internal map
   * instance.
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Reusable property map builder.
   * 
   * @see Properties#builder()
   */
  public static class Builder {

    /*
     * Insertion order preserving map. On build(), we create a copy so that in
     * the unlikely event that a previous property map is hanging around and
     * hasn't been written it won't be modified.
     */
    private final Map<String, Object> map = new LinkedHashMap<>();

    private Builder() {}

    /**
     * Add a property to this builder.
     * 
     * @param key for value
     * @param value for key
     * @return this builder
     */
    public Builder put(String key, Object value) {
      map.put(key, value);
      return this;
    }

    /**
     * Add a SimpleStyle property to this builder.
     * 
     * @param key for value
     * @param value for key
     * @return this builder
     */
    public Builder put(Style key, Object value) {
      return put(key.toString(), value);
    }

    /**
     * Return a mutable copy of the internal builder map.
     */
    public Map<String, Object> build() {
      return new LinkedHashMap<>(map);
    }
  }

  /**
   * Identifiers for <a
   * href="https://github.com/mapbox/simplestyle-spec/tree/master/1.1.0"
   * target="_top"> simplestyle-spec</a> (v1.1.0) properties. These properties
   * are rendering hints that may improve the appearance of GeoJSON features in
   * some rendering environments. The description for each identifier indicates
   * the expected value type when adding style-keyed properties.
   * 
   * <p><a name="color-rules"/><b>Color rules:</b><ul><li>Colors can be in short
   * form: {@code "#ace"}<li>Or long form: {@code "#aaccee"}<li>With or without
   * the prefix: {@code #}<li>Colors are interpreted the same as in CSS...<li>In
   * either {@code #RRGGBB} or {@code #RGB} order<li>Other color formats or
   * named colors are not supported</ul>
   */
  public enum Style {

    /**
     * Value: {@code String}
     */
    DESCRIPTION,

    /**
     * Value: color {@code String} (see <a href="#color-rules">color rules</a>)
     */
    FILL,

    /**
     * Value: {@code 0.0 ≤ double ≤ 1.0}
     */
    FILL_OPACITY,

    /**
     * Value: color {@code String} (see <a href="#color-rules">color rules</a>)
     */
    MARKER_COLOR,

    /**
     * Value: {@code ["small", "medium, "large"]}
     */
    MARKER_SIZE,

    /**
     * Value: <a href="https://www.mapbox.com/maki-icons/" target="_top">named
     * icon</a> {@code String}, {@code 0 ≤ int ≤ 9}, or any lowercase character
     * {@code String}, {@code a-z}
     */
    MARKER_SYMBOL,

    /**
     * Value: color {@code String} (see <a href="#color-rules">color rules</a>)
     */
    STROKE,

    /**
     * Value: {@code 0.0 ≤ double ≤ 1.0}
     */
    STROKE_OPACITY,

    /**
     * Value: {@code 0.0 ≤ double}
     */
    STROKE_WIDTH,

    /**
     * Value: {@code String}
     */
    TITLE;

    private static final Converter<Style, String> STRING_CONVERTER =
        TextUtils.enumStringConverter(Style.class, CaseFormat.LOWER_HYPHEN);

    /**
     * Returns the {@link #name()} of this identifier converted to
     * {@link CaseFormat#LOWER_HYPHEN}.
     */
    @Override
    public String toString() {
      return STRING_CONVERTER.convert(this);
    }
  }
}
