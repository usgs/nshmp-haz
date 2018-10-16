package gov.usgs.earthquake.nshmp.gmm;

import static gov.usgs.earthquake.nshmp.gmm.FaultStyle.NORMAL;
import static gov.usgs.earthquake.nshmp.gmm.FaultStyle.REVERSE;
import static gov.usgs.earthquake.nshmp.gmm.GmmInput.Field.MW;
import static gov.usgs.earthquake.nshmp.gmm.GmmInput.Field.RRUP;
import static gov.usgs.earthquake.nshmp.gmm.GmmInput.Field.VS30;
import static gov.usgs.earthquake.nshmp.gmm.GmmInput.Field.ZTOP;
import static java.lang.Math.exp;
import static java.lang.Math.log;
import static java.lang.Math.min;
import static java.lang.Math.sqrt;

import java.util.Map;

import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Range;

import gov.usgs.earthquake.nshmp.data.Interpolator;
import gov.usgs.earthquake.nshmp.eq.Earthquakes;
import gov.usgs.earthquake.nshmp.gmm.GmmInput.Constraints;

/**
 * Abstract implementation of the shallow crustal, upper mantle, subduction
 * interface, and subduction slab ground motion models by Zhao et al. (2016).
 * 
 * <p><b>Implementation notes:</b><ul>
 * 
 * <li>All models supply site-class specific sigma, however, only the total
 * sigma is used here as reported in the various coefficient tables.</li>
 * 
 * <li>All models currently ignore volcanic path terms.</li>
 * 
 * <li>The interface model handles shallow, {@code zHyp ≤ 25 km}, and deep,
 * {@code zHyp > 25 km}, events differently.</li>
 * 
 * <li>THe site amplification term is handled via interpolation of terms
 * computed for discrete Vs30 values that correspond to the Zhao site classes:
 * I, II, III, and IV.</li>
 * 
 * </ul>
 * 
 * <p><b>References:</b><ul>
 * 
 * <li>Zhao, J.X., Liang, X., Jiang, F., Xing, H., Zhu, M., Hou, R., Zhang, Y.,
 * Lan, X., Rhoades, D.A., Irikura, K., Fukushima, Y., and Somerville, P.G.,
 * 2016, Ground-motion prediction equations for subduction interface earthquakes
 * in Japan using site class and simple geometric attenuation functions:
 * Bulletin of the Seismological Society of America, v. 106, p.
 * 1518-1534.<b>doi:</b> <a href="http://dx.doi.org/10.1785/0120150034"
 * target="_top">10.1785/0120150034</a></li>
 * 
 * <li>Zhao, J.X., Jiang, F., Shi, P., Xing, H., Huang, H., Hou, R., Zhang, Y.,
 * Yu, P., Lan, X., Rhoades, D.A., Somerville, P.G., Irikura, K., and Fukushima,
 * Y., 2016, Ground-motion prediction equations for subduction slab earthquakes
 * in Japan using site class and simple geometric attenuation functions:
 * Bulletin of the Seismological Society of America, v. 106, p.
 * 1535-1551.<b>doi:</b> <a href="http://dx.doi.org/10.1785/0120150056"
 * target="_top">10.1785/0120150056</a></li>
 * 
 * <li>Zhao, J.X., Zhou, S.L., Gao, P.J., Zhang, Y.B., Zhou, J., Lu, M., and
 * Rhoades, D.A., 2016, Ground-motion prediction equations for shallow crustal
 * and upper mantle earthquakes in Japan using site class and simple geometric
 * attenuation functions: Bulletin of the Seismological Society of America, v.
 * 106, p. 1552-1569.<b>doi:</b> <a href="http://dx.doi.org/10.1785/0120150063"
 * target="_top">10.1785/0120150063</a></li>
 * 
 * </ul>
 *
 * <p><b>Component:</b> geometric mean of two randomly oriented horizontal
 * components
 * 
 * @author Peter Powers
 * @see Gmm#ZHAO_16_SHALLOW_CRUST
 * @see Gmm#ZHAO_16_UPPER_MANTLE
 * @see Gmm#ZHAO_16_INTERFACE
 * @see Gmm#ZHAO_16_SLAB
 */
@Beta
public abstract class ZhaoEtAl_2016 implements GroundMotionModel {

  /*
   * Implementation notes:
   * 
   * Site amplification terms shared by all models, Table 3, Zhao, Zhou et al.
   * (2016) stored in Zhao16_siteamp.csv. The 'rock-site factor' (AmSCI) was
   * removed from this table because the values were independently smoothed in
   * the interface model; AmSCI is included as a coefficient with the values in
   * the curstal and slab tables being the same.
   * 
   * TODO See notes in nonlinCrossover().
   */

