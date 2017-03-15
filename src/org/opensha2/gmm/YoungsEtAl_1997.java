package org.opensha2.gmm;

import static java.lang.Math.exp;
import static java.lang.Math.log;
import static java.lang.Math.min;
import static java.lang.Math.pow;

import static org.opensha2.gmm.GmmInput.Field.MW;
import static org.opensha2.gmm.GmmInput.Field.RRUP;
import static org.opensha2.gmm.GmmInput.Field.VS30;
import static org.opensha2.gmm.GmmInput.Field.ZTOP;
import static org.opensha2.gmm.Imt.PGA;

import org.opensha2.eq.Earthquakes;
import org.opensha2.gmm.GmmInput.Constraints;

import com.google.common.collect.Range;

import java.util.Map;

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
 * <p><b>doi:</b> <a href="http://dx.doi.org/10.1785/gssrl.68.1.58">
 * 10.1785/gssrl.68.1.58</a>
 *
 * <p><b>Component:</b> Geometric mean of two horizontal components
 *
 * @author Peter Powers
 * @see Gmm#YOUNGS_97_INTER
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
    }
  }

  private final Coefficients coeffs;
  private final Coefficients coeffsPGA;
  private final BooreAtkinsonSiteAmp siteAmp;

  YoungsEtAl_1997(final Imt imt) {
    coeffs = new Coefficients(imt, COEFFS);
    coeffsPGA = new Coefficients(PGA, COEFFS);
    siteAmp = new BooreAtkinsonSiteAmp(imt);
  }

  @Override
  public final ScalarGroundMotion calc(final GmmInput in) {
    double μ = calcMean(coeffs, coeffsPGA, siteAmp, isSlab(), in);
    double σ = calcStdDev(coeffs, in.Mw);
    return DefaultScalarGroundMotion.create(μ, σ);
  }

  abstract boolean isSlab();

  private static final double calcMean(final Coefficients c, final Coefficients cPGA,
      final BooreAtkinsonSiteAmp siteAmp, final boolean slab, final GmmInput in) {

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
      gnd = gnd + siteAmp.calc(pganl, vs30, VGEO[ir]);
    }
    return gnd;

  }

  private static final double calcStdDev(final Coefficients c, final double Mw) {
    // same sigma for soil and rock; sigma capped at M=8 per Youngs et al.
    return c.gc4 + c.gc5 * min(8.0, Mw);
  }

  static final class Interface extends YoungsEtAl_1997 {
    static final String NAME = YoungsEtAl_1997.NAME + ": Interface";

    Interface(Imt imt) {
      super(imt);
    }

    @Override
    final boolean isSlab() {
      return false;
    }
  }

  static final class Slab extends YoungsEtAl_1997 {
    static final String NAME = YoungsEtAl_1997.NAME + ": Slab";

    Slab(Imt imt) {
      super(imt);
    }

    @Override
    final boolean isSlab() {
      return true;
    }
  }

}
