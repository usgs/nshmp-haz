package org.opensha2.gmm;

import static java.lang.Math.log10;
import static java.lang.Math.pow;

import static org.opensha2.gmm.GmmInput.Field.MAG;
import static org.opensha2.gmm.GmmInput.Field.RRUP;
import static org.opensha2.gmm.GmmInput.Field.VS30;
import static org.opensha2.gmm.GmmInput.Field.ZTOP;
import static org.opensha2.gmm.GmmUtils.BASE_10_TO_E;
import static org.opensha2.gmm.GmmUtils.LN_G_CM_TO_M;
import static org.opensha2.gmm.Imt.PGA;

import org.opensha2.eq.fault.Faults;
import org.opensha2.gmm.GmmInput.Constraints;

import com.google.common.collect.Range;

import java.util.Map;

/**
 * Abstract implementation of the subduction ground motion model by Atkinson &
 * Boore (2003). This implementation matches that used in the 2008 USGS NSHM.
 * This model has global- and Cascadia-specific forms and can be used for both
 * slab and interface events. In the 2008 NSHM, the 'interface' form is used
 * with the Cascadia subduction zone models and the 'slab' form is used with
 * gridded 'deep' events in northern California and the Pacific Northwest. In
 * the 2014 NSHM, 'slab' implementations with Mw saturation at 7.8 were added.
 *
 * <p><b>Note:</b> NSHM fortran implementations implement strict hypocentral
 * depths that are hardcoded into these implementations as well. FOr interface
 *
 * <p><b>Note:</b> Direct instantiation of {@code GroundMotionModel}s is
 * prohibited. Use {@link Gmm#instance(Imt)} to retrieve an instance for a
 * desired {@link Imt}.</p>
 *
 * <p><b>Reference:</b> Atkinson, G.M. and Boore, D.M., 2003, Empirical
 * ground-motion relations for subduction-zone earthquakes and their application
 * to Cascadia and other regions: Bulletin of the Seismological Society of
 * America, v. 93, p. 1703-1729.</p>
 *
 * <p><b>doi:</b> <a href="http://dx.doi.org/10.1785/0120020156">
 * 10.1785/0120020156</a></p>
 *
 * <p><b>Component:</b> horizontal (not clear from publication)</p>
 *
 * @author Peter Powers
 * @see Gmm#AB_03_CASC_INTER
 * @see Gmm#AB_03_CASC_SLAB
 * @see Gmm#AB_03_CASC_SLAB_LOW_SAT
 * @see Gmm#AB_03_GLOB_INTER
 * @see Gmm#AB_03_GLOB_SLAB
 * @see Gmm#AB_03_GLOB_SLAB_LOW_SAT
 */
public abstract class AtkinsonBoore_2003 implements GroundMotionModel {

  static final String NAME = "Atkinson & Boore (2003)";

  // TODO will probably want to have constraints per-implementation
  static final Constraints CONSTRAINTS = Constraints.builder()
      .set(MAG, Range.closed(5.0, 9.5))
      .set(RRUP, Range.closed(0.0, 1000.0))
      .set(ZTOP, Faults.SLAB_DEPTH_RANGE)
      .set(VS30, Range.closed(150.0, 1500.0))
      .build();

  static final CoefficientContainer COEFFS_CASC_SLAB,
  COEFFS_CASC_INTERFACE,
  COEFFS_GLOBAL_SLAB,
  COEFFS_GLOBAL_INTERFACE;

  static {
    COEFFS_CASC_SLAB = new CoefficientContainer("AB03_cascadia_slab.csv");
    COEFFS_CASC_INTERFACE = new CoefficientContainer("AB03_cascadia_interface.csv");
    COEFFS_GLOBAL_SLAB = new CoefficientContainer("AB03_global_slab.csv");
    COEFFS_GLOBAL_INTERFACE = new CoefficientContainer("AB03_global_interface.csv");
  }

  private static final class Coefficients {

