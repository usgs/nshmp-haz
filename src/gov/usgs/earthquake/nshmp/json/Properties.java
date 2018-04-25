package gov.usgs.earthquake.nshmp.json;

import java.util.HashMap;
import java.util.Map;

import static com.google.common.base.Strings.isNullOrEmpty;

/**
 * Create {@code Properties} for {@link Feature}s. 
 * <br>
 * 
 * The {@code Properties} is simply a {@code Map<String, Object>} to allow
 * setting of both GeoJson spec and custom properties. 
 * <br><br>
 * 
 * The {@link Builder} must be used to set the properties. See {@link Builder}
 * for example.
 * 
 * @author Brandon Clayton
 */
public class Properties {
  /** The {@code Properties} */
  public Map<String, Object> attributes;

  private Properties(Builder builder) {
    this.attributes = builder.attributes;
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
    private Map<String, Object> attributes = new HashMap<>();

    private Builder() {}

    /**
     * Return a new instance of {@link Properties}. 
     * <br> 
     * The "title" and "id" property must be set before calling {@link #build()}. 
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
      if (isNullOrEmpty((String) this.attributes.get(Attributes.TITLE.toLowerCase())) ||
          isNullOrEmpty((String) this.attributes.get(Attributes.ID.toLowerCase()))) {
        throw new IllegalStateException("Title and id fields can not be empty");
      }
      return new Properties(this);
    }

    /**
     * Set the id.
     * @param id The id.
     * @return The {@code Builder} to be chainable.
     */
    public Builder id(String id) {
      this.attributes.put(Attributes.ID.toLowerCase(), id);
      return this;
    }

    /**
     * Add a key and {@code double} to the {@code Properties}.
     * @param key The {@code Properties} key.
     * @param value The corresponding value.
     * @return The {@code Builder} to be chainable.
     */
    public Builder put(String key, double value) {
      this.attributes.put(key, value);
      return this;
    }

    /**
     * Add a key and {@code int} to the {@code Properties}.
     * @param key The {@code Properties} key.
     * @param value The corresponding value.
     * @return The {@code Builder} to be chainable.
     */
    public Builder put(String key, int value) {
      this.attributes.put(key, value);
      return this;
    }

    /**
     * Add a key and {@code Object} to the {@code Properties}.
     * @param key The {@code Properties} key.
     * @param value The corresponding value.
     * @return The {@code Builder} to be chainable.
     */
    public Builder put(String key, Object value) {
      this.attributes.put(key, value);
      return this;
    }

    /**
     * Add a key and {@code String} to the {@code Properties}.
     * @param key The {@code Properties} key.
     * @param value The corresponding value.
     * @return The {@code Builder} to be chainable.
     */
    public Builder put(String key, String value) {
      this.attributes.put(key, value);
      return this;
    }

    /**
     * Add a {@code Map<String, Object>} to the {@code Properties}.
     * @param attributes The {@code Properties}
     * @return The {@code Builder} to be chainable.
     */
    public Builder putAll(Map<String, Object> attributes) {
      for (String key : attributes.keySet()) {
        this.attributes.put(key, attributes.get(key));
      }

      return this;
    }

    /**
     * Set the title.
     * @param title The title.
     * @return The {@code Builder} to be chainable.
     */
    public Builder title(String title) {
      this.attributes.put(Attributes.TITLE.toLowerCase(), title);
      return this;
    }

  }

  /**
   * Return a {@code String} in JSON format.
   */
  @Override
  public String toString() {
    return Util.GSON.toJson(this);
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

}
