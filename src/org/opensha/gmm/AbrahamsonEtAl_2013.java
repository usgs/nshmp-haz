package org.opensha.gmm;

import static java.lang.Math.cos;
import static java.lang.Math.exp;
import static java.lang.Math.log;
import static java.lang.Math.pow;
import static java.lang.Math.sqrt;
import static org.opensha.geo.GeoTools.TO_RAD;
import static org.opensha.gmm.FaultStyle.NORMAL;
import static org.opensha.gmm.FaultStyle.REVERSE;

import org.opensha.calc.ScalarGroundMotion;
import org.opensha.data.Interpolate;

/**
 * Implementation of the Abrahamson, Silva &amp; Kamai (2013) next generation
 * attenuation relationship developed as part of NGA West II.
 * 
 * <p>See: Abrahamson, N.A., Silva, W.J., and Kamai, R., 2013, Update of the
 * AS08 ground-motion prediction equations based on the NGA-West2 data set, PEER
 * Report 2013/04.</p>
 * 
 * <p>See: <a href="http://peer.berkeley.edu/ngawest2/final-products/">NGA-West2
 * Final Products</a></p>
 * 
 * <p>Component: RotD50 (average horizontal)</p>
 * 
 * @author Peter Powers
 */
final class AbrahamsonEtAl_2013 implements GroundMotionModel {

	public static final String NAME = "Abrahamson, Silva \u0026 Kamai (2013)";
	
	public static final CoefficientContainer CC = new CoefficientContainer(
		"ASK13.csv", Coeffs.class);
	
	static final class Coeffs extends Coefficients {
		double a1, a2, a3, a4, a5, a6, a8, a10, a11, a12, a13, a14, a15, a17,
				a43, a44, a45, a46, b, c, c4, s1e, s2e, s3, s4, s1m, s2m, s5,
				s6, M1, Vlin;
	}
	
	// author declared constants
	private static final double M2 = 5.0;
	private static final double N = 1.5;
	private static final double A7 = 0.0;

	// implementation constants
	private static final double A = pow(610, 4);
	private static final double B = pow(1360, 4) + A;
	private static final double VS_RK = 1180.0;
	
//	private static final double RY0 = -1.0;
	
	// TODO inline constant coeffs with final statics
	private final Coeffs coeffs;

	AbrahamsonEtAl_2013(IMT imt) {
		coeffs = (Coeffs) CC.get(imt);
	}
	
	@Override
	public final ScalarGroundMotion calc(GMM_Input props) {
		FaultStyle style = rakeToFaultStyle(props.rake);
		return calc(coeffs, props.Mw, props.rJB, props.rRup, props.rX,
			props.dip, props.width, props.zTop, style, props.vs30, props.vsInf,
			props.z1p0);
	}
	
	FaultStyle rakeToFaultStyle(double rake) {
		return GMM_Utils.rakeToFaultStyle_NSHMP(rake);
	}
	
