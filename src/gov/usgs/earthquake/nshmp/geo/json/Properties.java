package gov.usgs.earthquake.nshmp.geo.json;

import static com.google.common.base.Preconditions.checkNotNull;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.TreeMap;

import com.google.gson.JsonElement;

/**
 * Create {@code Properties} for {@link Feature}s. 
 * <br>
 * 
 * The {@code Properties} is simply a {@code Map<String, JsonElement>} to allow
 *    setting of both GeoJson spec and custom properties. 
 * <br><br>
 * 
 * The {@link Builder} must be used to set the properties. See {@link Builder}
 *  for example.
 * 
 * @author Brandon Clayton
 */
public class Properties {
  /** The {@code Properties} */
  private Map<String, JsonElement> attributes;

  private Properties(Builder builder) {
    this.attributes = new TreeMap<String, JsonElement>(builder.attributes);
  }
 
  /**
   * Return a {@code Map<String, JsonElement>} that represents the 
   *    properties.
   * @return The properties.
   */
  public Map<String, JsonElement> getProperties() {
    return attributes;
  }

  /**
   * Return {@code Properties} that match a specified {@code Class<T>}
   *    and return that {@code Class<T>} type.
   * <br>
   * 
   * The {@code Class<T>} structure must match that of the GeoJson properties
   *    object or will return {@code null} properties.
   * <br><br>
   * 
   * Example GeoJson Properties:
   * <pre>
   *  properties: {
   *    "mMax": [
   *      {
   *        "id": 1,
   *        "Mw": 6.8,
   *        "weight": 0.1
   *      }
   *    ]
   *  }
   * </pre>
   * 
   * Example Class to Represent Properties:
   * <pre>
   *  static class Max {
   *    ArrayList&ltMaxAttribites&gt mMax;  
   *  }
   *  
   *  static class MaxAttributes {
   *    int id;
   *    double Mw;
   *    double weight;
   *  }
   * </pre>
   * 
   * Example:
   * <pre>
   * Max mMax = properties.getProperty(Max.class);
   * </pre>
   *   
   * @param classOfT The {@code Class} structure that matches the properties
   * @return The {@code Class<T>}
   */
  public <T> T getProperty(Class<T> classOfT) {
    JsonElement propertiesEl = JsonUtil.GSON.toJsonTree(attributes);
    T properties = JsonUtil.GSON.fromJson(propertiesEl, classOfT);
    
    return properties; 
  }
  
 /**
  * Return {@code Properties} that match a specified {@code Type} given
  *     a key that matches the {@code Properties}.
  *     
  * Example GeoJson Properties:
  * <pre>
  *  properties: {
  *    "mMax": [
  *      {
  *        "id": 1,
  *        "Mw": 6.8,
  *        "weight": 0.1
  *      }
  *    ]
  *  }
  * </pre>
  * 
  * Example Class to Represent Properties:
  * <pre>
  *  static class MaxAttributes {
  *    int id;
  *    double Mw;
  *    double weight;
  *  }
  * </pre>
  * 
  * Example:
  * <pre>
  * ArrayList&ltMaxAttributes&gt mMax = properties.getProperty(
  *     "mMax", 
  *     new TypeToken&ltArrayList&ltMaxAttributes&gt&gt() {}.getType()); 
  * </pre>
  *     
  * @param key The {@code String} associated with the property.
  * @param typeOfT The specific type of source. You can obtain this type by 
  *     using the {@link com.google.gson.reflect.TypeToken} class.
  * @return The {@code Type}.
  */
  public <T> T getProperty(String key, Type typeOfT) {
    JsonElement propertiesEl = JsonUtil.GSON.toJsonTree(attributes.get(key));
    checkNotNull(propertiesEl, "Could not get attribute: " + key);
    
    return JsonUtil.GSON.fromJson(propertiesEl, typeOfT);
  }
  
  /**
   * Return the {@code JsonElement} corresponding to a key in the {@code Properties}
   *  
   * @param key The {@code String} key.
   * @return The value.
   */
  public JsonElement getProperty(String key) {
    return checkProperty(key);
  }

  /**
   * Return the {@code Boolean} corresponding to a key in the {@code Properties}
   *    {@code Map<String, Object>}.
   * 
   * @param key The {@code String} key.
   * @return The value;
   */
  public Boolean getBooleanProperty(String key) {
    return checkProperty(key).getAsBoolean();
  }
  
