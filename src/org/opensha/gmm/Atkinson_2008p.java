package org.opensha.gmm;

import static org.opensha.gmm.GMM_Utils.BASE_10_TO_E;
import static org.opensha.gmm.IMT.PGA;
import static org.opensha.gmm.SiteClass.SOFT_ROCK;

import org.opensha.calc.ScalarGroundMotion;

/**
 * Modified form of the relationship for the Central and Eastern US by Atkinson
 * (2008). This implementation matches that used in the 2014 USGS NSHMP and uses
 * table lookups instead of functional forms to compute ground motions. This
 * relation is commonly referred to as A08 Prime (A08').
 * 
 * <p>Implementation note: Mean values are clamped per
 * {@link GMM_Utils#ceusMeanClip(IMT, double)}.</p>
 * 
 * <p>See: Atkinson, G.M., 2008, Ground-motion prediction equations for eastern
 * North America from a referenced empirical approach—Implications for epistemic
 * uncertainty: Bulletin of the Seismological Society of America, v. 98, no. 3,
 * p. 1304–1318.</p>
 * 
 * <p>See: Atkinson, G. M., and Boore, D. M., 2011. Modifications to existing
 * ground-motion prediction equations in light of new data: Bulletin of the
 * Seismological Society of America, v. 101, no. 3, p. 1121–1135.</p>
 * 
 * <p>Component: horizontal (not clear from publication)</p>
 * 
 * @author Peter Powers
 */
final class Atkinson_2008p implements GroundMotionModel {

	// TODO convert to functional form
	
	public static final String NAME = "Atkinson (2008) Prime";
	
	// only includes periods and a-to-bc conversion factors
	public static final CoefficientContainer CC = new CoefficientContainer(
		"AB08P.csv", Coeffs.class);

	private final GMM_Table table;
	
	// implementation constants
	private static final double SIGMA = 0.3 * BASE_10_TO_E;

	static class Coeffs extends Coefficients {
		double bcfac;
	}
	
	private final Coeffs coeffs;

	Atkinson_2008p(IMT imt) {
		coeffs = (Coeffs) CC.get(imt);
		table = GMM_Tables.getAtkinson08(imt);
	}

	@Override
	public final ScalarGroundMotion calc(GMM_Source props) {

		double mean = table.get(props.rJB, props.Mw);
		
		// TODO Steve Harmsen has also included SA0P02 along with PGA but
		// comments in fortran from Gail say bcfac scales with distance for PGA
		//
		// TODO I THINK THIS IS MISSING SFAC GFAC CONVERSIONS

		// TODO I can't find an explicit reference for this formula; it is
		// described in Atkinson (2008) p.1306
		if (GMM_Utils.ceusSiteClass(props.vs30) == SOFT_ROCK) {
			if (coeffs.imt == PGA) {
				mean += - 0.3 + 0.15 * Math.log10(props.rJB);
			} else {
				mean += coeffs.bcfac;
			}
		}
		
		return DefaultScalarGroundMotion.create(
			GMM_Utils.ceusMeanClip(coeffs.imt, mean), SIGMA);
	}
			
	

	public static void main(String[] args) {
				
//		GMM_Source in = GMM_Source.create(6.80, 0.0, 4.629, 5.963, 27.0, 28.0, 2.1, 8.456, 90.0, 760.0, true, Double.NaN, Double.NaN);
//		ScalarGroundMotion sgm;
//		
//		System.out.println("PGA");
//		Idriss_2013 asPGA = new Idriss_2013(IMT.PGA);
//		sgm = asPGA.calc(in);
//		System.out.println(sgm);
//
//		System.out.println("5Hz");
//		Idriss_2013 as5Hz = new Idriss_2013(IMT.SA0P2);
//		sgm = as5Hz.calc(in);
//		System.out.println(sgm);
//
//		System.out.println("1Hz");
//		Idriss_2013 as1Hz = new Idriss_2013(IMT.SA1P0);
//		sgm = as1Hz.calc(in);
//		System.out.println(sgm);
	}

}
