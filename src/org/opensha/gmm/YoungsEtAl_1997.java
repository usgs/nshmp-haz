package org.opensha.gmm;

import static java.lang.Math.exp;
import static java.lang.Math.log;
import static java.lang.Math.min;
import static java.lang.Math.pow;
import static org.opensha.gmm.Imt.PGA;

/**
 * Abstract implementation of the subduction ground motion model by Youngs et
 * al. (1997). This implementation matches that used in the 2008 USGS NSHMP
 * where it is sometimes identified as the Geomatrix ground motion model.
 * This implementation has been modified from its original form to an NGA style
 * (S. Harmsen 7/13/2009) wherein mean ground motion varies continuously with
 * Vs30 (sigma remains the same as original). This is acheived through use of a
 * period-dependent site amplification function modified from Boore &
 * Atkinson (2008).
 * 
 * <p>This model supports both slab and interface type events. In the 2008
 * NSHMP, the 'interface' form is used with the Cascadia subduction zone models
 * and the 'slab' form is used with gridded 'deep' events in northern California
 * and the Pacific Northwest.</p>
 * 
 * <p><b>Note:</b> Direct instantiation of {@code GroundMotionModel}s is
 * prohibited. Use {@link Gmm#instance(Imt)} to retrieve an instance for a
 * desired {@link Imt}.</p>
 * 
 * <p><b>Reference:</b> Youngs, R.R., Chiou, S.-J., Silva, W.J., and Humphrey,
 * J.R., 1997, Strong ground motion ground motion models for subduction
 * zone earthquakes: Seismological Research Letters, v. 68, p. 58-73.</p>
 * 
 * <p><b>Component:</b> Geometric mean of two horizontal components</p>
 * 
 * @author Peter Powers
 * @see Gmm#YOUNGS_97_INTER
 * @see Gmm#YOUNGS_97_SLAB
 */
public abstract class YoungsEtAl_1997 implements GroundMotionModel {
	
	// notes from original OpenSHA implementation TODO revisit
	// NOTE RupTopDepthParam is used in a funny way here (see also Atkinson &
	// Boore sub). Currently it is not adjusted when updating an earthquake
	// rupture, but must be set manually. It defaults to 20km (the value
	// imposed in the 2008 NSHMP Cascadia subduction interface model. Any other
	// sources should update this value independently.


	static final String NAME = "Youngs et al. (1997)";
	
	static final CoefficientContainer CC = new CoefficientContainer("Youngs97.csv", Coeffs.class);
	static final CoefficientContainer CC_SA = new CoefficientContainer("ABsiteAmp.csv",
		CoeffsSiteAmp.class);
	
	static class Coeffs extends Coefficients {
		double gc1, gc1s, gc2, gc2s, gc3, gc3s, gc4, gc5;
	}

	static class CoeffsSiteAmp extends Coefficients {
		double blin, b1, b2;
	}

	// author declared constants
	private static final double[] VGEO = { 760.0, 300.0, 475.0 };
	private static final double GC0 = 0.2418;
	private static final double GCS0 = -0.6687;
	private static final double CI = 0.3846;
	private static final double CIS = 0.3643;
	private static final double GCH = 0.00607;
	private static final double GCHS = 0.00648;
	private static final double GMR = 1.414;
	private static final double GMS = 1.438;
	private static final double GEP = 0.554;

	// implementation constants
	// none
	
	private final Coeffs coeffs, coeffsPGA;
	private final CoeffsSiteAmp coeffsSA;
	

	YoungsEtAl_1997(Imt imt) {
		coeffs = (Coeffs) CC.get(imt);
		coeffsPGA = (Coeffs) CC.get(PGA);
		coeffsSA = (CoeffsSiteAmp) CC_SA.get(imt);
	}
	
	@Override
	public final ScalarGroundMotion calc(GmmInput props) {
		double mean = calcMean(coeffs, coeffsPGA, coeffsSA, props.Mw,
			props.rRup, props.zTop, props.vs30, isSlab());
		double sigma = calcStdDev(coeffs, props.Mw);
		return DefaultScalarGroundMotion.create(mean, sigma);
	}

	abstract boolean isSlab();

	private static final double calcMean(Coeffs c, Coeffs cPGA, CoeffsSiteAmp cSA,
			double Mw, double rRup, double zTop, double vs30, boolean slab) {
		
		double slabVal = slab ? 1 : 0;
		
		// NSHMP hazgridXnga caps slab events at M=8 after AB03 sub
		if (slab) Mw = Math.min(8.0, Mw);
		
		// reference PGA; determine nonlinear response using this value
		double gnd0p = GC0 + CI * slabVal;
		double gnd0, gz, g1, g2, g3, g4, ge, gm;
		int ir;
		if (vs30 > 520.0) { // rock
			gnd0 = GC0 + CI * slabVal; // no interface term ci for subduction
			gz = GCH;
			g1 = c.gc1;
			g2 = c.gc2;
			g3 = c.gc3;
			g4 = 1.7818;
			ge = 0.554;
			gm = GMR;
			ir = 0;
		} else { // soil
			gnd0 = GCS0 + CIS * slabVal; // no interface term cis for subduction
			gz = GCHS;
			g1 = c.gc1s;
			g2 = c.gc2s;
			g3 = c.gc3s;
			g4 = 1.097;
			ge = 0.617;
			gm = GMS;
			ir = 1;
		}

		double gndm = gnd0 + g1 + (gm * Mw) + g2 * Math.pow(10.0 - Mw, 3) + (gz * zTop);
		double arg = Math.exp(ge * Mw);
		double gnd = gndm + g3 * Math.log(rRup + g4 * arg);
		if (vs30 != VGEO[ir]) {
			// frankel mods for nonlin siteamp July 7/09
			double gndzp = gnd0p + zTop * GCH + cPGA.gc1;
			double gndmp = gndzp + GMR * Mw + cPGA.gc2 * pow(10.0 - Mw, 3);
			double argp = exp(GEP * Mw);
			double gndp = gndmp + cPGA.gc3 * log(rRup + 1.7818 * argp);
			double pganl = exp(gndp);
			gnd = gnd + baSiteAmp(cSA, pganl, vs30, VGEO[ir]);
		}
		return gnd;

	}

