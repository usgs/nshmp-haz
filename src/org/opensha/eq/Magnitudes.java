package org.opensha.eq;

import static org.opensha.data.DataUtils.validate;
import static java.lang.Math.log10;
import static java.lang.Math.pow;

import org.opensha.data.DataUtils;

import com.google.common.collect.Range;

/**
 * Earthquake magnitude utilities.
 * @author Peter Powers
 */
public final class Magnitudes {

	/**
	 * Minimum allowed earthquake magnitude (-2.0). This numeric value is used
	 * for range checking and is not bound to any particular magnitude scale.
	 */
	public static final double MIN_MAG = -2.0;

	/**
	 * Maximum allowed earthquake magnitude (9.7). This numeric value is used
	 * for range checking and is not bound to any particular magnitude scale.
	 */
	public static final double MAX_MAG = 9.7;

	/** Shear modulus. μ = 3 * 10<sup>10</sup> dyne/cm<sup>2</sup>.*/
	public static final double MU = 3e10;
	
	private static final Range<Double> magRange = Range
		.closed(MIN_MAG, MAX_MAG);

	/**
	 * Verifies that a magnitude value is between {@code MIN_MAG} and
	 * {@code MAX_MAG} (inclusive). Method returns the supplied value and can be
	 * used inline.
	 * 
	 * @param mag magnitude to validate
	 * @return the supplied magnitude
	 * @throws IllegalArgumentException if {@code magdip} value is out of range
	 * @see DataUtils#validate(Range, String, double)
	 */
	public static double validateMag(double mag) {
		return validate(magRange, "Magnitude", mag);
	}

	/**
	 * Convert moment magnitude, <em>M</em><sub>W</sub>, to seismic moment,
	 * <em>M</em><sub>0</sub>.
	 * @param magnitude to convert
	 * @return the equivalent seismic moment in Newton-meters
	 */
	public static double magToMoment(double magnitude) {
		return pow(10, 1.5 * magnitude + 9.05);
	}

	/**
	 * Convert seismic moment, <em>M</em><sub>0</sub>, to moment magnitude,
	 * <em>M</em><sub>w</sub>.
	 * @param moment to convert (in Newton-meters)
	 * @return the equivalent moment magnitude
	 */
	public static double momentToMag(double moment) {
		return (log10(moment) - 9.05) / 1.5;
	}
	
	// TODO finish this, check conversion for slip in m instead of mm/yr for rate
//	/**
//	 * @param area in km<sup>2</sup>
//	 * @param slip 
//	 * @return
//	 */
//	public static double moment(double area, double slip) {
//		return area *
//	}
//	public static double recurrence(double Mw, double area, double slip)

	
}
