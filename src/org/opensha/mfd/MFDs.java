package org.opensha.mfd;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.Math.exp;
import static java.lang.Math.log;
import static java.lang.Math.pow;
import static org.opensha.eq.Magnitudes.magToMoment;

import org.opensha.eq.Magnitudes;

/**
 * Factory and utility methods for working with magnitude frequency
 * distributions (MFDs).
 * 
 * TODO note application guidance implied by floats() TODO should Incremental
 * MFDs really descend from EvenlyDiscretizedFunction; if someone passes in
 * non-uniformely spaced mag values, there will be problems
 * 
 * @author Peter Powers
 */
public class MFDs {

	private static final int DEFAULT_TRUNC_TYPE = 2;
	private static final int DEFAULT_TRUNC_LEVEL = 2;

	/**
	 * Creates a new single magnitude {@code IncrementalMFD}.
	 * 
	 * @param mag for MFD
	 * @param cumRate total cumulative event rate for lone magnitude bin
	 * @param floats {@code true} if ruptures referencing this mfd should float;
	 *        {@code false} otherwise
	 * @return a new {@code IncrementalMFD}
	 */
	public static IncrementalMFD newSingleMFD(double mag, double cumRate, boolean floats) {
		IncrementalMFD mfd = buildIncrementalBaseMFD(mag, mag, 1, floats);
		mfd.set(mag, cumRate);
		return mfd;
	}

	/**
	 * Creates a new single magnitude moment-balanced {@code IncrementalMFD}.
	 * 
	 * @param mag for MFD
	 * @param moRate total moment rate of lone magnitude bin
	 * @param floats {@code true} if ruptures referencing this mfd should float;
	 *        {@code false} otherwise
	 * @return a new {@code IncrementalMFD}
	 */
	public static IncrementalMFD newSingleMoBalancedMFD(double mag, double moRate, boolean floats) {
		double cumRate = moRate / Magnitudes.magToMoment(mag);
		return newSingleMFD(mag, cumRate, floats);
	}

	/**
	 * Creates a new {@code IncrementalMFD} with the supplied magnitudes and
	 * rates. For the MFD returned, {@link IncrementalMFD#floats()} always
	 * returns {@code true}.
	 * 
	 * <p><b>NOTE:</b> This method expects evenly spaced magnitudes; if they are
	 * not, results are undefined.</p>
	 * 
	 * @param mags for MFD
	 * @param rates for MFD
	 * @return a new {@code IncrementalMFD}
	 */
	public static IncrementalMFD newIncrementalMFD(double[] mags, double[] rates) {
		checkArgument(checkNotNull(mags).length == checkNotNull(rates).length);
		checkArgument(mags.length > 1);
		checkArgument(rates.length > 1);

		IncrementalMFD mfd = buildIncrementalBaseMFD(mags[0], mags[mags.length - 1], mags.length,
			true);
		for (int i = 0; i < mags.length; i++) {
			mfd.set(mags[i], rates[i]);
		}
		return mfd;
	}

	/**
	 * Creates a new {@code GaussianMFD} that is doubly-truncated at
	 * {@code 2*sigma}. For the MFD returned, {@link IncrementalMFD#floats()}
	 * always returns {@code false}.
	 * 
	 * @param mean magnitude
	 * @param sigma standard deviation
	 * @param size number of magnitude bins inclusive of min and max magnitudes
	 * @param cumRate total cumulative rate
	 * @return a new {@code GaussianMFD}
	 */
	public static GaussianMFD newGaussianMFD(double mean, double sigma, int size, double cumRate) {
		GaussianMFD mfd = buildGaussianBaseMFD(mean, sigma, size);
		mfd.setAllButTotMoRate(mean, sigma, cumRate, DEFAULT_TRUNC_LEVEL, DEFAULT_TRUNC_TYPE);
		return mfd;
	}

	/**
	 * Creates a new moment-balanced {@code GaussianMFD} that is
	 * doubly-truncated at {@code 2*sigma}. For the MFD returned,
	 * {@link IncrementalMFD#floats()} always returns {@code false}.
	 * 
	 * @param mean magnitude
	 * @param sigma standard deviation
	 * @param size number of magnitude bins inclusive of min and max magnitudes
	 * @param moRate total moment rate
	 * @return a new {@code GaussianMFD}
	 */
	public static GaussianMFD newGaussianMoBalancedMFD(double mean, double sigma, int size,
			double moRate) {
		GaussianMFD mfd = buildGaussianBaseMFD(mean, sigma, size);
		mfd.setAllButCumRate(mean, sigma, moRate, DEFAULT_TRUNC_LEVEL, DEFAULT_TRUNC_TYPE);
		return mfd;
	}

	/**
	 * Creates a new {@code GutenbergRichterMFD}. For the MFD returned,
	 * {@link IncrementalMFD#floats()} always returns {@code true}.
	 * 
	 * @param min magnitude
	 * @param delta magnitude
	 * @param size number of magnitude bins inclusive of min and max magnitudes
	 * @param b value (slope of GR relation)
	 * @param cumRate total cumulative rate
	 * @return a new {@code GutenbergRichterMFD}
	 */
	public static GutenbergRichterMFD newGutenbergRichterMFD(double min, double delta, int size,
			double b, double cumRate) {
		GutenbergRichterMFD mfd = buildGutenbergRichterBaseMFD(min, delta, size);
		mfd.setAllButTotMoRate(min, min + (size - 1) * delta, cumRate, b);
		return mfd;
	}

