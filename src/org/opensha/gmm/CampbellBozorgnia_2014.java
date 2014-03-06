package org.opensha.gmm;

import static java.lang.Math.cos;
import static java.lang.Math.exp;
import static java.lang.Math.log;
import static java.lang.Math.max;
import static java.lang.Math.pow;
import static java.lang.Math.sqrt;
import static org.opensha.geo.GeoTools.TO_RAD;
import static org.opensha.gmm.FaultStyle.NORMAL;
import static org.opensha.gmm.IMT.PGA;
import static org.opensha.gmm.IMT.SA0P01;
import static org.opensha.gmm.IMT.SA0P25;

import java.util.EnumSet;
import java.util.Set;

import org.opensha.calc.ScalarGroundMotion;

/**
 * Implementation of the Campbell &amp; Bozorgnia (2013) next generation
 * attenuation relationship developed as part of NGA West II.
 * 
 * <p>See: Campbell, K.W., and Bozorgnia, Y., 2013, NGA-West2 Campbell-Bozorgnia
 * ground motion model for the horizontal components of PGA, PGV, and 5%-damped
 * elastic pseudo-acceleration response spectra for periods ranging from 0.01 to
 * 10 sec, PEER Report 2013/06.</p>
 * 
 * <p>See: <a href="http://peer.berkeley.edu/ngawest2/final-products/">NGA-West2
 * Final Products</a></p>
 * 
 * <p>Component: RotD50 (average horizontal)</p>
 * 
 * @author Peter Powers
 */
final class CampbellBozorgnia_2014 implements GroundMotionModel {

	// TODO review class javadoc and update citation to EQS

	public static final String NAME = "Campbell \u0026 Bozorgnia (2014)";

	public static final CoefficientContainer CC = new CoefficientContainer(
		"CB14.csv", Coeffs.class);

	static class Coeffs extends Coefficients {
		
		double c0, c1, c2, c3, c4, c5, c6, c7, c9, c10, c11, c12, c13, c14,
				c15, c16, c17, c18, c19, c20, a2, h1, h2, h3, h5, h6, k1, k2,
				k3, phi1, phi2, tau1, tau2, rho;
		
		// same for all periods; replaced with constant; or unused (c8)
		double c8, h4, c, n, phi_lnaf;
		// unused regional and other coeffs
		double Dc20_CA, Dc20_JP, Dc20_CH, phiC;
	}
	
	// implementation constants
	private static final double H4 = 0.0;
	private static final double C = 1.88;
	private static final double N = 1.18;
	private static final double PHI_LNAF_SQ = 0.09; // 0.3^2

	private static final Set<IMT> SHORT_PERIODS = EnumSet.range(SA0P01, SA0P25);

	private final Coeffs coeffs, coeffsPGA;
	
	CampbellBozorgnia_2014(IMT imt) {
		coeffs = (Coeffs) CC.get(imt);
		coeffsPGA = (Coeffs) CC.get(PGA);
	}
	
	@Override
	public final ScalarGroundMotion calc(GMM_Input props) {
		FaultStyle style = rakeToFaultStyle(props.rake);
		return calc(coeffs, coeffsPGA, props.Mw, props.rJB, props.rRup,
			props.rX, props.dip, props.width, props.zTop, props.zHyp, style,
			props.vs30, props.z2p5);
	}

	FaultStyle rakeToFaultStyle(double rake) {
		return GMM_Utils.rakeToFaultStyle_NSHMP(rake);
	}
	
	private static final ScalarGroundMotion calc(Coeffs c, Coeffs cPGA,
			double Mw, double rJB, double rRup, double rX, double dip,
			double width, double zTop, double zHyp, FaultStyle style,
			double vs30, double z2p5) {
		
		// calc pga rock reference value using CA vs30 z2p5 value: 0.398
		double pgaRock = (vs30 < c.k1) ? exp(calcMean(cPGA, Mw, rJB,
			rRup, rX, dip, width, zTop, zHyp, 1100.0, 0.398, style, 0.0))
			: 0.0;

		double mean = calcMean(c, Mw, rJB, rRup, rX, dip, width, zTop,
			zHyp, vs30, z2p5, style, pgaRock);
		
		// prevent SA<PGA for short periods
		if (SHORT_PERIODS.contains(c.imt)) {
			double pgaMean = calcMean(cPGA, Mw, rJB, rRup, rX, dip,
				width, zTop, zHyp, vs30, z2p5, style, pgaRock);
			mean = max(mean, pgaMean);
		}
		
		double stdDev = calcStdDev(c, cPGA, Mw, vs30, pgaRock);

		return DefaultScalarGroundMotion.create(mean, stdDev);
	}
	
