package org.opensha.gmm;

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.Math.log;
import static java.lang.Math.log10;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static org.opensha.gmm.GmmUtils.BASE_10_TO_E;
import static org.opensha.gmm.Imt.PGA;
import static org.opensha.gmm.MagConverter.NONE;
import static org.opensha.gmm.SiteClass.SOFT_ROCK;

import java.util.Map;

/**
 * Abstract implementation of the ground motion model for stable continental
 * regions by Atkinson & Boore (2006). This implementation matches that used in
 * the 2008 USGS NSHMP. In addition to have two stress-drop scaling variants,
 * this model also comes in magnitude converting (mb to Mw) flavors to support
 * the 2008 central and eastern US model.
 * 
 * <p><b>Note:</b> Direct instantiation of {@code GroundMotionModel}s is
 * prohibited. Use {@link Gmm#instance(Imt)} to retrieve an instance for a
 * desired {@link Imt}.</p>
 * 
 * <p><b>Implementation note:</b> this uses a reduced set of frequencies that
 * correspond most closely to defined {@code Imt}s.</p>
 * 
 * <p><b>Implementation note:</b> Mean values are clamped per
 * {@link GmmUtils#ceusMeanClip(Imt, double)}.</p>
 * 
 * <p><b>Reference:</b> Atkinson, G.M., and Boore, D.M., 2006, Earthquake
 * ground-motion prediction equations for eastern North America: Bulletin of the
 * Seismological Society of America, v. 96, p. 2181–2205.</p>
 * 
 * <p><b>Component:</b> horizontal (not clear from publication)</p>
 * 
 * @author Peter Powers
 * @see Gmm#AB_06_140BAR
 * @see Gmm#AB_06_140BAR_AB
 * @see Gmm#AB_06_140BAR_J
 * @see Gmm#AB_06_200BAR
 * @see Gmm#AB_06_200BAR_AB
 * @see Gmm#AB_06_200BAR_J
 */
public abstract class AtkinsonBoore_2006 implements GroundMotionModel, ConvertsMag {

	// This model was developed using data from hard rock sites and simulations.
	// CoefficientsOld are provided for hard rock and soft rock sites. Use the
	// hard
	// rock coefficients for vs30 >= 2000. Use soft rock coeffs for vs30 <= 760
	// and lower. It is not clear from Atkinson and Boore (2006) how vs30
	// between 760 and 2000 should be handled. The published algorithms will
	// keep returning the vs760 values and this is likely incorrect. For now,
	// we only allow vs760 and vs200 in keeping with other CEUS GMMs.

	// This implementation includes an abstract method scaleFactor(), the value
	// of which can be obtained via the static utility method
	// scaleFactorCalc(stress). A stress value of 140bars will return a scale
	// factor of 0.

	// notes from original implementation and fortran:
	//
	// rounded .3968 to 0.4 s for one element of abper. SH june 30 2008

	static final String NAME = "Atkinson & Boore (2006)";

	static final CoefficientsNew COEFFS_A = new CoefficientsNew("AB06A.csv");
	static final CoefficientsNew COEFFS_BC = new CoefficientsNew("AB06BC.csv");

	private static final double GFAC = 6.8875526; // ln (980)
	private static final double TFAC = -0.5108256; // ln(0.6)
	private static final double vref = 760.0;
	private static final double v1 = 180.0;
	private static final double v2 = 300.0;
	private static final double fac70 = 1.8450980; // log10(70)
	private static final double fac140 = 2.1461280; // log10(140)
	private static final double facv1 = -0.5108256; // ln(v1/v2)
	private static final double facv2 = -0.9295360; // ln(v2/vref)
	private static final double SIGMA = 0.3 * BASE_10_TO_E;
	// private static final double STRESSFAC = 0.5146; // ln(200/140)/ln(2)
	// private static final double SFAC = 2.302585; // ln(10)

	private static final class Coeffs {

		final double c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, bln, b1, b2, del, m1,
				mh;

		Coeffs(Map<String, Double> coeffs) {
			c1 = coeffs.get("c1");
			c2 = coeffs.get("c2");
			c3 = coeffs.get("c3");
			c4 = coeffs.get("c4");
			c5 = coeffs.get("c5");
			c6 = coeffs.get("c6");
			c7 = coeffs.get("c7");
			c8 = coeffs.get("c8");
			c9 = coeffs.get("c9");
			c10 = coeffs.get("c10");
			bln = coeffs.get("bln");
			b1 = coeffs.get("b1");
			b2 = coeffs.get("b2");
			del = coeffs.get("del");
			m1 = coeffs.get("m1");
			mh = coeffs.get("mh");
		}
	}

	private final Coeffs coeffsA;
	private final Coeffs coeffsA_PGA;
	private final Coeffs coeffsBC;
	private final Coeffs coeffsBC_PGA;
	private final Imt imt;

