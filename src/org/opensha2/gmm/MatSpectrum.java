package org.opensha2.gmm;

/**
 * Ground motion spectrum data container for use with Matlab.
 *
 * <p><b>Note:</b> This class is not intended for use in production code.</p>
 *
 * @author Peter Powers
 * @see MatUtil
 */
public class MatSpectrum {

  public final double[] periods;
  public final double[] means;
  public final double[] sigmas;

  MatSpectrum(int size) {
    periods = new double[size];
    means = new double[size];
    sigmas = new double[size];
  }

}
