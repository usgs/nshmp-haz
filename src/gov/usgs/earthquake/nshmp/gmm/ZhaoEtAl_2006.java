package gov.usgs.earthquake.nshmp.gmm;

import static gov.usgs.earthquake.nshmp.gmm.GmmInput.Field.MW;
import static gov.usgs.earthquake.nshmp.gmm.GmmInput.Field.RRUP;
import static gov.usgs.earthquake.nshmp.gmm.GmmInput.Field.VS30;
import static gov.usgs.earthquake.nshmp.gmm.GmmInput.Field.ZTOP;
import static gov.usgs.earthquake.nshmp.gmm.GmmUtils.LN_G_CM_TO_M;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA0P01;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA0P02;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA0P03;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA0P05;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA0P075;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA0P1;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA10P0;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA5P0;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA7P5;
import static java.lang.Math.exp;
import static java.lang.Math.log;
import static java.lang.Math.min;
import static java.lang.Math.sqrt;

import java.util.Map;
import java.util.Set;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Range;
import com.google.common.collect.Sets;

import gov.usgs.earthquake.nshmp.data.Interpolator;
import gov.usgs.earthquake.nshmp.eq.Earthquakes;
import gov.usgs.earthquake.nshmp.gmm.GmmInput.Constraints;
import gov.usgs.earthquake.nshmp.gmm.ZhaoEtAl_2016.SiteClass;

/**
 * Abstract implementation of the subduction ground motion model by Zhao et al.
 * (2006). This implementation matches that used in the USGS NSHM.
 *
 * <p>This model supports both slab and interface type events. In the 2008
 * NSHMP, the 'interface' form is used with the Cascadia subduction zone models
 * and the 'slab' form is used with gridded 'deep' events in northern California
 * and the Pacific Northwest.
 *
 * <p><b>Note:</b> Direct instantiation of {@code GroundMotionModel}s is
 * prohibited. Use {@link Gmm#instance(Imt)} to retrieve an instance for a
 * desired {@link Imt}.
 *
 * <p><b>Implementation notes:</b><ul>
 * 
 * <li>When used for interface events, sigma is computed using the generic value
 * of tau, rather than the interface specific value (see inline comments for
 * more information).<li>
 * 
 * <li>Hypocentral depths for interface events are fixed at 20km.</li>
 * 
 * <li>Hypocentral depths for slab events are set to {@code min(zTop, 125)};
 * minimum rupture distance (rRup) is 1.0 km.</li>
 * 
 * <li>Support for spectral period 0.75s is provided using interpolated
 * coefficients ported from USGS legacy Fortran implementations.</li>
 * 
 * <li>Support for spectral period 0.01s is provided using the same coefficients
 * as PGA.</li>
 * 
 * <li>Support for spectral periods 0.02s, 0.03s, and 0.075s is provided via
 * interpolation of ground motion and sigma of adjacent periods for which there
 * are coefficients.</li>
 * 
 * <li>Support for spectral periods 7.5s and 10.0s is provided via extrapolation
 * using the 2014 and 2018 NSHM GMM logic tree reference values for slab and
 * interface source types, scaled by the ratio at 5.0s.</li></ul>
 *
 * <p><b>Reference:</b> Zhao, J.X., Zhang, J., Asano, A., Ohno, Y., Oouchi, T.,
 * Takahashi, T., Ogawa, H., Irikura, K., Thio, H.K., Somerville, P.G.,
 * Fukushima, Y., and Fukushima, Y., 2006, Attenuation relations of strong
 * ground motion in Japan using site classification based on predominant period:
 * Bulletin of the Seismological Society of America, v. 96, p. 898–913.
 *
 * <p><b>doi:</b> <a href="http://dx.doi.org/10.1785/0120050122">
 * 10.1785/0120050122</a>
 *
 * <p><b>Component:</b> Geometric mean of two horizontal components
 *
 * @author Peter Powers
 * @see Gmm#ZHAO_06_INTERFACE
 * @see Gmm#ZHAO_06_SLAB
 * @see Gmm#ZHAO_06_INTERFACE_BASIN_AMP
 * @see Gmm#ZHAO_06_SLAB_BASIN_AMP
 */
public abstract class ZhaoEtAl_2006 implements GroundMotionModel {

  static final String NAME = "Zhao et al. (2006)";

  // TODO will probably want to have constraints per-implementation
  static final Constraints CONSTRAINTS = Constraints.builder()
      .set(MW, Range.closed(5.0, 9.5))
      .set(RRUP, Range.closed(0.0, 1000.0))
      .set(ZTOP, Earthquakes.SLAB_DEPTH_RANGE)
      .set(VS30, Range.closed(150.0, 1000.0))
      .build();

  // TODO Zhao06.csv contains higher precision coefficents than
  // supplied in publication; consider revising d, Sr, Si, Ssl

  static final CoefficientContainer COEFFS = new CoefficientContainer("Zhao06.csv");

