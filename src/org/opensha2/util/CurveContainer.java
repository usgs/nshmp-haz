package org.opensha2.util;

import static com.google.common.base.Preconditions.checkArgument;

import org.opensha2.data.Data;
import org.opensha2.data.XySequence;
import org.opensha2.geo.GriddedRegion;
import org.opensha2.geo.Location;
import org.opensha2.geo.Regions;
import org.opensha2.gmm.Imt;

import com.google.common.base.CharMatcher;
import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import com.google.common.io.LineProcessor;
import com.google.common.io.LittleEndianDataInputStream;
import com.google.common.primitives.Doubles;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Class for storing and accessing grids of hazard curves. These can be NSHMP
 * national scale datasets such as those available here:
 * 
 * http://earthquake.usgs.gov/hazards/products/conterminous/2008/data/
 * 
 * and on related pages (see comments at bottom of class for formatting
 * variants), or OpenSHA regional and national curve data sets. Class does not
 * specify whether curves are probability or rate based.
 *
 * @author Peter Powers
 */
class CurveContainer implements Iterable<Location> {

  GriddedRegion region;
  double[] xs;
  Map<Integer, double[]> ysMap;
  XySequence baseCurve;

  /**
   * Returns the hazard curve for the supplied location.
   * @param loc of interest
   * @return the associated hazard curve
   * @throws IllegalArgumentException if loc is out of range of curve data
   */
  public XySequence getCurve(Location loc) {
    int idx = region.indexForLocation(loc);
    Preconditions.checkArgument(idx != -1, "Location is out of range: " + loc);
    double[] ys = ysMap.get(idx);
    return XySequence.copyOf(baseCurve).add(ys);
  }

  public List<Double> getValues(Location loc) {
    int idx = region.indexForLocation(loc);
    Preconditions.checkArgument(
      idx != -1, "Location is out of range: " + loc);
    return Doubles.asList(ysMap.get(idx));
  }

  /**
   * Returns the number of curves stored in this container.
   * @return the container size
   */
  public int size() {
    return ysMap.size();
  }

  /**
   * Scales the contained curves in place by the supplied value.
   * @param scale
   * @return a reference to {@code this} (for inlining and chaining)
   */
  public CurveContainer scale(double scale) {
    for (double[] values : ysMap.values()) {
      Data.multiply(scale, values);
    }
    return this;
  }

  /**
   * Adds the curves of the supplied container to this one.
   * @param cc container to add
   * @throws IllegalArgumentException if underlying gridded regions are not the
   *         same
   */
  public void add(CurveContainer cc) {
    checkArgument(region.equals(cc.region));
    for (Integer idx : ysMap.keySet()) {
      Data.add(ysMap.get(idx), cc.ysMap.get(idx));
    }
  }

  /**
   * Adds the curves in the supplied container to this.
   * 
   * NOTE be careful using this: Supplied container should be equivalent or
   * HIGHER resolution than this
   */
  public void union(CurveContainer cc) {
    checkArgument(
      xs.length == cc.xs.length,
      "Curve container size: %s != %s",
      xs.length, cc.xs.length);

    for (Location loc : this) {
      int idxFrom = cc.region.indexForLocation(loc);
      int idxTo = region.indexForLocation(loc);
      if (idxFrom != -1) {
        double[] fromYs = cc.ysMap.get(idxFrom);
        Data.add(ysMap.get(idxTo), fromYs);
      }
    }
  }

  /**
   * Adds the curves as available from the supplied NSHMP container.
   * @param cc
   */
  public void addNSHMP(CurveContainer cc) {
    for (Location loc : this) {
      XySequence f = null;
      try {
        f = cc.getCurve(loc);
      } catch (IllegalArgumentException iae) {
        System.out.println("Swallowed");
        continue;
      }
      int idx = region.indexForLocation(loc);
      Data.add(ysMap.get(idx), Doubles.toArray(f.yValues().subList(0, xs.length)));
    }
  }

