package org.opensha2.gmm;

import static java.lang.Math.log;

import static org.opensha2.gmm.GmmInput.Field.MW;
import static org.opensha2.gmm.GmmInput.Field.RJB;
import static org.opensha2.gmm.GmmInput.Field.VS30;
import static org.opensha2.gmm.SiteClass.HARD_ROCK;
import static org.opensha2.util.Maths.hypot;

import org.opensha2.gmm.GmmInput.Constraints;

import com.google.common.collect.Range;

import java.util.Map;

/**
 * Implementation of the hard rock ground motion model for the Central and
 * Eastern US by Somerville et al. (2001). This implementation matches that used
 * in the 2008 USGS NSHMP and is only used for fault sources and gridded
 * representation of faults (e.g. Charleston).
 *
 * <p><b>Note:</b> Direct instantiation of {@code GroundMotionModel}s is
 * prohibited. Use {@link Gmm#instance(Imt)} to retrieve an instance for a
 * desired {@link Imt}.
 *
 * <p><b>Implementation note:</b> Mean values are clamped per
 * {@link GmmUtils#ceusMeanClip(Imt, double)}.
 *
 * <p><b>Reference:</b> Somerville, P., Collins, N., Abrahamson, N., Graves, R.,
 * and Saikia, C., 2001, Ground motion attenuation relations for the Central and
 * Eastern United States — Final report, June 30, 2001: Report to U.S.
 * Geological Survey for award 99HQGR0098, 38 p.
 *
 * <p><b>Component:</b> not specified
 *
 * @author Peter Powers
 * @see Gmm#SOMERVILLE_01
 */
public final class SomervilleEtAl_2001 implements GroundMotionModel {

  // * TODO check doc that distance is rjb
  // * verify that Somerville imposes dtor of 6.0:
  // * THIS IS NOT IMPOSED IN HAZFX
  // * e.g. double dist = Math.sqrt(rjb * rjb + 6.0 * 6.0);

  static final String NAME = "Somerville et al. (2001)";

  static final Constraints CONSTRAINTS = Constraints.builder()
      .set(MW, Range.closed(4.0, 8.0))
      .set(RJB, Range.closed(0.0, 1000.0))
      .set(VS30, Range.closed(760.0, 2000.0))
      .build();

  static final CoefficientContainer COEFFS = new CoefficientContainer("Somerville01.csv");

  private static final double Z_MIN = 6.0;
  private static final double R_CUT = 50.0; // km
  private static final double R1 = hypot(R_CUT, Z_MIN);

  private static final class Coefficients {

    final Imt imt;
    final double a1, a1h, a2, a3, a4, a5, a6, a7, σ0;

    Coefficients(Imt imt, CoefficientContainer cc) {
      this.imt = imt;
      Map<String, Double> coeffs = cc.get(imt);
      a1 = coeffs.get("a1");
      a1h = coeffs.get("a1h");
      a2 = coeffs.get("a2");
      a3 = coeffs.get("a3");
      a4 = coeffs.get("a4");
      a5 = coeffs.get("a5");
      a6 = coeffs.get("a6");
      a7 = coeffs.get("a7");
      σ0 = coeffs.get("sig0");
    }
  }

  private final Coefficients coeffs;

  SomervilleEtAl_2001(final Imt imt) {
    coeffs = new Coefficients(imt, COEFFS);
  }

  @Override
  public final ScalarGroundMotion calc(final GmmInput in) {
    double μ = calcMean(coeffs, in.Mw, in.rJB, in.vs30);
    return DefaultScalarGroundMotion.create(μ, coeffs.σ0);
  }

  private static final double calcMean(final Coefficients c, final double Mw, final double rJB,
      final double vs30) {

    SiteClass siteClass = GmmUtils.ceusSiteClass(vs30);
    double gnd = (siteClass == HARD_ROCK) ? c.a1h : c.a1;
    gnd += c.a2 * (Mw - 6.4) + c.a7 * (8.5 - Mw) * (8.5 - Mw);

    // Somerville fixes depth at 6km - faults and gridded
    double R = hypot(rJB, Z_MIN);

    gnd += c.a4 * (Mw - 6.4) * log(R) + c.a5 * rJB;
    if (rJB < R_CUT) {
      gnd += c.a3 * log(R);
    } else {
      gnd += c.a3 * log(R1) + c.a6 * (log(R) - log(R1));
    }

    return GmmUtils.ceusMeanClip(c.imt, gnd);
  }

}
