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

/**
 * Abstract implementation of the subduction ground motion model by Youngs et
 * al. (1997). This implementation matches that used in the 2008 USGS NSHMP
 * where it is sometimes identified as the Geomatrix ground motion model. This
 * implementation has been modified from its original form to an NGA style (S.
 * Harmsen 7/13/2009) wherein mean ground motion varies continuously with Vs30
 * (sigma remains the same as original). This is acheived through use of a
 * period-dependent site amplification function modified from Boore & Atkinson
 * (2008).
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
 * <p><b>Reference:</b> Youngs, R.R., Chiou, S.-J., Silva, W.J., and Humphrey,
 * J.R., 1997, Strong ground motion ground motion models for subduction zone
 * earthquakes: Seismological Research Letters, v. 68, p. 58-73.
 *
 * <p><b>doi:</b> <a href="http://dx.doi.org/10.1785/gssrl.68.1.58"
 * target="_top"> 10.1785/gssrl.68.1.58</a>
 *
 * <p><b>Component:</b> Geometric mean of two horizontal components
 *
 * @author Peter Powers
 * @see Gmm#YOUNGS_97_INTERFACE
 * @see Gmm#YOUNGS_97_SLAB
 */
public abstract class YoungsEtAl_1997 implements GroundMotionModel {

  static final String NAME = "Youngs et al. (1997)";

  // TODO will probably want to have constraints per-implementation
  static final Constraints CONSTRAINTS = Constraints.builder()
      .set(MW, Range.closed(5.0, 9.5))
      .set(RRUP, Range.closed(0.0, 1000.0))
      .set(ZTOP, Earthquakes.SLAB_DEPTH_RANGE)
      .set(VS30, Range.closed(150.0, 1000.0))
      .build();

  static final CoefficientContainer COEFFS = new CoefficientContainer("Youngs97.csv");

  private static final double[] VGEO = { 760.0, 300.0, 475.0 };
  private static final double GC0 = 0.2418;
  private static final double GCS0 = -0.6687;
  private static final double CI = 0.3846;
  private static final double CIS = 0.3643;
  private static final double GCH = 0.00607;
  private static final double GCHS = 0.00648;
  private static final double GMR = 1.414;
  private static final double GMS = 1.438;
  private static final double GEP = 0.554;

  private static final class Coefficients {

    final double gc1, gc1s, gc2, gc2s, gc3, gc3s, gc4, gc5;
    final double blin, b1, b2;

    Coefficients(Imt imt, CoefficientContainer cc) {
      Map<String, Double> coeffs = cc.get(imt);
      gc1 = coeffs.get("gc1");
      gc1s = coeffs.get("gc1s");
      gc2 = coeffs.get("gc2");
      gc2s = coeffs.get("gc2s");
      gc3 = coeffs.get("gc3");
      gc3s = coeffs.get("gc3s");
      gc4 = coeffs.get("gc4");
      gc5 = coeffs.get("gc5");
      blin = coeffs.get("blin");
      b1 = coeffs.get("b1");
      b2 = coeffs.get("b2");
    }
  }

  private final Coefficients coeffs;
  private final Coefficients coeffsPGA;

  YoungsEtAl_1997(final Imt imt) {
    coeffs = new Coefficients(imt, COEFFS);
    coeffsPGA = new Coefficients(PGA, COEFFS);
  }

  @Override
  public final ScalarGroundMotion calc(final GmmInput in) {
    double μ = calcMean(coeffs, coeffsPGA, isSlab(), in);
    double σ = calcStdDev(coeffs, in.Mw);
    return DefaultScalarGroundMotion.create(μ, σ);
  }

  abstract boolean isSlab();

  private static final double calcMean(
      final Coefficients c,
      final Coefficients cPGA,
      final boolean slab,
      final GmmInput in) {

    double Mw = in.Mw;
    double rRup = in.rRup;
    double zTop = in.zTop;
    double vs30 = in.vs30;

    double slabVal = slab ? 1 : 0;

    // NSHMP hazgridXnga caps slab events at M=8 after AB03 sub
    if (slab) {
      Mw = Math.min(8.0, Mw);
    }

    // reference PGA; determine nonlinear response using this value
    double gnd0p = GC0 + CI * slabVal;
    double gnd0, gz, g1, g2, g3, g4, ge, gm;
    int ir;
    if (vs30 > 520.0) { // rock
      gnd0 = GC0 + CI * slabVal; // no interface term ci for subduction
      gz = GCH;
      g1 = c.gc1;
      g2 = c.gc2;
      g3 = c.gc3;
      g4 = 1.7818;
      ge = 0.554;
      gm = GMR;
      ir = 0;
    } else { // soil
      gnd0 = GCS0 + CIS * slabVal; // no interface term cis for subduction
      gz = GCHS;
      g1 = c.gc1s;
      g2 = c.gc2s;
      g3 = c.gc3s;
      g4 = 1.097;
      ge = 0.617;
      gm = GMS;
      ir = 1;
    }

    double gndm = gnd0 + g1 + (gm * Mw) + g2 * Math.pow(10.0 - Mw, 3) + (gz * zTop);
    double arg = Math.exp(ge * Mw);
    double gnd = gndm + g3 * Math.log(rRup + g4 * arg);
    if (vs30 != VGEO[ir]) {
      // frankel mods for nonlin siteamp July 7/09
      double gndzp = gnd0p + zTop * GCH + cPGA.gc1;
      double gndmp = gndzp + GMR * Mw + cPGA.gc2 * pow(10.0 - Mw, 3);
      double argp = exp(GEP * Mw);
      double gndp = gndmp + cPGA.gc3 * log(rRup + 1.7818 * argp);
      double pganl = exp(gndp);
      gnd = gnd + calcSiteAmp(c, pganl, vs30, VGEO[ir]);
    }
    return gnd;

  }

