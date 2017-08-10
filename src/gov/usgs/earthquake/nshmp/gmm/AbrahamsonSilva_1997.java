package gov.usgs.earthquake.nshmp.gmm;

import static gov.usgs.earthquake.nshmp.gmm.GmmInput.Field.DIP;
import static gov.usgs.earthquake.nshmp.gmm.GmmInput.Field.MW;
import static gov.usgs.earthquake.nshmp.gmm.GmmInput.Field.RAKE;
import static gov.usgs.earthquake.nshmp.gmm.GmmInput.Field.RJB;
import static gov.usgs.earthquake.nshmp.gmm.GmmInput.Field.RRUP;
import static gov.usgs.earthquake.nshmp.gmm.GmmInput.Field.VS30;
import static java.lang.Math.log;
import static java.lang.Math.sqrt;

import com.google.common.collect.Range;

import java.util.Map;

import gov.usgs.earthquake.nshmp.eq.fault.Faults;
import gov.usgs.earthquake.nshmp.gmm.GmmInput.Constraints;

/**
 * Implementation of the Abrahamson & Silva (1997) ground motion model for
 * shallow earthquakes in active continental crust. In keeping with prior NSHMP
 * implementations of this older model, only soft rock sites are supported
 * (V𝗌30 = 760 m/s).
 *
 * <p><b>Note:</b> Direct instantiation of {@code GroundMotionModel}s is
 * prohibited. Use {@link Gmm#instance(Imt)} to retrieve an instance for a
 * desired {@link Imt}.
 *
 * <p><b>Reference:</b> Abrahamson, N. A. and W.J. Silva, 1997, Empirical
 * response spectral attenuation relations for shallow crustal earthquakes:
 * Seismological Research Letters, v. 68, n. 1, p. 94-127.
 *
 * <p><b>doi:</b> <a href="http://dx.doi.org/10.1785/gssrl.68.1.94">
 * 10.1785/gssrl.68.1.94</a>
 *
 * <p><b>Component:</b> average horizontal
 *
 * @author Allison Shumway
 * @author Peter Powers
 * @see Gmm#AS_97
 */
public class AbrahamsonSilva_1997 implements GroundMotionModel {

  static final String NAME = "Abrahamson & Silva (1997)";

  static final Constraints CONSTRAINTS = Constraints.builder()
      .set(MW, Range.closed(4.0, 8.0))
      .set(RJB, Range.closed(0.0, 300.0))
      .set(RRUP, Range.closed(0.0, 300.0))
      .set(DIP, Faults.DIP_RANGE)
      .set(RAKE, Faults.RAKE_RANGE)
      .set(VS30, Range.singleton(760.0))
      .build();

  static final CoefficientContainer COEFFS = new CoefficientContainer("AS97.csv");

  private static final double A2 = 0.512;
  private static final double A4 = 0.144;
  private static final double A13 = 0.17;
  private static final double C1 = 6.4;

  private static final class Coefficients {

    final double a1, a3, a5, a6, a9, a12, b5, b6, c4;

    // same for all periods; replaced with constant
    // final double a2, a4, a13, c1, c5, n;

    // unused
    // final double a10, a11;

    Coefficients(Imt imt, CoefficientContainer cc) {
      Map<String, Double> coeffs = cc.get(imt);
      a1 = coeffs.get("a1");
      a3 = coeffs.get("a3");
      a5 = coeffs.get("a5");
      a6 = coeffs.get("a6");
      a9 = coeffs.get("a9");
      a12 = coeffs.get("a12");
      b5 = coeffs.get("b5");
      b6 = coeffs.get("b6");
      c4 = coeffs.get("c4");
    }
  }

  private final Coefficients coeffs;

  AbrahamsonSilva_1997(final Imt imt) {
    coeffs = new Coefficients(imt, COEFFS);
  }

  @Override
  public ScalarGroundMotion calc(final GmmInput in) {
    double μ = calcMean(coeffs, in);
    double σ = calcStdDev(coeffs, in.Mw);
    return DefaultScalarGroundMotion.create(μ, σ);
  }

  private static final double calcMean(final Coefficients c, final GmmInput in) {

    // frequently used method locals
    double Mw = in.Mw;
    double rRup = in.rRup;
    double rake = in.rake;

    // base model
    double R = sqrt(rRup * rRup + c.c4 * c.c4);
    double f1 = c.a1 +
        (((Mw <= C1) ? A2 : A4) * (Mw - C1)) +
        (c.a12 * (8.5 - Mw) * (8.5 - Mw)) +
        (c.a3 + A13 * (Mw - C1)) * log(R);

    // style-of-faulting term - use rake to determine F; the values below
    // were derived from the OpenSHA implementation for which guidance was
    // supplied by Norm Abrahamson
    double f3 = 0.0;
    if (rake > 67.5 && rake < 112.5) {
      f3 = calcFaultStyle(c, Mw);
    } else if (rake > 22.5 && rake < 157.5) {
      f3 = 0.5 * calcFaultStyle(c, Mw);
    }

    // hanging-wall term
    double f4 = 0.0;
    if (in.rJB == 0.0 && in.dip < 90.0) {
      double hwM = (Mw >= 6.5) ? 1.0 : (Mw > 5.5) ? Mw - 5.5 : 0.0;
      double hwR = 0.0;
      if (rRup < 4.0) {
        hwR = 0.0;
      } else if (rRup < 8.0) {
        hwR = c.a9 * (rRup - 4.0) / 4.0;
      } else if (rRup < 18.0) {
        hwR = c.a9;
      } else if (rRup < 25.0) {
        hwR = c.a9 * (1.0 - (rRup - 18.0) / 7.0);
      }
      f4 = hwM * hwR;
    }

    // no site response term required for rock (f5)

    return f1 + f3 + f4;
  }

  private static double calcFaultStyle(final Coefficients c, final double Mw) {
    return (Mw <= 5.8) ? c.a5 : (Mw < C1) ? c.a5 + (c.a6 - c.a5) / (C1 - 5.8) : c.a6;
  }

  private static double calcStdDev(final Coefficients c, final double Mw) {
    return (Mw <= 5.0) ? c.b5 : (Mw < 7.0) ? c.b5 - c.b6 * (Mw - 5.0) : c.b5 - 2 * c.b6;
  }

}