	private static final ScalarGroundMotion calc(Coeffs c, double Mw,
			double rJB, double rRup, double rX, double dip,
			double width, double zTop, FaultStyle style, double vs30,
			boolean vsInferred, double z1p0) {

		// @formatter:off

		//  ****** Mean ground motion and standard deviation model ****** 
		
		// Base Model (magnitude and distance dependence for strike-slip eq)
		
		// Magnitude dependent taper -- Equation 4.4
		double c4mag = (Mw >= 5) ? c.c4 : 
					   (Mw >= 4) ? c.c4 - (c.c4 - 1.0) * (5.0 - Mw) : 1.0;
					   
		// -- Equation 4.3
		double R = sqrt(rRup * rRup + c4mag * c4mag);
				
		// -- Equation 4.2
		double MaxMw = 8.5 - Mw;
		double MwM1 = Mw - c.M1;

		double f1 = c.a1 + c.a17 * rRup;
		if (Mw > c.M1) {
			f1 += c.a5 * MwM1 + c.a8 * MaxMw * MaxMw + (c.a2 + c.a3 * MwM1) * log(R);
		} else if (Mw >= M2) {
			f1 += c.a4 * MwM1 + c.a8 * MaxMw * MaxMw + (c.a2 + c.a3 * MwM1) * log(R);
		} else {
			double M2M1 = M2 - c.M1;
			double MaxM2 = 8.5 - M2;
			double MwM2 = Mw - M2;
			f1 += c.a4 * M2M1 + c.a8 * MaxM2 * MaxM2 + c.a6 * MwM2 + A7 * MwM2 * MwM2 + (c.a2 + c.a3 * M2M1) * log(R);
		}

		
		// Aftershock Model (Class1 = mainshock; Class2 = afershock)
		// not currently used as rJBc (the rJB from the centroid of the parent
		// Class1 event) is not defined; requires event type flag -- Equation 7
		// double f11 = 0.0 * c.a14;
		//if (rJBc < 5) {
		//	f11 = c.a14;
		//} else if (rJBc <= 15) {
		//	f11 = c.a14 * (1 - (rJBc - 5.0) / 10.0);
		//}

		// Hanging Wall Model
		double f4 = 0.0;
		if (rX >= 0.0) {
			double T1 = 0.0, T2 = 0.0, T3 = 0.0, T4 = 0.0, T5 = 0.0;
			
			// ....dip taper -- Equation 4.11
			T1 = (dip > 30.0) ? (90.0 - dip) / 45 : 1.33333333; // 60/45
			
			// ....mag taper -- Equation 4.12
			double hw_a2 = 0.2; // TODO make constant
			double dM = Mw - 6.5;
			if (Mw >= 6.5) {
				T2 = 1 + hw_a2 * dM;
			} else if (Mw > 5.5) {
				T2 = 1 + hw_a2 * dM + (1 - hw_a2) * dM * dM;
			}
			
			// ....rX taper -- Equation 4.13
			double r1 = width * cos(dip * TO_RAD);
			double r2 = 3 * r1;
//			System.out.println("r1: " + r1);
//			System.out.println("r2: " + r2);
			double h1 = 0.25, h2 = 1.5, h3 = -0.75; // TODO make constant or inline
			if (rX <= r1) {
				double rXr1 = rX / r1; 
				T3 = h1 + h2 * rXr1 + h3 * rXr1 * rXr1;
			} else if (rX <= r2) {
				T3 = 1-(rX-r1)/(r2-r1);
			}
			
			// ....zTop taper -- Equation 4.14
			if (zTop <= 10) T4 = 1 - (zTop * zTop) / 100.0;
			
//			if (RY0 >= 0.0) {
//				// ....rX, rY0 taper -- Equation 4.15a
//				double rY1 = rX * tan(20 * TO_RAD);
//				if (RY0 < rY1) {
//					T5 = 1;
//				} else if (RY0 - rY1 < 5) {
//					T5 = 1 - (RY0 - rY1) / 5;
//				}
//			} else {
				// ....rX, non-rY0 taper -- Equation 4.15b
				if (rJB == 0.0) {
					T5 = 1;
				} else if (rJB < 30.0) {
					T5 = 1 - rJB / 30.0;
				}
//			}
			
			// total -- Equation 4.10
			f4 = c.a13 * T1 * T2 * T3 * T4 * T5;
//			System.out.println(T1 + " " +T2 + " " +T3+ " " +T4 + " " +T5);
		}
		
		// Depth to Rupture Top Model -- Equation 4.16
		double f6 = c.a15;
		if (zTop < 20.0) f6 *= zTop / 20.0;

		// Style-of-Faulting Model -- Equations 4.5 & 4.6
		// (note that f7 always resolves to 0 as a11 are all 0s)
		double f78 = 0.0; // fault-style terms combined
		if (Mw > 5.0) {
			f78 = (style == REVERSE) ? c.a11 : 
				  (style == NORMAL) ? c.a12 : 0.0;
		} else if (Mw >= 4.0) {
			f78 = (style == REVERSE) ? c.a11 * (Mw - 4) : 
				  (style == NORMAL) ? c.a12 * (Mw - 4) : 0.0;
		}
	
		// Soil Depth Model -- Equation 4.17
		double f10 = calcSoilTerm(c, vs30, z1p0);
		
		// Site Response Model
		double f5 = 0.0;
		double v1 = getV1(c.imt); // -- Equation 4.9
		double vs30s = (vs30 < v1) ? vs30 : v1; // -- Equation 4.8

		// Site term -- Equation 4.7
		double saRock = 0.0; // calc Sa1180 (rock reference) if necessary 
		if (vs30 < c.Vlin) {
			// soil term (f10) for Sa1180 is zero per R. Kamai's code where
			// Z1 < 0 for Sa1180 loop
			double vs30s_rk = (VS_RK < v1) ? VS_RK : v1;
			// use this f5 form for Sa1180 Vlin is always < 1180
			double f5_rk = (c.a10 + c.b * N) * log(vs30s_rk / c.Vlin);
			saRock = exp(f1 + f78 + f5_rk + f4 + f6);
			f5 = c.a10 * log(vs30s / c.Vlin) - c.b * log(saRock + c.c) + c.b *
				log(saRock + c.c * pow(vs30s / c.Vlin, N));
		} else {
			f5 = (c.a10 + c.b * N) * log(vs30s / c.Vlin);
		}

		// Constant Displacement Model (TBD)

		// total model (no aftershock f11)
		double mean = f1 + f78 + f5 + f4 + f6 + f10; 
		
//		System.out.println("f1 " + f1);
//		System.out.println("f78 " + f78);
//		System.out.println("f5 " + f5);
//		System.out.println("f4 " + f4);
//		System.out.println("f6 " + f6);
//		System.out.println("f10 " + f10);
		
		
		// ****** Aleatory uncertainty model ******
		
		// the code below removes unnecessary square-sqrt pairs
		
		// Intra-event term -- Equation 7.1
		double phiAsq = vsInferred ? 
			getPhiA(Mw, c.s1e, c.s2e) : 
			getPhiA(Mw, c.s1m, c.s2m);
		phiAsq *= phiAsq;
		
		// Inter-event term -- Equation 7.2
		double tauB = getTauA(Mw, c.s3, c.s4);
		
		// Intra-event term with site amp variability removed -- Equation 7.7
		// (phiAmp=0.4)^2 = 0.16
		double phiAmpSq = 0.16; // TODO make constant
		double phiBsq = phiAsq - phiAmpSq;
		
		// Parital deriv. of ln(soil amp) w.r.t. ln(SA1100) -- Equation 7.10
		// saRock subject to same vs30 < Vlin test as in mean model
		double dAmp_p1 = get_dAmp(c.b, c.c, c.Vlin, vs30, saRock) + 1.0;
		
		// phi squared, with non-linear effects -- Equation 24
		double phiSq = phiBsq * dAmp_p1 * dAmp_p1 + phiAmpSq;

		// tau squared, with non-linear effects -- Equation 25
		double tau = tauB * dAmp_p1;
		
		// total std dev
		double stdDev = sqrt(phiSq + tau * tau);
		
		return DefaultScalarGroundMotion.create(mean, stdDev);

	}
	