  static final String NAME = "Zhao et al. (2016)";

  // TODO this needs updating, custom constraints for subclasses
  static final Constraints CONSTRAINTS = Constraints.builder()
      .set(MW, Range.closed(5.0, 9.5))
      .set(RRUP, Range.closed(0.0, 1000.0))
      .set(ZTOP, Earthquakes.SLAB_DEPTH_RANGE)
      .set(VS30, Range.closed(150.0, 1000.0))
      .build();

  static final CoefficientContainer COEFFS_CRUST = new CoefficientContainer("Zhao16_crust.csv");
  static final CoefficientContainer COEFFS_INTER = new CoefficientContainer("Zhao16_interface.csv");
  static final CoefficientContainer COEFFS_SLAB = new CoefficientContainer("Zhao16_slab.csv");
  static final CoefficientContainer SITE_AMP = new CoefficientContainer("Zhao16_siteamp.csv");

  private final SiteAmp siteAmp;

  private static final double MC = 7.1;
  private static final double X_0_CR = 2.0;
  private static final double X_0_INT = 10.0;

  ZhaoEtAl_2016(final Imt imt) {
    siteAmp = new SiteAmp(imt, SITE_AMP);
  }

  /*
   * Compute the natural-log "hard-rock" ground motion. This is the basic ground
   * motion model without the site term, ln(A).
   */
  abstract double saRock(GmmInput in);

  /*
   * The elastic site amplification ratio, aNmax, is unique to each site class
   * and model. The interface model has depth dependent values; zTop is ignored
   * by other implementations.
   */
  abstract double elasticSiteAmpRatio(SiteClass siteClass, double zTop);

  /*
   * Model and site-class dependent nonlinear soil site smoothing factor.
   */
  abstract double smoothingFactor(SiteClass siteClass);

  abstract double sigma();

  @Override
  public ScalarGroundMotion calc(GmmInput in) {
    double lnSaRock = saRock(in);
    double siteTerm = siteTerm(in, exp(lnSaRock));
    return new DefaultScalarGroundMotion(
        lnSaRock + siteTerm,
        sigma());
  }

  private double siteTerm(GmmInput in, double saRock) {
    Range<SiteClass> siteRange = siteRange(in.vs30);
    /*
     * Reverse range values here because lower site classes correspond to higher
     * Vs30 values.
     */
    SiteClass lower = siteRange.upperEndpoint();
    SiteClass upper = siteRange.lowerEndpoint();
    if (lower == upper) {
      return siteTerm(lower, in.zTop, saRock);
    }
    double fsLower = siteTerm(lower, in.zTop, saRock);
    double fsUpper = siteTerm(upper, in.zTop, saRock);
    return Interpolator.findY(
        lower.vs30, fsLower,
        upper.vs30, fsUpper,
        in.vs30);
  }

  private double siteTerm(SiteClass siteClass, double zTop, double saRock) {
    double aNmax = elasticSiteAmpRatio(siteClass, zTop);
    double aMax = siteAmp.aMax.get(siteClass);
    double sRC = siteAmp.sRc.get(siteClass);
    double Imf = siteClass.impedance;
    double fSR = smoothingFactor(siteClass);
    return siteTerm(aNmax, aMax, sRC, Imf, fSR, saRock);
  }

  /*
   * Zhao, Zhou, et al. (2016)
   */
  static final class ShallowCrust extends ZhaoEtAl_2016 {

    static final String NAME = ZhaoEtAl_2016.NAME + " : Shallow Crust";

    /* Tables 4, 5, 6 */
    static final class Coefficients {

      final double c1, c2, c, d, fN, b, g, gL, gN, e, γ, σ;

      /* Site amplification */
      final double AmSCI, s2, s3, s4;
      final Map<SiteClass, Double> fsr;

      /* Unused or constant: FumRV, FumNS, gum, eum, evcr, sigma, tau */

