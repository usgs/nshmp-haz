package org.opensha.gmm;

import static org.opensha.gmm.GMM_Utils.BASE_10_TO_E;
import static org.opensha.gmm.MagConverter.NONE;
import static org.opensha.gmm.SiteClass.HARD_ROCK;
import static org.opensha.gmm.SiteClass.SOFT_ROCK;

import org.opensha.calc.ScalarGroundMotion;

/**
 * Implementation of the Frankel et al. (1996) ground motion model for stable
 * continental regions. This implementation matches that used in the 2008 USGS
 * NSHMP and comes in two additional magnitude converting (mb to Mw) flavors to
 * support the 2008 central and eastern US model.
 * 
 * <p><b>Note:</b> Direct instantiation of {@code GroundMotionModel}s is
 * prohibited. Use {@link GMM#instance(IMT)} to retrieve an instance for a
 * desired {@link IMT}.</p>
 * 
 * <p><b>Implementation note:</b> Mean values are clamped per
 * {@link GMM_Utils#ceusMeanClip(IMT, double)}.</p>
 * 
 * <p><b>Reference:</b> Frankel, A., Mueller, C., Barnhard, T., Perkins, D.,
 * Leyendecker, E., Dickman, N., Hanson, S., and Hopper, M., 1996, National
 * Seismic Hazard Maps—Documentation June 1996: U.S. Geological Survey Open-File
 * Report 96–532, 110 p.</p>
 * 
 * <p><b>Component:</b> not specified</p>
 * 
 * @author Peter Powers
 * @see GMM#FRANKEL_96
 * @see GMM#FRANKEL_96_AB
 * @see GMM#FRANKEL_96_J
 */
public class FrankelEtAl_1996 implements GroundMotionModel, ConvertsMag {

	static final String NAME = "Frankel et al. (1996)";
	
	// only holds period dependent sigma values
	static final CoefficientContainer CC = new CoefficientContainer("Frankel96.csv", Coeffs.class);
	
	private final GMM_Table bcTable;
	private final GMM_Table aTable;
	
	static class Coeffs extends Coefficients {
		double bsigma;
	}
	
	private final Coeffs coeffs;

	FrankelEtAl_1996(IMT imt) {
		coeffs = (Coeffs) CC.get(imt);
		bcTable = GMM_Tables.getFrankel96(imt, SOFT_ROCK);
		aTable = GMM_Tables.getFrankel96(imt, HARD_ROCK);
	}

	@Override
	public final ScalarGroundMotion calc(GMM_Source props) {
		SiteClass sc = GMM_Utils.ceusSiteClass(props.vs30);
		double mean = (sc == SOFT_ROCK) ?
			bcTable.get(props.rRup, converter().convert(props.Mw)) :
			aTable.get(props.rRup, converter().convert(props.Mw));
		
		mean = GMM_Utils.ceusMeanClip(coeffs.imt, mean);
		double std = coeffs.bsigma * BASE_10_TO_E;
		return DefaultScalarGroundMotion.create(mean, std);
	}
			
	@Override
	public MagConverter converter() {
		return NONE;
	}

}
