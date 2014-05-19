package org.opensha.gmm;

import static java.lang.Math.log;
import static java.lang.Math.min;
import static org.opensha.gmm.FaultStyle.REVERSE;

import org.opensha.calc.ScalarGroundMotion;

/**
 * Implementation of the Idriss (2013) next generation attenuation relationship
 * developed as part of <a href="http://peer.berkeley.edu/ngawest2">NGA West
 * II</a>.
 * 
 * <p><b>Note:</b> Direct instantiation of {@code GroundMotionModel}s is
 * prohibited. Use {@link GMM#instance(IMT)} to retrieve an instance for a
 * desired {@link IMT}.</p>
 * 
 * <p><b>Implementation notes:</b> Idriss (2013) recommends a cap of Vs=1200m/s
 * (implemented) and a distance limit of 150km (not implemented).</p>
 * 
 * <p><b>Implementation notes:</b> 0.01s SA values used for PGA.</p>
 * 
 * <p><b>Reference:</b> Idriss, I.M., 2013, NGA-West2 model for estimating
 * average horizontal values of pseudo-absolute spectral accelerations generated
 * by crustal earthquakes, PEER Report 2013/08.</p>
 * 
 * <p><b>See:</b> <a
 * href="http://peer.berkeley.edu/ngawest2/final-products/">NGA-West2 Final
 * Products</a></p>
 * 
 * <p><b>Component:</b> RotD50 (average horizontal)</p>
 * 
 * @author Peter Powers
 */
public final class Idriss_2014 implements GroundMotionModel {

	// TODO review class javadoc and update citation to EQS
	
	static final String NAME = "Idriss (2014)";
	
	static final CoefficientContainer CC = new CoefficientContainer(
		"Idriss14.csv", Coeffs.class);
	
	static class Coeffs extends Coefficients {
		double a1_lo, a2_lo, a1_hi, a2_hi, a3, b1_lo, b2_lo, b1_hi, b2_hi, xi,
				gamma, phi;
	}
	
	private final Coeffs coeffs;

	Idriss_2014(IMT imt) {
		coeffs = (Coeffs) CC.get(imt);
	}

	@Override
	public final ScalarGroundMotion calc(GMM_Source props) {
		FaultStyle style = rakeToFaultStyle(props.rake);
		return calc(coeffs, props.Mw, props.rRup, style, props.vs30);
	}
	
	FaultStyle rakeToFaultStyle(double rake) {
		return GMM_Utils.rakeToFaultStyle_NSHMP(rake);
	}
	
	private static final ScalarGroundMotion calc(Coeffs c,
			double Mw, double rRup, FaultStyle style, double vs30) {

		double mean = calcMean(c, Mw, rRup, style, vs30);
		double stdDev = calcStdDev(c, Mw);

		return DefaultScalarGroundMotion.create(mean, stdDev);
	}
	
	// Mean ground motion model - cap of Vs = 1200 m/s
	private static final double calcMean(Coeffs c, double Mw, double rRup,
			FaultStyle style, double vs30) {
		
		double a1 = c.a1_lo, a2 = c.a2_lo;
		double b1 = c.b1_lo, b2 = c.b2_lo;
		if (Mw > 6.75) {
			a1 = c.a1_hi; a2 = c.a2_hi;
			b1 = c.b1_hi; b2 = c.b2_hi;
		}

		return a1 + a2 * Mw + c.a3 * (8.5 - Mw) * (8.5 - Mw) -
			(b1 + b2 * Mw) * log(rRup + 10.0) +
			c.xi * log(min(vs30, 1200.0)) + 
			c.gamma * rRup + (style == REVERSE ? c.phi : 0.0);
	}

	// Aleatory uncertainty model
	private static final double calcStdDev(Coeffs c, double Mw) {
		double s1 = 0.035;
		Double T = c.imt.period();
		s1 *= (T == null || T <= 0.05) ? log(0.05) : (T < 3.0) ? log(T)
			: log(3d);
		double s2 = 0.06;
		s2 *= (Mw <= 5.0) ? 5.0 : (Mw < 7.5) ? Mw : 7.5;
		return 1.18 + s1 - s2;
	}
	
}
