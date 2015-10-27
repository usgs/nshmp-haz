package org.opensha2.geo;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.Math.PI;
import static org.opensha2.data.Data.checkInRange;

import org.opensha2.data.Data;

import com.google.common.collect.Range;

/**
 * Constants and utility methods related to geographic calculations.
 * 
 * @author Peter Powers
 */
public class GeoTools {

	/**
	 * The Authalic mean radius (A<subscript>r</subscript>) of the earth
	 * [6371.0072 km] (see <a
	 * href="http://en.wikipedia.org/wiki/Earth_radius#Authalic_radius"
	 * target="_blank">Wikipedia</a>).
	 */
	public static final double EARTH_RADIUS_MEAN = 6371.0072;

	/**
	 * The equatorial radius of the earth [6378.1370 km] (see <a
	 * href="http://en.wikipedia.org/wiki/Earth_radius#Equatorial_radius"
	 * target="_blank">Wikipedia</a>) as derived from the WGS-84 ellipsoid.
	 */
	public static final double EARTH_RADIUS_EQUATORIAL = 6378.1370;

	/**
	 * The polar radius of the earth [6356.7523 km] (see <a
	 * href="http://en.wikipedia.org/wiki/Earth_radius#Polar_radius"
	 * target="_blank">Wikipedia</a>) as derived from the WGS-84 ellipsoid.
	 */
	public static final double EARTH_RADIUS_POLAR = 6356.7523;

	/** Minimum latitude value (-90°). */
	public static final double MIN_LAT = -90.0;

	/** Maximum latitude value (90°). */
	public static final double MAX_LAT = 90.0;

	/** Minimum longitude value (-180°). */
	public static final double MIN_LON = -180.0;

	// TODO test if and which distance calcs can handle
	// this higher MAX_LON
	/** Maximum longitude value (180°). */
	public static final double MAX_LON = 360.0;

	/**
	 * Minimum allowed earthquake depth value (-5 km) following the
	 * positive-down depth convention of seismology. TODO move to eq related
	 * class
	 */
	public static final double MIN_DEPTH = -5.0;

	/**
	 * Maximum allowed earthquake depth value (700 km) following the
	 * positive-down depth convention of seismology. TODO move to eq related
	 * class
	 */
	public static final double MAX_DEPTH = 700.0;

	private static final Range<Double> latRange = Range.closed(MIN_LAT, MAX_LAT);
	private static final Range<Double> lonRange = Range.closed(MIN_LON, MAX_LON);
	private static final Range<Double> depthRange = Range.closed(MIN_DEPTH, MAX_DEPTH);

	/** Conversion multiplier for degrees to radians */
	public static final double TO_RAD = Math.toRadians(1.0);

	/** Conversion multiplier for radians to degrees */
	public static final double TO_DEG = Math.toDegrees(1.0);

	/** Convenience constant for 2 * PI */
	public static final double TWOPI = 2 * PI;

	/** Convenience constant for PI / 2 */
	public static final double PI_BY_2 = PI / 2;

	/** Convenience constant for arcseconds per degree (3600). */
	public static final double SECONDS_PER_DEGREE = 3600;

	/** Convenience constant for arcminutes per degree (60). */
	public static final double MINUTES_PER_DEGREE = 60;

	/**
	 * Verifies that a latitude value is between {@code MIN_LAT} and
	 * {@code MAX_LAT} (inclusive). Method returns the supplied value and can be
	 * used inline.
	 * 
	 * @param lat to validate
	 * @return the supplied latitude
	 * @throws IllegalArgumentException if {@code lat} is out of range
	 * @see Data#checkInRange(Range, String, double)
	 */
	public static double validateLat(double lat) {
		return checkInRange(latRange, "Latitude", lat);
	}

	/**
	 * Verifies that an array of latitude values are between {@code MIN_LAT} and
	 * {@code MAX_LAT} (inclusive). Method returns the supplied values and can
	 * be used inline.
	 * 
	 * @param lats to validate
	 * @return the supplied latitude values
	 * @throws IllegalArgumentException if any value is out of range
	 * @see Data#checkInRange(Range, String, double...)
	 */
	public static double[] validateLats(double... lats) {
		return checkInRange(latRange, "Latitude", lats);
	}

	/**
	 * Verifies that a longitude value is between {@code MIN_LON} and
	 * {@code MAX_LON} (inclusive). Method returns the supplied value and can be
	 * used inline.
	 * 
	 * @param lon to validate
	 * @return the supplied longitude
	 * @throws IllegalArgumentException if {@code lon} is out of range
	 * @see Data#checkInRange(Range, String, double)
	 */
	public static double validateLon(double lon) {
		return checkInRange(lonRange, "Longitude", lon);
	}

