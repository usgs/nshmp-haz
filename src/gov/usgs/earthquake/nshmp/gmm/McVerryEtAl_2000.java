package gov.usgs.earthquake.nshmp.gmm;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static gov.usgs.earthquake.nshmp.eq.TectonicSetting.ACTIVE_SHALLOW_CRUST;
import static gov.usgs.earthquake.nshmp.eq.TectonicSetting.SUBDUCTION_INTERFACE;
import static gov.usgs.earthquake.nshmp.eq.TectonicSetting.VOLCANIC;
import static gov.usgs.earthquake.nshmp.gmm.FaultStyle.NORMAL;
import static gov.usgs.earthquake.nshmp.gmm.FaultStyle.REVERSE;
import static gov.usgs.earthquake.nshmp.gmm.FaultStyle.REVERSE_OBLIQUE;
import static gov.usgs.earthquake.nshmp.gmm.FaultStyle.STRIKE_SLIP;
import static gov.usgs.earthquake.nshmp.gmm.GmmInput.Field.MW;
import static gov.usgs.earthquake.nshmp.gmm.GmmInput.Field.RAKE;
import static gov.usgs.earthquake.nshmp.gmm.GmmInput.Field.RRUP;
import static gov.usgs.earthquake.nshmp.gmm.GmmInput.Field.VS30;
import static gov.usgs.earthquake.nshmp.gmm.GmmInput.Field.ZHYP;
import static gov.usgs.earthquake.nshmp.gmm.Imt.PGA;
import static java.lang.Math.exp;
import static java.lang.Math.log;
import static java.lang.Math.sqrt;

import com.google.common.collect.Range;

import java.util.Map;

import gov.usgs.earthquake.nshmp.eq.TectonicSetting;
import gov.usgs.earthquake.nshmp.eq.fault.Faults;
import gov.usgs.earthquake.nshmp.gmm.GmmInput.Constraints;

/**
 * Abstract implementation of the ground motion model by McVerry et al. (2000).
 *
 * <p><b>Implementation details:</b><ul><li>McVerry proposes a hanging wall term
 * but it was not specifically modeled and is not implemented here.</li><li> New
 * Zealand uses site classes that do not strictly correspond to fixed ranges of
 * Vs30, in contrast with the US model; NZ soil site classes C and D consider
 * stratification and site-specific response period. This implementation uses
 * the following New Zealend site class to Vs30 values for convenience and
 * consistency with the majority of other ground motion models:<ul><li>Class A:
 * 1500 < Vs30</li><li>Class B: 360 < Vs30 ≤ 1500</li><li>Class C: 250 < Vs30 ≤
 * 360</li><li>Class D: 150 < Vs30 ≤ 250</li><li>Class E: s30 ≤ 150 (not
 * supported)</li></ul></li></ul>
 *
 * <p><b>Model applicability:</b> This needs work (TODO). Prior implementations
 * restricted distance to 400km, foacl depths to 100km, and Magnitudes between
 * 5.0 and 8.5. However the model supports a range of tectonic settings and
 * McVerry et al. (2006) restrict magnitude to 7.5 and distance to 400km for
 * curstal earthquakes, and restrict magnitudes to 8.0 and distances to 500km
 * for subduction earthquake.<p>
 *
 * <p><b>Reference:</b> McVerry, G.H., Zhao, J.X., Abrahamson, N.A., and
 * Somerville, P.G., 2000, Crustal and subduction zone attenuation realations
 * for New Zealand earthquakes: Proc 12th World conference on earthquake
 * engineering, Auckland, New Zealand, February, 2000.
 *
 * <p><b>Reference:</b> McVerry, G.H., Zhao, J.X., Abrahamson, N.A., and
 * Somerville, P.G., 2000, New Zealand acceleration response spectrum
 * attenuation relations for crustal and subduction zone earthquakes: Bulletin
 * of the New Zealand Society of Earthquake Engineering, v. 39, n. 4, p.
 * 1-58.
 *
 * <p><b>Component:</b> Model supports geometric mean or maximum of two
 * horizontal components; only concrete implementations of max-horizontal
 * component are provided at this time.
 *
 * @author Brendon A. Bradley
 * @author Peter Powers
 */
public abstract class McVerryEtAl_2000 implements GroundMotionModel {

  // TODO need hypocentral depth for subduction

  // NOTE: Changed rake cutoffs to be symmetric and conform with 2006 pub.
  // NOTE: updated NZ_SourceID to collapse SR RS keys

  static final String NAME = "McVerry et al. (2000)";

  // TODO will probably want to have constraints per-implementation
  // (e.g. zHyp only used by subduction)

