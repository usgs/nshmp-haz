package org.opensha.calc;

import static java.lang.Math.min;
import static org.apache.commons.math3.special.Erf.erf;
import static org.opensha.calc.ExceedanceModel.TRUNCATION_UPPER_ONLY;
import static org.opensha.calc.ExceedanceModel.TRUNCATION_LOWER_UPPER;

import java.util.List;

import org.opensha.data.ArrayXY_Sequence;
import org.opensha.data.XY_Point;
import org.opensha.data.XY_Sequence;

/**
 * Note that these are all linear space calculations; any hazard values should
 * first be converted to log space.
 *
 * @author Peter Powers
 */
public class Utils {

	/** Precomputed square-root of 2. */
	public static final double SQRT_2 = Math.sqrt(2);

	/**
	 * Compute the probability of exceeding a {@code value} in a normal
	 * distribution defined by {@code μ} and {@code σ}.
	 * 
	 * @param μ mean
	 * @param σ standard deviation
	 * @param value to exceed
	 * @param truncType none, one-sided, or two-sided truncation
	 * @param truncLevel in number of standard deviations
	 */
	@Deprecated
	public static double calcProbExceed(double μ, double σ, double value,
			ExceedanceModel truncType, double truncLevel) {

		double clip = μ + truncLevel * σ;
		double pHi = gaussProbExceed(μ, σ, clip);

		switch (truncType) {
			case TRUNCATION_UPPER_ONLY:
				return gaussProbExceed(μ, σ, value, pHi, 1.0);
			case TRUNCATION_LOWER_UPPER:
				return gaussProbExceed(μ, σ, value, pHi, 1.0 - pHi);
			default: // NONE case
				return gaussProbExceed(μ, σ, value, 0.0, 1.0);
		}
	}

	/**
	 * Compute the 'clamped' probability of exceeding a {@code value} in a
	 * normal distribution defined by {@code μ} and {@code σ}, where
	 * {@code clamp} is an absolute maximum that may be above or below any
	 * {@code μ + n * σ} truncation. For two-sided truncation, the lower bound
	 * is set to a sigma based value (e.g. {@code μ - n * σ}) that may yield an
	 * assymmetric trunction.
	 * 
	 * @param μ mean
	 * @param σ standard deviation
	 * @param value to exceed
	 * @param truncType none, one-sided, or two-sided truncation
	 * @param truncLevel in number of standard deviations
	 * @param clamp maximum allowable value
	 */
	@Deprecated public static double calcClampedProbExceed(double μ, double σ, double value,
			ExceedanceModel truncType, double truncLevel, double clamp) {

		double clipHi = min(μ + truncLevel * σ, clamp);
		double pHi = gaussProbExceed(μ, σ, clipHi);

		switch (truncType) {
			case TRUNCATION_UPPER_ONLY:
				return gaussProbExceed(μ, σ, value, pHi, 1.0);
			case TRUNCATION_LOWER_UPPER:
				double pLo = gaussProbExceed(μ, σ, μ - truncLevel * σ);
				return gaussProbExceed(μ, σ, value, pHi, pLo);
			default: // NONE case
				return gaussProbExceed(μ, σ, value, 0.0, 1.0);
		}
	}

	/**
	 * Populates the supplied sequence with probabilites of exceeding its
	 * x-values in a normal distribution defined by {@code μ} and {@code σ}. If
	 * {@code clamp} is {@code true}, the lesser of {@code 3*sigma} and
	 * {@code clampVal} is used to truncate the upper part of the underlying
	 * Gaussian.
	 * 
	 * @param μ log mean ground motion
	 * @param σ log std deviation of ground motion
	 * @param values to exceed
	 * @param truncType none, one-sided, or two-sided truncation
	 * @param truncLevel in number of standard deviations
	 * @return a reference to the supplied sequence
	 */
	@Deprecated
	public static XY_Sequence setProbExceed(double μ, double σ, XY_Sequence values,
			ExceedanceModel truncType, double truncLevel) {

		double clip = μ + truncLevel * σ;
		double pHi = gaussProbExceed(μ, σ, clip);

		switch (truncType) {
			case TRUNCATION_UPPER_ONLY:
				return gaussProbExceed(μ, σ, values, pHi, 1.0);
			case TRUNCATION_LOWER_UPPER:
				return gaussProbExceed(μ, σ, values, pHi, 1.0 - pHi);
			default: // NONE case
				return gaussProbExceed(μ, σ, values, 0.0, 1.0);
		}
	}

