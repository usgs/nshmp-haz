package org.opensha.geo;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.opensha.geo.GeoTools.TO_DEG;
import static org.opensha.geo.GeoTools.TO_RAD;
import static org.opensha.geo.GeoTools.validateDepth;
import static org.opensha.geo.GeoTools.validateLat;
import static org.opensha.geo.GeoTools.validateLon;

import java.util.Iterator;

import org.opensha.util.Parsing;

import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;

/**
 * A {@code Location} represents a point with reference to the earth's
 * ellipsoid. It is expressed in terms of latitude, longitude, and depth. As in
 * seismology, the convention adopted in here is for depth to be positive-down,
 * always. All utility methods in this package assume this to be the case.
 * {@code Location}s may be defined using longitude values in the range:
 * [-180&#176;, 360&#176;]. {@code Location} instances are immutable.
 * 
 * <p>Note that although static factory methods take arguments in the order:
 * [lat, lon, depth], {@code String} representations of a {@code Location} are
 * in the order: [lon, lat, depth], consistent with KML, GeoJSON, and other
 * digital coordinate formats that match standard plotting coordinate order: [x,
 * y, z].</p>
 * 
 * <p>For computational cenvenience, latitude and longitude values are converted
 * and stored internally in radians. Special {@code get***Rad()} methods are
 * provided to access this native format.</p>
 * 
 * @author Peter Powers
 */
public final class Location implements Comparable<Location> {

	/**
	 * Format {@code String("%.5f")} used for presentation of {@code Location} data.
	 * For use with {@link String#format(String, Object...)}.
	 */
	public static final String FORMAT = "%.5f";
	
	private static final String TO_STR_FMT = FORMAT + "," + FORMAT + "," + FORMAT;
	
	private final double lat;
	private final double lon;
	private final double depth;

	private Location(double lat, double lon, double depth) {
		this.lat = validateLat(lat) * TO_RAD;
		this.lon = validateLon(lon) * TO_RAD;
		this.depth = validateDepth(depth);
	}
	
	private Location(Location loc) {
		this.lat = loc.lat;
		this.lon = loc.lon;
		this.depth = loc.depth;
	}

	/**
	 * Creates a new {@code Location} with the supplied latitude and
	 * longitude and a depth of 0 km.
	 * 
	 * @param lat latitude in decimal degrees
	 * @param lon longitude in decimal degrees
	 * @return a new {@code Location}
	 * @throws IllegalArgumentException if any supplied values are out of range
	 * @see GeoTools
	 */
	public static Location create(double lat, double lon) {
		return new Location(lat, lon, 0);
	}

	/**
	 * Creates a new {@code Location} with the supplied latitude, longitude,
	 * and depth.
	 * 
	 * @param lat latitude in decimal degrees
	 * @param lon longitude in decimal degrees
	 * @param depth in km (positive down)
	 * @return a new {@code Location}
	 * @throws IllegalArgumentException if any supplied values are out of range
	 * @see GeoTools
	 */
	public static Location create(double lat, double lon, double depth) {
		return new Location(lat, lon, depth);
	}

	/**
	 * Creates a new {@code Location} that is identircal to the one supplied.
	 * @param loc {@code Location} to copy
	 * @return an exact copy of the supplied {@code Location}
	 */
	public static Location copyOf(Location loc) {
		return new Location(loc);
	}
	
	/**
	 * Generates a new {@code Location} by parsing the supplied {@code String}.
	 * Method is the complement of {@link #toString()} and is intended for use
	 * with the result of that method.
	 * @param s to parse
	 * @return a new Location
	 * @throws NumberFormatException if s is unparseable
	 * @see #toString()
	 */
	public static Location fromString(String s) {
		return FromStringFunction.INSTANCE.apply(s);
	}

	/**
	 * Returns the depth of this {@code Location}.
	 * @return the {@code Location} depth in km
	 */
	public double depth() {
		return depth;
	}

	/**
	 * Returns the latitude of this {@code Location}.
	 * @return the {@code Location} latitude in decimal degrees
	 */
	public double lat() {
		return lat * TO_DEG;
	}

	/**
	 * Returns the longitude of this {@code Location}.
	 * @return the {@code Location} longitude in decimal degrees
	 */
	public double lon() {
		return lon * TO_DEG;
	}

	/**
	 * Returns the latitude of this {@code Location} in radians.
	 * @return the {@code Location} latitude in radians
	 */
	public double latRad() {
		return lat;
	}

	/**
	 * Returns the longitude of this {@code Location} in radians.
	 * @return the {@code Location} longitude in radians
	 */
	public double lonRad() {
		return lon;
	}

	/**
	 * Returns a KML compatible tuple: 'lon,lat,depth' (no spaces).
	 * @see #fromString(String)
	 */
	@Override
	public String toString() {
		return String.format(TO_STR_FMT, lon(), lat(), depth());
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (!(obj instanceof Location)) return false;
		Location loc = (Location) obj;
		// NOTE because rounding errors may give rise to very slight
		// differences in radian values that disappear when converting back
		// to decimal degrees, and because most Locations are initialized
		// with decimal degree values, equals() compares decimal degrees
		// rather than the native radian values. ppowers 4/12/2010
		if (lat() != loc.lat()) return false;
		if (lon() != loc.lon()) return false;
		if (depth() != loc.depth()) return false;
		return true;
	}

	// TODO update this with lib hash methods
	@Override
	public int hashCode() {
		// edit did same fix as for equals, now uses getters
		long latHash = Double.doubleToLongBits(lat());
		long lonHash = Double.doubleToLongBits(lon() + 1000);
		long depHash = Double.doubleToLongBits(depth() + 2000);
		long v = latHash + lonHash + depHash;
		return (int) (v ^ (v >>> 32));
	}

	/**
	 * Compares this {@code Location} to another and sorts first by latitude,
	 * then by longitude. When sorting a list of randomized but evenly spaced
	 * grid of {@code Location}s, the resultant ordering will be left to right
	 * across rows of uniform latitude, ascending to the leftmost next higher
	 * latitude at the end of each row (left-to-right, bottom-to-top).
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

	/**
	 * Returns a {@link Function} that can be used to convert {@code String}s
	 * to {@code Location}s. 
	 * @return a {@code String} to {@code Location} conversion function
	 */
	public static Function<String, Location> fromStringFunction() {
		return FromStringFunction.INSTANCE;
	}

	// TODO possibly refactor this to handle Strings only containing lat and lon
	private static enum FromStringFunction implements
			Function<String, Location> {
		INSTANCE;
		@Override
		public Location apply(String s) {
			Iterator<Double> it = FluentIterable
				.from(Parsing.splitOnCommas(checkNotNull(s)))
				.transform(Parsing.doubleValueFunction()).iterator();
			// lon is first arg in KML but second in Location constructor
			double lon = it.next();
			return create(it.next(), lon, it.next());
		}
	};

}
