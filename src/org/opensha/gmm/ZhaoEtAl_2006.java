package org.opensha.gmm;

import static java.lang.Math.exp;
import static java.lang.Math.log;
import static java.lang.Math.min;
import static java.lang.Math.sqrt;

/**
 * Abstract implementation of the subduction ground motion model by Zhao et al.
 * (2006). This implementation matches that used in the USGS NSHM.
 * 
 * <p>This model supports both slab and interface type events. In the 2008
 * NSHMP, the 'interface' form is used with the Cascadia subduction zone models
 * and the 'slab' form is used with gridded 'deep' events in northern California
 * and the Pacific Northwest.</p>
 * 
 * <p><b>Note:</b> Direct instantiation of {@code GroundMotionModel}s is
 * prohibited. Use {@link Gmm#instance(Imt)} to retrieve an instance for a
 * desired {@link Imt}.</p>
 * 
 * <p><b>Implementation notes:</b> <ol><li>When used for interface events, sigma
 * is computed using the generic value of tau, rather than the interface
 * specific value(see inline comments for more information).<li> <li>Hypocentral
 * depths for interface events are fixed at 20km.</li><li>Hypocentral depths for
 * slab events are set as the depth to top of rupture.</li></ol></p>
 * 
 * <p><b>Reference:</b> Zhao, J.X., Zhang, J., Asano, A., Ohno, Y., Oouchi, T.,
 * Takahashi, T., Ogawa, H., Irikura, K., Thio, H.K., Somerville, P.G.,
 * Fukushima, Y., and Fukushima, Y., 2006, Attenuation relations of strong
 * ground motion in Japan using site classification based on predominant period:
 * Bulletin of the Seismological Society of America, v. 96, p. 898–913.</p>
 * 
 * <p><b>Component:</b> Geometric mean of two horizontal components</p>
 * 
 * @author Peter Powers
 * @see Gmm#ZHAO_06_INTER
 * @see Gmm#ZHAO_06_SLAB
 */
public abstract class ZhaoEtAl_2006 implements GroundMotionModel {

	static final String NAME = "Zhao et al. (2006)";

	static final CoefficientsNew CC = new CoefficientsNew("Zhao06.csv", Coeffs.class);

	static class Coeffs extends CoefficientsOld {
		double T, a, b, c, d, e, Sr, Si, Ss, Ssl, C1, C2, C3, sigma, tau, tauI, tauS, Ps, Qi, Qs,
				Wi, Ws;
	}

	// author declared constants
	private static final double DEPTH_I = 20.0;
	private static final double HC = 15.0;
	private static final double MC_S = 6.3;
	private static final double MC_I = 6.5;
	private static final double GCOR = 6.88806;

	// implementation constants
	private static final double MAX_DEPTH = 125.0;

	private final Coeffs coeffs;

	ZhaoEtAl_2006(Imt imt) {
		coeffs = (Coeffs) CC.get(imt);
	}

	@Override public final ScalarGroundMotion calc(GmmInput props) {
		double mean = calcMean(coeffs, props.Mw, props.rRup, props.zTop, props.vs30, isSlab());
		double sigma = calcStdDev(coeffs, isSlab());
		return DefaultScalarGroundMotion.create(mean, sigma);
	}

	abstract boolean isSlab();

	private static final double calcMean(Coeffs c, double Mw, double rRup, double zTop,
			double vs30, boolean slab) {

		if (!slab) zTop = DEPTH_I;

		double site = (vs30 >= 600.0) ? c.C1 : (vs30 >= 300.0) ? c.C2 : c.C3;

		double hfac = (zTop < HC) ? 0.0 : min(zTop, MAX_DEPTH) - HC;

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
		double gnd = c.a * Mw + c.b * rRup - Math.log(r) + site;
		gnd = gnd + c.e * hfac + afac;
		return gnd + xmcor - GCOR;

	}

	private static final double calcStdDev(Coeffs c, boolean slab) {
		// Frankel email may 22 2007: use sigt from table 5. Not the
		// reduced-tau sigma associated with mag correction seen in
		// table 6. Zhao says "truth" is somewhere in between.
		return sqrt(c.sigma * c.sigma + (slab ? c.tauS * c.tauS : c.tau * c.tau));
	}

	static final class Interface extends ZhaoEtAl_2006 {
		static final String NAME = ZhaoEtAl_2006.NAME + ": Interface";

		Interface(Imt imt) {
			super(imt);
		}

		@Override final boolean isSlab() {
			return false;
		}
	}

	static final class Slab extends ZhaoEtAl_2006 {
		static final String NAME = ZhaoEtAl_2006.NAME + ": Slab";

		Slab(Imt imt) {
			super(imt);
		}

		@Override final boolean isSlab() {
			return true;
		}
	}

}
