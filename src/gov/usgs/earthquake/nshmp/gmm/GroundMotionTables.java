package gov.usgs.earthquake.nshmp.gmm;

import static com.google.common.io.Resources.getResource;
import static com.google.common.io.Resources.readLines;
import static gov.usgs.earthquake.nshmp.gmm.Imt.PGA;
import static gov.usgs.earthquake.nshmp.gmm.Imt.PGV;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA0P03;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA0P3;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA3P0;
import static gov.usgs.earthquake.nshmp.internal.Parsing.splitToDoubleList;
import static gov.usgs.earthquake.nshmp.internal.Parsing.splitToList;
import static gov.usgs.earthquake.nshmp.internal.TextUtils.NEWLINE;
import static java.lang.Math.log10;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Enums;
import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.LineProcessor;
import com.google.common.primitives.Doubles;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Logger;

import gov.usgs.earthquake.nshmp.data.Data;
import gov.usgs.earthquake.nshmp.gmm.GmmUtils.CeusSiteClass;
import gov.usgs.earthquake.nshmp.gmm.GroundMotionTables.GroundMotionTable.Position;
import gov.usgs.earthquake.nshmp.internal.Parsing;
import gov.usgs.earthquake.nshmp.internal.Parsing.Delimiter;

/**
 * Utility class to load and fetch {@code GroundMotionModel} lookup tables.
 *
 * Frankel, Atkinson, and Pezeshk tables store ground motion in log10 values.
 * Atkinson flavored tables store ground motion in cm/s^2. NGA-East tables
 * contain linear ground motion values. All tables interpolate in log10 distance
 * and linear in magnitude.
 *
 * @author Peter Powers
 */
final class GroundMotionTables {

  static GroundMotionTable getFrankel96(Imt imt, CeusSiteClass siteClass) {
    return siteClass == CeusSiteClass.SOFT_ROCK ? FRANKEL_SOFT_ROCK.get(imt)
        : FRANKEL_HARD_ROCK.get(imt);
  }

  static GroundMotionTable getAtkinson06(Imt imt) {
    return ATKINSON_06.get(imt);
  }

  static GroundMotionTable getAtkinson08(Imt imt) {
    return ATKINSON_08.get(imt);
  }

  static GroundMotionTable getPezeshk11(Imt imt) {
    return PEZESHK_11.get(imt);
  }

  static GroundMotionTable[] getNgaEast(Imt imt) {
    return NGA_EAST.get(imt);
  }

  static GroundMotionTable getNgaEastSeed(String id, Imt imt) {
    return NGA_EAST_SEEDS.get(id).get(imt);
  }

  static double[] getNgaEastWeights(Imt imt) {
    return NGA_EAST_WEIGHTS.get(imt);
  }

  private static final String TABLE_DIR = "tables/";

  private static final String[] frankelSrcSR = {
      "pgak01l.tbl", "t0p2k01l.tbl", "t1p0k01l.tbl", "t0p1k01l.tbl",
      "t0p3k01l.tbl", "t0p5k01l.tbl", "t2p0k01l.tbl" };

  private static final String[] frankelSrcHR = {
      "pgak006.tbl", "t0p2k006.tbl", "t1p0k006.tbl", "t0p1k006.tbl",
      "t0p3k006.tbl", "t0p5k006.tbl", "t2p0k006.tbl" };

  private static final String ATKINSON_06_SRC = "AB06revA_Rcd.dat";
  private static final String ATKINSON_08_SRC = "A08revA_Rjb.dat";
  private static final String PEZESHK_11_SRC = "P11A_Rcd.dat";

  private static final String NGA_EAST_FILENAME_FMT = "nga-east-usgs-%s.dat";
  private static final String NGA_EAST_SEED_FILENAME_FMT = "nga-east-%s.dat";
  private static final int NGA_EAST_MODEL_COUNT = 13;

  static final List<String> NGA_EAST_SEED_IDS = ImmutableList.copyOf(new String[] {
      "1CCSP",
      "1CVSP",
      "2CCSP",
      "2CVSP",
      "B_a04",
      "B_ab14",
      "B_ab95",
      "B_bca10d",
      "B_bs11",
      "B_sgd02",
      "Frankel",
      "Graizer",
      "Graizer16",
      "Graizer17",
      "HA15",
      "PEER_EX",
      "PEER_GP",
      "PZCT15_M1SS",
      "PZCT15_M2ES",
      "SP15",
      "YA15" });

