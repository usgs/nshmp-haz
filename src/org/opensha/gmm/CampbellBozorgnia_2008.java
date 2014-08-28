package org.opensha.gmm;

import static java.lang.Math.exp;
import static java.lang.Math.log;
import static java.lang.Math.max;
import static java.lang.Math.pow;
import static java.lang.Math.sqrt;
import static org.opensha.gmm.FaultStyle.NORMAL;
import static org.opensha.gmm.FaultStyle.REVERSE;
import static org.opensha.gmm.Imt.PGA;
import static org.opensha.gmm.Imt.SA0P01;
import static org.opensha.gmm.Imt.SA0P25;

import java.util.EnumSet;
import java.util.Set;

import org.opensha.calc.ScalarGroundMotion;

/**
 * Implementation of the Campbell &amp; Bozorgnia (2008) next generation
 * attenuation for active crustal regions relationship developed as part of <a
 * href="http://peer.berkeley.edu/ngawest/">NGA West I</a>.
 * 
 * <p><b>Note:</b> Direct instantiation of {@code GroundMotionModel}s is
 * prohibited. Use {@link Gmm#instance(Imt)} to retrieve an instance for a
 * desired {@link Imt}.</p>
 * 
 * <p><b>Reference:</b> NGA Ground Motion Model for the Geometric Mean
 * Horizontal Component of PGA, PGV, PGD and 5% Damped Linear Elastic Response
 * Spectra for Periods Ranging from 0.01 to 10 s, Earthquake Spectra, Volume 24,
 * Number 1, pp. 139-171.</p>
 * 
 * <p><b>Component:</b> GMRotI50 (geometric mean)</p>
 * 
 * @author Peter Powers
 * @see Gmm#CB_08
 */
public final class CampbellBozorgnia_2008 implements GroundMotionModel {

	static final String NAME = "Campbell & Bozorgnia (2008)";
	
	static final CoefficientContainer CC = new CoefficientContainer("CB08.csv", Coeffs.class);
	
	static class Coeffs extends Coefficients {
		double c0, c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, k1, k2,
				k3, s_lny, t_lny, s_c, rho;
	}
	
	private static final Set<Imt> SHORT_PERIODS = EnumSet.range(SA0P01, SA0P25);

	// author declared constants
	private static final double S_lnAF = 0.3;
	private static final double N = 1.18;
	private static final double C = 1.88;
	
	// implementation constants
	private static final double S_lnAFsq = S_lnAF * S_lnAF;

	private final Coeffs coeffs, coeffsPGA;

	CampbellBozorgnia_2008(Imt imt) {
		coeffs = (Coeffs) CC.get(imt);
		coeffsPGA = (Coeffs) CC.get(PGA);
	}
	
	@Override
	public final ScalarGroundMotion calc(GmmInput props) {
		FaultStyle style = rakeToFaultStyle(props.rake);
		return calc(coeffs, coeffsPGA, props.Mw, props.rJB, props.rRup,
			props.dip, props.zTop, style, props.vs30, props.z2p5);
	}

	FaultStyle rakeToFaultStyle(double rake) {
		return GmmUtils.rakeToFaultStyle_NSHMP(rake);
	}

	private static final ScalarGroundMotion calc(Coeffs c, Coeffs cPGA,
			double Mw, double rJB, double rRup, double dip, double zTop,
			FaultStyle style, double vs30, double z2p5) {

		double pgaRock = (vs30 < c.k1) ? exp(calcMean(cPGA, Mw, rJB,
			rRup, dip, zTop, style, 1100.0, z2p5, 0.0)) : 0.0;

		double mean = calcMean(c, Mw, rJB, rRup, dip, zTop, style, vs30, z2p5,
			pgaRock);
		
		// prevent SA<PGA for short periods
		if (SHORT_PERIODS.contains(c.imt)) {
			double pgaMean = calcMean(cPGA, Mw, rJB, rRup, dip, zTop,
				style, vs30, z2p5, pgaRock);
			mean = max(mean, pgaMean);
		}
		double stdDev = calcStdDev(c, cPGA, vs30, pgaRock);

		return DefaultScalarGroundMotion.create(mean, stdDev);
	}
	
