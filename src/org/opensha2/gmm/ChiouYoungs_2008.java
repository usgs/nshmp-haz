package org.opensha2.gmm;

import static java.lang.Math.abs;
import static java.lang.Math.cos;
import static java.lang.Math.cosh;
import static java.lang.Math.exp;
import static java.lang.Math.log;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.pow;
import static java.lang.Math.sqrt;
import static java.lang.Math.tanh;

import static org.opensha2.gmm.FaultStyle.NORMAL;
import static org.opensha2.gmm.FaultStyle.REVERSE;
import static org.opensha2.gmm.GmmInput.Field.DIP;
import static org.opensha2.gmm.GmmInput.Field.MW;
import static org.opensha2.gmm.GmmInput.Field.RAKE;
import static org.opensha2.gmm.GmmInput.Field.VS30;
import static org.opensha2.gmm.GmmInput.Field.Z1P0;
import static org.opensha2.gmm.GmmInput.Field.ZTOP;

import org.opensha2.eq.fault.Faults;
import org.opensha2.gmm.GmmInput.Constraints;
import org.opensha2.util.Maths;

import com.google.common.collect.Range;

import java.util.Map;

/**
 * Implementation of the Chiou & Youngs (2008) next generation attenuation
 * relationship for active crustal regions developed as part of <a
 * href="http://peer.berkeley.edu/ngawest/">NGA West I</a>.
 *
 * <p><b>Note:</b> Direct instantiation of {@code GroundMotionModel}s is
 * prohibited. Use {@link Gmm#instance(Imt)} to retrieve an instance for a
 * desired {@link Imt}.
 *
 * <p><p>Reference: Chiou, B.S.-J. and Youngs R.R., 2008, An NGA model for the
 * average horizontal component of peak ground motion and response spectra:
 * Earthquake Spectra, v. 24, n. 1, p. 173-215.
 *
 * <p><b>doi:</b> <a href="http://dx.doi.org/10.1193/1.2894832">
 * 10.1193/1.2894832</a>
 *
 * <p><b>Component:</b> GMRotI50 (geometric mean)
 *
 * @author Peter Powers
 * @see Gmm#CY_08
 */
public final class ChiouYoungs_2008 implements GroundMotionModel {

  static final String NAME = "Chiou & Youngs (2008)";

  static final Constraints CONSTRAINTS = Constraints.builder()
      .set(MW, Range.closed(4.0, 8.5))
      .setDistances(200.0)
      .set(DIP, Faults.DIP_RANGE)
      .set(ZTOP, Range.closed(0.0, 15.0))
      .set(RAKE, Faults.RAKE_RANGE)
      .set(VS30, Range.closedOpen(150.0, 1500.0))
      // TODO borrowed from ASK14
      .set(Z1P0, Range.closed(0.0, 3.0))
      .build();

  static final CoefficientContainer COEFFS = new CoefficientContainer("CY08.csv");

  private static final double C2 = 1.06;
  private static final double C3 = 3.45;
  private static final double C4 = -2.1;
  private static final double C4A = -0.5;
  private static final double CRB = 50;
  private static final double CHM = 3;
  private static final double CG3 = 4;

  private static final class Coefficients {

    final double c1, c1a, c1b, c5, c6, c7, c9, c9a,
        cg1, cg2, cn, cm,
        φ1, φ2, φ3, φ4, φ5, φ6, φ7, φ8,
        τ1, τ2, σ1, σ2, σ3;

    // unused
    // final double c7a, c10, sig4

    Coefficients(Imt imt, CoefficientContainer cc) {
      Map<String, Double> coeffs = cc.get(imt);
      c1 = coeffs.get("c1");
      c1a = coeffs.get("c1a");
      c1b = coeffs.get("c1b");
      c5 = coeffs.get("c5");
      c6 = coeffs.get("c6");
      c7 = coeffs.get("c7");
      c9 = coeffs.get("c9");
      c9a = coeffs.get("c9a");
      cg1 = coeffs.get("cg1");
      cg2 = coeffs.get("cg2");
      cn = coeffs.get("cn");
      cm = coeffs.get("cm");
      φ1 = coeffs.get("phi1");
      φ2 = coeffs.get("phi2");
      φ3 = coeffs.get("phi3");
      φ4 = coeffs.get("phi4");
      φ5 = coeffs.get("phi5");
      φ6 = coeffs.get("phi6");
      φ7 = coeffs.get("phi7");
      φ8 = coeffs.get("phi8");
      τ1 = coeffs.get("tau1");
      τ2 = coeffs.get("tau2");
      σ1 = coeffs.get("sig1");
      σ2 = coeffs.get("sig2");
      σ3 = coeffs.get("sig3");
    }
  }

