package org.opensha.gmm;

import static org.opensha.gmm.GmmUtils.BASE_10_TO_E;
import static org.opensha.gmm.Imt.PGA;
import static org.opensha.gmm.SiteClass.SOFT_ROCK;

import java.util.Map;

/**
 * Implementation of the Pezeshk, Zandieh, & Tavakoli (2011) ground motion
 * model for stable continental regions. This implementation matches that used
 * in the 2014 USGS NSHMP and uses table lookups (median) and functional forms
 * (sigma) to compute ground motions.
 * 
 * <p><b>Note:</b> Direct instantiation of {@code GroundMotionModel}s is
 * prohibited. Use {@link Gmm#instance(Imt)} to retrieve an instance for a
 * desired {@link Imt}.</p>
 * 
 * <p><b>Implementation note:</b> Mean values are clamped per
 * {@link GmmUtils#ceusMeanClip(Imt, double)}.</p>
 * 
 * <p><b>Reference:</b> Pezeshk, S., Zandieh, A., Tavakoli, B., 2011. Hybrid
 * empirical ground-motion prediction equations for Eastern North America using
 * NGA models and updated seismological parameters: Bulletin of the
 * Seismological Society of America, v. 101, no. 4, p. 1859–1870.</p>
 * 
 * <p><b>Component:</b> GMRotI50 (geometric mean)</p>
 * 
 * @author Peter Powers
 * @see Gmm#PEZESHK_11
 */
public final class PezeshkEtAl_2011 implements GroundMotionModel {

	static final String NAME = "Pezeshk et al. (2011)";
	
	static final CoefficientsNew COEFFS = new CoefficientsNew("P11.csv");

	private static final double SIGMA_FAC = -6.95e-3;

	private static final class Coeffs {
		
		double c12, c13, c14, bcfac;
		
		Coeffs(Map<String, Double> coeffs) {
			c12 = coeffs.get("c12");
			c13 = coeffs.get("c13");
			c14 = coeffs.get("c14");
			bcfac = coeffs.get("bcfac");
		}
	}
	
	private final Coeffs coeffs;
	private final Imt imt;
	private final GmmTable table;
	
	PezeshkEtAl_2011(final Imt imt) {
		this.imt = imt;
		coeffs = new Coeffs(COEFFS.get(imt));
		table = GmmTables.getPezeshk11(imt);
	}

	@Override
	public final ScalarGroundMotion calc(final GmmInput in) {

		double μ = table.get(in.rRup, in.Mw);
		
		// TODO Steve Harmsen has also included SA0P02 along with PGA but
		// comments in fortran from Gail say bcfac scales with distance for PGA
		//
		// This scaling is also applied to P11 in 2014 NSHMP codes
		//
		// TODO I can't find an explicit reference for this formula; it is
		// described in Atkinson (2008) p.1306
		if (GmmUtils.ceusSiteClass(in.vs30) == SOFT_ROCK) {
			if (imt == PGA) {
				μ += - 0.3 + 0.15 * Math.log10(in.rJB);
			} else {
				μ += coeffs.bcfac;
			}
		}
		
		μ = GmmUtils.ceusMeanClip(imt, μ);
		double σ = calcStdDev(coeffs, in.Mw);
		
		return DefaultScalarGroundMotion.create(μ, σ);
	}
	
	private static double calcStdDev(final Coeffs c, final double Mw) {
		double σ = (Mw <= 7.0) ?
			c.c12 * Mw + c.c13 :
			SIGMA_FAC * Mw + c.c14;
		return σ * BASE_10_TO_E;
	}

}
