package gov.usgs.earthquake.nshmp.calc;

import static com.google.common.base.Preconditions.checkArgument;
import static gov.usgs.earthquake.nshmp.gmm.Imt.PGA;
import static gov.usgs.earthquake.nshmp.gmm.Imt.PGV;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA0P75;
import static java.lang.Double.isNaN;

import java.util.ArrayList;
import java.util.List;

import gov.usgs.earthquake.nshmp.data.XyPoint;
import gov.usgs.earthquake.nshmp.data.XySequence;
import gov.usgs.earthquake.nshmp.gmm.Imt;
import gov.usgs.earthquake.nshmp.gmm.MultiScalarGroundMotion;
import gov.usgs.earthquake.nshmp.util.Maths;

/**
 * Uncertainty models govern how the values of a complementary cumulative normal
 * distribution (or probability of exceedence) are computed given a mean,
 * {@code μ}, standard deviation, {@code σ}, and other possibly relevant
 * arguments.
 *
 * <p>Each model implements methods that compute the probability of exceeding a
 * single value or a {@link XySequence} of values. Some arguments are only used
 * by some models; for example, {@link #NONE} ignores {@code σ}, but it must be
 * supplied for consistency. See individual models for details.
 *
 * <p>Internally, models use a high precision approximation of the Gauss error
 * function (see Abramowitz and Stegun 7.1.26) when computing exceedances.
 *
 * @author Peter Powers
 */
public enum ExceedanceModel {

  /*
   * TODO We probably want to refactor this to probability model and provide
   * 'occurrence' in addition to exceedence. See commented distribution function
   * at eof.
   */

  /**
   * No uncertainty. Any {@code σ} supplied to methods is ignored yielding a
   * complementary unit step function for a range of values spanning μ.
   *
   * <p>Model ignores {@code σ}, truncation level,{@code n}, and {@code imt}.
   */
  NONE {
    @Override
    double exceedance(double μ, double σ, double n, Imt imt, double value) {
      return Maths.stepFunction(μ, value);
    }

    @Override
    XySequence exceedance(double μ, double σ, double n, Imt imt, XySequence sequence) {
      for (XyPoint p : sequence) {
        p.set(Maths.stepFunction(μ, p.x()));
      }
      return sequence;
    }
  },

  /**
   * No truncation.
   *
   * <p>Model ignores truncation level, {@code n}, and {@code imt}.
   */
  TRUNCATION_OFF {
    @Override
    double exceedance(double μ, double σ, double n, Imt imt, double value) {
      return boundedCcdFn(μ, σ, value, 0.0, 1.0);
    }

    @Override
    XySequence exceedance(double μ, double σ, double n, Imt imt, XySequence sequence) {
      return boundedCcdFn(μ, σ, sequence, 0.0, 1.0);
    }
  },

  /**
   * Upper truncation only at {@code μ + σ * n}.
   *
   * <p>Model ignores {@code imt}.
   */
  TRUNCATION_UPPER_ONLY {
    @Override
    double exceedance(double μ, double σ, double n, Imt imt, double value) {
      return boundedCcdFn(μ, σ, value, prob(μ, σ, n), 1.0);
    }

    @Override
    XySequence exceedance(double μ, double σ, double n, Imt imt, XySequence sequence) {
      return boundedCcdFn(μ, σ, sequence, prob(μ, σ, n), 1.0);
    }
  },

  /**
   * Upper and lower truncation at {@code μ ± σ * n}.
   *
   * <p>Model ignores {@code imt}.
   */
  TRUNCATION_LOWER_UPPER {
    @Override
    double exceedance(double μ, double σ, double n, Imt imt, double value) {
      double pHi = prob(μ, σ, n);
      return boundedCcdFn(μ, σ, value, pHi, 1.0 - pHi);
    }

    @Override
    XySequence exceedance(double μ, double σ, double n, Imt imt, XySequence sequence) {
      double pHi = prob(μ, σ, n);
      return boundedCcdFn(μ, σ, sequence, pHi, 1.0 - pHi);
    }
  },

  /**
   * Fast implementation of upper truncation fixed at 3σ.
   * 
   * <p>Model ignores truncation level, {@code n}, and {@code imt}.
   */
  TRUNCATION_3SIGMA_UPPER {
    @Override
    double exceedance(double μ, double σ, double n, Imt imt, double value) {
      return Ccdfs.UPPER_3SIGMA.get(μ, σ, value);
    }

    @Override
    XySequence exceedance(double μ, double σ, double n, Imt imt, XySequence sequence) {
      return Ccdfs.UPPER_3SIGMA.get(μ, σ, sequence);
    }
  },

