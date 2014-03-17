package org.opensha.gmm;

import static java.lang.Math.exp;
import static java.lang.Math.log;
import static java.lang.Math.min;
import static java.lang.Math.sqrt;

import org.opensha.calc.ScalarGroundMotion;

/**
 * Abstract implementation of the subduction ground motion model by Zhao et al.
 * (2006). This implementation matches that used in the USGS NSHM.
 * 
 * <p>This implementation supports both slab and interface type events. In the
 * 2008 NSHMP, the 'interface' form is used with the Cascadia subduction zone
 * models and the 'slab' form is used with gridded 'deep' events in northern
 * California and the Pacific Northwest. Concrete implementations need to
 * implement {@link #isSlab()}.</p>
 * 
 * <p>Implementation notes: <ol><li>When used for interface events, sigma is
 * computed using the generic value of tau, rather than the interface specific
 * value(see inline comments for more information).<li> <li>Hypocentral depths
 * for interface events are fixed at 20km.</li></ol></p>
 * 
 * <p>See: Zhao, J.X., Zhang, J., Asano, A., Ohno, Y., Oouchi, T., Takahashi,
 * T., Ogawa, H., Irikura, K., Thio, H.K., Somerville, P.G., Fukushima, Y., and
 * Fukushima, Y., 2006, Attenuation relations of strong ground motion in Japan
 * using site classification based on predominant period: Bulletin of the
 * Seismological Society of America, v. 96, p. 898–913.</p>
 * 
 * <p>Component: Geometric mean of two horizontal components</p>
 * 
 * @author Peter Powers
 */
abstract class ZhaoEtAl_2006 implements GroundMotionModel {

	static final String NAME = "Zhao et al. (2006)";
	
	public static final CoefficientContainer CC = new CoefficientContainer(
		"Zhao06.csv", Coeffs.class);
	
	static class Coeffs extends Coefficients {
		double T, a, b, c, d, e, Sr, Si, Ss, Ssl, C1, C2, C3, sigma, tau, tauI,
				tauS, Ps, Qi, Qs, Wi, Ws;
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

	ZhaoEtAl_2006(IMT imt) {
		coeffs = (Coeffs) CC.get(imt);
	}
	
	@Override
	public final ScalarGroundMotion calc(GMM_Input props) {
		double mean = calcMean(coeffs, props.Mw, props.rRup, props.zTop, props.vs30, isSlab());
		double sigma = calcStdDev(coeffs, isSlab());
		return DefaultScalarGroundMotion.create(mean, sigma);
	}
	
	abstract boolean isSlab();

	private static final double calcMean(Coeffs c, double Mw, double rRup,
			double zTop, double vs30, boolean slab) {
		
		// TODO implementation should be using zHyp instead. However, the
		// current NSHM uses zTop for gridded/slab sources
		
		if (!slab) zTop = DEPTH_I;
		
		double site = (vs30 >= 600.0) ? c.C1 : (vs30 >= 300.0) ? c.C2 : c.C3;

		double hfac = (zTop < HC) ? 0.0 : min(zTop, MAX_DEPTH) - HC;
		
		double m2 = Mw - (slab ? MC_S : MC_I);

		double afac, xmcor;
		
		if (slab) {
			afac = c.Ssl * log(rRup) + c.Ss;
			xmcor= c.Ps * m2 + c.Qs * m2 * m2 + c.Ws;

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
		return sqrt(c.sigma * c.sigma +
			(slab ? c.tauS * c.tauS : c.tau * c.tau));
	}

	
	public static void main(String[] args) {

		GMM_Input in = GMM_Input.create(6.80, 0.0, 4.629, 5.963, 27.0, 28.0, 2.1, 8.456, 90.0, 760.0, true, Double.NaN, Double.NaN);
		ScalarGroundMotion sgm;
		
		System.out.println("PGA");
		CampbellBozorgnia_2008 asPGA = new CampbellBozorgnia_2008(IMT.PGA);
		sgm = asPGA.calc(in);
		System.out.println(sgm);

		System.out.println("5Hz");
		CampbellBozorgnia_2008 as5Hz = new CampbellBozorgnia_2008(IMT.SA0P2);
		sgm = as5Hz.calc(in);
		System.out.println(sgm);

		System.out.println("1Hz");
		CampbellBozorgnia_2008 as1Hz = new CampbellBozorgnia_2008(IMT.SA1P0);
		sgm = as1Hz.calc(in);
		System.out.println(sgm);
		
	}

}