	/**
	 * Verifies that an array of longitude values are between {@code MIN_LON}
	 * and {@code MAX_LON} (inclusive). Method returns the supplied values and
	 * can be used inline.
	 * 
	 * @param lons to validate
	 * @return the supplied longitude values
	 * @throws IllegalArgumentException if any value is out of range
	 * @see Data#checkInRange(Range, String, double...)
	 */
	public static double[] validateLons(double... lons) {
		return checkInRange(lonRange, "Longitude", lons);
	}

	/**
	 * Verifies that a depth value is between {@code MIN_DEPTH} and
	 * {@code MAX_DEPTH} (inclusive). Method returns the supplied value and can
	 * be used inline.
	 * 
	 * @param depth to validate
	 * @return the supplied depth
	 * @throws IllegalArgumentException if {@code depth} is out of range
	 * @see Data#checkInRange(Range, String, double)
	 */
	public static double validateDepth(double depth) {
		return checkInRange(depthRange, "Depth", depth);
	}

	/**
	 * Verifies that an array of depth values are between {@code MIN_DEPTH} and
	 * {@code MAX_DEPTH} (inclusive). Method returns the supplied values and can
	 * be used inline.
	 * 
	 * @param depths to validate
	 * @return the supplied depth values
	 * @throws IllegalArgumentException if any value is out of range
	 * @see Data#checkInRange(Range, String, double...)
	 */
	public static double[] validateDepths(double... depths) {
		return checkInRange(depthRange, "Depth", depths);
	}

	/**
	 * Returns the radius of the earth at the latitude of the supplied
	 * <code>Location</code> (see <a
	 * href="http://en.wikipedia.org/wiki/Earth_radius#Authalic_radius"
	 * target="_blank">Wikipedia</a> for source).
	 * 
	 * @param p the <code>Location</code> at which to compute the earth's radius
	 * @return the earth's radius at the supplied <code>Location</code>
	 */
	public static double radiusAtLocation(Location p) {
		checkNotNull(p, "Supplied location is null");
		double cosL = Math.cos(p.latRad());
		double sinL = Math.sin(p.latRad());
		double C1 = cosL * EARTH_RADIUS_EQUATORIAL;
		double C2 = C1 * EARTH_RADIUS_EQUATORIAL;
		double C3 = sinL * EARTH_RADIUS_POLAR;
		double C4 = C3 * EARTH_RADIUS_POLAR;
		return Math.sqrt((C2 * C2 + C4 * C4) / (C1 * C1 + C3 * C3));
	}

	/**
	 * Returns the number of degrees of latitude per km at a given
	 * <code>Location</code>. This can be used to convert between km-based and
	 * degree-based grid spacing. The calculation takes into account the shape
	 * of the earth (oblate spheroid) and scales the conversion accordingly.
	 * 
	 * @param p the <code>Location</code> at which to conversion value
	 * @return the number of decimal degrees latitude per km at a given
	 *         <code>Location</code>
	 * @see #radiusAtLocation(Location)
	 */
	public static double degreesLatPerKm(Location p) {
		return TO_DEG / radiusAtLocation(checkNotNull(p));
	}

	/**
	 * Returns the number of degrees of longitude per km at a given
	 * <code>Location</code>. This can be used to convert between km-based and
	 * degree-based grid spacing. The calculation scales the degrees longitude
	 * per km at the equator by the cosine of the supplied latitude.
	 * (<i>Note</i>: The values returned are not based on the radius of
	 * curvature of the earth at the supplied location.)
	 * 
	 * @param p the <code>Location</code> at which to conversion value
	 * @return the number of decimal degrees longitude per km at a given
	 *         <code>Location</code>
	 */
	public static double degreesLonPerKm(Location p) {
		return TO_DEG / (EARTH_RADIUS_EQUATORIAL * Math.cos(checkNotNull(p).latRad()));
	}

	/**
	 * Converts arcseconds to decimal degrees.
	 * @param seconds value to convert
	 * @return the equivalent number of decimal degrees
	 */
	public static double secondsToDeg(double seconds) {
		return seconds / SECONDS_PER_DEGREE;
	}

	/**
	 * Convert arcminutes to decimal degrees.
	 * @param minutes value to convert
	 * @return the equivalent number of decimal degrees
	 */
	public static double minutesToDeg(double minutes) {
		return minutes / MINUTES_PER_DEGREE;
	}

	/**
	 * Convert {@code degrees} and decimal {@code minutes} to decimal degrees.
	 * 
	 * @param degrees
	 * @param minutes
	 */
	public static double toDecimalDegrees(double degrees, double minutes) {
		return (degrees < 0) ? (degrees - minutesToDeg(minutes))
			: (degrees + minutesToDeg(minutes));
	}

}