	private static final double getV1(IMT imt) {
		Double T = imt.getPeriod();
		if (T == null) return 1500.0;
		if (T >= 3.0) return 800.0;
		if (T > 0.5) return exp( -0.35 * log(T / 0.5) + log(1500.0));
		return 1500.0;
	}
	
	
	// used for interpolation in calcSoilTerm(), below
	private static final double[] VS_BINS = {150d, 250d, 400d, 700d, 1000d};
		
	// Soil depth model adapted from CY13 form
	private static final double calcSoilTerm(Coeffs c, double vs30, double z1p0) {
		if (Double.isNaN(z1p0)) return 0.0;
		double vsPow4 = vs30 * vs30 * vs30 * vs30;
		double z1ref = exp(-7.67 / 4.0 * log((vsPow4 + A) / B)) / 1000.0; // km

//		double z1c = (vs30 > 500.0) ? c.a46 :
//					 (vs30 > 300.0) ? c.a45 :
//					 (vs30 > 200.0) ? c.a44 : c.a43;

		// new interpolation algorithm
		double[] vsCoeff = {c.a43, c.a44, c.a45, c.a46, c.a46};
		double z1c = Interpolate.findY(VS_BINS, vsCoeff, vs30);
		
		return z1c * log((z1p0 + 0.01) / (z1ref + 0.01));
	}
		
	private static final double getPhiA(double Mw, double s1, double s2) {
		return Mw < 4.0 ? s1 :
			   Mw > 6.0 ? s2 : 
			   s1 + ((s2 - s1) / 2) * (Mw - 4.0);
	}
	
	private static final double getTauA(double Mw, double s3, double s4) {
		return Mw < 5.0 ? s3 :
			   Mw > 7.0 ? s4 :
			   s3 + ((s4 - s3) / 2) * (Mw - 5.0);
	}

	private static final double get_dAmp(double b, double c, double vLin,
			double vs30, double saRock) {
		if (vs30 >= vLin) return 0.0;
		return (-b * saRock) / (saRock + c) +
			    (b * saRock) / (saRock + c * pow(vs30 / vLin, N));
	}	

}