	// Mean ground motion model
	private static final double calcMean(Coeffs c, double Mw, double rJB,
			double rRup, double rX, double dip, double width, double zTop,
			double zHyp, double vs30, double z2p5, FaultStyle style,
			double pgaRock) {
		
		// @formatter:off
		
		// Magnitude term -- Equation 2
		double Fmag = c.c0 + c.c1 * Mw;
		if (Mw > 6.5) {
			Fmag += c.c2 * (Mw - 4.5) + c.c3 * (Mw - 5.5) + c.c4 * (Mw - 6.5);
		} else if (Mw > 5.5) {
			Fmag += c.c2 * (Mw - 4.5) + c.c3 * (Mw - 5.5);
		} else if (Mw > 4.5) {
			Fmag += c.c2 * (Mw - 4.5);
		}

		// Distance term -- Equation 3
		double r = sqrt(rRup * rRup + c.c7 * c.c7);
		double Fr = (c.c5 + c.c6 * Mw) * log(r);
	    
		// Style-of-Faulting term -- Equations 4, 5, 6
		// c8 is always 0 so REVERSE switch has been removed
		double Fflt = 0.0;
		if (style == NORMAL && Mw > 4.5) {
			Fflt = c.c9;
			if (Mw <= 5.5) Fflt *= (Mw - 4.5);
		}
		
		// Hanging-Wall term
		double Fhw = 0.0;
		// short-circuit: f4 is 0 if rX < 0, Mw <= 5.5, zTop > 16.66
		// these switches have been removed below
		if (rX >= 0.0 && Mw > 5.5 && zTop <= 16.66) { // short-circuit
		
			// Jennifer Donahue's HW Model plus CB08 distance taper
			//  -- Equations 9, 10, 11 & 12
			double r1 = width * cos(dip * TO_RAD);
			double r2 = 62.0 * Mw - 350.0;
			double rXr1 = rX / r1;
			double rXr2r1 = (rX - r1) / (r2 - r1);
			double f1_rX = c.h1 + c.h2 * rXr1 + c.h3 * (rXr1 * rXr1);
			double f2_rX = H4 + c.h5 * (rXr2r1) + c.h6 * rXr2r1 * rXr2r1;
			
			// ... rX -- Equation 8
			double Fhw_rX = (rX >= r1) ? max(f2_rX, 0.0) : f1_rX;
			
			// ... rRup -- Equation 13
			double Fhw_rRup = (rRup == 0.0) ? 1.0 : (rRup - rJB) / rRup;
	
			// ... magnitude -- Equation 14
			double Fhw_m = 1.0 + c.a2 * (Mw - 6.5);
			if (Mw > 6.5) Fhw_m *= (Mw - 5.5);
	
			// ... depth -- Equation 15
			double Fhw_z = 1.0 - 0.06 * zTop;
	
			// ... dip -- Equation 16
			double Fhw_d = (90.0 - dip) / 45.0;
	
			// ... total -- Equation 7
			Fhw = c.c10 * Fhw_rX * Fhw_rRup * Fhw_m * Fhw_z * Fhw_d;
		}
		
		// Shallow Site Response term - pgaRock term is computed through an
		// initial call to this method with vs30=1100; 1100 is higher than any
		// k1 value so else condition always prevails -- Equation 18
		double vsk1 = vs30 / c.k1;
		double Fsite = (vs30 <= c.k1) ? c.c11 * log(vsk1) + 
			c.k2 * (log(pgaRock + C * pow(vsk1, N)) - log(pgaRock + C)) :
				(c.c11 + c.k2 * N) * log(vsk1);
		
		// Basin Response term  -- Equation 20
		// update z2p5 with CA model if not supplied -- Equation 33
		if (Double.isNaN(z2p5)) z2p5 = exp(7.089 - 1.144 * log(vs30));
		double Fsed = 0.0;
		if (z2p5 <= 1.0) {
			Fsed = c.c14 * (z2p5 - 1.0);
		} else if (z2p5 > 3.0) {
			Fsed = c.c16 * c.k3 * exp(-0.75) * (1.0 - exp(-0.25 * (z2p5 - 3.0)));
		}

		// Hypocentral Depth term -- Equations 21, 22, 23
		double Fhyp = (zHyp <= 7.0) ? 0.0 : (zHyp <= 20.0) ? zHyp - 7.0 : 13.0;
		if (Mw <= 5.5) {
			Fhyp *= c.c17;
		} else if (Mw <= 6.5) {
			Fhyp *= (c.c17 + (c.c18 - c.c17) * (Mw - 5.5));
		} else {
			Fhyp *= c.c18;
		}

		// Fault Dip term -- Equation 24
		double Fdip = (Mw > 5.5) ? 0.0 :
					  (Mw > 4.5) ? c.c19 * (5.5 - Mw) * dip :
			        	  c.c19 * dip;
		
		// Anelastic Attenuation term -- Equation 25
		double Fatn = (rRup > 80.0) ? c.c20 * (rRup - 80.0) : 0.0;
		
		// total model -- Equation 1
		return Fmag + Fr + Fflt + Fhw + Fsite + Fsed + Fhyp + Fdip + Fatn;
	}

