package gov.usgs.earthquake.nshmp.gmm;

import static gov.usgs.earthquake.nshmp.gmm.Imt.SA0P5;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA1P0;
import static java.lang.Math.exp;
import static java.lang.Math.log;
import static java.lang.Math.sqrt;

import java.util.Set;

import com.google.common.collect.Iterables;
import com.google.common.collect.Streams;

import gov.usgs.earthquake.nshmp.data.Interpolator;

/**
 * BETA implementation<p>
 *
 * Implement support for Conditional PGV for crustal-earthquake GMM without
 * native PGV support.
 *
 * Recommended for use up to M8.5 crustal earthquakes. The models for PGA andd
 * PSA(T=1) are only applicable for M5 and greater crustal earthquakes.
 *
 * <p><b>Implementation notes:</b><ul>
 *
 * <li>Conditional PGV model for vertical component not yet implemented</li>
 *
 * <li>PGA-based and PSA(T=1)-based models for PGV (for GMMs with sparse
 * available IMTs) not yet implemented</li> </ul>
 *
 * <p><b>Reference:</b> Abrahamson, N.A. and Bhasin, S., 2020, Conditional
 * ground-motion model for Peak Ground Velocity for active crustal regions. <a
 * href="https://peer.berkeley.edu/sites/default/files/2020_05_abrahamson_final.pdf">PEER
 * Report 2020/05</a>, October, 2020.
 *
 * @author U.S. Geological Survey
 *
 */
class UsgsPgvSupport {

  // TODO does conditional model behave for different site classes

  /*
   * Coefficients are fixed and not keyed to period so we can include them as
   * static final fields in this class.
   */

  // Equation 3.6 coefficients, Tpgv
  private static final double b1 = -4.09;
  private static final double b2 = 0.66;
  // private static final double sigmaTpgv = 0.2;

  // Table 3.2 Conditional PGV model coefficients for crustal earthquakes
  // (HORIZONTAL)
  // Note: typo in Table 3.2 lists two a7 coefficients and no a8
  private static final double a1 = 5.39;
  private static final double a2 = 0.799;
  private static final double a3 = 0.654;
  private static final double a4 = 0.479;
  private static final double a5 = -0.062;
  private static final double a6 = -0.359;
  private static final double a7 = -0.134;
  private static final double a8 = 0.023;
  // private static final double phi = 0.29;
  // private static final double tau = 0.16;
  private static final double σPgvCond = 0.33;

  // // Table 3.2 Conditional PGV model coefficients for crustal earthquakes
  // // (VERTICAL)
  // private static final double a1_v = 5.51;
  // private static final double a2_v = 0.763;
  // private static final double a3_v = 0.538;
  // private static final double a4_v = 0.131;
  // private static final double a5_v = -0.106;
  // private static final double a6_v = -0.431;
  // private static final double a7_v = -0.089;
  // private static final double a8_v = 0.017;
  // // private static final double phi_v = 0.32;
  // // private static final double tau_v = 0.15;
  // private static final double sigma_v = 0.35;

  private UsgsPgvSupport() {}

