package org.opensha.gmm;

import static java.lang.Math.exp;
import static java.lang.Math.log;
import static org.opensha.gmm.FaultStyle.NORMAL;
import static org.opensha.gmm.FaultStyle.REVERSE;
import static org.opensha.gmm.FaultStyle.STRIKE_SLIP;
import static org.opensha.gmm.FaultStyle.UNKNOWN;
import static org.opensha.gmm.Imt.PGA;

import java.util.Map;

/**
 * Implementation of the Boore & Atkinson (2008) next generation attenuation
 * relationship for active crustal regions developed as part of <a
 * href="http://peer.berkeley.edu/ngawest/">NGA West I</a>.
 * 
 * <p><b>Note:</b> Direct instantiation of {@code GroundMotionModel}s is
 * prohibited. Use {@link Gmm#instance(Imt)} to retrieve an instance for a
 * desired {@link Imt}.</p>
 * 
 * <p><b>Reference:</b> Boore, D.M., and Atkinson, G.M., 2008, Ground-motion
 * prediction equations for the average horizontal component of PGA, PGV, and
 * 5%-damped PSA at spectral periods between 0.01s and 10.0s, Earthquake
 * Spectra, Volume 24, Number 1, pp. 99-138.</p>
 * 
 * <p><b>Component:</b> GMRotI50 (geometric mean)</p>
 * 
 * @author Peter Powers
 * @see Gmm#BA_08
 */
public final class BooreAtkinson_2008 implements GroundMotionModel {

	static final String NAME = "Boore & Atkinson (2008)";

	static final CoefficientsNew COEFFS = new CoefficientsNew("BA08.csv");

	// author defined constants
	private static final double PGAlo = 0.06;
	private static final double A2 = 0.09;
	private static final double A1 = 0.03;
	private static final double V1 = 180.0;
	private static final double V2 = 300.0;
	private static final double Vref = 760;
	private static final double Mref = 4.5;
	private static final double Rref = 1;

	static final class Coeffs {
		double b_lin, b1, b2, c1, c2, c3, e1, e2, e3, e4, e5, e6, e7, h, mh, s,
				t_u, s_tu, t_m, s_tm;

		Coeffs(Map<String, Double> coeffs) {
			b_lin = coeffs.get("b_lin");
			b1 = coeffs.get("b1");
			b2 = coeffs.get("b2");
			c1 = coeffs.get("c1");
			c2 = coeffs.get("c2");
			c3 = coeffs.get("c3");
			e1 = coeffs.get("e1");
			e2 = coeffs.get("e2");
			e3 = coeffs.get("e3");
			e4 = coeffs.get("e4");
			e5 = coeffs.get("e5");
			e6 = coeffs.get("e6");
			e7 = coeffs.get("e7");
			h = coeffs.get("h");
			mh = coeffs.get("mh");
			s = coeffs.get("s");
			t_u = coeffs.get("t_u");
			s_tu = coeffs.get("s_tu");
			t_m = coeffs.get("t_m");
			s_tm = coeffs.get("s_tm");
		}
	}

	private final Coeffs coeffs;
	private final Coeffs coeffsPGA;

	BooreAtkinson_2008(final Imt imt) {
		coeffs = new Coeffs(COEFFS.get(imt));
		coeffsPGA = new Coeffs(COEFFS.get(PGA));
	}

	@Override public final ScalarGroundMotion calc(final GmmInput in) {
		return calc(coeffs, coeffsPGA, in);
	}

	// TODO not sure how to test this or make backwards compatible version for
	// comparisons. In 2008, the NSHMP mistakenly would supply fractional
	// weights
	// to the different fault styles whereas the 4 different fault styles
	// should be booleans and only ever have one of their values set to 1.

	private static final ScalarGroundMotion calc(final Coeffs c, final Coeffs cPGA,
			final GmmInput in) {

		FaultStyle style = GmmUtils.rakeToFaultStyle_NSHMP(in.rake);

		double pga4nl = exp(calcPGA4nl(cPGA, in.Mw, in.rJB, style));

		double mean = calcMean(c, style, pga4nl, in);

		double stdDev = calcStdDev(c, style);

		return DefaultScalarGroundMotion.create(mean, stdDev);
	}

	// Mean ground motion model
	private static final double calcMean(final Coeffs c, final FaultStyle style,
			final double pga4nl, final GmmInput in) {

		// Source/Event Term
		double Fm = calcSourceTerm(c, in.Mw, style);

		// Path Term
		double Fd = calcPathTerm(c, in.Mw, in.rJB);

		// Site Response Term -- Linear
		double vs30 = in.vs30;
		double Fs = c.b_lin * log(vs30 / Vref);

		// Site Response Term -- Nonlinear
		double bnl = 0.0; // vs30 >= 760 case
		if (vs30 < Vref) {
			if (vs30 > V2) {
				bnl = c.b2 * log(vs30 / Vref) / log(V2 / Vref);
			} else if (vs30 > V1) {
				bnl = (c.b1 - c.b2) * log(vs30 / V2) / log(V1 / V2) + c.b2;
			} else {
				bnl = c.b1;
			}
		}

		double Fnl = 0.0;
		if (pga4nl <= A1) {
			Fnl = bnl * log(PGAlo / 0.1);
		} else if (pga4nl <= A2) {
			double dX = log(A2 / A1);
			double dY = bnl * log(A2 / PGAlo);
			double _c = (3.0 * dY - bnl * dX) / (dX * dX);
			double d = -(2.0 * dY - bnl * dX) / (dX * dX * dX);
			double p = log(pga4nl / A1);
			Fnl = bnl * log(PGAlo / 0.1) + (_c * p * p) + (d * p * p * p);

		} else {
			Fnl = bnl * log(pga4nl / 0.1);
		}

		// Total site
		Fs += Fnl;

		// Total Model
		return Fm + Fd + Fs;
	}

	// Median PGA for ref rock (Vs30=760m/s); always called with PGA coeffs
	private static final double calcPGA4nl(final Coeffs c, final double Mw, final double rJB,
			final FaultStyle style) {

		// Source/Event Term
		double Fm = calcSourceTerm(c, Mw, style);

		// Path Term
		double Fd = calcPathTerm(c, Mw, rJB);

		return Fm + Fd;
	}

	// Source/Event Term
	private static final double calcSourceTerm(final Coeffs c, final double Mw,
			final FaultStyle style) {
		double Fm = (style == STRIKE_SLIP) ? c.e2 : (style == NORMAL) ? c.e3 :
			(style == REVERSE) ? c.e4 : c.e1; // else unkown
		double MwMh = Mw - c.mh;
		Fm += (Mw <= c.mh) ? c.e5 * MwMh + c.e6 * MwMh * MwMh : c.e7 * MwMh;
		return Fm;
	}

	// Path Term
	private static final double calcPathTerm(final Coeffs c, final double Mw, final double rJB) {
		double r = Math.sqrt(rJB * rJB + c.h * c.h);
		return (c.c1 + c.c2 * (Mw - Mref)) * log(r / Rref) + c.c3 *
			(r - Rref);
	}

	// Aleatory uncertainty model
	private static final double calcStdDev(final Coeffs c, final FaultStyle style) {
		// independent values for tau and sigma are available in coeffs
		return style == UNKNOWN ? c.s_tu : c.s_tm;
	}

}
