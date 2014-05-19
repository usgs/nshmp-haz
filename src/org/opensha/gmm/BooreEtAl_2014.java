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

import org.opensha.calc.ScalarGroundMotion;

/**
 * Implementation of the Boore, Stewart, Seyhan, &amp; Atkinson (2014) next
 * generation attenuation relationship for active crustal regions developed as
 * part of<a href="http://peer.berkeley.edu/ngawest2">NGA West II</a>.
 * 
 * <p><b>Note:</b> Direct instantiation of {@code GroundMotionModel}s is
 * prohibited. Use {@link GMM#instance(IMT)} to retrieve an instance for a
 * desired {@link IMT}.</p>
 * 
 * <p><b>Reference:</b> Boore, D.M., Stewart, J.P., Seyhan, E., and Atkinson,
 * G.M., 2014, NGA-West 2 equations for predicting PGA, PGV, and 5%-damped PSA
 * for shallow crustal earthquakes, Earthquake Spectra, in press.</p>
 * 
 * <p><b>Component:</b> RotD50 (average horizontal)</p>
 * 
 * @author Peter Powers
 * @see GMM#BSSA_14
 */
public final class BooreEtAl_2014 implements GroundMotionModel {

	static final String NAME = "Boore, Stewart, Seyhan \u0026 Atkinson (2014)";

	static final CoefficientContainer CC = new CoefficientContainer("BSSA14.csv", Coeffs.class);

	static final class Coeffs extends Coefficients {

		double e0, e1, e2, e3, e4, e5, e6, Mh, c1, c2, c3, h, c, Vc, f4, f5,
				f6, f7, R1, R2, dPhiR, dPhiV, phi1, phi2, tau1, tau2;

		// same for all periods; replaced with constant
		double Mref, Rref, Dc3CaTw, Vref, f1, f3, v1, v2;
		// unused regional coeffs
		double Dc3CnTr, Dc3ItJp;
	}

	private static final double A = pow(570.94, 4);
	private static final double B = pow(1360, 4) + A;
	private static final double M_REF = 4.5;
	private static final double R_REF = 1.0;
	private static final double DC3_CA_TW = 0.0;
	private static final double V_REF = 760.0;
	private static final double F1 = 0.0;
	private static final double F3 = 0.1;
	private static final double V1 = 225;
	private static final double V2 = 300;

	private final Coeffs coeffs, coeffsPGA;

	BooreEtAl_2014(IMT imt) {
		coeffs = (Coeffs) CC.get(imt);
		coeffsPGA = (Coeffs) CC.get(PGA);
	}

	// TODO limit supplied z1p0 to 0-3 km
	
	@Override
	public final ScalarGroundMotion calc(GMM_Source props) {
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

		// Source/Event Term -- Equation 2
		double Fe = calcSourceTerm(c, Mw, style);
		
		// Path Term -- Equations 3, 4
		double R = sqrt(rJB * rJB + c.h * c.h);
		double Fp = calcPathTerm(c, Mw, R);

		// Site Linear Term -- Equation 6
		double vsLin = (vs30 <= c.Vc) ? vs30 : c.Vc;
		double lnFlin = c.c * log(vsLin / V_REF);
		
		// Site Nonlinear Term -- Equations 7, 8
		double f2 = c.f4 * (exp(c.f5 * (min(vs30, 760.0) - 360.0)) - 
				exp(c.f5 * (760.0 - 360.0)));
		double lnFnl = F1 + f2 * log((pgaRock + F3) / F3);

		// Basin depth term -- Equations 9, 10 , 11
		double DZ1 = calcDeltaZ1(z1p0, vs30);
		double Fdz1 = (c.imt.isSA() && c.imt.period() >= 0.65) ?
			(DZ1 <= c.f7 / c.f6) ? c.f6 * DZ1 : c.f7
				: 0.0;
		
		// Total site term -- Equation 5
		double Fs = lnFlin + lnFnl + Fdz1;

		// Total model -- Equation 1
		return Fe + Fp + Fs;
	}
	
	// Median PGA for ref rock (Vs30=760m/s); always called with PGA coeffs
	private static final double calcPGArock(Coeffs c, double Mw, double rJB,
			FaultStyle style) {
		
		// Source/Event Term -- Equation 2
		double FePGA = calcSourceTerm(c, Mw, style);
		
		// Path Term -- Equation 3
		double R = sqrt(rJB * rJB + c.h * c.h);
		double FpPGA = calcPathTerm(c, Mw, R);

		// No Site term -- [Vs30rk==760] < [Vc(PGA)=1500] && 
		// ln(Vs30rk / V_REF) = ln(760/760) = 0

		// Total PGA model -- Equation 1
		return exp(FePGA + FpPGA);
	}

	// Source/Event Term -- Equation 2
	private static final double calcSourceTerm(Coeffs c, double Mw,
			FaultStyle style) {
		double Fe = (style == STRIKE_SLIP) ? c.e1 :
					(style == REVERSE) ? c.e3 :
					(style == NORMAL) ? c.e2 : c.e0; // else UNKNOWN
		double MwMh = Mw - c.Mh;
		Fe += (Mw <= c.Mh) ? c.e4 * MwMh + c.e5 * MwMh * MwMh : c.e6 * MwMh;
		return Fe;
	}
	
	// Path Term, base model -- Equation 3
	private static final double calcPathTerm(Coeffs c, double Mw, double R) {
		return (c.c1 + c.c2 * (Mw - M_REF)) * log(R / R_REF) +
			(c.c3 + DC3_CA_TW) * (R - R_REF);
	}
	
	// Calculate delta Z1 in km as a  function of vs30 and using the default 
	// model of ChiouYoungs_2013 -- Equations 10, 11
	private static final double calcDeltaZ1(double z1p0, double vs30) {
		if (Double.isNaN(z1p0)) return 0.0;
		double vsPow4 = vs30 * vs30 * vs30 * vs30;
		return z1p0 - exp(-7.15 / 4.0 * log((vsPow4 + A) / B)) / 1000.0;
	}

	// Aleatory uncertainty model
	private static final double calcStdDev(Coeffs c, double Mw, double rJB,
			double vs30) {

		// Inter-event Term -- Equation 14
		double tau = (Mw >= 5.5) ? c.tau2 : (Mw <= 4.5) ? c.tau1 : c.tau1 +
			(c.tau2 - c.tau1) * (Mw - 4.5);
		
		// Intra-event Term -- Equations 15, 16, 17
		double phiM = (Mw >= 5.5) ? c.phi2 : (Mw <= 4.5) ? c.phi1
			: c.phi1 + (c.phi2 - c.phi1) * (Mw - 4.5);
		
		double phiMR = phiM;
		if (rJB > c.R2) {
			phiMR += c.dPhiR;
		} else if (rJB > c.R1) {
			phiMR += c.dPhiR * (log(rJB / c.R1) / log(c.R2 / c.R1));
		}
		
		double phiMRV = phiMR;
		if (vs30 <= V1) {
			phiMRV -= c.dPhiV;
		} else if (vs30 < V2) {
			phiMRV -= c.dPhiV * (log(V2 / vs30) / log(V2 / V1));
		}

		// Total model -- Equation 13
		return sqrt(phiMRV * phiMRV + tau * tau);
	}

}
