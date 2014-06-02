package org.opensha.geo;

import static java.lang.Math.PI;
import static java.lang.Math.abs;
import static java.lang.Math.atan2;
import static java.lang.Math.acos;
import static java.lang.Math.asin;
import static java.lang.Math.cos;
import static java.lang.Math.sin;
import static java.lang.Math.sqrt;
import static org.opensha.geo.Direction.NORTH;
import static org.opensha.geo.Direction.WEST;
import static org.opensha.geo.GeoTools.TWOPI;
import static org.opensha.geo.GeoTools.TO_DEG;
import static org.opensha.geo.GeoTools.TO_RAD;
import static org.opensha.geo.GeoTools.EARTH_RADIUS_MEAN;

import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.util.Collection;

import org.apache.commons.math3.util.Precision;

import com.google.common.base.Predicate;

/**
 * Static utility methods to operate on geographic {@code Location} data.
 * 
 * <p> See: <a href="http://williams.best.vwh.net/avform.htm" target="_blank">
 * Aviation Formulary</a> for formulae implemented in this class as well as <a
 * href="http://www.movable-type.co.uk/scripts/latlong.html"
 * target="_blank">Moveable Type Scripts</a> for other implementations.</p>
 * 
 * <p>Unless explicitely stated, these methods do not perform any {@code null}
 * argument checking. TODO: should this be reconsidered</p>
 * 
 * @author Peter Powers
 * @see Location
 */
public final class Locations {

	/*
	 * Developer Notes: All experimental, exploratory and test methods were
	 * moved to the LocationUtilsTest.java. On the basis of various experiments,
	 * older methods to calculate distance were replaced with updated versions,
	 * many of which leverage spherical geometry to yield more accurate results.
	 * Some 'fast' versions were updated as well. All legacy methods, however,
	 * are preserved in LocationUtilsTest.java where comparison tests can be
	 * rerun. P.Powers 3-6-2010
	 * 
	 * Most methods take Locations exclusively as arguments. This alleviates any
	 * error checking that must otherwise be performed on user supplied lat-lon
	 * values. It also alleviates the need for expensive degree-radian
	 * conversions by using radians, the native format for Locations,
	 * exclusively.
	 * 
	 * TODO: Add log warnings when 'fast' methods are being used for points that
	 * exceed some max separation.
	 */

	/* No instantiation allowed */
	private Locations() {}

	/**
	 * {@code Enum} used indicate sidedness of points with respect to a line.
	 */
	public enum Side {
		// TODO needed??
		/** Indicates a point is on the right side of a line. */
		RIGHT,
		/** Indicates a point is on the left side of a line. */
		LEFT,
		/** Indicates a point is on the a line. */
		ON;
	}

	/**
	 * Calculates the angle between two {@code Location}s using the <a
	 * href="http://en.wikipedia.org/wiki/Haversine_formula" target="_blank">
	 * Haversine</a> formula. This method properly handles values spanning
	 * &#177;180&#176;. See <a
	 * href="http://williams.best.vwh.net/avform.htm#Dist"> Aviation
	 * Formulary</a> for source. Result is returned in radians.
	 * 
	 * @param p1 the first {@code Location} point
	 * @param p2 the second {@code Location} point
	 * @return the angle between the points (in radians)
	 */
	public static double angle(Location p1, Location p2) {
		double lat1 = p1.latRad();
		double lat2 = p2.latRad();
		double sinDlatBy2 = sin((lat2 - lat1) / 2.0);
		double sinDlonBy2 = sin((p2.lonRad() - p1.lonRad()) / 2.0);
		// half length of chord connecting points
		double c = (sinDlatBy2 * sinDlatBy2) +
			(cos(lat1) * cos(lat2) * sinDlonBy2 * sinDlonBy2);
		return 2.0 * atan2(sqrt(c), sqrt(1 - c));
	}

