package org.opensha.gmm;

import static java.lang.Math.exp;
import static java.lang.Math.log;
import static java.lang.Math.min;
import static java.lang.Math.pow;
import static java.lang.Math.sqrt;
import static org.opensha.gmm.FaultStyle.NORMAL;
import static org.opensha.gmm.FaultStyle.REVERSE;
import static org.opensha.gmm.FaultStyle.STRIKE_SLIP;
import static org.opensha.gmm.IMT.PGA;
import static org.opensha.gmm.IMT.PGV;

import org.opensha.calc.ScalarGroundMotion;

/**
 * Implementation of the Boore, Stewart, Seyhan, &amp; Atkinson (2013) next
 * generation attenuation relationship developed as part of NGA West II.
 * 
 * <p>See: Boore, D.M., Stewart, J.P., Seyhan, E., and Atkinson, G.M., 2013,
 * NGA-West2 equations for predicting response spectral accelerations for
 * shallow crustal earthquakes, PEER Report 2013/05.</p>
 * 
 * <p>See: <a href="http://peer.berkeley.edu/ngawest2/final-products/">NGA-West2
 * Final Products</a></p>
 * 
 * <p>Component: RotD50 (average horizontal)</p>
 * 
 * @author Peter Powers
 */
final class BooreEtAl_2013 implements GroundMotionModel {

	public static final String NAME = "Boore, Stewart, Seyhan \u0026 Atkinson (2013)";

	public static final CoefficientContainer CC = new CoefficientContainer(
		"BSSA13.csv", Coeffs.class);

	static final class Coeffs extends Coefficients {
		double e0, e1, e2, e3, e4, e5, e6, Mh, c1, c2, c3, Mref, Rref, h,
				Dc3CaTw, Dc3CnTr, Dc3ItJp, c, Vc, Vref, f1, f3, f4, f5, f6, f7,
				R1, R2, dPhiR, dPhiV, V1, V2, phi1, phi2, tau1, tau2;
	}

	// implementation constants
	private static final double A = pow(570.94, 4);
	private static final double B = pow(1360, 4) + A;

	private final Coeffs coeffs, coeffsPGA;

	BooreEtAl_2013(IMT imt) {
		coeffs = (Coeffs) CC.get(imt);
		coeffsPGA = (Coeffs) CC.get(PGA);
	}

	// TODO limit supplied z1p0 to 0-3 km
	
	@Override
	public final ScalarGroundMotion calc(GMM_Input props) {
		FaultStyle style = rakeToFaultStyle(props.rake);
		return calc(coeffs, coeffsPGA, props.Mw, props.rJB, style, props.vs30,
			props.z1p0);
	}

	FaultStyle rakeToFaultStyle(double rake) {
		return GMM_Utils.rakeToFaultStyle_NSHMP(rake);
	}

	private static final ScalarGroundMotion calc(Coeffs c, Coeffs cPGA,
			double Mw, double rJB, FaultStyle style, double vs30, double z1p0) {

		double pgaRock = calcPGArock(cPGA, Mw, rJB, style);
		double mean = calcMean(c, Mw, rJB, vs30, z1p0, style, pgaRock);
		double stdDev = calcStdDev(c, Mw, rJB, vs30);

		return DefaultScalarGroundMotion.create(mean, stdDev);
	}

	// Mean ground motion model
	private static final double calcMean(Coeffs c, double Mw, double rJB,
			double vs30, double z1p0, FaultStyle style, double pgaRock) {

		// Source/Event Term -- Equation 3.5
		double Fe = calcSourceTerm(c, Mw, style);
		
		// Path Term
		double R = sqrt(rJB * rJB + c.h * c.h); // -- Equation 3.4
		// Base model -- Equation 3.3
		double Fpb = calcPathTerm(c, Mw, R);
		// Adjusted path term -- Equation 3.7
		double Fp = Fpb + c.Dc3CaTw * (R - c.Rref);

		// Site Linear Term
		double vsLin = (vs30 <= c.Vc) ? vs30 : c.Vc;
		double lnFlin = c.c * log(vsLin / c.Vref); // -- Equation 3.9
		
		// Site Nonlinear Term
		// -- Equation 3.11
		double f2 = c.f4 * (exp(c.f5 * (min(vs30, 760.0) - 360.0)) - 
				exp(c.f5 * (760.0 - 360.0)));
		// -- Equation 3.10
		double lnFnl = c.f1 + f2 * log((pgaRock + c.f3) / c.f3);
		// Base model -- Equation 3.8
		double Fsb = lnFlin + lnFnl;

		// Basin depth term -- Equations 4.9 and 4.10
		double DZ1 = calcDeltaZ1(z1p0, vs30);
		double Fz1 = 0.0;
		// this implemention matches that in S. Rezaeian's Matlab codes
		// I assume the repeated -9.9 values for f6 and f7 coeffs are
		// appropriate for PGV and just not used (but for some reason repeated
		// through all periods up to 0.65s
		if (c.imt.equals(PGV) || 
				(c.imt.getPeriod() != null && c.imt.getPeriod() >= 0.65)) {
			// -- Equation 3.13
			Fz1 = (DZ1 <= c.f7 / c.f6) ? c.f6 * DZ1 : c.f7;
		}
		double Fs = Fsb + Fz1;

		// Total model
		return Fe + Fp + Fs;
	}
	
