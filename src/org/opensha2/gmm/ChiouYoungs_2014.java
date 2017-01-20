package org.opensha2.gmm;

import static java.lang.Math.cos;
import static java.lang.Math.cosh;
import static java.lang.Math.exp;
import static java.lang.Math.log;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.pow;
import static java.lang.Math.sqrt;
import static java.lang.Math.tanh;

import static org.opensha2.geo.GeoTools.TO_RAD;
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

import com.google.common.collect.Range;

import java.util.Map;

/**
 * Implementation of the Chiou & Youngs (2014) next generation attenuation
 * relationship for active crustal regions developed as part of <a
 * href="http://peer.berkeley.edu/ngawest2">NGA West II</a>.
 *
 * <p><b>Note:</b> Direct instantiation of {@code GroundMotionModel}s is
 * prohibited. Use {@link Gmm#instance(Imt)} to retrieve an instance for a
 * desired {@link Imt}.
 *
 * <p><b>Implementation note:</b> 0.01s SA values used for PGA.
 *
 * <p><b>Reference:</b> Chiou, B.S.-J. and Youngs, R.R., 2014, Update of the
 * Chiou and Youngs NGA model for the average horizontal component of peak
 * ground motion and response spectra, Earthquake Spectra, v. 30, n. 3, p.
 * 1117-1153.
 *
 * <p><b>doi:</b> <a href="http://dx.doi.org/10.1193/072813EQS219M">
 * 10.1193/072813EQS219M</a>
 *
 * <p><b>Component:</b> RotD50 (average horizontal)
 *
 * @author Peter Powers
 * @see Gmm#CY_14
 */
public final class ChiouYoungs_2014 implements GroundMotionModel {

  // this model includes 0.12 and 0.17s periods that
  // are not generally supported in other models

  static final String NAME = "Chiou & Youngs (2014)";

  static final Constraints CONSTRAINTS = Constraints.builder()
      .set(MW, Range.closed(3.5, 8.5))
      .setDistances(300.0)
      .set(DIP, Faults.DIP_RANGE)
      .set(ZTOP, Range.closed(0.0, 20.0))
      .set(RAKE, Faults.RAKE_RANGE)
      .set(VS30, Range.closedOpen(180.0, 1500.0))
      // TODO borrowed from ASK14
      .set(Z1P0, Range.closed(0.0, 3.0))
      .build();

  static final CoefficientContainer COEFFS = new CoefficientContainer("CY14.csv");

  private static final double C2 = 1.06;
  private static final double C4 = -2.1;
  private static final double C4A = -0.5;
  private static final double dC4 = C4A - C4;
  private static final double CRB = 50.0;
  private static final double CRBsq = CRB * CRB;
  private static final double C11 = 0.0;
  private static final double PHI6 = 300.0;
  private static final double A = pow(571, 4);
  private static final double B = pow(1360, 4) + A;

  private static final class Coefficients {

    final double c1, c1a, c1b, c1c, c1d, c3, c5, c6, c7, c7b, c9, c9a, c9b, c11b,
        cn, cM, cHM,
        γ1, γ2, γ3,
        φ1, φ2, φ3, φ4, φ5,
        τ1, τ2,
        σ1, σ2, σ3;

    // same for all periods; replaced with constant
    // double c2, c4, c4a, c11, cRB, phi6;

    // unused regional and other coeffs
    // double c8, c8a, c8b, sigma2_JP, gamma_JP_IT, gamma_WN, phi1_JP,
    // phi5_JP, phi6_JP;

    Coefficients(Imt imt, CoefficientContainer cc) {
      Map<String, Double> coeffs = cc.get(imt);
      c1 = coeffs.get("c1");
      c1a = coeffs.get("c1a");
      c1b = coeffs.get("c1b");
      c1c = coeffs.get("c1c");
      c1d = coeffs.get("c1d");
      c3 = coeffs.get("c3");
      c5 = coeffs.get("c5");
      c6 = coeffs.get("c6");
      c7 = coeffs.get("c7");
      c7b = coeffs.get("c7b");
      c9 = coeffs.get("c9");
      c9a = coeffs.get("c9a");
      c9b = coeffs.get("c9b");
      c11b = coeffs.get("c11b");
      cn = coeffs.get("cn");
      cM = coeffs.get("cM");
      cHM = coeffs.get("cHM");
      γ1 = coeffs.get("cgamma1");
      γ2 = coeffs.get("cgamma2");
      γ3 = coeffs.get("cgamma3");
      φ1 = coeffs.get("phi1");
      φ2 = coeffs.get("phi2");
      φ3 = coeffs.get("phi3");
      φ4 = coeffs.get("phi4");
      φ5 = coeffs.get("phi5");
      τ1 = coeffs.get("tau1");
      τ2 = coeffs.get("tau2");
      σ1 = coeffs.get("sigma1");
      σ2 = coeffs.get("sigma2");
      σ3 = coeffs.get("sigma3");
    }
  }

  private final Coefficients coeffs;

  ChiouYoungs_2014(final Imt imt) {
    coeffs = new Coefficients(imt, COEFFS);
  }

  @Override
  public final ScalarGroundMotion calc(final GmmInput in) {
    return calc(coeffs, in);
  }

  private static final ScalarGroundMotion calc(final Coefficients c, final GmmInput in) {

    // terms used by both mean and stdDev
    double saRef = calcSAref(c, in);
    double soilNonLin = calcSoilNonLin(c, in.vs30);

    double μ = calcMean(c, in.vs30, in.z1p0, soilNonLin, saRef);
    double σ = calcStdDev(c, in.Mw, in.vsInf, soilNonLin, saRef);

    return DefaultScalarGroundMotion.create(μ, σ);
  }

