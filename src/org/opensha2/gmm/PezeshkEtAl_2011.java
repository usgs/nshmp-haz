package org.opensha2.gmm;

import static org.opensha2.gmm.GmmInput.Field.MAG;
import static org.opensha2.gmm.GmmInput.Field.RRUP;
import static org.opensha2.gmm.GmmInput.Field.VS30;
import static org.opensha2.gmm.GmmUtils.BASE_10_TO_E;
import static org.opensha2.gmm.GmmUtils.atkinsonTableValue;

import java.util.Map;

import org.opensha2.gmm.GmmInput.Constraints;
import org.opensha2.gmm.GroundMotionTables.GroundMotionTable;

import com.google.common.collect.Range;

/**
 * Implementation of the Pezeshk, Zandieh, & Tavakoli (2011) ground motion model
 * for stable continental regions. This implementation matches that used in the
 * 2014 USGS NSHMP and uses table lookups (median) and functional forms (sigma)
 * to compute ground motions.
 * 
 * <p><b>Note:</b> Direct instantiation of {@code GroundMotionModel}s is
 * prohibited. Use {@link Gmm#instance(Imt)} to retrieve an instance for a
 * desired {@link Imt}.</p>
 * 
 * <p><b>Implementation note:</b> Mean values are clamped per
 * {@link GmmUtils#ceusMeanClip(Imt, double)}.</p>
 * 
 * <p><b>Reference:</b> Pezeshk, S., Zandieh, A., Tavakoli, B., 2011. Hybrid
 * empirical ground-motion prediction equations for eastern North America using
 * NGA models and updated seismological parameters: Bulletin of the
 * Seismological Society of America, v. 101, no. 4, p. 1859–1870.</p>
 * 
 * <p><b>doi:</b> <a href="http://dx.doi.org/10.1785/0120100144">
 * 10.1785/0120100144</a></p>
 * 
 * <p><b>Component:</b> GMRotI50 (geometric mean)</p>
 * 
 * @author Peter Powers
 * @see Gmm#PEZESHK_11
 */
public final class PezeshkEtAl_2011 implements GroundMotionModel {

  static final String NAME = "Pezeshk et al. (2011)";

  static final Constraints CONSTRAINTS = Constraints.builder()
    .set(MAG, Range.closed(4.0, 8.0))
    .set(RRUP, Range.closed(0.0, 1000.0))
    .set(VS30, Range.closed(760.0, 2000.0))
    .build();

  static final CoefficientContainer COEFFS = new CoefficientContainer("P11.csv");

  private static final double SIGMA_FAC = -6.95e-3;

  private static final class Coefficients {

    final Imt imt;
    final double c12, c13, c14, bcfac;

    Coefficients(Imt imt, CoefficientContainer cc) {
      this.imt = imt;
      Map<String, Double> coeffs = cc.get(imt);
      c12 = coeffs.get("c12");
      c13 = coeffs.get("c13");
      c14 = coeffs.get("c14");
      bcfac = coeffs.get("bcfac");
    }
  }

  private final Coefficients coeffs;
  private final GroundMotionTable table;

  PezeshkEtAl_2011(final Imt imt) {
    coeffs = new Coefficients(imt, COEFFS);
    table = GroundMotionTables.getPezeshk11(imt);
  }

  @Override
  public final ScalarGroundMotion calc(final GmmInput in) {
    double r = Math.max(in.rRup, 1.0);
    double μ = atkinsonTableValue(table, coeffs.imt, in.Mw, r, in.vs30, coeffs.bcfac);
    double σ = calcStdDev(coeffs, in.Mw);
    return DefaultScalarGroundMotion.create(GmmUtils.ceusMeanClip(coeffs.imt, μ), σ);
  }

  private static double calcStdDev(final Coefficients c, final double Mw) {
    double σ = (Mw <= 7.0) ? c.c12 * Mw + c.c13 : SIGMA_FAC * Mw + c.c14;
    return σ * BASE_10_TO_E;
  }

}
