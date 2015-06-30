package org.opensha2.gmm;

import static org.opensha2.gmm.GmmUtils.BASE_10_TO_E;
import static org.opensha2.gmm.MagConverter.NONE;
import static org.opensha2.gmm.SiteClass.HARD_ROCK;
import static org.opensha2.gmm.SiteClass.SOFT_ROCK;

import org.opensha2.gmm.GroundMotionTables.GroundMotionTable;

/**
 * Implementation of the Frankel et al. (1996) ground motion model for stable
 * continental regions. This implementation matches that used in the 2008 USGS
 * NSHMP and comes in two additional magnitude converting (mb to Mw) flavors to
 * support the 2008 central and eastern US model.
 * 
 * <p><b>Note:</b> Direct instantiation of {@code GroundMotionModel}s is
 * prohibited. Use {@link Gmm#instance(Imt)} to retrieve an instance for a
 * desired {@link Imt}.</p>
 * 
 * <p><b>Implementation note:</b> Mean values are clamped per
 * {@link GmmUtils#ceusMeanClip(Imt, double)}.</p>
 * 
 * <p><b>Reference:</b> Frankel, A., Mueller, C., Barnhard, T., Perkins, D.,
 * Leyendecker, E., Dickman, N., Hanson, S., and Hopper, M., 1996, National
 * Seismic Hazard Maps—Documentation June 1996: U.S. Geological Survey Open-File
 * Report 96–532, 110 p.</p>
 * 
 * <p><b>Component:</b> not specified</p>
 * 
 * @author Peter Powers
 * @see Gmm#FRANKEL_96
 * @see Gmm#FRANKEL_96_AB
 * @see Gmm#FRANKEL_96_J
 */
public class FrankelEtAl_1996 implements GroundMotionModel, ConvertsMag {

	static final String NAME = "Frankel et al. (1996)";

	static final CoefficientContainer COEFFS = new CoefficientContainer("Frankel96.csv");

	private final double bσ;
	private final Imt imt;
	private final GroundMotionTable bcTable;
	private final GroundMotionTable aTable;

	FrankelEtAl_1996(Imt imt) {
		this.imt = imt;
		bσ = COEFFS.get(imt, "bsigma");
		bcTable = GroundMotionTables.getFrankel96(imt, SOFT_ROCK);
		aTable = GroundMotionTables.getFrankel96(imt, HARD_ROCK);
	}

	@Override public final ScalarGroundMotion calc(GmmInput in) {
		SiteClass siteClass = GmmUtils.ceusSiteClass(in.vs30);
		double Mw = converter().convert(in.Mw);
		double μ = (siteClass == SOFT_ROCK) ? bcTable.get(in.rRup, Mw) : aTable.get(in.rRup, Mw);
		μ = GmmUtils.ceusMeanClip(imt, μ * BASE_10_TO_E);
		double σ = bσ * BASE_10_TO_E;
		return DefaultScalarGroundMotion.create(μ, σ);
	}

	@Override public MagConverter converter() {
		return NONE;
	}

}
