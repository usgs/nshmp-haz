package org.opensha2.gmm;

import static org.opensha2.gmm.Gmm.AB_03_CASC_SLAB;
import static org.opensha2.gmm.Gmm.AB_03_CASC_SLAB_LOW_SAT;
import static org.opensha2.gmm.Gmm.AB_03_GLOB_SLAB;
import static org.opensha2.gmm.Gmm.AB_03_GLOB_SLAB_LOW_SAT;
import static org.opensha2.gmm.Gmm.BCHYDRO_12_SLAB;
import static org.opensha2.gmm.Gmm.YOUNGS_97_SLAB;
import static org.opensha2.gmm.Gmm.ZHAO_06_SLAB;
import static org.opensha2.gmm.Imt.PGA;
import static org.opensha2.gmm.Imt.SA0P2;
import static org.opensha2.gmm.Imt.SA1P0;
import static org.opensha2.gmm.Imt.SA3P0;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.io.IOException;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

@SuppressWarnings("javadoc")
@RunWith(Parameterized.class)
public class SubSlab extends GmmTest {

  private static String GMM_INPUTS = "SLAB_inputs.csv";
  private static String GMM_RESULTS = "SLAB_results.csv";

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

  public SubSlab(int index, Gmm gmm, Imt imt, double exMedian, double exSigma) {
    super(index, gmm, imt, exMedian, exSigma);
  }

  /* Result generation sets */
  private static Set<Gmm> gmms = EnumSet.of(
      AB_03_GLOB_SLAB,
      AB_03_GLOB_SLAB_LOW_SAT,
      AB_03_CASC_SLAB,
      AB_03_CASC_SLAB_LOW_SAT,
      BCHYDRO_12_SLAB,
      YOUNGS_97_SLAB,
      ZHAO_06_SLAB);

  private static Set<Imt> imts = EnumSet.of(
      PGA,
      SA0P2,
      SA1P0,
      SA3P0);

  public static void main(String[] args) throws IOException {
    GmmTest.generateResults(gmms, imts, GMM_INPUTS, GMM_RESULTS);
  }

}