  /*
   * This is messy for now; TODO need to figure out the best way to pass in
   * fixed sigmas. The peer models below simply set a value internally as
   * dicated by the test cases that use these models.
   */
  @Deprecated
  PEER_MIXTURE_REFERENCE {
    @Override
    double exceedance(double μ, double σ, double n, Imt imt, double value) {
      return boundedCcdFn(μ, 0.65, value, 0.0, 1.0);
    }

    @Override
    XySequence exceedance(double μ, double σ, double n, Imt imt, XySequence sequence) {
      return boundedCcdFn(μ, 0.65, sequence, 0.0, 1.0);
    }
  },

  /**
   * Model accomodates the heavy tails observed in earthquake data that are not
   * well matched by a purely normal distribution at high ε by combining two
   * distributions (with 50% weight each) created using modulated σ-values (0.8σ
   * and 1.2σ). Model does not impose any truncation.
   *
   * <p>Model ignores truncation level, {@code n}, and {@code imt}.
   */
  PEER_MIXTURE_MODEL {
    @Override
    double exceedance(double μ, double σ, double n, Imt imt, double value) {
      σ = 0.65;
      double p1 = boundedCcdFn(μ, σ * 0.8, value, 0.0, 1.0);
      double p2 = boundedCcdFn(μ, σ * 1.2, value, 0.0, 1.0);
      return (p1 + p2) / 2.0;
    }

    @Override
    XySequence exceedance(double μ, double σ, double n, Imt imt, XySequence sequence) {
      for (XyPoint p : sequence) {
        p.set(exceedance(μ, σ, n, imt, p.x()));
      }
      return sequence;
    }
  },

  /**
   * Model provides {@link Imt}-dependent maxima and exists to support clamps on
   * ground motions that have historically been applied in the CEUS NSHM due to
   * sometimes unreasonably high ground motions implied by {@code μ + 3σ}. Model
   * imposes one-sided (upper) truncation at {@code μ + nσ} if clamp is not
   * exceeded.
   */
  NSHM_CEUS_MAX_INTENSITY {
    @Override
    double exceedance(double μ, double σ, double n, Imt imt, double value) {
      double pHi = prob(μ, σ, n, Math.log(ceusMaxValue(imt)));
      return boundedCcdFn(μ, σ, value, pHi, 1.0);
    }

    @Override
    XySequence exceedance(double μ, double σ, double n, Imt imt, XySequence sequence) {
      double pHi = prob(μ, σ, n, Math.log(ceusMaxValue(imt)));
      return boundedCcdFn(μ, σ, sequence, pHi, 1.0);
    }
  },

  /**
   * Same as {@link #NSHM_CEUS_MAX_INTENSITY}, except that {@code n = 3}.
   * 
   * <p>Model ignores truncation level, {@code n}, and {@code imt}.
   */
  NSHM_CEUS_3SIGMA_MAX_INTENSITY {
    @Override
    double exceedance(double μ, double σ, double n, Imt imt, double value) {
      double lnMaxGm = Math.log(ceusMaxValue(imt));
      double ln3σGm = μ + 3.0 * σ;
      if (ln3σGm < lnMaxGm) {
        return Ccdfs.UPPER_3SIGMA.get(μ, σ, value);
      }
      double pHi = Maths.normalCcdf(μ, σ, lnMaxGm);
      return boundedCcdFn(μ, σ, value, pHi, 1.0);
    }

    @Override
    XySequence exceedance(double μ, double σ, double n, Imt imt, XySequence sequence) {
      double lnMaxGm = Math.log(ceusMaxValue(imt));
      double ln3σGm = μ + 3.0 * σ;
      if (ln3σGm < lnMaxGm) {
        return Ccdfs.UPPER_3SIGMA.get(μ, σ, sequence);
      }
      double pHi = Maths.normalCcdf(μ, σ, lnMaxGm);
      return boundedCcdFn(μ, σ, sequence, pHi, 1.0);
    }
  };