	/**
	 * Calculates the great circle surface distance between two {@code Location}
	 * s using the Haversine formula for computing the angle between two points.
	 * For a faster, but less accurate implementation at large separations, see
	 * {@link #horzDistanceFast(Location, Location)}.
	 * 
	 * @param p1 the first {@code Location} point
	 * @param p2 the second {@code Location} point
	 * @return the distance between the points in km
	 * @see #angle(Location, Location)
	 * @see #horzDistanceFast(Location, Location)
	 */
	public static double horzDistance(Location p1, Location p2) {
		return EARTH_RADIUS_MEAN * angle(p1, p2);
	}

	/**
	 * Calculates approximate distance between two {@code Location}s. This
	 * method is about 2 orders of magnitude faster than {@code horzDistance()},
	 * but is imprecise at large distances. Method uses the latitudinal and
	 * longitudinal differences between the points as the sides of a right
	 * triangle. The longitudinal distance is scaled by the cosine of the mean
	 * latitude.
	 * 
	 * <p><b>Note:</b> This method does <i>NOT</i> support values spanning
	 * #177;180&#176; and fails where the numeric angle exceeds 180&#176;.
	 * Convert data to the 0-360&#176; interval or use
	 * {@link #horzDistance(Location, Location)} in such instances.</p>
	 * 
	 * @param p1 the first {@code Location} point
	 * @param p2 the second {@code Location} point
	 * @return the distance between the points in km
	 * @see #horzDistance(Location, Location)
	 */
	public static double horzDistanceFast(Location p1, Location p2) {
		// modified from J. Zechar:
		// calculates distance between two points, using formula
		// as specifed by P. Shebalin via email 5.8.2004
		double lat1 = p1.latRad();
		double lat2 = p2.latRad();
		double dLat = lat1 - lat2;
		double dLon = (p1.lonRad() - p2.lonRad()) * cos((lat1 + lat2) * 0.5);
		return EARTH_RADIUS_MEAN * sqrt(dLat * dLat + dLon * dLon);
	}
	
	public static void main(String[] args) {
		Location p1 = Location.create(40, 163);
		Location p2 = Location.create(40, 165);
		Location p3 = Location.create(40, 167);
		System.out.println(horzDistanceFast(p2, p1));
		System.out.println(horzDistanceFast(p2, p3));
		System.out.println(horzDistance(p2, p1));
		System.out.println(horzDistance(p2, p3));
		
	}

	/**
	 * Returns the vertical separation between two {@code Location}s. The
	 * returned value is not absolute and preserves the sign of the difference
	 * between the points.
	 * 
	 * @param p1 the first {@code Location} point
	 * @param p2 the first {@code Location} point
	 * @return the vertical separation between the points
	 */
	public static double vertDistance(Location p1, Location p2) {
		return p2.depth() - p1.depth();
	}

	/**
	 * Calculates the distance in three dimensions between two {@code Location}s
	 * using spherical geometry. Method returns the straight line distance
	 * taking into account the depths of the points. For a faster, but less
	 * accurate implementation at large separations, see
	 * {@link #linearDistanceFast(Location, Location)}.
	 * 
	 * @param p1 the first {@code Location} point
	 * @param p2 the second {@code Location} point
	 * @return the distance in km between the points
	 * @see #linearDistanceFast(Location, Location)
	 */
	public static double linearDistance(Location p1, Location p2) {
		double alpha = angle(p1, p2);
		double R1 = EARTH_RADIUS_MEAN - p1.depth();
		double R2 = EARTH_RADIUS_MEAN - p2.depth();
		double B = R1 * sin(alpha);
		double C = R2 - R1 * cos(alpha);
		return sqrt(B * B + C * C);
	}