  private static final double[] ATKINSON_R = {
      -1.000, 0.000, 0.301, 0.699, 1.000, 1.176, 1.301, 1.398, 1.477, 1.602,
      1.699, 1.778, 1.845, 1.903, 1.954, 2.000, 2.041, 2.079, 2.176, 2.301,
      2.398, 2.477, 2.544, 2.602, 2.699 };

  private static final double[] PEZESHK_R = {
      0.000, 0.301, 0.699, 1.000, 1.176, 1.301, 1.477, 1.602, 1.699, 1.778,
      1.845, 1.903, 2.000, 2.079, 2.146, 2.255, 2.301, 2.398, 2.477, 2.602,
      2.699, 2.778, 2.845, 2.903, 3.000 };

  private static final double[] FRANKEL_R = {
      1.0, 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8, 1.9, 2.0, 2.1,
      2.2, 2.3, 2.4, 2.5, 2.6, 2.7, 2.8, 2.9, 3.0 };

  private static final double[] NGA_EAST_R = Data.log(new double[] {
      0.00001, 1.0, 5.0, 10.0, 15.0, 20.0, 25.0, 30.0, 40.0, 50.0, 60.0, 70.0, 80.0, 90.0, 100.0,
      110.0, 120.0, 130.0, 140.0, 150.0, 175.0, 200.0, 250.0, 300.0, 350.0, 400.0, 450.0, 500.0,
      600.0, 700.0, 800.0, 1000.0, 1200.0, 1500.0 });

  private static final double[] ATKINSON_M = {
      4.00, 4.25, 4.50, 4.75, 5.00, 5.25, 5.50, 5.75, 6.00,
      6.25, 6.50, 6.75, 7.00, 7.25, 7.50, 7.75, 8.00 };

  private static final double[] PEZESHK_M = {
      4.50, 4.75, 5.00, 5.25, 5.50, 5.75, 6.00, 6.25,
      6.50, 6.75, 7.00, 7.25, 7.50, 7.75, 8.00 };

  private static final double[] FRANKEL_M = {
      4.4, 4.6, 4.8, 5.0, 5.2, 5.4, 5.6, 5.8, 6.0, 6.2, 6.4,
      6.6, 6.8, 7.0, 7.2, 7.4, 7.6, 7.8, 8.0, 8.2 };

  private static final double[] NGA_EAST_M = {
      4.0, 4.5, 5.0, 5.5, 6.0, 6.5, 7.0, 7.5, 7.8, 8.0, 8.2 };

  // different numeric representations of 0.33 3.3 and 33.0 Hz
  private static final Set<Double> FREQ3_LO = ImmutableSet.of(0.32, 0.33);
  private static final Set<Double> FREQ3_MID = ImmutableSet.of(3.2, 3.33);
  private static final Set<Double> FREQ3_HI = ImmutableSet.of(32.0, 33.0, 33.33);

  private static final Map<Imt, GroundMotionTable> FRANKEL_HARD_ROCK;
  private static final Map<Imt, GroundMotionTable> FRANKEL_SOFT_ROCK;
  private static final Map<Imt, GroundMotionTable> ATKINSON_06;
  private static final Map<Imt, GroundMotionTable> ATKINSON_08;
  private static final Map<Imt, GroundMotionTable> PEZESHK_11;
  private static final Map<Imt, GroundMotionTable[]> NGA_EAST;
  private static final Map<String, Map<Imt, GroundMotionTable>> NGA_EAST_SEEDS;
  private static final Map<Imt, double[]> NGA_EAST_WEIGHTS;

  static {
    FRANKEL_HARD_ROCK = initFrankel(frankelSrcHR);
    FRANKEL_SOFT_ROCK = initFrankel(frankelSrcSR);
    ATKINSON_06 = initAtkinson(ATKINSON_06_SRC, ATKINSON_R, ATKINSON_M);
    ATKINSON_08 = initAtkinson(ATKINSON_08_SRC, ATKINSON_R, ATKINSON_M);
    PEZESHK_11 = initAtkinson(PEZESHK_11_SRC, PEZESHK_R, PEZESHK_M);
    NGA_EAST = initNgaEast();
    NGA_EAST_SEEDS = initNgaEastSeeds();
    NGA_EAST_WEIGHTS = initNgaEastWeights();
  }

