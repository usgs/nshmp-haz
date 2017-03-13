package org.opensha2.mfd;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.Math.exp;
import static java.lang.Math.log;
import static java.lang.Math.pow;

import static org.opensha2.eq.Earthquakes.magToMoment_N_m;

import org.opensha2.data.Data;
import org.opensha2.data.XySequence;

import com.google.common.base.Converter;
import com.google.common.primitives.Doubles;

import java.util.ArrayList;
import java.util.List;

/**
 * Factory and utility methods for working with magnitude frequency
 * distributions (Mfds).
 *
 * TODO note application guidance implied by floats() TODO should Incremental
 * Mfds really descend from EvenlyDiscretizedFunction; if someone passes in
 * non-uniformely spaced mag values, there will be problems
 *
 * @author Peter Powers
 */
public final class Mfds {

  private static final int DEFAULT_TRUNC_TYPE = 2;
  private static final int DEFAULT_TRUNC_LEVEL = 2;

  private Mfds() {}

  /**
   * Creates a new single magnitude {@code IncrementalMfd}.
   *
   * @param mag for MFD
   * @param cumRate total cumulative event rate for lone magnitude bin
   * @param floats {@code true} if ruptures referencing this mfd should float;
   *        {@code false} otherwise
   * @return a new {@code IncrementalMfd}
   */
  public static IncrementalMfd newSingleMFD(double mag, double cumRate, boolean floats) {
    IncrementalMfd mfd = buildIncrementalBaseMFD(mag, mag, 1, floats);
    mfd.set(mag, cumRate);
    return mfd;
  }

  /**
   * Creates a new single magnitude moment-balanced {@code IncrementalMfd}.
   *
   * @param mag for MFD
   * @param moRate total moment rate of lone magnitude bin
   * @param floats {@code true} if ruptures referencing this mfd should float;
   *        {@code false} otherwise
   * @return a new {@code IncrementalMfd}
   */
  public static IncrementalMfd newSingleMoBalancedMFD(double mag, double moRate, boolean floats) {
    double cumRate = moRate / magToMoment_N_m(mag);
    return newSingleMFD(mag, cumRate, floats);
  }

  // TODO The assumption below is pretty bad, especially if we want to
  // recombine
  // MFDs and may end up with uneven spacing. Why does an MFD have to be
  // evenly
  // spaced?? Seems overly restrictive.

  // TODO why on earth does the code below take mags and rates (which are
  // presumed to be the same size) and rather than use the supplied mags
  // rebuilds the mag array introducing double-precision issues.
  //
  // TODO can't this just go direct to XySequence??

  /**
   * Creates a new {@code IncrementalMfd} with the supplied magnitudes and
   * rates. For the MFD returned, {@link IncrementalMfd#floats()} always returns
   * {@code true}.
   *
   * <p><b>NOTE:</b> This method expects evenly spaced magnitudes; if they are
   * not, results are undefined.
   *
   * @param mags for MFD
   * @param rates for MFD
   * @return a new {@code IncrementalMfd}
   */
  public static IncrementalMfd newIncrementalMFD(double[] mags, double[] rates) {
    checkArgument(checkNotNull(mags).length == checkNotNull(rates).length);
    checkArgument(mags.length > 1);
    checkArgument(rates.length > 1);

    IncrementalMfd mfd = buildIncrementalBaseMFD(mags[0], mags[mags.length - 1], mags.length,
        true);
    for (int i = 0; i < mags.length; i++) {
      mfd.set(mags[i], rates[i]);
    }
    return mfd;
  }

  /**
   * Creates a new {@code GaussianMfd} that is doubly-truncated at
   * {@code 2*sigma}.
   *
   * @param mean magnitude
   * @param sigma standard deviation
   * @param size number of magnitude bins inclusive of min and max magnitudes
   * @param cumRate total cumulative rate
   * @param floats {@code true} if ruptures referencing this mfd should float;
   *        {@code false} otherwise
   * @return a new {@code GaussianMfd}
   */
  public static GaussianMfd newGaussianMFD(double mean, double sigma, int size, double cumRate,
      boolean floats) {
    GaussianMfd mfd = buildGaussianBaseMFD(mean, sigma, size, floats);
    mfd.setAllButTotMoRate(mean, sigma, cumRate, DEFAULT_TRUNC_LEVEL, DEFAULT_TRUNC_TYPE);
    return mfd;
  }

  /**
   * Creates a new moment-balanced {@code GaussianMfd} that is doubly-truncated
   * at {@code 2*sigma}. For the MFD returned, {@link IncrementalMfd#floats()}
   * always returns {@code false}.
   *
   * @param mean magnitude
   * @param sigma standard deviation
   * @param size number of magnitude bins inclusive of min and max magnitudes
   * @param moRate total moment rate
   * @param floats {@code true} if ruptures referencing this mfd should float;
   *        {@code false} otherwise
   * @return a new {@code GaussianMfd}
   */
  public static GaussianMfd newGaussianMoBalancedMFD(double mean, double sigma, int size,
      double moRate, boolean floats) {
    GaussianMfd mfd = buildGaussianBaseMFD(mean, sigma, size, floats);
    mfd.setAllButCumRate(mean, sigma, moRate, DEFAULT_TRUNC_LEVEL, DEFAULT_TRUNC_TYPE);
    return mfd;
  }

