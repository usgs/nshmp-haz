package org.opensha.gmm;

import static java.lang.Math.exp;
import static java.lang.Math.log;
import static java.lang.Math.min;
import static java.lang.Math.pow;
import static java.lang.Math.sqrt;
import static org.opensha.gmm.FaultStyle.NORMAL;
import static org.opensha.gmm.FaultStyle.REVERSE;
import static org.opensha.gmm.FaultStyle.STRIKE_SLIP;
import static org.opensha.gmm.Imt.PGA;

import java.util.Map;

/**
 * Implementation of the Boore, Stewart, Seyhan, & Atkinson (2014) next
 * generation ground motion model for active crustal regions developed as part
 * of<a href="http://peer.berkeley.edu/ngawest2">NGA West II</a>.
 * 
 * <p><b>Note:</b> Direct instantiation of {@code GroundMotionModel}s is
 * prohibited. Use {@link Gmm#instance(Imt)} to retrieve an instance for a
 * desired {@link Imt}.</p>
 * 
 * <p><b>Reference:</b> Boore, D.M., Stewart, J.P., Seyhan, E., and Atkinson,
 * G.M., 2014, NGA-West 2 equations for predicting PGA, PGV, and 5%-damped PSA
 * for shallow crustal earthquakes, Earthquake Spectra, v. 30, n. 3, p.
 * 1057-1085.</p>
 * 
 * <p><b>doi:</b> <a href="http://dx.doi.org/10.1193/070113EQS184M">
 * http://dx.doi.org/10.1193/070113EQS184M</a></p>
 * 
 * <p><b>Component:</b> RotD50 (average horizontal)</p>
 * 
 * @author Peter Powers
 * @see Gmm#BSSA_14
 */
public final class BooreEtAl_2014 implements GroundMotionModel {

	static final String NAME = "Boore, Stewart, Seyhan & Atkinson (2014)";

	static final CoefficientsNew COEFFS = new CoefficientsNew("BSSA14.csv");

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

	private static final class Coeffs {

		final double e0, e1, e2, e3, e4, e5, e6, Mh, c1, c2, c3, h, c, Vc, f4, f5,
				f6, f7, r1, r2, Δφ_r, Δφ_v, φ1, φ2, τ1, τ2;

		// same for all periods; replaced with constant
		// double Mref, Rref, Dc3CaTw, Vref, f1, f3, v1, v2;

		// unused regional coeffs
		// double Dc3CnTr, Dc3ItJp;

		Coeffs(Map<String, Double> coeffs) {
			e0 = coeffs.get("e0");
			e1 = coeffs.get("e1");
			e2 = coeffs.get("e2");
			e3 = coeffs.get("e3");
			e4 = coeffs.get("e4");
			e5 = coeffs.get("e5");
			e6 = coeffs.get("e6");
			Mh = coeffs.get("Mh");
			c1 = coeffs.get("c1");
			c2 = coeffs.get("c2");
			c3 = coeffs.get("c3");
			h = coeffs.get("h");
			c = coeffs.get("c");
			Vc = coeffs.get("Vc");
			f4 = coeffs.get("f4");
			f5 = coeffs.get("f5");
			f6 = coeffs.get("f6");
			f7 = coeffs.get("f7");
			r1 = coeffs.get("R1");
			r2 = coeffs.get("R2");
			Δφ_r = coeffs.get("dPhiR");
			Δφ_v = coeffs.get("dPhiV");
			φ1 = coeffs.get("phi1");
			φ2 = coeffs.get("phi2");
			τ1 = coeffs.get("tau1");
			τ2 = coeffs.get("tau2");
		}
	}

	private final Coeffs coeffs;
	private final Coeffs coeffsPGA;
	private final Imt imt;

	BooreEtAl_2014(final Imt imt) {
		this.imt = imt;
		coeffs = new Coeffs(COEFFS.get(imt));
		coeffsPGA = new Coeffs(COEFFS.get(PGA));
	}

	// TODO limit supplied z1p0 to 0-3 km

	@Override public final ScalarGroundMotion calc(final GmmInput in) {
		return calc(coeffs, coeffsPGA, imt, in);
	}

	private static final ScalarGroundMotion calc(final Coeffs c, final Coeffs cPGA, final Imt imt,
			final GmmInput in) {

		FaultStyle style = GmmUtils.rakeToFaultStyle_NSHMP(in.rake);

		double pgaRock = calcPGArock(cPGA, in.Mw, in.rJB, style);
		double mean = calcMean(c, imt, style, pgaRock, in);
		double stdDev = calcStdDev(c, in);

		return DefaultScalarGroundMotion.create(mean, stdDev);
	}