  /**
   * Subtracts the curves of the supplied container from this one.
   * @param cc container to add
   * @throws IllegalArgumentException if underlying gridded regions are not the
   *         same
   */
  public void subtract(CurveContainer cc) {
    checkArgument(region.equals(cc.region));
    for (Integer idx : ysMap.keySet()) {
      Data.subtract(ysMap.get(idx), cc.ysMap.get(idx));
    }
  }

  @Override
  public Iterator<Location> iterator() {
    return region.iterator();
  }

  private static double minLat = 24.6;
  private static double maxLat = 50.0;
  private static double minLon = -125.0;
  private static double maxLon = -100.0;

  // private GriddedRegion createWusRegion(double spacing) {
  // return Regions.createRectangularGridded(
  // "NSHMP Map Region",
  // Location.create(minLat, minLon),
  // Location.create(maxLat, maxLon),
  // spacing, spacing,
  // GriddedRegion.ANCHOR_0_0);
  //
  // }

  /**
   * Creates a curve container for NSHMP national scale datafrom the supplied
   * data file and region. The supplied file is assumed to be in the standard
   * format output by NSHMP fortran 'combine' codes.
   * 
   * @param f file
   * @return a new curve container object
   */
  // public static CurveContainer create(File f) {
  //
  // GriddedRegion wusRegion = Regions.createRectangularGridded(
  // "NSHMP WUS Map Region",
  // Location.create(minLat, minLon),
  // Location.create(maxLat, maxLon),
  // 0.05, 0.05,
  // GriddedRegion.ANCHOR_0_0);
  // CurveFileProcessor_NSHMP cfp = new CurveFileProcessor_NSHMP(region);
  // CurveContainer curves = null;
  // try {
  // curves = Files.readLines(f, Charsets.US_ASCII, cfp);
  // } catch (IOException ioe) {
  // ioe.printStackTrace();
  // }
  // return curves;
  // }

  /**
   * Creates a curve container for a localized area from supplied data file,
   * region, and grid spacing. The data locations should match the nodes in the
   * gridded region. Results are unspecified if the two do not agree. The
   * supplied file is assumed to be in curve csv format.
   * 
   * @param f file
   * @param region for file
   * @return a new curve container object
   */
  public static CurveContainer create(File f, GriddedRegion region) throws IOException {
    CurveFileProcessor_SHA cfp = new CurveFileProcessor_SHA(region);
    CurveContainer curves = Files.readLines(f, Charsets.US_ASCII, cfp);
    return curves;
  }

  // create an empty curve container; all curves are zero-valued
  public static CurveContainer create(GriddedRegion gr, double[] xs) {
    CurveContainer cc = new CurveContainer();
    cc.xs = Arrays.copyOf(xs, xs.length);
    cc.ysMap = Maps.newHashMap();
    cc.region = gr;
    for (int i = 0; i < gr.size(); i++) {
      cc.ysMap.put(i, new double[xs.length]);
    }
    return cc;
  }