  private static Map<Imt, GroundMotionTable> initFrankel(String[] files) {
    Map<Imt, GroundMotionTable> map = Maps.newEnumMap(Imt.class);
    for (String file : files) {
      try {
        Imt imt = frankelFilenameToIMT(file);
        URL url = getResource(GroundMotionTables.class, TABLE_DIR + file);
        double[][] data = readLines(url, UTF_8, new FrankelParser());
        map.put(imt, new LogDistanceTable(data, FRANKEL_R, FRANKEL_M));
      } catch (IOException ioe) {
        handleIOex(ioe, file);
      }
    }
    return map;
  }

  private static Imt frankelFilenameToIMT(String s) {
    if (s.startsWith("pga")) {
      return PGA;
    }
    StringBuilder sb = new StringBuilder();
    sb.append(s.charAt(1)).append('.').append(s.charAt(3));
    return Imt.fromPeriod(Double.valueOf(sb.toString()));
  }

  private static Map<Imt, GroundMotionTable> initAtkinson(
      String file,
      double[] rKeys,
      double[] mKeys) {

    Map<Imt, GroundMotionTable> map = Maps.newEnumMap(Imt.class);
    URL url = getResource(GroundMotionTables.class, TABLE_DIR + file);
    try {
      AtkinsonParser parser = new AtkinsonParser(rKeys.length);
      Map<Imt, double[][]> dataMap = readLines(url, UTF_8, parser);
      for (Entry<Imt, double[][]> entry : dataMap.entrySet()) {
        double[][] data = entry.getValue();
        map.put(entry.getKey(), new LogDistanceScalingTable(data, rKeys, mKeys));
      }
    } catch (IOException ioe) {
      handleIOex(ioe, file);
    }
    return map;
  }

  private static Map<Imt, GroundMotionTable[]> initNgaEast() {
    Map<Imt, GroundMotionTable[]> map = Maps.newEnumMap(Imt.class);
    for (int i = 0; i < NGA_EAST_MODEL_COUNT; i++) {
      String filename = String.format(NGA_EAST_FILENAME_FMT, i + 1);
      /*
       * TODO nga-east data are not public and therefore may not exist when
       * initializing Gmm's; we therefore temporarily allow mga-east ground
       * motion tables to initialize to null. Once data are public remove
       * try-catch.
       */
      URL url;
      try {
        url = getResource(GroundMotionTables.class, TABLE_DIR + filename);
      } catch (IllegalArgumentException iae) {
        return null;
      }
      try {
        NgaEastParser parser = new NgaEastParser(NGA_EAST_R.length);
        Map<Imt, double[][]> dataMap = readLines(url, UTF_8, parser);
        for (Entry<Imt, double[][]> entry : dataMap.entrySet()) {
          double[][] data = entry.getValue();
          LogDistanceTable table = new LogDistanceTable(data, NGA_EAST_R, NGA_EAST_M);
          Imt imt = entry.getKey();
          if (map.get(imt) == null) {
            map.put(imt, new GroundMotionTable[NGA_EAST_MODEL_COUNT]);
          }
          map.get(imt)[i] = table;
        }
      } catch (IOException ioe) {
        handleIOex(ioe, filename);
      }
    }
    return map;
  }

  private static Map<String, Map<Imt, GroundMotionTable>> initNgaEastSeeds() {
    Map<String, Map<Imt, GroundMotionTable>> map = new HashMap<>();
    for (String id : NGA_EAST_SEED_IDS) {
      String filename = String.format(NGA_EAST_SEED_FILENAME_FMT, id);
      /*
       * TODO nga-east data are not public and therefore may not exist when
       * initializing Gmm's; we therefore temporarily allow mga-east ground
       * motion tables to initialize to null. Once data are public remove
       * try-catch.
       */
      URL url;
      try {
        url = getResource(GroundMotionTables.class, TABLE_DIR + filename);
      } catch (IllegalArgumentException iae) {
        return null;
      }

      try {
        NgaEastParser parser = new NgaEastParser(NGA_EAST_R.length);
        Map<Imt, double[][]> dataMap = readLines(url, UTF_8, parser);
        for (Entry<Imt, double[][]> entry : dataMap.entrySet()) {
          double[][] data = entry.getValue();
          LogDistanceTable table = new LogDistanceTable(data, NGA_EAST_R, NGA_EAST_M);
          Imt imt = entry.getKey();
          if (map.get(id) == null) {
            Map<Imt, GroundMotionTable> seedMap = Maps.newEnumMap(Imt.class);
            map.put(id, seedMap);
          }
          map.get(id).put(imt, table);
        }
      } catch (IOException ioe) {
        handleIOex(ioe, filename);
      }
    }
    return map;
  }

