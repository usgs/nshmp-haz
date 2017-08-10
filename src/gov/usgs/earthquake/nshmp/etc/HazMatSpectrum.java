package gov.usgs.earthquake.nshmp.etc;

import static gov.usgs.earthquake.nshmp.internal.TextUtils.NEWLINE;

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
