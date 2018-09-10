package gov.usgs.earthquake.nshmp.geo.json;

import static com.google.common.base.CaseFormat.UPPER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_UNDERSCORE;
import static com.google.common.base.Preconditions.checkNotNull;

import java.lang.reflect.Type;

import com.google.common.base.CaseFormat;
import com.google.common.base.Converter;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

/**
 * The nine case-sensitive GeoJson types: 
 *  <ul> 
 *    <li> FeatureCollection </li> 
 *    <li> Feature </li> 
 *    <li> Point </li> 
 *    <li> MultiPoint </li> 
 *    <li> Polygon </li>
 *    <li> MultiPolygon </li> 
 *    <li> LineString </li> 
 *    <li> MultiLineString </li> 
 *    <li> GeometryCollection </li> 
 *  </ul>
 * 
 * @author Brandon Clayton
 */
public enum GeoJsonType {
  FEATURE,
  FEATURE_COLLECTION,
  
  /* GeoJson geometry type */
  GEOMETRY_COLLECTION,
  LINE_STRING,
  MULTI_LINE_STRING,
  MULTI_POINT,
  MULTI_POLYGON,
  POINT,
  POLYGON;

  /**
   * Return a upper camel case {@code String}.
   * @return The upper camel case {@code String}.
   */
  public String toUpperCamelCase() {
    return UPPER_UNDERSCORE.to(UPPER_CAMEL, name());
  }
 
  /**
   * Return a {@code GeoJsonType} from a {@code String}.
   * The {@code String} should be in upper camel case as per GeoJson spec.
   * 
   * @param type The string representing the {@code GeoJsonType}.
   * @return The {@code GeoJsonType}.
   */
  public static GeoJsonType getEnum(String type) {
    for (GeoJsonType jsonType : GeoJsonType.values()) {
      if (jsonType.toUpperCamelCase().equals(type)) {
        return jsonType;
      }
    }
    throw new IllegalStateException("Could not find type: " + type);
  }

  /**
   * A converter for use with enums whose serialized form is a
   * {@link CaseFormat} other than {@code UPPER_UNDERSCORE}. This method assumes
   * the values of any supplied enum are declared using UPPER_UNDERSCORE;
   * results are not guaraneteed if they are not.
   */
  public static <E extends Enum<E>> Converter<E, String> enumStringConverter(
      Class<E> enumType,
      CaseFormat toStringFormat) {

    return new EnumStringConverter<>(
        checkNotNull(enumType),
        checkNotNull(toStringFormat));
  }

  static final class EnumStringConverter<E extends Enum<E>> extends Converter<E, String> {

    final Class<E> enumType;
    final CaseFormat enumFormat = CaseFormat.UPPER_UNDERSCORE;
    final CaseFormat stringFormat;

    private EnumStringConverter(Class<E> enumType, CaseFormat stringFormat) {
      this.enumType = enumType;
      this.stringFormat = stringFormat;
    }

    @Override
    protected String doForward(E e) {
      return enumFormat.to(stringFormat, e.name());
    }

    @Override
    protected E doBackward(String s) {
      return Enum.valueOf(enumType, stringFormat.to(enumFormat, s));
    }
  }

  /* Enum.toString() serializer. */
  static final class Serializer<E extends Enum<E>> implements JsonSerializer<E> {

    @Override
    public JsonElement serialize(
        E src,
        Type typeOfSrc,
        JsonSerializationContext context) {

      return new JsonPrimitive(src.toString());
    }
  }

}