  private static final double HC = 15.0;
  private static final double MC_S = 6.5;
  private static final double MC_I = 6.3;
  private static final double MAX_SLAB_DEPTH = 125.0;
  private static final double INTERFACE_DEPTH = 20.0;
  private static final double VS30_ROCK = 760.0;

  private static final Map<Imt, Range<Imt>> INTERPOLATED_IMTS = Maps.immutableEnumMap(
      ImmutableMap.of(
          SA0P02, Range.closed(SA0P01, SA0P05),
          SA0P03, Range.closed(SA0P01, SA0P05),
          SA0P075, Range.closed(SA0P05, SA0P1)));

  private static final Set<Imt> EXTRAPOLATED_IMTS = Sets.immutableEnumSet(
      SA7P5,
      SA10P0);
  
  /*
   * Map Zhao subtype to reference Gmms for extrapolation. This is not
   * implemented as a static map due to circular Gmm references.
   */
  @SuppressWarnings("incomplete-switch")
  private static Map<Gmm, Double> extrapolationRefs(Gmm gmm) {
    switch (gmm) {
      case ZHAO_06_SLAB:
        return ImmutableMap.of(
            Gmm.BCHYDRO_12_SLAB, 1.0);
      case ZHAO_06_SLAB_BASIN_AMP:
        return ImmutableMap.of(
            Gmm.BCHYDRO_12_SLAB_BASIN_AMP, 1.0);
      case ZHAO_06_INTERFACE:
        return ImmutableMap.of(
            Gmm.BCHYDRO_12_INTERFACE, 0.5,
            Gmm.AM_09_INTERFACE, 0.5);
      case ZHAO_06_INTERFACE_BASIN_AMP:
        return ImmutableMap.of(
            Gmm.BCHYDRO_12_INTERFACE_BASIN_AMP, 0.5,
            Gmm.AM_09_INTERFACE_BASIN_AMP, 0.5);
        default:
          throw new IllegalArgumentException();
    }
  }

  private static final class Coefficients {

    final double a, b, c, d, e, Si, Ss, Ssl, C1, C2, C3, C4, σ, τ, τS, Ps, Qi, Qs, Wi, Ws;

    // unused
    // final double Sr, tauI;

    Coefficients(Imt imt, CoefficientContainer cc) {
      Map<String, Double> coeffs = cc.get(imt);
      a = coeffs.get("a");
      b = coeffs.get("b");
      c = coeffs.get("c");
      d = coeffs.get("d");
      e = coeffs.get("e");
      Si = coeffs.get("Si");
      Ss = coeffs.get("Ss");
      Ssl = coeffs.get("Ssl");
      C1 = coeffs.get("C1");
      C2 = coeffs.get("C2");
      C3 = coeffs.get("C3");
      C4 = coeffs.get("C4");
      σ = coeffs.get("sigma");
      τ = coeffs.get("tau");
      τS = coeffs.get("tauS");
      Ps = coeffs.get("Ps");
      Qi = coeffs.get("Qi");
      Qs = coeffs.get("Qs");
      Wi = coeffs.get("Wi");
      Ws = coeffs.get("Ws");
    }
  }

  private final Coefficients coeffs;
  private final CampbellBozorgnia_2014.BasinAmp cb14basinAmp;

  /* gmms = null if !flag */
  private final boolean interpolated;
  private final GroundMotionModel interpolatedGmm;
  private final boolean extrapolated;
  private final GroundMotionModel extrapolatedGmm;

  ZhaoEtAl_2006(final Imt imt, Gmm subtype) {
    coeffs = new Coefficients(imt, COEFFS);
    cb14basinAmp = new CampbellBozorgnia_2014.BasinAmp(imt);
    interpolated = INTERPOLATED_IMTS.containsKey(imt);
    interpolatedGmm = interpolated
        ? new InterpolatedGmm(subtype, imt, INTERPOLATED_IMTS.get(imt))
        : null;
    extrapolated = EXTRAPOLATED_IMTS.contains(imt);
    extrapolatedGmm = extrapolated
        ? new ExtrapolatedGmm(subtype, imt, SA5P0, extrapolationRefs(subtype))
        : null;
  }

  @Override
  public final ScalarGroundMotion calc(GmmInput in) {
    if (interpolated) {
      return interpolatedGmm.calc(in);
    }

    if (extrapolated) {
      return extrapolatedGmm.calc(in);
    }

    double σ = calcStdDev(coeffs, isSlab());

    if (basinEffect()) {
      // Possibly use basin/site term from
      // CB14 with local rock reference.
      double fSite = siteTermStep(coeffs, VS30_ROCK);
      double μRock = calcMean(coeffs, isSlab(), fSite, in);
      double cbBasin = cb14basinAmp.basinDelta(in, VS30_ROCK);
      double μ = μRock + cbBasin;
      return DefaultScalarGroundMotion.create(μ, σ);
    }

    double fSite = siteTermStep(coeffs, in.vs30);
    double μ = calcMean(coeffs, isSlab(), fSite, in);
    return DefaultScalarGroundMotion.create(μ, σ);
  }