	// Median PGA for ref rock (Vs30=760m/s); always called with PGA coeffs
	private static final double calcPGArock(Coeffs c, double Mw, double rJB,
			FaultStyle style) {
		
		// Source/Event Term
		double FePGA = calcSourceTerm(c, Mw, style);
		
		// Path Term
		double R = sqrt(rJB * rJB + c.h * c.h); // -- Equation 3.4
		// Base model -- Equation 3.3
		double FpbPGA = calcPathTerm(c, Mw, R);

		// -- Equation 3.9
		double Vs30rk = 760; // TODO make constant
		double vsPGArk = (Vs30rk <= c.Vc) ? Vs30rk : c.Vc;
		double FsPGA = c.c*log(vsPGArk/c.Vref);

		// Total model -- Equations 3.1 & 3.6
		return exp(FePGA + FpbPGA + FsPGA);
	}

	// Source/Event Term
	private static final double calcSourceTerm(Coeffs c, double Mw,
			FaultStyle style) {
		double Fe = (style == STRIKE_SLIP) ? c.e1 :
					(style == REVERSE) ? c.e3 :
					(style == NORMAL) ? c.e2 : c.e0; // else UNKNOWN
		double MwMh = Mw - c.Mh;
		// -- Equation 3.5a : Equation 3.5b
		Fe += (Mw <= c.Mh) ? c.e4 * MwMh + c.e5 * MwMh * MwMh : c.e6 * MwMh;
		return Fe;
	}
	
	// Path Term, base model -- Equation 3.3
	private static final double calcPathTerm(Coeffs c, double Mw, double R) {
		return (c.c1 + c.c2 * (Mw - c.Mref)) * log(R / c.Rref) + c.c3 *
			(R - c.Rref);
	}
	
	// Calculate delta Z1 in km as a  function of vs30 and using the default 
	// model of ChiouYoungs_2013
	private static final double calcDeltaZ1(double z1p0, double vs30) {
		if (Double.isNaN(z1p0)) return 0.0;
		double vsPow4 = vs30 * vs30 * vs30 * vs30;
		//  -- Equations 4.9a and 4.10
		return z1p0 - exp(-7.15 / 4.0 * log((vsPow4 + A) / B)) / 1000.0;
	}

	// Aleatory uncertainty model
	private static final double calcStdDev(Coeffs c, double Mw, double rJB,
			double vs30) {

		// Inter-event Term -- Equation 4.11
		// (reordered, most Mw will be > 5.5)
		double tau = (Mw >= 5.5) ? c.tau2 : (Mw <= 4.5) ? c.tau1 : c.tau1 +
			(c.tau2 - c.tau1) * (Mw - 4.5);
		
		// Intra-event Term
		
		//  -- Equation 4.12
		double phiM = (Mw >= 5.5) ? c.phi2 : (Mw <= 4.5) ? c.phi1
			: c.phi1 + (c.phi2 - c.phi1) * (Mw - 4.5);
		
		//  -- Equation 4.13
		double phiMR = phiM;
		if (rJB > c.R2) {
			phiMR += c.dPhiR;
		} else if (rJB > c.R1) {
			phiMR += c.dPhiR * (log(rJB / c.R1) / log(c.R2 / c.R1));
		}
		
		//  -- Equation 4.14
		double V1 = 225; // TODO make constant
		double V2 = 300;
		double phiMRV = 0.0;
		if (vs30 >= V2) {
		    phiMRV = phiMR;
		} else if (vs30 >= V1) {
			phiMRV = phiMR - c.dPhiV * (log(V2 / vs30) / log(V2 / V1));
		} else {
		    phiMRV = phiMR - c.dPhiV;
		}

		// Total model -- Equation 3.2
		return sqrt(phiMRV * phiMRV + tau * tau);
	}

}
