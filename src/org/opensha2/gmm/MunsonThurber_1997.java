package org.opensha2.gmm;

import static java.lang.Math.log10;
import static java.lang.Math.sqrt;

import static org.opensha2.gmm.GmmInput.Field.MAG;
import static org.opensha2.gmm.GmmInput.Field.RJB;
import static org.opensha2.gmm.GmmInput.Field.VS30;
import static org.opensha2.gmm.GmmUtils.BASE_10_TO_E;

import org.opensha2.gmm.GmmInput.Constraints;

import com.google.common.collect.Range;

/**
 * Implementation of the Munson & Thurber (1997) ground motion model for
 * horizontal peak ground acceleration (PGA) for the island of Hawaii. In
 * keeping with prior NSHMP implementations of this older model, only lava sites
 * are supported (V𝗌30 = 650 m/s).
 * 
 * <p>For the 1998 Hawaii NSHM, maps were calculated for rock (Vs30 = 760 m/s)
 * using the lava coefficients, as this was deemed close enough to the measured
 * Vs30 = 650 m/s of the lava site velocity (C. Thurber, personal comm., 1998).
 * 
 * <p><b>Note:</b> This GMM is only valid for PGA and 0.2 seconds. 0.2 is
 * supported through the linear scaling of log PGA ground motion. Also,
 * for larger magnitudes (M > 7), an additional magnitude term derived from
 * Boore, Joyner, and Fumal 1993) is applied. See also the 1998 Alaska NSHMP
 * <a href="http://earthquake.usgs.gov/hazards/products/hi/1998/documentation/">
 * documentation</a>.
 *
 * <p><b>Note:</b> Direct instantiation of {@code GroundMotionModel}s is
 * prohibited. Use {@link Gmm#instance(Imt)} to retrieve an instance for a
 * desired {@link Imt}.
 *
 * <p><b>Reference:</b> Munson, C.G., and Thurber, C.H., 1997, Analysis of the
 * attenuation of strong ground motion on the island of Hawaii, Bulletin of the
 * Seismological Society of America, v. 87, n. 4, pp. 945-960.
 *
 * <p><b>Component:</b> larger of the two horizontal
 *
 * @author Allison Shumway
 * @author Peter Powers
 * @see Gmm#MT_97
 */
public final class MunsonThurber_1997 implements GroundMotionModel {

  static final String NAME = "Munson & Thurber (1997)";

  static final Constraints CONSTRAINTS = Constraints.builder()
      .set(MAG, Range.closed(4.0, 8.0))
      .set(RJB, Range.closed(0.0, 88.0))
      .set(VS30, Range.closed(200.0, 760.0))
      .build();

  static final CoefficientContainer COEFFS = new CoefficientContainer("MT97.csv");

  private static final double B0 = 0.518;
  private static final double B1 = 0.387;
  private static final double B1_BJF = 0.216;
  private static final double B2 = -0.00256;
  private static final double H2 = 11.29 * 11.29;
  private static final double SIGMA = 0.237 * BASE_10_TO_E;

  private final Imt imt;

  MunsonThurber_1997(final Imt imt) {
    this.imt = imt;
  }

  @Override
  public ScalarGroundMotion calc(final GmmInput in) {
    double μ = calcMean(in.Mw, in.rJB);
    if (imt == Imt.SA0P02) {
      μ *= 2.2;
    }
    return DefaultScalarGroundMotion.create(μ, SIGMA);
  }

  private final double calcMean(final double Mw, final double rJB) {
    // Implementation is only of rsoft-rock/lava sites so ash
    // term has been removed
    double r = sqrt((rJB * rJB) + H2);
    double μBase10 = B0 + magTerm(Mw) + (B2 * r) - log10(r);
    return μBase10 * BASE_10_TO_E;
  }
  
  private static double magTerm(double Mw) {
    if (Mw <= 7.0) {
      return B1 * (Mw - 6.0);
    } else if (Mw <=7.7) {
      return B1 + B1_BJF * (Mw - 7.0);
    } else {
      return B1 + B1_BJF;
    }
  }

}