      Coefficients(Imt imt, CoefficientContainer cc) {

        Map<String, Double> coeffs = cc.get(imt);
        c1 = coeffs.get("c1");
        c2 = coeffs.get("c2");
        c = coeffs.get("ccr");
        d = coeffs.get("dcr");
        fN = coeffs.get("FcrN");
        b = coeffs.get("bcr");
        g = coeffs.get("gcr");
        gL = coeffs.get("gcrL");
        gN = coeffs.get("gcrN");
        e = coeffs.get("ecr");
        γ = coeffs.get("gamma");
        σ = coeffs.get("sigmaT");

        AmSCI = coeffs.get("AmSCI");
        s2 = coeffs.get("S2");
        s3 = coeffs.get("S3");
        s4 = coeffs.get("S4");

        fsr = Maps.immutableEnumMap(ImmutableMap.of(
            SiteClass.I, coeffs.get("FsrCrI"),
            SiteClass.II, coeffs.get("FsrCrII"),
            SiteClass.III, coeffs.get("FsrCrIII"),
            SiteClass.IV, coeffs.get("FsrCrIV")));
      }
    }

    private final Coefficients c;
    private final Map<SiteClass, Double> aNmax;

    ShallowCrust(Imt imt) {
      super(imt);
      c = new Coefficients(imt, COEFFS_CRUST);
      aNmax = Maps.immutableEnumMap(ImmutableMap.of(
          SiteClass.I, c.AmSCI,
          SiteClass.II, c.AmSCI * exp(c.s2),
          SiteClass.III, c.AmSCI * exp(c.s3),
          SiteClass.IV, c.AmSCI * exp(c.s4)));
    }

    @Override
    final double saRock(final GmmInput in) {

      FaultStyle style = GmmUtils.rakeToFaultStyle_NSHMP(in.rake);
      double Mw = in.Mw;
      double rRup = in.rRup;

      /* Source term; equation 1 */
      double mTerm = (Mw > MC) ? c.c * MC + c.d * (Mw - MC) : c.c * Mw;
      double fm = c.b * in.zTop + mTerm + (style == NORMAL ? c.fN : 0.0);

      /* Geometric spreading distance; equation 4 */
      double r = X_0_CR + rRup + exp(c.c1 + c.c2 * min(Mw, MC));

      /* Geometric attenuation terms; equations 3, 6 */
      double fg = c.g * log(r) +
          c.gL * log(in.rRup + 200.0) +
          c.gN * log(min(rRup, 30.0) + exp(c.c1 + 6.5 * c.c2));

      /* Anelastic attenuation term; volcanic ignored (eV * rRup) */
      double fe = c.e * in.rRup;

      return fm + fg + fe + c.γ;
    }

    @Override
    final double elasticSiteAmpRatio(final SiteClass siteClass, double zTop) {
      return aNmax.get(siteClass);
    }

    @Override
    double smoothingFactor(SiteClass siteClass) {
      return c.fsr.get(siteClass);
    }

    @Override
    final double sigma() {
      return c.σ;
    }
  }

  /*
   * Zhao, Zhou, et al. (2016)
   */
  static final class UpperMantle extends ZhaoEtAl_2016 {

    static final String NAME = ZhaoEtAl_2016.NAME + " : Upper Mantle";

    /* Tables 4, 5, 6 */
    static final class Coefficients {

      final double c1, c2, c, d, fRV, fNS, g, gL, gN, e, γ, σ;

      /* Site amplification */
      final double AmSCI, s2, s3, s4;
      final Map<SiteClass, Double> fsr;

      /* Unused or constant: FcrN, bcr, gcr, ecr, evcr, sigma, tau */

      Coefficients(Imt imt, CoefficientContainer cc) {

        Map<String, Double> coeffs = cc.get(imt);
        c1 = coeffs.get("c1");
        c2 = coeffs.get("c2");
        c = coeffs.get("ccr");
        d = coeffs.get("dcr");
        fRV = coeffs.get("FumRV");
        fNS = coeffs.get("FumNS");
        g = coeffs.get("gum");
        gL = coeffs.get("gcrL");
        gN = coeffs.get("gcrN");
        e = coeffs.get("eum");
        γ = coeffs.get("gamma");
        σ = coeffs.get("sigmaT");

        AmSCI = coeffs.get("AmSCI");
        s2 = coeffs.get("S2");
        s3 = coeffs.get("S3");
        s4 = coeffs.get("S4");

        fsr = Maps.immutableEnumMap(ImmutableMap.of(
            SiteClass.I, coeffs.get("FsrUmI"),
            SiteClass.II, coeffs.get("FsrUmII"),
            SiteClass.III, coeffs.get("FsrUmIII"),
            SiteClass.IV, coeffs.get("FsrUmIV")));
      }
    }

    private final Coefficients c;
    private final Map<SiteClass, Double> aNmax;

