package gov.usgs.earthquake.nshmp.gmm;

import static gov.usgs.earthquake.nshmp.gmm.Gmm.NGA_EAST_USGS;
import static gov.usgs.earthquake.nshmp.gmm.Gmm.NGA_EAST_USGS_SEEDS;
import static gov.usgs.earthquake.nshmp.gmm.Imt.PGA;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA0P02;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA0P1;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA0P2;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA1P0;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA3P0;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA5P0;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA10P0;

import java.io.IOException;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@SuppressWarnings("javadoc")
@RunWith(Parameterized.class)
public class NgaEast extends GmmTest {

  private static String GMM_INPUTS = "nga-east-inputs.csv";
  private static String GMM_RESULTS = "nga-east-results.csv";

  @Parameters(name = "{index}: {0} {2} {1}")
  public static Collection<Object[]> data() throws IOException {
    return loadResults(GMM_RESULTS);
  }

  public NgaEast(int index, Gmm gmm, Imt imt, double exMedian, double exSigma) {
    super(index, gmm, imt, exMedian, exSigma, GMM_INPUTS);
  }

  /* Result generation sets */
  private static Set<Gmm> gmms = EnumSet.of(NGA_EAST_USGS, NGA_EAST_USGS_SEEDS);
  private static Set<Imt> imts = EnumSet.of(PGA, SA0P02, SA0P1, SA0P2, SA1P0, SA3P0, SA5P0, SA10P0);

  public static void main(String[] args) throws IOException {
    GmmTest.generateResults(gmms, imts, GMM_INPUTS, GMM_RESULTS);
  }

}
