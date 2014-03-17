package org.opensha.gmm;

import static org.opensha.gmm.GMM_Utils.BASE_10_TO_E;
import static org.opensha.gmm.MagConverter.NONE;
import static org.opensha.gmm.SiteClass.HARD_ROCK;
import static org.opensha.gmm.SiteClass.SOFT_ROCK;

import org.opensha.calc.ScalarGroundMotion;

/**
 * Implementation of the Frankel et al. (1996) ground motion model for
 * use with the 2008 CEUS NSHMP.
 * 
 * <p>Implementation note: Mean values are clamped per
 * {@link GMM_Utils#ceusMeanClip(IMT, double)}.</p>
 * 
 * <p>See: Frankel, A., Mueller, C., Barnhard, T., Perkins, D., Leyendecker, E.,
 * Dickman, N., Hanson, S., and Hopper, M., 1996, National Seismic Hazard
 * Maps—Documentation June 1996: U.S. Geological Survey Open-File Report 96–532,
 * 110 p.</p>
 * 
 * <p>Component: not specified</p>
 * 
 * @author Peter Powers
 */
class FrankelEtAl_1996 implements GroundMotionModel, ConvertsMag {

	public static final String NAME = "Frankel et al. (1996)";
	
	// only holds period dependent sigma values
	public static final CoefficientContainer CC = new CoefficientContainer(
		"Frankel96.csv", Coeffs.class);
	
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
