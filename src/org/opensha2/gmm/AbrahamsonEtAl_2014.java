package org.opensha2.gmm;

import static java.lang.Math.cos;
import static java.lang.Math.exp;
import static java.lang.Math.log;
import static java.lang.Math.pow;
import static java.lang.Math.sqrt;

import static org.opensha2.gmm.FaultStyle.NORMAL;
import static org.opensha2.gmm.GmmInput.Field.DIP;
import static org.opensha2.gmm.GmmInput.Field.MW;
import static org.opensha2.gmm.GmmInput.Field.RAKE;
import static org.opensha2.gmm.GmmInput.Field.VS30;
import static org.opensha2.gmm.GmmInput.Field.VSINF;
import static org.opensha2.gmm.GmmInput.Field.WIDTH;
import static org.opensha2.gmm.GmmInput.Field.Z1P0;
import static org.opensha2.gmm.GmmInput.Field.ZTOP;

import org.opensha2.data.Interpolate;
import org.opensha2.eq.Earthquakes;
import org.opensha2.eq.fault.Faults;
import org.opensha2.gmm.GmmInput.Constraints;
import org.opensha2.util.Maths;

import com.google.common.collect.Range;

import java.util.Map;

/**
 * Implementation of the Abrahamson, Silva & Kamai (2014) next generation ground
 * motion model for active crustal regions developed as part of <a
 * href="http://peer.berkeley.edu/ngawest2">NGA West II</a>.
 *
 * <p><b>Note:</b> Direct instantiation of {@code GroundMotionModel}s is
 * prohibited. Use {@link Gmm#instance(Imt)} to retrieve an instance for a
 * desired {@link Imt}.
 *
 * <p><b>Reference:</b> Abrahamson, N.A., Silva, W.J., and Kamai, R., 2014,
 * Summary of the ASK14 ground-motion relation for active crustal regions:
 * Earthquake Spectra, v. 30, n. 3, p. 1025-1055.
 *
 * <p><b>doi:</b> <a href="http://dx.doi.org/10.1193/070913EQS198M">
 * 10.1193/070913EQS198M</a>
 *
 * <p><b>Component:</b> RotD50 (average horizontal)
 *
 * @author Peter Powers
 * @see Gmm#ASK_14
 */
public final class AbrahamsonEtAl_2014 implements GroundMotionModel {

  static final String NAME = "Abrahamson, Silva & Kamai (2014)";

  static final Constraints CONSTRAINTS = Constraints.builder()
      .set(MW, Range.closed(3.0, 8.5))
      .setDistances(300.0)
      .set(DIP, Faults.DIP_RANGE)
      .set(WIDTH, Earthquakes.CRUSTAL_WIDTH_RANGE)
      .set(ZTOP, Earthquakes.CRUSTAL_DEPTH_RANGE)
      .set(RAKE, Faults.RAKE_RANGE)
      .set(VS30, Range.closedOpen(180.0, 1000.0))
      .set(VSINF)
      .set(Z1P0, Range.closed(0.0, 3.0))
      .build();

  static final CoefficientContainer COEFFS = new CoefficientContainer("ASK14.csv");

  private static final double A3 = 0.275;
  private static final double A4 = -0.1;
  private static final double A5 = -0.41;
  private static final double M2 = 5.0;
  private static final double N = 1.5;
  private static final double C4 = 4.5;
  private static final double A = pow(610, 4);
  private static final double B = pow(1360, 4) + A;
  private static final double VS_RK = 1180.0;
  private static final double A2_HW = 0.2;
  private static final double H1 = 0.25;
  private static final double H2 = 1.5;
  private static final double H3 = -0.75;
  private static final double PHI_AMP_SQ = 0.16;

  // private static final double RY0 = -1.0;

  private static final class Coefficients {

    final Imt imt;
    final double a1, a2, a6, a8, a10, a12, a13, a15, a17, a43, a44, a45, a46,
        b, c,
        s1e, s2e, s3, s4, s1m, s2m,
        M1, Vlin;

    // same for all periods; replaced with constant
    // final double a3, a4, a5, c4, n;

    // currently unused
    // final double a7, a11, a14, a16, s5, s6;

    // Japan model
    // final double a25, a28, a29, a31, a36, a37, a38, a39, a40, a41, a42;

    Coefficients(Imt imt, CoefficientContainer cc) {
      this.imt = imt;
      Map<String, Double> coeffs = cc.get(imt);
      a1 = coeffs.get("a1");
      a2 = coeffs.get("a2");
      a6 = coeffs.get("a6");
      a8 = coeffs.get("a8");
      a10 = coeffs.get("a10");
      a12 = coeffs.get("a12");
      a13 = coeffs.get("a13");
      a15 = coeffs.get("a15");
      a17 = coeffs.get("a17");
      a43 = coeffs.get("a43");
      a44 = coeffs.get("a44");
      a45 = coeffs.get("a45");
      a46 = coeffs.get("a46");
      b = coeffs.get("b");
      c = coeffs.get("c");
      s1e = coeffs.get("s1e");
      s2e = coeffs.get("s2e");
      s3 = coeffs.get("s3");
      s4 = coeffs.get("s4");
      s1m = coeffs.get("s1m");
      s2m = coeffs.get("s2m");
      M1 = coeffs.get("M1");
      Vlin = coeffs.get("Vlin");
    }
  }

