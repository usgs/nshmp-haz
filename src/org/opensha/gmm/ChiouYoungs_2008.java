package org.opensha.gmm;

import static java.lang.Math.abs;
import static java.lang.Math.cos;
import static java.lang.Math.cosh;
import static java.lang.Math.exp;
import static java.lang.Math.log;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.pow;
import static java.lang.Math.sqrt;
import static java.lang.Math.tanh;
import static org.opensha.geo.GeoTools.TO_RAD;
import static org.opensha.gmm.FaultStyle.NORMAL;
import static org.opensha.gmm.FaultStyle.REVERSE;

import org.opensha.calc.ScalarGroundMotion;

/**
 * Implementation of the Chiou &amp; Youngs (2008) next generation attenuation
 * relationship developed as part of NGA West I.
 * 
 * <p>See: Chiou, B.S.J. and Youngs R.R. (2008), An NGA model for the average
 * horizontal component of peak ground motion and response spectra, Earthquake
 * Spectra, 24(1), 173-215.</p>
 * 
 * <p>Component: GMRotI50 (geometric mean)</p>
 * 
 * @author Peter Powers
 */
final class ChiouYoungs_2008 implements GroundMotionModel {

	static final String NAME = "Chiou \u0026 Youngs (2008)";
	
	static final CoefficientContainer CC = new CoefficientContainer("CY08.csv", Coeffs.class);

	static class Coeffs extends Coefficients {
		double c1, c1a, c1b, c5, c6, c7, c7a, c9, c9a, c10, cg1, cg2, cn, cm,
				phi1, phi2, phi3, phi4, phi5, phi6, phi7, phi8, tau1, tau2,
				sig1, sig2, sig3, sig4;
	}

	// author declared constants
	private static final double C2 = 1.06;
	private static final double C3 = 3.45;
	private static final double C4 = -2.1;
	private static final double C4A = -0.5;
	private static final double CRB = 50;
	private static final double CHM = 3;
	private static final double CG3 = 4;
	
	private final Coeffs coeffs;
	
	ChiouYoungs_2008(IMT imt) {
		coeffs = (Coeffs) CC.get(imt);
	}

	@Override
	public final ScalarGroundMotion calc(GMM_Source props) {
		FaultStyle style = rakeToFaultStyle(props.rake);
		return calc(coeffs, props.Mw, props.rJB, props.rRup, props.rX,
			props.dip, props.zTop, style, props.vs30, props.vsInf,
			props.z1p0);
	}
	
	FaultStyle rakeToFaultStyle(double rake) {
		return GMM_Utils.rakeToFaultStyle_NSHMP(rake);
	}
	
	private static final ScalarGroundMotion calc(Coeffs c, double Mw,
			double rJB, double rRup, double rX, double dip, double zTop,
			FaultStyle style, double vs30, boolean vsInf, double z1p0) {

		// terms used by both mean and stdDev
		double lnYref = calcLnYref(c, Mw, rJB, rRup, rX, dip, zTop, style);
		double soilNonLin = calcSoilNonLin(c, vs30);

		double mean = calcMean(c, vs30, z1p0, soilNonLin, lnYref);
		double stdDev = calcStdDev(c, Mw, vsInf, soilNonLin, lnYref);

		return DefaultScalarGroundMotion.create(mean, stdDev);
	}
	