  static final Constraints CONSTRAINTS = Constraints.builder()
      .set(MW, Range.closed(4.0, 8.0))
      .set(RRUP, Range.closed(0.0, 200.0))
      .set(ZHYP, Range.closed(0.0, 20.0))
      .set(RAKE, Faults.RAKE_RANGE)
      .set(VS30, Range.closed(150.0, 1500.0))
      .build();

  // geomean and max-horizontal coefficients
  static final CoefficientContainer COEFFS_GM = new CoefficientContainer("McVerry00_gm.csv");
  static final CoefficientContainer COEFFS_MH = new CoefficientContainer("McVerry00_mh.csv");

  private static final double C4AS = -0.144;
  private static final double C6AS = 0.17;
  private static final double C12Y = 1.414;
  private static final double C18Y = 1.7818;
  private static final double C19Y = 0.554;
  private static final double C32 = -0.2;

  private static final class Coefficients {

    // 'as' and 'y' suffixes indicate attribution to
    // Abrahamson & Silva or Youngs et al.

    final Imt imt;
    final double c1, c3as, c5, c8, c10as, c11, c13y, c15, c17, c20, c24, c29, c30as, c33as,
        c43, c46, σ6, σSlope, τ;

    Coefficients(Imt imt, CoefficientContainer cc) {
      this.imt = imt;
      Map<String, Double> coeffs = cc.get(imt);
      c1 = coeffs.get("c1");
      c3as = coeffs.get("c3as");
      c5 = coeffs.get("c5");
      c8 = coeffs.get("c8");
      c10as = coeffs.get("c10as");
      c11 = coeffs.get("c11");
      c13y = coeffs.get("c13y");
      c15 = coeffs.get("c15");
      c17 = coeffs.get("c17");
      c20 = coeffs.get("c20");
      c24 = coeffs.get("c24");
      c29 = coeffs.get("c29");
      c30as = coeffs.get("c30as");
      c33as = coeffs.get("c33as");
      c43 = coeffs.get("c43");
      c46 = coeffs.get("c46");
      σ6 = coeffs.get("sigma6");
      σSlope = coeffs.get("sigSlope");
      τ = coeffs.get("tau");
    }

    // pga'
    Coefficients(boolean geomean) {
      imt = PGA;
      if (geomean) {
        c1 = 0.07713;
        c3as = 0.0;
        c5 = -0.00898;
        c8 = -0.73728;
        c10as = 5.6;
        c11 = 8.08611;
        c13y = 0.0;
        c15 = -2.552;
        c17 = -2.49894;
        c20 = 0.0159;
        c24 = -0.43223;
        c29 = 0.3873;
        c30as = -0.23;
        c33as = 0.26;
        c43 = -0.31036;
        c46 = -0.0325;
        σ6 = 0.5099;
        σSlope = -0.0259;
        τ = 0.2469;
      } else {
        // max horizontal
        c1 = 0.1813;
        c3as = 0.0;
        c5 = -0.00846;
        c8 = -0.75519;
        c10as = 5.6;
        c11 = 8.10697;
        c13y = 0.0;
        c15 = -2.552;
        c17 = -2.48795;
        c20 = 0.01622;
        c24 = -0.41369;
        c29 = 0.44307;
        c30as = -0.23;
        c33as = 0.26;
        c43 = -0.29648;
        c46 = -0.03301;
        σ6 = 0.5035;
        σSlope = -0.0635;
        τ = 0.2598;
      }
    }
  }

  private final Coefficients coeffs;
  private final Coefficients coeffsPGA;
  private final Coefficients coeffsPGAprime;

  McVerryEtAl_2000(Imt imt) {
    coeffs = new Coefficients(imt, isGeomean() ? COEFFS_GM : COEFFS_MH);
    coeffsPGA = new Coefficients(PGA, isGeomean() ? COEFFS_GM : COEFFS_MH);
    coeffsPGAprime = new Coefficients(isGeomean());
  }

  @Override
  public final ScalarGroundMotion calc(GmmInput in) {
    double μ = calcMean(coeffs, coeffsPGA, coeffsPGAprime, tectonicSetting(), in);
    double σ = calcStdDev(coeffs, in.Mw);
    return DefaultScalarGroundMotion.create(μ, σ);
  }

  /* as opposed to greatest horizontal */
  abstract boolean isGeomean();

  /* as opposed to subduction */
  abstract TectonicSetting tectonicSetting();

  private static double calcMean(final Coefficients c, final Coefficients cPGA,
      final Coefficients cPGAp, final TectonicSetting tect, final GmmInput in) {

    double pgaMean = calcMeanBase(cPGA, tect, in);

    if (c.imt == PGA) {
      return pgaMean;
    }

    double pga_prime = exp(calcMeanBase(cPGAp, tect, in));
    double sa_prime = exp(calcMeanBase(c, tect, in));
    return log(sa_prime * exp(pgaMean) / pga_prime);
  }

