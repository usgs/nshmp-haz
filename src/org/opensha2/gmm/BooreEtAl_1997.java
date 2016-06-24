package org.opensha2.gmm;

import static java.lang.Math.log;
import static java.lang.Math.sqrt;

import static org.opensha2.gmm.FaultStyle.REVERSE;
import static org.opensha2.gmm.FaultStyle.STRIKE_SLIP;
import static org.opensha2.gmm.GmmInput.Field.MAG;
import static org.opensha2.gmm.GmmInput.Field.RAKE;
import static org.opensha2.gmm.GmmInput.Field.RJB;
import static org.opensha2.gmm.GmmInput.Field.VS30;

import org.opensha2.eq.fault.Faults;
import org.opensha2.gmm.GmmInput.Constraints;

import com.google.common.collect.Range;

import java.util.Map;

/**
 * Implementation of the Boore, Joyner & Fumal (1997) ground motion model for
 * shallow earthquakes in active continental crust. This implementation supports
 * rock and soil sites abnd provides independent treatment for strike-slip,
 * reverse, and unknown faulting mechanisms.
 *
 * <p><b>Note:</b> Direct instantiation of {@code GroundMotionModel}s is
 * prohibited. Use {@link Gmm#instance(Imt)} to retrieve an instance for a
 * desired {@link Imt}.
 *
 * <p><b>Reference:</b> Boore, D.M., Joyner, W.B., and Fumal, T.E., 1997,
 * Equations for estimating horizontal response spectra and peak acceleration
 * from western North American earthquakes: A summary of recent work,
 * Seismological Research Letters, v. 68, n. 1, p. 128-153.
 *
 * <p><b>doi:</b><a href="http://dx.doi.org/10.1785/gssrl.68.1.128">
 * 10.1785/gssrl.68.1.128</a>
 * 
 * <p><b>Erratum:</b> Boore, D.M., 2005, Equations for estimating horizontal
 * response spectra and peak acceleration from western North American
 * earthquakes: A summary of recent work, Seismological Research Letters, v. 76,
 * n. 3, p. 368-369.
 *
 * <p><b>doi:</b><a href="http://dx.doi.org/10.1785/gssrl.76.3.368">
 * 10.1785/gssrl.76.3.368</a>
 *
 * <p><b>Component:</b> random horizontal
 *
 * @author Allison Shumway
 * @author Peter Powers
 * @see Gmm#BJF_97
 */
public final class BooreEtAl_1997 implements GroundMotionModel {

  static final String NAME = "Boore et al. (1997)";

  static final Constraints CONSTRAINTS = Constraints.builder()
      .set(MAG, Range.closed(4.0, 8.0))
      .set(RJB, Range.closed(0.0, 80.0))
      .set(RAKE, Faults.RAKE_RANGE)
      .set(VS30, Range.closed(250.0, 1100.0))
      .build();

  static final CoefficientContainer COEFFS = new CoefficientContainer("BJF97.csv");

  private static final class Coefficients {

    final double b1ss, b1rv, b1all, b2, b3, b5, bv, va, h, σlnY;

    // unused
    // final double σ1, σC, σr, σe;

    Coefficients(Imt imt, CoefficientContainer cc) {
      Map<String, Double> coeffs = cc.get(imt);
      b1ss = coeffs.get("b1ss");
      b1rv = coeffs.get("b1rv");
      b1all = coeffs.get("b1all");
      b2 = coeffs.get("b2");
      b3 = coeffs.get("b3");
      b5 = coeffs.get("b5");
      bv = coeffs.get("bv");
      va = coeffs.get("va");
      h = coeffs.get("h");
      σlnY = coeffs.get("sig_lnY");
    }
  }

  private final Coefficients coeffs;

  BooreEtAl_1997(final Imt imt) {
    coeffs = new Coefficients(imt, COEFFS);
  }

  @Override
  public ScalarGroundMotion calc(final GmmInput in) {
    double μ = calcMean(coeffs, in);
    return DefaultScalarGroundMotion.create(μ, coeffs.σlnY);
  }

  private static final double calcMean(final Coefficients c, final GmmInput in) {
    FaultStyle style = GmmUtils.rakeToFaultStyle_NSHMP(in.rake);
    double b1 = (style == STRIKE_SLIP) ? c.b1ss : (style == REVERSE) ? c.b1rv : c.b1all;
    double r = sqrt(in.rJB * in.rJB + c.h * c.h);
    double mFac = in.Mw - 6.0;
    return b1 +
        c.b2 * mFac +
        c.b3 * mFac * mFac +
        c.b5 * log(r) +
        c.bv * log(in.vs30 / c.va);
  }

}