	/**
	 * Calculates the approximate linear distance in three dimensions between
	 * two {@code Location}s. This simple and speedy implementation uses the
	 * Pythagorean theorem, treating horizontal and vertical separations as
	 * orthogonal.
	 * 
	 * <p><b>Note:</b> This method is very imprecise at large separations and
	 * should not be used for points &gt;200km apart. If an estimate of
	 * separation distance is not known in advance use
	 * {@link #linearDistance(Location, Location)} for more reliable
	 * results.</p>
	 * 
	 * <p><b>Note:</b> This method fails for values spanning &#177;180&#176;;
	 * see {@link #horzDistanceFast(Location, Location)}.</p>
	 * 
	 * @param p1 the first {@code Location} point
	 * @param p2 the second {@code Location} point
	 * @return the distance in km between the points
	 * @see #linearDistance(Location, Location)
	 */
	// TODO examine whether all uses of this method are appropriate or
	// if more accurate linearDistance() should be used instead
	public static double linearDistanceFast(Location p1, Location p2) {
		double h = horzDistanceFast(p1, p2);
		double v = vertDistance(p1, p2);
		return sqrt(h * h + v * v);
	}

	/**
	 * Computes the shortest distance between a point and a line (great-circle).
	 * that extends infinitely in both directions. Both the line and point are
	 * assumed to be at the earth's surface; the depth component of each
	 * {@code Location} is ignored. This method uses the true spherical
	 * geometric function for 'off-track distance'; See <a
	 * href="http://williams.best.vwh.net/avform.htm#XTE"> Aviation
	 * Formulary</a> for source. The sign of the result indicates which side of
	 * the supplied line {@code p3} is on (right:[+] left:[-]).
	 * 
	 * <p>This method, though more accurate over longer distances and line
	 * lengths, is up to 20x slower than
	 * {@link #distanceToLineFast(Location, Location, Location)}. However, this
	 * method returns accurate results for values spanning #177;180&#176;.</p>
	 * 
	 * <p>If the line should instead be treated as a segment such that the
	 * result will be a distance to an endpoint if {@code p3} does not project
	 * onto the segment, use
	 * {@link #distanceToSegment(Location, Location, Location)} instead.</p>
	 * 
	 * @param p1 the first {@code Location} point on the line
	 * @param p2 the second {@code Location} point on the line
	 * @param p3 the {@code Location} point for which distance will be
	 *        calculated
	 * @return the shortest distance in km between the supplied point and line
	 * @see #distanceToLineFast(Location, Location, Location)
	 * @see #distanceToSegment(Location, Location, Location)
	 */
	public static double distanceToLine(Location p1, Location p2, Location p3) {
		// angular distance
		double ad13 = angle(p1, p3);
		// delta azimuth p1 to p3 and azimuth p1 to p2
		double Daz13az12 = azimuthRad(p1, p3) - azimuthRad(p1, p2);
		// cross-track distance (in radians)
		double xtd = asin(sin(ad13) * sin(Daz13az12));
		return (abs(xtd) < TOLERANCE) ? 0.0 : xtd * EARTH_RADIUS_MEAN;
	}

	/**
	 * Computes the shortest distance between a point and a line. Both the line
	 * and point are assumed to be at the earth's surface; the depth component
	 * of each {@code Location} is ignored. This is a fast, geometric, cartesion
	 * (flat-earth approximation) solution in which longitude of the line points
	 * are scaled by the cosine of latitude; it is only appropriate for use over
	 * short distances (e.g. &lt;200 km). The sign of the result indicates which
	 * side of the supplied line {@code p3} is on (right:[+] left:[-]).
	 * 
	 * <p><b>Note:</b> This method does <i>NOT</i> support values spanning
	 * &#177;180&#176; and results for such input values are not guaranteed.
	 * Convert data to the 0-360&#176; interval or use
	 * {@link #distanceToLine(Location, Location, Location)} in such instances.</p>
	 * 
	 * <p>If the line should instead be treated as a segment such that
	 * the result will be a distance to an endpoint if {@code p3} does not
	 * project onto the segment, use
	 * {@link #distanceToSegmentFast(Location, Location, Location)} instead.</p>
	 * 
	 * @param p1 the first {@code Location} point on the line
	 * @param p2 the second {@code Location} point on the line
	 * @param p3 the {@code Location} point for which distance will be
	 *        calculated
	 * @return the shortest distance in km between the supplied point and line
	 * @see #distanceToLine(Location, Location, Location)
	 * @see #distanceToSegmentFast(Location, Location, Location)
	 */
	public static double distanceToLineFast(Location p1, Location p2,
			Location p3) {

		double lat1 = p1.latRad();
		double lat2 = p2.latRad();
		double lat3 = p3.latRad();
		double lon1 = p1.lonRad();

		// use average latitude to scale longitude
		double lonScale = cos(0.5 * lat3 + 0.25 * lat1 + 0.25 * lat2);

		// first point on line transformed to origin; others scaled by lon
		double x2 = (p2.lonRad() - lon1) * lonScale;
		double y2 = lat2 - lat1;
		double x3 = (p3.lonRad() - lon1) * lonScale;
		double y3 = lat3 - lat1;

		return (x3 * y2 - x2 * y3) / Math.sqrt(x2 * x2 + y2 * y2) * EARTH_RADIUS_MEAN;
	}

