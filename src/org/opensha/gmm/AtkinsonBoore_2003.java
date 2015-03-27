package org.opensha.gmm;

import static java.lang.Math.log10;
import static java.lang.Math.pow;
import static org.opensha.gmm.GmmUtils.BASE_10_TO_E;
import static org.opensha.gmm.Imt.PGA;

/**
 * Abstract implementation of the subduction ground motion model by Atkinson &
 * Boore (2003). This implementation matches that used in the 2008 USGS NSHMP.
 * This model has global- and Cascadia-specific forms and can be used for both
 * slab and interface events. In the 2008 NSHMP, the 'interface' form is used
 * with the Cascadia subduction zone models and the 'slab' form is used with
 * gridded 'deep' events in northern California and the Pacific Northwest.
 * 
 * <p><b>Note:</b> Direct instantiation of {@code GroundMotionModel}s is
 * prohibited. Use {@link Gmm#instance(Imt)} to retrieve an instance for a
 * desired {@link Imt}.</p>
 * 
 * <p><b>Reference:</b> Atkinson, G.M. and Boore, D.M., 2003, Empirical
 * Ground-Motion Relations for Subduction-Zone Earthquakes and Their Application
 * to Cascadia and Other Regions: Bulletin of the Seismological Society of
 * America, v. 93, p. 1012–1033.</p>
 * 
 * <p><b>Component:</b> horizontal (not clear from publication)</p>
 * 
 * @author Peter Powers
 * @see Gmm#AB_03_CASC_INTER
 * @see Gmm#AB_03_CASC_SLAB
 * @see Gmm#AB_03_GLOB_INTER
 * @see Gmm#AB_03_GLOB_SLAB
 */
public abstract class AtkinsonBoore_2003 implements GroundMotionModel {

	// TODO from original implementation -- recheck
	// NOTE RupTopDepthParam is used in a funny way here (see also Youngs/
	// Geomatrix). Currently it is not adjusted when updating an earthquake
	// rupture, but must be set manually. It defaults to 20km (the value
	// imposed in the 2008 NSHMP Cascadia subduction interface model. Any other
	// sources should update this value independently.

	static final String NAME = "Atkinson & Boore (2003)";

	// This relation supports a number of options and requires PGA reference
	// values. Although implementation specific coeffs could be loaded by
	// subclasses, their overhead is so small that they're loaded here.
	//
	// S = Slab, I = Interface
	// G = Global, C = Cascadia/PNW
	static final CoefficientContainer CC_CS, CC_CI, CC_GS, CC_GI;

	static {
		CC_CS = new CoefficientContainer("AB03_cascadia_slab.csv", Coeffs.class);
		CC_CI = new CoefficientContainer("AB03_cascadia_interface.csv", Coeffs.class);
		CC_GS = new CoefficientContainer("AB03_global_slab.csv", Coeffs.class);
		CC_GI = new CoefficientContainer("AB03_global_interface.csv", Coeffs.class);
	}

	static class Coeffs extends Coefficients {
		double c1, c2, c3, c4, c5, c6, c7, sig;
	}

	// author declared constants
	private static final double gfac = 2.9912261; // log10(980)

	// implementation constants
	// none

	private final Coeffs coeffs;
	private final Coeffs coeffsPGA;

	AtkinsonBoore_2003(Imt imt) {
		if (isSlab()) {
			coeffs = (Coeffs) (isGlobal() ? CC_GS.get(imt) : CC_CS.get(imt));
			coeffsPGA = (Coeffs) (isGlobal() ? CC_GS.get(PGA) : CC_CS.get(PGA));
		} else {
			coeffs = (Coeffs) (isGlobal() ? CC_GI.get(imt) : CC_CI.get(imt));
			coeffsPGA = (Coeffs) (isGlobal() ? CC_GI.get(PGA) : CC_CI.get(PGA));
		}
	}

	@Override public final ScalarGroundMotion calc(GmmInput props) {
		double mean = calcMean(coeffs, coeffsPGA, props.Mw, props.rRup, props.zTop, props.vs30,
			isSlab(), coeffs.imt.frequency());
		double sigma = coeffs.sig * BASE_10_TO_E;
		return DefaultScalarGroundMotion.create(mean, sigma);
	}

	// subclass flag
	abstract boolean isGlobal();

	// subclass flag
	abstract boolean isSlab();

	// SF2 variable of AB06 needs to be provided by subclasses via
	private static final double calcMean(Coeffs c, Coeffs cPGA, double Mw, double rRup,
			double zTop, double vs30, boolean slab, double freq) {

		// "saturation effect" p. 1709 AB 2003
		Mw = Math.min(Mw, slab ? 8.0 : 8.5);

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

		double dist2 = Math.sqrt(rRup * rRup + delta * delta);
		double gnd = gndm + c.c3 * zTop + c.c4 * dist2 - g * log10(dist2);
		double rpga = cPGA.c1 + cPGA.c2 * Mw + cPGA.c3 * zTop + cPGA.c4 * dist2 - g * log10(dist2);
		rpga = pow(10, rpga);

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

		return (gnd - gfac) * BASE_10_TO_E;
	}

	static final class CascadiaInterface extends AtkinsonBoore_2003 {
		static final String NAME = AtkinsonBoore_2003.NAME + ": Cascadia Interface";

		CascadiaInterface(Imt imt) {
			super(imt);
		}

		@Override final boolean isGlobal() {
			return false;
		}

		@Override final boolean isSlab() {
			return false;
		}
	}

	static final class CascadiaSlab extends AtkinsonBoore_2003 {
		static final String NAME = AtkinsonBoore_2003.NAME + ": Cascadia Slab";

		CascadiaSlab(Imt imt) {
			super(imt);
		}

		@Override final boolean isGlobal() {
			return false;
		}

		@Override final boolean isSlab() {
			return true;
		}
	}

	static final class GlobalInterface extends AtkinsonBoore_2003 {
		static final String NAME = AtkinsonBoore_2003.NAME + ": Global Interface";

		GlobalInterface(Imt imt) {
			super(imt);
		}

		@Override final boolean isGlobal() {
			return true;
		}

		@Override final boolean isSlab() {
			return false;
		}
	}

	static final class GlobalSlab extends AtkinsonBoore_2003 {
		static final String NAME = AtkinsonBoore_2003.NAME + ": Global Slab";

		GlobalSlab(Imt imt) {
			super(imt);
		}

		@Override final boolean isGlobal() {
			return true;
		}

		@Override final boolean isSlab() {
			return true;
		}
	}

}
