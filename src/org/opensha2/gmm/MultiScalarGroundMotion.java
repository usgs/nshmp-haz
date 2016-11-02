package org.opensha2.gmm;

import org.opensha2.data.Data;

import java.util.Arrays;

/**
 * Extension to {@code ScalarGroundMotion} heirarchy added in support of
 * NGA-East. The {@link #mean} method of this class returns the weighted mean of
 * the NGA-East
 *
 * @author Peter Powers
 */
public class MultiScalarGroundMotion extends DefaultScalarGroundMotion {

  // TODO package privatize if possible? no weight validation is
  // performed or length agreement checking

  // TODO array exposure is dangerous and shoul dbe changed in favor of
  // immutable lists; this is  agood candidate for immutable data arrays

  private final double[] means;
  private final double[] weights;

  MultiScalarGroundMotion(double[] means, double[] weights, double sigma) {
    super(Data.sum(Data.multiply(Arrays.copyOf(means, means.length), weights)), sigma);
    this.means = means;
    this.weights = weights;
  }

  public double[] means() {
    return means;
  }

  public double[] weights() {
    return weights;
  }

}
