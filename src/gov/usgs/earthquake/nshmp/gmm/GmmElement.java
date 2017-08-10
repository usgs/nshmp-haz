package gov.usgs.earthquake.nshmp.gmm;

import static com.google.common.base.CaseFormat.UPPER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_UNDERSCORE;

import com.google.common.base.CaseFormat;

/**
 * Ground motion model (Gmm) XML elements.
 * @author Peter Powers
 */
@SuppressWarnings("javadoc")
public enum GmmElement {

  GROUND_MOTION_MODELS,
  UNCERTAINTY,
  MODEL_SET,
  MODEL;

  /**
   * Returns a {@code CaseFormat#UPPER_CAMEL} {@code String} representation of
   * this {@code GmmElement}.
   */
  @Override
  public String toString() {
    return UPPER_UNDERSCORE.to(UPPER_CAMEL, name());
  }

  /**
   * Converts supplied {@code String} to equivalent {@code GmmElement}. Method
   * expects a {@code String} with {@code CaseFormat#UPPER_CAMEL}
   * @param s {@code String} to convert
   * @return the corresponding {@code GmmElement}
   * @see CaseFormat
   * @throws IllegalArgumentException if supplied {@code String} is incorrectly
   *         formatted or no matching {@code GmmElement} exists
   */
  public static GmmElement fromString(String s) {
    return valueOf(UPPER_CAMEL.to(UPPER_UNDERSCORE, s));
  }

}
