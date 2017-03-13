package org.opensha2.geo;

import static com.google.common.base.Preconditions.checkNotNull;

import static org.opensha2.geo.Coordinates.TO_DEG;
import static org.opensha2.geo.Coordinates.TO_RAD;
import static org.opensha2.geo.Coordinates.checkDepth;
import static org.opensha2.geo.Coordinates.checkLatitude;
import static org.opensha2.geo.Coordinates.checkLongitude;

import org.opensha2.internal.Parsing;
import org.opensha2.internal.Parsing.Delimiter;

import com.google.common.base.Converter;
import com.google.common.collect.FluentIterable;
import com.google.common.primitives.Doubles;

import java.util.List;
import java.util.Objects;

/**
 * A {@code Location} represents a point with reference to the earth's
 * ellipsoid. It is expressed in terms of latitude, longitude, and depth. As in
 * seismology, the convention adopted in here is for depth to be positive-down,
 * always. Locations may be defined using longitude values in the range: [-180°,
 * 360°]. Location instances are immutable.
 *
 * <p>Note that although static factory methods take arguments in the order:
 * {@code [lat, lon, depth]}, {@code String} representations of a location are
 * in the order: {@code [lon, lat, depth]}, consistent with KML, GeoJSON, and
 * other digital coordinate formats that match standard plotting coordinate
 * order: {@code [x, y, z]}.
 *
 * <p>For computational convenience, latitude and longitude values are converted
 * and stored internally in radians. Special {@code get***Rad()} methods are
 * provided to access this native format.
 *
 * @author Peter Powers
 */
public final class Location implements Comparable<Location> {

  private final double lat;
  private final double lon;
  private final double depth;

  private Location(double lat, double lon, double depth) {
    this.lat = checkLatitude(lat) * TO_RAD;
    this.lon = checkLongitude(lon) * TO_RAD;
    this.depth = checkDepth(depth);
  }

  /**
   * Create a new {@code Location} with the supplied latitude and longitude and
   * a depth of 0 km.
   *
   * @param lat latitude in decimal degrees
   * @param lon longitude in decimal degrees
   * @throws IllegalArgumentException if any supplied values are out of range
   * @see Coordinates
   */
  public static Location create(double lat, double lon) {
    return create(lat, lon, 0);
  }

  /**
   * Create a new {@code Location} with the supplied latitude, longitude, and
   * depth.
   *
   * @param lat latitude in decimal degrees
   * @param lon longitude in decimal degrees
   * @param depth in km (positive down)
   * @throws IllegalArgumentException if any supplied values are out of range
   * @see Coordinates
   */
  public static Location create(double lat, double lon, double depth) {
    return new Location(lat, lon, depth);
  }

  /**
   * Generate a new {@code Location} by parsing the supplied {@code String}.
   * Method is intended for use with the result of {@link #toString()}.
   *
   * @param s string to parse
   * @throws NumberFormatException if {@code s} is unparseable
   * @throws IndexOutOfBoundsException if {@code s} contains fewer than 3
   *         comma-separated values; any additional values in the suppied string
   *         are ignored
   * @see #toString()
   * @see #stringConverter()
   */
  public static Location fromString(String s) {
    return StringConverter.INSTANCE.reverse().convert(s);
  }

  /**
   * Returns the depth of this {@code Location} in km.
   */
  public double depth() {
    return depth;
  }

  /**
   * The latitude of this {@code Location} in decimal degrees.
   */
  public double lat() {
    return lat * TO_DEG;
  }

  /**
   * The longitude of this {@code Location} in decimal degrees.
   */
  public double lon() {
    return lon * TO_DEG;
  }

  /**
   * The latitude of this {@code Location} in radians.
   */
  public double latRad() {
    return lat;
  }

  /**
   * The longitude of this {@code Location} in radians.
   */
  public double lonRad() {
    return lon;
  }

  /**
   * Return a KML compatible tuple: 'lon,lat,depth' (no spaces).
   * @see #fromString(String)
   * @see #stringConverter()
   */
  @Override
  public String toString() {
    return stringConverter().convert(this);
  }

  /**
   * Return a {@link Converter} that converts between {@code Location}s and
   * {@code String}s.
   *
   * <p>Calls to {@code converter.reverse().convert(String)} will throw a
   * {@code NumberFormatException} if the values in the supplied string are
   * unparseable; or an {@code IndexOutOfBoundsException} if the supplied string
   * contains fewer than 3 comma-separated values; any additional values in the
   * suppied string are ignored.
   */
  public static Converter<Location, String> stringConverter() {
    return StringConverter.INSTANCE;
  }

  private static final class StringConverter extends Converter<Location, String> {

    static final StringConverter INSTANCE = new StringConverter();
    static final String FORMAT = "%.5f,%.5f,%.5f";

    @Override
    protected String doForward(Location loc) {
      return String.format(FORMAT, loc.lon(), loc.lat(), loc.depth());
    }

    @Override
    protected Location doBackward(String s) {
      List<Double> values = FluentIterable
          .from(Parsing.split(checkNotNull(s), Delimiter.COMMA))
          .transform(Doubles.stringConverter())
          .toList();
      return create(values.get(1), values.get(0), values.get(2));
    }
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (!(obj instanceof Location)) {
      return false;
    }
    Location loc = (Location) obj;
    return this.lat == loc.lat &&
        this.lon == loc.lon &&
        this.depth == loc.depth;
  }

  @Override
  public int hashCode() {
    return Objects.hash(lat, lon, depth);
  }

  /**
   * Compare this {@code Location} to another and sort first by latitude, then
   * by longitude. When sorting a list of {@code Location}s, the resultant
   * ordering is left-to-right, bottom-to-top.
   *
   * @param loc {@code Location} to compare {@code this} to
   * @return a negative integer, zero, or a positive integer if this
   *         {@code Location} is less than, equal to, or greater than the
   *         specified {@code Location}.
   */
  @Override
  public int compareTo(Location loc) {
    double d = (lat == loc.lat) ? lon - loc.lon : lat - loc.lat;
    return (d != 0) ? (d < 0) ? -1 : 1 : 0;
  }

}
