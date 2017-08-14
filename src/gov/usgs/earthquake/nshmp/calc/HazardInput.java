package gov.usgs.earthquake.nshmp.calc;

import gov.usgs.earthquake.nshmp.eq.model.Rupture;
import gov.usgs.earthquake.nshmp.gmm.GmmInput;
import gov.usgs.earthquake.nshmp.gmm.GroundMotionModel;

/**
 * A {@link GroundMotionModel} input that carries {@link Rupture} rate
 * information along with it
 *
 * @author Peter Powers
 * @see GmmInput
 */
public final class HazardInput extends GmmInput {
  // TODO package rpivatize

  final double rate;

  public HazardInput(
      double rate,
      double Mw, double rJB, double rRup, double rX,
      double dip, double width, double zTop, double zHyp, double rake,
      double vs30, boolean vsInf, double z1p0, double z2p5) {

    super(Mw, rJB, rRup, rX, dip, width, zTop, zHyp, rake, vs30, vsInf, z1p0, z2p5);
    this.rate = rate;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + " [rate=" + String.format("%.4g", rate) + " " +
        super.toString() + "]";
  }

}
