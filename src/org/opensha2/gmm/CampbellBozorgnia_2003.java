package org.opensha2.gmm;

import static java.lang.Math.exp;
import static java.lang.Math.log;
import static java.lang.Math.max;
import static java.lang.Math.sqrt;

import static org.opensha2.gmm.FaultStyle.REVERSE;
import static org.opensha2.gmm.FaultStyle.UNKNOWN;
import static org.opensha2.gmm.GmmInput.Field.MW;
import static org.opensha2.gmm.GmmInput.Field.RAKE;
import static org.opensha2.gmm.GmmInput.Field.RRUP;
import static org.opensha2.gmm.GmmInput.Field.VS30;

import org.opensha2.eq.fault.Faults;
import org.opensha2.gmm.GmmInput.Constraints;

import com.google.common.collect.Range;

import java.util.Map;

/**
 * Implementation of the Campbell & Bozorgnia (2003) ground motion model for
 * shallow earthquakes in active continental crust. In keeping with prior NSHMP
 * implementations of this older model, only soft rock sites are supported
 * (V𝗌30 = 760 m/s) following specific guidance by the model authors. Longer
 * periods are also not implemented here due to a dependency on Boore, Joyner &
 * Fumal (1997) whose longest period is 2 sec. This implementation also ignores
 * the author's suggestion to use 'uncorrected' PGA values when computing PGA
 * and uses 'corrected' values for both PGA and spectral acceleration.
 *
 * <p><b>Note:</b> Direct instantiation of {@code GroundMotionModel}s is
 * prohibited. Use {@link Gmm#instance(Imt)} to retrieve an instance for a
 * desired {@link Imt}.
 *
 * <p><b>Reference:</b> Campbell, K.W., and Bozorgnia, Y., 2003, Updated
 * near-source ground-motion (attenuation) relations for the horizontal and
 * vertical components of peak ground acceleration and acceleration response
 * spectra: Bulletin of Seismological Society of America, v. 93, n. 1, p.
 * 314-331.
 * 
 * <p><b>doi:</b> <a href="http://dx.doi.org/10.1785/0120020029">
 * 10.1785/0120020029</a>
 * 
 * <p><b>Errata doi:</b> <a href="http://dx.doi.org/10.1785/0120030143">
 * 10.1785/0120030143</a> and <a href="http://dx.doi.org/10.1785/0120030099">
 * 10.1785/0120030099</a>
 *
 * <p><b>Component:</b> average horizontal
 *
 * @author Allison Shumway
 * @author Peter Powers
 * @see Gmm#CB_03
 */
public class CampbellBozorgnia_2003 implements GroundMotionModel {

  static final String NAME = "Campbell & Bozorgnia (2003)";

  static final Constraints CONSTRAINTS = Constraints.builder()
      .set(MW, Range.closed(5.8, 8.0))
      .set(RRUP, Range.closed(0.0, 300.0))
      .set(RAKE, Faults.RAKE_RANGE)
      .set(VS30, Range.singleton(760.0))
      .build();

  static final CoefficientContainer COEFFS;

  static {
    COEFFS = new CoefficientContainer("CB03.csv");
  }

  private static final class Coefficients {

    final double c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c13, c14, c15, c16;

    // unused
    // final double c4, c12, c17, n, r2;

    Coefficients(Imt imt, CoefficientContainer cc) {
      Map<String, Double> coeffs = cc.get(imt);
      c1 = coeffs.get("c1");
      c2 = coeffs.get("c2");
      c3 = coeffs.get("c3");
      c4 = coeffs.get("c4");
      c5 = coeffs.get("c5");
      c6 = coeffs.get("c6");
      c7 = coeffs.get("c7");
      c8 = coeffs.get("c8");
      c9 = coeffs.get("c9");
      c10 = coeffs.get("c10");
      c11 = coeffs.get("c11");
      c13 = coeffs.get("c13");
      c14 = coeffs.get("c14");
      c15 = coeffs.get("c15");
      c16 = coeffs.get("c16");
    }
  }

  private static final class CoefficientsBjf97 {

    final double bv;

    CoefficientsBjf97(Imt imt, CoefficientContainer cc) {
      Map<String, Double> coeffs = cc.get(imt);
      bv = coeffs.get("bv");
    }
  }

  private final Coefficients coeffs;
  private final CoefficientsBjf97 coeffsBjf97;

  private final double revStyleTerm;
  private final double unkStyleTerm;
  private final double gS;
  private final double f4;

  CampbellBozorgnia_2003(final Imt imt) {
    coeffs = new Coefficients(imt, COEFFS);
    coeffsBjf97 = new CoefficientsBjf97(imt, BooreEtAl_1997.COEFFS);
    revStyleTerm = 0.5 * coeffs.c10 + 0.5 * coeffs.c11;
    unkStyleTerm = 0.25 * coeffs.c10 + 0.25 * coeffs.c11;
    gS = coeffs.c5 + 0.5 * coeffs.c6 + 0.5 * coeffs.c7;
    f4 = 0.5 * coeffs.c13 + 0.5 * coeffs.c14 + coeffsBjf97.bv * log(760.0 / 620.0);
  }

  @Override
  public ScalarGroundMotion calc(GmmInput in) {
    double μ = calcMean(coeffs, in);
    double σ = calcStdDev(coeffs.c16, in.Mw);
    return DefaultScalarGroundMotion.create(μ, σ);
  }

  private final double calcMean(
      final Coefficients c,
      final GmmInput in) {

    double Mw = in.Mw;

    // magnitude scaling
    double mTerm = (8.5 - Mw) * (8.5 - Mw);
    double f1 = c.c2 * Mw + c.c3 * mTerm;

    // distance scaling - per verbal communication with Ken Campbell it
    // is ok to approximate rSeis by constraining rRup to be >= 3.0 km.
    // (see also Campbell 1997)
    double rSeis = max(in.rRup, 3.0);
    double rTerm = gS * exp(c.c8 * Mw + c.c9 * mTerm);
    double f2 = rSeis * rSeis + rTerm * rTerm;

    // focal mechanism
    FaultStyle style = GmmUtils.rakeToFaultStyle_NSHMP(in.rake);
    double f3 = (style == REVERSE) ? revStyleTerm : (style == UNKNOWN) ? unkStyleTerm : 0.0;

    // far-source effect of local site conditions
    // (pre-calculated)

    // hanging-wall effect
    double HW = (in.rJB < 5.0 && in.dip <= 70.0) ? (5.0 - in.rJB) / 5.0 : 0.0;
    double hwM = (Mw > 6.5) ? 1.0 : (Mw >= 5.5) ? Mw - 5.5 : 0.0;
    double hwR = (rSeis < 8.0) ? c.c15 * (rSeis / 8.0) : c.c15;
    double f5 = HW * f3 * hwM * hwR;

    return c.c1 + f1 + c.c4 * log(sqrt(f2)) + f3 + f4 + f5;
  }

  private static final double calcStdDev(final double c16, final double Mw) {
    return c16 - ((Mw < 7.4) ? 0.07 * Mw : 0.518);
  }

}
