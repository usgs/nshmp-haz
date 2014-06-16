package org.opensha.gmm;

import static java.lang.Math.log;
import static java.lang.Math.pow;
import static java.lang.Math.sqrt;
import static org.opensha.gmm.MagConverter.NONE;

import org.opensha.calc.ScalarGroundMotion;

/**
 * Implementation of the Tavakoli &amp; Pezeshk (2005) ground motion model for
 * stable continental regions. This implementation matches that used in the 2008
 * USGS NSHMP and comes in two additional magnitude converting (mb to Mw)
 * flavors to support the 2008 central and eastern US model.
 * 
 * <p><b>Note:</b> Direct instantiation of {@code GroundMotionModel}s is
 * prohibited. Use {@link Gmm#instance(IMT)} to retrieve an instance for a
 * desired {@link IMT}.</p>
 * 
 * <p><b>Implementation note:</b> Mean values are clamped per
 * {@link GMM_Utils#ceusMeanClip(IMT, double)}.</p>
 * 
 * <p><b>Reference:</b> Tavakoli, B., and Pezeshk, S., 2005,
 * Empirical-stochastic ground-motion prediction for eastern North America:
 * Bulletin of the Seismological Society of America, v. 95, p. 2283–2296.</p>
 * 
 * <p><b>Component:</b> not specified (avg horizontal implied)</p>
 * 
 * @author Peter Powers
 * @see Gmm#TP_05
 * @see Gmm#TP_05_AB
 * @see Gmm#TP_05_J
 */
public class TavakoliPezeshk_2005 implements GroundMotionModel, ConvertsMag {


	// TODO NOTE in docs that frankel terms are used for soft rock.
	
//	 * TODO
//	 * 		- needs to support Rrup
//	 * 		- vs30 param, or kill in favor of hard/soft rock options
//	 * 		  as other ceus att rels
//	 * 		- rem: 2km gridded dtor min was removed

	// notes from original implementation and fortran:
	//
	// c1 below is based on a CEUS conversion from c1h where c1h Vs30 is in
	// the NEHRP A range (Vs30 = ??). Frankel dislikes the use of wus
	// siteamp for ceus. So for all periods we use Frankel 1996 terms.
	// c1 modified at 0.1, 0.3, 0.5, and 2.0 s for Frankel ceus amp. mar 19
	// 2007.
	// c1 for 1hz 5hz and pga also use the Fr. CEUS a->bc factors developed
	// in 1996(?).
	// corrected c1(0.3s) to 0.0293 from K Campbell email Oct 13 2009.
	// c1 checked for pga, 1hz and 5hz apr 17 2007. c1(0.4s) added June 30
	//
	// c for c15, corrected value for the 0.5-s or 2 Hz motion, from email Pezeshk dec 7 2007

	
	// TODO fix clamp values (not implemented here yet) to match other CEUS gmms

	static final String NAME = "Tavakoli \u0026 Pezeshk (2005)";
	
	static final CoefficientContainer CC = new CoefficientContainer("TP05.csv", Coeffs.class);
	
	static class Coeffs extends Coefficients {
		double c1, c1h, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14, c15, c16;
	}
	
	private final Coeffs coeffs;

	TavakoliPezeshk_2005(IMT imt) {
		coeffs = (Coeffs) CC.get(imt);
	}
	
	@Override
	public final ScalarGroundMotion calc(GMM_Input props) {
		return DefaultScalarGroundMotion.create(
			calcMean(coeffs, converter().convert(props.Mw), props.rRup, props.vs30),
			calcStdDev(coeffs, props.Mw));
	}

	@Override
	public MagConverter converter() {
		return NONE;
	}

	private static final double calcMean(Coeffs c, double Mw, double rRup,
			double vs30) {
		
		// TODO clean
		
		//boolean sp = period < 0.5 && period > 0.02;
//		double c5sq = c.c5 * c.c5;

		// c R: For near-surface dtor a singularity is possible. Limit at 2 km
		// minimum.
		// NOTE I do not think this is important; singularity would require
		// a site exactly on a fault trace; this situation is not covered in
		// hazFX
		// double H1= Math.max(dtor[kk],2.0);
		// double H1sq=H1*H1;
		// above is now handled by reading rRup (and dtor for gridded)

//		if (magType == LG_PHASE) mag = Utils.mblgToMw(magConvCode, mag);
		//vs30 = 760;
		double f1;
		if (vs30 >= 1500.0) {
			f1 = c.c1h + c.c2 * Mw + c.c3 * pow((8.5 - Mw), 2.5);
		} else if (vs30 > 900.0) {
			f1 = 0.5 * (c.c1h + c.c1) + c.c2 * Mw + c.c3 * pow((8.5 - Mw), 2.5);
		} else {
			f1 = c.c1 + c.c2 * Mw + c.c3 * pow((8.5 - Mw), 2.5);
		}
		double cor = Math.exp(c.c6 * Mw + c.c7 * pow((8.5 - Mw), 2.5));
		
//		System.out.println("cor: " + cor);
//		double corsq = cor * cor;

		double f2 = c.c9 * log(rRup + 4.5);
//		System.out.println("c9: " + c9 + " rRup: " + rRup);
		if (rRup > 70.0) f2 = f2 + c.c10 * log(rRup / 70.0);
		if (rRup > 130.0) f2 = f2 + c.c11 * log(rRup / 130.0);
		double R = sqrt(rRup * rRup + c.c5 * c.c5 * cor * cor);
		double f3 = (c.c4 + c.c13 * Mw) * log(R) + (c.c8 + c.c12 * Mw) * R;
		double gnd = f1 + f2 + f3;

		return GMM_Utils.ceusMeanClip(c.imt, gnd);
	}

	private static final double calcStdDev(Coeffs c, double Mw) {
		// TODO clean
		// if (magType == LG_PHASE) mag = Utils.mblgToMw(magConvCode, mag);
        return (Mw < 7.2) ? c.c14 + c.c15 * Mw : c.c16;
	}

}
