package org.opensha2.gmm;

import static java.lang.Math.log;
import static java.lang.Math.pow;
import static java.lang.Math.sqrt;

import static org.opensha2.gmm.GmmInput.Field.MW;
import static org.opensha2.gmm.GmmInput.Field.RRUP;
import static org.opensha2.gmm.GmmInput.Field.VS30;
import static org.opensha2.gmm.MagConverter.NONE;
import static org.opensha2.gmm.SiteClass.HARD_ROCK;

import org.opensha2.gmm.GmmInput.Constraints;

import com.google.common.collect.Range;

import java.util.Map;

/**
 * Implementation of the hybrid ground motion model for stable continental
 * regions by Campbell (2003). This implementation matches that used in the 2008
 * USGS NSHMP and comes in two additional magnitude converting (mb to Mw)
 * flavors to support the 2008 central and eastern US model.
 *
 * <p><b>Note:</b> Direct instantiation of {@code GroundMotionModel}s is
 * prohibited. Use {@link Gmm#instance(Imt)} to retrieve an instance for a
 * desired {@link Imt}.
 *
 * <p><b>Implementation note:</b> Mean values are clamped per
 * {@link GmmUtils#ceusMeanClip(Imt, double)}.
 *
 * <p><b>Reference:</b> Campbell, K.W., 2003, Prediction of strong ground motion
 * using the hybrid empirical method and its use in the devel- opment of
 * ground-motion (attenuation) relations in eastern North America: Bulletin of
 * the Seismological Society of America, v. 93, p. 1012–1033.
 *
 * <p><b>doi:</b> <a href="http://dx.doi.org/10.1785/0120020002">
 * 10.1785/0120020002</a>
 *
 * <p><b>Component:</b> geometric mean of two horizontal components
 *
 * @author Peter Powers
 * @see Gmm#CAMPBELL_03
 * @see Gmm#CAMPBELL_03_AB
 * @see Gmm#CAMPBELL_03_J
 */
public class Campbell_2003 implements GroundMotionModel, ConvertsMag {

  // notes from original implementation and fortran:
  //
  // some coefficients are labeled differnetly than in paper
  // localCoeff(paperCoeff):
  // c5(c7) c6(c8) c7(c9) c8(c10) c9(c5) c10(c6)
  //
  // c clamp for 2s set to 0 as per Ken Campbell's email of Aug 18 2008.

  // TODO fix clamp values (not implemented here yet) to match other CEUS gmms

  static final String NAME = "Campbell (2003)";

  static final Constraints CONSTRAINTS = Constraints.builder()
      .set(MW, Range.closed(4.0, 8.0))
      .set(RRUP, Range.closed(0.0, 1000.0))
      .set(VS30, Range.closed(760.0, 2000.0))
      .build();

  static final CoefficientContainer COEFFS = new CoefficientContainer("Campbell03.csv");

  private static final double LOG_70 = 4.2484952;
  private static final double LOG_130 = 4.8675345;

  private static final class Coefficients {

    final Imt imt;
    final double c1, c1h, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13;

    Coefficients(Imt imt, CoefficientContainer cc) {
      this.imt = imt;
      Map<String, Double> coeffs = cc.get(imt);
      c1 = coeffs.get("c1");
      c1h = coeffs.get("c1h");
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
      c12 = coeffs.get("c12");
      c13 = coeffs.get("c13");
    }
  }

  private final Coefficients coeffs;

  Campbell_2003(final Imt imt) {
    coeffs = new Coefficients(imt, COEFFS);
  }

  @Override
  public final ScalarGroundMotion calc(final GmmInput in) {

    double Mw = converter().convert(in.Mw);
    SiteClass siteClass = GmmUtils.ceusSiteClass(in.vs30);

    double μ = calcMean(coeffs, Mw, in.rRup, siteClass);
    double σ = calcStdDev(coeffs, Mw);

    return DefaultScalarGroundMotion.create(μ, σ);
  }

  @Override
  public MagConverter converter() {
    return NONE;
  }

  private final double calcMean(final Coefficients c, final double Mw, final double rRup,
      final SiteClass siteClass) {

    double gnd0 = siteClass == HARD_ROCK ? c.c1h : c.c1;
    double gndm = gnd0 + c.c2 * Mw + c.c3 * (8.5 - Mw) * (8.5 - Mw);
    double cfac = pow((c.c5 * Math.exp(c.c6 * Mw)), 2);

    double arg = sqrt(rRup * rRup + cfac);
    double fac = 0.0;
    if (rRup > 70.0) {
      fac = c.c7 * (log(rRup) - LOG_70);
    }
    if (rRup > 130.0) {
      fac = fac + c.c8 * (log(rRup) - LOG_130);
    }
    double gnd = gndm + c.c4 * log(arg) + fac + (c.c9 + c.c10 * Mw) * rRup;

    return GmmUtils.ceusMeanClip(c.imt, gnd);
  }

  private final double calcStdDev(final Coefficients c, final double Mw) {
    return (Mw < 7.16) ? c.c11 + c.c12 * Mw : c.c13;
  }

}
