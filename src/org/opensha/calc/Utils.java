package org.opensha.calc;

import java.util.Arrays;
import java.util.List;

import org.apache.commons.math3.special.Erf;
import org.opensha.data.ArrayXY_Sequence;
import org.opensha.data.DataUtils;
import org.opensha.data.XY_Point;
import org.opensha.data.XY_Sequence;

/**
 * Add comments here
 *
 * @author Peter Powers
 */
public class Utils {

	// TODO this needs work
	
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
	public static XY_Sequence setExceedProbabilities(XY_Sequence imls,
			double mean, double sigma, boolean clamp, double clampVal) {

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

	/*
	 * Computes joint probability of exceedence given the occurrence of a
	 * cluster of events: [1 - [(1-PE1) * (1-PE2) * ...]]. The probability of
	 * exceedance of each individual event is given in the supplied curves.
	 * WARNING: Method modifies curves in place and returns result in the first
	 * supplied curve.s
	 */
	public static ArrayXY_Sequence calcClusterExceedProb(List<ArrayXY_Sequence> curves) {
		ArrayXY_Sequence combined = ArrayXY_Sequence.copyOf(curves.get(0)).complement();
		for (int i=1; i < curves.size(); i++) {
			combined.multiply(curves.get(i).complement());
		}
		return combined.complement();
	}

	// @formatter:off
	
	/**
	 * These intensity measure levels expand the standard ranges of values used
	 * at different periods in past NSHMs to a common set that can be used
	 * across all periods with slightly higher discretization than before. It is
	 * derived from ln(-8.8):ln(2.0) with a 0.4 step include the highest ground
	 * motions (~7.5 g) and lower values
	 */
	public static final double[] NSHM_IMLS = {
		0.0002, 0.0005,
		0.001, 0.002, 0.00316, 0.00422, 0.00562, 0.0075,
		0.01, 0.0133, 0.0178, 0.0237, 0.0316, 0.0422, 0.0562, 0.075,
		0.1, 0.133, 0.178, 0.237, 0.316, 0.422, 0.562, 0.75,
		1.0, 1.33, 1.78, 2.37, 3.16, 5.01, 7.94};

	
	/**
	 * This sequence densifies the 'important' part of the curve (in high hazard areas)
	 * and adds additional values at the high and low end
	 */
	private static final double[] LOG10_IMLS = {
		-3.7, -3.3,
		-3.0, -2.7, -2.5, -2.375, -2.25, -2.125,
		-2.0, -1.875, -1.75, -1.625, -1.5, -1.375, -1.25, -1.125,
		-1.0, -0.875, -0.75, -0.625, -0.5, -0.375, -0.25, -0.125,
		 0.0,  0.125,  0.25,  0.375,  0.5,  0.7, 0.9 };
		 
		
	public static void main(String[] args) {
		System.out.println(Arrays.toString(LOG10_IMLS));
		System.out.println(LOG10_IMLS.length);
		
		double[] localImls = Arrays.copyOf(LOG10_IMLS, LOG10_IMLS.length);
		DataUtils.pow10(localImls);
		
//		double[] imls = DataUtils.buildCleanSequence(-8.8, 2.0, 0.4, true, 1);
//		System.out.println(Arrays.toString(imls));
//		DataUtils.exp(imls);
		System.out.println(Arrays.toString(localImls));
	}

}
