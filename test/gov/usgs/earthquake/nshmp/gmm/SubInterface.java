package gov.usgs.earthquake.nshmp.gmm;

import static gov.usgs.earthquake.nshmp.gmm.Gmm.AB_03_CASCADIA_INTERFACE;
import static gov.usgs.earthquake.nshmp.gmm.Gmm.AB_03_GLOBAL_INTERFACE;
import static gov.usgs.earthquake.nshmp.gmm.Gmm.AM_09_INTERFACE;
import static gov.usgs.earthquake.nshmp.gmm.Gmm.BCHYDRO_12_INTERFACE;
import static gov.usgs.earthquake.nshmp.gmm.Gmm.YOUNGS_97_INTERFACE;
import static gov.usgs.earthquake.nshmp.gmm.Gmm.ZHAO_06_INTERFACE;
import static gov.usgs.earthquake.nshmp.gmm.Imt.PGA;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA0P2;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA1P0;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA3P0;

import java.io.IOException;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@SuppressWarnings("javadoc")
@RunWith(Parameterized.class)
public class SubInterface extends GmmTest {

  private static String GMM_INPUTS = "interface-inputs.csv";
  private static String GMM_RESULTS = "interface-results.csv";

  @Parameters(name = "{index}: {0} {2} {1}")
  public static Collection<Object[]> data() throws IOException {
    return loadResults(GMM_RESULTS);
  }

  public SubInterface(int index, Gmm gmm, Imt imt, double exMedian, double exSigma) {
    super(index, gmm, imt, exMedian, exSigma, GMM_INPUTS);
  }

  /* Result generation sets */
  private static Set<Gmm> gmms = EnumSet.of(
      AB_03_GLOBAL_INTERFACE,
      AB_03_CASCADIA_INTERFACE,
      AM_09_INTERFACE,
      BCHYDRO_12_INTERFACE,
      YOUNGS_97_INTERFACE,
      ZHAO_06_INTERFACE);

  private static Set<Imt> imts = EnumSet.of(
      PGA,
      SA0P2,
      SA1P0,
      SA3P0);

  public static void main(String[] args) throws IOException {
    GmmTest.generateResults(gmms, imts, GMM_INPUTS, GMM_RESULTS);
  }

}