  private static Map<Imt, double[]> initNgaEastWeights() {
    Map<Imt, double[]> map = Maps.newEnumMap(Imt.class);
    String filename = String.format(NGA_EAST_FILENAME_FMT, "weights");
    /*
     * TODO clean up as above; tamporarily allowing weights to init to null.
     * Once data are public remove try-catch.
     */
    URL url;
    try {
      url = getResource(GroundMotionTables.class, TABLE_DIR + filename);
    } catch (IllegalArgumentException iae) {
      // iae.printStackTrace();
      return null;
    }
    try {
      List<String> lines = readLines(url, UTF_8);
      List<Imt> imts = FluentIterable
          .from(splitToList(lines.get(0), Delimiter.COMMA))
          .skip(1)
          .transform(Enums.stringConverter(Imt.class))
          .toList();
      for (Imt imt : imts) {
        map.put(imt, new double[NGA_EAST_MODEL_COUNT]);
      }
      for (int i = 0; i < NGA_EAST_MODEL_COUNT; i++) {
        List<Double> weights = splitToDoubleList(lines.get(i + 1), Delimiter.COMMA);
        for (int j = 1; j < weights.size(); j++) {
          map.get(imts.get(j - 1))[i] = weights.get(j);
        }
      }
    } catch (IOException ioe) {
      handleIOex(ioe, filename);
    }
    return map;
  }

  /* IO error handler */
  static void handleIOex(IOException ioe, String file) {
    StringBuilder sb = new StringBuilder(NEWLINE);
    sb.append("** IO error: ").append("GroundMotionTable; ");
    sb.append(ioe.getMessage()).append(NEWLINE);
    sb.append("**   File: ").append(file).append(NEWLINE);
    sb.append("** Exiting **").append(NEWLINE);
    Logger.getLogger(GroundMotionTables.class.getName()).severe(sb.toString());
    System.exit(1);
  }

  /*
   * Interface implemented by handlers of table-based ground motion data.
   *
   * Single method returns a interpolated ground motion value from the table.
   * Values outside the range supported by the table are generally constrained
   * to min or max values, although implementations may behave differently. Some
   * implementations store data in log space and therefore perform log
   * interpolation.
   *
   * Whether r is rRup or rJB is implementation specific.
   * 
   * NOTE that using position is only valid for distances and magnitdues
   * supported by a table. get(r,m) may return a different result than
   * get(postion(r,m)) if r or m is out of range and a table does not enforce
   * clamping behavior
   */
  interface GroundMotionTable {

    /**
     * Return an interpolated ground motion value from the table. Values outside
     * the range supported by the table are generally constrained to min or max
     * values, although individual implementations may behave differently.
     *
     * @param r distance to consider, whether this is rRup or rJB is
     *        implementation specific
     * @param m magnitude to consider
     * @return the natural log of the ground motion for the supplied {@code r}
     *         and {@code m}
     */
    double get(double r, double m);

    /**
     * Return an interpolated ground motion value from the table corresponding
     * to the supplied table position data.
     *
     * @param p table position data (indices and bin fractions)
     * @return the natural log of the ground motion at supplied table position
     */
    double get(Position p);

    /**
     * Return position data that can be used to derive an interpolated value
     * from a table. This is convenient when repeat lookups from identically
     * structures tables is required.
     * 
     * @param r distance to consider, whether this is rRup or rJB is
     *        implementation specific
     * @param m magnitude to consider
     * @return the {@code Position} in a data table as specified by distance and
     *         magnitude indices and fractions
     */
    Position position(double r, double m);

    static final class Position {

      final int ir;
      final int im;
      final double rFraction;
      final double mFraction;

      Position(int ir, int im, double rFraction, double mFraction) {
        this.ir = ir;
        this.im = im;
        this.rFraction = rFraction;
        this.mFraction = mFraction;
      }

    }
  }

  /*
   * NOTE No data validation is performed in this package private class. It's
   * conceivable someone would supply an inapproprate distance. Negative
   * distances yield an NaN result, r=0 will give the lowest value in a table;
   * log10(0) = -Infinity (for whatever reason) which clamps to the low end of a
   * table.
   */

  /* Base table implementation */
  private static class ClampingTable implements GroundMotionTable {

