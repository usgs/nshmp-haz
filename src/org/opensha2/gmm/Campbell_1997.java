package org.opensha2.gmm;

import static java.lang.Math.exp;
import static java.lang.Math.log;
import static java.lang.Math.max;
import static java.lang.Math.sqrt;
import static java.lang.Math.tanh;

import static org.opensha2.gmm.FaultStyle.NORMAL;
import static org.opensha2.gmm.FaultStyle.STRIKE_SLIP;
import static org.opensha2.gmm.FaultStyle.UNKNOWN;
import static org.opensha2.gmm.GmmInput.Field.MAG;
import static org.opensha2.gmm.GmmInput.Field.RAKE;
import static org.opensha2.gmm.GmmInput.Field.RRUP;
import static org.opensha2.gmm.GmmInput.Field.VS30;
import static org.opensha2.gmm.Imt.PGA;

import org.opensha2.eq.fault.Faults;
import org.opensha2.gmm.GmmInput.Constraints;

import com.google.common.collect.Range;

import java.util.Map;

/**
 * Implementation of the Campbell (1997) ground motion model for worldwide
 * earthquakes in active tectonic regions. In keeping with prior NSHMP
 * implementations of this older model, only soft rock sites are supported
 * (V𝗌30 = 760 m/s).
 *
 * <p><b>Note:</b> Direct instantiation of {@code GroundMotionModel}s is
 * prohibited. Use {@link Gmm#instance(Imt)} to retrieve an instance for a
 * desired {@link Imt}.
 *
 * <p><b>Reference:</b> Campbell, K.W., 1997, Empirical near-source attenuation
 * relationships for horizontal and vertical components of peak ground
 * acceleration, peak ground velocity, and pseudo-absolute acceleration response
 * spectra, Seismological Research Letters, v. 68, n. 1, pp. 154-179.
 * 
 * <p><b>doi:</b> <a href="http://dx.doi.org/10.1785/gssrl.68.1.154">
 * 10.1785/gssrl.68.1.154</a>
 * 
 * <p><b>Errata doi:</b> <a href="http://dx.doi.org/10.1785/gssrl.71.3.352">
 * 10.1785/gssrl.71.3.352</a> and <a
 * href="http://dx.doi.org/10.1785/gssrl.72.4.474"> 10.1785/gssrl.72.4.474</a>
 *
 * <p><b>Component:</b> geometric mean of two horizontal
 *
 * @author Allison Shumway
 * @author Peter Powers
 * @see Gmm#CAMPBELL_97
 */

public class Campbell_1997 implements GroundMotionModel {

  static final String NAME = "Campbell (1997)";

  static final Constraints CONSTRAINTS = Constraints.builder()
      .set(MAG, Range.closed(5.0, 8.0))
      .set(RRUP, Range.closed(0.0, 60.0))
      .set(RAKE, Faults.RAKE_RANGE)
      .set(VS30, Range.singleton(760.0))
      .build();

  static final CoefficientContainer COEFFS;

  static {
    COEFFS = new CoefficientContainer("Campbell97.csv");
  }

  private static final class Coefficients {

    final Imt imt;
    final double c1, c2, c3, c4, c5, c6, c7, c8, c9, c10;

    // unused
    // final double c11, c12;

    Coefficients(Imt imt, CoefficientContainer cc) {
      this.imt = imt;
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
    }
  }

  private final Coefficients coeffs;
  private final Coefficients coeffsPga;

  Campbell_1997(final Imt imt) {
    coeffs = new Coefficients(imt, COEFFS);
    coeffsPga = new Coefficients(PGA, COEFFS);
  }

  @Override
  public ScalarGroundMotion calc(GmmInput in) {

    // distance scaling - per verbal communication with Ken Campbell, it
    // is ok to approximate rSeis by constraining rRup to be >= 3.0 km
    // (see also Campbell & Bozorgnia 2003)
    double rSeis = max(in.rRup, 3.0);
    FaultStyle style = GmmUtils.rakeToFaultStyle_NSHMP(in.rake);

    double μ = calcMeanPga(coeffsPga, in.Mw, rSeis, style);
    double σ = calcStdDevPga(μ);

    if (coeffs.imt.isSA()) {
      μ = calcMeanSa(coeffs, in.Mw, rSeis, μ);
      σ = calcStdDevSa(σ);
    }
    return DefaultScalarGroundMotion.create(μ, σ);
  }

  private static final double calcMeanPga(
      final Coefficients c,
      final double Mw,
      final double rSeis,
      final FaultStyle style) {

    // Implementation is only for soft/generic rock sites so the hard rock
    // and soil depth terms have been eliminated

    double lnR = log(rSeis);
    double f = faultTerm(style);
    double rTerm = c.c4 * exp(c.c5 * Mw);

    return c.c1 + (c.c2 * Mw) +
        c.c3 * log(sqrt(rSeis * rSeis + rTerm * rTerm)) +
        (c.c6 + c.c7 * lnR + c.c8 * Mw) * f +
        (c.c9 + c.c10 * lnR);
  }

  private static final double calcMeanSa(
      final Coefficients c,
      final double Mw,
      final double rSeis,
      final double μPga) {

    return μPga + c.c1 + 
        c.c2 * tanh(c.c3 * (Mw - 4.7)) + 
        (c.c4 + (c.c5 * Mw)) * rSeis + 
        (0.5 * c.c6) + 
        c.c7 * tanh(c.c8);
  }

  private static final double calcStdDevPga(final double μPga) {
    double pgaLin = exp(μPga);
    if (pgaLin < 0.068) {
      return 0.55;
    } else if (pgaLin <= 0.21) {
      return 0.173 - 0.140 * μPga;
    } else {
      return 0.39;
    }
  }

  private static final double calcStdDevSa(final double σPga) {
    return sqrt((σPga * σPga) + (0.27 * 0.27));
  }

  private static double faultTerm(FaultStyle style) {
    return (style == STRIKE_SLIP || style == NORMAL) ? 0.0 : (style == UNKNOWN) ? 0.5 : 1.0;
  }

}