	private static final double calcStdDev(Coeffs c, double Mw) {
		// same sigma for soil and rock; sigma capped at M=8 per Youngs et al.
		return c.gc4 + c.gc5 * min(8.0, Mw);
	}

	
	// 12/12/2011 pp converted v1 and v2 to scalars
	private static final double V1 = 180.0;
	private static final double V2 = 300.0;

	private static final double A1 = 0.030;
	private static final double A2 = 0.090;
	private static final double A2FAC = 0.405465108;

	private static final double VREF = 760.0;
	private static final double DX = 1.098612289; // ln(a2/a1)
	private static final double DXSQ = 1.206948961;
	private static final double DXCUBE = 1.325968960;

	// ln(pga_low/0.1) where pga_low = 0.06
	private static final double PLFAC = -0.510825624;


	/*
	 * Utility method returns a site response value that is a continuous
	 * function of <code>vs30</code>: log(AMP at vs30)-log(AMP at vs30r). Value
	 * at <code>vs30 == vs30r</code> is unity. This function was adapted from
	 * hazSUBXngatest.f and is valid for 23 periods.
	 * @param pganl reference pga
	 * @param vs30 at a site of interest
	 * @param vs30r reference vs30, usually one value for soil and another for
	 *        rock
	 * @param per period index
	 * @return the site response correction
	 */
	private static double baSiteAmp(CoeffsSiteAmp c, double pganl, double vs30,
			double vs30r) {

		double dy, dyr, site, siter = 0.0;

		double bnl, bnlr;
		// some site term precalcs that are not M or d dependent
		if (V1 < vs30 && vs30 <= V2) {
			bnl = (c.b1 - c.b2) * log(vs30 / V2) / log(V1 / V2) + c.b2;
		} else if (V2 < vs30 && vs30 <= VREF) {
			bnl = c.b2 * log(vs30 / VREF) / log(V2 / VREF);
		} else if (vs30 <= V1) {
			bnl = c.b1;
		} else {
			bnl = 0.0;
		}

		if (V1 < vs30r && vs30r <= V2) {
			// repeat site term precalcs that are not M or d dependent @ ref.
			// vs.
			bnlr = (c.b1 - c.b2) * log(vs30r / V2) /
				log(V1 / V2) + c.b2;
		} else if (V2 < vs30r && vs30r <= VREF) {
			bnlr = c.b2 * log(vs30r / VREF) / log(V2 / VREF);
		} else if (vs30r <= V1) {
			bnlr = c.b1;
		} else {
			bnlr = 0.0;
		}

		dy = bnl * A2FAC; // ADF added line
		dyr = bnlr * A2FAC;
		site = c.blin * log(vs30 / VREF);
		siter = c.blin * log(vs30r / VREF);

		// Second part, nonlinear siteamp reductions below.
		if (pganl <= A1) {
			site = site + bnl * PLFAC;
			siter = siter + bnlr * PLFAC;
		} else if (pganl <= A2) {
			// extra lines smooth a kink in siteamp, pp 9-11 of boore sept
			// report. c and d from p 10 of boore sept report. Smoothing
			// introduces extra calcs in the range a1 < pganl < a2. Otherwise
			// nonlin term same as in june-july. Many of these terms are fixed
			// and are defined in data or parameter statements. Of course, if a1
			// and a2 change from their sept 06 values the parameters will also
			// have to be redefined. (a1,a2) represents a siteamp smoothing
			// range (units g)
			double cc = (3. * dy - bnl * DX) / DXSQ;
			double dd = (bnl * DX - 2. * dy) / DXCUBE;
			double pgafac = log(pganl / A1);
			double psq = pgafac * pgafac;
			site = site + bnl * PLFAC + (cc + dd * pgafac) * psq;
			cc = (3. * dyr - bnlr * DX) / DXSQ;
			dd = (bnlr * DX - 2. * dyr) / DXCUBE;
			siter = siter + bnlr * PLFAC + (cc + dd * pgafac) * psq;
		} else {
			double pgafac = log(pganl / 0.1);
			site = site + bnl * pgafac;
			siter = siter + bnlr * pgafac;
		}
		return site - siter;
	}

}