  private static double calcMeanBase(final Coefficients c, final TectonicSetting tect,
      final GmmInput in) {

    double lnSA_AB = (tect == ACTIVE_SHALLOW_CRUST || tect == VOLCANIC)
        ? calcCrustal(c, tect, in) : calcSubduction(c, tect, in);

    double lnSA_CD = calcSiteTerm(c, in.vs30, lnSA_AB);

    return lnSA_AB + lnSA_CD;
  }

  private static double calcCrustal(final Coefficients c, final TectonicSetting tect,
      final GmmInput in) {

    double Mw = in.Mw;
    double rRup = in.rRup;

    double rVol = (tect == VOLCANIC) ? rRup : 0.0;

    FaultStyle style = rakeToFaultStyle(in.rake);
    double faultTerm = (style == REVERSE) ? c.c33as : (style == REVERSE_OBLIQUE)
        ? c.c33as * 0.5 : (style == NORMAL) ? C32 : 0.0;

    return c.c1 +
        C4AS * (Mw - 6.0) +
        c.c3as * (8.5 - Mw) * (8.5 - Mw) +
        c.c5 * rRup +
        (c.c8 + C6AS * (Mw - 6.0)) * log(sqrt(rRup * rRup + c.c10as * c.c10as)) +
        c.c46 * rVol + faultTerm;
  }

  private static double calcSubduction(final Coefficients c, final TectonicSetting tect,
      final GmmInput in) {

    double Mw = in.Mw;
    double magTerm = 10 - Mw;

    double subTerm = (tect == SUBDUCTION_INTERFACE) ? c.c24 : 0.0;

    return c.c11 +
        (C12Y + (c.c15 - c.c17) * C19Y) * (Mw - 6) +
        c.c13y * magTerm * magTerm * magTerm +
        c.c17 * log(in.rRup + C18Y * exp(C19Y * Mw)) +
        c.c20 * in.zHyp + subTerm;

    // NOTE: tectonic setting terms from publication:
    // c.c24 * SI + c.c46 * rVol * (1 - DS);
    //
    // volcanic sources will always be fed to calcCrustal so rVol will
    // alwyas be 0.0 here; only interface (or not) matters.
  }

  private static double calcSiteTerm(final Coefficients c, final double vs30,
      final double lnSA_AB) {
    SiteClass siteClass = SiteClass.fromVs30(vs30);
    checkState(siteClass != SiteClass.E);
    return (siteClass == SiteClass.C) ? c.c29
        : (siteClass == SiteClass.D) ? c.c30as * log(exp(lnSA_AB) + 0.03) + c.c43 : 0.0;
  }

  private double calcStdDev(final Coefficients c, final double Mw) {
    double sigma = c.σ6 +
        ((Mw >= 7.0) ? c.σSlope : (Mw <= 5.0) ? -c.σSlope : c.σSlope * (Mw - 6.0));
    return sqrt(sigma * sigma + c.τ * c.τ);
  }

  // @formatter:on

  /*
   * New Zealand site classes; these do not stricly correspond to ranges of vs30
   * values
   */
  private static enum SiteClass {
    A(1500.0),
    B(360.0),
    C(250.0),
    D(150.0),
    E(0.0);
    private double min;

    private SiteClass(double min) {
      this.min = min;
    }

    static SiteClass fromVs30(double vs30) {
      checkArgument(vs30 > 0.0);
      for (SiteClass siteClass : values()) {
        if (vs30 > siteClass.min) {
          return siteClass;
        }
      }
      throw new IllegalStateException("Shouldn't be here");
    }
  }

  private static FaultStyle rakeToFaultStyle(double rake) {
    if ((rake > 33 && rake <= 56) || (rake >= 124 && rake < 147)) {
      return REVERSE_OBLIQUE;
    } else if (rake > 56 && rake < 124) {
      return REVERSE;
    } else if (rake > -147 && rake < -33) {
      return NORMAL;
    } else {
      // rake <= -147 || rake >= 147
      // rake <= 33 && rake >= -33
      return STRIKE_SLIP;
    }
  }

  // TODO clean
  public static void main(String[] args) {
    System.out.println(rakeToFaultStyle(10)); // SS
    System.out.println(rakeToFaultStyle(45)); // SR
    System.out.println(rakeToFaultStyle(70)); // R
    System.out.println(rakeToFaultStyle(110)); // R
    System.out.println(rakeToFaultStyle(132)); // SR
    System.out.println(rakeToFaultStyle(168)); // SS

    System.out.println(rakeToFaultStyle(-10)); // SS
    System.out.println(rakeToFaultStyle(-45)); // N
    System.out.println(rakeToFaultStyle(-70)); // N
    System.out.println(rakeToFaultStyle(-110)); // N
    System.out.println(rakeToFaultStyle(-132)); // N
    System.out.println(rakeToFaultStyle(-168)); // SS
  }