    final double[][] data;
    final double[] rKeys;
    final double[] mKeys;

    ClampingTable(double[][] data, double[] rKeys, double[] mKeys) {
      this.data = data;
      this.rKeys = rKeys;
      this.mKeys = mKeys;
    }

    @Override
    public double get(double r, double m) {
      return get(position(r, m));
    }

    @Override
    public double get(Position p) {
      return interpolate(data, p);
    }

    @Override
    public Position position(double r, double m) {
      int ir = dataIndex(rKeys, r);
      int im = dataIndex(mKeys, m);
      return new Position(
          ir, im,
          fraction(rKeys[ir], rKeys[ir + 1], r),
          fraction(mKeys[im], mKeys[im + 1], m));
    }
  }

  /*
   * For tables where r keys are log10
   */
  private static class LogDistanceTable extends ClampingTable {

    LogDistanceTable(double[][] data, double[] rKeys, double[] mKeys) {
      super(data, rKeys, mKeys);
    }

    @Override
    public Position position(double r, double m) {
      return super.position(log10(r), m);
    }
  }

  /*
   * For tables where r keys are log10 and ground motion scales like 1/r beyond
   * the table maximum.
   */
  private static class LogDistanceScalingTable extends LogDistanceTable {

    final double rMax;

    LogDistanceScalingTable(double[][] data, double[] rKeys, double[] mKeys) {
      super(data, rKeys, mKeys);
      this.rMax = rKeys[rKeys.length - 1];
    }

    @Override
    public double get(double r, double m) {
      double μLog = super.get(r, m);
      double rLog = log10(r);
      return (rLog <= rMax) ? μLog : μLog - (rLog - rMax);
    }
  }

  // @formatter:off
  /*
   * Basic bilinear interpolation
   *
   *    c11---i1----c12
   *     |     |     |
   *     |-----o-----| < f2
   *     |     |     |
   *    c21---i2----c22
   *           ^
   *          f1
   *
   */
  // @formatter:on

  private static final double interpolate(double[][] data, Position p) {
    return interpolate(
        data[p.ir][p.im],
        data[p.ir][p.im + 1],
        data[p.ir + 1][p.im],
        data[p.ir + 1][p.im + 1],
        p.mFraction,
        p.rFraction);
  }

  private static final double interpolate(
      double c11,
      double c12,
      double c21,
      double c22,
      double f1,
      double f2) {

    double i1 = c11 + f1 * (c12 - c11);
    double i2 = c21 + f1 * (c22 - c21);
    return i1 + f2 * (i2 - i1);
  }

  private static final double fraction(double lo, double hi, double value) {
    return value < lo ? 0.0 : value > hi ? 1.0 : (value - lo) / (hi - lo);
  }

  /*
   * NOTE this was lifted from the interpolate class and could parhaps benefit
   * from checking the size of 'data' and then doing linear instead of binary
   * search.
   *
   * This is a clamping index search algorithm; it will always return an index
   * in the range [0, data.length - 2]; it is always used to get some value at
   * index and index+1
   */
  private static final int dataIndex(final double[] data, final double value) {
    int i = Arrays.binarySearch(data, value);
    // adjust index for low value (-1) and in-sequence insertion pt
    i = (i == -1) ? 0 : (i < 0) ? -i - 2 : i;
    // adjust hi index to next to last index
    return (i >= data.length - 1) ? --i : i;
  }

  /* Parser for Frankel tables. */
  private static class FrankelParser implements LineProcessor<double[][]> {

    boolean firstLine = true;
    List<List<Double>> data = Lists.newArrayList();

    @Override
    public double[][] getResult() {
      return toArray(data);
    }

    @Override
    public boolean processLine(String line) throws IOException {
      if (firstLine) {
        firstLine = false;
        return true;
      }
      List<Double> values = splitToDoubleList(line, Delimiter.SPACE);
      data.add(values.subList(1, values.size()));
      return true;
    }
  }

  /* Parser for NGA-East tables. */
  private static class NgaEastParser implements LineProcessor<Map<Imt, double[][]>> {

    final int rSize;
    int lineCount = -2;
    Imt imt;

    Map<Imt, List<List<Double>>> dataMap = Maps.newEnumMap(Imt.class);
    List<List<Double>> dataLists;

    NgaEastParser(int rSize) {
      this.rSize = rSize;
    }