	// TODO reenable once rupture surface code migrated
	/**
	 * Calculates the distance between the supplied {@code Location} and the
	 * {@code EventlyGridddedSurface} by looping over all the locations in the
	 * surface and returning the smallest one determined by
	 * {@link #horzDistance(Location, Location)}.
	 * 
	 * @param loc a {@code Location}
	 * @param rupSurf an EvenlyGriddedSurfaceAPI
	 * @return the minimum distance to a surface from the supplied
	 *         {@code Location}
	 */
	// public static double distanceToSurf(Location loc,
	// AbstractEvenlyGriddedSurface rupSurf) {
	// double minDistance = Double.MAX_VALUE;
	// double horzDist, vertDist, totalDist;
	// for (Location loc2 : rupSurf) {
	// horzDist = horzDistance(loc, loc2);
	// vertDist = vertDistance(loc, loc2);
	// totalDist = horzDist * horzDist + vertDist * vertDist;
	// if (totalDist < minDistance) minDistance = totalDist;
	// }
	// return pow(minDistance, 0.5);
	// }

	/**
	 * Calculates the distance between the supplied {@code Location} and the
	 * {@code EventlyGridddedSurface} by looping over all the locations in the
	 * surface and returning the smallest one determined by
	 * {@link #horzDistanceFast(Location, Location)}.
	 * 
	 * @param loc a {@code Location}
	 * @param rupSurf an EvenlyGriddedSurfaceAPI
	 * @return the minimum distance to a surface from the supplied
	 *         {@code Location}
	 */
	// public static double distanceToSurfFast(Location loc, RuptureSurface
	// rupSurf) {
	// double minDistance = Double.MAX_VALUE;
	// double horzDist, vertDist, totalDist;
	//
	// for (Location loc2 : rupSurf.getEvenlyDiscritizedListOfLocsOnSurface()) {
	// horzDist = horzDistanceFast(loc, loc2);
	// vertDist = vertDistance(loc, loc2);
	// totalDist = horzDist * horzDist + vertDist * vertDist;
	// if (totalDist < minDistance) minDistance = totalDist;
	// }
	// return pow(minDistance, 0.5);
	// }

