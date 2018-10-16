package gov.usgs.earthquake.nshmp.gmm;

import static gov.usgs.earthquake.nshmp.gmm.GmmInput.Field.MW;
import static gov.usgs.earthquake.nshmp.gmm.GmmInput.Field.RRUP;
import static gov.usgs.earthquake.nshmp.gmm.GmmInput.Field.VS30;
import static gov.usgs.earthquake.nshmp.gmm.GmmUtils.BASE_10_TO_E;
import static gov.usgs.earthquake.nshmp.gmm.GmmUtils.LN_G_CM_TO_M;
import static java.lang.Math.log10;
import static java.lang.Math.max;
import static java.lang.Math.pow;
import static java.lang.Math.sqrt;

import java.util.Map;

import com.google.common.annotations.Beta;
import com.google.common.collect.Range;

import gov.usgs.earthquake.nshmp.gmm.GmmInput.Constraints;

/**
 * Implementation of the ground motion model by Atkinson (2015) for induced
 * seismicity.
 *
 * <p><b>Note:</b> Direct instantiation of {@code GroundMotionModel}s is
 * prohibited. Use {@link Gmm#instance(Imt)} to retrieve an instance for a
 * desired {@link Imt}.
 *
 * <p><b>Reference:</b> Atkinson, G.M., 2015, Ground-motion prediction equation
 * for small-to-moderate events at short hypocentral distances, with application
 * to induced-seismicity hazards: Bulletin of the Seismological Society of
 * America, v. 105, p. 981-992.
 *
 * <p><b>doi:</b><a href="http://dx.doi.org/10.1785/0120140142" target="_top">
 * 10.1785/0120140142</a>
 *
 * <p><b>Component:</b> orientation-independent horizontal
 *
 * @author Peter Powers
 * @see Gmm#ATKINSON_15
 */
@Beta
public final class Atkinson_2015 implements GroundMotionModel {

  static final String NAME = "Atkinson (2015)";

  // TODO
  static final Constraints CONSTRAINTS = Constraints.builder()
      .set(MW, Range.closed(3.0, 6.0))
      .set(RRUP, Range.closed(0.0, 300.0))
      .set(VS30, Range.singleton(760.0))
      .build();

  static final CoefficientContainer COEFFS = new CoefficientContainer("Atkinson15.csv");

  private static final class Coefficients {

    final Imt imt;
    final double c0, c1, c2, c3, c4, φ, τ, σ;

    Coefficients(Imt imt, CoefficientContainer cc) {
      this.imt = imt;
      Map<String, Double> coeffs = cc.get(imt);
      c0 = coeffs.get("c0");
      c1 = coeffs.get("c1");
      c2 = coeffs.get("c2");
      c3 = coeffs.get("c3");
      c4 = coeffs.get("c4");
      φ = coeffs.get("phi");
      τ = coeffs.get("tau");
      σ = coeffs.get("sigma");
    }
  }

  private final Coefficients coeffs;

  Atkinson_2015(final Imt imt) {
    coeffs = new Coefficients(imt, COEFFS);
  }

  @Override
  public final ScalarGroundMotion calc(final GmmInput in) {
    double μ = calcMean(coeffs, in);
    double σ = coeffs.σ * BASE_10_TO_E;
    return DefaultScalarGroundMotion.create(GmmUtils.ceusMeanClip(coeffs.imt, μ), σ);
  }

  private static final double calcMean(final Coefficients c, final GmmInput in) {
    double Mw = in.Mw;
    double rRup = in.rRup;
    double h_eff = max(1, pow(10, -1.72 + 0.43 * Mw));
    double r = sqrt(rRup * rRup + h_eff * h_eff);
    double μ = c.c0 + c.c1 * Mw + c.c2 * Mw * Mw + c.c3 * log10(r) + c.c4 * r;
    return μ * BASE_10_TO_E - LN_G_CM_TO_M;
  }

}