  // Seismic Source Scaling -- Equation 11
  private static final double calcSAref(final Coefficients c, final GmmInput in) {

    double Mw = in.Mw;
    double rJB = in.rJB;
    double rRup = in.rRup;
    double zTop = in.zTop;

    FaultStyle style = GmmUtils.rakeToFaultStyle_NSHMP(in.rake);

    // Magnitude scaling
    double r1 = c.c1 + C2 * (Mw - 6.0) + ((C2 - c.c3) / c.cn) *
        log(1.0 + exp(c.cn * (c.cM - Mw)));

    // Near-field magnitude and distance scaling
    double r2 = C4 * log(rRup + c.c5 * cosh(c.c6 * max(Mw - c.cHM, 0.0)));

    // Far-field distance scaling
    double γ = (c.γ1 + c.γ2 / cosh(max(Mw - c.γ3, 0.0)));
    double r3 = dC4 * log(sqrt(rRup * rRup + CRBsq)) + rRup * γ;

    // Scaling with other source variables
    double coshM = cosh(2 * max(Mw - 4.5, 0));
    double cosδ = cos(in.dip * TO_RAD);
    // Center zTop on the zTop-M relation
    double ΔZtop = zTop - calcMwZtop(style, Mw);
    double r4 = (c.c7 + c.c7b / coshM) * ΔZtop + (C11 + c.c11b / coshM) * cosδ * cosδ;
    r4 += (style == REVERSE) ? (c.c1a + c.c1c / coshM)
        : (style == NORMAL) ? (c.c1b + c.c1d / coshM) : 0.0;

    // Hanging-wall effect
    double r5 = 0.0;
    if (in.rX >= 0.0) {
      r5 = c.c9 * cos(in.dip * TO_RAD) *
          (c.c9a + (1.0 - c.c9a) * tanh(in.rX / c.c9b)) *
          (1 - sqrt(rJB * rJB + zTop * zTop) / (rRup + 1.0));
    }

    // Directivity effect (not implemented)
    // cDPP = centered DPP (direct point directivity parameter)
    // double c8 = 0.2154; // corrected from 2.154 12/3/13 per email from
    // Sanaz
    // double c8a = 0.2695;
    // double Mc8 = Mw-c.c8b;
    // double r6 = c8 * exp(-c8a * Mc8 * Mc8) *
    // max(0.0, 1.0 - max(0, rRup - 40.0) / 30.0) *
    // min(max(0, Mw - 5.5) / 0.8, 1.0) * cDPP;

    return exp(r1 + r2 + r3 + r4 + r5);
  }

  private static final double calcSoilNonLin(final Coefficients c, final double vs30) {
    double exp1 = exp(c.φ3 * (min(vs30, 1130.0) - 360.0));
    double exp2 = exp(c.φ3 * (1130.0 - 360.0));
    return c.φ2 * (exp1 - exp2);
  }

  // Mean ground motion model -- Equation 12
  private static final double calcMean(final Coefficients c, final double vs30,
      final double z1p0, final double snl, final double saRef) {

    // Soil effect: linear response
    double sl = c.φ1 * min(log(vs30 / 1130.0), 0.0);

    // Soil effect: nonlinear response (base passed in)
    double snl_mod = snl * log((saRef + c.φ4) / c.φ4);

    // Soil effect: sediment thickness
    double dZ1 = calcDeltaZ1(z1p0, vs30);
    double rkdepth = c.φ5 * (1.0 - exp(-dZ1 / PHI6));

    // total model
    return log(saRef) + sl + snl_mod + rkdepth;
  }

  // Center zTop on the zTop-M relation -- Equations 4, 5
  private static final double calcMwZtop(final FaultStyle style, final double Mw) {
    double mzTop = 0.0;
    if (style == REVERSE) {
      mzTop = (Mw <= 5.849) ? 2.704 : max(2.704 - 1.226 * (Mw - 5.849), 0);
    } else {
      mzTop = (Mw <= 4.970) ? 2.673 : max(2.673 - 1.136 * (Mw - 4.970), 0);
    }
    return mzTop * mzTop;
  }

  // -- Equation 1
  private static final double calcDeltaZ1(final double z1p0, final double vs30) {
    if (Double.isNaN(z1p0)) {
      return 0.0;
    }
    double vsPow4 = vs30 * vs30 * vs30 * vs30;
    return z1p0 * 1000.0 - exp(-7.15 / 4 * log((vsPow4 + A) / B));
  }

  // Aleatory uncertainty model -- Equation 3.9
  private static final double calcStdDev(final Coefficients c, final double Mw,
      final boolean vsInf, final double snl, final double saRef) {

    // Response Term - linear vs. non-linear
    double NL0 = snl * saRef / (saRef + c.φ4);

    // Magnitude thresholds
    double mTest = min(max(Mw, 5.0), 6.5) - 5.0;

    // Inter-event Term
    double τ = c.τ1 + (c.τ2 - c.τ1) / 1.5 * mTest;

    // Intra-event term
    double σNL0 = c.σ1 + (c.σ2 - c.σ1) / 1.5 * mTest;
    double vsTerm = vsInf ? c.σ3 : 0.7;
    double NL0sq = (1 + NL0) * (1 + NL0);
    σNL0 *= sqrt(vsTerm + NL0sq);

    return sqrt(τ * τ * NL0sq + σNL0 * σNL0);
  }

}
