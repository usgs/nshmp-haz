package org.opensha.calc;

import org.apache.commons.math3.special.Erf;
import org.opensha.data.XY_Point;
import org.opensha.data.XY_Sequence;

/**
 * Add comments here
 *
 * @author Peter Powers
 */
public class Utils {

	/** Precomputed square-root of 2. */
	public static final double SQRT_2 = Math.sqrt(2);

	// TODO change below to take HazardCurve?
	
	/**
	 * Returns the supplied function populated with exceedance probabilites
	 * calculated per the NSHMP. If {@code clamp} is {@code true}, the
	 * lesser of {@code 3*sigma} and {@code clampVal} is used to
	 * truncate the upper part of the underlying Gaussian.
	 * 
	 * @param imls function to populate
	 * @param mean log mean ground motion
	 * @param sigma log std deviation of ground motion
	 * @param clamp whether to enable additional truncation
	 * @param clampVal truncation value
	 * @return the populated function (non-log ground motions)
	 */
	public static XY_Sequence getExceedProbabilities(XY_Sequence imls,
			double mean, double sigma, boolean clamp, double clampVal) {
		// double sigma = getStdDev();
		// double gnd = getMean();
		// System.out.print(mean + " " + sigma);

		double clip = mean + 3 * sigma;
		if (clamp) {
			double clip3s = Math.exp(clip);
			double clipPer = clampVal;
			if (clipPer < clip3s && clipPer > 0) clip = Math.log(clipPer);
		}
		try {
			double Pclip = Utils.gaussProbExceed(mean, sigma, clip);
			for (XY_Point p : imls) {
				p.set(gaussProbExceed(mean, sigma, Math.log(p.x()), Pclip,
					GaussTruncation.ONE_SIDED));
			}
		} catch (RuntimeException me) {
			me.printStackTrace();
		}
		return imls;
	}

	/**
	 * Returns the probability of exceeding the supplied target value in a
	 * Gaussian distribution asuming no truncation.
	 * @param mean of distribution
	 * @param std deviation
	 * @param value to exceed
	 * @return the probability of exceeding the supplied value
	 */
	public static double gaussProbExceed(double mean, double std, double value) {
		return (Erf.erf((mean - value) / (std * SQRT_2)) + 1.0) * 0.5;
	}
	
	/**
	 * Returns the probability of exceeding the supplied target value in a
	 * (possibly truncated) Gaussian distribution. This method requires the
	 * probability corresponding to some level of truncation. As it is an often
	 * reused value, it is left to the user to supply it from
	 * {@link #gaussProbExceed(double, double, double)}. The supplied truncation
	 * probability should be the probability at the one-sided upper truncation
	 * bound.
	 * 
	 * @param mean of distribution
	 * @param std deviation
	 * @param value to exceed
	 * @param trunc truncation probability
	 * @param type the truncation type
	 * @return the probability of exceeding the supplied value
	 * @see #gaussProbExceed(double, double, double)
	 */
	public static double gaussProbExceed(double mean, double std, double value,
			double trunc, GaussTruncation type) {
		// checkArgument(trunc >= 0,
		// "Truncation must be a positive value or 0");
		double P = gaussProbExceed(mean, std, value);
		if (type == GaussTruncation.ONE_SIDED) {
			P = (P - trunc) / (1.0 - trunc);
			return probBoundsCheck(P);
		} else if (type == GaussTruncation.TWO_SIDED) {
			P = (P - trunc) / (1 - 2 * trunc);
			return probBoundsCheck(P);
		} else {
			return P;
		}
	}

	// TODO does this exists due to double precission errors possibly pushing
	// probabilities above 1 or below 0 ??
	private static double probBoundsCheck(double P) {
		return (P < 0) ? 0 : (P > 1) ? 1 : P;
	}


}