  /**
   * Calculates conditional PGV for the specified GMM
   *
   * TODO: rename method for AB20 model
   *
   * @param gmm of type Gmm
   * @param in of type GmmInput
   * @return PGV ScalarGroundMotion
   */
  static ScalarGroundMotion calcAB20Pgv(Gmm gmm, GmmInput in) {

    // calculate SA(TPgv), sigma(TPgv)
    ScalarGroundMotion gmTPgv = calcSaGroundMotion(gmm, in);

    // process ground motion according to model

    // Eq. 3.8: f1(M) term
    double f1;
    if (in.Mw < 5.0) {
      f1 = a2;
    } else if (in.Mw <= 7.5) {
      f1 = a2 + (a3 - a2) * (in.Mw - 5.0) / 2.5;
    } else {
      f1 = a3;
    }

    // Eq. 3.7
    double lnPgv = a1 + f1 * gmTPgv.mean() +
        a4 * (in.Mw - 6.0) +
        a5 * Math.pow(8.5 - in.Mw, 2) +
        a6 * log(in.rRup + 5.0 * exp(0.4 * (in.Mw - 6.0))) +
        (a7 + a8 * (in.Mw - 5.0)) * log(in.vs30 / 425);

    // Eq. 15: aleatory variability
    double σPgv = sqrt(f1 * f1 * gmTPgv.sigma() * gmTPgv.sigma() + σPgvCond * σPgvCond);

    /* Is there another way to get out the PSA(Tpgv) value for validation? */
    // // debug dump: Mw, rRup, Vs30, TPgv, gmTPgv_mean, gmTPgv_sigma, f1,
    // // lnPgv, pgv, sigmaPgv
    // System.out.printf("%.2f\t%.1f\t%.1f\t%.6f\t%.6e\t%.6f\t%.6f\t%.6f\t%.6e\t%.6f\n",
    // in.Mw, in.rRup, in.vs30, computeTPgv(in.Mw), gmTPgv.mean(),
    // gmTPgv.sigma(), f1,
    // lnPgv, exp(lnPgv), σPgv);

    return DefaultScalarGroundMotion.create(lnPgv, σPgv);
  }

  /*
   * Internal calculation of the SA ground motion for the specified GMM for
   * input to the conditional PGV model
   *
   * @param Gmm gmm
   *
   * @param GmmInput in
   *
   * @return ScalarGroundMotion for target T(PGV)
   */
  static ScalarGroundMotion calcSaGroundMotion(Gmm gmm, GmmInput in) {
    Set<Imt> saImts = gmm.responseSpectrumIMTs();
    // need to verify that this Set is being returned with ascending
    // iteration order; that is, when we loop over them
    //
    // Iteration order is controlled by set1 in the call to
    // "Sets.intersection(set1, set2)" in gmm.responseSpectrumIMTs()
    // --> If we reverse the order of set1, set2 in that call, the returned
    // set saImts will be sorted in the same order as the IMT Enums
    //
    // Other places responseSpectrumIMTs() is called:
    // HazMatImpl and ResponseSpectra, both in nshmp-haz-v2

    double targetPeriod = computeTPgv(in.Mw);

    // GMM sa Imt bounds
    Imt minImt = saImts.stream().findFirst().get();
    Imt maxImt = Streams.findLast(saImts.stream()).get();
    double minPeriod = minImt.period();
    double maxPeriod = maxImt.period();

    // Check that target is between min max
    // checkState(expression, errorMessage);

    // sa Imt interpolation bounds
    // need periods for interpolation
    // need Imts to compute ground motion
    Imt lowerImt = minImt;
    Imt upperImt = minImt;
    double lowerPeriod = minPeriod; // 0.01s SA known minimum
    double upperPeriod = minPeriod;

    // TODO: throw error if TargetPeriod is outside of the range of lowerPeriod
    // - upperPeriod supported by gmm

    // having set the lower bound with the first element,
    // skip it when looping
    for (Imt saImt : Iterables.skip(saImts, 1)) {
      upperImt = saImt;
      upperPeriod = saImt.period();
      if (upperPeriod > targetPeriod) {
        break;
      }
      lowerImt = upperImt;
      lowerPeriod = upperPeriod;
    }

    /*
     * new InterpolatedGmm(...) currently requires the target to be an IMT, so
     * using this here would require an alternate implementation of the
     * InterpolatedGmm constructor...
     *
     * Also, we may automatically calculate the spectra at all available IMTs
     * for the GMM up front, so we may just be able to get those values and
     * interpolate without new calls to calc()
     */
    ScalarGroundMotion lowerGm = gmm.instance(lowerImt).calc(in);
    ScalarGroundMotion upperGm = gmm.instance(upperImt).calc(in);

    /*
     * Use the static interpolator method findY. I'm not sure yet if we want to
     * use log(groundMotion), TBD. --> The paper suggests that "the value of
     * PSA(TPgv) should be interpolated using log-log interpolation on the
     * available PSA(T) values"
     */
    double μ = Interpolator.findY(
        log(lowerPeriod), lowerGm.mean(),
        log(upperPeriod), upperGm.mean(),
        log(targetPeriod));

    double σ = Interpolator.findY(
        log(lowerPeriod), lowerGm.sigma(),
        log(upperPeriod), upperGm.sigma(),
        log(targetPeriod));

    return DefaultScalarGroundMotion.create(μ, σ);
  }

