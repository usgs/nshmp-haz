package gov.usgs.earthquake.nshmp.util;

/**
 * Interface indicating an object has a name, usually prettified for display and
 * distinct from {@code toString()}, which often provides a more complete
 * description of an object, or a raw data representation. This method should
 * never return {@code null} or an empty {@code String}.
 *
 * @author Peter Powers
 */
public interface Named {

  /**
   * Returns an object's display name.
   * @return the name
   */
  String name();

}
