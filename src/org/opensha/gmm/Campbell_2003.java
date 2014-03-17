package org.opensha.gmm;

import static java.lang.Math.log;
import static java.lang.Math.pow;
import static java.lang.Math.sqrt;
import static org.opensha.gmm.MagConverter.NONE;
import static org.opensha.gmm.SiteClass.HARD_ROCK;

import org.opensha.calc.ScalarGroundMotion;

/**
 * Implementation of the hybrid attenuation relationship for the Central and
 * Eastern US by Campbell (2003). This implementation matches that used in the
 * 2008 USGS NSHMP.
 * 
 * <p>Implementation note: Mean values are clamped per
 * {@link GMM_Utils#ceusMeanClip(IMT, double)}.</p>
 * 
 * <p>See: Campbell, K.W., 2003, Prediction of strong ground motion using the
 * hybrid empirical method and its use in the devel- opment of ground-motion
 * (attenuation) relations in eastern North America: Bulletin of the
 * Seismological Society of America, v. 93, p. 1012–1033.</p>
 * 
 * TODO Verify that Campbell imposes max(dtor,5); he does require rRup; why is
 * depth constrained as such in hazgrid? As with somerville, no depth is imposed
 * in hazFX - make sure 0.01 as PGA is handled corectly; may require change to
 * period = 0.0
 * 
 * <p>Component: geometric mean of two horizontal components</p>
 * 
 * @author Peter Powers
 */
class Campbell_2003 implements GroundMotionModel, ConvertsMag {

	// notes from original implementation and fortran:
	//
	// some coefficients are labeled differnetly than in paper
	// localCoeff(paperCoeff):
	// c5(c7) c6(c8) c7(c9) c8(c10) c9(c5) c10(c6)
	//
	// c clamp for 2s set to 0 as per Ken Campbell's email of Aug 18 2008.
	
	// TODO fix clamp values (not implemented here yet) to match other CEUS gmms

	public static final String NAME = "Campbell (2003)";
	
	public static final CoefficientContainer CC = new CoefficientContainer(
		"Campbell03.csv", Coeffs.class);
	
	static class Coeffs extends Coefficients {
		double c1, c1h, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13;
	}
		
	// author declared constants
	private static final double LOG_70 = 4.2484952;
	private static final double LOG_130 = 4.8675345;

	// implementation constants
	// none
	
	private final Coeffs coeffs;

	Campbell_2003(IMT imt) {
		coeffs = (Coeffs) CC.get(imt);
	}
	
	@Override
	public final ScalarGroundMotion calc(GMM_Source props) {
		SiteClass siteClass = GMM_Utils.ceusSiteClass(props.vs30);
		return DefaultScalarGroundMotion.create(
			calcMean(coeffs, converter().convert(props.Mw), props.rRup,
				siteClass),
			calcStdDev(coeffs, props.Mw));
	}

	@Override
	public MagConverter converter() {
		return NONE;
	}

	private static final double calcMean(Coeffs c, double Mw, double rRup,
			SiteClass siteClass) {
		
		double gnd0 = siteClass == HARD_ROCK ? c.c1h : c.c1;
		// TODO clean (check other CEUS migrations)
		// if (magType == LG_PHASE) mag = Utils.mblgToMw(magConvCode, mag);
		double gndm = gnd0 + c.c2 * Mw + c.c3 * (8.5 - Mw) * (8.5 - Mw);
		double cfac = pow((c.c5 * Math.exp(c.c6 * Mw)), 2);

		double arg = sqrt(rRup * rRup + cfac);
		double fac = 0.0;
		if (rRup > 70.0) fac = c.c7 * (log(rRup) - LOG_70);
		if (rRup > 130.0) fac = fac + c.c8 * (log(rRup) - LOG_130);
		double gnd = gndm + c.c4 * log(arg) + fac + (c.c9 + c.c10 * Mw) * rRup;

		return GMM_Utils.ceusMeanClip(c.imt, gnd);
	}

	private static final double calcStdDev(Coeffs c, double Mw) {
		// TODO clean
		// if (magType == LG_PHASE) mag = Utils.mblgToMw(magConvCode, mag);
		return (Mw < 7.16) ? c.c11 + c.c12 * Mw : c.c13;
	}

	
	public static void main(String[] args) {

		GMM_Source in = GMM_Source.create(6.80, 0.0, 4.629, 5.963, 27.0, 28.0, 2.1, 8.456, 90.0, 760.0, true, Double.NaN, Double.NaN);
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
