package org.opensha2.geo;

import static org.opensha2.data.Data.checkInRange;

import org.opensha2.util.Maths;

import com.google.common.collect.Range;

/**
 * Constants and utility methods pertaining to geographic coordinates.
 *
 * @author Peter Powers
 */
public class Coordinates {

  /**
   * The Authalic mean radius (A<subscript>r</subscript>) of the earth:
   * {@code 6371.0072 km}.
   * 
   * @see <a href="http://en.wikipedia.org/wiki/Earth_radius#Authalic_radius"
   *      target="_blank">Wikipedia</a>
   */
  public static final double EARTH_RADIUS_MEAN = 6371.0072;

  /**
   * The equatorial radius of the earth as derived from the WGS-84 ellipsoid:
   * {@code 6378.1370 km}.
   * 
   * @see <a href="http://en.wikipedia.org/wiki/Earth_radius#Equatorial_radius"
   *      target="_blank">Wikipedia</a>
   */
  public static final double EARTH_RADIUS_EQUATORIAL = 6378.1370;

  /**
   * The polar radius of the earth as derived from the WGS-84 ellipsoid:
   * {@code 6356.7523 km]}.
   * 
   * @see <a href="http://en.wikipedia.org/wiki/Earth_radius#Polar_radius"
   *      target="_blank">Wikipedia</a>
   */
  public static final double EARTH_RADIUS_POLAR = 6356.7523;

  /** Supported latitudes: {@code [-90..90]°}. */
  public static final Range<Double> LAT_RANGE = Range.closed(-90.0, 90.0);

  /** Supported longitudes: {@code (-360..360)°}. */
  public static final Range<Double> LON_RANGE = Range.open(-360.0, 360.0);

  /** Constant for arcminutes per degree: {@code 60″}. */
  public static final double MINUTES_PER_DEGREE = 60;

  /** Constant for arcseconds per degree: {@code 3600′}. */
  public static final double SECONDS_PER_DEGREE = 3600;

  /**
   * Ensure that {@code -90° ≤ latitude ≤ 90°}.
   *
   * @param latitude to validate
   * @return the validated latitude
   * @throws IllegalArgumentException if {@code latitude} is outside the range
   *         {@code [-90..90]°}
   */
  public static double checkLatitude(double latitude) {
    return checkInRange(LAT_RANGE, "Latitude", latitude);
  }

  /**
   * Ensure that {@code -360° < longitude < 360°}.
   *
   * @param longitude to validate
   * @return the validated longitude
   * @throws IllegalArgumentException if {@code longitude} is outside the range
   *         {@code (-360..360)°}
   */
  public static double checkLongitude(double longitude) {
    return checkInRange(LON_RANGE, "Longitude", longitude);
  }

  /**
   * Return the radius of the earth at the latitude of the supplied
   * <code>Location</code> (see <a
   * href="http://en.wikipedia.org/wiki/Earth_radius#Authalic_radius"
   * target="_blank">Wikipedia</a> for source).
   *
   * @param location at which to compute the earth's radius
   * @return the earth's radius at the supplied {@code location}
   */
  public static double radiusAtLocation(Location location) {
    double cosL = Math.cos(location.latRad());
    double sinL = Math.sin(location.latRad());
    double C1 = cosL * EARTH_RADIUS_EQUATORIAL;
    double C2 = C1 * EARTH_RADIUS_EQUATORIAL;
    double C3 = sinL * EARTH_RADIUS_POLAR;
    double C4 = C3 * EARTH_RADIUS_POLAR;
    return Math.sqrt((C2 * C2 + C4 * C4) / (C1 * C1 + C3 * C3));
  }

  /**
   * Return a conversion factor for the number of degrees of latitude per km at
   * a given {@code location}. This can be used to convert between km-based and
   * degree-based grid spacing. The calculation takes into account the shape of
   * the earth (oblate spheroid) and scales the conversion accordingly.
   *
   * @param location at which to compute conversion value
   * @return the number of decimal degrees latitude per km at the supplied
   *         {@code location}
   * @see #radiusAtLocation(Location)
   */
  public static double degreesLatPerKm(Location location) {
    return Maths.TO_DEG / radiusAtLocation(location);
  }

  /**
   * Return a conversion factor for the number of degrees of longitude per km at
   * a given {@code location}. This can be used to convert between km-based and
   * degree-based grid spacing. The calculation scales the degrees longitude per
   * km at the equator by the cosine of the supplied latitude. (<i>Note</i>: The
   * values returned are not based on the radius of curvature of the earth at
   * the supplied location.)
   *
   * @param location at which to compute conversion value
   * @return the number of decimal degrees longitude per km at the supplied
   *         {@code location}
   */
  public static double degreesLonPerKm(Location location) {
    return Maths.TO_DEG / (EARTH_RADIUS_EQUATORIAL * Math.cos(location.latRad()));
  }

  /**
   * Convert arc{@code seconds} to decimal degrees.
   * 
   * @param seconds to convert
   * @return the equivalent number of decimal degrees
   */
  public static double secondsToDeg(double seconds) {
    return seconds / SECONDS_PER_DEGREE;
  }

  /**
   * Convert arc{@code minutes} to decimal degrees.
   * 
   * @param minutes to convert
   * @return the equivalent number of decimal degrees
   */
  public static double minutesToDeg(double minutes) {
    return minutes / MINUTES_PER_DEGREE;
  }

  /**
   * Convert {@code degrees} and decimal {@code minutes} to decimal degrees.
   *
   * @param degrees to convert
   * @param minutes to convert
   * @return the equivalent number of decimal degrees
   */
  public static double toDecimalDegrees(double degrees, double minutes) {
    return (degrees < 0) ? (degrees - minutesToDeg(minutes))
        : (degrees + minutesToDeg(minutes));
  }

}
