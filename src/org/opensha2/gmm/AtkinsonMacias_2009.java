package org.opensha2.gmm;

import static java.lang.Math.log10;

import static org.opensha2.gmm.GmmInput.Field.MW;
import static org.opensha2.gmm.GmmInput.Field.RRUP;
import static org.opensha2.gmm.GmmInput.Field.VS30;
import static org.opensha2.gmm.GmmUtils.BASE_10_TO_E;
import static org.opensha2.gmm.GmmUtils.LN_G_CM_TO_M;
import static org.opensha2.gmm.Imt.PGA;
import static org.opensha2.util.MathUtils.hypot;

import org.opensha2.gmm.GmmInput.Constraints;

import com.google.common.collect.Range;

import java.util.Map;

/**
 * Implementation of the subduction interface ground motion model by Atkinson &
 * Macias (2009). This implementation matches that used in the 2014 USGS NSHMP.
 *
 * <p><b>Note:</b> Direct instantiation of {@code GroundMotionModel}s is
 * prohibited. Use {@link Gmm#instance(Imt)} to retrieve an instance for a
 * desired {@link Imt}.
 *
 * <p><b>Implementation notes:</b> <ul><li>NSHM fortran implementation converts
 * 0.13Hz to 7.7s; this implementation uses 7.5s instead.</li><li>Model uses a
 * magnitude dependent depth term and so does not impose 20km hypocentral depth
 * as other subduction interface models do.</li></ul>
 *
 * <p><b>Reference:</b> Atkinson, G.M. and Macias, D.M., 2009, Predicted ground
 * motions for great interface earthquakes in the Cascadia subduction zone:
 * Bulletin of the Seismological Society of America, v. 99, p. 1552-1578.
 *
 * <p><b>doi:</b><a href="http://dx.doi.org/10.1785/0120080147">
 * 10.1785/0120080147</a>
 *
 * <p><b>Component:</b> geometric mean of two horizontal components
 *
 * @author Peter Powers
 * @see Gmm#AM_09_INTER
 */
public final class AtkinsonMacias_2009 implements GroundMotionModel {
  
  /*
   * TODO 0.75s interpolated period coefficients added that should
   * be removed if a viable on-the-fly interpolation algorithm is 
   * added.
   */

  static final String NAME = "Atkinson & Macias (2009): Interface";

  static final Constraints CONSTRAINTS = Constraints.builder()
      .set(MW, Range.closed(5.0, 9.5))
      .set(RRUP, Range.closed(0.0, 1000.0))
      .set(VS30, Range.closed(150.0, 1500.0))
      .build();

  static final CoefficientContainer COEFFS = new CoefficientContainer("AM09.csv");

  private static final class Coefficients {

    final double c0, c1, c2, c3, c4, σ;

    Coefficients(Imt imt, CoefficientContainer cc) {
      Map<String, Double> coeffs = cc.get(imt);
      c0 = coeffs.get("c0");
      c1 = coeffs.get("c1");
      c2 = coeffs.get("c2");
      c3 = coeffs.get("c3");
      c4 = coeffs.get("c4");
      σ = coeffs.get("sig");
    }
  }

  private final Coefficients coeffs;
  private final Coefficients coeffsPGA;
  private final BooreAtkinsonSiteAmp siteAmp;

  AtkinsonMacias_2009(final Imt imt) {
    coeffs = new Coefficients(imt, COEFFS);
    coeffsPGA = new Coefficients(PGA, COEFFS);
    siteAmp = new BooreAtkinsonSiteAmp(imt);
  }

  @Override
  public final ScalarGroundMotion calc(final GmmInput in) {
    double μ = calcMean(coeffs, coeffsPGA, siteAmp, in);
    double σ = coeffs.σ * BASE_10_TO_E;
    return DefaultScalarGroundMotion.create(μ, σ);
  }

  private static final double calcMean(final Coefficients c, final Coefficients cPga,
      final BooreAtkinsonSiteAmp siteAmp, final GmmInput in) {

    double μ = calcMean(c, in);
    if (in.vs30 != 760.0) {
      double μPgaRock = calcMean(cPga, in);
      μ += siteAmp.calc(μPgaRock, in.vs30, 760.0);
    }
    return μ;
  }

  private static final double calcMean(final Coefficients c, final GmmInput in) {
    double Mw = in.Mw;
    double h = (Mw * Mw) - (3.1 * Mw) - 14.55;
    double dM = Mw - 8.0;
    double gnd = c.c0 + (c.c3 * dM) + (c.c4 * dM * dM);
    double r = hypot(in.rRup, h);
    gnd += c.c1 * log10(r) + c.c2 * r;
    return gnd * BASE_10_TO_E - LN_G_CM_TO_M;
  }

}