  private static final double calcStdDev(final Coefficients c, final double Mw) {
    // same sigma for soil and rock; sigma capped at M=8 per Youngs et al.
    return c.gc4 + c.gc5 * min(8.0, Mw);
  }

  static final class Interface extends YoungsEtAl_1997 {
    static final String NAME = YoungsEtAl_1997.NAME + " : Interface";

    Interface(Imt imt) {
      super(imt);
    }

    @Override
    final boolean isSlab() {
      return false;
    }
  }

  static final class Slab extends YoungsEtAl_1997 {
    static final String NAME = YoungsEtAl_1997.NAME + " : Slab";

    Slab(Imt imt) {
      super(imt);
    }

    @Override
    final boolean isSlab() {
      return true;
    }
  }

  /*
   * Local implementation of the Boore & Atkinson (2008) NGA-West1 site
   * amplification model that computes amplification relative to a reference
   * vs30 other than the internal rock reference of 760. Atkinson & Macias
   * (2009), which also relied on this implementation now points to the
   * published implementation in the Boore & Atkinson (2008) GMM [BA_08].
   */

  private static final double V1 = 180.0;
  private static final double V2 = 300.0;
  private static final double A1 = 0.030;
  private static final double A2 = 0.090;
  private static final double A2FAC = 0.405465108;
  private static final double VREF = 760.0;
  private static final double DX = 1.098612289; // ln(a2/a1)
  private static final double DXSQ = 1.206948961;
  private static final double DXCUBE = 1.325968960;
  private static final double PLFAC = -0.510825624; // ln(0.06/0.1)

  private static double calcSiteAmp(
      Coefficients c,
      double lnPga,
      double vs30,
      double vs30r) {

    double dy, dyr, site, siter = 0.0;

    double bnl, bnlr;
    // some site term precalcs that are not M or d dependent
    if (V1 < vs30 && vs30 <= V2) {
      bnl = (c.b1 - c.b2) * log(vs30 / V2) / log(V1 / V2) + c.b2;
    } else if (V2 < vs30 && vs30 <= VREF) {
      bnl = c.b2 * log(vs30 / VREF) / log(V2 / VREF);
    } else if (vs30 <= V1) {
      bnl = c.b1;
    } else {
      bnl = 0.0;
    }

    if (V1 < vs30r && vs30r <= V2) {
      // repeat site term precalcs that are not M or d dependent
      // @ reference vs
      bnlr = (c.b1 - c.b2) * log(vs30r / V2) / log(V1 / V2) + c.b2;
    } else if (V2 < vs30r && vs30r <= VREF) {
      bnlr = c.b2 * log(vs30r / VREF) / log(V2 / VREF);
    } else if (vs30r <= V1) {
      bnlr = c.b1;
    } else {
      bnlr = 0.0;
    }

    dy = bnl * A2FAC; // ADF added line
    dyr = bnlr * A2FAC;
    site = c.blin * log(vs30 / VREF);
    siter = c.blin * log(vs30r / VREF);

    // Second part, nonlinear siteamp reductions below.
    if (lnPga <= A1) {
      site = site + bnl * PLFAC;
      siter = siter + bnlr * PLFAC;
    } else if (lnPga <= A2) {
      // extra lines smooth a kink in siteamp, pp 9-11 of boore sept
      // report. c and d from p 10 of boore sept report. Smoothing
      // introduces extra calcs in the range a1 < pganl < a2. Otherwise
      // nonlin term same as in june-july. Many of these terms are fixed
      // and are defined in data or parameter statements. Of course, if a1
      // and a2 change from their sept 06 values the parameters will also
      // have to be redefined. (a1,a2) represents a siteamp smoothing
      // range (units g)
      double cc = (3. * dy - bnl * DX) / DXSQ;
      double dd = (bnl * DX - 2. * dy) / DXCUBE;
      double pgafac = log(lnPga / A1);
      double psq = pgafac * pgafac;
      site = site + bnl * PLFAC + (cc + dd * pgafac) * psq;
      cc = (3. * dyr - bnlr * DX) / DXSQ;
      dd = (bnlr * DX - 2. * dyr) / DXCUBE;
      siter = siter + bnlr * PLFAC + (cc + dd * pgafac) * psq;
    } else {
      double pgafac = log(lnPga / 0.1);
      site = site + bnl * pgafac;
      siter = siter + bnlr * pgafac;
    }
    return site - siter;
  }

}
