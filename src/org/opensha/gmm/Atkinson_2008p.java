package org.opensha.gmm;

import static org.opensha.gmm.GmmUtils.BASE_10_TO_E;
import static org.opensha.gmm.Imt.PGA;
import static org.opensha.gmm.SiteClass.SOFT_ROCK;

/**
 * Modified form of the relationship for the Central and Eastern US by Atkinson
 * (2008). This implementation matches that used in the 2014 USGS NSHMP and uses
 * table lookups instead of functional forms to compute ground motions. This
 * relation is commonly referred to as A08 Prime (A08').
 * 
 * <p><b>Note:</b> Direct instantiation of {@code GroundMotionModel}s is
 * prohibited. Use {@link Gmm#instance(Imt)} to retrieve an instance for a
 * desired {@link Imt}.</p>
 * 
 * <p><b>Implementation note:</b> Mean values are clamped per
 * {@link GmmUtils#ceusMeanClip(Imt, double)}.</p>
 * 
 * <p><b>Reference:</b> Atkinson, G.M., 2008, Ground-motion prediction equations
 * for eastern North America from a referenced empirical approach—Implications
 * for epistemic uncertainty: Bulletin of the Seismological Society of America,
 * v. 98, no. 3, p. 1304–1318.</p>
 * 
 * <p><b>Reference:</b> Atkinson, G. M., and Boore, D. M., 2011. Modifications
 * to existing ground-motion prediction equations in light of new data: Bulletin
 * of the Seismological Society of America, v. 101, no. 3, p. 1121–1135.</p>
 * 
 * <p><b>Component:</b> horizontal (not clear from publication)</p>
 * 
 * @author Peter Powers
 * @see Gmm#ATKINSON_08_PRIME
 */
public final class Atkinson_2008p implements GroundMotionModel {

	static final String NAME = "Atkinson (2008) Prime";

	static final CoefficientsNew COEFFS = new CoefficientsNew("AB08P.csv");

	// implementation constants
	private static final double SIGMA = 0.3 * BASE_10_TO_E;

	private final double bcfac;
	private final Imt imt;
	private final GmmTable table;

	Atkinson_2008p(final Imt imt) {
		this.imt = imt;
		bcfac = COEFFS.get(imt, "bcfac");
		table = GmmTables.getAtkinson08(imt);
	}

	@Override public final ScalarGroundMotion calc(final GmmInput in) {

		double mean = table.get(in.rJB, in.Mw);

		// TODO Steve Harmsen has also included SA0P02 along with PGA but
		// comments in fortran from Gail say bcfac scales with distance for PGA
		//
		// TODO I THINK THIS IS MISSING SFAC GFAC CONVERSIONS

		// TODO I can't find an explicit reference for this formula; it is
		// described in Atkinson (2008) p.1306
		if (GmmUtils.ceusSiteClass(in.vs30) == SOFT_ROCK) {
			if (imt == PGA) {
				mean += -0.3 + 0.15 * Math.log10(in.rJB);
			} else {
				mean += bcfac;
			}
		}

		return DefaultScalarGroundMotion.create(GmmUtils.ceusMeanClip(imt, mean), SIGMA);
	}

}