	/**
	 * Populates the supplied sequence with 'clamped' probabilites of exceeding
	 * its x-values in a normal distribution defined by {@code μ} and {@code σ},
	 * where {@code clamp} is an absolute maximum that may be above or below any
	 * {@code μ + n * σ} truncation. For two-sided truncation, the lower bound
	 * is set to a sigma based value (e.g. {@code μ - n * σ}) that may yield an
	 * assymmetric trunction.
	 * 
	 * @param μ log mean ground motion
	 * @param σ log std deviation of ground motion
	 * @param values to exceed
	 * @param truncType none, one-sided, or two-sided truncation
	 * @param truncLevel in number of standard deviations
	 * @param clamp maximum allowable value
	 * @return a reference to the supplied sequence
	 */
	@Deprecated public static XY_Sequence setClampedProbExceed(double μ, double σ,
			XY_Sequence values, ExceedanceModel truncType, double truncLevel, double clamp) {

		double clipHi = min(μ + truncLevel * σ, clamp);
		double pHi = gaussProbExceed(μ, σ, clipHi);

		switch (truncType) {
			case TRUNCATION_UPPER_ONLY:
				return gaussProbExceed(μ, σ, values, pHi, 1.0);
			case TRUNCATION_LOWER_UPPER:
				double pLo = gaussProbExceed(μ, σ, μ - truncLevel * σ);
				return gaussProbExceed(μ, σ, values, pHi, pLo);
			default: // NONE case
				return gaussProbExceed(μ, σ, values, 0.0, 1.0);
		}
	}

	/*
	 * Compute the probability of exceeding the supplied target value in a
	 * Gaussian distribution assuming no truncation.
	 */
	private static double gaussProbExceed(double μ, double σ, double value) {
		return (erf((μ - value) / (σ * SQRT_2)) + 1.0) * 0.5;
	}

	/*
	 * Compute the probability that a value will be exceeded, subject to upper
	 * and lower probability limits.
	 */
	private static double gaussProbExceed(double μ, double σ, double value, double pHi, double pLo) {
		double p = gaussProbExceed(μ, σ, value);
		return probBoundsCheck((p - pHi) / (pLo - pHi));
	}

	/*
	 * Compute the probabilities that the x-values in {@code values} will be
	 * exceeded. Returns the supplied {@code XY_Sequence} populated with
	 * probabilities.
	 */
	private static XY_Sequence gaussProbExceed(double μ, double σ, XY_Sequence values, double pHi,
			double pLo) {
		for (XY_Point point : values) {
			double p = gaussProbExceed(μ, σ, point.x());
			point.set(probBoundsCheck((p - pHi) / (pLo - pHi)));
		}
		return values;
	}

	/*
	 * TODO does this exist due to double precission errors possibly pushing
	 * probabilities above 1 or below 0 ?? Run a test sometime to determine if P
	 * EVER ventures outside [0, 1]
	 */
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
		for (int i = 1; i < curves.size(); i++) {
			combined.multiply(curves.get(i).complement());
		}
		return combined.complement();
	}

	// @formatter:off
	
	public static final double[] NSHM_PGA_IMLS = { 0.0050, 0.0070, 0.0098, 0.0137, 0.0192, 0.0269, 0.0376, 0.0527, 0.0738, 0.103, 0.145, 0.203, 0.284, 0.397, 0.556, 0.778, 1.09, 1.52, 2.13 };
	
	/* slightly modified version of 5Hz curve, size = 20 */
	public static final double[] NSHM_IMLS = {
		0.0025, 0.0045, 0.0075, 0.0113, 0.0169, 0.0253, 0.0380, 0.0570, 0.0854, 0.128, 0.192, 0.288, 0.432, 0.649, 0.973, 1.46, 2.19, 3.28, 4.92, 7.38 };
	
	public static ArrayXY_Sequence nshmpCurve() {
		return ArrayXY_Sequence.create(NSHM_IMLS, null);
	}
	
	/**
	 * These intensity measure levels expand the standard ranges of values used
	 * at different periods in past NSHMs to a common set that can be used
	 * across all periods with slightly higher discretization than before. It is
	 * derived from ln(-8.8):ln(2.0) with a 0.4 step include the highest ground
	 * motions (~7.5 g) and lower values
	 */
//	public static final double[] NSHM_IMLS = {
//		0.0002, 0.0005,
//		0.001, 0.002, 0.00316, 0.00422, 0.00562, 0.0075,
//		0.01, 0.0133, 0.0178, 0.0237, 0.0316, 0.0422, 0.0562, 0.075,
//		0.1, 0.133, 0.178, 0.237, 0.316, 0.422, 0.562, 0.75,
//		1.0, 1.33, 1.78, 2.37, 3.16, 5.01, 7.94};

	
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
		double mean = 2.0;
		double std = 0.5;
		double value = 2.3;
		
		System.out.println(calcProbExceed(mean, std, value, TRUNCATION_UPPER_ONLY, 3.0));
		System.out.println(calcProbExceed(mean, std, value, TRUNCATION_LOWER_UPPER, 3.0));
		
//		System.out.println(gaussProbExceed(mean, std, value));
//		System.out.println(gaussProbExceed2(mean, std, value));
		
//		System.out.println(Arrays.toString(LOG10_IMLS));
//		System.out.println(LOG10_IMLS.length);
//		
//		double[] localImls = Arrays.copyOf(LOG10_IMLS, LOG10_IMLS.length);
//		DataUtils.pow10(localImls);
		
//		double[] imls = DataUtils.buildCleanSequence(-8.8, 2.0, 0.4, true, 1);
//		System.out.println(Arrays.toString(imls));
//		DataUtils.exp(imls);
//		System.out.println(Arrays.toString(localImls));
	}

}
