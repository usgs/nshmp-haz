package gov.usgs.earthquake.nshmp.gmm;

import static gov.usgs.earthquake.nshmp.gmm.GmmInput.Field.MW;
import static gov.usgs.earthquake.nshmp.gmm.GmmInput.Field.RRUP;
import static gov.usgs.earthquake.nshmp.gmm.GmmInput.Field.VS30;
import static gov.usgs.earthquake.nshmp.gmm.GmmInput.Field.ZTOP;
import static gov.usgs.earthquake.nshmp.gmm.Imt.PGA;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA0P02;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA0P03;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA0P05;
import static java.lang.Math.exp;
import static java.lang.Math.log;
import static java.lang.Math.min;
import static java.lang.Math.pow;

import java.util.Map;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Range;

import gov.usgs.earthquake.nshmp.eq.Earthquakes;
import gov.usgs.earthquake.nshmp.gmm.GmmInput.Constraints;

/**
 * Abstract implementation of the subduction ground motion model created for BC
 * Hydro, Canada, by Addo, Abrahamson, & Youngs (2012). An update to this
 * implementation is documented in Abrahamson, Gregor, & Addo (2016). This model
 * supports both slab and interface type events.
 *
 * <p><b>Note:</b> Direct instantiation of {@code GroundMotionModel}s is
 * prohibited. Use {@link Gmm#instance(Imt)} to retrieve an instance for a
 * desired {@link Imt}.
 * 
 * <p><b>Changes due to Abrahamson et al. (2016) update:</b><ul>
 * 
 * <li>Added coefficients for 0.02s that are identical to PGA. 0.02s ground
 * motions had previously had been computed via interpolation between 0.01s and
 * 0.05s.</li>
 * 
 * <li>Slab {@code zTop} adjusted from 125 km to 120 km.</li>
 * 
 * <li>Changed interpolation of the {@code ΔC1} magnitude-scaling break term to
 * log spectral periods and linear values; prior implementation was linear;
 * change affects the following spectral periods: 0.4s, 0.6s (disabled), 0.75s,
 * 1.5s, and 2.5s wherein the {@code ΔC1} value has decreased by 0.01</li></ul>
 *
 * <p><b>Implementation notes:</b><ul>
 * 
 * <li>Treats all sites as forearc; no backarc term is considered.</li>
 * 
 * <li>{@code zTop} is interpreted as hypocentral depth and is only used for
 * slab events; it is limited to 120 km, consistent with other subduction
 * models.</li>
 * 
 * <li>Only the middle branch of the {@code ΔC1} magnitude-scaling break term
 * for interface events is considered; the {@code ΔC1} term for slab events is
 * fixed at -0.3.</li>
 * 
 * <li>Support for spectral period 0.01s is provided using the same coefficients
 * as bounding periods PGA and 0.02s.</li>
 * 
 * <li>Support for spectral periods 0.03s is provided via interpolation of
 * ground motion and sigma of adjacent periods.</li></ul>
 *
 * <p><b>Reference:</b> Abrahamson, N., Gregor, N., and Addo, K., 2016, BC Hydro
 * ground motion prediction equations for subduction earthquakes: Earthquake
 * Spectra, v. 32, n. 1, p. 23-44.
 * 
 * <p><b>doi:</b> <a href="http://dx.doi.org/10.1193/051712EQS188MR"
 * target="_top">10.1193/051712EQS188MR</a>
 * 
 * <p><b>Reference:</b> Addo, K., Abrahamson, N., and Youngs, R., (BC Hydro),
 * 2012, Probabilistic seismic hazard analysis (PSHA) model—Ground motion
 * characterization (GMC) model: Report E658, v. 3, November.
 *
 * <p><b>Component:</b> Geometric mean of two horizontal components
 *
 * @author Peter Powers
 * @see Gmm#BCHYDRO_12_INTERFACE
 * @see Gmm#BCHYDRO_12_SLAB
 * @see Gmm#BCHYDRO_12_INTERFACE_BASIN_AMP
 * @see Gmm#BCHYDRO_12_SLAB_BASIN_AMP
 */
public abstract class BcHydro_2012 implements GroundMotionModel {

  static final String NAME = "BC Hydro (2012)";

  // TODO will probably want to have constraints per-implementation (e.g. slab
  // vs interface depth limits)
  static final Constraints CONSTRAINTS = Constraints.builder()
      .set(MW, Range.closed(5.0, 9.5))
      .set(RRUP, Range.closed(0.0, 1000.0))
      .set(ZTOP, Earthquakes.SLAB_DEPTH_RANGE)
      .set(VS30, Range.closed(150.0, 1000.0))
      .build();

  static final CoefficientContainer COEFFS = new CoefficientContainer("BCHydro12.csv");

  private static final double C1 = 7.8;
  private static final double T3 = 0.1;
  private static final double T4 = 0.9;
  private static final double T5 = 0.0;
  private static final double T9 = 0.4;
  private static final double C4 = 10.0;
  private static final double C = 1.88;
  private static final double N = 1.18;
  private static final double VSS_MAX = 1000.0;
  private static final double SIGMA = 0.74;
  private static final double ΔC1_SLAB = -0.3;
  private static final double VS30_ROCK = 1000.0;