	// Seismic Source Scaling - aftershock term removed
	private static final double calcLnYref(Coeffs c, double Mw, double rJB,
			double rRup, double rX, double dip, double zTop, FaultStyle style) {
		
		double cosDelta = cos(dip * TO_RAD);
		double rAlt = sqrt(rJB * rJB + zTop * zTop);
		double hw = (rX < 0.0) ? 0.0 : 1.0;
		
		// @formatter:off
		double f_term = (style == REVERSE) ? c.c1a : 
						(style == NORMAL) ? c.c1b : 0.0;
		return c.c1 + (f_term + c.c7 * (zTop - 4.0)) +  // mainshock term [* (1 - AS)]
			// [(c.c10 + c.c7a * (zTop - 4.0)) * AS +]  aftershock term
			C2 * (Mw - 6.0) + ((C2 - C3) / c.cn) * log(1.0 + exp(c.cn * (c.cm - Mw))) +
			C4 * log(rRup + c.c5 * cosh(c.c6 * max(Mw - CHM, 0))) +
			(C4A - C4) * 0.5 * log(rRup * rRup + CRB * CRB) +
			(c.cg1 + c.cg2 / cosh(max(Mw - CG3, 0.0))) * rRup +
			c.c9 * hw * tanh(rX * cosDelta * cosDelta / c.c9a) *
			(1 - rAlt / (rRup + 0.001));
		// @formatter:on
	}

	// Mean ground motion model
	private static final double calcMean(Coeffs c, double vs30, double z1p0,
			double snl, double lnYref) {

		// basin depth
		double zBasin = Double.isNaN(z1p0) ? calcBasinZ(vs30) : z1p0;

		// @formatter:off
		return lnYref + c.phi1 * min(log(vs30 / 1130.0), 0) +
			snl * log((exp(lnYref) + c.phi4) / c.phi4) +
			c.phi5 * (1.0 - 1.0 / cosh(c.phi6 * max(0.0, zBasin - c.phi7))) +
			c.phi8 / cosh(0.15 * max(0.0, zBasin - 15.0));
		// @formatter:on
	}
	
	private static final double calcSoilNonLin(Coeffs c, double vs30) {
		double exp1 = exp(c.phi3 * (min(vs30, 1130.0) - 360.0));
		double exp2 = exp(c.phi3 * (1130.0 - 360.0));
		return c.phi2 * (exp1 - exp2);
	}

	// NSHMP treatment, if vs=760+/-20 -> 40, otherwise compute
	private static final double calcBasinZ(double vs30) {
		if (abs(vs30 - 760.0) < 20.0) return 40.0;
		return exp(28.5 - 3.82 * log(pow(vs30, 8.0) + pow(378.7, 8.0)) / 8.0);
	}

	// Aleatory uncertainty model
	private static final double calcStdDev(Coeffs c, double Mw,
			boolean vsInf, double snl, double lnYref) {

		double Yref = exp(lnYref);

		// Response Term - linear vs. non-linear
		double NL0 = snl * Yref / (Yref + c.phi4);
		
		// Magnitude thresholds
		double mTest = min(max(Mw, 5.0), 7.0) - 5.0;

		// Inter-event Term
		double tau = c.tau1 + (c.tau2 - c.tau1) / 2.0 * mTest;

		// Intra-event term (aftershock removed)
		double sigmaNL0 = c.sig1 + (c.sig2 - c.sig1) / 2.0 * mTest; // [+ c.sig4]
		double vsTerm = vsInf ? c.sig3 : 0.7;
		double NL0sq = (1 + NL0) * (1 + NL0);
		sigmaNL0 *= sqrt(vsTerm + NL0sq);

		// Total model
		return sqrt(tau * tau * NL0sq + sigmaNL0 * sigmaNL0);
	}

	
	public static void main(String[] args) {

		GMM_Source in = GMM_Source.create(6.80, 0.0, 4.629, 5.963, 27.0, 28.0, 2.1, 8.456, 90.0, 760.0, true, Double.NaN, Double.NaN);
		ScalarGroundMotion sgm;
		
		System.out.println("PGA");
		ChiouYoungs_2008 asPGA = new ChiouYoungs_2008(IMT.PGA);
		sgm = asPGA.calc(in);
		System.out.println(sgm);

		System.out.println("5Hz");
		ChiouYoungs_2008 as5Hz = new ChiouYoungs_2008(IMT.SA0P2);
		sgm = as5Hz.calc(in);
		System.out.println(sgm);

		System.out.println("1Hz");
		ChiouYoungs_2008 as1Hz = new ChiouYoungs_2008(IMT.SA1P0);
		sgm = as1Hz.calc(in);
		System.out.println(sgm);

	}

}
