package org.opensha2.gmm;

import static java.lang.Math.cos;
import static java.lang.Math.exp;
import static java.lang.Math.log;
import static java.lang.Math.max;
import static java.lang.Math.pow;
import static java.lang.Math.sqrt;

import static org.opensha2.gmm.FaultStyle.NORMAL;
import static org.opensha2.gmm.GmmInput.Field.DIP;
import static org.opensha2.gmm.GmmInput.Field.MW;
import static org.opensha2.gmm.GmmInput.Field.RAKE;
import static org.opensha2.gmm.GmmInput.Field.VS30;
import static org.opensha2.gmm.GmmInput.Field.WIDTH;
import static org.opensha2.gmm.GmmInput.Field.Z2P5;
import static org.opensha2.gmm.GmmInput.Field.ZHYP;
import static org.opensha2.gmm.GmmInput.Field.ZTOP;
import static org.opensha2.gmm.Imt.PGA;
import static org.opensha2.gmm.Imt.SA0P01;
import static org.opensha2.gmm.Imt.SA0P25;

import org.opensha2.eq.fault.Faults;
import org.opensha2.gmm.GmmInput.Constraints;
import org.opensha2.util.Maths;

import com.google.common.collect.Range;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Implementation of the Campbell & Bozorgnia (2014) next generation ground
 * motion model for active crustal regions developed as part of <a
 * href="http://peer.berkeley.edu/ngawest2">NGA West II</a>.
 *
 * <p><b>Note:</b> Direct instantiation of {@code GroundMotionModel}s is
 * prohibited. Use {@link Gmm#instance(Imt)} to retrieve an instance for a
 * desired {@link Imt}.
 *
 * <p><b>Reference:</b> Campbell, K.W., and Bozorgnia, Y., 2014, NGA-West2
 * ground motion model for the average horizontal components of PGA, PGV, and
 * 5%-damped linear acceleration response spectra: Earthquake Spectra, v. 30, n.
 * 3, p. 1087-1115.
 *
 * <p><b>doi:</b> <a href="http://dx.doi.org/10.1193/062913EQS175M">
 * 10.1193/062913EQS175M</a>
 *
 * <p><b>Component:</b> RotD50 (average horizontal)
 *
 * @author Peter Powers
 * @see Gmm#CB_14
 */
public final class CampbellBozorgnia_2014 implements GroundMotionModel {

  static final String NAME = "Campbell & Bozorgnia (2014)";

  static final CoefficientContainer COEFFS = new CoefficientContainer("CB14.csv");

  static final Constraints CONSTRAINTS = Constraints.builder()
      // TODO there are rake dependent M restrictions
      .set(MW, Range.closed(3.3, 8.5))
      .setDistances(300.0)
      // TODO actually is 15-90
      .set(DIP, Faults.DIP_RANGE)
      .set(WIDTH, Faults.CRUSTAL_WIDTH_RANGE)
      .set(ZHYP, Range.closed(0.0, 20.0))
      .set(ZTOP, Range.closed(0.0, 20.0))
      .set(RAKE, Faults.RAKE_RANGE)
      .set(VS30, Range.closedOpen(150.0, 1500.0))
      .set(Z2P5, Range.closed(0.0, 10.0))
      .build();

  private static final double H4 = 1.0;
  private static final double C = 1.88;
  private static final double N = 1.18;
  private static final double PHI_LNAF_SQ = 0.09; // 0.3^2

  private static final Set<Imt> SHORT_PERIODS = EnumSet.range(SA0P01, SA0P25);

  private static final class Coefficients {

    final Imt imt;
    final double c0, c1, c2, c3, c4, c5, c6, c7, c9, c10, c11, c14, c16, c17, c18, c19, c20,
        a2,
        h1, h2, h3, h5, h6,
        k1, k2, k3,
        φ1, φ2,
        τ1, τ2,
        ρ;

    // same for all periods; replaced with constant; or unused (c8)
    // double c8, c12, c13, h4, c, n, phi_lnaf;

    // unused regional and other coeffs
    // double c15, Dc20_CA, Dc20_JP, Dc20_CH, phiC;

    Coefficients(Imt imt, CoefficientContainer cc) {
      this.imt = imt;
      Map<String, Double> coeffs = cc.get(imt);
      c0 = coeffs.get("c0");
      c1 = coeffs.get("c1");
      c2 = coeffs.get("c2");
      c3 = coeffs.get("c3");
      c4 = coeffs.get("c4");
      c5 = coeffs.get("c5");
      c6 = coeffs.get("c6");
      c7 = coeffs.get("c7");
      c9 = coeffs.get("c9");
      c10 = coeffs.get("c10");
      c11 = coeffs.get("c11");
      c14 = coeffs.get("c14");
      c16 = coeffs.get("c16");
      c17 = coeffs.get("c17");
      c18 = coeffs.get("c18");
      c19 = coeffs.get("c19");
      c20 = coeffs.get("c20");
      a2 = coeffs.get("a2");
      h1 = coeffs.get("h1");
      h2 = coeffs.get("h2");
      h3 = coeffs.get("h3");
      h5 = coeffs.get("h5");
      h6 = coeffs.get("h6");
      k1 = coeffs.get("k1");
      k2 = coeffs.get("k2");
      k3 = coeffs.get("k3");
      φ1 = coeffs.get("phi1");
      φ2 = coeffs.get("phi2");
      τ1 = coeffs.get("tau1");
      τ2 = coeffs.get("tau2");
      ρ = coeffs.get("rho");
    }
  }

  private final Coefficients coeffs;
  private final Coefficients coeffsPGA;

  CampbellBozorgnia_2014(final Imt imt) {
    coeffs = new Coefficients(imt, COEFFS);
    coeffsPGA = new Coefficients(PGA, COEFFS);
  }

  @Override
  public final ScalarGroundMotion calc(final GmmInput in) {
    return calc(coeffs, coeffsPGA, in);
  }

  private static final ScalarGroundMotion calc(final Coefficients c, final Coefficients cPGA,
      final GmmInput in) {

    FaultStyle style = GmmUtils.rakeToFaultStyle_NSHMP(in.rake);
    double vs30 = in.vs30;
    double z2p5 = in.z2p5;

    // calc pga rock reference value using CA vs30 z2p5 value: 0.398
    double pgaRock = (vs30 < c.k1) ? exp(calcMean(cPGA, style, 1100.0, 0.398, 0.0, in)) : 0.0;

    double μ = calcMean(c, style, vs30, z2p5, pgaRock, in);

    // prevent SA<PGA for short periods
    if (SHORT_PERIODS.contains(c.imt)) {
      double pgaMean = calcMean(cPGA, style, vs30, z2p5, pgaRock, in);
      μ = max(μ, pgaMean);
    }

    double σ = calcStdDev(c, cPGA, in.Mw, in.vs30, pgaRock);

    return DefaultScalarGroundMotion.create(μ, σ);
  }

  // Mean ground motion model -- we use supplied vs30 and z2p5 rather than
  // values from input to impose 1100 and 0.398 when computing rock reference
  private static final double calcMean(final Coefficients c, final FaultStyle style,
      final double vs30, final double z2p5, final double pgaRock, final GmmInput in) {

    double Mw = in.Mw;
    double rRup = in.rRup;
    double rX = in.rX;
    double dip = in.dip;

    // Magnitude term -- Equation 2
    double Fmag = c.c0 + c.c1 * Mw;
    if (Mw > 6.5) {
      Fmag += c.c2 * (Mw - 4.5) + c.c3 * (Mw - 5.5) + c.c4 * (Mw - 6.5);
    } else if (Mw > 5.5) {
      Fmag += c.c2 * (Mw - 4.5) + c.c3 * (Mw - 5.5);
    } else if (Mw > 4.5) {
      Fmag += c.c2 * (Mw - 4.5);
    }

    // Distance term -- Equation 3
    double r = sqrt(rRup * rRup + c.c7 * c.c7);
    double Fr = (c.c5 + c.c6 * Mw) * log(r);

    // Style-of-Faulting term -- Equations 4, 5, 6
    // c8 is always 0 so REVERSE switch has been removed
    double Fflt = 0.0;
    if (style == NORMAL && Mw > 4.5) {
      Fflt = c.c9;
      if (Mw <= 5.5) {
        Fflt *= (Mw - 4.5);
      }
    }

    // Hanging-Wall term
    double Fhw = 0.0;
    // short-circuit: f4 is 0 if rX < 0, Mw <= 5.5, zTop > 16.66
    // these switches have been removed below
    if (rX >= 0.0 && Mw > 5.5 && in.zTop <= 16.66) { // short-circuit

      // Jennifer Donahue's HW Model plus CB08 distance taper
      // -- Equations 9, 10, 11 & 12
      double r1 = in.width * cos(dip * Maths.TO_RAD);
      double r2 = 62.0 * Mw - 350.0;
      double rXr1 = rX / r1;
      double rXr2r1 = (rX - r1) / (r2 - r1);
      double f1_rX = c.h1 + c.h2 * rXr1 + c.h3 * (rXr1 * rXr1);
      double f2_rX = H4 + c.h5 * (rXr2r1) + c.h6 * rXr2r1 * rXr2r1;

      // ... rX -- Equation 8
      double Fhw_rX = (rX >= r1) ? max(f2_rX, 0.0) : f1_rX;

      // ... rRup -- Equation 13
      double Fhw_rRup = (rRup == 0.0) ? 1.0 : (rRup - in.rJB) / rRup;

      // ... magnitude -- Equation 14
      double Fhw_m = 1.0 + c.a2 * (Mw - 6.5);
      if (Mw <= 6.5) {
        Fhw_m *= (Mw - 5.5);
      }

      // ... depth -- Equation 15
      double Fhw_z = 1.0 - 0.06 * in.zTop;

      // ... dip -- Equation 16
      double Fhw_d = (90.0 - dip) / 45.0;

      // ... total -- Equation 7
      Fhw = c.c10 * Fhw_rX * Fhw_rRup * Fhw_m * Fhw_z * Fhw_d;
    }

    // Shallow Site Response term - pgaRock term is computed through an
    // initial call to this method with vs30=1100; 1100 is higher than any
    // k1 value so else condition always prevails -- Equation 18
    double vsk1 = vs30 / c.k1;
    double Fsite = (vs30 <= c.k1) ? c.c11 * log(vsk1) +
        c.k2 * (log(pgaRock + C * pow(vsk1, N)) - log(pgaRock + C))
        : (c.c11 + c.k2 * N) * log(vsk1);

    // Basin Response term -- Equation 20
    // update z2p5 with CA model if not supplied -- Equation 33
    double z2p5copy = z2p5;
    if (Double.isNaN(z2p5)) {
      z2p5copy = exp(7.089 - 1.144 * log(vs30));
    }
    double Fsed = 0.0;
    if (z2p5copy <= 1.0) {
      Fsed = c.c14 * (z2p5copy - 1.0);
    } else if (z2p5copy > 3.0) {
      Fsed = c.c16 * c.k3 * exp(-0.75) * (1.0 - exp(-0.25 * (z2p5copy - 3.0)));
    }

    // Hypocentral Depth term -- Equations 21, 22, 23
    double zHyp = in.zHyp;
    double Fhyp = (zHyp <= 7.0) ? 0.0 : (zHyp <= 20.0) ? zHyp - 7.0 : 13.0;
    if (Mw <= 5.5) {
      Fhyp *= c.c17;
    } else if (Mw <= 6.5) {
      Fhyp *= (c.c17 + (c.c18 - c.c17) * (Mw - 5.5));
    } else {
      Fhyp *= c.c18;
    }

    // Fault Dip term -- Equation 24
    double Fdip = (Mw > 5.5) ? 0.0 : (Mw > 4.5) ? c.c19 * (5.5 - Mw) * dip : c.c19 * dip;

    // Anelastic Attenuation term -- Equation 25
    double Fatn = (rRup > 80.0) ? c.c20 * (rRup - 80.0) : 0.0;

    // total model -- Equation 1
    return Fmag + Fr + Fflt + Fhw + Fsite + Fsed + Fhyp + Fdip + Fatn;
  }

  // Aleatory uncertainty model
  private static final double calcStdDev(final Coefficients c, final Coefficients cPGA,
      final double Mw, final double vs30, final double pgaRock) {

    // -- Equation 31
    double vsk1 = vs30 / c.k1;
    double alpha = (vs30 < c.k1) ? c.k2 * pgaRock *
        (1 / (pgaRock + C * pow(vsk1, N)) - 1 / (pgaRock + C)) : 0.0;

    // Magnitude dependence -- Equations 29 & 30
    double tau_lnYB, tau_lnPGAB, phi_lnY, phi_lnPGAB;
    if (Mw <= 4.5) {
      tau_lnYB = c.τ1;
      phi_lnY = c.φ1;
      tau_lnPGAB = cPGA.τ1;
      phi_lnPGAB = cPGA.φ1;
    } else if (Mw < 5.5) {
      tau_lnYB = stdMagDep(c.τ1, c.τ2, Mw);
      phi_lnY = stdMagDep(c.φ1, c.φ2, Mw);
      tau_lnPGAB = stdMagDep(cPGA.τ1, cPGA.τ2, Mw);
      phi_lnPGAB = stdMagDep(cPGA.φ1, cPGA.φ2, Mw);
    } else {
      tau_lnYB = c.τ2;
      phi_lnY = c.φ2;
      tau_lnPGAB = cPGA.τ2;
      phi_lnPGAB = cPGA.φ2;
    }

    // intra-event std dev -- Equation 27
    double alphaTau = alpha * tau_lnPGAB;
    double tauSq = tau_lnYB * tau_lnYB + alphaTau * alphaTau +
        2.0 * alpha * c.ρ * tau_lnYB * tau_lnPGAB;

    // inter-event std dev -- Equation 28
    double phi_lnYB = sqrt(phi_lnY * phi_lnY - PHI_LNAF_SQ);
    phi_lnPGAB = sqrt(phi_lnPGAB * phi_lnPGAB - PHI_LNAF_SQ);
    double aPhi_lnPGAB = alpha * phi_lnPGAB;

    // phi_lnaf terms in eqn. 30 cancel when expanded leaving phi_lnY only
    double phiSq = phi_lnY * phi_lnY + aPhi_lnPGAB * aPhi_lnPGAB +
        2.0 * c.ρ * phi_lnYB * aPhi_lnPGAB;

    // total model -- Equation 32
    return sqrt(phiSq + tauSq);
  }

  private static final double stdMagDep(final double lo, final double hi, final double Mw) {
    return hi + (lo - hi) * (5.5 - Mw);
  }

}