  private final Coefficients coeffs;

  AbrahamsonEtAl_2014(final Imt imt) {
    coeffs = new Coefficients(imt, COEFFS);
  }

  @Override
  public final ScalarGroundMotion calc(final GmmInput in) {
    return calc(coeffs, in);
  }

  private static final ScalarGroundMotion calc(final Coefficients c, final GmmInput in) {

    // frequently used method locals
    double Mw = in.Mw;
    double rJB = in.rJB;
    double rRup = in.rRup;
    double rX = in.rX;
    double dip = in.dip;
    double zTop = in.zTop;
    double vs30 = in.vs30;

    // ****** Mean ground motion and standard deviation model ******

    // Base Model (magnitude and distance dependence for strike-slip eq)

    // Magnitude dependent taper -- Equation 4
    double c4mag = (Mw > 5) ? C4 : (Mw > 4) ? C4 - (C4 - 1.0) * (5.0 - Mw) : 1.0;

    // -- Equation 3
    double R = sqrt(rRup * rRup + c4mag * c4mag);

    // -- Equation 2
    double MaxMwSq = (8.5 - Mw) * (8.5 - Mw);
    double MwM1 = Mw - c.M1;

    double f1 = c.a1 + c.a17 * rRup;
    if (Mw > c.M1) {
      f1 += A5 * MwM1 + c.a8 * MaxMwSq + (c.a2 + A3 * MwM1) * log(R);
    } else if (Mw >= M2) {
      f1 += A4 * MwM1 + c.a8 * MaxMwSq + (c.a2 + A3 * MwM1) * log(R);
    } else {
      double M2M1 = M2 - c.M1;
      double MaxM2Sq = (8.5 - M2) * (8.5 - M2);
      double MwM2 = Mw - M2;
      // a7 == 0; removed a7 * MwM2 * MwM2 below
      f1 += A4 * M2M1 + c.a8 * MaxM2Sq + c.a6 * MwM2 + (c.a2 + A3 * M2M1) * log(R);
    }

    // Aftershock Model (Class1 = mainshock; Class2 = afershock)
    // not currently used as rJBc (the rJB from the centroid of the parent
    // Class1 event) is not defined; requires event type flag -- Equation 7
    // double f11 = 0.0 * a14;
    // if (rJBc < 5) {
    // f11 = a14;
    // } else if (rJBc <= 15) {
    // f11 = a14 * (1 - (rJBc - 5.0) / 10.0);
    // }

    // Hanging Wall Model
    double f4 = 0.0;
    // short-circuit: f4 is 0 if rJB >= 30, rX < 0, Mw <= 5.5, zTop > 10
    // these switches have been removed below
    if (rJB < 30 && rX >= 0.0 && Mw > 5.5 && zTop <= 10.0) {

      // ... dip taper -- Equation 11
      double T1 = (dip > 30.0) ? (90.0 - dip) / 45 : 1.33333333; // 60/45

      // ... mag taper -- Equation 12
      double dM = Mw - 6.5;
      double T2 = (Mw >= 6.5) ? 1 + A2_HW * dM : 1 + A2_HW * dM - (1 - A2_HW) * dM * dM;

      // ... rX taper -- Equation 13
      double T3 = 0.0;
      double r1 = in.width * cos(dip * Maths.TO_RAD);
      double r2 = 3 * r1;
      if (rX <= r1) {
        double rXr1 = rX / r1;
        T3 = H1 + H2 * rXr1 + H3 * rXr1 * rXr1;
      } else if (rX <= r2) {
        T3 = 1 - (rX - r1) / (r2 - r1);
      }

      // ... zTop taper -- Equation 14
      double T4 = 1 - (zTop * zTop) / 100.0;

      // ... rX, rY0 taper -- Equation 15b
      double T5 = (rJB == 0.0) ? 1.0 : 1 - rJB / 30.0;

      // total -- Equation 10
      f4 = c.a13 * T1 * T2 * T3 * T4 * T5;
    }

    // Depth to Rupture Top Model -- Equation 16
    double f6 = c.a15;
    if (zTop < 20.0) {
      f6 *= zTop / 20.0;
    }

    // Style-of-Faulting Model -- Equations 5 & 6
    // Note: REVERSE doesn not need to be implemented as f7 always resolves
    // to 0 as a11==0; we skip f7 here
    FaultStyle style = GmmUtils.rakeToFaultStyle_NSHMP(in.rake);
    double f78 = (style == NORMAL) ? (Mw > 5.0) ? c.a12 : (Mw >= 4.0) ? c.a12 * (Mw - 4) : 0.0
        : 0.0;

    // Soil Depth Model -- Equation 17
    double f10 = calcSoilTerm(c, vs30, in.z1p0);

    // Site Response Model
    double f5 = 0.0;
    double v1 = getV1(c.imt); // -- Equation 9
    double vs30s = (vs30 < v1) ? vs30 : v1; // -- Equation 8

    // Site term -- Equation 7
    double saRock = 0.0; // calc Sa1180 (rock reference) if necessary
    double c_Vlin = c.Vlin;
    double c_b = c.b;
    double c_c = c.c;
    if (vs30 < c_Vlin) {
      // soil term (f10) for Sa1180 is zero per R. Kamai's code where
      // Z1 < 0 for Sa1180 loop
      double vs30s_rk = (VS_RK < v1) ? VS_RK : v1;
      // use this f5 form for Sa1180 Vlin is always < 1180
      double f5_rk = (c.a10 + c_b * N) * log(vs30s_rk / c_Vlin);
      saRock = exp(f1 + f78 + f5_rk + f4 + f6);
      f5 = c.a10 * log(vs30s / c_Vlin) - c_b * log(saRock + c_c) + c_b *
          log(saRock + c_c * pow(vs30s / c_Vlin, N));
    } else {
      f5 = (c.a10 + c_b * N) * log(vs30s / c_Vlin);
    }

    // total model (no aftershock f11) -- Equation 1
    double μ = f1 + f78 + f5 + f4 + f6 + f10;

    // ****** Aleatory uncertainty model ******

    // Intra-event term -- Equation 24
    double phiAsq = in.vsInf ? getPhiA(Mw, c.s1e, c.s2e) : getPhiA(Mw, c.s1m, c.s2m);
    phiAsq *= phiAsq;

    // Inter-event term -- Equation 25
    double tauB = getTauA(Mw, c.s3, c.s4);

    // Intra-event term with site amp variability removed -- Equation 27
    double phiBsq = phiAsq - PHI_AMP_SQ;

    // Parital deriv. of ln(soil amp) w.r.t. ln(SA1180) -- Equation 30
    // saRock subject to same vs30 < Vlin test as in mean model
    double dAmp_p1 = get_dAmp(c_b, c_c, c_Vlin, vs30, saRock) + 1.0;

    // phi squared, with non-linear effects -- Equation 28
    double phiSq = phiBsq * dAmp_p1 * dAmp_p1 + PHI_AMP_SQ;

    // tau squared, with non-linear effects -- Equation 29
    double τ = tauB * dAmp_p1;

    // total std dev
    double σ = sqrt(phiSq + τ * τ);

    return DefaultScalarGroundMotion.create(μ, σ);

  }

