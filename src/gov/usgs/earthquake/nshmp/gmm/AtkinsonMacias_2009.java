package gov.usgs.earthquake.nshmp.gmm;

import static gov.usgs.earthquake.nshmp.gmm.GmmInput.Field.MW;
import static gov.usgs.earthquake.nshmp.gmm.GmmInput.Field.RRUP;
import static gov.usgs.earthquake.nshmp.gmm.GmmInput.Field.VS30;
import static gov.usgs.earthquake.nshmp.gmm.GmmUtils.BASE_10_TO_E;
import static gov.usgs.earthquake.nshmp.gmm.GmmUtils.LN_G_CM_TO_M;
import static gov.usgs.earthquake.nshmp.gmm.Imt.PGA;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA0P01;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA0P02;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA0P03;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA0P05;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA0P075;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA0P1;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA0P15;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA0P2;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA0P25;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA0P3;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA1P0;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA1P5;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA2P0;
import static gov.usgs.earthquake.nshmp.util.Maths.hypot;
import static java.lang.Math.log10;

import java.util.Map;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Range;

import gov.usgs.earthquake.nshmp.gmm.GmmInput.Constraints;

/**
 * Implementation of the subduction interface ground motion model by Atkinson &
 * Macias (2009). This implementation matches that used in the 2014 USGS NSHMP.
 *
 * <p><b>Note:</b> Direct instantiation of {@code GroundMotionModel}s is
 * prohibited. Use {@link Gmm#instance(Imt)} to retrieve an instance for a
 * desired {@link Imt}.
 *
 * <p><b>Implementation notes:</b><ul>
 * 
 * <li>NSHM fortran implementation converts 0.13Hz to 7.7s; this implementation
 * uses 7.5s instead.</li>
 * 
 * <li>Model uses a magnitude dependent depth term and so does not impose 20km
 * hypocentral depth as other subduction interface models do.</li>
 * 
 * <li>Support for spectral period 0.01s is provided using the same coefficients
 * as PGA.</li>
 * 
 * <li>Support for spectral periods 0.02s, 0.03s, 0.075s, 0.15s, 0.25s, and 1.5s
 * is provided via interpolation of ground motion and sigma of adjacent periods
 * for which there are coefficients.</li></ul>
 *
 * <p><b>Reference:</b> Atkinson, G.M. and Macias, D.M., 2009, Predicted ground
 * motions for great interface earthquakes in the Cascadia subduction zone:
 * Bulletin of the Seismological Society of America, v. 99, p. 1552-1578.
 *
 * <p><b>doi:</b><a href="http://dx.doi.org/10.1785/0120080147" target="_top">
 * 10.1785/0120080147</a>
 *
 * <p><b>Component:</b> geometric mean of two horizontal components
 *
 * @author Peter Powers
 * @see Gmm#AM_09_INTERFACE
 * @see Gmm#AM_09_INTERFACE_BASIN
 */
public class AtkinsonMacias_2009 implements GroundMotionModel {

  /*
   * TODO 0.75s interpolated period coefficients added that should be removed if
   * a viable on-the-fly interpolation algorithm is added.
   */
  static final String NAME = "Atkinson & Macias (2009) : Interface";

  static final Constraints CONSTRAINTS = Constraints.builder()
      .set(MW, Range.closed(5.0, 9.5))
      .set(RRUP, Range.closed(0.0, 1000.0))
      .set(VS30, Range.closed(150.0, 1500.0))
      .build();

  static final CoefficientContainer COEFFS = new CoefficientContainer("AM09.csv");

  private static final double VS30_REF = 760.0;

  private static final Map<Imt, Range<Imt>> INTERPOLATED_IMTS = Maps.immutableEnumMap(
      ImmutableMap.<Imt, Range<Imt>> builder()
          .put(SA0P02, Range.closed(SA0P01, SA0P05))
          .put(SA0P03, Range.closed(SA0P01, SA0P05))
          .put(SA0P075, Range.closed(SA0P05, SA0P1))
          .put(SA0P15, Range.closed(SA0P1, SA0P2))
          .put(SA0P25, Range.closed(SA0P2, SA0P3))
          .put(SA1P5, Range.closed(SA1P0, SA2P0))
          .build());

  private static final class Coefficients {

    final Imt imt;
    final double c0, c1, c2, c3, c4, σ;

    Coefficients(Imt imt, CoefficientContainer cc) {
      this.imt = imt;
      Map<String, Double> coeffs = cc.get(imt);
      c0 = coeffs.get("c0");
      c1 = coeffs.get("c1");
      c2 = coeffs.get("c2");
      c3 = coeffs.get("c3");
      c4 = coeffs.get("c4");
      σ = coeffs.get("sig");
    }
  }

  private final Coefficients coeffs;
  private final Coefficients coeffsPGA;
  private final BooreAtkinson_2008 siteAmp;
  private final CampbellBozorgnia_2014 cb14;

  // interpolatedGmm = null if !interpolated
  private final boolean interpolated;
  private final GroundMotionModel interpolatedGmm;

  AtkinsonMacias_2009(final Imt imt) {
    this(imt, Gmm.AM_09_INTERFACE);
  }

  AtkinsonMacias_2009(final Imt imt, Gmm subtype) {
    coeffs = new Coefficients(imt, COEFFS);
    coeffsPGA = new Coefficients(PGA, COEFFS);
    siteAmp = new BooreAtkinson_2008(imt);
    cb14 = new CampbellBozorgnia_2014(imt);
    interpolated = INTERPOLATED_IMTS.containsKey(imt);
    interpolatedGmm = interpolated
        ? new InterpolatedGmm(subtype, imt, INTERPOLATED_IMTS.get(imt))
        : null;
  }

  @Override
  public final ScalarGroundMotion calc(final GmmInput in) {
    if (interpolated) {
      return interpolatedGmm.calc(in);
    }

    double σ = coeffs.σ * BASE_10_TO_E;
    double μRef = calcMean(coeffs, in);
    double μPga = calcMean(coeffsPGA, in);
    double site = siteAmp.siteAmp(μPga, in.vs30);
    double μAm = μRef + site;

    /* Add (possibly depth-tapered) CB14 deep basin term. */
    if (deepBasinEffect()) {
      μAm += cb14.deepBasinScaling(in.z2p5);
    }

    return DefaultScalarGroundMotion.create(μAm, σ);
  }

  boolean deepBasinEffect() {
    return false;
  }

  private static final double calcMean(final Coefficients c, final GmmInput in) {
    double Mw = in.Mw;
    double h = (Mw * Mw) - (3.1 * Mw) - 14.55;
    double dM = Mw - 8.0;
    double gnd = c.c0 + (c.c3 * dM) + (c.c4 * dM * dM);
    double r = hypot(in.rRup, h);
    gnd += c.c1 * log10(r) + c.c2 * r;
    return gnd * BASE_10_TO_E - LN_G_CM_TO_M;
  }

  /*
   * Developer note: In most GMMs, subtype constructors, if present, need only
   * the IMT argument to initialize their parent. To support several
   * interpolated spectral periods, the parent also needs to know the specific
   * subtype Gmm identifier in order to obtain concrete instances of the
   * bounding spectral periods.
   */

  static final class Basin extends AtkinsonMacias_2009 {
    static final String NAME = AtkinsonMacias_2009.NAME + " : Basin";

    Basin(Imt imt) {
      super(imt, Gmm.AM_09_INTERFACE_BASIN);
    }

    @Override
    final boolean deepBasinEffect() {
      return true;
    }
  }
}
