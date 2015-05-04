package org.opensha.gmm;

import static java.lang.Math.exp;
import static java.lang.Math.log;
import static java.lang.Math.max;
import static java.lang.Math.pow;
import static org.opensha.gmm.FaultStyle.REVERSE;

/**
 * Implementation of the ground motion model for shallow crustal earthquakes by
 * Sadigh et al. (1997). This implementation supports soil and rock sites, the
 * rather too brad cutoff for which is vs30=750 m/s.
 * 
 * <p><b>Note:</b> Direct instantiation of {@code GroundMotionModel}s is
 * prohibited. Use {@link Gmm#instance(Imt)} to retrieve an instance for a
 * desired {@link Imt}.</p>
 * 
 * <p><b>Reference:</b> Sadigh, K., Chang, C.-Y. , Egan, J.A., Makdisi, F., and
 * Youngs, R.R., 1997, Attenuation relationships for shallow crustal earthquakes
 * based on California strong motion data, Seismological Research Letters,
 * 68(1), 180-189.</p>
 * 
 * <p><b>Component:</b> geometric mean of two horizontal components</p>
 * 
 * @author Peter Powers
 */
public class SadighEtAl_1997 implements GroundMotionModel {

	// TODO this needs better site type identification by vs30 value
	// TODO model is only applicable to 100km

	/*
	 * The Sadigh model provides different functional forms for soil and rock
	 * site classes, has numerous magnitude and style-of-faulting coefficient
	 * variants. This implementation nests style-of-faulting specific coefficents
	 * in the coeff tables and keeps four uniform tables for the two site
	 * classes supported with a low and high magnitude flavor of each. This
	 * yields some redundancy in the coefficent tables but reduces the need for
	 * conditional expressions.
	 */

	static final String NAME = "Sadigh et al. (1997)";

	static final CoefficientsNew CC_BC_LO, CC_BC_HI, CC_D_LO, CC_D_HI;

	static {
		CC_BC_LO = new CoefficientsNew("Sadigh97_BClo.csv", Coeffs.class);
		CC_BC_HI = new CoefficientsNew("Sadigh97_BChi.csv", Coeffs.class);
		CC_D_LO = new CoefficientsNew("Sadigh97_Dlo.csv", Coeffs.class);
		CC_D_HI = new CoefficientsNew("Sadigh97_Dhi.csv", Coeffs.class);
	}

	static class Coeffs extends CoefficientsOld {
		double c1r, c1ss, c2, c3, c4, c5, c6r, c6ss, c7, sig0, cM, sigMax;
	}

	// author declared constants
	// none

	// implementation constants
	private final double VS30_CUT = 750.0;

	private final Coeffs coeffs_bc_lo, coeffs_bc_hi, coeffs_d_lo, coeffs_d_hi;

	SadighEtAl_1997(Imt imt) {
		coeffs_bc_lo = (Coeffs) CC_BC_LO.get(imt);
		coeffs_bc_hi = (Coeffs) CC_BC_LO.get(imt);
		coeffs_d_lo = (Coeffs) CC_D_LO.get(imt);
		coeffs_d_hi = (Coeffs) CC_D_LO.get(imt);
	}

	@Override public final ScalarGroundMotion calc(GmmInput props) {
		FaultStyle faultStyle = GmmUtils.rakeToFaultStyle_NSHMP(props.rake);

		double mean, sigma;

		if (props.vs30 > VS30_CUT) {
			// rock
			Coeffs c = props.Mw <= 6.5 ? coeffs_bc_lo : coeffs_bc_hi;
			mean = calcRockMean(c, props.Mw, props.rRup, faultStyle);
			sigma = calcStdDev(c, props.Mw);
		} else {
			// soil
			Coeffs c = props.Mw <= 6.5 ? coeffs_d_lo : coeffs_d_hi;
			mean = calcSoilMean(c, props.Mw, props.rRup, faultStyle);
			sigma = calcStdDev(c, props.Mw);
		}

		return DefaultScalarGroundMotion.create(mean, sigma);
	}

	private double calcRockMean(Coeffs c, double Mw, double rRup, FaultStyle style) {
		// modified to saturate above Mw=8.5

		// rock site coeffs are not dependent on style-of-faulting
		// so we just use the rock flavor (c1r == c1ss)

		double lnY = c.c1r + c.c2 * Mw + c.c3 * pow(max(8.5 - Mw, 0.0), 2.5) + c.c4 *
			log(rRup + exp(c.c5 + c.c6r * Mw)) + c.c7 * log(rRup + 2);

		// scale reverse amplitudes by 1.2; 0.18232 = ln(1.2)
		return (style == REVERSE) ? lnY + 0.18232 : lnY;
	}

	private double calcSoilMean(Coeffs c, double Mw, double rRup, FaultStyle style) {
		// modified to saturate above Mw=8.5

		double c1 = (style == REVERSE) ? c.c1r : c.c1ss;
		double c6 = (style == REVERSE) ? c.c6r : c.c6ss;

		return c1 + c.c2 * Mw - c.c3 * log(rRup + c.c4 * exp(c.c5 * Mw)) + c6 + c.c7 *
			pow(max(8.5 - Mw, 0.0), 2.5);
	}

	private double calcStdDev(Coeffs c, double Mw) {
		// mMax_bc = 7.21, mMax_d = 7.0, coeff tables were populated
		// with maxSigma for soil sites, maxSigma for rock were
		// included in publication
		return max(c.sig0 + c.cM * Mw, c.sigMax);
	}

}
