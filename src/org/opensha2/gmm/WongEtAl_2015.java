package org.opensha2.gmm;

import static java.lang.Math.exp;
import static java.lang.Math.log;
import static java.lang.Math.max;

import static org.opensha2.gmm.GmmInput.Field.MW;
import static org.opensha2.gmm.GmmInput.Field.RRUP;
import static org.opensha2.gmm.GmmInput.Field.VS30;
import static org.opensha2.gmm.GmmInput.Field.ZTOP;

import org.opensha2.gmm.GmmInput.Constraints;

import com.google.common.annotations.Beta;
import com.google.common.collect.Range;

import java.util.Map;

/**
 * Implementation of the ground motion model by Wong et al. (2015) for deep
 * event (>20 km) on the Big Island, Hawaii.
 *
 * <p><b>Note:</b> Direct instantiation of {@code GroundMotionModel}s is
 * prohibited. Use {@link Gmm#instance(Imt)} to retrieve an instance for a
 * desired {@link Imt}.
 *
 * <p><b>Reference:</b> Wong, I.G., Silva, W.J., Darragh, R., Gregor, N., and
 * Dober, M., 2015, A ground motion prediction model for deep earthquakes
 * beneath the island of Hawaii: Earthquake Spectra, v. 31, p. 1763–1788.
 * 
 * <p><b>doi:</b><a href="http://doi.org/10.1193/012012EQS015M">
 * 10.1193/012012EQS015M</a>
 *
 * <p><b>Component:</b> average horizontal
 *
 * @author Peter Powers
 * @see Gmm#WONG_15
 */
@Beta
public final class WongEtAl_2015 implements GroundMotionModel {

  static final String NAME = "Wong et al. (2015)";

  // TODO
  static final Constraints CONSTRAINTS = Constraints.builder()
      .set(MW, Range.closed(5.0, 8.0))
      .set(RRUP, Range.closed(0.0, 300.0))
      .set(ZTOP, Range.closed(20.0, 60.0))
      .set(VS30, Range.singleton(760.0))
      .build();

  private static final double R_MIN = 20.0;
  
  static final CoefficientContainer COEFFS = new CoefficientContainer("Wong15.csv");

  private static final class Coefficients {

    final double c1, c2, c3, c4, c5, c6, σ;

    Coefficients(Imt imt, CoefficientContainer cc) {
      Map<String, Double> coeffs = cc.get(imt);
      c1 = coeffs.get("C1");
      c2 = coeffs.get("C2");
      c3 = coeffs.get("C3");
      c4 = coeffs.get("C4");
      c5 = coeffs.get("C5");
      c6 = coeffs.get("C6");
      σ = coeffs.get("sigma");
    }
  }

  private final Coefficients coeffs;

  WongEtAl_2015(final Imt imt) {
    coeffs = new Coefficients(imt, COEFFS);
  }

  @Override
  public final ScalarGroundMotion calc(final GmmInput in) {
    return DefaultScalarGroundMotion.create(
        calcMean(coeffs, in),
        coeffs.σ);
  }

  private static final double calcMean(final Coefficients c, final GmmInput in) {
    double Mw = in.Mw;
    return c.c1 + c.c2 * Mw +
        (c.c4 + c.c5 * Mw) * log(max(R_MIN, in.rJB) + exp(c.c3)) +
        c.c6 * (Mw - 6.0) * (Mw - 6.0);
  }

}
