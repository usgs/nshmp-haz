package org.opensha2.geo;

import java.util.Arrays;
import java.util.Objects;

/**
 * A rectangular (in Mercator projection) bounding box specified by a lower-left
 * coordinate ({@link #min()}) and an upper-right coordinate ({@link #max()}).
 * 
 * <p>Bounds are 2-dimensional in that the depth component of the corners will
 * always be 0.
 *
 * @author Peter Powers
 */
public final class Bounds {

	private final Location min;
	private final Location max;

	Bounds(Location min, Location max) {
		this.min = min;
		this.max = max;
	}

	Bounds(double minLat, double minLon, double maxLat, double maxLon) {
		this(Location.create(minLat, minLon), Location.create(maxLat, maxLon));
	}

	/**
	 * Return the lower left coordinate (minimum latatide and longitude) as a
	 * {@code Location}.
	 */
	public Location min() {
		return min;
	}

	/**
	 * Return the upper right coordinate (maximum latitude and longitude) as a
	 * {@code Location}.
	 */
	public Location max() {
		return max;
	}

	/**
	 * Return this {@code Bounds} as a {@code LocationList} of four vertices,
	 * starting with {@link #min()} and winding counter-clockwise.
	 */
	public LocationList toList() {
		return LocationList.create(
			min,
			Location.create(min.lat(), max.lon()),
			max,
			Location.create(max.lat(), min.lon()));
	}

	/**
	 * Return the values of this {@code Bounds} object in the form
	 * {@code [min().lon(), min().lat(), max().lon(), max().lat()]}.
	 */
	public double[] toArray() {
		return new double[] { min.lon(), min.lat(), max.lon(), max.lat() };
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null) return false;
		if (!(obj instanceof Bounds)) return false;
		Bounds other = (Bounds) obj;
		if (min == other.min && max == other.max) return true;
		return min.equals(other.min) && max.equals(other.max);
	}

	@Override
	public int hashCode() {
		return Objects.hash(min, max);
	}

	/**
	 * Returns the string representation of {@link #toArray()}.
	 */
	@Override
	public String toString() {
		return Arrays.toString(toArray());
	}

}
