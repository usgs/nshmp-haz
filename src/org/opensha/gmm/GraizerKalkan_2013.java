package org.opensha.gmm;

import static java.lang.Math.abs;
import static java.lang.Math.atan;
import static java.lang.Math.cos;
import static java.lang.Math.exp;
import static java.lang.Math.log;
import static java.lang.Math.max;
import static java.lang.Math.pow;
import static java.lang.Math.sqrt;
import static org.opensha.gmm.FaultStyle.REVERSE;
import static org.opensha.gmm.IMT.PGA;
import static org.opensha.gmm.IMT.PGD;
import static org.opensha.gmm.IMT.PGV;

import java.util.EnumSet;
import java.util.Set;

import org.opensha.calc.ScalarGroundMotion;

/**
 * Implementation of the Graizer &amp; Kalkan (2013) GMPE. This model does not
 * support specific periods as most other GMMs do; it can handle any frequency.
 * 
 * <p>Component: geometric mean of two randomly oriented horizontal
 * components</p>
 * 
 * @author Peter Powers
 */
@Deprecated
final class GraizerKalkan_2013 implements GroundMotionModel {

	static final String NAME = "Graizer \u0026 Kalkan (2013)";
	
	static final Set<IMT> IMTS = EnumSet.complementOf(EnumSet.of(PGV, PGD));
	
	private final double period;
	
	GraizerKalkan_2013(IMT imt) {
		period = imt.equals(PGA) ? 0.01 : imt.period();
	}

	@Override
	public ScalarGroundMotion calc(GmmInput props) {
		FaultStyle style = rakeToFaultStyle(props.rake);
		return calc(period, props.Mw, props.rRup, style, props.vs30);
	}
	
	FaultStyle rakeToFaultStyle(double rake) {
		return GMM_Utils.rakeToFaultStyle_NSHMP(rake);
	}
		
	private static ScalarGroundMotion calc(double period, double Mw,
			double rRup, FaultStyle style, double vs30) {
		double F = (style == REVERSE) ? 1.28 : 1.0;

		// fixed at 150m for now; generic CA value per email from Vladimir
		// TODO create WUS variant; double check fortran for dBasin change
		double dBasin = 0.150;

		double pgaRef = calcLnPGA(Mw, rRup, vs30, dBasin, F);
		double sa = calcSpectralShape(period, Mw, rRup, vs30, dBasin);
		double mean = log(sa) + pgaRef;
		double std = calcStdDev(period);
		
		return DefaultScalarGroundMotion.create(mean, std);
	}
	
	private static final double m1 = -0.0012;
	private static final double m2 = -0.38;
	private static final double m3 = 0.0006;
	private static final double m4 = 3.9;
	
	private static final double a1 = 0.01686;
	private static final double a2 = 1.2695;
	private static final double a3 = 0.0001;

	private static final double s1 = 0.000;
	private static final double s2 = 0.077;
	private static final double s3 = 0.3251;

	private static final double t1 = 0.001;
	private static final double t2 = 0.59;
	private static final double t3 = -0.0005;
	private static final double t4 = -2.3;

	private static final double calcSpectralShape(double per, double Mw,
			double rRup, double vs30, double dBasin) {
		double mu = m1 * rRup + m2 * Mw + m3 * vs30 + m4;
		double A = (a1 * Mw + a2) * exp(a3 * rRup);
		double si = s1 * rRup - (s2 * Mw + s3);
		double T1 = abs(t1 * rRup + t2 * Mw + t3 * vs30 + t4);
		double To = max(0.3, T1);

		double slope = 1.763 - 0.25 * atan(1.4 * (dBasin - 1.0));
		double F1A = (log(per) + mu) / si;
		double F1 = A * exp(-0.5 * F1A * F1A);
		double F2A = pow(per / To, slope);
		double F2 = 1.0 / sqrt((1.0 - F2A) * (1.0 - F2A) + 2.25 * F2A);
        return F1 + F2;   
	}
	
	private static final double calcStdDev(double per) {
		double Sigma1 = 0.5522 + 0.0047 * log(per);
		double Sigma2 = 0.646 + 0.0497 * log(per);
		return max(Sigma1, Sigma2);
	}
	
	private static final double c1 = 0.140;
	private static final double c2 = -6.250;
	private static final double c3 = 0.370;
	private static final double c4 = 2.237;
	private static final double c5 = -7.542;
	private static final double c6 = -0.125;
	private static final double c7 = 1.190;
	private static final double c8 = -6.150;
	private static final double c9 = 0.600;
	private static final double bv = -0.240;
	private static final double VA = 484.5;
	private static final double c11 = 0.345;
	private static final double Q = 150.0; // California specific (is 156.6 in SH code)
	// TODO Q, above, needs to be updated to 205 outside CA
	
	private static final double calcLnPGA(double Mw, double rRup, double vs30,
			double dBasin, double F) {

		// Attenuation Equations
		double F1 = log((c1 * atan(Mw + c2) + c3) * F / 1.12);
		double Ro = c4 * Mw + c5;
		double Do = c6 * cos(c7 * (Mw + c8)) + c9;

		double rRo1 = rRup / Ro;
		double rRo2 = 1.0 - rRo1;
		double F2 = -0.5 * log(rRo2 * rRo2 + 4 * (Do * Do) * rRo1);

		// New Anelastic Eq.
		double F3 = -c11 * rRup / Q;
		double F4 = bv * log(vs30 / VA);

		// New Basin Eq.
		double bd1 = (1.5 / (dBasin + 0.1)) * (1.5 / (dBasin + 0.1));
		double Bas_Depth = 1.4 / sqrt((1.0 - bd1) * (1.0 - bd1) + 1.96 * bd1);

		double bd2 = (40.0 / (rRup + 0.1)) * (40.0 / (rRup + 0.1));
		double Bas_Dist = 1.0 / sqrt((1.0 - bd2) * (1.0 - bd2) + 1.96 * bd2);
		double Bas_Cor = Bas_Depth * Bas_Dist;
		double F5 = log(1 + Bas_Cor / 1.3);

		// Final Eq.
		return F1 + F2 + F3 + F4 + F5;
	}

}
