package org.opensha.gmm;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static java.lang.Math.exp;
import static java.lang.Math.log;
import static java.lang.Math.sqrt;
import static org.opensha.eq.TectonicSetting.ACTIVE_SHALLOW_CRUST;
import static org.opensha.eq.TectonicSetting.SUBDUCTION_INTERFACE;
import static org.opensha.eq.TectonicSetting.VOLCANIC;
import static org.opensha.gmm.FaultStyle.NORMAL;
import static org.opensha.gmm.FaultStyle.REVERSE;
import static org.opensha.gmm.FaultStyle.REVERSE_OBLIQUE;
import static org.opensha.gmm.FaultStyle.STRIKE_SLIP;
import static org.opensha.gmm.Imt.PGA;

import org.opensha.calc.ScalarGroundMotion;
import org.opensha.eq.TectonicSetting;

/**
 * Abstract implementation of the ground motion model by McVerry et al. (2000).
 * 
 * <p><b>Implementation details:</b><ul><li>McVerry proposes a hanging wall term
 * but it was not specifically modeled and is not implemented here.</li><li> New
 * Zealand uses site classes that do not strictly correspond to fixed ranges of
 * Vs30, in contrast with the US model; NZ soil site classes C and D consider
 * stratification and site-specific response period. This implementation uses
 * the following New Zealend site class to Vs30 values for convenience and
 * consistency with the majority of other ground motion models:<ul><li>Class A:
 * 1500 &lt; Vs30</li><li>Class B: 360 &lt; Vs30 &le; 1500</li><li>Class C: 250
 * &lt; Vs30 &le; 360</li><li>Class D: 150 &lt; Vs30 &le; 250</li><li>Class E:
 * s30 &le; 150 (not supported)</li></ul></li></ul></p>
 * 
 * <p><b>Model applicability:</b> This needs work (TODO). Prior implementations
 * restricted distance to 400km, foacl depths to 100km, and Magnitudes between
 * 5.0 and 8.5. However the model supports a range of tectonic settings and
 * McVerry et al. (2006) restrict magnitude to 7.5 and distance to 400km for
 * curstal earthquakes, and restrict magnitudes to 8.0 and distances to 500km
 * for subduction earthquake.<p>
 * 
 * <p><b>Reference:</b> McVerry, G.H., Zhao, J.X., Abrahamson, N.A., and
 * Somerville, P.G., 2000, Crustal and subduction zone attenuation realations
 * for New Zealand earthquakes: Proc 12th World conference on earthquake
 * engineering, Auckland, New Zealand, February, 2000.</p>
 * 
 * <p><b>Reference:</b> McVerry, G.H., Zhao, J.X., Abrahamson, N.A., and
 * Somerville, P.G., 2000, New Zealand acceleration response spectrum
 * attenuation relations for crustal and subduction zone earthquakes: Bulletin
 * of the New Zealand Society of Earthquake Engineering, v. 39, n. 4, p.
 * 1-58.</p>
 * 
 * <p><b>Component:</b> Model supports geometric mean or maximum of two
 * horizontal components; only concrete implementations of max-horizontal
 * component are provided at this time.</p>
 * 
 * @author Brendon A. Bradley
 * @author Peter Powers
 */
public abstract class McVerryEtAl_2000 implements GroundMotionModel {

	// TODO need hypocentral depth for subduction
	
	// NOTE: Changed rake cutoffs to be symmetric and conform with 2006 pub.
	// NOTE: updated NZ_SourceID to collapse SR RS keys

	static final String NAME = "McVerry et al. (2000)";

	// geomean and max-horizontal coefficients
	static final CoefficientContainer CC = new CoefficientContainer("McVerry00_gm.csv",
		Coeffs.class);
	static final CoefficientContainer CC_MH = new CoefficientContainer("McVerry00_mh.csv",
		Coeffs.class);

	static class Coeffs extends Coefficients {
		// 'as' and 'y' suffixes indicate attribution to
		// Abrahamson & Silva or Youngs et al.
		double c1, c3as, c5, c8, c10as, c11, c13y, c15, c17, c20, c24, c29, c30as, c33as, c43, c46,
				sigma6, sigSlope, tau;
	}

	// author declared constants
	private static final double C4AS = -0.144;
	private static final double C6AS = 0.17;
	private static final double C12Y = 1.414;
	private static final double C18Y = 1.7818;
	private static final double C19Y = 0.554;
	private static final double C32 = -0.2;

	private final Coeffs coeffs, coeffsPGA, coeffsPGAprime;

	McVerryEtAl_2000(Imt imt) {
		coeffs = (Coeffs) (isGeomean() ? CC.get(imt) : CC_MH.get(imt));
		coeffsPGA = (Coeffs) (isGeomean() ? CC.get(PGA) : CC_MH.get(PGA));
		coeffsPGAprime = pgaPrime(isGeomean());
	}

	@Override public final ScalarGroundMotion calc(GmmInput props) {
		double mean = calcMean(coeffs, coeffsPGA, coeffsPGAprime, props.Mw, props.rRup, props.zHyp,
			props.rake, props.vs30, tectonicSetting());
		double sigma = calcStdDev(coeffs, props.Mw);
		return DefaultScalarGroundMotion.create(mean, sigma);
	}