  /**
   * Creates a new {@code GutenbergRichterMfd}. For the MFD returned,
   * {@link IncrementalMfd#floats()} always returns {@code true}.
   *
   * @param min magnitude
   * @param delta magnitude
   * @param size number of magnitude bins inclusive of min and max magnitudes
   * @param b value (slope of GR relation)
   * @param cumRate total cumulative rate
   * @return a new {@code GutenbergRichterMfd}
   */
  public static GutenbergRichterMfd newGutenbergRichterMFD(double min, double delta, int size,
      double b, double cumRate) {
    GutenbergRichterMfd mfd = buildGutenbergRichterBaseMFD(min, delta, size);
    mfd.setAllButTotMoRate(min, min + (size - 1) * delta, cumRate, b);
    return mfd;
  }

  /**
   * Creates a new moment-balanced {@code GutenbergRichterMfd}. For the MFD
   * returned, {@link IncrementalMfd#floats()} always returns {@code true}.
   *
   * @param min magnitude
   * @param delta magnitude
   * @param size number of magnitude bins inclusive of min and max magnitudes
   * @param b value (slope of GR relation)
   * @param moRate total moment rate
   * @return a new {@code GutenbergRichterMfd}
   */
  public static GutenbergRichterMfd newGutenbergRichterMoBalancedMFD(double min, double delta,
      int size, double b, double moRate) {
    GutenbergRichterMfd mfd = buildGutenbergRichterBaseMFD(min, delta, size);
    mfd.setAllButTotCumRate(min, min + (size - 1) * delta, moRate, b);
    return mfd;
  }

  /*
   * A Tapered GR distribution is difficult to make as a child of GR because to
   * fully initialize a GR requires multiple steps (e.g. scaleTo...) Could do it
   * independently; would require calculateRelativeRates. We'll just create a
   * factory method for now until MFD TODO Builders are impl.
   */

  public static IncrementalMfd newTaperedGutenbergRichterMFD(double min, double delta, int size,
      double a, double b, double corner, double weight) {
    GutenbergRichterMfd mfd = newGutenbergRichterMFD(min, delta, size, b, 1.0);
    double incrRate = incrRate(a, b, min) * weight;
    mfd.scaleToIncrRate(min, incrRate);
    taper(mfd, corner);
    return mfd;
  }

  private static final double TAPERED_LARGE_MAG = 9.05;
  private static final double SMALL_MO_MAG = 4.0;

  /*
   * This Tapered-GR implementation maintains consistency with NSHM but should
   * probably be revisited because scaling varies with choice of
   * TAPERED_LARGE_MAG and SMALL_MO_MAG, below. Although variation is not great,
   * it would probably be better to derive SMALL_MO_MAG from the supllied MFD
   * and use Magnitudes.MAX_MAG for TAPERED_LARGE_MAG instead.
   */
  private static void taper(GutenbergRichterMfd mfd, double mCorner) {

    double minMo = magToMoment_N_m(SMALL_MO_MAG);
    double cornerMo = magToMoment_N_m(mCorner);
    double largeMo = magToMoment_N_m(TAPERED_LARGE_MAG);
    double beta = mfd.get_bValue() / 1.5;
    double binHalfWidth = mfd.getDelta() / 2.0;

    for (int i = 0; i < mfd.getNum(); i++) {
      double mag = mfd.getX(i);
      double magMoLo = magToMoment_N_m(mag - binHalfWidth);
      double magMoHi = magToMoment_N_m(mag + binHalfWidth);

      double magBinCountTapered = magBinCount(minMo, magMoLo, magMoHi, beta, cornerMo);
      double magBinCount = magBinCount(minMo, magMoLo, magMoHi, beta, largeMo);
      double scale = magBinCountTapered / magBinCount;
      mfd.set(i, mfd.getY(i) * scale);
    }
  }

  /*
   * Convenience method for computing the number of events in a tapered GR
   * magnitude bin.
   */
  private static double magBinCount(double minMo, double magMoLo, double magMoHi, double beta,
      double cornerMo) {
    return pareto(minMo, magMoLo, beta, cornerMo) - pareto(minMo, magMoHi, beta, cornerMo);
  }

  /*
   * Complementary Pareto distribution: cumulative number of events with seismic
   * moment greater than magMo with an exponential taper
   */
  private static double pareto(double minMo, double magMo, double beta, double cornerMo) {
    return pow(minMo / magMo, beta) * exp((minMo - magMo) / cornerMo);
  }

