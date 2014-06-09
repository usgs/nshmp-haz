package org.opensha.gmm;

import static org.opensha.gmm.GMM_Utils.BASE_10_TO_E;
import static org.opensha.gmm.IMT.PGA;
import static org.opensha.gmm.SiteClass.SOFT_ROCK;

import org.opensha.calc.ScalarGroundMotion;

/**
 * Implementation of the Pezeshk, Zandieh, &amp; Tavakoli (2011) ground motion
 * model for stable continental regions. This implementation matches that used
 * in the 2014 USGS NSHMP and uses table lookups (median) and functional forms
 * (sigma) to compute ground motions.
 * 
 * <p><b>Note:</b> Direct instantiation of {@code GroundMotionModel}s is
 * prohibited. Use {@link GMM#instance(IMT)} to retrieve an instance for a
 * desired {@link IMT}.</p>
 * 
 * <p><b>Implementation note:</b> Mean values are clamped per
 * {@link GMM_Utils#ceusMeanClip(IMT, double)}.</p>
 * 
 * <p><b>Reference:</b> Pezeshk, S., Zandieh, A., Tavakoli, B., 2011. Hybrid
 * empirical ground-motion prediction equations for Eastern North America using
 * NGA models and updated seismological parameters: Bulletin of the
 * Seismological Society of America, v. 101, no. 4, p. 1859–1870.</p>
 * 
 * <p><b>Component:</b> GMRotI50 (geometric mean)</p>
 * 
 * @author Peter Powers
 * @see GMM#PEZESHK_11
 */
public final class PezeshkEtAl_2011 implements GroundMotionModel {

	// TODO convert to functional form
	
	static final String NAME = "Pezeshk et al. (2011)";
	
	// period a-to-bc conversion factors and sigma coefficients
	static final CoefficientContainer CC = new CoefficientContainer("P11.csv", Coeffs.class);

	private final GMM_Table table;
	
	// author constants
	private static final double SIGMA_FAC = -6.95e-3;
	
	// implementation constants
	// none

	static class Coeffs extends Coefficients {
		double c12, c13, c14, bcfac;
	}
	
	private final Coeffs coeffs;

	PezeshkEtAl_2011(IMT imt) {
		coeffs = (Coeffs) CC.get(imt);
		table = GMM_Tables.getPezeshk11(imt);
	}

	@Override
	public final ScalarGroundMotion calc(GMM_Input props) {

		double mean = table.get(props.rRup, props.Mw);
		
		// TODO Steve Harmsen has also included SA0P02 along with PGA but
		// comments in fortran from Gail say bcfac scales with distance for PGA
		//
		// This scaling is also applied to P11 in 2014 NSHMP codes
		//
		// TODO I can't find an explicit reference for this formula; it is
		// described in Atkinson (2008) p.1306
		if (GMM_Utils.ceusSiteClass(props.vs30) == SOFT_ROCK) {
			if (coeffs.imt == PGA) {
				mean += - 0.3 + 0.15 * Math.log10(props.rJB);
			} else {
				mean += coeffs.bcfac;
			}
		}
		
		mean = GMM_Utils.ceusMeanClip(coeffs.imt, mean);
		double sigma = calcStdDev(coeffs, props.Mw);
		
		return DefaultScalarGroundMotion.create(mean, sigma);
	}
	
	private static double calcStdDev(Coeffs c, double Mw) {
		double sigma = (Mw <= 7.0) ?
			c.c12 * Mw + c.c13 :
			SIGMA_FAC * Mw + c.c14;
		return sigma * BASE_10_TO_E;
	}

}