	/**
	 * Creates a new moment-balanced {@code GutenbergRichterMFD}. For the MFD
	 * returned, {@link IncrementalMFD#floats()} always returns {@code true}.
	 * 
	 * @param min magnitude
	 * @param delta magnitude
	 * @param size number of magnitude bins inclusive of min and max magnitudes
	 * @param b value (slope of GR relation)
	 * @param moRate total moment rate
	 * @return a new {@code GutenbergRichterMFD}
	 */
	public static GutenbergRichterMFD newGutenbergRichterMoBalancedMFD(double min, double delta,
			int size, double b, double moRate) {
		GutenbergRichterMFD mfd = buildGutenbergRichterBaseMFD(min, delta, size);
		mfd.setAllButTotCumRate(min, min + (size - 1) * delta, moRate, b);
		return mfd;
	}

	private static IncrementalMFD buildIncrementalBaseMFD(double min, double max, int size,
			boolean floats) {
		return new IncrementalMFD(min, max, size, floats);
	}

	private static GaussianMFD buildGaussianBaseMFD(double mean, double sigma, int size) {
		return new GaussianMFD(mean - 2 * sigma, mean + 2 * sigma, size);
	}

	private static GutenbergRichterMFD buildGutenbergRichterBaseMFD(double min, double delta,
			int size) {
		return new GutenbergRichterMFD(min, size, delta);
	}

	/**
	 * Computes total moment rate as done by NSHMP code from supplied magnitude
	 * info and the Gutenberg-Richter a- and b-values. <b>Note:</b> the a- and
	 * b-values assume an incremental distribution.
	 * 
	 * @param mMin minimum magnitude (after adding <code>dMag</code>/2)
	 * @param nMag number of magnitudes
	 * @param dMag magnitude bin width
	 * @param a value (incremental and defined wrt <code>dMag</code> for M0)
	 * @param b value
	 * @return the total moment rate
	 */
	public static double totalMoRate(double mMin, int nMag, double dMag, double a, double b) {
		double moRate = 1e-10; // start with small, non-zero rate
		double M;
		for (int i = 0; i < nMag; i++) {
			M = mMin + i * dMag;
			moRate += grRate(a, b, M) * magToMoment(M);
		}
		return moRate;
	}

	/**
	 * Returns the Gutenberg Richter event rate for the supplied a- and b-values
	 * and magnitude.
	 * 
	 * @param a value (log10 rate of M=0 events)
	 * @param b value
	 * @param M magnitude of interest
	 * @return the rate of magnitude <code>M</code> events
	 */
	public static double grRate(double a, double b, double M) {
		return pow(10, a - b * M);
	}

	/**
	 * Computes the Gutenberg-Richter incremental rate at the supplied
	 * magnitude. Convenience method for <code>N(M) = a*(10^-bm)</code>.
	 * 
	 * TODO is this confusing? the NSHMP stores a-values in different ways [a A]
	 * where a = log10(A); should users just supply grRate() with
	 * 
	 * @param a value (incremental and defined wrt <code>dMag</code> for M0)
	 * @param b value
	 * @param mMin minimum magnitude of distribution
	 * @return the rate at the supplied magnitude
	 */
	public static double incrRate(double a, double b, double mMin) {
		return a * Math.pow(10, -b * mMin);
	}

	/**
	 * Determines the number of magnitude bins for the supplied arguments. If
	 * dMag does not divide evenly into {@code mMax - mMin}, and the result of
	 * this method is used to build a Gutenberg-Richter MFD, the maximum
	 * magnitude of the MFD may not equal the {@code mMax} supplied here.
	 * 
	 * @param mMin minimum magnitude to consider
	 * @param mMax maximum magnitude to consider
	 * @param dMag magnitude delta
	 * @return the number of magnitude bins
	 */
	public static int magCount(double mMin, double mMax, double dMag) {
		return (int) ((mMax - mMin) / dMag + 1.4);
	}

	/**
	 * Given an observed annual rate of occurrence of some event (in num/yr),
	 * method returns the Poisson probability of occurence over the specified
	 * time period.
	 * @param rate (annual) of occurence of some event
	 * @param time period of interest
	 * @return the Poisson probability of occurrence in the specified
	 *         <code>time</code>
	 */
	public static double rateToProb(double rate, double time) {
		return 1 - exp(-rate * time);
	}

	/**
	 * Given the Poisson probability of the occurence of some event over a
	 * specified time period, method returns the annual rate of occurrence of
	 * that event.
	 * @param P the Poisson probability of an event's occurrence
	 * @param time period of interest
	 * @return the annnual rate of occurrence of the event
	 */
	public static double probToRate(double P, double time) {
		return -log(1 - P) / time;
	}

}
