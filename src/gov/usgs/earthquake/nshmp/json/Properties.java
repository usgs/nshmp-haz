package gov.usgs.earthquake.nshmp.json;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonElement;

import static com.google.common.base.Preconditions.checkNotNull;

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
    this.attributes = builder.attributes;
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
    JsonElement propertiesEl = Util.GSON.toJsonTree(attributes);
    T properties = Util.GSON.fromJson(propertiesEl, classOfT);
    
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
    JsonElement propertiesEl = Util.GSON.toJsonTree(
        attributes.get(key), 
        typeOfT);
    
    checkNotNull(propertiesEl, "Could not get attribute: " + key);
   
    return Util.GSON.fromJson(propertiesEl, typeOfT);
  }
  
  /**
   * Return the {@code JsonElement} corresponding to a key in the {@code Properties}
   *  
   * @param key The {@code String} key.
   * @return The value.
   */
  public JsonElement getProperty(String key) {
    JsonElement value = attributes.get(key);
    checkNotNull(value, "Could not get attribute: " + key);
    
    return value;
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
   * Return a {@code String} in JSON format.
   * @return The JSON {@code String}.
   */
  public String toJsonString() {
    return Util.GSON.toJson(this);
  }

  /**
   * Return a new instance of {@link Builder}
   * @return New {@code Builder}
   */
  public static Builder builder() {
    return new Builder();
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
    private Map<String, JsonElement> attributes = new HashMap<>();

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
     * Set the id.
     * @param id The id.
     * @return The {@code Builder} to be chainable.
     */
    public Builder id(String id) {
      attributes.put(
          Attributes.ID.toLowerCase(), 
          Util.GSON.toJsonTree(id, String.class));
      return this;
    }

    /**
     * Add a key and {@code double} to the {@code Properties}.
     * @param key The {@code Properties} key.
     * @param value The corresponding value.
     * @return The {@code Builder} to be chainable.
     */
    public Builder put(String key, double value) {
      attributes.put(
          key, 
          Util.GSON.toJsonTree(value, double.class));
      return this;
    }

    /**
     * Add a key and {@code int} to the {@code Properties}.
     * @param key The {@code Properties} key.
     * @param value The corresponding value.
     * @return The {@code Builder} to be chainable.
     */
    public Builder put(String key, int value) {
      attributes.put(
          key, 
          Util.GSON.toJsonTree(value, int.class));
      return this;
    }

    /**
     * Add a key and {@code Object} to the {@code Properties}.
     * @param key The {@code Properties} key.
     * @param value The corresponding value.
     * @return The {@code Builder} to be chainable.
     */
    public Builder put(String key, Object value) {
      attributes.put(
          key, 
          Util.GSON.toJsonTree(value));
      return this;
    }

    /**
     * Add a key and {@code String} to the {@code Properties}.
     * @param key The {@code Properties} key.
     * @param value The corresponding value.
     * @return The {@code Builder} to be chainable.
     */
    public Builder put(String key, String value) {
      attributes.put(
          key, 
          Util.GSON.toJsonTree(value, String.class));
      return this;
    }

    /**
     * Add a {@code Map<String, Object>} to the {@code Properties}.
     * @param attributes The {@code Properties}
     * @return The {@code Builder} to be chainable.
     */
    public Builder putAll(Map<String, JsonElement> attributes) {
      for (String key : attributes.keySet()) {
        this.attributes.put(
            key, 
            attributes.get(key));
      }

      return this;
    }
    
    /**
     * Set the title.
     * @param title The title.
     * @return The {@code Builder} to be chainable.
     */
    public Builder title(String title) {
      attributes.put(
          Attributes.TITLE.toLowerCase(), 
          Util.GSON.toJsonTree(title, String.class));
      return this;
    }

  }
  
  /**
   * Attribute keys.
   * 
   * @author Brandon Clayton
   */
  private enum Attributes {
    ID,
    TITLE;

    /**
     * Return a lower case {@code String}.
     * @return The lower case {@code String}.
     */
    String toLowerCase() {
      return name().toLowerCase();
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
