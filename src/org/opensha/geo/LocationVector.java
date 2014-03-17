package org.opensha.geo;

import static org.opensha.geo.GeoTools.*;

/**
 * This class encapsulates information describing a vector between two
 * {@code Location}s. Internally, this vector is defined by the azimuth
 * (bearing) and the horizontal and vertical separation between the points. Note
 * that a {@code LocationVector} from point A to point B is not the complement
 * of that from point B to A. Although the horizontal and vertical components
 * will be the same, the azimuth will likely change by some value other than
 * 180&#176;.
 * 
 * <p><b>NOTE:</b> Although a {@code LocationVector} will function in any
 * reference frame, the convention in seismology and that adopted in OpenSHA is
 * for depth to be positive down. Also, azimuth is stored internally in radians
 * for computational convenience. Be sure to use the {@link #azimuth()}
 * (radians) or // {@link #azimuthDegrees()} (decimal-degrees) where
 * appropriate.</p>
 * 
 * @author Peter Powers
 */
public class LocationVector {

	private double azimuth;
	private double vert;
	private double horiz;

	private LocationVector(double azimuth, double horiz, double vert) {
		// TODO validation?? class may be suitable for garbage in/out
		this.azimuth = azimuth;
		this.horiz = horiz;
		this.vert = vert;
	}

	/**
	 * Initializes a new {@code LocationVector} with the supplied values. Note
	 * that {@code azimuth} is expected in <i>radians</i>.
	 * 
	 * @param azimuth to set in <i>radians</i>
	 * @param horizontal component to set in km
	 * @param vertical component to set in km
	 * @return a new {@code LocationVector}
	 */
	public static LocationVector create(double azimuth, double horizontal,
			double vertical) {
		return new LocationVector(azimuth, horizontal, vertical);
	}

	/**
	 * Creates a new {@code LocationVector} with horizontal and vertical
	 * components derived from the supplied {@code plunge} and {@code length}.
	 * Note that {@code azimuth} and {@code plunge} are expected in
	 * <i>radians</i>.
	 * 
	 * @param azimuth to set in <i>radians</i>
	 * @param plunge to set in <i>radians</i>
	 * @param length of vector in km
	 * @return a new {@code LocationVector}
	 */
	public static LocationVector createWithPlunge(double azimuth,
			double plunge, double length) {
		return create(azimuth, length * Math.cos(plunge),
			length * Math.sin(plunge));
	}

	/**
	 * Returns the {@code LocationVector} describing the move from one
	 * {@code Location} to another.
	 * 
	 * @param p1 the first {@code Location}
	 * @param p2 the second {@code Location}
	 * @return a new {@code LocationVector}
	 */
	public static LocationVector create(Location p1, Location p2) {
		// NOTE A 'fast' implementation of this method was tested
		// but no performance gain was realized P.Powers 3-5-2010
		return LocationVector.create(Locations.azimuthRad(p1, p2),
			Locations.horzDistance(p1, p2), Locations.vertDistance(p1, p2));
	}

	/**
	 * Returns a copy of the supplied vector with azimuth and vertical
	 * components reversed.
	 * 
	 * <p><b>NOTE</b>: create(p1, p2) is not equivalent to create
	 * reverseOf(create(p2, p1)). Although the horizontal and vertical
	 * components will likley be the same but the azimuths will potentially be
	 * quite different depending on the separation between {@code p1} and
	 * {@code p2}.
	 * 
	 * @param v {@code LocationVector} to copy and flip
	 * @return the flipped copy
	 */
	public static LocationVector reverseOf(LocationVector v) {
		return create((v.azimuth + PI_BY_2) % TWOPI, v.horiz, -v.vert);
	}

	/**
	 * Returns the azimuth of this vector in decimal degrees.
	 * @return the azimuth in decimal degrees
	 * @see #azimuth()
	 */
	public double azimuthDegrees() {
		return azimuth * TO_DEG;
	}

	/**
	 * Returns the azimuth of this vector in radians.
	 * @return the azimuth in radians
	 * @see #azimuthDegrees()
	 */
	public double azimuth() {
		return azimuth;
	}

	/**
	 * Returns the angle (in radians) between this vector and a horizontal
	 * plane. This method is intended for use at relatively short separations
	 * (e.g. &lteq;200km) as it degrades at large distances where curvature is
	 * not considered. Note that positive angles are down, negative angles are
	 * up.
	 * @return the plunge of this vector
	 */
	public double plunge() {
		return Math.atan(vert / horiz);
	}

	/**
	 * Returns the angle (in decimal degrees) between this vector and a
	 * horizontal plane. This method is intended for use at relatively short
	 * separations (e.g. &lteq;200km) as it degrades at large distances where
	 * curvature is not considered. Note that positive angles are down, negative
	 * angles are up.
	 * @return the plunge of this vector
	 */
	public double plungeDegrees() {
		return plunge() * GeoTools.TO_DEG;
	}

	/**
	 * Returns the vertical component of this vector.
	 * @return the vertical component in km
	 */
	public double vertical() {
		return vert;
	}

	/**
	 * Returns the horizontal component of this vector.
	 * @return the horizontal component in km
	 */
	public double horizontal() {
		return horiz;
	}

	@Override
	public String toString() {
		StringBuffer b = new StringBuffer();
		b.append(this.getClass().getSimpleName());
		b.append(":  az = ");
		b.append(azimuthDegrees());
		b.append("  dH = ");
		b.append(horiz);
		b.append("  dV = ");
		b.append(vert);
		return b.toString();
	}

}