	// Aleatory uncertainty model
	private static final double calcStdDev(Coeffs c, Coeffs cPGA, double Mw,
			double vs30, double pgaRock) {

		//  -- Equation 31
		double vsk1 = vs30 / c.k1;
		double alpha = (vs30 < c.k1) ? c.k2 * pgaRock * 
			(1 / (pgaRock + C * pow(vsk1, N)) - 1 / (pgaRock + C)) : 0.0;
		
		// Magnitude dependence -- Equations 29 & 30
		double tau_lnYB, tau_lnPGAB, phi_lnY, phi_lnPGAB;
		if (Mw <= 4.5) {
			tau_lnYB = c.tau1;
			phi_lnY = c.phi1;
			tau_lnPGAB = cPGA.tau1;
			phi_lnPGAB = cPGA.phi1;
		} else if (Mw < 5.5) {
			tau_lnYB = stdMagDep(c.tau1, c.tau2, Mw);
			phi_lnY = stdMagDep(c.phi1, c.phi2, Mw);
			tau_lnPGAB = stdMagDep(cPGA.tau1, cPGA.tau2, Mw);
			phi_lnPGAB = stdMagDep(cPGA.phi1, cPGA.phi2, Mw);
		} else {
			tau_lnYB = c.tau2;
			phi_lnY = c.phi2;
			tau_lnPGAB = cPGA.tau2;
			phi_lnPGAB = cPGA.phi2;
		}
		
		// intra-event std dev -- Equation 27
		double alphaTau = alpha * tau_lnPGAB;
		double tauSq = tau_lnYB * tau_lnYB + alphaTau * alphaTau + 
			2.0 * alpha * c.rho * tau_lnYB * tau_lnPGAB;
		
		// inter-event std dev -- Equation 28
		double phi_lnYB = sqrt(phi_lnY * phi_lnY - PHI_LNAF_SQ);
		phi_lnPGAB = sqrt(phi_lnPGAB * phi_lnPGAB - PHI_LNAF_SQ);
		double aPhi_lnPGAB = alpha * phi_lnPGAB;

		// phi_lnaf terms in eqn. 30 cancel when expanded leaving phi_lnY only
		double phiSq = phi_lnY * phi_lnY + aPhi_lnPGAB * aPhi_lnPGAB +
			2.0 * c.rho * phi_lnYB * aPhi_lnPGAB;
		
		// total model -- Equation 32
		return sqrt(phiSq + tauSq);
		// @formatter:on
	}

	private static final double stdMagDep(double lo, double hi, double Mw) {
		return hi + (lo - hi) * (5.5 - Mw);
	}

}