	/* as opposed to greatest horizontal */
	abstract boolean isGeomean();

	/* as opposed to subduction */
	abstract TectonicSetting tectonicSetting();

	private static double calcMean(Coeffs c, Coeffs cPGA, Coeffs cPGAp, double Mw, double rRup,
			double zHyp, double rake, double vs30, TectonicSetting tect) {

		double pgaMean = calcMeanBase(cPGA, Mw, rRup, zHyp, rake, vs30, tect);

		if (c.imt == PGA) return pgaMean;

		double pga_prime = exp(calcMeanBase(cPGAp, Mw, rRup, zHyp, rake, vs30, tect));
		double sa_prime = exp(calcMeanBase(c, Mw, rRup, zHyp, rake, vs30, tect));
		return log(sa_prime * exp(pgaMean) / pga_prime);
	}

	private static double calcMeanBase(Coeffs c, double Mw, double rRup, double zHyp, double rake,
			double vs30, TectonicSetting tect) {

		FaultStyle style = rakeToFaultStyle(rake);

		double lnSA_AB = (tect == ACTIVE_SHALLOW_CRUST || tect == VOLCANIC) ? calcCrustal(c, Mw,
			rRup, tect, style) : calcSubduction(c, Mw, rRup, zHyp, tect);

		double lnSA_CD = calcSiteTerm(c, vs30, lnSA_AB);

		return lnSA_AB + lnSA_CD;
	}

	// @formatter:off
	private static double calcCrustal(Coeffs c, double Mw, double rRup, TectonicSetting tect,
			FaultStyle style) {

		double rVol = (tect == VOLCANIC) ? rRup : 0.0;

		double faultTerm = (style == REVERSE) ? c.c33as :
			               (style == REVERSE_OBLIQUE) ? c.c33as * 0.5 :
			               (style == NORMAL) ? C32 : 0.0;

		return c.c1 + C4AS * (Mw - 6.0) +
			   c.c3as * (8.5 - Mw) * (8.5 - Mw) +
			   c.c5 * rRup + (c.c8 +
					C6AS * (Mw - 6.0)) * log(sqrt(rRup * rRup + c.c10as * c.c10as)) +
			   c.c46 * rVol + faultTerm;
	}
	
	private static double calcSubduction(Coeffs c, double Mw, double rRup, double zHyp,
			TectonicSetting tect) {

		double magTerm = 10 - Mw;
		
		double subTerm = (tect == SUBDUCTION_INTERFACE) ? c.c24 : 0.0;
		
		return c.c11 + (C12Y + (c.c15
					- c.c17) * C19Y) * (Mw - 6) +
			   c.c13y * magTerm * magTerm * magTerm +
			   c.c17 * log(rRup + C18Y * exp(C19Y * Mw)) +
			   c.c20 * zHyp + subTerm;
		
		// NOTE: tectonic setting terms from publication:
		// c.c24 * SI + c.c46 * rVol * (1 - DS);
		// 
		// volcanic sources will always be fed to calcCrustal so rVol will
		// alwyas be 0.0 here; only interface (or not) matters.
	}

	private static double calcSiteTerm(Coeffs c, double vs30, double lnSA_AB) {
		SiteClass siteClass = SiteClass.fromVs30(vs30);
		checkState(siteClass != SiteClass.E);
		return (siteClass == SiteClass.C) ? c.c29 :
			   (siteClass == SiteClass.D) ? c.c30as * log(exp(lnSA_AB) + 0.03) + c.c43 : 0.0;
	}

	private double calcStdDev(Coeffs c, double Mw) {
		double sigma = c.sigma6 +
			((Mw >= 7.0) ? c.sigSlope : (Mw <= 5.0) ? -c.sigSlope : c.sigSlope * (Mw - 6.0));
		return sqrt(sigma * sigma + c.tau * c.tau);
	}
	// @formatter:on

	/*
	 * New Zealand site classes; these do not stricly correspond to ranges of
	 * vs30 values
	 */
	private static enum SiteClass {
		A(1500.0),
		B(360.0),
		C(250.0),
		D(150.0),
		E(0.0);
		private double min;

		private SiteClass(double min) {
			this.min = min;
		}

		static SiteClass fromVs30(double vs30) {
			checkArgument(vs30 > 0.0);
			for (SiteClass siteClass : values()) {
				if (vs30 > siteClass.min) return siteClass;
			}
			throw new IllegalStateException("Shouldn't be here");
		}
	}

	private static FaultStyle rakeToFaultStyle(double rake) {
		if ((rake > 33 && rake <= 56) || (rake >= 124 && rake < 147)) {
			return REVERSE_OBLIQUE;
		} else if (rake > 56 && rake < 124) {
			return REVERSE;
		} else if (rake > -147 && rake < -33) {
			return NORMAL;
		} else {
			// rake <= -147 || rake >= 147
			// rake <=   33 && rake >= -33
			return STRIKE_SLIP; 
		}
	}
	