  private static double ceusMaxValue(Imt imt) {
    /*
     * Clamping/limiting is turned off at and above 0.75 sec.
     *
     * TODO few CEUS Gmms support PGV; only Atkinson 06p and 08p. Revisit as it
     * may just be more appropriate to throw a UOE.
     */
    if (imt.isSA()) {
      return imt.ordinal() < SA0P75.ordinal() ? 6.0 : Double.MAX_VALUE;
    }
    if (imt == PGA) {
      return 3.0;
    }
    if (imt == PGV) {
      return 400.0;
    }
    throw new UnsupportedOperationException();
  }

  /**
   * Compute the probability of exceeding a {@code value}.
   *
   * @param μ mean
   * @param σ standard deviation
   * @param n truncation level in units of {@code σ} (truncation = n * σ)
   * @param imt intenisty measure type (only used by
   *        {@link #NSHM_CEUS_MAX_INTENSITY}
   * @param value for which to compute the exceedance probability
   */
  abstract double exceedance(double μ, double σ, double n, Imt imt, double value);

  /**
   * Compute the probability of exceeding a sequence of x-values.
   *
   * @param μ mean
   * @param σ standard deviation
   * @param n truncation level in units of {@code σ} (truncation = n * σ)
   * @param imt intenisty measure type (only used by
   *        {@link #NSHM_CEUS_MAX_INTENSITY}
   * @param sequence the x-values for which to compute exceedance probabilities
   * @return the supplied {@code sequence}
   */
  abstract XySequence exceedance(double μ, double σ, double n, Imt imt, XySequence sequence);

  /*
   * Return a list of exceedance curves, one for each tree branch in the
   * supplied MultiScalarGroundMotion. NOTE that returned curves have NOT been
   * scaled by their branch weight. Given that this scaling will be applied
   * later (in the case of cluster sources) it is imperative that the iteration
   * order of weights applied downstream is the same as the order used here when
   * generating the curves [loop means then sigmas].
   */
  List<XySequence> treeExceedance(
      MultiScalarGroundMotion msgm,
      double n,
      Imt imt,
      XySequence sequence) {

    double[] means = msgm.means();
    double[] sigmas = msgm.sigmas();
    List<XySequence> curves = new ArrayList<>(means.length * sigmas.length);

    for (int i = 0; i < means.length; i++) {
      double μ = means[i];
      for (int j = 0; j < sigmas.length; j++) {
        curves.add(exceedance(μ, sigmas[j], n, imt, XySequence.emptyCopyOf(sequence)));
      }
    }
    return curves;
  }

  /*
   * Computes curves for each gmm branch, scales each by its weight, and returns
   * the summed result in the supplied seqeunce.
   */
  XySequence treeExceedanceCombined(
      MultiScalarGroundMotion msgm,
      double n,
      Imt imt,
      XySequence sequence) {

    double[] means = msgm.means();
    double[] meanWts = msgm.meanWeights();
    double[] sigmas = msgm.sigmas();
    double[] sigmaWts = msgm.sigmaWeights();

    sequence.clear();
    XySequence branchCurve = XySequence.copyOf(sequence);

    for (int i = 0; i < means.length; i++) {
      double μ = means[i];
      double μWt = meanWts[i];
      for (int j = 0; j < sigmas.length; j++) {
        double wt = μWt * sigmaWts[j];
        exceedance(μ, sigmas[j], n, imt, branchCurve);
        sequence.add(branchCurve.multiply(wt));
      }
    }
    return sequence;
  }

  /*
   * Bounded complementary cumulative distribution. Compute the probability that
   * a value will be exceeded, subject to upper and lower probability limits.
   */
  private static double boundedCcdFn(
      double μ,
      double σ,
      double value,
      double pHi,
      double pLo) {

    double p = Maths.normalCcdf(μ, σ, value);
    return probBoundsCheck((p - pHi) / (pLo - pHi));
  }

  /*
   * Bounded complementary cumulative distribution. Compute the probabilities
   * that the x-values in {@code values} will be exceeded, subject to upper and
   * lower probability limits. Return the supplied {@code XySequence} populated
   * with probabilities.
   */
  private static XySequence boundedCcdFn(
      double μ,
      double σ,
      XySequence sequence,
      double pHi,
      double pLo) {

    for (XyPoint p : sequence) {
      p.set(boundedCcdFn(μ, σ, p.x(), pHi, pLo));
    }
    return sequence;
  }

