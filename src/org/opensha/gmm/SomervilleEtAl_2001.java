package org.opensha.gmm;

import static java.lang.Math.log;
import static org.opensha.gmm.SiteClass.HARD_ROCK;
import static org.opensha.util.MathUtils.hypot;

import org.opensha.calc.ScalarGroundMotion;

/**
 * Implementation of the hard rock attenuation relationship for the Central and
 * Eastern US by Somerville et al. (2001). This implementation matches that used
 * in the 2008 USGS NSHMP and is only used for fault sources and gridded
 * representation of faults (e.g. Charleston).
 * 
 * <p><b>Note:</b> Direct instantiation of {@code GroundMotionModel}s is
 * prohibited. Use {@link Gmm#instance(IMT)} to retrieve an instance for a
 * desired {@link IMT}.</p>
 * 
 * <p><b>Implementation note:</b> Mean values are clamped per
 * {@link GMM_Utils#ceusMeanClip(IMT, double)}.</p>
 * 
 * <p><b>Reference:</b> Somerville, P., Collins, N., Abrahamson, N., Graves, R.,
 * and Saikia, C., 2001, Ground motion attenuation relations for the Central and
 * Eastern United States — Final report, June 30, 2001: Report to U.S.
 * Geological Survey for award 99HQGR0098, 38 p.</p>
 * 
 * <p><b>Component:</b> not specified</p>
 * 
 * @author Peter Powers
 * @see Gmm#SOMERVILLE_01
 */
public final class SomervilleEtAl_2001 implements GroundMotionModel {
	
//	 * TODO check doc that distance is rjb
//	 * 		verify that Somerville imposes dtor of 6.0:
//	 * 		THIS IS NOT IMPOSED IN HAZFX
//	 * 		e.g. double dist = Math.sqrt(rjb * rjb + 6.0 * 6.0);

	static final String NAME = "Somerville et al. (2001)";
	
	static final CoefficientContainer CC = new CoefficientContainer("Somerville01.csv",
		Coeffs.class);
	
	static class Coeffs extends Coefficients {
		double a1, a1h, a2, a3, a4, a5, a6, a7, sig0;
	}
	
	// author declared constants
	// none

	// implementation constants
	private static final double Z_MIN = 6.0;
	private static final double R_CUT = 50.0; // km
	private static final double R1 = hypot(R_CUT, Z_MIN);
	
	private final Coeffs coeffs;

	SomervilleEtAl_2001(IMT imt) {
		coeffs = (Coeffs) CC.get(imt);
	}
	
	@Override
	public final ScalarGroundMotion calc(GmmInput props) {
		SiteClass siteClass = GMM_Utils.ceusSiteClass(props.vs30);
		return DefaultScalarGroundMotion.create(
			calcMean(coeffs, props.Mw, props.rJB, siteClass),
			coeffs.sig0);
	}

	private static final double calcMean(Coeffs c, double Mw, double rJB,
			SiteClass siteClass) {
		
		double gnd = (siteClass == HARD_ROCK) ? c.a1h : c.a1;
		gnd += c.a2 * (Mw - 6.4) + c.a7 * (8.5 - Mw) * (8.5 - Mw);

		// Somerville fixes depth at 6km - faults and gridded
		double R = hypot(rJB, Z_MIN);
		
		gnd += c.a3 * log(R) + c.a4 * (Mw - 6.4) * log(R) + c.a5 * rJB;
		if (rJB >= R_CUT) gnd += c.a6 * (log(R) - log(R1));

		return GMM_Utils.ceusMeanClip(c.imt, gnd);
	}
	
}
