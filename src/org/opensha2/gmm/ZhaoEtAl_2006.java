package org.opensha2.gmm;

import static java.lang.Math.exp;
import static java.lang.Math.log;
import static java.lang.Math.min;
import static java.lang.Math.sqrt;
import static org.opensha2.gmm.GmmInput.Field.MAG;
import static org.opensha2.gmm.GmmInput.Field.RRUP;
import static org.opensha2.gmm.GmmInput.Field.VS30;
import static org.opensha2.gmm.GmmInput.Field.ZTOP;

import java.util.Map;

import org.opensha2.eq.fault.Faults;
import org.opensha2.gmm.GmmInput.Constraints;

import com.google.common.collect.Range;

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
 * specific value (see inline comments for more information).<li><li>Hypocentral
 * depths for interface events are fixed at 20km.</li><li>Hypocentral depths for
 * slab events are set to {@code min(zTop, 125)}; minimum rupture distance
 * (rRup) is 1.0 km.</li></ol></p>
 * 
 * <p><b>Reference:</b> Zhao, J.X., Zhang, J., Asano, A., Ohno, Y., Oouchi, T.,
 * Takahashi, T., Ogawa, H., Irikura, K., Thio, H.K., Somerville, P.G.,
 * Fukushima, Y., and Fukushima, Y., 2006, Attenuation relations of strong
 * ground motion in Japan using site classification based on predominant period:
 * Bulletin of the Seismological Society of America, v. 96, p. 898–913.</p>
 * 
 * <p><b>doi:</b> <a href="http://dx.doi.org/10.1785/0120050122">
 * 10.1785/0120050122</a></p>
 * 
 * <p><b>Component:</b> Geometric mean of two horizontal components</p>
 * 
 * @author Peter Powers
 * @see Gmm#ZHAO_06_INTER
 * @see Gmm#ZHAO_06_SLAB
 */
public abstract class ZhaoEtAl_2006 implements GroundMotionModel {

	static final String NAME = "Zhao et al. (2006)";

	// TODO will probably want to have constraints per-implementation
	static final Constraints CONSTRAINTS = Constraints.builder()
			.set(MAG, Range.closed(5.0, 9.5))
			.set(RRUP, Range.closed(0.0, 1000.0))
			.set(ZTOP, Faults.SLAB_DEPTH_RANGE)
			.set(VS30, Range.closed(150.0, 1000.0))
			.build();
	
	static final CoefficientContainer COEFFS = new CoefficientContainer("Zhao06.csv");

	private static final double HC = 15.0;
	private static final double MC_S = 6.3;
	private static final double MC_I = 6.5;
	private static final double GCOR = 6.88806;
	private static final double MAX_SLAB_DEPTH = 125.0;
	private static final double INTERFACE_DEPTH = 20.0;

	private static final class Coefficients {

		final double a, b, c, d, e, Si, Ss, Ssl, C1, C2, C3, σ, τ, τS, Ps, Qi, Qs, Wi, Ws;

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

	ZhaoEtAl_2006(final Imt imt) {
		coeffs = new Coefficients(imt, COEFFS);
	}

	@Override public final ScalarGroundMotion calc(GmmInput in) {
		double μ = calcMean(coeffs, isSlab(), in);
		double σ = calcStdDev(coeffs, isSlab());
		return DefaultScalarGroundMotion.create(μ, σ);
	}

	abstract boolean isSlab();

	private static final double calcMean(final Coefficients c, final boolean slab, final GmmInput in) {

		double Mw = in.Mw;
		double rRup = Math.max(in.rRup, 1.0); // avoid ln(0) below
		double zTop = slab ? min(in.zTop, MAX_SLAB_DEPTH) : INTERFACE_DEPTH;
		double vs30 = in.vs30;

		double site = (vs30 >= 600.0) ? c.C1 : (vs30 >= 300.0) ? c.C2 : c.C3;

		double hfac = (zTop < HC) ? 0.0 : -HC;

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

	private static final double calcStdDev(final Coefficients c, final boolean slab) {
		// Frankel email may 22 2007: use sigt from table 5. Not the
		// reduced-tau sigma associated with mag correction seen in
		// table 6. Zhao says "truth" is somewhere in between.
		return sqrt(c.σ * c.σ + (slab ? c.τS * c.τS : c.τ * c.τ));
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
