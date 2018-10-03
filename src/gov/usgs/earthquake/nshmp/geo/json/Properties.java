package gov.usgs.earthquake.nshmp.geo.json;

import static com.google.common.base.Preconditions.checkNotNull;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.common.base.CaseFormat;
import com.google.common.base.Converter;
import com.google.common.collect.ImmutableMap;

/**
 * GeoJSON properties helper class.
 * 
 * <p>GeoJSON properties serialize to/from a {@code Map<String, Object>}. To
 * simplify repeated property map creation where only one property might be
 * changing, this class provides a re-usable builder. The class also provides
 * methods to help get and set <a
 * href="https://github.com/mapbox/simplestyle-spec/tree/master/1.1.0"
 * target="_top">simplestyle-spec</a> (v1.1.0) properties that may be considered
 * when rendering GeoJSON online.
 * 
 * <p>If a property map contains arrays or other nested objects as values, use
 * {@link #get(String)} and cast to the appropriate type. An array of any type
 * will have been deserialized to a {@code List<?>}; an object will have been
 * deserialized to a {@code Map<String,?>} of its members.
 * 
 * @author Peter Powers
 * @author Brandon Clayton
 */
public final class Properties {

  /* Property parsing. */

  private Map<String, Object> source;

  Properties(Map<String, Object> source) {
    this.source = source;
  }

  /**
   * Return the value for the specified key as an {@code Object}.
   * @throws NullPointerException if no such property key exists
   */
  public Object get(String key) {
    return checkNotNull(source.get(key), "No key '%s' in map: %s", key, source);
  }

  /**
   * Return the value for the specified simplestyle key as an {@code Object}.
   * @throws NullPointerException if no such property key exists
   */
  public Object get(Style key) {
    return get(key.toString());
  }

  /**
   * Return the value for the specified key as an {@code int}.
   * @throws NullPointerException if no such property key exists
   */
  public int getInt(String key) {
    return ((Double) get(key)).intValue();
  }

  /**
   * Return the value for the specified simplestyle key as an {@code int}.
   * @throws NullPointerException if no such property key exists
   */
  public int getInt(Style key) {
    return ((Double) get(key)).intValue();
  }

  /**
   * Return the value for specified key as an {@code double}.
   * @throws NullPointerException if no such property key exists
   */
  public double getDouble(String key) {
    return (double) get(key);
  }

  /**
   * Return the value for the specified simplestyle key as an {@code double}.
   * @throws NullPointerException if no such property key exists
   */
  public double getDouble(Style key) {
    return (double) get(key);
  }

  /**
   * Return the value for specified key as an {@code String}.
   * @throws NullPointerException if no such property key exists
   */
  public String getString(String key) {
    return (String) get(key);
  }

  /**
   * Return the value for the specified simplestyle key as an {@code String}.
   * @throws NullPointerException if no such property key exists
   */
  public String getString(Style key) {
    return (String) get(key);
  }

  /* Property building. */

  /**
   * Return a reusable property map builder that preserves the order in which
   * properties are added.
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Property map builder.
   */
  public static class Builder {

    private final Map<String, Object> map;

    private Builder() {
      map = new LinkedHashMap<>();
    }

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
     * Return an immutable map reflecting the current contents of this builder.
     */
    public Map<String, Object> build() {
      return ImmutableMap.copyOf(map);
    }
  }

  /**
   * Identifiers for <a
   * href="https://github.com/mapbox/simplestyle-spec/tree/master/1.1.0"
   * target="_top"> simplestyle-spec</a> (v1.1.0) properties.
   */
  @SuppressWarnings("javadoc")
  public enum Style {

    DESCRIPTION,
    FILL,
    FILL_OPACITY,
    ID,
    MARKER_COLOR,
    MARKER_SIZE,
    MARKER_SYMBOL,
    SPACING,
    STROKE,
    STROKE_OPACITY,
    STROKE_WIDTH,
    TITLE;

    private static final Converter<Style, String> STRING_CONVERTER =
        Util.enumStringConverter(Style.class, CaseFormat.LOWER_HYPHEN);

    @Override
    public String toString() {
      return STRING_CONVERTER.convert(this);
    }

    /* Reverse of toString case format conversion. */
    Style fromString(String s) {
      return STRING_CONVERTER.reverse().convert(s);
    }

    /* Case format converter. */
    static Converter<Style, String> converter() {
      return STRING_CONVERTER;
    }
  }

  // TODO validate for the different styles
  // TODO check that method is appropriate for style

  public static void main(String[] args) {

    Path path = Paths.get("../nshmp-haz-catalogs/2018/zones/SSCn.geojson");
    System.out.println(path.toAbsolutePath());
    FeatureCollection fc = GeoJson.fromJson(path);
    Properties props = fc.features.get(0).properties();
    System.out.println(((List<?>) props.get("mMax")).get(0).getClass());

    // Object obj = fc.features.get(0).properties.get("mMax");
    //
    // List<?> pp1 = (List<?>) obj;
    // System.out.println(pp1.get(0).getClass());
    // System.out.println(obj.getClass());
    //
    // System.out.println();

  }
}