  /**
   * Return the {@code String} corresponding to a key in the {@code Properties}
   *    {@code Map<String, Object>}.
   *  
   * @param key The {@code String} key.
   * @return The value.
   */
  public String getStringProperty(String key) {
    return checkProperty(key).getAsString();
  }
 
  /**
   * Return the {@code double} corresponding to a key in the {@code Properties}
   *    {@code Map<String, Object>}.
   *  
   * @param key The {@code String} key.
   * @return The value.
   */
  public double getDoubleProperty(String key) {
    return checkProperty(key).getAsDouble();
  }
 
  /**
   * Return the {@code int} corresponding to a key in the {@code Properties}
   *    {@code Map<String, Object>}.
   *  
   * @param key The {@code String} key.
   * @return The value.
   */
  public int getIntProperty(String key) {
    return checkProperty(key).getAsInt();
  }
 
  /**
   * Determine if a key is present in the {@code Properties}.
   * @param key The {@code String} key to find.
   * @return Whether the key is present.
   */
  public Boolean hasProperty(String key) {
    JsonElement value = attributes.get(key);
    return value == null ? false : true;
  }
 
  /**
   * Return a {@code String} in JSON format.
   * @return The JSON {@code String}.
   */
  public String toJsonString() {
    return JsonUtil.GSON.toJson(this);
  }

  /**
   * Return a new instance of {@link Builder}
   * @return New {@code Builder}
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Common {@code Properties} keys.
   * @author Brandon Clayton
   */
  public static final class Key {
    public static final String DESCRIPTION = "description";
    public static final String FILL = "fill";
    public static final String FILL_OPACITY = "fill-opacity";
    public static final String ID = "id";
    public static final String MARKER_COLOR = "marker-color";
    public static final String MARKER_SIZE = "marker-size";
    public static final String MARKER_SYMBOL = "marker-symbol";
    public static final String SPACING = "spacing";
    public static final String STROKE = "stroke";
    public static final String STROKE_OPACITY = "stroke-opacity";
    public static final String STROKE_WIDTH = "stroke-width";
    public static final String TITLE = "title";
  }
  
  /**
   * Build a new {@link Properties} instance. <br><br>
   * 
   * Example:
   * 
   * <pre>
   * {@code
   *   Properties properties = Properties.builder()
   *      .title("My Title")
   *      .id("myId")
   *      .put("fill", "none")
   *      .put("linewidth", 2)
   *      .build();
   * }
   * </pre>
   * 
   * @author Brandon Clayton
   */
  public static class Builder {
    private Map<String, JsonElement> attributes = new TreeMap<>();

    private Builder() {}

    /**
     * Return a new instance of {@link Properties}. 
     * <br> 
     * Use convenience methods for setting "title" and "id" with ease: 
     *  <ul> 
     *    <li> {@link #id(String)} </li> 
     *    <li> {@link #title(String)} </li> 
     *  </ul>
     * 
     * @return New {@code Properties}
     */
    public Properties build() {
      return new Properties(this);
    }

    /**
     * Add the fill.
     * @param hexColor A HEX color.
     * @return The {@code Builder} to be chainable.
     */
    public Builder fill(String hexColor) {
      attributes.put(Key.FILL, JsonUtil.GSON.toJsonTree(hexColor, String.class));
      return this;
    }

    /**
     * Add the opacity. [0, 1]
     * @param opacity The opacity
     * @return The {@code Builder} to be chainable.
     */
    public Builder fillOpacity(double opacity) {
      attributes.put(Key.FILL_OPACITY, JsonUtil.GSON.toJsonTree(opacity, double.class));
      return this;
    }

    /**
     * Set the id.
     * @param id The id.
     * @return The {@code Builder} to be chainable.
     */
    public Builder id(String id) {
      attributes.put(Key.ID, JsonUtil.GSON.toJsonTree(id, String.class));
      return this;
    }

    /**
     * Add the marker size.
     * @param size The size.
     * @return The {@code Builder} to be chainable.
     */
    public Builder markerSize(String size) {
      attributes.put(Key.MARKER_SIZE, JsonUtil.GSON.toJsonTree(size, String.class ));
      return this;
    }

