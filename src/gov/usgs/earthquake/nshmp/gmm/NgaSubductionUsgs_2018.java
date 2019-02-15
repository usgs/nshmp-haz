package gov.usgs.earthquake.nshmp.gmm;

import static gov.usgs.earthquake.nshmp.gmm.GmmInput.Field.MW;
import static gov.usgs.earthquake.nshmp.gmm.GmmInput.Field.RRUP;
import static gov.usgs.earthquake.nshmp.gmm.GmmInput.Field.VS30;
import static gov.usgs.earthquake.nshmp.gmm.GmmInput.Field.ZTOP;
import static gov.usgs.earthquake.nshmp.gmm.Imt.PGA;
import static java.lang.Math.exp;
import static java.lang.Math.log;
import static java.lang.Math.min;
import static java.lang.Math.pow;

import java.util.Map;

import com.google.common.collect.Range;

import gov.usgs.earthquake.nshmp.eq.Earthquakes;
import gov.usgs.earthquake.nshmp.gmm.GmmInput.Constraints;
import gov.usgs.earthquake.nshmp.util.Maths;

/**
 * Abstract implementation of the preliminary PEER NGA-Subduction ground motion
 * model. The model is a custom USGS product and will likely be superceded by
 * the final NGA-Subduction models, when available. The model supports both slab
 * and interface type events.
 *
 * <p><b>Note:</b> Direct instantiation of {@code GroundMotionModel}s is
 * prohibited. Use {@link Gmm#instance(Imt)} to retrieve an instance for a
 * desired {@link Imt}.
 *
 * <p><b>Model and implementation notes:</b><ul>
 * 
 * <li>Calibrated for use in Cascadia only.</li>
 * 
 * <li>Makes no distinction between forearc and backarc sites.</li>
 * 
 * <li>{@code zTop} is interpreted as hypocentral depth and is only used for
 * slab events; it is limited to 100 km, consistent with other subduction
 * models.</li>
 * 
 * <li>Epistemic uncertainty on the Cascadia adjustment term can be disabled by
 * using {@link Gmm} instances with the {@code *_NO_EPI} suffix.</li></ul>
 *
 * <p><b>Reference:</b> Abrahamson, N., Kuehn, N., Zeynep Gulerce, Z., Gregor,
 * N., Bozognia, Y., Parker, G., Stewart, J., Chiou, B., Idriss, I.M., Campbell,
 * K., and Youngs, R., 2018, 2017, Update of the BC Hydro subduction
 * ground-motion model using the NGA-Subduction dataset: PEER Report No.
 * 2018/02, 101 p.
 *
 * <p><b>Component:</b> Geometric mean of two horizontal components
 *
 * @author Peter Powers
 * @see Gmm#NGA_SUB_USGS_INTERFACE
 * @see Gmm#NGA_SUB_USGS_INTERFACE_NO_EPI
 * @see Gmm#NGA_SUB_USGS_SLAB
 * @see Gmm#NGA_SUB_USGS_SLAB_NO_EPI
 */
public abstract class NgaSubductionUsgs_2018 implements GroundMotionModel {

  static final String NAME = "NGA-Subduction USGS (2018)";

  /*
   * Developer notes:
   * 
   * Implementation includes fixes to typos in report: (1) Equation for fSite
   * needs '+' after -b Ln(PGA1000+c) (2) Coefficient A3 should be 0.1 not -0.1
   * (3) Slab adjustment term epi at 7.5s and 10s should be 0.3 in Table 4.4
   */

  // TODO need subtype specific constraints
  static final Constraints CONSTRAINTS = Constraints.builder()
      .set(MW, Range.closed(5.0, 9.5))
      .set(RRUP, Range.closed(0.0, 1000.0))
      .set(ZTOP, Earthquakes.SLAB_DEPTH_RANGE)
      .set(VS30, Range.closed(150.0, 1000.0))
      .build();

  static final CoefficientContainer COEFFS = new CoefficientContainer("nga-subduction.csv");

  private static final double VS30_ROCK = 1000.0;
  private static final double N = 1.18;
  private static final double C = 1.88;
  private static final double C4 = 10.0; // km
  private static final double A3 = 0.1;
  private static final double A5 = 0.0;
  private static final double A9 = 0.4;
  private static final double A10 = 1.73;
  private static final double C1_SLAB = 7.2;
  private static final double Δ_INT_EPI_LO = -0.3;
  private static final double Δ_INT_EPI_HI = 0.3;
  private static final double Φ = 0.62;

  private static final double[] EPI_WTS = { 0.2, 0.6, 0.2 };
  private static final double[] SIGMA_WT = { 1.0 };

  private static final class Coefficients {

    final double a1, a2, a4, a6, a11, a12, a13, a14;
    final double vlin, b, c1int;
    final double Δint, Δslab, ΔslabEpiLo, ΔslabEpiHi;
    final double τ;