  // create cc for binary nshmp curve file
  public static CurveContainer create(URL url) throws IOException {
    LittleEndianDataInputStream in =
      new LittleEndianDataInputStream(url.openStream());

    CurveContainer cc = new CurveContainer();

    // read names 6 * char(128)
    int n = 128;
    for (int i = 0; i < 6; i++) {
      byte[] nameDat = new byte[n];
      in.read(nameDat, 0, n);
      // System.out.println(new String(nameDat));
    }
    float period = in.readFloat();
    int nX = in.readInt();
    Imt imt = Imt.fromPeriod(period);
    // System.out.println("period: " + period);
    // System.out.println("nX: " + nX);

    // read x-vals real*4 * 20
    List<Double> xs = Lists.newArrayList();
    for (int i = 0; i < 20; i++) {
      double val = MathUtils.round(in.readFloat(), 3);
      // System.out.println(val);
      // need to read 20 values to advance caret, but only save ones used
      if (i < nX) xs.add(val);
    }
    cc.xs = Doubles.toArray(xs);
    // System.out.println("xVals: " + cc.xs);

    // read extras real*4 * 10
    List<Double> extras = Lists.newArrayList();
    for (int i = 0; i < 10; i++) {
      double val = MathUtils.round(in.readFloat(), 2);
      extras.add(val);
    }
    // System.out.println("extras: " + extras);
    double minLon = extras.get(1);
    double maxLon = extras.get(2);
    double spacing = extras.get(3);
    double minLat = extras.get(4);
    double maxLat = extras.get(5);
    Location nwLoc = Location.create(maxLat, minLon);
    Location seLoc = Location.create(minLat, maxLon);

    cc.region = Regions.createRectangularGridded(
      "NSHMP Region",
      nwLoc, seLoc,
      spacing, spacing,
      GriddedRegion.ANCHOR_0_0);
    // System.out.println(gr.getNodeCount());
    int nRows = (int) Math.rint((maxLat - minLat) / spacing) + 1;
    int nCols = (int) Math.rint((maxLon - minLon) / spacing) + 1;

    cc.ysMap = Maps.newHashMapWithExpectedSize(cc.region.size());

    for (int i = 0; i < cc.region.size(); i++) {
      // read nX values for each i
      List<Double> vals = Lists.newArrayList();
      for (int j = 0; j < nX; j++) {
        vals.add((double) in.readFloat());
      }
      int regionIdx = calcIndex(i, nRows, nCols);
      cc.ysMap.put(regionIdx, Doubles.toArray(vals));
    }

    in.close();
    return cc;
  }

  /*
   * This method converts an NSHMP index to the correct GriddedRegion index
   */
  private static int calcIndex(int idx, int nRows, int nCols) {
    return (nRows - (idx / nCols) - 1) * nCols + (idx % nCols);
    // compact form of:
    // int col = idx % nCols;
    // int row = idx / nCols;
    // int targetRow = nRows - row - 1;
    // return targetRow * nCols + col;
  }

  // reads from ascii curve files
  static class CurveFileProcessor_NSHMP implements LineProcessor<CurveContainer> {

    private Splitter split;
    private int xCount = 0;
    private int headCount = 0;
    private int headLines = 3;
    private CurveContainer cc;

    private List<Double> xs = new ArrayList<>();

    CurveFileProcessor_NSHMP(GriddedRegion region) {
      split = Splitter.on(CharMatcher.WHITESPACE).omitEmptyStrings();
      cc = new CurveContainer();
      cc.region = region;
      cc.ysMap = Maps.newHashMapWithExpectedSize(region.size());
    }

    @Override
    public CurveContainer getResult() {
      return cc;
    }

    @Override
    public boolean processLine(String line) throws IOException {

      // skip first 3 lines for either format
      if (headCount < headLines) {
        headCount++;
        return true;
      }

      // short lines are going to be x values
      if (line.length() < 20) {
        xs.add(Double.parseDouble(line));
        xCount++;
        return true;
      }

      if (cc.xs == null) cc.xs = Doubles.toArray(xs);

      addCurve(line);
      return true;
    }

    private void addCurve(String line) {
      Iterator<String> it = split.split(line).iterator();
      // read location
      Location loc =
        Location.create(Double.parseDouble(it.next()), Double.parseDouble(it.next()));
      int idx = cc.region.indexForLocation(loc);
      double[] vals = new double[xCount];
      for (int i = 0; i < xCount; i++) {
        vals[i] = Double.parseDouble(it.next());
      }
      cc.ysMap.put(idx, vals);
    }
  }

  static class CurveFileProcessor_SHA implements LineProcessor<CurveContainer> {

    private Splitter split;
    private boolean firstLine = true;
    private CurveContainer cc;

