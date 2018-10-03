package gov.usgs.earthquake.nshmp.geo.json;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import com.google.common.base.CaseFormat;
import com.google.common.base.Converter;
import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import gov.usgs.earthquake.nshmp.geo.Location;
import gov.usgs.earthquake.nshmp.geo.LocationList;

final class Util {

  /**
   * A converter for use with enums whose serialized form is a
   * {@link CaseFormat} other than {@code UPPER_UNDERSCORE}. This method assumes
   * the values of any supplied enum are declared using UPPER_UNDERSCORE;
   * results are not guaraneteed if they are not.
   */
  static <E extends Enum<E>> Converter<E, String> enumStringConverter(
      Class<E> enumType,
      CaseFormat toStringFormat) {

    return new EnumStringConverter<>(
        checkNotNull(enumType),
        checkNotNull(toStringFormat));
  }

  private static final class EnumStringConverter<E extends Enum<E>> extends Converter<E, String> {

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

  /* Serialize using the enum Converter. */
  static final class Serializer<E extends Enum<E>> implements JsonSerializer<E> {

    private final Converter<E, String> converter;

    Serializer(Converter<E, String> converter) {
      this.converter = converter;
    }

    @Override
    public JsonElement serialize(
        E src,
        Type typeOfSrc,
        JsonSerializationContext context) {

      return new JsonPrimitive(converter.convert(src));
    }
  }

  /* Deserialize using the enum Converter. */
  static final class Deserializer<E extends Enum<E>> implements JsonDeserializer<E> {

    private final Converter<String, E> converter;

    Deserializer(Converter<E, String> converter) {
      this.converter = converter.reverse();
    }

    @Override
    public E deserialize(
        JsonElement json,
        Type typeOfT,
        JsonDeserializationContext context)
        throws JsonParseException {

      return converter.convert(json.getAsString());
    }
  }

  /* Custom single line formatting of double[] coordinate arrays. */
  static final class ArrayAdapterFactory implements TypeAdapterFactory {
    @SuppressWarnings("unchecked")
    @Override
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
      if (double[].class.isAssignableFrom(type.getRawType())) {
        final TypeAdapter<double[]> delegate =
            (TypeAdapter<double[]>) GeoJson.GSON.getDelegateAdapter(this, type);

        return (TypeAdapter<T>) new TypeAdapter<double[]>() {
          @Override
          public void write(JsonWriter out, double[] array) throws IOException {
            out.jsonValue(Arrays.toString(array));
          }

          @Override
          public double[] read(JsonReader in) throws IOException {
            return delegate.read(in);
          }
        }.nullSafe();
      }
      return null;
    }
  }

  // TODO clean
  public static void main(String[] args) {
    String geojson = GeoJson.builder()
        .add(Feature.point(Location.create(34, -117))
            .id("featureId")
            .properties(ImmutableMap.of(
                "title", "Feature Title",
                "id", 1,
                "color", "#ff0080")))
        .add(Feature.polygon(
            LocationList.create(
                Location.create(34, -117),
                Location.create(35, -118),
                Location.create(37, -116),
                Location.create(38, -117)))
            .id(3))
        .toJson();
    System.out.println(geojson);
    
    FeatureCollection fc = GeoJson.fromJson(geojson);
    System.out.println(fc.bbox);
    System.out.println(fc.features.get(0).properties.get("id"));
    
    Path path = Paths.get("../nshmp-haz-catalogs/2018/zones/SSCn.geojson");
    System.out.println(path.toAbsolutePath());
    fc = GeoJson.fromJson(path);
    Object obj = fc.features.get(0).properties.get("mMax");
    List<?> pp1 = (List<?>) obj;
    System.out.println(pp1.get(0).getClass());
    System.out.println(obj.getClass());
    /*
     * in the zones case, mmax deserializes to and array of 
     */
   
  }

}
