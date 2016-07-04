package org.opensha2.gmm;

import java.util.Set;

/**
 * Static utiltiy methods targeted at Matlab users wishing to compute scalar
 * ground motions.
 *
 * @author Peter Powers
 */
public class MatUtil {

  /**
   * Compute the median ground motion and its standard deviation for a specified
   * model, intensity measure type (Imt), and source
   *
   * <p>{@code enum} types are identified in matlab as e.g. {@code Gmm$ASK_14}
   * .
   *
   * @param model to use
   * @param imt intensity measure type (e.g. {@code PGA}, {@code SA1P00})
   * @param source parameterization
   * @return a two-element double[] containing the natural log of the median
   *         ground motion and its standard deviation
   */
  public static double[] calc(Gmm model, Imt imt, GmmInput source) {
    ScalarGroundMotion sgm = model.instance(imt).calc(source);
    return new double[] { sgm.mean(), sgm.sigma() };
  }

  /**
   * Compute a spectrum of ground motions and their standard deviations for a
   * specified model and source. All spectral periods supported by the model are
   * returned.
   *
   * <p>{@code enum} types are identified in matlab as e.g. {@code Gmm$ASK_14}
   * .
   *
   * @param model to use
   * @param source parameterization
   * @return a {@link MatSpectrum} data container
   */
  public static MatSpectrum spectrum(Gmm model, GmmInput source) {
    Set<Imt> imts = model.responseSpectrumIMTs();
    MatSpectrum spectrum = new MatSpectrum(imts.size());
    int i = 0;
    for (Imt imt : imts) {
      ScalarGroundMotion sgm = model.instance(imt).calc(source);
      spectrum.periods[i] = imt.period();
      spectrum.means[i] = sgm.mean();
      spectrum.sigmas[i] = sgm.sigma();
      i++;
    }
    return spectrum;
  }

}
