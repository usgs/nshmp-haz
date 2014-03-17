package org.opensha.gmm;

import static java.lang.Math.exp;
import static java.lang.Math.log;
import static java.lang.Math.sqrt;
import static org.opensha.gmm.SiteClass.HARD_ROCK;

import static org.opensha.gmm.MagConverter.*;

import org.opensha.calc.ScalarGroundMotion;

/**
 * Implementation of mid-continent Toro et al. (1997) attenuation relationship
 * with 2002 updates. This implementation matches that used in the 2008 USGS
 * NSHMP.
 * 
 * <p>Implementation note: Mean values are clamped per
 * {@link GMM_Utils#ceusMeanClip(IMT, double)}.</p>
 * 
 * <p>See: Toro, G.R., 2002, Modification of the Toro et al. (1997) attenuation
 * relations for large magnitudes and short distances: Risk Engineering, Inc.
 * report, http://www.riskeng.com/PDF/atten_toro_extended.pdf. (TODO this
 * link no longer works)</p>
 * 
 * <p>See: Toro, G.R., Abrahamson, N.A., and Schneider, J.F., 1997, A model of
 * strong ground motions from earthquakes in central and eastern North
 * America—Best estimates and uncertain- ties: Seismological Research Letters,
 * v. 68, p. 41–57.</p>
 * 
 * <p>Component: not specified</p>
 * 
 * @author Peter Powers
 */
abstract class ToroEtAl_1997 implements GroundMotionModel {
	
	// TODO fix clamp values (not implemented here yet) to match other CEUS gmms

	// notes from fortran source:
	//
	// added 0.04 and 0.4 s coeffs july 16 2008 (NRC files)
	// (TODO may want to remove these periods - coeff interpolation)
	// I would rather do these on the fly
	//
	// MbLg coeffs. BC/A 2-hz Siteamp = 1.58, with BC-A coef. diff. of 0.4574.
	//
	// Mw coeffs for BC rock. 3hz BC-A is 0.5423 (BC/A siteamp is then 1.72)
	// Mw coeffs. 3.33 hz is log-log from the 2.5 and 5 hz values.
	//
	// Sigma in nat log units. Saves a divide
	// Toro : slightly larger sigma for 1 and 2 s. Toro Lg based mag has
	// larger sigma for larger M (table 3, p 50 ,srl 1997. This isn't
	// in our rendering)

	static final String NAME = "Toro et al. (1997)";
	
	// load both Mw and Mb coefficients
	public static final CoefficientContainer CC = new CoefficientContainer(
		"Toro97Mw.csv", Coeffs.class);
	static final CoefficientContainer CC_MB = new CoefficientContainer(
		"Toro97Mb.csv", Coeffs.class);
	
	static class Coeffs extends Coefficients {
		double t1, t1h, t2, t3, t4, t5, t6, th, tsigma;
	}
	
	// author declared constants
	// none

	// implementation constants
	// none
	
	private final Coeffs coeffs;

	ToroEtAl_1997(IMT imt) {
		coeffs = (Coeffs) (isMw() ? CC.get(imt) : CC_MB.get(imt));
	}
	
	@Override
	public final ScalarGroundMotion calc(GMM_Input props) {
		SiteClass siteClass = GMM_Utils.ceusSiteClass(props.vs30);
		
		
		return DefaultScalarGroundMotion.create(
			calcMean(coeffs, props.Mw, props.rJB, siteClass, isMw()),
			coeffs.tsigma);
	}

	abstract boolean isMw();
	
	private static final double calcMean(Coeffs c, double mag, double rJB,
			SiteClass siteClass, boolean isMw) {
		
		double thsq = c.th * c.th;

		// magnitude correction: with Toro model, you change the coefficients
		// appropriate to the magnitude.
		//
		// New, Nov 2006: the finite-fault correction, affects the fictitious
		// depth or bending point; from Toro Paducah paper. Mod. Dec 2007, mblg
		// to Mw for the correction.

		double mCorr;
		
		if (isMw) {
			mCorr = Math.exp(-1.25 + 0.227 * mag);
		} else {
			double magJ = MB_TO_MW_JOHNSTON.convert(mag);
			double cor1 = exp(-1.25 + 0.227 * magJ);
			double magAB = MB_TO_MW_ATKIN_BOORE.convert( mag);
			double cor2 = exp(-1.25 + 0.227 * magAB);
			mCorr = sqrt(cor1 * cor2); // geo mean
		}

		double dist = Math.sqrt(rJB * rJB + thsq * mCorr * mCorr);

		// default to SOFT_ROCK values
		double gnd = (siteClass == HARD_ROCK) ? c.t1h : c.t1;
		gnd += c.t2 * (mag - 6.0) + c.t3 * ((mag - 6.0) * (mag - 6.0));
		gnd += -c.t4 * log(dist) - c.t6 * dist;

		double factor = log(dist / 100.0);
		if (factor > 0) gnd = gnd - (c.t5 - c.t4) * factor;

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
