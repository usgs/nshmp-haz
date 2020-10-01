package gov.usgs.earthquake.nshmp.gmm;

import static gov.usgs.earthquake.nshmp.gmm.Imt.PGA;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA0P01;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA0P02;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA0P03;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA0P05;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA0P075;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA0P1;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA0P15;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA0P2;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA0P25;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA0P3;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA0P4;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA0P5;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA0P75;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA10P0;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA1P0;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA1P5;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA2P0;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA3P0;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA4P0;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA5P0;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA7P5;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import com.google.common.io.Resources;

/**
 * Implementation support for Guo &amp; Chapman (2019) gulf coastal plain
 * spectral ratio amplification model. This class is not a GMM, it provides
 * utility methods lookup spectral ratios based on sediment depth, source
 * magnitude, and epicentral distance.
 *
 * @author U.S. Geological Survey
 */
class GuoChapman_2019 {

  private static final EnumSet<Imt> MPRS = EnumSet.of(
      PGA, SA0P01, SA0P02, SA0P03, SA0P05, SA0P075,
      SA0P1, SA0P15, SA0P2, SA0P25, SA0P3, SA0P4, SA0P5, SA0P75,
      SA1P0, SA1P5, SA2P0, SA3P0, SA4P0, SA5P0, SA7P5, SA10P0);

  private static final double[] Z = {
      0.0, 0.1, 0.2, 0.3, 0.4, 0.6, 0.8,
      1.0, 1.5, 2.5, 3.5, 4.5, 5.5, 6.5, 7.5, 8.5, 9.5, 10.5, 11.5, 12.5 };

  private static final double[] M = {
      4.0, 4.5, 5.0, 5.5, 6.0, 6.5, 7.0, 7.5, 8.0, 8.2 };

  private static final double[] R = {
      0.0, 25.0, 50.0, 75.0, 100.0, 150.0,
      200.0, 300.0, 400.0, 500.0, 600.0, 700.0, 800.0, 900.0, 1000.0,
      1100.0, 1200.0, 1350.0, 1500.0 };

  private static final Map<Imt, double[][][]> scales;

  static {
    System.out.println(Z.length * M.length * R.length);
    scales = new EnumMap<Imt, double[][][]>(Imt.class);
    for (Imt imt : MPRS) {
      scales.put(imt, loadTable(imt));
    }
  }

  private static double[][][] loadTable(Imt imt) {
    double[][][] data = new double[Z.length][M.length][R.length];
    String path = "tables/cpa_" + imt.name() + ".csv";
    URL url = Resources.getResource(GuoChapman_2019.class, path);
    try {
      List<String> lines = Resources.readLines(url, UTF_8);
      int lineIndex = 1; // skip 1st line
      for (int zi = 0; zi < Z.length; zi++) {
        for (int mi = 0; mi < M.length; mi++) {
          for (int ri = 0; ri < R.length; ri++) {
            String line = lines.get(lineIndex++);
            String value = line.substring(line.lastIndexOf(',') + 1).trim();
            data[zi][mi][ri] = Double.parseDouble(value);
          }
        }
      }
    } catch (IOException ioe) {
      throw new RuntimeException(ioe);
    }
    return data;
  }

  /**
   * Return the psa ratio for the supplied sediment depth, magnitude and
   * distance.
   *
   * @param z sediment depth (in km)
   * @param m source magnitude
   * @param r hypocentral distance
   */
  static double cpaPsaRatio(Imt imt, double z, double m, double r) {
    int i = arrayIndex(Z, z);
    int j = arrayIndex(M, m);
    int k = arrayIndex(R, r);

    double zf = fraction(Z[i], Z[i + 1], z);
    double mf = fraction(M[j], M[j + 1], m);
    double rf = fraction(R[k], R[k + 1], r);

    double[][][] data = scales.get(imt);

    double z1m1r1 = data[i][j][k];
    double z1m1r2 = data[i][j][k + 1];
    double z1m2r1 = data[i][j + 1][k];
    double z1m2r2 = data[i][j + 1][k + 1];
    double z2m1r1 = data[i + 1][j][k];
    double z2m1r2 = data[i + 1][j][k + 1];
    double z2m2r1 = data[i + 1][j + 1][k];
    double z2m2r2 = data[i + 1][j + 1][k + 1];

    double z1m1 = interpolate(z1m1r1, z1m1r2, rf);
    double z1m2 = interpolate(z1m2r1, z1m2r2, rf);
    double z2m1 = interpolate(z2m1r1, z2m1r2, rf);
    double z2m2 = interpolate(z2m2r1, z2m2r2, rf);

    double z1 = interpolate(z1m1, z1m2, mf);
    double z2 = interpolate(z2m1, z2m2, mf);

    return interpolate(z1, z2, zf);
  }

  // public static void main(String[] args) {
  // // 0.74631047
  // double scale = cpaPsaRatio(PGA, 0.8, 8.2, 100);
  // System.out.println(scale);
  // }

  private static double interpolate(double lo, double hi, double fraction) {
    return lo + fraction * (hi - lo);
  }

  private static final double fraction(double lo, double hi, double value) {
    return value < lo ? 0.0 : value > hi ? 1.0 : (value - lo) / (hi - lo);
  }

  // TODO move to Indexing in nshmp-lib
  // clamped result constrained to [0 data.length-2] so i+1
  // never throws IndexOutOfBoundsEx
  private static final int arrayIndex(double[] data, double value) {
    int i = Arrays.binarySearch(data, value);
    // adjust index for low value (-1) and in-sequence insertion pt
    i = (i == -1) ? 0 : (i < 0) ? -i - 2 : i;
    // adjust hi index to next to last index
    return (i >= data.length - 1) ? --i : i;
  }

}