    /**
     * Add a marker symbol, a single alphanumeric character (a-z or 0-9). 
     * @param symbol The symbol.
     * @return The {@code Builder} to be chainable.
     */
    public Builder markerSymbol(String symbol) {
      attributes.put(Key.MARKER_SYMBOL, JsonUtil.GSON.toJsonTree(symbol, String.class ));
      return this;
    }

    /**
     * Add the marker color, a HEX color.
     * @param hexColor The HEX color.
     * @return The {@code Builder} to be chainable.
     */
    public Builder markerColor(String hexColor) {
      attributes.put(Key.MARKER_COLOR, JsonUtil.GSON.toJsonTree(hexColor, String.class));
      return this;
    }

    /**
     * Add a key and {@code double} to the {@code Properties}.
     * @param key The {@code Properties} key.
     * @param value The corresponding value.
     * @return The {@code Builder} to be chainable.
     */
    public Builder put(String key, double value) {
      attributes.put(key, JsonUtil.GSON.toJsonTree(value, double.class));
      return this;
    }

    /**
     * Add a key and {@code int} to the {@code Properties}.
     * @param key The {@code Properties} key.
     * @param value The corresponding value.
     * @return The {@code Builder} to be chainable.
     */
    public Builder put(String key, int value) {
      attributes.put(key, JsonUtil.GSON.toJsonTree(value, int.class));
      return this;
    }

    /**
     * Add a key and {@code Object} to the {@code Properties}.
     * @param key The {@code Properties} key.
     * @param value The corresponding value.
     * @return The {@code Builder} to be chainable.
     */
    public Builder put(String key, Object value) {
      attributes.put(key, JsonUtil.GSON.toJsonTree(value));
      return this;
    }

    /**
     * Add a key and {@code String} to the {@code Properties}.
     * @param key The {@code Properties} key.
     * @param value The corresponding value.
     * @return The {@code Builder} to be chainable.
     */
    public Builder put(String key, String value) {
      attributes.put(key, JsonUtil.GSON.toJsonTree(value, String.class));
      return this;
    }

    /**
     * Add a {@code Map<String, Object>} to the {@code Properties}.
     * @param attributes The {@code Properties}
     * @return The {@code Builder} to be chainable.
     */
    public Builder putAll(Map<String, JsonElement> attributes) {
      for (String key : attributes.keySet()) {
        this.attributes.put(key, attributes.get(key));
      }

      return this;
    }
   
    /**
     * Add the spacing.
     * @param spacing The spacing.
     * @return The {@code Builder} to be chainable.
     */
    public Builder spacing(double spacing) {
      attributes.put(Key.SPACING, JsonUtil.GSON.toJsonTree(spacing, double.class));
      return this;
    }

    /**
     * Add the stroke, a HEX color.
     * @param hexColor The HEX color.
     * @return The {@code Builder} to be chainable.
     */
    public Builder stroke(String hexColor) {
      attributes.put(Key.STROKE, JsonUtil.GSON.toJsonTree(hexColor, String.class));
      return this;
    }

    /**
     * Add the stroke opacity. [0, 1.0]
     * @param opacity The opacity.
     * @return The {@code Builder} to be chainable.
     */
    public Builder strokeOpacity(double opacity) {
      attributes.put(Key.STROKE_OPACITY, JsonUtil.GSON.toJsonTree(opacity, double.class));
      return this;
    }

    /**
     * Add the stroke width.
     * @param width The width.
     * @return The {@code Builder} to be chainable.
     */
    public Builder strokeWidth(double width) {
      attributes.put(Key.STROKE_WIDTH, JsonUtil.GSON.toJsonTree(width, double.class));
      return this;
    }

    /**
     * Set the title.
     * @param title The title.
     * @return The {@code Builder} to be chainable.
     */
    public Builder title(String title) {
      attributes.put(Key.TITLE, JsonUtil.GSON.toJsonTree(title, String.class));
      return this;
    }
    
  }
  
  /**
   * Return the {@code Object} corresponding to a key in the {@code Properties}
   *    {@code Map<String, Object>}.
   *  
   * @param key The {@code String} key.
   * @return The value.
   */
  private JsonElement checkProperty(String key) {
    JsonElement value = attributes.get(key);
    checkNotNull(value, "Could not get attribute: " + key);
    
    return value;
  }

}