  /*
   * For truncated distributions, p may be out of range. For upper truncations,
   * p may be less than pHi, yielding a negative value in boundedCcdFn(); for
   * lower truncations, p may be greater than pLo, yielding a value > 1.0 in
   * boundedCcdFn().
   */
  private static double probBoundsCheck(double p) {
    return (p < 0.0) ? 0.0 : (p > 1.0) ? 1.0 : p;
  }

  /*
   * Compute ccd value at μ + nσ.
   */
  private static double prob(double μ, double σ, double n) {
    return Maths.normalCcdf(μ, σ, μ + n * σ);
  }

  /*
   * Compute ccd value at min(μ + nσ, max).
   */
  private static double prob(double μ, double σ, double n, double max) {
    return Maths.normalCcdf(μ, σ, Math.min(μ + n * σ, max));
  }

  /*
   * Computes joint probability of exceedence given the occurrence of a cluster
   * of events: [1 - [(1-PE1) * (1-PE2) * ...]]. The probability of exceedance
   * of each individual event is given in the supplied curves.
   *
   * @param curves for which to calculate joint probability of exceedance
   */
  static XySequence clusterExceedance(List<XySequence> curves) {
    XySequence combined = XySequence.copyOf(curves.get(0)).complement();
    for (int i = 1; i < curves.size(); i++) {
      combined.multiply(curves.get(i).complement());
    }
    return combined.complement();
  }

  /* Wrapper class avoids unnecessary initialization of array(s). */
  private static final class Ccdfs {
    static final CcdfArray UPPER_3SIGMA = new CcdfArray(Double.NaN, 3.0);
  }

  /* Ensures a clean Δ. */
  private static final int PRECISION = 8;
  private static final int CCND_ARRAY_SIZE = 10000001;
  private static final double EMAX = 4.0;

  /*
   * Complementary cumulative standard normal distribution. Array may be
   * initialized with truncated values (lower and/or upper) supplied in units of
   * σ. Any truncations must fall with in the discretization limits of the
   * table, which are currently set at EMAX = ±4.0. For no lower or upper
   * truncation, supply a value of Double.NaN for εMin or εMax.
   * 
   * Probabilities below -EMAX are set to 1, and probabilities above EMAX are
   * set to 0.
   * 
   * The use of 'Lo' or 'Hi' in variable names refers to the lower
   * (probabilities closer to 1) and upper (probabilities closer to 0) ends of
   * the ccdn, respectively.
   */
  private static final class CcdfArray {

    private final double[] p;
    private final double Δε;
    private final double εMin;
    private final double εMax;

    CcdfArray(double εMin, double εMax) {

      checkArgument(isNaN(εMin) || εMin >= -EMAX, "εMin [%s] < [%s]", εMin, -EMAX);
      checkArgument(isNaN(εMax) || εMax <= EMAX, "εMax [%s] > [%s]", εMax, EMAX);

      this.εMin = isNaN(εMin) ? -EMAX : εMin;
      this.εMax = isNaN(εMax) ? EMAX : εMax;

      checkArgument(this.εMin < this.εMax, "εMin [%s] ≥ εMax [%s]", this.εMin, this.εMax);

      p = new double[CCND_ARRAY_SIZE];

      double pLo = isNaN(εMin) ? 1.0 : Maths.normalCcdf(0.0, 1.0, this.εMin);
      double pHi = isNaN(εMax) ? 0.0 : Maths.normalCcdf(0.0, 1.0, this.εMax);

      double Δ = Maths.round(1.0 / (CCND_ARRAY_SIZE - 1), PRECISION);
      Δε = Δ * (this.εMax - this.εMin);

      p[0] = 1.0;
      for (int i = 1; i < p.length - 1; i++) {
        double pi = Maths.normalCcdf(0.0, 1.0, this.εMin + Δε * i);
        p[i] = (pi - pHi) / (pLo - pHi);
      }
      p[CCND_ARRAY_SIZE - 1] = 0.0;
    }

    double get(double μ, double σ, double x) {
      double ε = Maths.epsilon(μ, σ, x);
      if (ε < this.εMin) {
        return 1.0;
      }
      if (ε <= this.εMax) {
        int i = (int) Math.round((ε - this.εMin) / Δε);
        return p[i];
      }
      return 0.0;
    }

    XySequence get(double μ, double σ, XySequence sequence) {
      for (XyPoint p : sequence) {
        p.set(get(μ, σ, p.x()));
      }
      return sequence;
    }
  }

}