    Coefficients(Imt imt, CoefficientContainer cc) {
      Map<String, Double> coeffs = cc.get(imt);
      a1 = coeffs.get("a1");
      a2 = coeffs.get("a2");
      a4 = coeffs.get("a4");
      a6 = coeffs.get("a6");
      a11 = coeffs.get("a11");
      a12 = coeffs.get("a12");
      a13 = coeffs.get("a13");
      a14 = coeffs.get("a14");
      vlin = coeffs.get("vlin");
      b = coeffs.get("b");
      c1int = coeffs.get("c1int");
      Δint = coeffs.get("adj_int");
      Δslab = coeffs.get("adj_slab");
      ΔslabEpiLo = coeffs.get("adj_slab_epi_lo");
      ΔslabEpiHi = coeffs.get("adj_slab_epi_hi");
      τ = coeffs.get("tau");
    }
  }

  private final Coefficients coeffs;
  private final Coefficients coeffsPGA;

  NgaSubductionUsgs_2018(final Imt imt) {
    coeffs = new Coefficients(imt, COEFFS);
    coeffsPGA = new Coefficients(PGA, COEFFS);
  }

  @Override
  public final ScalarGroundMotion calc(final GmmInput in) {
    double pgaRock = (in.vs30 < coeffs.vlin)
        ? exp(calcMean(coeffsPGA, isSlab(), 0.0, in.Mw, in.rRup, in.zTop, VS30_ROCK))
        : 0.0;
    double μ = calcMean(coeffs, isSlab(), pgaRock, in.Mw, in.rRup, in.zTop, in.vs30);
    double σ = Maths.hypot(Φ, coeffs.τ);

    /* μ Cascadia adjustment */
    μ += isSlab() ? coeffs.Δslab : coeffs.Δint;

    if (!includeEpi()) {
      return DefaultScalarGroundMotion.create(μ, σ);
    }

    double epiLo = isSlab() ? coeffs.ΔslabEpiLo : Δ_INT_EPI_LO;
    double epiHi = isSlab() ? coeffs.ΔslabEpiHi : Δ_INT_EPI_HI;
    double[] means = { μ + epiLo, μ, μ + epiHi };

    // TODO need single sigma specialization of MSGM
    return new MultiScalarGroundMotion(
        means, EPI_WTS,
        new double[] { σ }, SIGMA_WT);
  }

  abstract boolean isSlab();

  abstract boolean includeEpi();

  private static final double calcMean(
      final Coefficients c,
      final boolean slab,
      final double pgaRock,
      final double Mw,
      final double rRup,
      final double zTop,
      final double vs30) {

    /* Mw scaling */
    double C1 = slab ? C1_SLAB : c.c1int;
    double a13m = c.a13 * (10 - Mw) * (10 - Mw);
    double fMag = (Mw <= C1 ? c.a4 : A5) * (Mw - C1) + a13m;

    /* Depth scaling; slab only */
    double fDepth = slab ? c.a11 * (min(zTop, 100.0) - 60.) : 0.0;

    /* Nonlinear site response scaling */
    double vsS = min(vs30, VS30_ROCK);
    double lnVs = log(vsS / c.vlin);
    double fSite = (vs30 < c.vlin)
        ? c.a12 * lnVs - c.b * log(pgaRock + C) + c.b * log(pgaRock + C * pow((vsS / c.vlin), N))
        : (c.a12 + c.b * N) * lnVs;

    double ΔC1term = slab ? c.a4 * (C1_SLAB - c.c1int) : 0.0;
    return c.a1 + ΔC1term +
        (c.a2 + (slab ? c.a14 : 0.0) + A3 * (Mw - 7.8)) *
            log(rRup + C4 * exp((Mw - 6.0) * A9)) +
        c.a6 * rRup + (slab ? A10 : 0.0) +
        fMag + fDepth + fSite;
  }

  static class Interface extends NgaSubductionUsgs_2018 {
    static final String NAME = NgaSubductionUsgs_2018.NAME + " : Interface";

    Interface(Imt imt) {
      super(imt);
    }

    @Override
    final boolean isSlab() {
      return false;
    }

    @Override
    boolean includeEpi() {
      return true;
    }
  }

  static class InterfaceCenter extends Interface {
    static final String NAME = Interface.NAME + " (no epi)";

    InterfaceCenter(Imt imt) {
      super(imt);
    }

    @Override
    boolean includeEpi() {
      return false;
    }
  }

  static class Slab extends NgaSubductionUsgs_2018 {
    static final String NAME = NgaSubductionUsgs_2018.NAME + " : Slab";

    Slab(Imt imt) {
      super(imt);
    }

    @Override
    final boolean isSlab() {
      return true;
    }

    @Override
    boolean includeEpi() {
      return true;
    }
  }

  static class SlabCenter extends Slab {
    static final String NAME = Slab.NAME + " (no epi)";

    SlabCenter(Imt imt) {
      super(imt);
    }

    @Override
    boolean includeEpi() {
      return false;
    }
  }
}
