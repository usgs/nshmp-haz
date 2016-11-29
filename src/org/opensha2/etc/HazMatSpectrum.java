package org.opensha2.etc;

import static org.opensha2.internal.TextUtils.NEWLINE;

import java.util.Arrays;

@SuppressWarnings("javadoc")
public class HazMatSpectrum {

  public double[] periods;
  public double[] means;
  public double[] sigmas;
  
  @Override
  public String toString() {
    return new StringBuilder("HazMatSpectrum: ")
        .append(NEWLINE)
        .append("  Periods: ").append(Arrays.toString(periods)).append(NEWLINE)
        .append("    Means: ").append(Arrays.toString(means)).append(NEWLINE)
        .append("   Sigmas: ").append(Arrays.toString(sigmas)).append(NEWLINE)
        .toString();
  }

}