  private static IncrementalMfd buildIncrementalBaseMFD(double min, double max, int size,
      boolean floats) {
    return new IncrementalMfd(min, max, size, floats);
  }

  private static GaussianMfd buildGaussianBaseMFD(double mean, double sigma, int size,
      boolean floats) {
    return new GaussianMfd(mean - 2 * sigma, mean + 2 * sigma, size, floats);
  }

  private static GutenbergRichterMfd buildGutenbergRichterBaseMFD(double min, double delta,
      int size) {
    return new GutenbergRichterMfd(min, size, delta);
  }

  /**
   * Computes total moment rate as done by NSHMP code from supplied magnitude
   * info and the Gutenberg-Richter a- and b-values. <b>Note:</b> the a- and
   * b-values assume an incremental distribution.
   *
   * @param mMin minimum magnitude (after adding {@code dMag/2})
   * @param nMag number of magnitudes
   * @param dMag magnitude bin width
   * @param a value (incremental and defined wrt {@code dMag} for M0)
   * @param b value
   * @return the total moment rate
   */
  public static double totalMoRate(double mMin, int nMag, double dMag, double a, double b) {
    double moRate = 1e-10; // start with small, non-zero rate
    double M;
    for (int i = 0; i < nMag; i++) {
      M = mMin + i * dMag;
      moRate += grRate(a, b, M) * magToMoment_N_m(M);
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
   * @return the rate of magnitude {@code M} events
   */
  public static double grRate(double a, double b, double M) {
    return pow(10, a - b * M);
  }

  /**
   * Computes the Gutenberg-Richter incremental rate at the supplied magnitude.
   * Convenience method for {@code N(M) = a*(10^-bm)}.
   *
   * TODO is this confusing? the NSHMP stores a-values in different ways [a A]
   * where a = log10(A); should users just supply grRate() with
   *
   * @param a value (incremental and defined wrt {@code dMag} for M0)
   * @param b value
   * @param mMin minimum magnitude of distribution
   * @return the rate at the supplied magnitude
   */
  public static double incrRate(double a, double b, double mMin) {
    return a * Math.pow(10, -b * mMin);
  }

  /**
   * Determines the number of magnitude bins for the supplied arguments. If dMag
   * does not divide evenly into {@code mMax - mMin}, and the result of this
   * method is used to build a Gutenberg-Richter MFD, the maximum magnitude of
   * the MFD may not equal the {@code mMax} supplied here.
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
   * method returns the Poisson probability of occurence over the specified time
   * period.
   * @param rate (annual) of occurence of some event
   * @param time period of interest
   * @return the Poisson probability of occurrence in the specified {@code time}
   */
  public static double rateToProb(double rate, double time) {
    return 1 - exp(-rate * time);
  }

  /**
   * Given the Poisson probability of the occurence of some event over a
   * specified time period, method returns the annual rate of occurrence of that
   * event.
   * @param P the Poisson probability of an event's occurrence
   * @param time period of interest
   * @return the annnual rate of occurrence of the event
   */
  public static double probToRate(double P, double time) {
    return -log(1 - P) / time;
  }

  /**
   * Return a converter between annual rate and Poisson probability.
   */
  public static Converter<Double, Double> annualRateToProbabilityConverter() {
    return AnnRateToPoissProbConverter.INSTANCE;
  }

  private static final class AnnRateToPoissProbConverter extends Converter<Double, Double> {
    static final AnnRateToPoissProbConverter INSTANCE = new AnnRateToPoissProbConverter();

    @Override
    protected Double doForward(Double rate) {
      return rateToProb(rate, 1.0);
    }

    @Override
    protected Double doBackward(Double prob) {
      return probToRate(prob, 1.0);
    }
  }

  /**
   * Convert an {@code IncrementalMfd} to an immutable {@code XySequence}.
   *
   * @param mfd to convert
   * @return a sequence populated with the values of the supplied
   *         {@code IncrementalMfd}.
   */
  public static XySequence toSequence(IncrementalMfd mfd) {
    return XySequence.createImmutable(
        Doubles.toArray(mfd.xValues()),
        Doubles.toArray(mfd.yValues()));
  }

  /**
   * Convert an {@code IncrementalMfd} to a mutable {@code XySequence}.
   *
   * @param mfd to convert
   * @return a sequence populated with the values of the supplied
   *         {@code IncrementalMfd}.
   */
  public static XySequence toMutableSequence(IncrementalMfd mfd) {
    return XySequence.create(mfd.xValues(), mfd.yValues());
  }

  /**
   * Combine all {@code mfds} into a single sequence.
   * @param mfds
   */
  @Deprecated
  public static XySequence combine(IncrementalMfd... mfds) {
    // TODO slated for removal once MFDs descend from XySequence
    checkArgument(checkNotNull(mfds).length > 0);
    List<XySequence> sequences = new ArrayList<>();
    for (IncrementalMfd mfd : mfds) {
      sequences.add(toSequence(mfd));
    }
    return Data.combine(sequences);
  }

}
