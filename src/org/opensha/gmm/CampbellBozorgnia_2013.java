package org.opensha.gmm;

import static java.lang.Math.cos;
import static java.lang.Math.exp;
import static java.lang.Math.log;
import static java.lang.Math.max;
import static java.lang.Math.pow;
import static java.lang.Math.sqrt;
import static org.opensha.geo.GeoTools.TO_RAD;
import static org.opensha.gmm.FaultStyle.NORMAL;
import static org.opensha.gmm.FaultStyle.REVERSE;
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
final class CampbellBozorgnia_2013 implements GroundMotionModel {

	public static final String NAME = "Campbell \u0026 Bozorgnia (2013)";

	public static final CoefficientContainer CC = new CoefficientContainer(
		"CB13.csv", Coeffs.class);

	static class Coeffs extends Coefficients {
		double c0, c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14,
				c15, c16, c17, c18, c19, c20, a2, h1, h2, h3, h4, h5, h6, k1,
				k2, k3, c, n, phi_lo, phi_hi, tau_lo, tau_hi, phi_lnaf,
				sigma_c, rho;
	}

	private static final Set<IMT> SHORT_PERIODS = EnumSet.range(SA0P01, SA0P25);

	// currently unexposed japan flag
	// in future make this class abstract and add abstract location method
	private static final double JP = 0;

	// TODO inline constance coeffs with final statics
	private final Coeffs coeffs, coeffsPGA;
	
	CampbellBozorgnia_2013(IMT imt) {
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

		double Fmag, Fr, Fflt, Fhw, Fhyp, Fdip, Fsed, Fatn, Fsite;
		
		// Magnitude term -- Equation 2
		if (Mw <= 4.5) {
			Fmag = c.c0 + c.c1 * Mw;
		} else if (Mw <= 5.5) {
			Fmag = c.c0 + c.c1 * Mw + c.c2 * (Mw - 4.5);
		} else if (Mw <= 6.5) {
			Fmag = c.c0 + c.c1 * Mw + c.c2 * (Mw - 4.5) + c.c3 * (Mw - 5.5);
		} else {
			Fmag = c.c0 + c.c1 * Mw + c.c2 * (Mw - 4.5) + c.c3 * (Mw - 5.5) + c.c4 * (Mw - 6.5);
		}

		// Distance term -- Equation 3
		double r = sqrt(rRup * rRup + c.c7 * c.c7);
		Fr = (c.c5 + c.c6 * Mw) * log(r);
	    
		// Style-of-faulting term -- Equations 5 & 6
		double Ff_F = (style == REVERSE) ? c.c8 : (style == NORMAL) ? c.c9 : 0.0;
		if (Mw <= 4.5) {
			Fflt = 0;
		} else if (Mw <= 5.5) {
			Fflt = (Mw - 4.5) * Ff_F;
		} else {
			Fflt = Ff_F;
		}
		
		// Hanging-wall term
		// Jennifer Donahue's HW Model plus CB08 distance taper
		//  -- Equations 9, 10, 11 & 12
		double r1 = width * cos(dip * TO_RAD);
		double r2 = 62.0 * Mw - 350.0;
		double rXr1 = rX / r1;
		double rXr2r1 = (rX - r1) / (r2 - r1);
		double f1_Rx = c.h1 + c.h2 * rXr1 + c.h3 * (rXr1 * rXr1);
		double f2_Rx = c.h4 + c.h5 * (rXr2r1) + c.h6 * rXr2r1 * rXr2r1;
		double Fhw_rRup, Fhw_r, Fhw_m, Fhw_z, Fhw_d;
		
		// CB08 distance taper -- Equation 13
		Fhw_rRup = (rRup == 0.0) ? 1.0 : (rRup - rJB) / rRup;

		// .....distance -- Equation 8
		if (rX < 0.0) {
			Fhw_r = 0.0;
		} else if (rX < r1) {
			Fhw_r = f1_Rx;
		} else {
			Fhw_r = max(f2_Rx, 0.0);
		}
		
		// .....magnitude -- Equation 14
		if (Mw <= 5.5) {
			Fhw_m = 0.0;
		} else if (Mw <= 6.5) {
			Fhw_m = (Mw - 5.5) * (1 + c.a2 * (Mw - 6.5));
		} else {
			Fhw_m = 1.0 + c.a2 * (Mw - 6.5);
		}

		// .....rupture depth -- Equation 15
		Fhw_z = (zTop > 16.66) ? 0.0 : 1.0 - 0.06 * zTop;

		// .....dip -- Equation 16
		Fhw_d = (90.0 - dip) / 45.0;

		// .....total -- Equation 7
		Fhw = c.c10 * Fhw_rRup * Fhw_r * Fhw_m * Fhw_z * Fhw_d;
		
		// update z2p5 with CA model if not supplied -- Eqn 6.9
		if (Double.isNaN(z2p5)) z2p5 = exp(7.089 - 1.144 * log(vs30));
		// Shallow sediment depth and 3-D basin term -- Equation 20
		if (z2p5 <= 1.0) {
			Fsed = (c.c14 + c.c15 * JP) * (z2p5 - 1.0);
		} else if (z2p5 <= 3.0) {
			Fsed = 0.0 * c.c16;
		} else {
			Fsed = c.c16 * c.k3 * exp(-0.75) * (1.0 - exp(-0.25 * (z2p5 - 3.0)));
		}

		// Hypo depth term -- Equations 21, 22 & 23
		Fhyp = (zHyp <= 7.0) ? 0.0 : (zHyp <= 20.0) ? zHyp - 7.0 : 13.0;

		if (Mw <= 5.5) {
			Fhyp *= c.c17;
		} else if (Mw <= 6.5) {
			Fhyp *= (c.c17 + (c.c18 - c.c17) * (Mw - 5.5));
		} else {
			Fhyp *= c.c18;
		}

		// Dip term -- Equation 24
		if (Mw <= 4.5) {
			Fdip = c.c19 * dip;
		} else if (Mw <= 5.5) {
			Fdip = c.c19 * (5.5 - Mw) * dip;
		} else {
			Fdip = 0.0;
		}

		// Anelastic attenuation term -- Equation 25
		Fatn = (rRup > 80.0) ? c.c20 * (rRup-80.0) : 0.0;
		
		// Shallow site response - pgaRock term is computed through an initial
		// call to this method with vs30=1100; 1100 is higher than any k1 value
		// so else condition always prevails -- Equation 18
		double vsk1 = vs30 / c.k1;
		if (vs30 <= c.k1) {
			Fsite = c.c11 * log(vsk1) +
				c.k2 * (log(pgaRock + c.c * pow(vsk1, c.n)) - 
						log(pgaRock + c.c));
		} else {
			Fsite = (c.c11 + c.k2 * c.n) * log(vsk1);
		}
		
		// NOTE Japan ignored  -- Equation 19

		// total model -- Equation 1
		return Fmag + Fr + Fflt + Fhw + Fsite + Fsed + Fhyp + Fdip + Fatn;
		// @formatter:on
	}