  static final class Crustal extends McVerryEtAl_2000 {
    final static String NAME = McVerryEtAl_2000.NAME + ": Crustal";

    Crustal(Imt imt) {
      super(imt);
    }

    @Override
    boolean isGeomean() {
      return false;
    }

    @Override
    TectonicSetting tectonicSetting() {
      return TectonicSetting.ACTIVE_SHALLOW_CRUST;
    }
  }

  static final class Volcanic extends McVerryEtAl_2000 {
    final static String NAME = McVerryEtAl_2000.NAME + ": Volcanic";

    Volcanic(Imt imt) {
      super(imt);
    }

    @Override
    boolean isGeomean() {
      return false;
    }

    @Override
    TectonicSetting tectonicSetting() {
      return TectonicSetting.VOLCANIC;
    }
  }

  static final class Interface extends McVerryEtAl_2000 {
    final static String NAME = McVerryEtAl_2000.NAME + ": Interface";

    Interface(Imt imt) {
      super(imt);
    }

    @Override
    boolean isGeomean() {
      return false;
    }

    @Override
    TectonicSetting tectonicSetting() {
      return TectonicSetting.SUBDUCTION_INTERFACE;
    }
  }

  static final class Slab extends McVerryEtAl_2000 {
    final static String NAME = McVerryEtAl_2000.NAME + ": Slab";

    Slab(Imt imt) {
      super(imt);
    }

    @Override
    boolean isGeomean() {
      return false;
    }

    @Override
    TectonicSetting tectonicSetting() {
      return TectonicSetting.SUBDUCTION_INTRASLAB;
    }
  }

  // TODO clean and/or implement
  // public void setEqkRupture(EqkRupture eqkRupture) throws
  // InvalidRangeException {
  //
  // magParam.setValueIgnoreWarning(new Double(eqkRupture.getMag()));
  // setFaultTypeFromRake(eqkRupture.getAveRake());
  // this.eqkRupture = eqkRupture;
  // setPropagationEffectParams();
  //
  // if (tecRegType.equals(FLT_TEC_ENV_INTERFACE) ||
  // tecRegType.equals(FLT_TEC_ENV_INTERFACE)) {
  // //Determine the focal depth
  // // this is problematic, see ticket #438
  // RuptureSurface surf = this.eqkRupture.getRuptureSurface();
  // double hypoLon = 0.0;
  // double hypoLat = 0.0;
  // double hypoDep = 0.0;
  // double cnt = 0.0;
  // for(Location loc: surf.getEvenlyDiscritizedListOfLocsOnSurface()) {
  // hypoLon += loc.getLongitude();
  // hypoLat += loc.getLatitude();
  // hypoDep += loc.getDepth();
  // cnt += 1;
  // }
  //
  // hypoLon = hypoLon / cnt;
  // hypoLat = hypoLat / cnt;
  // hypoDep = hypoDep / cnt;
  // focalDepthParam.setValueIgnoreWarning(new Double(hypoDep));
  // }
  //
  //
  // }

  // // Computing the hypocentral depth
  // // System.out.println("Zhao et al -->"+this.eqkRupture.getInfo());
  //
  // RuptureSurface surf = this.eqkRupture.getRuptureSurface();
  //
  // // ----------------------------------------------------------------------
  // MARCO 2010.03.15
  // // Compute the hypocenter as the middle point of the rupture
  // // this is problematic, see ticket #438
  // double hypoLon = 0.0;
  // double hypoLat = 0.0;
  // double hypoDep = 0.0;
  // double cnt = 0.0;
  // for(Location loc: surf.getEvenlyDiscritizedListOfLocsOnSurface()) {
  // hypoLon += loc.getLongitude();
  // hypoLat += loc.getLatitude();
  // hypoDep += loc.getDepth();
  // cnt += 1;
  // }
  // hypoLon = hypoLon / cnt;
  // hypoLat = hypoLat / cnt;
  // hypoDep = hypoDep / cnt;
  // hypodepth = hypoDep;
  // // System.out.println("computed hypocentral depth:"+hypodepth);
  // // hypodepth = this.eqkRupture.getHypocenterLocation().getDepth();
  // // System.out.println("real hypocentral depth:"+hypodepth);
  // // ----------------------------------------------------------------------
  // MARCO 2010.03.15

}