    UpperMantle(Imt imt) {
      super(imt);
      c = new Coefficients(imt, COEFFS_CRUST);
      aNmax = Maps.immutableEnumMap(ImmutableMap.of(
          SiteClass.I, c.AmSCI,
          SiteClass.II, c.AmSCI * exp(c.s2),
          SiteClass.III, c.AmSCI * exp(c.s3),
          SiteClass.IV, c.AmSCI * exp(c.s4)));
    }

    @Override
    final double saRock(final GmmInput in) {

      FaultStyle style = GmmUtils.rakeToFaultStyle_NSHMP(in.rake);
      double Mw = in.Mw;
      double rRup = in.rRup;

      /* Source term; equation 2 */
      double mTerm = (Mw > MC) ? c.c * MC + c.d * (Mw - MC) : c.c * Mw;
      double fm = mTerm + (style == REVERSE ? c.fRV : c.fNS);

      /* Geometric spreading distance; equation 4 */
      double r = X_0_CR + rRup + exp(c.c1 + c.c2 * min(Mw, MC));

      /* Geometric attenuation terms; equations 6, 7 */
      double fg = c.g * log(r) +
          c.gL * log(in.rRup + 200.0) +
          c.gN * log(min(rRup, 30.0) + exp(c.c1 + 6.5 * c.c2));

      /* Anelastic attenuation term; volcanic ignored (eV * rRup) */
      double fe = c.e * in.rRup;

      return fm + fg + fe + c.γ;
    }

    @Override
    final double elasticSiteAmpRatio(final SiteClass siteClass, double zTop) {
      return aNmax.get(siteClass);
    }

    @Override
    double smoothingFactor(SiteClass siteClass) {
      return c.fsr.get(siteClass);
    }

    @Override
    final double sigma() {
      return c.σ;
    }
  }

  /*
   * Zhao, Liang, et al. (2016)
   * 
   * Notes: use x_ij = rRup iff fault plane known, otherwise rHyp
   */
  static final class Interface extends ZhaoEtAl_2016 {

    static final String NAME = ZhaoEtAl_2016.NAME + " : Interface";

    private static final double C2 = 1.151;

    /* Tables 2, 3, 4, 5 */
    static final class Coefficients {

      final double c1, cD, cS, d, γS, b, g, gDL, gSL, eS, γ, σ;

      /* Site amplification */
      final double AmSCI, s2, s3, s4, s5, s6, s7;
      final Map<SiteClass, Double> fsr;

      /* Unused or constant: c2, eV, sigma, tau */

      Coefficients(Imt imt, CoefficientContainer cc) {

        Map<String, Double> coeffs = cc.get(imt);
        c1 = coeffs.get("c1");
        cD = coeffs.get("cD");
        cS = coeffs.get("cS");
        d = coeffs.get("d");
        γS = coeffs.get("gammaS");
        b = coeffs.get("b");
        g = coeffs.get("g");
        gDL = coeffs.get("gDL");
        gSL = coeffs.get("gSL");
        eS = coeffs.get("eS");
        γ = coeffs.get("gamma");
        σ = coeffs.get("sigmaT");

        AmSCI = coeffs.get("AmSCI");
        s2 = coeffs.get("S2");
        s3 = coeffs.get("S3");
        s4 = coeffs.get("S4");
        s5 = coeffs.get("S5");
        s6 = coeffs.get("S6");
        s7 = coeffs.get("S7");

        fsr = Maps.immutableEnumMap(ImmutableMap.of(
            SiteClass.I, coeffs.get("FsrI"),
            SiteClass.II, coeffs.get("FsrII"),
            SiteClass.III, coeffs.get("FsrIII"),
            SiteClass.IV, coeffs.get("FsrIV")));
      }
    }

    private final Coefficients c;
    private final Map<SiteClass, Double> aNmax_shallow;
    private final Map<SiteClass, Double> aNmax_deep;

    Interface(Imt imt) {
      super(imt);
      c = new Coefficients(imt, COEFFS_INTER);
      aNmax_shallow = Maps.immutableEnumMap(ImmutableMap.of(
          SiteClass.I, c.AmSCI,
          SiteClass.II, c.AmSCI * exp(c.s2),
          SiteClass.III, c.AmSCI * exp(c.s3),
          SiteClass.IV, c.AmSCI * exp(c.s4)));
      aNmax_deep = Maps.immutableEnumMap(ImmutableMap.of(
          SiteClass.I, c.AmSCI,
          SiteClass.II, c.AmSCI * exp(c.s5),
          SiteClass.III, c.AmSCI * exp(c.s6),
          SiteClass.IV, c.AmSCI * exp(c.s7)));
    }

