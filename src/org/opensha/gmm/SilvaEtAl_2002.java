package org.opensha.gmm;

import static java.lang.Math.exp;
import static org.opensha.gmm.SiteClass.HARD_ROCK;
import static org.opensha.gmm.MagConverter.NONE;

import org.opensha.calc.ScalarGroundMotion;

/**
 * Implementation of the hard rock attenuation relationship for the Central and
 * Eastern US by Silva et al. (2002). This implementation matches that used in
 * the 2008 USGS NSHMP.
 * 
 * <p>Implementation note: Mean values are clamped per
 * {@link GMM_Utils#ceusMeanClip(IMT, double)}.</p>
 * 
 * <p>See: Silva, W., Gregor, N., and Darragh, R., 2002, Development of hard
 * rock attenuation relations for central and eastern North America, internal
 * report from Pacific Engineering, November 1, 2002,
 * http://www.pacificengineering.org/CEUS/
 * Development%20of%20Regional%20Hard_ABC.pdf</p>
 * 
 * <p>Component: average horizontal (not clear from publication)</p>
 * 
 * @author Peter Powers
 */
class SilvaEtAl_2002 implements GroundMotionModel, ConvertsMag {

	// notes from original implementation and fortran:
	//
	// c1 from c1hr using A->BC factors, 1.74 for 0.1s, 1.72 for 0.3s, 1.58 for 0.5s, and 1.20 for 2s
	// this from A Frankel advice, Mar 14 2007. For 25 hz use PGA amp.
	// For BC at 2.5 hz use interp between .3 and .5. 1.64116 whose log is 0.4953
	//
	// c note very high sigma for longer period SA


	public static final String NAME = "Silva et al. (2002)";
	
	public static final CoefficientContainer CC = new CoefficientContainer(
		"Silva02.csv", Coeffs.class);
	
	static class Coeffs extends Coefficients {
		double c1, c1hr, c2, c4, c6, c7, c10, sigma;
	}
			
	private final Coeffs coeffs;

	SilvaEtAl_2002(IMT imt) {
		coeffs = (Coeffs) CC.get(imt);
	}
	
	@Override
	public final ScalarGroundMotion calc(GMM_Input props) {
		SiteClass siteClass = GMM_Utils.ceusSiteClass(props.vs30);
		return DefaultScalarGroundMotion.create(
			calcMean(coeffs, converter().convert(props.Mw), props.rJB,
				siteClass),	coeffs.sigma);
	}
	
	@Override
	public MagConverter converter() {
		return NONE;
	}

	private static final double calcMean(Coeffs c, double Mw, double rJB,
			SiteClass siteClass) {

		double c1 = (siteClass == HARD_ROCK) ? c.c1hr : c.c1;
		double gnd0 = c1 + c.c2 * Mw + c.c10 * (Mw - 6.0) * (Mw - 6.0);
		double fac = c.c6 + c.c7 * Mw;
		double gnd = gnd0 + fac * Math.log(rJB + exp(c.c4));

		return GMM_Utils.ceusMeanClip(c.imt, gnd);
	}

	
	public static void main(String[] args) {

		GMM_Input in = GMM_Input.create(6.80, 0.0, 4.629, 5.963, 27.0, 28.0, 2.1, 8.456, 90.0, 760.0, true, Double.NaN, Double.NaN);
		ScalarGroundMotion sgm;
		
		System.out.println("PGA");
		CampbellBozorgnia_2008 asPGA = new CampbellBozorgnia_2008(IMT.PGA);
		sgm = asPGA.calc(in);
		System.out.println(sgm);

		System.out.println("5Hz");
		CampbellBozorgnia_2008 as5Hz = new CampbellBozorgnia_2008(IMT.SA0P2);
		sgm = as5Hz.calc(in);
		System.out.println(sgm);

		System.out.println("1Hz");
		CampbellBozorgnia_2008 as1Hz = new CampbellBozorgnia_2008(IMT.SA1P0);
		sgm = as1Hz.calc(in);
		System.out.println(sgm);
		
	}

}