    CurveFileProcessor_SHA(GriddedRegion region) {
      split = Splitter.on(',').omitEmptyStrings();
      cc = new CurveContainer();
      cc.region = region;
      cc.ysMap = Maps.newHashMapWithExpectedSize(region.size());
    }

    @Override
    public CurveContainer getResult() {
      return cc;
    }

    @Override
    public boolean processLine(String line) throws IOException {

      if (firstLine) {
        Iterable<String> vals = Iterables.skip(split.split(line), 2);
        List<Double> xs = new ArrayList<>();
        for (String val : vals) {
          xs.add(Double.parseDouble(val));
        }
        cc.xs = Doubles.toArray(xs);
        firstLine = false;
        return true;
      }

      addCurve(line);
      return true;
    }

    private void addCurve(String line) {
      Iterable<String> it = split.split(line);
      // read location
      Location loc = Location.create(
        Double.parseDouble(Iterables.get(it, 1)),
        Double.parseDouble(Iterables.get(it, 0)));
      int idx = cc.region.indexForLocation(loc);
      List<Double> vals = Lists.newArrayList();
      for (String sVal : Iterables.skip(it, 2)) {
        vals.add(Double.parseDouble(sVal));
      }
      cc.ysMap.put(idx, Doubles.toArray(vals));
    }
  }