  // -- Equation 9
  private static final double getV1(final Imt imt) {
    Double T = imt.period();
    if (T == null) {
      return 1500.0;
    }
    if (T >= 3.0) {
      return 800.0;
    }
    if (T > 0.5) {
      return exp(-0.35 * log(T / 0.5) + log(1500.0));
    }
    return 1500.0;
  }

  // used for interpolation in calcSoilTerm(), below
  private static final double[] VS_BINS = { 150d, 250d, 400d, 700d, 1000d };

  // Soil depth model adapted from CY13 form -- Equation 17
  private static final double calcSoilTerm(final Coefficients c, final double vs30,
      final double z1p0) {
    // short circuit; default z1 will be the same as z1ref
    if (Double.isNaN(z1p0)) {
      return 0.0;
    }
    // -- Equation 18
    double vsPow4 = vs30 * vs30 * vs30 * vs30;
    double z1ref = exp(-7.67 / 4.0 * log((vsPow4 + A) / B)) / 1000.0; // km

    // double z1c = (vs30 > 500.0) ? a46 :
    // (vs30 > 300.0) ? a45 :
    // (vs30 > 200.0) ? a44 : a43;

    // new interpolation algorithm
    double[] vsCoeff = { c.a43, c.a44, c.a45, c.a46, c.a46 };
    double z1c = Interpolate.findY(VS_BINS, vsCoeff, vs30);

    return z1c * log((z1p0 + 0.01) / (z1ref + 0.01));
  }

  // -- Equation 24
  private static final double getPhiA(final double Mw, final double s1, final double s2) {
    return Mw < 4.0 ? s1 : Mw > 6.0 ? s2 : s1 + ((s2 - s1) / 2) * (Mw - 4.0);
  }

  // -- Equation 25
  private static final double getTauA(final double Mw, final double s3, final double s4) {
    return Mw < 5.0 ? s3 : Mw > 7.0 ? s4 : s3 + ((s4 - s3) / 2) * (Mw - 5.0);
  }

  // -- Equation 30
  private static final double get_dAmp(final double b, final double c, final double vLin,
      final double vs30, final double saRock) {
    if (vs30 >= vLin) {
      return 0.0;
    }
    return (-b * saRock) / (saRock + c) +
        (b * saRock) / (saRock + c * pow(vs30 / vLin, N));
  }

}