	// TODO clean
	public static void main(String[] args) {
		System.out.println(rakeToFaultStyle(10));  // SS
		System.out.println(rakeToFaultStyle(45));  // SR
		System.out.println(rakeToFaultStyle(70));  // R
		System.out.println(rakeToFaultStyle(110)); // R
		System.out.println(rakeToFaultStyle(132)); // SR
		System.out.println(rakeToFaultStyle(168)); // SS
		
		System.out.println(rakeToFaultStyle(-10));  // SS
		System.out.println(rakeToFaultStyle(-45));  // N
		System.out.println(rakeToFaultStyle(-70));  // N
		System.out.println(rakeToFaultStyle(-110)); // N
		System.out.println(rakeToFaultStyle(-132)); // N
		System.out.println(rakeToFaultStyle(-168)); // SS
	}

	private static Coeffs pgaPrime(boolean isGeomean) {
		Coeffs c = new Coeffs();
		if (isGeomean) {
			c.c1 = 0.07713;
			c.c3as = 0.0;
			c.c5 = -0.00898;
			c.c8 = -0.73728;
			c.c10as = 5.6;
			c.c11 = 8.08611;
			c.c13y = 0.0;
			c.c15 = -2.552;
			c.c17 = -2.49894;
			c.c20 = 0.0159;
			c.c24 = -0.43223;
			c.c29 = 0.3873;
			c.c30as = -0.23;
			c.c33as = 0.26;
			c.c43 = -0.31036;
			c.c46 = -0.0325;
			c.sigma6 = 0.5099;
			c.sigSlope = -0.0259;
			c.tau = 0.2469;
		} else {
			// max horizontal
			c.c1 = 0.1813;
			c.c3as = 0.0;
			c.c5 = -0.00846;
			c.c8 = -0.75519;
			c.c10as = 5.6;
			c.c11 = 8.10697;
			c.c13y = 0.0;
			c.c15 = -2.552;
			c.c17 = -2.48795;
			c.c20 = 0.01622;
			c.c24 = -0.41369;
			c.c29 = 0.44307;
			c.c30as = -0.23;
			c.c33as = 0.26;
			c.c43 = -0.29648;
			c.c46 = -0.03301;
			c.sigma6 = 0.5035;
			c.sigSlope = -0.0635;
			c.tau = 0.2598;
		}
		return c;
	}

	// TODO clean and/or implement
	// public void setEqkRupture(EqkRupture eqkRupture) throws
	// InvalidRangeException {
	//
	// magParam.setValueIgnoreWarning(new Double(eqkRupture.getMag()));
	// setFaultTypeFromRake(eqkRupture.getAveRake());
	// this.eqkRupture = eqkRupture;
	// setPropagationEffectParams();
	//
	// if (tecRegType.equals(FLT_TEC_ENV_INTERFACE) ||
	// tecRegType.equals(FLT_TEC_ENV_INTERFACE)) {
	// //Determine the focal depth
	// // this is problematic, see ticket #438
	// RuptureSurface surf = this.eqkRupture.getRuptureSurface();
	// double hypoLon = 0.0;
	// double hypoLat = 0.0;
	// double hypoDep = 0.0;
	// double cnt = 0.0;
	// for(Location loc: surf.getEvenlyDiscritizedListOfLocsOnSurface()) {
	// hypoLon += loc.getLongitude();
	// hypoLat += loc.getLatitude();
	// hypoDep += loc.getDepth();
	// cnt += 1;
	// }
	//
	// hypoLon = hypoLon / cnt;
	// hypoLat = hypoLat / cnt;
	// hypoDep = hypoDep / cnt;
	// focalDepthParam.setValueIgnoreWarning(new Double(hypoDep));
	// }
	//
	//
	// }

	// // Computing the hypocentral depth
	// // System.out.println("Zhao et al -->"+this.eqkRupture.getInfo());
	//
	// RuptureSurface surf = this.eqkRupture.getRuptureSurface();
	//
	// // ----------------------------------------------------------------------
	// MARCO 2010.03.15
	// // Compute the hypocenter as the middle point of the rupture
	// // this is problematic, see ticket #438
	// double hypoLon = 0.0;
	// double hypoLat = 0.0;
	// double hypoDep = 0.0;
	// double cnt = 0.0;
	// for(Location loc: surf.getEvenlyDiscritizedListOfLocsOnSurface()) {
	// hypoLon += loc.getLongitude();
	// hypoLat += loc.getLatitude();
	// hypoDep += loc.getDepth();
	// cnt += 1;
	// }
	// hypoLon = hypoLon / cnt;
	// hypoLat = hypoLat / cnt;
	// hypoDep = hypoDep / cnt;
	// hypodepth = hypoDep;
	// // System.out.println("computed hypocentral depth:"+hypodepth);
	// // hypodepth = this.eqkRupture.getHypocenterLocation().getDepth();
	// // System.out.println("real hypocentral depth:"+hypodepth);
	// // ----------------------------------------------------------------------
	// MARCO 2010.03.15

}
