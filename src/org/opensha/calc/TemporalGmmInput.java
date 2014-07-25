package org.opensha.calc;

import org.opensha.eq.forecast.Rupture;
import org.opensha.gmm.GmmInput;
import org.opensha.gmm.GroundMotionModel;

/**
 * A {@link GroundMotionModel} input that carries {@link Rupture} rate
 * information along with it
 * 
 * @author Peter Powers
 * @see GmmInput
 */
final class TemporalGmmInput extends GmmInput {

	final double rate;

	TemporalGmmInput(double rate, double Mw, double rJB, double rRup, double rX, double dip,
		double width, double zTop, double zHyp, double rake, double vs30, boolean vsInf,
		double z2p5, double z1p0) {

		super(Mw, rJB, rRup, rX, dip, width, zTop, zHyp, rake, vs30, vsInf, z2p5, z1p0);
		this.rate = rate;
	}

	@Override public String toString() {
		return getClass().getSimpleName() + " [rate=" + String.format("%.4g", rate) + " " +
			super.toString() + "]";
	}
}