  /*
   * Check and document here the range of periods that are spanned by Mw 4.5 to
   * 8.5; I haven't read the paper in detail; I did see 8.5 mentioned as a
   * reasonable upper limit in Mw, but I don't know what the recommended lower
   * limit is.
   *
   * - Recommended upper limit on Mw is 8.5 - Lower limit on Mw is unspecified,
   * but f1 term is conditionally defined for M < 5, and coefficients for
   * PGA-based and PSA(T=1)-based models for PGV may be specified for M>5 but
   * there is a typo in the paper here
   *
   * Also check some higher Mw values (that we have in the model) so that we can
   * make some determinations, if necessary, about how to enforce a valid range
   * of magnitudes. I asume that for the range supported by the model that the
   * resultant periods will always be within 0.01 and 10s.
   *
   * - TPgv ranges from 0.017 to 8.85 sec for M = 0.0 to 9.5, respectively.
   */
  static double computeTPgv(double M) {
    // Eq. 3.6
    return exp(b1 + b2 * M);
  }

  /*
   * Calculates PGV (cm/s) from the SA ground motion from the specified GMM
   * using the Newmark & Hall (1982) method
   *
   * Ported from: shakemap/shakelib/conversions/imt/newmark_hall_1982.py
   *
   * Note: calculates NH82 PGV in units of cm/s
   */
  static ScalarGroundMotion calcPgvNH(Gmm gmm, GmmInput in) {

    // get SA(1.0 s) for the specified Gmm
    ScalarGroundMotion gm1p0 = calcSaGroundMotionNH(gmm, in);

    // convert amplitude to PGV in cm/s (rounded as in shakemap/shakelib)
    // published factor: 386.4 / 2 / PI / 1.65 * SA(1.0) = PGV in inches/s
    double conversionFactor = log(37.27 * 2.54);
    double σPgvNH = 0.5146578;

    double lnPgv = gm1p0.mean() + conversionFactor;

    double σPgv = sqrt(gm1p0.sigma() * gm1p0.sigma() + σPgvNH * σPgvNH);

    return DefaultScalarGroundMotion.create(lnPgv, σPgv);
  }

  /*
   * Internal calculation of the SA ground motion for the specified GMM for
   * conversion to PGV using the Newmark & Hall (1982) method
   */
  private static ScalarGroundMotion calcSaGroundMotionNH(Gmm gmm, GmmInput in) {
    return gmm.instance(SA1P0).calc(in);
  }

  static ScalarGroundMotion calcPgvBA06(Gmm gmm, GmmInput in) {
    // From AB20 PEER report
    /*
     * Bommer and Alarcon (2006) conditional PGV (cm/s):
     *
     * ln(PGV) = 3.89 + ln[PSA(T=0.5)]
     *
     * sigma?
     */

    double conversionFactor = 3.89;

    ScalarGroundMotion gm0p5 = gmm.instance(SA0P5).calc(in);

    double lnPgv = conversionFactor + gm0p5.mean();

    return DefaultScalarGroundMotion.create(lnPgv, gm0p5.sigma());
  }

  static ScalarGroundMotion calcPgvHW15(Gmm gmm, GmmInput in) {
    // From AB20 PEER report:
    /*
     * Huang and Whittaker (2015) conditional PGV (cm/s):
     *
     * ln(PGV) = 3.75 + ln[PSA(T=1)]] + 0.13 * Mw
     *
     * sigma(PGV|PSA) = 0.43, or 0.33?
     */

    ScalarGroundMotion gm1p0 = gmm.instance(SA1P0).calc(in);

    double lnPgv = 3.75 + gm1p0.mean() + 0.13 * in.Mw;

    double σPgv = sqrt(gm1p0.sigma() * gm1p0.sigma() + 0.43 * 0.43);

    return DefaultScalarGroundMotion.create(lnPgv, σPgv);
  }

}