    @Override
    final double saRock(final GmmInput in) {

      double Mw = in.Mw;
      double rRup = in.rRup;
      boolean deep = in.zHyp > 25.0;

      /* Source term; equations 1, 2 */
      double cDepth = deep ? c.cD : c.cS;
      double mTerm = (Mw > MC) ? cDepth * MC + c.d * (Mw - MC) : cDepth * Mw;
      double fm = c.b * in.zTop + c.γS + mTerm;

      /* Geometric spreading distance; equation 4 */
      double r = X_0_INT + rRup + exp(c.c1 + C2 * min(Mw, MC));

      /* Geometric attenuation terms; equations 3a, 6 */
      double fg = c.g * log(r) + (deep ? c.gDL : c.gSL) * log(rRup + 200.0);

      /* Anelastic attenuation (shallow only); volcanic ignored (eV * rV) */
      double fe = deep ? 0.0 : c.eS * rRup;

      return fm + fg + fe + c.γ;
    }

    @Override
    final double elasticSiteAmpRatio(final SiteClass siteClass, double zTop) {
      return (zTop > 25.0 ? aNmax_deep : aNmax_shallow).get(siteClass);
    }

    @Override
    double smoothingFactor(SiteClass siteClass) {
      return c.fsr.get(siteClass);
    }

    @Override
    final double sigma() {
      return c.σ;
    }
  }

  /*
   * Zhao, Jiang, et al. (2016)
   */
  static final class Slab extends ZhaoEtAl_2016 {

    static final String NAME = ZhaoEtAl_2016.NAME + " : Slab";

    private static final double MSC = 6.3;
    private static final double ΔMC = MC - MSC;
    private static final double ΔMCSQ = ΔMC * ΔMC;
    private static final double C2 = 1.151;

    /* Tables 4, 5, 6, 7 */
    static final class Coefficients {

      final double c1, csl1, csl2, d, b, g, gL, e, eH, γ, σ;

      /* Site amplification */
      final double AmSCI, s2, s3, s4;
      final Map<SiteClass, Double> fsr;

      /* Unused or constant: c2, eV, sigma, tau */

      Coefficients(Imt imt, CoefficientContainer cc) {

        Map<String, Double> coeffs = cc.get(imt);
        c1 = coeffs.get("c1");
        csl1 = coeffs.get("cSL1");
        csl2 = coeffs.get("cSL2");
        d = coeffs.get("d");
        b = coeffs.get("b");
        g = coeffs.get("g");
        gL = coeffs.get("gL");
        e = coeffs.get("e");
        eH = coeffs.get("eH");
        γ = coeffs.get("gamma");
        σ = coeffs.get("sigmaT");

        AmSCI = coeffs.get("AmSCI");
        s2 = coeffs.get("S2");
        s3 = coeffs.get("S3");
        s4 = coeffs.get("S4");

        fsr = Maps.immutableEnumMap(ImmutableMap.of(
            SiteClass.I, coeffs.get("FsrI"),
            SiteClass.II, coeffs.get("FsrII"),
            SiteClass.III, coeffs.get("FsrIII"),
            SiteClass.IV, coeffs.get("FsrIV")));
      }
    }

    private final Coefficients c;
    private final Map<SiteClass, Double> aNmax;

    Slab(Imt imt) {
      super(imt);
      c = new Coefficients(imt, COEFFS_SLAB);
      aNmax = Maps.immutableEnumMap(ImmutableMap.of(
          SiteClass.I, c.AmSCI,
          SiteClass.II, c.AmSCI * exp(c.s2),
          SiteClass.III, c.AmSCI * exp(c.s3),
          SiteClass.IV, c.AmSCI * exp(c.s4)));
    }

    @Override
    final double saRock(final GmmInput in) {

      double Mw = in.Mw;
      double rRup = in.rRup;
      double zTop = in.zTop;

      /* Source term; equation 1 */
      double fm = c.b * zTop;
      if (Mw > MC) {
        fm += c.csl1 * MC + c.csl2 * ΔMCSQ + c.d * (Mw - MC);
      } else {
        double Δmsc = Mw - MSC;
        fm += c.csl1 * Mw + c.csl2 * Δmsc * Δmsc;
      }

      /* Geometric spreading distance; equation 3 */
      double r = rRup + exp(c.c1 + C2 * min(Mw, MC));

      /* Geometric attenuation terms; equation 2a */
      double fg = c.g * log(r) + c.gL * log(rRup + 200.0);

      /* Anelastic atten. term; volc. ignored (eV * rV) equations 2a, 5 */
      double fe = c.e * rRup + (zTop < 50.0 ? 0.0 : c.eH * (0.02 * zTop - 1.0));

      return fm + fg + fe + c.γ;
    }