	AtkinsonBoore_2006(final Imt imt) {
		this.imt = imt;
		coeffsA = new Coeffs(COEFFS_A.get(imt));
		coeffsA_PGA = new Coeffs(COEFFS_A.get(PGA));
		coeffsBC = new Coeffs(COEFFS_BC.get(imt));
		coeffsBC_PGA = new Coeffs(COEFFS_BC.get(PGA));
	}

	@Override public final ScalarGroundMotion calc(final GmmInput in) {

		// this call will only allow vs30 = 760 | 2000
		SiteClass siteClass = GmmUtils.ceusSiteClass(in.vs30);
		Coeffs coeffs = siteClass == SOFT_ROCK ? coeffsBC : coeffsA;
		Coeffs coeffsPGA = siteClass == SOFT_ROCK ? coeffsBC_PGA : coeffsA_PGA;

		return DefaultScalarGroundMotion.create(
			calcMean(coeffs, coeffsPGA, imt, converter(), scaleFactor(), in),
			SIGMA);
	}

	@Override public MagConverter converter() {
		return NONE;
	}

	private static final double DEFAULT_STRESS = 140.0;
	private static final double MIN_STRESS = 35.0;
	private static final double MAX_STRESS = 560.0;

	abstract double scaleFactor();

	private static final double scaleFactorCalc(final double stress) {
		checkArgument(stress >= MIN_STRESS && stress <= MAX_STRESS,
			"Supplied stress value [%s] is out of range [%s, %s]", stress,
			MIN_STRESS, MAX_STRESS);
		return log(stress / DEFAULT_STRESS) / log(2);
	}

	// SF2 variable of AB06 needs to be provided by subclasses via
	private static final double calcMean(final Coeffs c, final Coeffs cPGA, final Imt imt,
			final MagConverter converter, final double stressScale, final GmmInput in) {

		double Mw = converter.convert(in.Mw);
		double rRup = in.rRup;
		double vs30 = in.vs30;

		double gndmp = c.c1 + c.c2 * Mw + c.c3 * Mw * Mw;

		// set up stress factor
		double sf2 = 0.0;
		if (stressScale != 0.0) {
			double diff = max(Mw - c.m1, 0.0);
			sf2 = stressScale *
				min(c.del + 0.05, 0.05 + c.del * diff / (c.mh - c.m1));
		}

		// per NSHMP implementation rRup floored to 2km
		rRup = max(rRup, 2.0);

		// pga calculations
		double rfac = log10(rRup);
		double f0 = max(1.0 - rfac, 0.0);
		double f1 = min(rfac, fac70);
		double f2 = max(rfac - fac140, 0.0);

		double gnd, S = 0.0;

		if (vs30 <= 760.0) {

			// compute pga on rock
			double gndPGA = cPGA.c1 + cPGA.c2 * Mw + cPGA.c3 * Mw * Mw;
			gnd = gndPGA + (cPGA.c4 + cPGA.c5 * Mw) * f1 +
				(cPGA.c6 + cPGA.c7 * Mw) * f2 + (cPGA.c8 + cPGA.c9 * Mw) * f0 +
				cPGA.c10 * rRup + sf2;

			double bnl;

			if (vs30 <= v1) {
				bnl = c.b1;
			} else if (vs30 <= v2) {
				bnl = (c.b1 - c.b2) * Math.log(vs30 / v2) / facv1 +
					c.b1;
			} else if (vs30 <= vref) {
				bnl = c.b2 * Math.log(vs30 / vref) / facv2;
			} else {
				bnl = 0.0;
			}

			double pga_bc = Math.pow(10, gnd);

			if (pga_bc <= 60.0) {
				S = c.bln * log(vs30 / vref) + bnl * TFAC;
			} else {
				S = c.bln * log(vs30 / vref) + bnl * log(pga_bc / 100.0);
			}

			S = log10(Math.exp(S));
		}

		gnd = gndmp + (c.c4 + c.c5 * Mw) * f1 +
			(c.c6 + c.c7 * Mw) * f2 + (c.c8 + c.c9 * Mw) *
			f0 + c.c10 * rRup + sf2 + S;

		gnd *= BASE_10_TO_E;
		if (imt != Imt.PGV) gnd -= GFAC;

		return GmmUtils.ceusMeanClip(imt, gnd);
	}

	// these are subclassed for mag conversion variants and therefore not final

	static class StressDrop_140bar extends AtkinsonBoore_2006 {
		static final String NAME = AtkinsonBoore_2006.NAME + ": 140 bar";

		private static final double STRESS = 140.0;
		private static final double SF2;

		static {
			SF2 = scaleFactorCalc(STRESS);
		}

		StressDrop_140bar(Imt imt) {
			super(imt);
		}

		@Override double scaleFactor() {
			return SF2;
		}

	}

	static class StressDrop_200bar extends AtkinsonBoore_2006 {
		static final String NAME = AtkinsonBoore_2006.NAME + ": 200 bar";

		private static final double STRESS = 200;
		private static final double SF2;

		static {
			SF2 = scaleFactorCalc(STRESS);
		}

		StressDrop_200bar(Imt imt) {
			super(imt);
		}

		@Override double scaleFactor() {
			return SF2;
		}
	}

}
