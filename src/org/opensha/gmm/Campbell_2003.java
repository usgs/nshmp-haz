package org.opensha.gmm;

import static java.lang.Math.log;
import static java.lang.Math.pow;
import static java.lang.Math.sqrt;
import static org.opensha.gmm.MagConverter.NONE;
import static org.opensha.gmm.SiteClass.HARD_ROCK;

import org.opensha.calc.ScalarGroundMotion;

/**
 * Implementation of the hybrid ground motion model for stable continental
 * regions by Campbell (2003). This implementation matches that used in the 2008
 * USGS NSHMP and comes in two additional magnitude converting (mb to Mw)
 * flavors to support the 2008 central and eastern US model.
 * 
 * <p><b>Note:</b> Direct instantiation of {@code GroundMotionModel}s is
 * prohibited. Use {@link Gmm#instance(Imt)} to retrieve an instance for a
 * desired {@link Imt}.</p>
 * 
 * <p><b>Implementation note:</b> Mean values are clamped per
 * {@link GmmUtils#ceusMeanClip(Imt, double)}.</p>
 * 
 * <p><b>Reference:</b> Campbell, K.W., 2003, Prediction of strong ground motion
 * using the hybrid empirical method and its use in the devel- opment of
 * ground-motion (attenuation) relations in eastern North America: Bulletin of
 * the Seismological Society of America, v. 93, p. 1012–1033.</p>
 * 
 * <p><b>Component:</b> geometric mean of two horizontal components</p>
 * 
 * @author Peter Powers
 * @see Gmm#CAMPBELL_03
 * @see Gmm#CAMPBELL_03_AB
 * @see Gmm#CAMPBELL_03_J
 */
public class Campbell_2003 implements GroundMotionModel, ConvertsMag {

	// notes from original implementation and fortran:
	//
	// some coefficients are labeled differnetly than in paper
	// localCoeff(paperCoeff):
	// c5(c7) c6(c8) c7(c9) c8(c10) c9(c5) c10(c6)
	//
	// c clamp for 2s set to 0 as per Ken Campbell's email of Aug 18 2008.
	
	// TODO fix clamp values (not implemented here yet) to match other CEUS gmms

	static final String NAME = "Campbell (2003)";
	
	static final CoefficientContainer CC = new CoefficientContainer("Campbell03.csv", Coeffs.class);
	
	static class Coeffs extends Coefficients {
		double c1, c1h, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13;
	}
		
	// author declared constants
	private static final double LOG_70 = 4.2484952;
	private static final double LOG_130 = 4.8675345;

	// implementation constants
	// none
	
	private final Coeffs coeffs;

	Campbell_2003(Imt imt) {
		coeffs = (Coeffs) CC.get(imt);
	}
	
	@Override
	public final ScalarGroundMotion calc(GmmInput props) {
		SiteClass siteClass = GmmUtils.ceusSiteClass(props.vs30);
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

		return GmmUtils.ceusMeanClip(c.imt, gnd);
	}

	private static final double calcStdDev(Coeffs c, double Mw) {
		// TODO clean
		// if (magType == LG_PHASE) mag = Utils.mblgToMw(magConvCode, mag);
		return (Mw < 7.16) ? c.c11 + c.c12 * Mw : c.c13;
	}

}
