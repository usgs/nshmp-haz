package org.opensha2.util;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.StandardSystemProperty;
import com.google.common.base.Strings;

/**
 * Miscellaneous {@code String} utilities.
 *
 * @author Peter Powers
 */
public class TextUtils {

  /** System specific newline string. */
  public static final String NEWLINE = StandardSystemProperty.LINE_SEPARATOR.value();

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

}
