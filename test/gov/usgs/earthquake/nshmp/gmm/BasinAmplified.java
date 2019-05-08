package gov.usgs.earthquake.nshmp.gmm;

import static gov.usgs.earthquake.nshmp.gmm.Gmm.AM_09_INTERFACE_BASIN_AMP;
import static gov.usgs.earthquake.nshmp.gmm.Gmm.ASK_14_BASIN_AMP;
import static gov.usgs.earthquake.nshmp.gmm.Gmm.BCHYDRO_12_INTERFACE_BASIN_AMP;
import static gov.usgs.earthquake.nshmp.gmm.Gmm.BCHYDRO_12_SLAB_BASIN_AMP;
import static gov.usgs.earthquake.nshmp.gmm.Gmm.BSSA_14_BASIN_AMP;
import static gov.usgs.earthquake.nshmp.gmm.Gmm.CB_14_BASIN_AMP;
import static gov.usgs.earthquake.nshmp.gmm.Gmm.CY_14_BASIN_AMP;
import static gov.usgs.earthquake.nshmp.gmm.Gmm.ZHAO_06_INTERFACE_BASIN_AMP;
import static gov.usgs.earthquake.nshmp.gmm.Gmm.ZHAO_06_SLAB_BASIN_AMP;
import static gov.usgs.earthquake.nshmp.gmm.Imt.PGA;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA0P02;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA0P2;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA0P5;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA10P0;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA1P0;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA3P0;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA5P0;

import java.io.IOException;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * Tests of 2018 NSHM GMMs that impose amplification-only basin effects.
 */
@SuppressWarnings("javadoc")
@RunWith(Parameterized.class)
public class BasinAmplified extends GmmTest {

  private static String GMM_INPUTS = "basin-amp-inputs.csv";
  private static String GMM_RESULTS = "basin-amp-results.csv";

  @Parameters(name = "{index}: {0} {2} {1}")
  public static Collection<Object[]> data() throws IOException {
    return loadResults(GMM_RESULTS);
  }

  public BasinAmplified(int index, Gmm gmm, Imt imt, double exMedian, double exSigma) {
    super(index, gmm, imt, exMedian, exSigma, GMM_INPUTS);
  }

  /* Result generation sets */
  private static Set<Gmm> gmms = EnumSet.of(
      ASK_14_BASIN_AMP,
      BSSA_14_BASIN_AMP,
      CB_14_BASIN_AMP,
      CY_14_BASIN_AMP,
      AM_09_INTERFACE_BASIN_AMP,
      BCHYDRO_12_INTERFACE_BASIN_AMP,
      ZHAO_06_INTERFACE_BASIN_AMP,
      BCHYDRO_12_SLAB_BASIN_AMP,
      ZHAO_06_SLAB_BASIN_AMP);

  private static Set<Imt> imts = EnumSet.of(PGA, SA0P02, SA0P2, SA0P5, SA1P0, SA3P0, SA5P0, SA10P0);

  public static void main(String[] args) throws IOException {
    GmmTest.generateResults(gmms, imts, GMM_INPUTS, GMM_RESULTS);
  }

}
