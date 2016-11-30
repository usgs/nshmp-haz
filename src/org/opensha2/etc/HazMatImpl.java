package org.opensha2.etc;

import org.opensha2.gmm.Gmm;
import org.opensha2.gmm.GmmInput;
import org.opensha2.gmm.Imt;
import org.opensha2.gmm.ScalarGroundMotion;

import java.util.Set;

@SuppressWarnings("javadoc")
public class HazMatImpl {

  public double[] gmmMean(
      String gmmStr,
      String imtStr,
      double Mw,
      double rJB,
      double rRup,
      double rX,
      double dip,
      double width,
      double zTop,
      double zHyp,
      double rake,
      double vs30,
      boolean vsInf,
      double z1p0,
      double z2p5) {

    GmmInput input = toInput(
        Mw,
        rJB, rRup, rX,
        dip, width, zTop, zHyp, rake,
        vs30, vsInf, z1p0, z2p5);
    Gmm gmm = Gmm.valueOf(gmmStr);
    Imt imt = Imt.valueOf(imtStr);
    ScalarGroundMotion sgm = gmm.instance(imt).calc(input);
    return new double[] { sgm.mean(), sgm.sigma() };
  }

  public double[][] gmmSpectrum(
      String gmmStr,
      double Mw,
      double rJB,
      double rRup,
      double rX,
      double dip,
      double width,
      double zTop,
      double zHyp,
      double rake,
      double vs30,
      boolean vsInf,
      double z1p0,
      double z2p5) {

    GmmInput input =
        toInput(
            Mw,
            rJB, rRup, rX,
            dip, width, zTop, zHyp, rake,
            vs30, vsInf, z1p0, z2p5);
    Gmm gmm = Gmm.valueOf(gmmStr);
    return gmmSpectrum(gmm, input);
  }

  private double[][] gmmSpectrum(Gmm gmm, GmmInput input) {
    Set<Imt> imts = gmm.responseSpectrumIMTs();
    double[][] spectrum = new double[3][imts.size()];
    int i = 0;
    for (Imt imt : imts) {
      ScalarGroundMotion sgm = gmm.instance(imt).calc(input);
      spectrum[0][i] = imt.period();
      spectrum[1][i] = sgm.mean();
      spectrum[2][i] = sgm.sigma();
      i++;
    }
    return spectrum;
  }

  private static GmmInput toInput(
      double Mw,
      double rJB,
      double rRup,
      double rX,
      double dip,
      double width,
      double zTop,
      double zHyp,
      double rake,
      double vs30,
      boolean vsInf,
      double z1p0,
      double z2p5) {

    return GmmInput.builder()
        .mag(Mw)
        .rJB(rJB)
        .rRup(rRup)
        .rX(rX)
        .dip(dip)
        .width(width)
        .zTop(zTop)
        .zHyp(zHyp)
        .rake(rake)
        .vs30(vs30)
        .vsInf(vsInf)
        .z1p0(z1p0)
        .z2p5(z2p5)
        .build();
  }

}
