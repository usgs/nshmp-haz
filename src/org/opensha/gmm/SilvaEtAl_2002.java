package org.opensha.gmm;

import static java.lang.Math.exp;
import static org.opensha.gmm.SiteClass.HARD_ROCK;
import static org.opensha.gmm.MagConverter.NONE;

import java.util.Map;

/**
 * Implementation of the Silva et al. (2002) ground motion model for stable
 * continental regions. This implementation matches that used in the 2008 USGS
 * NSHMP and comes in two additional magnitude converting (mb to Mw) flavors to
 * support the 2008 central and eastern US model.
 * 
 * <p><b>Note:</b> Direct instantiation of {@code GroundMotionModel}s is
 * prohibited. Use {@link Gmm#instance(Imt)} to retrieve an instance for a
 * desired {@link Imt}.</p>
 * 
 * <p><b>Implementation note:</b> Mean values are clamped per
 * {@link GmmUtils#ceusMeanClip(Imt, double)}.</p>
 * 
 * <p><b>Reference:</b> Silva, W., Gregor, N., and Darragh, R., 2002,
 * Development of hard rock attenuation relations for central and eastern North
 * America, internal report from Pacific Engineering, November 1, 2002,
 * http://www.pacificengineering.org/CEUS/
 * Development%20of%20Regional%20Hard_ABC.pdf</p>
 * 
 * <p><b>Component:</b> average horizontal (not clear from publication)</p>
 * 
 * @author Peter Powers
 * @see Gmm#SILVA_02
 * @see Gmm#SILVA_02_AB
 * @see Gmm#SILVA_02_J
 */
public class SilvaEtAl_2002 implements GroundMotionModel, ConvertsMag {

	// notes from original implementation and fortran:
	//
	// c1 from c1hr using A->BC factors, 1.74 for 0.1s, 1.72 for 0.3s, 1.58 for
	// 0.5s, and 1.20 for 2s
	// this from A Frankel advice, Mar 14 2007. For 25 hz use PGA amp.
	// For BC at 2.5 hz use interp between .3 and .5. 1.64116 whose log is
	// 0.4953
	//
	// c note very high sigma for longer period SA

	static final String NAME = "Silva et al. (2002)";

	static final CoefficientsNew COEFFS = new CoefficientsNew("Silva02.csv");

	private static final class Coeffs {

		double c1, c1hr, c2, c4, c6, c7, c10, σ;

		Coeffs(Map<String, Double> coeffs) {
			c1 = coeffs.get("c1");
			c1hr = coeffs.get("c1hr");
			c2 = coeffs.get("c2");
			c4 = coeffs.get("c4");
			c6 = coeffs.get("c6");
			c7 = coeffs.get("c7");
			c10 = coeffs.get("c10");
			σ = coeffs.get("sigma");
		}
	}

	private final Coeffs coeffs;
	private final Imt imt;

	SilvaEtAl_2002(final Imt imt) {
		this.imt = imt;
		coeffs = new Coeffs(COEFFS.get(imt));
	}

	@Override public final ScalarGroundMotion calc(final GmmInput props) {
		SiteClass siteClass = GmmUtils.ceusSiteClass(props.vs30);
		double μ = calcMean(coeffs, imt, converter().convert(props.Mw), props.rJB, siteClass);
		return DefaultScalarGroundMotion.create(μ, coeffs.σ);
	}

	@Override public MagConverter converter() {
		return NONE;
	}

	private static final double calcMean(final Coeffs c, final Imt imt, final double Mw,
			final double rJB, final SiteClass siteClass) {

		double c1 = (siteClass == HARD_ROCK) ? c.c1hr : c.c1;
		double gnd0 = c1 + c.c2 * Mw + c.c10 * (Mw - 6.0) * (Mw - 6.0);
		double fac = c.c6 + c.c7 * Mw;
		double gnd = gnd0 + fac * Math.log(rJB + exp(c.c4));

		return GmmUtils.ceusMeanClip(imt, gnd);
	}

}