    @Override
    public Map<Imt, double[][]> getResult() {
      Map<Imt, double[][]> out = Maps.newEnumMap(Imt.class);
      for (Entry<Imt, List<List<Double>>> entry : dataMap.entrySet()) {
        Imt imt = entry.getKey();
        out.put(imt, toArray(entry.getValue()));
      }
      return out;
    }

    @Override
    public boolean processLine(String line) throws IOException {
      lineCount++;

      if (lineCount == -1) {
        imt = Imt.valueOf(line);
        if (dataMap.get(imt) == null) {
          dataLists = new ArrayList<List<Double>>();
          dataMap.put(imt, dataLists);
        }
        return true;
      }

      if (lineCount == 0) {
        return true;
      }

      List<Double> values = splitToDoubleList(line, Delimiter.COMMA);
      List<Double> lnValues = Data.ln(new ArrayList<>(values.subList(1, values.size())));
      dataLists.add(lnValues);

      if (lineCount == rSize) {
        lineCount = -2;
      }
      return true;
    }
  }

  /* Parser for Atkinson style tables. */
  private static class AtkinsonParser implements LineProcessor<Map<Imt, double[][]>> {

    final int rSize;
    int lineIndex = -1;
    int rIndex = -1;
    List<Imt> imts = null;
    Map<Imt, List<List<Double>>> dataMap = Maps.newEnumMap(Imt.class);

    AtkinsonParser(int rSize) {
      this.rSize = rSize;
    }

    @Override
    public Map<Imt, double[][]> getResult() {
      Map<Imt, double[][]> out = Maps.newEnumMap(Imt.class);
      for (Entry<Imt, List<List<Double>>> entry : dataMap.entrySet()) {
        Imt imt = entry.getKey();
        out.put(imt, toArray(entry.getValue()));
      }
      return out;
    }

    @Override
    public boolean processLine(String line) throws IOException {
      lineIndex++;

      if (lineIndex < 2) {
        return true;
      }

      if (lineIndex == 2) {
        List<Imt> imtList = FluentIterable
            .from(Parsing.split(line, Delimiter.SPACE))
            .transform(Doubles.stringConverter())
            .transform(new FrequencyToIMT())
            .toList();
        // remove dupes -- (e.g., 2s PGA columns in P11)
        imts = Lists.newArrayList(new LinkedHashSet<Imt>(imtList));
        for (Imt imt : imts) {
          List<List<Double>> outerList = new ArrayList<List<Double>>(); // r
          dataMap.put(imt, outerList);
          for (int i = 0; i < rSize; i++) {
            List<Double> innerList = new ArrayList<Double>(); // m
            outerList.add(innerList);
          }
        }
        return true;
      }

      List<Double> values = Parsing.splitToDoubleList(line, Delimiter.SPACE);

      if (values.size() == 1) {
        // reset rIndex for every single mag line encountered
        rIndex = -1;
        return true;
      }

      if (values.isEmpty()) {
        return true;
      }

      rIndex++;
      for (int i = 0; i < imts.size(); i++) {
        Imt imt = imts.get(i);
        List<List<Double>> data = dataMap.get(imt);
        data.get(rIndex).add(values.get(i + 1));
      }

      return true;
    }
  }

  /*
   * Converts frequencies from Gail Atkinson style Gmm tables to IMTs.
   * Frequencies corresponding to 0.03s, 0.3s, and 3s are variably identified
   * and handled independently. AB06 uses 0.32, 3.2, and 32 which do not
   * strictly correspond to 3s, 0.3s, and 0.03s, but we use them anyway.
   */
  static class FrequencyToIMT implements Function<Double, Imt> {
    @Override
    public Imt apply(Double f) {
      if (FREQ3_LO.contains(f)) {
        return SA3P0;
      }
      if (FREQ3_MID.contains(f)) {
        return SA0P3;
      }
      if (FREQ3_HI.contains(f)) {
        return SA0P03;
      }
      if (f == 99.0) {
        return PGA;
      }
      if (f == 89.0) {
        return PGV;
      }
      return Imt.fromPeriod(1.0 / f);
    }
  }

  // TODO consider moving to Data
  private static double[][] toArray(List<List<Double>> data) {
    int s1 = data.size();
    int s2 = data.get(0).size();
    double[][] out = new double[s1][s2];
    for (int i = 0; i < s1; i++) {
      for (int j = 0; j < s2; j++) {
        out[i][j] = data.get(i).get(j);
      }
    }
    return out;
  }

}
