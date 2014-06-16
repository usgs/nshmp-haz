package org.opensha.gmm;

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
 * Implementation of the Chiou &amp; Youngs (2014) next generation attenuation
 * relationship for active crustal regions developed as part of <a
 * href="http://peer.berkeley.edu/ngawest2">NGA West II</a>.
 * 
 * <p><b>Note:</b> Direct instantiation of {@code GroundMotionModel}s is
 * prohibited. Use {@link Gmm#instance(IMT)} to retrieve an instance for a
 * desired {@link IMT}.</p>
 * 
 * <p><b>Implementation note:</b> 0.01s SA values used for PGA.</p>
 * 
 * <p><b>Reference:</b> Chiou, B.S.J. and Youngs, R.R., 2014, Update of the
 * Chiou and Youngs NGA model for the average horizontal component of
 * peak ground motion and response spectra, Earthquake Spectra, in press.</p>
 * 
 * <p><b>Component:</b> RotD50 (average horizontal)</p>
 * 
 * @author Peter Powers
 * @see Gmm#CY_14
 */
public final class ChiouYoungs_2014 implements GroundMotionModel {

	// TODO consider removing odd 0.12 and 0.17s periods

	static final String NAME = "Chiou \u0026 Youngs (2014)";

	static final CoefficientContainer CC = new CoefficientContainer(
		"CY14.csv", Coeffs.class);

	static class Coeffs extends Coefficients {
		
		double c1, c1a, c1b, c1c, c1d, c3, c5, c6, c7, c7b, c8b, c9, c9a, c9b,
				c11b, cn, cM, cHM, cgamma1, cgamma2, cgamma3, phi1, phi2, phi3,
				phi4, phi5, tau1, tau2, sigma1, sigma2, sigma3;
		
		// same for all periods; replaced with constant
		double c2, c4, c4a, c11, cRB, phi6;
		// unused regional and other coeffs
		double c8, c8a, sigma2_JP, gamma_JP_IT, gamma_WN, phi1_JP, phi5_JP,
				phi6_JP;
	}

	private static final double C2 = 1.06;
	private static final double C4 = -2.1;
	private static final double C4A = -0.5;
	private static final double dC4 = C4A - C4;
	private static final double CRB = 50.0;
	private static final double CRBsq = CRB * CRB;
	private static final double C11 = 0.0;
	private static final double PHI6 = 300.0;
	private static final double A = pow(571, 4);
	private static final double B = pow(1360, 4) + A;

	private final Coeffs coeffs;

	ChiouYoungs_2014(IMT imt) {
		coeffs = (Coeffs) CC.get(imt);
	}

	@Override
	public final ScalarGroundMotion calc(GmmInput props) {
		FaultStyle style = rakeToFaultStyle(props.rake);
		return calc(coeffs, props.Mw, props.rJB, props.rRup, props.rX,
			props.dip, props.zTop, style, props.vs30, props.vsInf,
			props.z1p0);
	}
	
	FaultStyle rakeToFaultStyle(double rake) {
		return GmmUtils.rakeToFaultStyle_NSHMP(rake);
	}
	
	private static final ScalarGroundMotion calc(Coeffs c, double Mw,
			double rJB, double rRup, double rX, double dip, double zTop,
			FaultStyle style, double vs30, boolean vsInf, double z1p0) {

		// terms used by both mean and stdDev
		double saRef = calcSAref(c, Mw, rJB, rRup, rX, dip, zTop, style);
		double soilNonLin = calcSoilNonLin(c, vs30);
		
		double mean = calcMean(c, vs30, z1p0, soilNonLin, saRef);
		double stdDev = calcStdDev(c, Mw, vsInf, soilNonLin, saRef);

		return DefaultScalarGroundMotion.create(mean, stdDev);
	}

	// Seismic Source Scaling -- Equation 11
	private static final double calcSAref(Coeffs c, double Mw, double rJB,
			double rRup, double rX, double dip, double zTop, FaultStyle style) {
		
		// Magnitude scaling
		double r1 = c.c1 + C2 * (Mw - 6.0) + ((C2 - c.c3) / c.cn) *
			log(1.0 + exp(c.cn * (c.cM - Mw)));

		// Near-field magnitude and distance scaling
		double r2 = C4 * log(rRup + c.c5 * cosh(c.c6 * max(Mw - c.cHM, 0.0)));

		// Far-field distance scaling
		double gamma = (c.cgamma1 + c.cgamma2 / cosh(max(Mw - c.cgamma3, 0.0)));
		double r3 = dC4 * log(sqrt(rRup * rRup + CRBsq)) + rRup * gamma;

		// Scaling with other source variables
		double coshM = cosh(2 * max(Mw - 4.5, 0));
		double cosDelta = cos(dip * TO_RAD);
		// Center zTop on the zTop-M relation
		double deltaZtop = zTop - calcMwZtop(style, Mw);
		double r4 = (c.c7 + c.c7b / coshM) * deltaZtop + 
				    (C11 + c.c11b / coshM) * cosDelta * cosDelta;
		r4 += (style == REVERSE) ? (c.c1a + c.c1c / coshM) : 
			  (style == NORMAL) ? (c.c1b + c.c1d / coshM) : 0.0; 

		// Hanging-wall effect
		double r5 = 0.0;
		if (rX >= 0.0) {
			r5 = c.c9 * cos(dip * TO_RAD) *
				(c.c9a + (1.0 - c.c9a) * tanh(rX / c.c9b)) *
				(1 - sqrt(rJB * rJB + zTop * zTop) / (rRup + 1.0));
		}

		// Directivity effect (not implemented)
		// cDPP = centered DPP (direct point directivity parameter)
		//double c8 = 0.2154; // corrected from 2.154 12/3/13 per email from Sanaz
		//double c8a = 0.2695;
		//double Mc8 = Mw-c.c8b;
		//double r6 = c8 * exp(-c8a * Mc8 * Mc8) *
		//	max(0.0, 1.0 - max(0, rRup - 40.0) / 30.0) *
		//	min(max(0, Mw - 5.5) / 0.8, 1.0) * cDPP;

		return exp(r1 + r2 + r3 + r4 + r5);
	}
	
	private static final double calcSoilNonLin(Coeffs c, double vs30) {
		double exp1 = exp(c.phi3 * (min(vs30, 1130.0) - 360.0));
		double exp2 = exp(c.phi3 * (1130.0 - 360.0));
		return c.phi2 * (exp1 - exp2);
	}

	// Mean ground motion model -- Equation 12
	private static final double calcMean(Coeffs c, double vs30, double z1p0,
			double snl, double saRef) {

		// Soil effect: linear response
		double sl = c.phi1 * min(log(vs30 / 1130.0), 0.0);

		// Soil effect: nonlinear response (base passed in)
		snl *= log((saRef + c.phi4) / c.phi4);

		// Soil effect: sediment thickness
		double dZ1 = calcDeltaZ1(z1p0, vs30);
		double rkdepth = c.phi5 * (1.0 - exp(-dZ1 / PHI6));

		// total model
		return log(saRef) + sl + snl + rkdepth;
	}

	// Center zTop on the zTop-M relation -- Equations 4, 5
	private static final double calcMwZtop(FaultStyle style, double Mw) {
		double mzTop = 0.0;
		if (style == REVERSE) {
			mzTop = (Mw <= 5.849) ? 2.704 : max(2.704 - 1.226 * (Mw - 5.849), 0);
		} else {
			mzTop = (Mw <= 4.970) ? 2.673 : max(2.673 - 1.136 * (Mw - 4.970), 0);
		}
		return mzTop * mzTop;
	}
	
	// -- Equation 1
	private static final double calcDeltaZ1(double z1p0, double vs30) {
		if (Double.isNaN(z1p0)) return 0.0;
		double vsPow4 = vs30 * vs30 * vs30 * vs30;
		return z1p0 * 1000.0 - exp(-7.15 / 4 * log((vsPow4 + A) / B));
	}

	// Aleatory uncertainty model -- Equation 3.9
	private static final double calcStdDev(Coeffs c, double Mw, boolean vsInf,
			double snl, double saRef) {

		// Response Term - linear vs. non-linear
		double NL0 = snl * saRef / (saRef + c.phi4);

		// Magnitude thresholds
		double mTest = min(max(Mw, 5.0), 6.5) - 5.0;

		// Inter-event Term
		double tau = c.tau1 + (c.tau2 - c.tau1) / 1.5 * mTest;

		// Intra-event term
		double sigmaNL0 = c.sigma1 + (c.sigma2 - c.sigma1) / 1.5 * mTest;
		double vsTerm = vsInf ? c.sigma3 : 0.7;
		double NL0sq = (1 + NL0) * (1 + NL0);
		sigmaNL0 *= sqrt(vsTerm + NL0sq);

		return sqrt(tau * tau * NL0sq + sigmaNL0 * sigmaNL0);
	}

}