	/**
	 * Computes the shortest distance between a point and a line segment (i.e.
	 * great-circle segment). Both the line and point are assumed to be at the
	 * earth's surface; the depth component of each {@code Location} is ignored.
	 * This method uses the true spherical geometric function for 'off-track
	 * distance'; See <a href="http://williams.best.vwh.net/avform.htm#XTE">
	 * Aviation Formulary</a> for source. This method always returns a positive
	 * result.
	 * 
	 * <p>This method, though more accurate over longer
	 * distances and line lengths, is up to 20x slower than
	 * {@link #distanceToSegmentFast(Location, Location, Location)}. However,
	 * this method returns accurate results for values spanning #177;180&#176;.</p>
	 * 
	 * <p> If the line should instead be treated as infinite, use
	 * {@link #distanceToLine(Location, Location, Location)} instead.</p>
	 * 
	 * @param p1 the first {@code Location} point on the line
	 * @param p2 the second {@code Location} point on the line
	 * @param p3 the {@code Location} point for which distance will be
	 *        calculated
	 * @return the shortest distance in km between the supplied point and line
	 * @see #distanceToLineFast(Location, Location, Location)
	 * @see #distanceToLine(Location, Location, Location)
	 */
	public static double distanceToSegment(Location p1, Location p2, Location p3) {

		// repeat calcs in distanceToLine() to cut down on replication of
		// expensive trig ops that would result from calling distanceToLine()

		// angular distance
		double ad13 = angle(p1, p3);
		// delta azimuth p1 to p3 and azimuth p1 to p2
		double Daz13az12 = azimuthRad(p1, p3) - azimuthRad(p1, p2);
		// cross-track distance (in radians)
		double xtd = asin(sin(ad13) * sin(Daz13az12));
		// along-track distance (in km)
		double atd = acos(cos(ad13) / cos(xtd)) * EARTH_RADIUS_MEAN;
		// check if beyond p3
		if (atd > horzDistance(p1, p2)) return horzDistance(p2, p3);
		// check if before p1
		if (cos(Daz13az12) < 0) return horzDistance(p1, p3);
		return (abs(xtd) < TOLERANCE) ? 0.0 : abs(xtd) * EARTH_RADIUS_MEAN;
	}

	/**
	 * Computes the shortest distance between a point and a line segment. Both
	 * the line and point are assumed to be at the earth's surface; the depth
	 * component of each {@code Location} is ignored. This is a fast, geometric,
	 * cartesion (flat-earth approximation) solution in which longitude of the
	 * line points are scaled by the cosine of latitude; it is only appropriate
	 * for use over short distances (e.g. &lt;200 km).
	 * 
	 * <p><b>Note:</b> This method fails for values spanning &#177;180&#176;;
	 * see {@link #distanceToLineFast(Location, Location, Location)}.</p>
	 * 
	 * <p>If the line should instead be treated as infinite, use
	 * {@link #distanceToLineFast(Location, Location, Location)} instead.</p>
	 * 
	 * @param p1 the first {@code Location} point on the line
	 * @param p2 the second {@code Location} point on the line
	 * @param p3 the {@code Location} point for which distance will be
	 *        calculated
	 * @return the shortest distance in km between the supplied point and line
	 * @see #distanceToSegment(Location, Location, Location)
	 * @see #distanceToLineFast(Location, Location, Location)
	 */
	public static double distanceToSegmentFast(Location p1, Location p2,
			Location p3) {

		double lat1 = p1.latRad();
		double lat2 = p2.latRad();
		double lat3 = p3.latRad();
		double lon1 = p1.lonRad();

		// use average latitude to scale longitude
		double lonScale = cos(0.5 * lat3 + 0.25 * lat1 + 0.25 * lat2);

		// first point on line transformed to origin; others scaled by lon
		double x2 = (p2.lonRad() - lon1) * lonScale;
		double y2 = lat2 - lat1;
		double x3 = (p3.lonRad() - lon1) * lonScale;
		double y3 = lat3 - lat1;

		return Line2D.ptSegDist(0, 0, x2, y2, x3, y3) * EARTH_RADIUS_MEAN;
	}

