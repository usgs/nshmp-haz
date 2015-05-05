package org.opensha.gmm;

import static org.opensha.gmm.GmmUtils.BASE_10_TO_E;
import static org.opensha.gmm.Imt.PGA;
import static org.opensha.gmm.SiteClass.SOFT_ROCK;

/**
 * Modified form of the relationship for the Central and Eastern US by Atkinson
 * & Boore (2006). This implementation matches that used in the 2014 USGS NSHMP,
 * incorporates a new, magnitude-dependent stress parameter, and uses table
 * lookups instead of functional forms to compute ground motions. This relation
 * is commonly referred to as AB06 Prime (AB06').
 * 
 * <p><b>Note:</b> Direct instantiation of {@code GroundMotionModel}s is
 * prohibited. Use {@link Gmm#instance(Imt)} to retrieve an instance for a
 * desired {@link Imt}.</p>
 * 
 * <p><b>Implementation note:</b> Mean values are clamped per
 * {@link GmmUtils#ceusMeanClip(Imt, double)}.</p>
 * 
 * <p><b>Reference:</b> Atkinson, G.M., and Boore, D.M., 2006, Earthquake
 * ground- motion prediction equations for eastern North America: Bulletin of
 * the Seismological Society of America, v. 96, p. 2181–2205.</p>
 * 
 * <p><b>Reference:</b> Atkinson, G. M., and Boore, D. M., 2011. Modifications
 * to existing ground-motion prediction equations in light of new data: Bulletin
 * of the Seismological Society of America, v. 101, no. 3, p. 1121–1135.</p>
 * 
 * <p><b>Component:</b> horizontal (not clear from publication)</p>
 * 
 * @author Peter Powers
 * @see Gmm#AB_06_PRIME
 */
public final class AtkinsonBoore_2006p implements GroundMotionModel {

	static final String NAME = "Atkinson & Boore (2006): Prime";

	static final CoefficientContainer COEFFS = new CoefficientContainer("AB06P.csv");

	private static final double SIGMA = 0.3 * BASE_10_TO_E;

	private final double bcfac;
	private final Imt imt;
	private final GmmTable table;

	AtkinsonBoore_2006p(final Imt imt) {
		this.imt = imt;
		bcfac = COEFFS.get(imt, "bcfac");
		table = GmmTables.getAtkinson06(imt);
	}

	@Override public final ScalarGroundMotion calc(final GmmInput in) {

		double μ = table.get(in.rRup, in.Mw);

		// TODO Steve Harmsen has also included SA0P02 along with PGA but
		// comments in fortran from Gail say bcfac scales with distance for PGA

		// TODO I THINK THIS IS MISSING SFAC GFAC CONVERSIONS
		//
		// TODO reference?? I can't find an explicit reference for this formula;
		// it is described in Atkinson (2008) p.1306
		if (GmmUtils.ceusSiteClass(in.vs30) == SOFT_ROCK) {
			if (imt == PGA) {
				μ += -0.3 + 0.15 * Math.log10(in.rJB);
			} else {
				μ += bcfac;
			}
		}

		return DefaultScalarGroundMotion.create(
			GmmUtils.ceusMeanClip(imt, μ), SIGMA);
	}

}