    final Imt imt;
    final double c1, c2, c3, c4, c5, c6, c7, sig;

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
      sig = coeffs.get("sig");
    }
  }

  private final Coefficients coeffs;
  private final Coefficients coeffsPGA;
  private final double mMax;

  AtkinsonBoore_2003(final Imt imt) {
    coeffs = initCoeffs(imt, isSlab(), isGlobal());
    coeffsPGA = initCoeffs(PGA, isSlab(), isGlobal());
    mMax = saturationMw();
  }

  private static Coefficients initCoeffs(final Imt imt, final boolean slab,
      final boolean global) {
    CoefficientContainer coeffs = slab && global ? COEFFS_GLOBAL_SLAB : slab ? COEFFS_CASC_SLAB
        : global ? COEFFS_GLOBAL_INTERFACE : COEFFS_CASC_INTERFACE;
    return new Coefficients(imt, coeffs);
  }

  @Override
  public final ScalarGroundMotion calc(final GmmInput in) {
    double μ = calcMean(coeffs, coeffsPGA, isSlab(), mMax, in);
    double σ = coeffs.sig * BASE_10_TO_E;
    return DefaultScalarGroundMotion.create(μ, σ);
  }

  // implementation flag
  abstract boolean isGlobal();

  // implementation flag
  abstract boolean isSlab();

  // implementation value
  // - interface events saturate at 8.5
  // - slab events saturate at 8.0 in the 2008 NSHM
  // - slab event saturation was reduced to 7.8 for 2014 NSHM
  abstract double saturationMw();

  private static final double SAT_MW_INTERFACE = 8.5;
  private static final double SAT_MW_SLAB_2008 = 8.0;
  private static final double SAT_MW_SLAB_2014 = 7.8;

  // SF2 variable of AB06 needs to be provided by subclasses via
  private static final double calcMean(final Coefficients c, final Coefficients cPGA,
      final boolean slab, final double mMax, final GmmInput in) {

    // "saturation effect" p. 1709 AB 2003
    double Mw = Math.min(in.Mw, mMax);

    // TODO what is the reasoning behind the following?
    // does zHyp yield unreliable results?

    // depth: fixed @ 20km for interface; max 100km for slab
    double depth = slab ? Math.min(in.zTop, 100.0) : 20.0;

    double delta = 0.00724 * pow(10, 0.507 * Mw);
    double g = pow(10, slab ? (0.301 - 0.01 * Mw) : (1.2 - 0.18 * Mw));
    double gndm = c.c1 + c.c2 * Mw;

    // as far as I can tell from hazSUBXnga and hazSUBXngatest, interface
    // events are fixed at depth = 20km, slab events from hazgrid are
    // variable but limited to <100km (this constraint is ignored; not
    // sure where it comes from; see depthp in hazgrid)
    // NOTE this constraint has been removed in favor of setting a default
    // depth of 20km in NSHMP08_SUB_Interface which is more appropriate as
    // the 20km value is NSHMP specific.
    // if (!slab) depth = 20;
    // TODO revisit above

    double dist2 = Math.sqrt(in.rRup * in.rRup + delta * delta);
    double gnd = gndm + c.c3 * depth + c.c4 * dist2 - g * log10(dist2);
    double rpga = cPGA.c1 + cPGA.c2 * Mw + cPGA.c3 * depth + cPGA.c4 * dist2 - g *
        log10(dist2);
    rpga = pow(10, rpga);

    double freq = c.imt.frequency();
    double sl;
    if ((rpga <= 100.0) || (freq <= 1.0)) {
      sl = 1.0;
    } else if ((rpga > 100.0) && (rpga < 500.0) && (freq > 1.0) && (freq < 2.0)) {
      sl = 1.0 - (freq - 1.) * (rpga - 100) / 400.0;
    } else if ((rpga >= 500.0) && (freq > 1.0) && (freq < 2.0)) {
      sl = 1. - (freq - 1.);
    } else if ((rpga > 100.) && (rpga < 500.0) && (freq >= 2.0)) {
      sl = 1. - (rpga - 100.) / 400.;
      // c if((rpga.ge.500.).and.(freq.ge.2.)) sl= 0.
    } else {
      sl = 0.0;
    }

    double vs30 = in.vs30;
    if (slab) {
      if (vs30 > 780.0) { // B-rock
        // do nothing gnd = gnd;
      } else if (vs30 > 660.0) { // BC-rock
        gnd = gnd + (sl * c.c5) * 0.5;
      } else if (vs30 > 360.0) { // C-soil
        gnd = gnd + sl * c.c5;
      } else if (vs30 > 190.0) { // D-soil
        gnd = gnd + sl * c.c6;
      } else { // DE or E-soil
        gnd = gnd + sl * c.c7;
      }
    } else {
      // in NSHMP, interface site amplification is more refined, why?
      // what's the history on this; TODO check with reference
      if (vs30 > 900.0) {
        // do nothing gnd = gnd;
      } else if (vs30 > 720.0) { // BC boundary
        gnd = gnd + (sl * c.c5) * 0.5;
      } else if (vs30 >= 380.0) { // site class C
        gnd = gnd + sl * c.c5;
      } else if (vs30 >= 350.0) { // CD boundary
        gnd = gnd + 0.5 * sl * (c.c5 + c.c6);
      } else if (vs30 >= 190.0) { // site class D
        gnd = gnd + sl * c.c6;
      } else if (vs30 >= 170.0) { // DE boundary
        gnd = gnd + 0.5 * sl * (c.c6 + c.c7);
      } else { // site class E
        gnd = gnd + sl * c.c7;
      }
    }

    return gnd * BASE_10_TO_E - LN_G_CM_TO_M;
  }

  static final class CascadiaInterface extends AtkinsonBoore_2003 {
    static final String NAME = createName(false, false, SAT_MW_INTERFACE);

    CascadiaInterface(Imt imt) {
      super(imt);
    }

    @Override
    final boolean isGlobal() {
      return false;
    }

    @Override
    final boolean isSlab() {
      return false;
    }

    @Override
    double saturationMw() {
      return SAT_MW_INTERFACE;
    }
  }

  static final class CascadiaSlab extends AtkinsonBoore_2003 {
    static final String NAME = createName(false, true, SAT_MW_SLAB_2008);

    CascadiaSlab(Imt imt) {
      super(imt);
    }

    @Override
    final boolean isGlobal() {
      return false;
    }

    @Override
    final boolean isSlab() {
      return true;
    }

    @Override
    double saturationMw() {
      return SAT_MW_SLAB_2008;
    }
  }

  static final class CascadiaSlabLowMagSaturation extends AtkinsonBoore_2003 {
    static final String NAME = createName(false, true, SAT_MW_SLAB_2014);

    CascadiaSlabLowMagSaturation(Imt imt) {
      super(imt);
    }

    @Override
    final boolean isGlobal() {
      return false;
    }

    @Override
    final boolean isSlab() {
      return true;
    }

    @Override
    double saturationMw() {
      return SAT_MW_SLAB_2014;
    }
  }

  static final class GlobalInterface extends AtkinsonBoore_2003 {
    static final String NAME = createName(true, false, SAT_MW_INTERFACE);

    GlobalInterface(Imt imt) {
      super(imt);
    }

    @Override
    final boolean isGlobal() {
      return true;
    }

    @Override
    final boolean isSlab() {
      return false;
    }

    @Override
    double saturationMw() {
      return SAT_MW_INTERFACE;
    }
  }

  static final class GlobalSlab extends AtkinsonBoore_2003 {
    static final String NAME = createName(true, true, SAT_MW_SLAB_2008);

    GlobalSlab(Imt imt) {
      super(imt);
    }

    @Override
    final boolean isGlobal() {
      return true;
    }

    @Override
    final boolean isSlab() {
      return true;
    }

    @Override
    double saturationMw() {
      return SAT_MW_SLAB_2008;
    }
  }

  static final class GlobalSlabLowMagSaturation extends AtkinsonBoore_2003 {
    static final String NAME = createName(true, true, SAT_MW_SLAB_2014);

    GlobalSlabLowMagSaturation(Imt imt) {
      super(imt);
    }

    @Override
    final boolean isGlobal() {
      return true;
    }

    @Override
    final boolean isSlab() {
      return true;
    }

    @Override
    double saturationMw() {
      return SAT_MW_SLAB_2014;
    }
  }

  static String createName(boolean global, boolean slab, double satMw) {
    StringBuilder sb = new StringBuilder(AtkinsonBoore_2003.NAME);
    sb.append(" : ").append(global ? "Global" : "Cascadia");
    sb.append(" : ").append(slab ? "Slab" : "Interface");
    sb.append(" : SatMw=").append(satMw);
    return sb.toString();
  }

}