  abstract boolean isSlab();

  abstract boolean basinEffect();

  private static final double calcMean(
      final Coefficients c,
      final boolean slab,
      final double fSite,
      final GmmInput in) {

    double Mw = in.Mw;
    double rRup = Math.max(in.rRup, 1.0); // avoid ln(0) below
    double zTop = slab ? min(in.zTop, MAX_SLAB_DEPTH) : INTERFACE_DEPTH;

    double hfac = (zTop < HC) ? 0.0 : zTop - HC;
    double m2 = Mw - (slab ? MC_S : MC_I);
    double afac, xmcor;

    if (slab) {
      afac = c.Ssl * log(rRup) + c.Ss;
      xmcor = c.Ps * m2 + c.Qs * m2 * m2 + c.Ws;
    } else {
      afac = c.Si;
      xmcor = c.Qi * m2 * m2 + c.Wi;
    }

    double r = rRup + c.c * exp(c.d * Mw);
    return c.a * Mw + c.b * rRup - Math.log(r) +
        c.e * hfac + afac + fSite + xmcor -
        LN_G_CM_TO_M;
  }

  private static final double calcStdDev(final Coefficients c, final boolean slab) {
    /*
     * Frankel email may 22 2007: use sigt from table 5. Not the reduced-tau
     * sigma associated with mag correction seen in table 6. Zhao says "truth"
     * is somewhere in between.
     */
    return sqrt(c.σ * c.σ + (slab ? c.τS * c.τS : c.τ * c.τ));
  }

  private static final double siteTermStep(final Coefficients c, double vs30) {
    return (vs30 >= 600.0) ? c.C1 : (vs30 >= 300.0) ? c.C2 : c.C3;
  }

  private static final double siteTermSmooth(final Coefficients c, double vs30) {
    Range<SiteClass> siteRange = ZhaoEtAl_2016.siteRange(vs30);
    SiteClass lower = siteRange.upperEndpoint();
    SiteClass upper = siteRange.lowerEndpoint();
    if (lower == upper) {
      return siteCoeff(c, lower);
    }
    double fsLower = siteCoeff(c, lower);
    double fsUpper = siteCoeff(c, upper);
    return Interpolator.findY(
        lower.vs30, fsLower,
        upper.vs30, fsUpper,
        vs30);
  }

  private static double siteCoeff(final Coefficients c, final SiteClass siteClass) {
    switch (siteClass) {
      case I:
        return c.C1;
      case II:
        return c.C2;
      case III:
        return c.C3;
      case IV:
        return c.C4;
      default:
        throw new IllegalStateException();
    }
  }

  /*
   * Developer note: In most GMMs, subtype constructors, if present, need only
   * the IMT argument to initialize their parent. To support several
   * interpolated spectral periods, the parent also needs to know the specific
   * subtype Gmm identifier in order to obtain concrete instances of the
   * bounding spectral periods. In the case of cascading subtypes, multiple
   * constructors are needed, c(imt) and c(imt, subtype), to support
   * Gmm.instance and additional sub-subtypes, respectively.
   */

  static class Interface extends ZhaoEtAl_2006 {
    static final String NAME = ZhaoEtAl_2006.NAME + " : Interface";

    Interface(Imt imt) {
      super(imt, Gmm.ZHAO_06_INTERFACE);
    }

    protected Interface(Imt imt, Gmm subtype) {
      super(imt, subtype);
    }

    @Override
    final boolean isSlab() {
      return false;
    }

    @Override
    boolean basinEffect() {
      return false;
    }
  }

  static final class BasinInterface extends Interface {
    static final String NAME = Interface.NAME + " : Basin Amp";

    BasinInterface(Imt imt) {
      super(imt, Gmm.ZHAO_06_INTERFACE_BASIN_AMP);
    }

    @Override
    final boolean basinEffect() {
      return true;
    }
  }

  static class Slab extends ZhaoEtAl_2006 {
    static final String NAME = ZhaoEtAl_2006.NAME + " : Slab";

    Slab(Imt imt) {
      super(imt, Gmm.ZHAO_06_SLAB);
    }

    protected Slab(Imt imt, Gmm subtype) {
      super(imt, subtype);
    }

    @Override
    final boolean isSlab() {
      return true;
    }

    @Override
    boolean basinEffect() {
      return false;
    }
  }

  static final class BasinSlab extends Slab {
    static final String NAME = Slab.NAME + " : Basin Amp";

    BasinSlab(Imt imt) {
      super(imt, Gmm.ZHAO_06_SLAB_BASIN_AMP);
    }

    @Override
    final boolean basinEffect() {
      return true;
    }
  }
}
