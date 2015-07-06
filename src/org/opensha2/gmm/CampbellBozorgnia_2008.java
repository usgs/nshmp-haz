package org.opensha2.gmm;

import static java.lang.Math.exp;
import static java.lang.Math.log;
import static java.lang.Math.max;
import static java.lang.Math.pow;
import static java.lang.Math.sqrt;
import static org.opensha2.gmm.FaultStyle.NORMAL;
import static org.opensha2.gmm.FaultStyle.REVERSE;
import static org.opensha2.gmm.GmmInput.Field.DIP;
import static org.opensha2.gmm.GmmInput.Field.MAG;
import static org.opensha2.gmm.GmmInput.Field.RAKE;
import static org.opensha2.gmm.GmmInput.Field.RJB;
import static org.opensha2.gmm.GmmInput.Field.RRUP;
import static org.opensha2.gmm.GmmInput.Field.VS30;
import static org.opensha2.gmm.GmmInput.Field.Z2P5;
import static org.opensha2.gmm.GmmInput.Field.ZTOP;
import static org.opensha2.gmm.Imt.PGA;
import static org.opensha2.gmm.Imt.SA0P01;
import static org.opensha2.gmm.Imt.SA0P25;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import org.opensha2.eq.fault.Faults;
import org.opensha2.gmm.GmmInput.Constraints;

import com.google.common.collect.Range;

/**
 * Implementation of the Campbell & Bozorgnia (2008) next generation attenuation
 * for active crustal regions relationship developed as part of <a
 * href="http://peer.berkeley.edu/ngawest/">NGA West I</a>.
 * 
 * <p><b>Note:</b> Direct instantiation of {@code GroundMotionModel}s is
 * prohibited. Use {@link Gmm#instance(Imt)} to retrieve an instance for a
 * desired {@link Imt}.</p>
 * 
 * <p><b>Reference:</b> NGA Ground Motion Model for the Geometric Mean
 * Horizontal Component of PGA, PGV, PGD and 5% Damped Linear Elastic Response
 * Spectra for Periods Ranging from 0.01 to 10 s: Earthquake Spectra, v. 24, n.
 * 1, pp. 139-171.</p>
 * 
 * <p><b>doi:</b> <a href="http://dx.doi.org/10.1193/1.2857546">
 * 10.1193/1.2857546</a></p>
 * 
 * <p><b>Component:</b> GMRotI50 (geometric mean)</p>
 * 
 * @author Peter Powers
 * @see Gmm#CB_08
 */
public final class CampbellBozorgnia_2008 implements GroundMotionModel {

	static final String NAME = "Campbell & Bozorgnia (2008)";

	static final Constraints CONSTRAINTS = GmmInput.constraintsBuilder()
		// TODO there are rake dependent M restrictions
		.set(MAG, Range.closed(4.0, 8.5))
		.set(RJB, Range.closed(0.0, 300.0))
		.set(RRUP, Range.closed(0.0, 300.0))
		// TODO actually is 15-90
		.set(DIP, Faults.DIP_RANGE)
		.set(ZTOP, Range.closed(0.0, 15.0))
		.set(RAKE, Faults.RAKE_RANGE)
		.set(VS30, Range.closedOpen(150.0, 1500.0))
		.set(Z2P5, Range.closed(0.0, 10.0))
		.build();

	static final CoefficientContainer COEFFS = new CoefficientContainer("CB08.csv");

	private static final Set<Imt> SHORT_PERIODS = EnumSet.range(SA0P01, SA0P25);

	private static final double S_lnAF = 0.3;
	private static final double N = 1.18;
	private static final double C = 1.88;
	private static final double S_lnAFsq = S_lnAF * S_lnAF;

	private static final class Coefficients {

		final Imt imt;
		final double
				c0, c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12,
				k1, k2, k3,
				s_lny, t_lny, ρ;

		// unused
		// double s_c;