  private static final Map<Imt, Range<Imt>> INTERPOLATED_IMTS = Maps.immutableEnumMap(
      ImmutableMap.of(SA0P03, Range.closed(SA0P02, SA0P05)));

  private static final class Coefficients {

    final Imt imt;
    final double vlin, b, θ1, θ2, θ6, θ10, θ11, θ12, θ13, θ14, ΔC1mid;

    // not currently used
    // final double t7, t8, t15, t16, dC1lo, dC1hi;

    Coefficients(Imt imt, CoefficientContainer cc) {
      this.imt = imt;
      Map<String, Double> coeffs = cc.get(imt);
      vlin = coeffs.get("vlin");
      b = coeffs.get("b");
      θ1 = coeffs.get("t1");
      θ2 = coeffs.get("t2");
      θ6 = coeffs.get("t6");
      θ10 = coeffs.get("t10");
      θ11 = coeffs.get("t11");
      θ12 = coeffs.get("t12");
      θ13 = coeffs.get("t13");
      θ14 = coeffs.get("t14");
      ΔC1mid = coeffs.get("dC1mid");
    }
  }

  private final Coefficients coeffs;
  private final Coefficients coeffsPGA;
  private final CampbellBozorgnia_2014.BasinAmp cb14basinAmp;

  // interpolatedGmm = null if !interpolated
  private final boolean interpolated;
  private final GroundMotionModel interpolatedGmm;

  BcHydro_2012(final Imt imt, Gmm subtype) {
    coeffs = new Coefficients(imt, COEFFS);
    coeffsPGA = new Coefficients(PGA, COEFFS);
    cb14basinAmp = new CampbellBozorgnia_2014.BasinAmp(imt);
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

    double pgaRock = exp(calcMean(coeffsPGA, isSlab(), 0.0, in.Mw, in.rRup, in.zTop, VS30_ROCK));
    double μAsk = calcMean(coeffs, isSlab(), pgaRock, in.Mw, in.rRup, in.zTop, in.vs30);

    if (!Double.isNaN(in.z2p5) && basinEffect()) {
      
      double μRock = calcMean(coeffs, isSlab(), pgaRock, in.Mw, in.rRup, in.zTop, VS30_ROCK);
      double cbBasin = cb14basinAmp.basinDelta(in, VS30_ROCK);
      double μCb = μRock + cbBasin;

      if ((μCb < μAsk) || (coeffs.imt.ordinal() < Imt.SA0P75.ordinal())) {
        return DefaultScalarGroundMotion.create(μAsk, SIGMA);
      } else if (coeffs.imt.equals(Imt.SA0P75)) {
        return DefaultScalarGroundMotion.create((μAsk + μCb) * 0.5, SIGMA);
      }
      return DefaultScalarGroundMotion.create(μCb, SIGMA);
    }
    return DefaultScalarGroundMotion.create(μAsk, SIGMA);
  }

  abstract boolean isSlab();

  abstract boolean basinEffect();

  private static final double calcMean(
      final Coefficients c,
      final boolean slab,
      final double pgaRock,
      final double Mw,
      final double rRup,
      final double zTop,
      final double vs30) {

    double ΔC1 = (slab ? ΔC1_SLAB : c.ΔC1mid);
    double mCut = C1 + ΔC1;
    double t13m = c.θ13 * (10 - Mw) * (10 - Mw);
    double fMag = (Mw <= mCut ? T4 : T5) * (Mw - mCut) + t13m;

    // no depth term for interface events
    double fDepth = slab ? c.θ11 * (min(zTop, 120.0) - 60.0) : 0.0;

    double vsS = min(vs30, VSS_MAX);

    double fSite = c.θ12 * log(vsS / c.vlin);
    if (vs30 < c.vlin) { // whether or not we use pgaRock
      fSite += -c.b * log(pgaRock + C) + c.b * log(pgaRock + C * pow((vsS / c.vlin), N));
    } else {
      // for pgaRock loop, vs=1000 > vlinPGA=865
      fSite += c.b * N * log(vsS / c.vlin);
    }

    return c.θ1 + T4 * ΔC1 +
        (c.θ2 + (slab ? c.θ14 : 0.0) + T3 * (Mw - 7.8)) *
            log(rRup + C4 * exp((Mw - 6.0) * T9)) +
        c.θ6 * rRup + (slab ? c.θ10 : 0.0) + fMag +
        fDepth +
        // fterm + no fterm for forearc sites
        fSite;
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

  static class Interface extends BcHydro_2012 {
    static final String NAME = BcHydro_2012.NAME + " : Interface";

    Interface(Imt imt) {
      super(imt, Gmm.BCHYDRO_12_INTERFACE);
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
      super(imt, Gmm.BCHYDRO_12_INTERFACE_BASIN_AMP);
    }

    @Override
    final boolean basinEffect() {
      return true;
    }
  }

  static class Slab extends BcHydro_2012 {
    static final String NAME = BcHydro_2012.NAME + " : Slab";

    Slab(Imt imt) {
      super(imt, Gmm.BCHYDRO_12_SLAB);
    }

    Slab(Imt imt, Gmm subtype) {
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
      super(imt, Gmm.BCHYDRO_12_SLAB);
    }

    @Override
    final boolean basinEffect() {
      return true;
    }
  }
}
