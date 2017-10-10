package gov.usgs.earthquake.nshmp.gmm;

import static gov.usgs.earthquake.nshmp.gmm.GmmInput.Field.MW;
import static gov.usgs.earthquake.nshmp.gmm.GmmInput.Field.RJB;
import static gov.usgs.earthquake.nshmp.gmm.GmmInput.Field.VS30;
import static gov.usgs.earthquake.nshmp.gmm.GmmUtils.BASE_10_TO_E;
import static java.lang.Math.log10;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.sqrt;

import com.google.common.collect.Range;

import java.util.Map;

import gov.usgs.earthquake.nshmp.gmm.GmmInput.Constraints;

/**
 * Implementation of the Shahjouei and Pezeshk (2016) ground motion model for
 * central and eastern North America (CENA). This ground motion model is an
 * updated version of the Shahjouei and Pezeshk NGA-East Seed Model
 * {@link Gmm#NGA_EAST_SEED_SP15}.
 *
 * <p><b>Note:</b> Direct instantiation of {@code GroundMotionModel}s is
 * prohibited. Use {@link Gmm#instance(Imt)} to retrieve an instance for a
 * desired {@link Imt}.
 *
 * <p><b>Reference:</b> Shahjouei, A., and Pezeshk, S., 2016. Alternative hybrid
 * ground-motion model for central and eastern North America using hybrid
 * simulations and NGA-West2 models: Bulletin of the Seismological Society of
 * America, v. 106, no. 2, pp. 734–754.
 *
 * <p><b>doi:</b> <a href="http://dx.doi.org/10.1785/0120140367">
 * 10.1785/0120140367</a>
 *
 * <p><b>Component:</b> average horizontal (RotD50)
 *
 * @author Allison Shumway
 * @author Peter Powers
 * @see Gmm#NGA_EAST_SEED_SP16
 */
public final class ShahjoueiPezeshk_2016 implements GroundMotionModel {

  /*
   * Developer notes:
   * 
   * Whereas some seed model updates were provided to the USGS as drop in
   * updates to the NGA-East ground motion table format (e.g. Graizer17), this
   * update was provided as the complete (mean and sigma) functional form and is
   * therefore implemented outside the NgaEastUsgs_2017 wrapper class.
   */
  static final String NAME = NgaEastUsgs_2017.Seed.NAME + "SP16";

  static final Constraints CONSTRAINTS = Constraints.builder()
      .set(MW, Range.closed(4.0, 8.0))
      .set(RJB, Range.closed(0.0, 1000.0))
      .set(VS30, Range.singleton(3000.0))
      .build();

  static final CoefficientContainer COEFFS = new CoefficientContainer("SP16.csv");

  private static final double SIGMA_FAC = -6.898e-3;
  private static final double SIGMA_FAC_PGV = -3.054e-5;

  private static final class Coefficients {

    final Imt imt;
    final double c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14, σReg;

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
      c11 = coeffs.get("c11");
      c12 = coeffs.get("c12");
      c13 = coeffs.get("c13");
      c14 = coeffs.get("c14");
      σReg = coeffs.get("sigma_reg");
    }
  }

  private final Coefficients coeffs;

  ShahjoueiPezeshk_2016(final Imt imt) {
    coeffs = new Coefficients(imt, COEFFS);
  }

  @Override
  public final ScalarGroundMotion calc(final GmmInput in) {
    double μ = calcMean(coeffs, in.Mw, in.rJB);
    double σ = calcStdDev(coeffs, in.Mw);
    return DefaultScalarGroundMotion.create(μ, σ);
  }

  private static double calcMean(final Coefficients c, final double Mw, final double rJB) {
    double r = sqrt(rJB * rJB + c.c11 * c.c11);
    double μ = c.c1 +
        (c.c2 * Mw) +
        (c.c3 * Mw * Mw) +
        (c.c4 + c.c5 * Mw) * min(log10(r), log10(60.0)) +
        (c.c6 + c.c7 * Mw) * max(min(log10(r / 60.0), log10(2.0)), 0.0) +
        (c.c8 + c.c9 * Mw) * max(log10(r / 120.0), 0.0) +
        (c.c10 * r);
    return μ * BASE_10_TO_E;
  }

  /* Aleatory sigma model only. */
  private static double calcStdDev(final Coefficients c, final double Mw) {
    double ψ = c.imt == Imt.PGV ? SIGMA_FAC_PGV : SIGMA_FAC;
    double σlnY = (Mw <= 6.5) ? c.c12 * Mw + c.c13 : ψ * Mw + c.c14;
    return sqrt(σlnY * σlnY + c.σReg * c.σReg);
  }

}
