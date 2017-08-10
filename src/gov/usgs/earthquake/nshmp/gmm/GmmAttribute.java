package gov.usgs.earthquake.nshmp.gmm;

import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_UNDERSCORE;

import com.google.common.base.CaseFormat;

/**
 * Ground motion model (Gmm) XML attributes.
 * @author Peter Powers
 */
@SuppressWarnings("javadoc")
public enum GmmAttribute {

  ID,
  WEIGHT,

  VALUES,
  WEIGHTS,

  MAX_DISTANCE;

  /**
   * Returns a {@code CaseFormat#LOWER_CAMEL} {@code String} representation of
   * this {@code GmmAttribute}.
   * @see CaseFormat
   */
  @Override
  public String toString() {
    return UPPER_UNDERSCORE.to(LOWER_CAMEL, name());
  }

  /**
   * Converts supplied {@code String} to the equivalent {@code GmmAttribute}.
   * Method expects a {@code String} with {@code CaseFormat#LOWER_CAMEL}
   * @param s {@code String} to convert
   * @return the corresponding {@code GmmAttribute}
   * @see CaseFormat
   * @throws IllegalArgumentException if supplied {@code String} is incorrectly
   *         formatted or no matching {@code GmmAttribute} exists
   */
  public static GmmAttribute fromString(String s) {
    return valueOf(LOWER_CAMEL.to(UPPER_UNDERSCORE, s));
  }

}