	/**
	 * Computes the initial azimuth (bearing) when moving from one
	 * {@code Location} to another. See <a
	 * href="http://williams.best.vwh.net/avform.htm#Crs"> Aviation
	 * Formulary</a> for source. For back azimuth, reverse the {@code Location}
	 * arguments. Result is returned in radians over the interval 0 to 2&pi;.
	 * 
	 * <p><b>Note:</b> It is more efficient to use this method for computation
	 * because {@code Location}s store lat and lon in radians internally. Use
	 * {@link #azimuth(Location, Location)} for presentation.</p>
	 * 
	 * @param p1 the first {@code Location} point
	 * @param p2 the second {@code Location} point
	 * @return the azimuth (bearing) from p1 to p2 in radians
	 * @see #azimuth(Location, Location)
	 */
	public static double azimuthRad(Location p1, Location p2) {

		double lat1 = p1.latRad();
		double lat2 = p2.latRad();

		// check the poles using a small number ~ machine precision
		if (isPole(p1)) {
			return ((lat1 > 0) ? PI : 0); // N : S pole
		}

		// for starting points other than the poles:
		double dLon = p2.lonRad() - p1.lonRad();
		double cosLat2 = cos(lat2);
		double azRad = atan2(sin(dLon) * cosLat2, cos(lat1) * sin(lat2) -
			sin(lat1) * cosLat2 * cos(dLon));

		return (azRad + TWOPI) % TWOPI;
	}

	/**
	 * Computes the initial azimuth (bearing) when moving from one
	 * {@link Location} to another in degrees. See <a
	 * href="http://williams.best.vwh.net/avform.htm#Crs"> Aviation
	 * Formulary</a> for source. For back azimuth, reverse the {@code Location}
	 * arguments. Result is returned in decimal degrees over the interval
	 * 0&#176; to 360&#176;.
	 * 
	 * @param p1 the first {@code Location} point
	 * @param p2 the second {@code Location} point
	 * @return the azimuth (bearing) from p1 to p2 in decimal degrees
	 * @see #azimuthRad(Location, Location)
	 */
	public static double azimuth(Location p1, Location p2) {
		return azimuthRad(p1, p2) * TO_DEG;
	}

	/**
	 * Computes a {@code Location} given an origin point, bearing, and distance.
	 * See <a href="http://williams.best.vwh.net/avform.htm#LL"> Aviation
	 * Formulary</a> for source. Note that {@code azimuth} is expected in
	 * <i>radians</i>.
	 * 
	 * @param p starting location point
	 * @param azimuth (bearing) in <i>radians</i> away from origin
	 * @param distance (horizontal) along bearing in km
	 * @return the end location
	 */
	public static Location location(Location p, double azimuth, double distance) {
		return location(p.latRad(), p.lonRad(), p.depth(), azimuth, distance, 0);
	}

	/**
	 * Computes a {@code Location} given an origin point and a
	 * {@code LocationVector}. See <a
	 * href="http://williams.best.vwh.net/avform.htm#LL"> Aviation Formulary</a>
	 * for source.
	 * 
	 * @param p starting location point
	 * @param d distance along bearing
	 * @return the end location
	 */
	public static Location location(Location p, LocationVector d) {
		return location(p.latRad(), p.lonRad(), p.depth(), d.azimuth(), d.horizontal(),
			d.vertical());
	}

	/*
	 * Internal helper; assumes lat, lon, and azimuth in radians, and depth and
	 * dist in km
	 */
	private static Location location(double lat, double lon, double depth,
			double az, double dH, double dV) {

		double sinLat1 = sin(lat);
		double cosLat1 = cos(lat);
		double ad = dH / EARTH_RADIUS_MEAN; // angular distance
		double sinD = sin(ad);
		double cosD = cos(ad);

		double lat2 = Math.asin(sinLat1 * cosD + cosLat1 * sinD * cos(az));

		double lon2 = lon + atan2(sin(az) * sinD * cosLat1, cosD - sinLat1 * sin(lat2));
		return Location.create(lat2 * TO_DEG, lon2 * TO_DEG, depth + dV);
	}

	/**
	 * Returns the angle (in decimal degrees) of a line between the first and
	 * second location relative to horizontal. This method is intended for use
	 * at relatively short separations ( e.g. &lteq; 200km) as it degrades at
	 * large distances where curvature is not considered. Note that positive
	 * angles are down, negative angles are up.
	 * @param p1 the first {@code Location} point
	 * @param p2 the second {@code Location} point
	 * @return the plunge of this vector
	 */
	public static double plunge(Location p1, Location p2) {
		return LocationVector.create(p1, p2).plunge();
	}