	// Mean ground motion model
	private static final double calcMean(final Coeffs c, final Imt imt, final FaultStyle style,
			final double pgaRock, final GmmInput in) {

		double Mw = in.Mw;
		double rJB = in.rJB;
		double vs30 = in.vs30;

		// Source/Event Term -- Equation 2
		double Fe = calcSourceTerm(c, Mw, style);

		// Path Term -- Equations 3, 4
		double R = sqrt(rJB * rJB + c.h * c.h);
		double Fp = calcPathTerm(c, Mw, R);

		// Site Linear Term -- Equation 6
		double vsLin = (vs30 <= c.Vc) ? vs30 : c.Vc;
		double lnFlin = c.c * log(vsLin / V_REF);

		// Site Nonlinear Term -- Equations 7, 8
		double f2 = c.f4 * (exp(c.f5 * (min(vs30, 760.0) - 360.0)) - exp(c.f5 * (760.0 - 360.0)));
		double lnFnl = F1 + f2 * log((pgaRock + F3) / F3);

		// Basin depth term -- Equations 9, 10 , 11
		double DZ1 = calcDeltaZ1(in.z1p0, vs30);
		double Fdz1 = (imt.isSA() && imt.period() >= 0.65) ?
			(DZ1 <= c.f7 / c.f6) ? c.f6 * DZ1 : c.f7 : 0.0;

		// Total site term -- Equation 5
		double Fs = lnFlin + lnFnl + Fdz1;

		// Total model -- Equation 1
		return Fe + Fp + Fs;
	}

	// Median PGA for ref rock (Vs30=760m/s); always called with PGA coeffs
	private static final double calcPGArock(final Coeffs c, final double Mw, final double rJB,
			final FaultStyle style) {

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
	private static final double calcSourceTerm(final Coeffs c, final double Mw,
			final FaultStyle style) {
		double Fe = (style == STRIKE_SLIP) ? c.e1 : (style == REVERSE) ? c.e3 :
			(style == NORMAL) ? c.e2 : c.e0; // else UNKNOWN
		double MwMh = Mw - c.Mh;
		Fe += (Mw <= c.Mh) ? c.e4 * MwMh + c.e5 * MwMh * MwMh : c.e6 * MwMh;
		return Fe;
	}

	// Path Term, base model -- Equation 3
	private static final double calcPathTerm(final Coeffs c, final double Mw, final double R) {
		return (c.c1 + c.c2 * (Mw - M_REF)) * log(R / R_REF) +
			(c.c3 + DC3_CA_TW) * (R - R_REF);
	}

	// Calculate delta Z1 in km as a function of vs30 and using the default
	// model of ChiouYoungs_2013 -- Equations 10, 11
	private static final double calcDeltaZ1(final double z1p0, final double vs30) {
		if (Double.isNaN(z1p0)) return 0.0;
		double vsPow4 = vs30 * vs30 * vs30 * vs30;
		return z1p0 - exp(-7.15 / 4.0 * log((vsPow4 + A) / B)) / 1000.0;
	}

	// Aleatory uncertainty model
	private static final double calcStdDev(final Coeffs c, final GmmInput in) {

		double Mw = in.Mw;
		double rJB = in.rJB;
		double vs30 = in.vs30;

		// Inter-event Term -- Equation 14
		double τ = (Mw >= 5.5) ? c.τ2 : (Mw <= 4.5) ? c.τ1 : c.τ1 + (c.τ2 - c.τ1) * (Mw - 4.5);

		// Intra-event Term -- Equations 15, 16, 17
		double φ_m = (Mw >= 5.5) ? c.φ2 : (Mw <= 4.5) ? c.φ1 : c.φ1 + (c.φ2 - c.φ1) * (Mw - 4.5);

		double φ_mr = φ_m;
		if (rJB > c.r2) {
			φ_mr += c.Δφ_r;
		} else if (rJB > c.r1) {
			φ_mr += c.Δφ_r * (log(rJB / c.r1) / log(c.r2 / c.r1));
		}

		double φ_mrv = φ_mr;
		if (vs30 <= V1) {
			φ_mrv -= c.Δφ_v;
		} else if (vs30 < V2) {
			φ_mrv -= c.Δφ_v * (log(V2 / vs30) / log(V2 / V1));
		}

		// Total model -- Equation 13
		return sqrt(φ_mrv * φ_mrv + τ * τ);
	}

}
