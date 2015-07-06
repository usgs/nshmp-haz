package org.opensha2.gmm;

import static org.opensha2.gmm.GmmInput.Field.MAG;
import static org.opensha2.gmm.GmmInput.Field.RRUP;
import static org.opensha2.gmm.GmmInput.Field.VS30;
import static org.opensha2.gmm.GmmUtils.BASE_10_TO_E;
import static org.opensha2.gmm.GmmUtils.atkinsonTableValue;

import org.opensha2.gmm.GmmInput.Constraints;
import org.opensha2.gmm.GroundMotionTables.GroundMotionTable;

import com.google.common.collect.Range;

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
 * ground-motion prediction equations for eastern North America: Bulletin of the
 * Seismological Society of America, v. 96, p. 2181–2205.</p>
 * 
 * <p><b>doi:</b> <a href="http://dx.doi.org/10.1785/0120050245">
 * 10.1785/0120050245</a></p>
 * 
 * <p><b>Reference:</b> Atkinson, G.M., and Boore, D.M., 2011, Modifications to
 * existing ground-motion prediction equations in light of new data: Bulletin of
 * the Seismological Society of America, v. 101, n. 3, p. 1121–1135.</p>
 * 
 * <p><b>doi:</b> <a href="http://dx.doi.org/10.1785/0120100270">
 * 10.1785/0120100270</a></p>
 * 
 * <p><b>Component:</b> horizontal (not clear from publication)</p>
 * 
 * @author Peter Powers
 * @see Gmm#AB_06_PRIME
 */
public final class AtkinsonBoore_2006p implements GroundMotionModel {

	static final String NAME = "Atkinson & Boore (2006): Prime";

	static final Constraints CONSTRAINTS = GmmInput.constraintsBuilder()
		.set(MAG, Range.closed(4.0, 8.0))
		.set(RRUP, Range.closed(0.0, 1000.0))
		.set(VS30, Range.closed(760.0, 2000.0))
		.build();

	static final CoefficientContainer COEFFS = new CoefficientContainer("AB06P.csv");

	private static final double SIGMA = 0.3 * BASE_10_TO_E;

	private final double bcfac;
	private final Imt imt;
	private final GroundMotionTable table;

	AtkinsonBoore_2006p(final Imt imt) {
		this.imt = imt;
		bcfac = COEFFS.get(imt, "bcfac");
		table = GroundMotionTables.getAtkinson06(imt);
	}

	@Override public final ScalarGroundMotion calc(final GmmInput in) {
		double r = Math.max(in.rRup, 1.8);
		double μ = atkinsonTableValue(table, imt, in.Mw, r, in.vs30, bcfac);
		return DefaultScalarGroundMotion.create(GmmUtils.ceusMeanClip(imt, μ), SIGMA);
	}

	// TODO clean
	public static void main(String[] args) {
		AtkinsonBoore_2006p gmm = new AtkinsonBoore_2006p(Imt.PGA);
		double m = atkinsonTableValue(gmm.table, Imt.PGA, 3.5, 4.0, 760.0, gmm.bcfac);
		System.out.println(m);
		System.out.println(Math.exp(m));
		double clipped = GmmUtils.ceusMeanClip(Imt.PGA, m);
		System.out.println(clipped);
		System.out.println(Math.exp(clipped));

	}

}