	/**
	 * Method returns a unit {@code LocationVector} that bisects the angle
	 * defined by the line segments <span style="text-decoration: overline">
	 * {@code p2p1}</span> and <span style="text-decoration: overline">
	 * {@code p2p3}</span>.
	 * @param p1 the first {@code Location} point
	 * @param p2 the second {@code Location} point
	 * @param p3 the third {@code Location} point
	 * @return the bisecting {@code LocationVector}
	 */
	public static LocationVector bisect(Location p1, Location p2, Location p3) {
		LocationVector v1 = LocationVector.create(p2, p1);
		LocationVector v2 = LocationVector.create(p2, p3);
		double az = (v2.azimuth() + v1.azimuth()) / 2;
		return LocationVector.create(az, 1, 0);
	}

	/**
	 * Tolerance used for location comparisons; 0.000000000001 which in
	 * decimal-degrees, radians, and km is comparable to micron-scale precision.
	 */
	public static final double TOLERANCE = 0.000000000001;

	/**
	 * Returns whether the supplied {@code Location} coincides with one of the
	 * poles. Any supplied {@code Location}s that are very close (less than a
	 * mm) will return {@code true}.
	 * 
	 * @param p {@code Location} to check
	 * @return {@code true} if {@code loc} coincides with one of the earth's
	 *         poles, {@code false} otherwise.
	 */
	public static boolean isPole(Location p) {
		return cos(p.latRad()) < TOLERANCE;
	}

	/**
	 * Returns a geographic (Mercator) {@link Rectangle2D} with coordinates in
	 * radians that is centered on {@code loc} and has a width and height of
	 * {@code 2 * distance}. The returned rectangle is intended for use in quick
	 * contains operations using a {@code Location}s native (radian-based)
	 * storage of latitude and longitude.
	 * 
	 * <p><b>Note:</b> Due to convergence of meridians, the returned rectangle
	 * will contains most, but not all, of a circle centered at {@code loc}.
	 * 
	 * @param loc on which to center the rectangle
	 * @param distance to extend rectangle in
	 * @return a {@link Rectangle2D} in geographic radian coordinates
	 * @see Location#latRad()
	 * @see Location#lonRad()
	 */
	public static Rectangle2D rectangle(Location loc, double distance) {
		Location maxLatLoc = location(loc, NORTH.bearingRad(), distance);
		Location minLonLoc = location(loc, WEST.bearingRad(), distance);
		double wRad = 2 * (loc.lonRad() - minLonLoc.lonRad());
		double hRad = 2 * (maxLatLoc.latRad() - loc.latRad());
		return new Rectangle2D.Double(minLonLoc.lonRad(), maxLatLoc.latRad(),
			wRad, hRad);
	}

	/**
	 * Returns {@code true} if the supplied {@code Location}s are very, very
	 * close to one another. Internally, lat, lon, and depth values must be
	 * within <1mm of each other.
	 * 
	 * @param p1 the first {@code Location} to compare
	 * @param p2 the second {@code Location} to compare
	 * @return {@code true} if the supplied {@code Location}s are very close,
	 *         {@code false} otherwise.
	 */
	public static boolean areSimilar(Location p1, Location p2) {
		if (!Precision.equals(p1.latRad(), p2.latRad(), TOLERANCE)) {
			return false;
		}
		if (!Precision.equals(p1.lonRad(), p2.lonRad(), TOLERANCE)) {
			return false;
		}
		if (!Precision.equals(p1.depth(), p2.depth(), TOLERANCE)) {
			return false;
		}
		return true;
	}

	// TODO these seem heavy

	/**
	 * Calculates the minimum latitude in the supplied {@code Collection} of
	 * {@code Location} objects.
	 * 
	 * @param locs - collection of locations
	 * @return the minimum latitude in the supplied locations, or positive
	 *         infinity if the {@code Collection} is empty.
	 * @throws NullPointerException if {@code locs} is null
	 */
	public static double calcMinLat(Collection<Location> locs) {
		double min = Double.POSITIVE_INFINITY;
		for (Location loc : locs) {
			double val = loc.lat();
			if (val < min) min = val;
		}
		return min;
	}

