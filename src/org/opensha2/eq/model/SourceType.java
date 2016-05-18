package org.opensha2.eq.model;

import static com.google.common.base.CaseFormat.UPPER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_UNDERSCORE;

import com.google.common.base.CaseFormat;

/**
 * Identifier for different earthquake {@link Source} types. These are declared
 * in a preferred order of iteration, with potentially longer running source
 * types coming first.
 *
 * @author Peter Powers
 */
public enum SourceType {

  /** Fault system source type. */
  SYSTEM,

  /** Area source type. */
  AREA,

  /** Gridded (background) seismicity source type. */
  GRID,

  /** Subduction intraslab source type. */
  SLAB,

  /** Subduction interface source type. */
  INTERFACE,

  /** Finite fault source type. */
  FAULT,

  /** Cluster source type. */
  CLUSTER;

  /**
   * Returns a {@code CaseFormat#UPPER_CAMEL} {@code String} representation of
   * this {@code SourceType}.
   */
  @Override
  public String toString() {
    return UPPER_UNDERSCORE.to(UPPER_CAMEL, name());
  }

  /**
   * Converts supplied {@code String} to equivalent {@code SourceType}. Method
   * expects a {@code String} with {@code CaseFormat#UPPER_CAMEL}
   * @param s {@code String} to convert
   * @return the corresponding {@code SourceType}
   * @see CaseFormat
   * @throws IllegalArgumentException if supplied {@code String} is incorrectly
   *         formatted or no matching {@code SourceType} exists
   */
  public static SourceType fromString(String s) {
    return valueOf(UPPER_CAMEL.to(UPPER_UNDERSCORE, s));
  }

}
