package org.opensha2.gmm;

import org.opensha2.data.Data;

import java.util.Arrays;

/**
 * Extension to {@code ScalarGroundMotion} heirarchy added in support of
 * NGA-East. The {@link #mean} method
 * of this class returns the weighted mean of the NGA-East  
 *
 * @author Peter Powers
 */
public class MultiScalarGroundMotion extends DefaultScalarGroundMotion {

  // TODO package privatize if possible? no weight validation is
  // performed or length agreement checking
  
  private final double[] means;
  private final double[] weights;
  
  MultiScalarGroundMotion(double[] means, double[] weights, double sigma) {
    // TODO DataArray.Builder should be improved to allow operation 
    // probably through the creation of mutable and immutable variants
    super(Data.sum(Data.multiply(Arrays.copyOf(means, means.length), weights)), sigma);
    this.means = means;
    this.weights = weights;
    
  }
}
