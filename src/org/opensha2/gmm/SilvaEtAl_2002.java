package org.opensha2.gmm;

import static org.opensha2.gmm.GmmInput.Field.*;
import static java.lang.Math.exp;
import static org.opensha2.gmm.SiteClass.HARD_ROCK;
import static org.opensha2.gmm.MagConverter.NONE;

import java.util.Map;

import org.opensha2.gmm.GmmInput.Constraints;

import com.google.common.collect.Range;

/**
 * Implementation of the Silva et al. (2002) ground motion model for stable
 * continental regions. This implementation matches that used in the 2008 USGS
 * NSHMP and comes in two additional magnitude converting (mb to Mw) flavors to
 * support the 2008 central and eastern US model.
 * 
 * <p><b>Note:</b> Direct instantiation of {@code GroundMotionModel}s is
 * prohibited. Use {@link Gmm#instance(Imt)} to retrieve an instance for a
 * desired {@link Imt}.</p>
 * 
 * <p><b>Implementation note:</b> Mean values are clamped per
 * {@link GmmUtils#ceusMeanClip(Imt, double)}.</p>
 * 
 * <p><b>Reference:</b> Silva, W., Gregor, N., and Darragh, R., 2002,
 * Development of hard rock attenuation relations for central and eastern North
 * America, internal report from Pacific Engineering, November 1, 2002, <a href=
 * "http://www.pacificengineering.org/CEUS/Development%20of%20Regional%20Hard_ABC.pdf"
 * >http://www.pacificengineering.org/CEUS/Development of Regional
 * Hard_ABC.pdf</a></p>
 * 
 * <p><b>Component:</b> average horizontal (not clear from publication)</p>
 * 
 * @author Peter Powers
 * @see Gmm#SILVA_02
 * @see Gmm#SILVA_02_AB
 * @see Gmm#SILVA_02_J
 */
public class SilvaEtAl_2002 implements GroundMotionModel, ConvertsMag {

  // TODO clean
  // notes from original implementation and fortran:
  //
  // c1 from c1hr using A->BC factors, 1.74 for 0.1s, 1.72 for 0.3s, 1.58 for
  // 0.5s, and 1.20 for 2s
  // this from A Frankel advice, Mar 14 2007. For 25 hz use PGA amp.
  // For BC at 2.5 hz use interp between .3 and .5. 1.64116 whose log is
  // 0.4953
  //
  // c note very high sigma for longer period SA

  static final String NAME = "Silva et al. (2002)";

  static final Constraints CONSTRAINTS = Constraints.builder()
    .set(MAG, Range.closed(4.0, 8.0))
    .set(RJB, Range.closed(0.0, 1000.0))
    .set(VS30, Range.closed(760.0, 2000.0))
    .build();

  static final CoefficientContainer COEFFS = new CoefficientContainer("Silva02.csv");

  private static final class Coefficients {

    final Imt imt;
    final double c1, c1hr, c2, c4, c6, c7, c10, σ;

    Coefficients(Imt imt, CoefficientContainer cc) {
      this.imt = imt;
      Map<String, Double> coeffs = cc.get(imt);
      c1 = coeffs.get("c1");
      c1hr = coeffs.get("c1hr");
      c2 = coeffs.get("c2");
      c4 = coeffs.get("c4");
      c6 = coeffs.get("c6");
      c7 = coeffs.get("c7");
      c10 = coeffs.get("c10");
      σ = coeffs.get("sigma");
    }
  }

  private final Coefficients coeffs;

  SilvaEtAl_2002(final Imt imt) {
    coeffs = new Coefficients(imt, COEFFS);
  }

  @Override
  public final ScalarGroundMotion calc(final GmmInput in) {
    SiteClass siteClass = GmmUtils.ceusSiteClass(in.vs30);
    double μ = calcMean(coeffs, converter().convert(in.Mw), in.rJB, siteClass);
    return DefaultScalarGroundMotion.create(μ, coeffs.σ);
  }

  @Override
  public MagConverter converter() {
    return NONE;
  }

  private static final double calcMean(final Coefficients c, final double Mw, final double rJB,
      final SiteClass siteClass) {

    double c1 = (siteClass == HARD_ROCK) ? c.c1hr : c.c1;
    double gnd0 = c1 + c.c2 * Mw + c.c10 * (Mw - 6.0) * (Mw - 6.0);
    double fac = c.c6 + c.c7 * Mw;
    double gnd = gnd0 + fac * Math.log(rJB + exp(c.c4));

    return GmmUtils.ceusMeanClip(c.imt, gnd);
  }

}