		Coefficients(Imt imt, CoefficientContainer cc) {
			this.imt = imt;
			Map<String, Double> coeffs = cc.get(imt);
			c0 = coeffs.get("c0");
			c1 = coeffs.get("c1");
			c2 = coeffs.get("c2");
			c3 = coeffs.get("c3");
			c4 = coeffs.get("c4");
			c5 = coeffs.get("c5");
			c6 = coeffs.get("c6");
			c7 = coeffs.get("c7");
			c8 = coeffs.get("c8");
			c9 = coeffs.get("c9");
			c10 = coeffs.get("c10");
			c11 = coeffs.get("c11");
			c12 = coeffs.get("c12");
			k1 = coeffs.get("k1");
			k2 = coeffs.get("k2");
			k3 = coeffs.get("k3");
			s_lny = coeffs.get("s_lny");
			t_lny = coeffs.get("t_lny");
			ρ = coeffs.get("rho");
		}
	}

	private final Coefficients coeffs;
	private final Coefficients coeffsPGA;

	CampbellBozorgnia_2008(final Imt imt) {
		coeffs = new Coefficients(imt, COEFFS);
		coeffsPGA = new Coefficients(PGA, COEFFS);
	}

	@Override public final ScalarGroundMotion calc(final GmmInput in) {
		return calc(coeffs, coeffsPGA, in);
	}

	private static final ScalarGroundMotion calc(final Coefficients c, final Coefficients cPGA,
			final GmmInput in) {

		FaultStyle style = GmmUtils.rakeToFaultStyle_NSHMP(in.rake);
		double vs30 = in.vs30;
		double pgaRock = (vs30 < c.k1) ? exp(calcMean(cPGA, style, 1100.0, 0.0, in)) : 0.0;

		double μ = calcMean(c, style, vs30, pgaRock, in);

		// prevent SA<PGA for short periods
		if (SHORT_PERIODS.contains(c.imt)) {
			double pgaMean = calcMean(cPGA, style, vs30, pgaRock, in);
			μ = max(μ, pgaMean);
		}
		double σ = calcStdDev(c, cPGA, vs30, pgaRock);

		return DefaultScalarGroundMotion.create(μ, σ);
	}

	// Mean ground motion model -- we use supplied vs30 rather than one from
	// input to impose 1100 when computing rock reference
	private static final double calcMean(final Coefficients c, final FaultStyle style,
			final double vs30, final double pga_rock, final GmmInput in) {

		double Mw = in.Mw;
		double rJB = in.rJB;
		double rRup = in.rRup;
		double zTop = in.zTop;

		double fmag, fdis, fflt, fhng, fsite, fsed;

		// Magnitude Term
		fmag = c.c0 + c.c1 * Mw;
		if (Mw > 5.5) fmag += c.c2 * (Mw - 5.5);
		if (Mw > 6.5) fmag += c.c3 * (Mw - 6.5);

		// Source to Site Term
		fdis = (c.c4 + c.c5 * Mw) * log(sqrt(rRup * rRup + c.c6 * c.c6));

		// Fault-style Term
		fflt = (style == REVERSE) ? c.c7 * ((zTop < 1.0) ? zTop : 1.0) :
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
		fhngd = (in.dip <= 70.0) ? 1.0 : (90.0 - in.dip) / 20.0;
		// .....total
		fhng = c.c9 * fhngr * fhngm * fhngz * fhngd;

		// Site Term - linear and non-linear
		double vsk1 = vs30 / c.k1;
		if (vs30 < c.k1) {
			fsite = c.c10 * log(vsk1) + c.k2 *
				(log(pga_rock + C * pow(vsk1, N)) - log(pga_rock + C));
		} else if (vs30 < 1100.0) {
			fsite = (c.c10 + c.k2 * N) * Math.log(vsk1);
		} else {
			fsite = (c.c10 + c.k2 * N) * Math.log(1100.0 / c.k1);
		}

		// update z2p5 if not supplied
		double z2p5 = in.z2p5;
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
	}

	// Aleatory uncertainty model
	private static final double calcStdDev(final Coefficients c, final Coefficients cPGA,
			final double vs30,
			final double pgaRock) {

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
				s_lnAb * s_lnAb + 2.0 * alpha * c.ρ * s_lnYb * s_lnAb);
		}

		// Total Model
		return sqrt(tau * tau + sigma * sigma);
	}

}