    @Override
    final double elasticSiteAmpRatio(final SiteClass siteClass, double zTop) {
      return aNmax.get(siteClass);
    }

    @Override
    double smoothingFactor(SiteClass siteClass) {
      return c.fsr.get(siteClass);
    }

    @Override
    final double sigma() {
      return c.σ;
    }
  }

  static class SiteAmp {

    final Map<SiteClass, Double> aMax;
    final Map<SiteClass, Double> sRc;

    SiteAmp(Imt imt, CoefficientContainer cc) {
      Map<String, Double> coeffs = cc.get(imt);

      aMax = Maps.immutableEnumMap(ImmutableMap.of(
          SiteClass.I, coeffs.get("AmaxI"),
          SiteClass.II, coeffs.get("AmaxII"),
          SiteClass.III, coeffs.get("AmaxIII"),
          SiteClass.IV, coeffs.get("AmaxIV")));

      sRc = Maps.immutableEnumMap(ImmutableMap.of(
          SiteClass.I, coeffs.get("SrcI"),
          SiteClass.II, coeffs.get("SrcII"),
          SiteClass.III, coeffs.get("SrcIII"),
          SiteClass.IV, coeffs.get("SrcIV")));
    }
  }

  static double siteTerm(
      double aNmax,
      double aMax,
      double sRC,
      double Imf,
      double fSR,
      double saRock) {

    double sReff = saRock * Imf;
    double sReffC = sRC * Imf;
    double sNC = nonlinCrossover(aNmax, aMax, sReffC);
    double sMR = sReff * fSR * sNC / sReffC;
    return nonlinAmpRatio(aNmax, aMax, sMR, sReffC);
  }

  private static double nonlinAmpRatio(double aNmax, double aMax, double sMR, double sReffC) {
    return log(aNmax) - log(aMax) * lnSqββ(sMR) / lnSqββ(sReffC);
  }

  private static double lnSqβ(double x) {
    return log(x * x + β);
  }

  private static double lnSqββ(double x) {
    return lnSqβ(x) - lnβ;
  }

  /* α = 2.0 exponent is handled via in-place multiplication */
  private static final double β = 0.6;
  private static final double lnβ = log(β);

  /* Identical in all models. */
  private static double nonlinCrossover(double aNmax, double aMax, double sReffC) {
    /* Zhao, Ziang, et al. (2016); slab; equation 14. */
    double sF = aNmax / aMax;
    double t = exp((log(aNmax) * log(sReffC * sReffC + β) - log(sF) * lnβ) / log(aMax));
    /*
     * For some short periods, exp(t) - β < 0 ==> NaN. These are cases where
     * aNmax and aMax are below 1.25, which are circumstances Zhao has special
     * cased to use approximate functional forms defined in Xhao, Hu et al.
     * (2015), but these are difficult to follow (e.g. θ can be any
     * "arbitrarily large" number). Zhao, Hu et al. also state that for Table 5,
     * those periods not listed for each site class do not require nonlinear
     * site terms, but this listing is inconsistent with the fSR tables in each
     * implementation.
     * 
     * For now, we are preventing the expression below from falling below 0,
     * which almost certainly incorrect.
     */
    return (t < β) ? 0.0 : sqrt(t - β);
  }

  static Range<SiteClass> siteRange(double vs30) {
    if (vs30 >= SiteClass.I.vs30) {
      return Range.singleton(SiteClass.I);
    } else if (vs30 >= SiteClass.II.vs30) {
      return Range.closedOpen(SiteClass.I, SiteClass.II);
    } else if (vs30 >= SiteClass.III.vs30) {
      return Range.closedOpen(SiteClass.II, SiteClass.III);
    } else if (vs30 >= SiteClass.IV.vs30) {
      return Range.closedOpen(SiteClass.III, SiteClass.IV);
    } else {
      return Range.singleton(SiteClass.IV);
    }
  }

  static enum SiteClass {
    I(760.0, 0.91),
    II(450.0, 1.023),
    III(250.0, 1.034),
    IV(150.0, 0.737);

    final double vs30;
    final double impedance;

    private SiteClass(double vs30, double impedance) {
      this.vs30 = vs30;
      this.impedance = impedance;
    }
  }

}