	// Mean ground motion model
	private static final double calcMean(Coeffs c, double Mw, double rJB,
			double rRup, double dip, double zTop, FaultStyle style,
			double vs30, double z2p5, double pga_rock) {
		
		// @formatter:off
		double fmag, fdis, fflt, fhng, fsite, fsed;
				
		// Magnitude Term
		fmag = c.c0 + c.c1 * Mw;
		if (Mw > 5.5) fmag += c.c2 * (Mw - 5.5);
		if (Mw > 6.5) fmag += c.c3 * (Mw - 6.5);

		// Source to Site Term
		fdis = (c.c4 + c.c5 * Mw) * log(sqrt(rRup * rRup + c.c6 * c.c6));

		// Fault-style Term
		fflt = (style == REVERSE) ? c.c7 *  ((zTop < 1.0) ? zTop : 1.0) : 
			   (style == NORMAL) ? c.c8 : 0.0;

		// Hanging-wall Term
		double fhngr, fhngm, fhngz, fhngd;
		// .....distance
		if (rJB == 0.0) {
			fhngr = 1.0;
		} else if (zTop < 1 && rJB > 0.0) {
			double rJBsq1 = sqrt(rJB * rJB + 1.0);
			fhngr = (max(rRup, rJBsq1) - rJB) / max(rRup, rJBsq1);
		} else {
			fhngr = (rRup - rJB) / rRup;
		}
		// .....magnitude
		if (Mw <= 6.0) {
			fhngm = 0.0;
		} else if (Mw < 6.5) {
			fhngm = 2.0 * (Mw - 6.0);
		} else {
			fhngm = 1.0;
		}
		// .....rupture depth
		fhngz = (zTop >= 20.0) ? 0.0 : (20.0 - zTop) / 20.0;
		// .....rupture dip
		fhngd = (dip <= 70.0) ? 1.0 : (90.0 - dip) / 20.0;
		// .....total
		fhng = c.c9 * fhngr * fhngm * fhngz * fhngd;

		// Site Term - linear and non-linear
		double vsk1 = vs30 / c.k1;
		if (vs30 < c.k1) {
			fsite = c.c10 * log(vsk1) + c.k2 * (log(pga_rock + C * pow(vsk1, N)) - log(pga_rock + C));
		} else if (vs30 < 1100.0) {
			fsite = (c.c10 + c.k2 * N) * Math.log(vsk1);
		} else {
			fsite = (c.c10 + c.k2 * N) * Math.log(1100.0 / c.k1);
		}
		
		// update z2p5 if not supplied
		if (Double.isNaN(z2p5)) z2p5 = (vs30 <= 2500) ? 2.0 : 0.0;
		// Shallow Sediment and Basin Term
		if (z2p5 < 1.0) {
			fsed = c.c11 * (z2p5 - 1.0);
		} else if (z2p5 <= 3.0) {
			fsed = 0.0;
		} else {
			fsed = c.c12 * c.k3 * exp(-0.75) * (1.0 - exp(-0.25 * (z2p5 - 3.0)));
		}
		
		// Total Model
		return fmag + fdis + fflt + fhng + fsite + fsed;
		// @formatter:on
	}

	// Aleatory uncertainty model
	private static final double calcStdDev(Coeffs c, Coeffs cPGA, double vs30,
			double pgaRock) {

		// Inter-event Term
		double tau = c.t_lny;

		// Intra-event Term
		double sigma = c.s_lny;
		if (vs30 < c.k1) {
			double s_lnYb = sqrt(c.s_lny * c.s_lny - S_lnAFsq);
			double s_lnAb = sqrt(cPGA.s_lny * cPGA.s_lny - S_lnAFsq);
			double alpha = c.k2 * pgaRock *
				((1.0 / (pgaRock + C * pow(vs30 / c.k1, N))) - 1 / (pgaRock + C));
			sigma = sqrt(s_lnYb * s_lnYb + S_lnAF * S_lnAF + alpha * alpha *
				s_lnAb * s_lnAb + 2.0 * alpha * c.rho * s_lnYb * s_lnAb);
		}

		// Total Model
		return sqrt(tau*tau + sigma*sigma);
	}

}