	/**
	 * Calculates the minimum longitude in the supplied {@code Collection} of
	 * {@code Location} objects.
	 * 
	 * @param locs - collection of locations
	 * @return the minimum longitude in the supplied locations, or positive
	 *         infinity if the {@code Collection} is empty.
	 * @throws NullPointerException if {@code locs} is null
	 */
	public static double calcMinLon(Collection<Location> locs) {
		double min = Double.POSITIVE_INFINITY;
		for (Location loc : locs) {
			double val = loc.lon();
			if (val < min) min = val;
		}
		return min;
	}

	/**
	 * Calculates the maximum latitude in the supplied {@code Collection} of
	 * {@code Location} objects.
	 * 
	 * @param locs - collection of locations
	 * @return the maximum latitude in the supplied locations, or negative
	 *         infinity if the {@code Collection} is empty.
	 * @throws NullPointerException if {@code locs} is null
	 */
	public static double calcMaxLat(Collection<Location> locs) {
		double max = Double.NEGATIVE_INFINITY;
		for (Location loc : locs) {
			double val = loc.lat();
			if (val > max) max = val;
		}
		return max;
	}

	/**
	 * Calculates the maximum longitude in the supplied {@code Collection} of
	 * {@code Location} objects.
	 * 
	 * @param locs - collection of locations
	 * @return the maximum longitude in the supplied locations, or negative
	 *         infinity if the {@code Collection} is empty.
	 * @throws NullPointerException if {@code locs} is null
	 */
	public static double calcMaxLon(Collection<Location> locs) {
		double max = Double.NEGATIVE_INFINITY;
		for (Location loc : locs) {
			double val = loc.lon();
			if (val > max) max = val;
		}
		return max;
	}
	
	// TODO these need to be made fail-safe for pole spanning distances and
	// -180 +180 spanning distances
	
	/**
	 * Returns a {@code Predicate} for {@code Location} filtering. The returned
	 * filter makes an initial pass using a rectangular geographic/Mercator
	 * filter.
	 * 
	 * @param loc {@code Location} relative to which other {@code Location}s are
	 *        filtered.
	 * @param distance beyond which the filter will return {@code false}
	 * @return a new {@code Location} filter
	 * @see #rectangleFilter(Location, double)
	 */
	public static Predicate<Location> distanceFilter(Location loc,
			double distance) {
		return new DistanceFilter(loc, distance);
	}

	/**
	 * Returns a {@code Predicate} for {@code Location} filtering using a
	 * rectangular geographic/Mercator filter.
	 * 
	 * @param loc {@code Location} relative to which other {@code Location}s are
	 *        filtered.
	 * @param distance beyond which the filter will return {@code false}
	 * @return a new {@code Location} filter
	 * @see #rectangle(Location, double)
	 */
	public static Predicate<Location> rectangleFilter(Location loc,
			double distance) {
		return new RectangleFilter(loc, distance);
	}

	private static class RectangleFilter implements Predicate<Location> {
		private final Rectangle2D locRect;

		private RectangleFilter(Location p1, double distance) {
			locRect = rectangle(p1, distance);
		}

		@Override
		public boolean apply(Location p2) {
			return locRect.contains(p2.lonRad(), p2.latRad());
		}
	}
	
	private static class DistanceFilter implements Predicate<Location> {
		private final Location p1;
		private final double distance;
		private final Predicate<Location> rectFilter;

		private DistanceFilter(Location p1, double distance) {
			this.p1 = p1;
			this.distance = distance;
			rectFilter = new RectangleFilter(p1, distance);
		}

		@Override
		public boolean apply(Location p2) {
			return rectFilter.apply(p2) ? horzDistanceFast(p1, p2) <= distance
				: false;
		}
	}	

}
