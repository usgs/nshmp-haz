package gov.usgs.earthquake.nshmp.internal;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;

import com.google.common.base.CaseFormat;
import com.google.common.base.Converter;
import com.google.common.base.StandardSystemProperty;
import com.google.common.base.Strings;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

/**
 * Miscellaneous {@code String} utilities.
 *
 * @author Peter Powers
 */
public class TextUtils {

  /** System specific newline string. */
  public static final String NEWLINE = StandardSystemProperty.LINE_SEPARATOR.value();

  /** Null string ("null"). */
  public static final String NULL = "null";

  /** The column on which to align values in a log entry. */
  public static final int LOG_VALUE_COLUMN = 32;

  private static final int LOG_INDENT_SIZE = 8;

  /** A newline plus the number of spaces to indent multiline log entries. */
  public static final String LOG_INDENT = NEWLINE + Strings.repeat(" ", LOG_INDENT_SIZE);

  /**
   * Verifies that the supplied {@code String} is neither {@code null} or empty.
   * Method returns the supplied value and can be used inline.
   *
   * @param name to verify
   * @throws IllegalArgumentException if name is {@code null} or empty
   */
  public static String validateName(String name) {
    checkArgument(!Strings.nullToEmpty(name).trim().isEmpty(), "Name may not be empty or null");
    return name;
  }

  /**
   * Returns a {@code Converter} for use with enums whose serialized form is a
   * format other than {@link CaseFormat#UPPER_UNDERSCORE}. This converter
   * assumes the values of any supplied enum are declared using
   * {@code UPPER_UNDERSCORE}; results are not guaranteed if they are not.
   * 
   * @param enumType to convert
   * @param caseFormat for conversion
   */
  public static <E extends Enum<E>> Converter<E, String> enumStringConverter(
      Class<E> enumType,
      CaseFormat caseFormat) {

    return new EnumStringConverter<>(
        checkNotNull(enumType),
        checkNotNull(caseFormat));
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

  /**
   * Returns a Gson {@code TypeAdapter} (serializer, deserializer) for enum
   * fields that uses the specified {@code Converter}.
   * 
   * @param converter to use for {@code CaseFormat} conversions
   */
  public static <E extends Enum<E>> TypeAdapter<E> enumSerializer(
      Converter<E, String> converter) {
    return new EnumAdapter<>(checkNotNull(converter));
  }

  /* Serialize using the enum Converter. */
  static final class EnumAdapter<E extends Enum<E>> extends TypeAdapter<E> {

    private final Converter<E, String> converter;

    EnumAdapter(Converter<E, String> converter) {
      this.converter = converter;
    }

    @Override
    public void write(JsonWriter out, E value) throws IOException {
      out.value(converter.convert(value));
    }

    @Override
    public E read(JsonReader in) throws IOException {
      return converter.reverse().convert(in.nextString());
    }
  }

}
