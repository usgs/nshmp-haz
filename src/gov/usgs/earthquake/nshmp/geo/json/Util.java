package gov.usgs.earthquake.nshmp.geo.json;

import static com.google.common.base.Preconditions.checkNotNull;

import java.lang.reflect.Type;

import com.google.common.base.CaseFormat;
import com.google.common.base.Converter;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import gov.usgs.earthquake.nshmp.internal.TextUtils;

final class Util {

  private Util() {}

  // TODO move to TextUtils and elsewhere
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

    final Class<E> type;
    final Converter<String, String> delegate;

    private EnumStringConverter(Class<E> type, CaseFormat stringFormat) {
      this.type = type;
      this.delegate = CaseFormat.UPPER_UNDERSCORE.converterTo(stringFormat);
    }

    @Override
    protected String doForward(E e) {
      return delegate.convert(e.name());
    }

    @Override
    protected E doBackward(String s) {
      return Enum.valueOf(type, delegate.reverse().convert(s));
    }
  }

  /* Serialize using the enum Converter. */
  static final class Serializer<E extends Enum<E>> implements
      JsonSerializer<E>,
      JsonDeserializer<E> {

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

    @Override
    public E deserialize(
        JsonElement json,
        Type typeOfT,
        JsonDeserializationContext context)
        throws JsonParseException {

      return converter.reverse().convert(json.getAsString());
    }

  }

  /* Reformats point arays onto single line and appends newline character */
  static String cleanPoints(String s) {
    return s.replaceAll("\\[\\s+([-\\d])", "[$1")
        .replaceAll(",\\s+([-\\d])", ", $1")
        .replaceAll("(\\d)\\s+\\]", "$1]")
        .replaceAll("}\\Z", "}" + TextUtils.NEWLINE);
  }
}