	// Aleatory uncertainty model
	private static final double calcStdDev(Coeffs c, Coeffs cPGA, double Mw,
			double vs30, double pgaRock) {

		// @formatter:off

		//  -- Equation 31
		double vsk1 = vs30 / c.k1;
		double alpha = 0.0;
		if (vs30 < c.k1) {
			alpha = c.k2 * pgaRock * (1 / (pgaRock + c.c * pow(vsk1, c.n))
					- 1 / (pgaRock + c.c));
		}
		
		// Magnitude dependence -- Equations 29 & 30
		double tau_lnYB, tau_lnPGAB, phi_lnY, phi_lnPGAB;
		if (Mw <= 4.5) {
			tau_lnYB = c.tau_lo;
			phi_lnY = c.phi_lo;
			tau_lnPGAB = cPGA.tau_lo;
			phi_lnPGAB = cPGA.phi_lo;
		} else if (Mw < 5.5) {
			tau_lnYB = stdMagDep(c.tau_lo, c.tau_hi, Mw);
			phi_lnY = stdMagDep(c.phi_lo, c.phi_hi, Mw);
			tau_lnPGAB = stdMagDep(cPGA.tau_lo, cPGA.tau_hi, Mw);
			phi_lnPGAB = stdMagDep(cPGA.phi_lo, cPGA.phi_hi, Mw);
		} else {
			tau_lnYB = c.tau_hi;
			phi_lnY = c.phi_hi;
			tau_lnPGAB = cPGA.tau_hi;
			phi_lnPGAB = cPGA.phi_hi;
		}
		
		// intra-event std dev -- Equation 27
		double alphaTau = alpha * tau_lnPGAB;
		double tau = sqrt(tau_lnYB * tau_lnYB + alphaTau * alphaTau + 
			2.0 * alpha * c.rho * tau_lnYB * tau_lnPGAB);
		
		// inter-event std dev -- Equation 28
		double phi_lnYB = sqrt(phi_lnY * phi_lnY - c.phi_lnaf * c.phi_lnaf);
		phi_lnPGAB = sqrt(phi_lnPGAB * phi_lnPGAB - cPGA.phi_lnaf * cPGA.phi_lnaf);
		double alphaPhi = alpha * phi_lnPGAB;

		double phi = sqrt(phi_lnY * phi_lnY + alphaPhi * alphaPhi +
			2.0 * alpha * c.rho * phi_lnYB * phi_lnPGAB);
		
		// total model -- Equation 32
		return sqrt(phi * phi + tau * tau);
		// @formatter:on
	}

	private static final double stdMagDep(double lo, double hi, double Mw) {
		return hi + (lo - hi) * (5.5 - Mw);
	}

}
