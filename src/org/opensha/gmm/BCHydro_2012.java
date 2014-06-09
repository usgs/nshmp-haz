package org.opensha.gmm;

import static java.lang.Math.exp;
import static java.lang.Math.log;
import static java.lang.Math.min;
import static java.lang.Math.pow;
import static org.opensha.gmm.IMT.PGA;

import org.opensha.calc.ScalarGroundMotion;

/**
 * Abstract implementation of the subduction ground motion model created for BC
 * Hydro, Canada, by Addo, Abrahamson, &amp; Youngs (2012). This implementation
 * matches that used in the USGS NSHM as supplied by N. Abrahamson.
 * 
 * <p>This model supports both slab and interface type events. In the 2008
 * NSHMP, the 'interface' form is used with the Cascadia subduction zone models
 * and the 'slab' form is used with gridded 'deep' events in northern California
 * and the Pacific Northwest.</p>
 * 
 * <p><b>Note:</b> Direct instantiation of {@code GroundMotionModel}s is
 * prohibited. Use {@link GMM#instance(IMT)} to retrieve an instance for a
 * desired {@link IMT}.</p>
 * 
 * <p><b>Implementation notes:</b> <ol><li>Treats all sites as
 * forearc.</li><li>'zTop' is interpreted as hypocentral depth and is only used
 * for slab events; it is limited to 125 km, consistent with other subduction
 * models.</li><li>The DeltaC1 term is keyed to the 'middle' BC Hydro branch for
 * interface events and fixed at -0.3 for slab events.</li></ol></p>
 * 
 * <p><b>Reference:</b> Addo, K., Abrahamson, N., and Youngs, R., (BC Hydro),
 * 2012, Probabilistic seismic hazard analysis (PSHA) model—Ground motion
 * characterization (GMC) model: Report E658, Volume 3, November.</p>
 * 
 * <p><b>Component:</b> Geometric mean of two horizontal components</p>
 * 
 * @author Peter Powers
 * @see GMM#BCHYDRO_12_INTER
 * @see GMM#BCHYDRO_12_SLAB
 */
public abstract class BCHydro_2012 implements GroundMotionModel {

	static final String NAME = "BC Hydro (2012)";

	static final CoefficientContainer CC = new CoefficientContainer("BCHydro12.csv", Coeffs.class);

	static class Coeffs extends Coefficients {
		double vlin, b, t1, t2, t6, t7, t8, t10, t11, t12, t13, t14, t15, t16, dC1lo, dC1mid,
				dC1hi;
	}

	// author declared constants
	private static final double C1 = 7.8;
	private static final double T3 = 0.1;
	private static final double T4 = 0.9;
	private static final double T5 = 0.0;
	private static final double T9 = 0.4;
	private static final double C4 = 10.0;
	private static final double C = 1.88;
	private static final double N = 1.18;
	
	// implementation constants
	private static final double VSS_MAX = 1000.0;
	private static final double SIGMA = 0.74;
	private static final double DC1_SLAB = -0.3;
	
	private final Coeffs coeffs, coeffsPGA;

	BCHydro_2012(IMT imt) {
		coeffs = (Coeffs) CC.get(imt);
		coeffsPGA = (Coeffs) CC.get(PGA);
	}

	@Override
	public final ScalarGroundMotion calc(GMM_Input props) {
		
		// pgaRock only required to compute non-linear site response when vs30
		// is less than period-dependent vlin cutoff
		double pgaRock = (props.vs30 < coeffs.vlin) ? calcMean(coeffsPGA,
			props.Mw, props.rRup, props.zTop, props.vs30, isSlab(), 0.0) : 0.0;
		
		double mean = calcMean(coeffs, props.Mw, props.rRup, props.zTop,
			props.vs30, isSlab(), pgaRock);
		
		return DefaultScalarGroundMotion.create(mean, SIGMA);
	}

	abstract boolean isSlab();
	
	private static final double calcMean(Coeffs c, double Mw, double rRup,
			double zTop, double vs30, boolean slab, double pgaRock) {

		// zTop = hypoDepth and is capped at 125km, only used when slab = true
		if (slab) zTop = min(zTop, 125.0);
		
		// DELC fixed at 0.0;
		double mCut = C1 + (slab ? DC1_SLAB : c.dC1mid);
		double t13m = c.t13 * (10 - Mw) * (10 - Mw);
		double fMag = (Mw <= mCut ? T4 : T5) * (Mw - mCut) + t13m;
		
		// no depth term for interface events
		double fDepth = slab ? c.t11 * (zTop-60.) : 0.0;
				
		double vsS = min(vs30, VSS_MAX);

		double fSite = c.t12 * log(vsS / c.vlin);
		if (vs30 < c.vlin) { // whether or not we use pgaRock
			fSite += -c.b * log(pgaRock + C) + c.b *
				log(pgaRock + C * pow((vsS / c.vlin), N));
		} else {
			// for pgaRock loop, vs=1000 > vlinPGA=865
			fSite += c.b * N * log(vsS / c.vlin);
		}
		
		return c.t1 +
				// c.t4 * delC1 ommitted b/c delC1=0
				(c.t2 + (slab ? c.t14 : 0.0) + T3 * (Mw - 7.8)) * 
					log(rRup + C4 * exp((Mw - 6.0) * T9)) +
				c.t6 * rRup +
				(slab ? c.t10 : 0.0) +
				fMag +
				fDepth +
				// fterm + no fterm for forearc sites
				fSite;
	}

}