  /**
   * Test
   * @param args
   */
  public static void main(String[] args) throws IOException {
    // File f = new
    // File("/Volumes/Scratch/nshmp-sources/FortranLatest/GM0P00/curves_us_all.pga");
    // GriddedRegion gr = getNSHMP_Region();
    // System.out.println("region created: " + gr.getNodeCount());
    // Stopwatch sw = new Stopwatch();
    // sw.start();
    // CurveContainer cc = CurveContainer.create(f);
    // sw.stop();
    // System.out.println("time: " + sw.elapsedMillis());
    // System.out.println("size: " + cc.size());
    // System.out.println(cc.getCurve(NEHRP_TestCity.LOS_ANGELES.location()));

    // // testing curve container addition
    // File f1 = new
    // File("/Users/pmpowers/projects/OpenSHA/tmp/UC3maps/src/FM-DM-MS-DSR-UV/FM3_1_ABM_EllB_DsrTap_CharConst_M5Rate8.7_MMaxOff7.6_NoFix_SpatSeisU2_0p1/CA_RELM/GM0P00/curves.csv");
    // File f2 = new
    // File("/Users/pmpowers/projects/OpenSHA/tmp/UC3maps/src/FM-DM-MS-DSR-UV/FM3_1_GEOL_EllB_DsrTap_CharConst_M5Rate8.7_MMaxOff7.6_NoFix_SpatSeisU2_0p1/CA_RELM/GM0P00/curves.csv");
    // File f3 = new
    // File("/Users/pmpowers/projects/OpenSHA/tmp/UC3maps/src/FM-DM-MS-DSR-UV/FM3_1_NEOK_EllB_DsrTap_CharConst_M5Rate8.7_MMaxOff7.6_NoFix_SpatSeisU2_0p1/CA_RELM/GM0P00/curves.csv");
    // CurveContainer cc1 = CurveContainer.create(f1, TestGrid.CA_RELM);
    // CurveContainer cc2 = CurveContainer.create(f2, TestGrid.CA_RELM);
    // CurveContainer cc3 = CurveContainer.create(f3, TestGrid.CA_RELM);
    //
    // Location loc = NEHRP_TestCity.LOS_ANGELES.location();
    //
    // System.out.println(cc1.getCurve(loc));
    // System.out.println(cc2.getCurve(loc));
    // System.out.println(cc3.getCurve(loc));
    //
    // cc1.add(cc2);
    // System.out.println(cc1.getCurve(loc));
    //
    // cc1.add(cc3);
    // System.out.println(cc1.getCurve(loc));

    // // testing curve container scaling
    // File f1 = new
    // File("/Users/pmpowers/projects/OpenSHA/tmp/UC3maps/src/FM-DM-MS-DSR-UV/FM3_1_ABM_EllB_DsrTap_CharConst_M5Rate8.7_MMaxOff7.6_NoFix_SpatSeisU2_0p1/CA_RELM/GM0P00/curves.csv");
    // CurveContainer cc1 = CurveContainer.create(f1, TestGrid.CA_RELM);
    // Location loc = NEHRP_TestCity.LOS_ANGELES.location();
    // System.out.println(cc1.getCurve(loc));
    // cc1.scale(0.5);
    // System.out.println(cc1.getCurve(loc));

    // String ltbID =
    // "FM3_1_ZENG_EllB_DsrUni_CharConst_M5Rate7.6_MMaxOff7.6_NoFix_SpatSeisU2";
    // LogicTreeBranch ltb = LogicTreeBranch.fromFileName(ltbID);
    // System.out.println(ltb.getAprioriBranchWt());

    // File f = new
    // File("/Volumes/Scratch/nshmp-cf/MEMPHIS/GM0P00/curves.csv");
    // Stopwatch sw = new Stopwatch();
    // sw.start();
    // CurveContainer cc = CurveContainer.create(f, TestGrid.MEMPHIS);
    // sw.stop();
    // System.out.println("time: " + sw.elapsedMillis());
    // System.out.println("size: " + cc.size());
    // System.out.println(cc.getCurve(NEHRP_TestCity.MEMPHIS.location()));

    // GriddedRegion gr = TestGrid.MEMPHIS.grid();
    // System.out.println(gr.indexForLocation(NEHRP_TestCity.MEMPHIS.location()));

    // LogicTreeBranch branch =
    // LogicTreeBranch.fromFileName("FM3_1_ZENG_EllB_DsrTap_CharConst_M5Rate8.7_MMaxOff7.6_NoFix_SpatSeisU2");
    // System.out.println(branch);
    // System.out.println(branch.getValue(DeformationModels.class));
    // System.out.println(branch.getAprioriBranchWt());
    // if
    // (branch.getValue(DeformationModels.class).equals(DeformationModels.ZENG))
    // {
    // branch.setValue(DeformationModels.ZENGBB);
    // }
    // System.out.println(branch);
    // System.out.println(branch.getValue(DeformationModels.class));
    // System.out.println(branch.getAprioriBranchWt());

    String srcDir = "/Users/pmpowers/projects/NSHMP/tmp/out";
    String srcFile = "sub.2014.pga";
    File curveFile = new File(srcDir, srcFile);
    URL url = curveFile.toURI().toURL();

    CurveContainer.create(url);
  }

}

// NOTE, USGS data files can be formatted as...

