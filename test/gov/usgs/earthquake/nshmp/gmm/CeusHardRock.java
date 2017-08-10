package gov.usgs.earthquake.nshmp.gmm;

import static gov.usgs.earthquake.nshmp.gmm.Gmm.AB_06_PRIME;
import static gov.usgs.earthquake.nshmp.gmm.Gmm.TORO_97_MB;
import static gov.usgs.earthquake.nshmp.gmm.Imt.PGA;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA0P2;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA1P0;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA2P0;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.io.IOException;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

import gov.usgs.earthquake.nshmp.gmm.Gmm;
import gov.usgs.earthquake.nshmp.gmm.Imt;

@SuppressWarnings("javadoc")
@RunWith(Parameterized.class)
public class CeusHardRock extends GmmTest {

  private static String GMM_INPUTS = "CEUS_vs2000_inputs.csv";
  private static String GMM_RESULTS = "CEUS_vs2000_results.csv";

  static {
    try {
      inputsList = loadInputs(GMM_INPUTS);
    } catch (IOException ioe) {
      ioe.printStackTrace();
      System.exit(1);
    }
  }

  @Parameters(name = "{index}: {0} {2} {1}")
  public static Collection<Object[]> data()
      throws IOException {
    return loadResults(GMM_RESULTS);
  }

  public CeusHardRock(int index, Gmm gmm, Imt imt, double exMedian, double exSigma) {
    super(index, gmm, imt, exMedian, exSigma);
  }

  /* Result generation sets */
  private static Set<Gmm> gmms = EnumSet.range(AB_06_PRIME, TORO_97_MB);
  private static Set<Imt> imts = EnumSet.of(PGA, SA0P2, SA1P0, SA2P0);

  public static void main(String[] args) throws IOException {
    GmmTest.generateResults(gmms, imts, GMM_INPUTS, GMM_RESULTS);
  }

}