  private final Coefficients coeffs;

  ChiouYoungs_2008(final Imt imt) {
    coeffs = new Coefficients(imt, COEFFS);
  }

  @Override
  public final ScalarGroundMotion calc(final GmmInput in) {
    return calc(coeffs, in);
  }

  private static final ScalarGroundMotion calc(final Coefficients c, final GmmInput in) {

    // terms used by both mean and stdDev
    double lnYref = calcLnYref(c, in);
    double soilNonLin = calcSoilNonLin(c, in.vs30);

    double μ = calcMean(c, in.vs30, in.z1p0, soilNonLin, lnYref);
    double σ = calcStdDev(c, in.Mw, in.vsInf, soilNonLin, lnYref);

    return DefaultScalarGroundMotion.create(μ, σ);
  }

  // Seismic Source Scaling - aftershock term removed
  private static final double calcLnYref(final Coefficients c, final GmmInput in) {

    double Mw = in.Mw;
    double rJB = in.rJB;
    double rRup = in.rRup;
    double zTop = in.zTop;

    FaultStyle style = GmmUtils.rakeToFaultStyle_NSHMP(in.rake);

    double cosDelta = cos(in.dip * Maths.TO_RAD);
    double rAlt = sqrt(rJB * rJB + zTop * zTop);
    double hw = (in.rX < 0.0) ? 0.0 : 1.0;

    double f_term = (style == REVERSE) ? c.c1a : (style == NORMAL) ? c.c1b : 0.0;

    return c.c1 + (f_term + c.c7 * (zTop - 4.0)) +
        // mainshock term [* (1 - AS)]
        // [(c.c10 + c.c7a * (zTop - 4.0)) * AS +] aftershock term
        C2 * (Mw - 6.0) + ((C2 - C3) / c.cn) * log(1.0 + exp(c.cn * (c.cm - Mw))) +
        C4 * log(rRup + c.c5 * cosh(c.c6 * max(Mw - CHM, 0))) +
        (C4A - C4) * 0.5 * log(rRup * rRup + CRB * CRB) +
        (c.cg1 + c.cg2 / cosh(max(Mw - CG3, 0.0))) * rRup +
        c.c9 * hw * tanh(in.rX * cosDelta * cosDelta / c.c9a) *
            (1 - rAlt / (rRup + 0.001));
  }

  // Mean ground motion model
  private static final double calcMean(final Coefficients c, final double vs30,
      final double z1p0, final double snl, final double lnYref) {

    // basin depth (in meters; z1p0 supplied in km)
    double zBasin = Double.isNaN(z1p0) ? calcBasinZ(vs30) : z1p0 * 1000.0;

    return lnYref + c.φ1 * min(log(vs30 / 1130.0), 0) +
        snl * log((exp(lnYref) + c.φ4) / c.φ4) +
        c.φ5 * (1.0 - 1.0 / cosh(c.φ6 * max(0.0, zBasin - c.φ7))) +
        c.φ8 / cosh(0.15 * max(0.0, zBasin - 15.0));
  }

  private static final double calcSoilNonLin(final Coefficients c, final double vs30) {
    double exp1 = exp(c.φ3 * (min(vs30, 1130.0) - 360.0));
    double exp2 = exp(c.φ3 * (1130.0 - 360.0));
    return c.φ2 * (exp1 - exp2);
  }

  // NSHMP treatment, if vs=760+/-20 -> 40, otherwise compute
  private static final double calcBasinZ(final double vs30) {
    if (abs(vs30 - 760.0) < 20.0) {
      return 40.0;
    }
    return exp(28.5 - 3.82 * log(pow(vs30, 8.0) + pow(378.7, 8.0)) / 8.0);
  }

  // Aleatory uncertainty model
  private static final double calcStdDev(final Coefficients c, final double Mw,
      final boolean vsInf, final double snl, final double lnYref) {

    double Yref = exp(lnYref);

    // Response Term - linear vs. non-linear
    double NL0 = snl * Yref / (Yref + c.φ4);

    // Magnitude thresholds
    double mTest = min(max(Mw, 5.0), 7.0) - 5.0;

    // Inter-event Term
    double τ = c.τ1 + (c.τ2 - c.τ1) / 2.0 * mTest;

    // Intra-event term (aftershock removed)
    double σNL0 = c.σ1 + (c.σ2 - c.σ1) / 2.0 * mTest;
    // [+ c.sig4]

    double vsTerm = vsInf ? c.σ3 : 0.7;
    double NL0sq = (1 + NL0) * (1 + NL0);
    σNL0 *= sqrt(vsTerm + NL0sq);

    // Total model
    return sqrt(τ * τ * NL0sq + σNL0 * σNL0);
  }

}