// #Pgm hazallXL.v4.f (harmsen) sums 2 hazard curves from
// ../conf/combine/curves_us.pga
// #Lat Long Rex for spectral period 0.00
// #GM set(g) 19
// 0.50000E-02
// 0.70000E-02
// 0.98000E-02
// 0.13700E-01
// 0.19200E-01
// 0.26900E-01
// 0.37600E-01
// 0.52700E-01
// 0.73800E-01
// 0.10300E+00
// 0.14500E+00
// 0.20300E+00
// 0.28400E+00
// 0.39700E+00
// 0.55600E+00
// 0.77800E+00
// 0.10900E+01
// 0.15200E+01
// 0.21300E+01
// 50.000 -125.000 4.99469E-02 4.11959E-02 3.25710E-02 2.47907E-02 1.82196E-02
// 1.30642E-02 9.22078E-03 6.37273E-03 4.27867E-03 2.73385E-03 1.58381E-03
// 8.28554E-04 3.82728E-04 1.54050E-04 5.20416E-05 1.41056E-05 2.58034E-06
// 1.93185E-07 2.47693E-08
// 50.000 -124.950 5.07659E-02 4.18204E-02 3.30050E-02 2.50571E-02 1.83528E-02
// 1.31039E-02 9.20262E-03 6.32359E-03 4.21818E-03 2.67578E-03 1.53755E-03
// 7.97565E-04 3.65283E-04 1.45810E-04 4.88500E-05 1.31283E-05 2.36483E-06
// 1.95222E-07 2.59898E-08
// 50.000 -124.900 5.15849E-02 4.24449E-02 3.34389E-02 2.53236E-02 1.84861E-02
// 1.31436E-02 9.18446E-03 6.27445E-03 4.15768E-03 2.61770E-03 1.49128E-03
// 7.66577E-04 3.47839E-04 1.37569E-04 4.56583E-05 1.21511E-05 2.14932E-06
// 1.97259E-07 2.72103E-08
// 50.000 -124.850 5.23902E-02 4.30591E-02 3.38673E-02 2.55895E-02 1.86229E-02
// 1.31892E-02 9.17283E-03 6.23128E-03 4.10218E-03 2.56377E-03 1.44840E-03
// 7.38148E-04 3.32114E-04 1.30299E-04 4.29240E-05 1.13473E-05 1.99972E-06
// 2.04181E-07 2.86878E-08
// 50.000 -124.800 5.31954E-02 4.36733E-02 3.42956E-02 2.58554E-02 1.87597E-02
// 1.32349E-02 9.16121E-03 6.18811E-03 4.04668E-03 2.50984E-03 1.40552E-03
// 7.09720E-04 3.16390E-04 1.23029E-04 4.01896E-05 1.05434E-05 1.85012E-06
// 2.11104E-07 3.01653E-08

// ... OR ...

// CEUShazard.200809.pga
// WUShazard.2008.pga
// 0.0E+0
// 0.5000E-02
// 0.7000E-02
// 0.9800E-02
// 0.1370E-01
// 0.1920E-01
// 0.2690E-01
// 0.3760E-01
// 0.5270E-01
// 0.7380E-01
// 0.1030E+00
// 0.1450E+00
// 0.2030E+00
// 0.2840E+00
// 0.3970E+00
// 0.5560E+00
// 0.7780E+00
// 0.1090E+01
// 0.1520E+01
// 0.2130E+01
// 50.00 -125.00 0.4950E-01 0.4074E-01 0.3212E-01 0.2435E-01 0.1782E-01
// 0.1271E-01 0.8927E-02 0.6140E-02 0.4105E-02 0.2613E-02 0.1507E-02 0.7847E-03
// 0.3606E-03 0.1444E-03 0.4850E-04 0.1305E-04 0.2349E-05 0.1625E-06 0.1919E-07
// 50.00 -124.95 0.5031E-01 0.4135E-01 0.3254E-01 0.2461E-01 0.1794E-01
// 0.1275E-01 0.8905E-02 0.6090E-02 0.4046E-02 0.2557E-02 0.1463E-02 0.7555E-03
// 0.3443E-03 0.1367E-03 0.4552E-04 0.1213E-04 0.2146E-05 0.1643E-06 0.2013E-07
// 50.00 -124.90 0.5112E-01 0.4197E-01 0.3296E-01 0.2487E-01 0.1807E-01
// 0.1278E-01 0.8883E-02 0.6040E-02 0.3986E-02 0.2501E-02 0.1419E-02 0.7262E-03
// 0.3279E-03 0.1290E-03 0.4254E-04 0.1122E-04 0.1942E-05 0.1660E-06 0.2107E-07
// 50.00 -124.85 0.5192E-01 0.4257E-01 0.3338E-01 0.2513E-01 0.1820E-01
// 0.1282E-01 0.8869E-02 0.5996E-02 0.3932E-02 0.2449E-02 0.1378E-02 0.6995E-03
// 0.3132E-03 0.1223E-03 0.4000E-04 0.1048E-04 0.1802E-05 0.1723E-06 0.2221E-07
