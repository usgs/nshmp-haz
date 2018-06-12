package gov.usgs.earthquake.nshmp.internal;

import static com.google.common.base.Preconditions.checkArgument;
import static java.nio.ByteOrder.LITTLE_ENDIAN;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.WRITE;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.util.List;

import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.google.common.io.LittleEndianDataInputStream;
import com.google.common.io.LittleEndianDataOutputStream;

import gov.usgs.earthquake.nshmp.geo.Bounds;
import gov.usgs.earthquake.nshmp.geo.GriddedRegion;
import gov.usgs.earthquake.nshmp.geo.Location;
import gov.usgs.earthquake.nshmp.geo.Regions;
import gov.usgs.earthquake.nshmp.gmm.Imt;
import gov.usgs.earthquake.nshmp.util.Maths;

@Deprecated
class BinaryCurves {

  // // needed becasue SH changed the NSHMP discretization
  // static double[] xPGA = new double[] {0.005, 0.007, 0.0098, 0.0137,
  // 0.0192, 0.0269,
  // 0.0376, 0.0527, 0.0738, 0.103, 0.145, 0.203,
  // 0.284, 0.397, 0.556, 0.778, 1.09, 1.52, 2.2,
  // 3.3};
  //
  // // our 0P30 calcs include an extra value (0.0025), should be identical to
  // 0P20 (5Hz)
  // static double[] x0P30 = Doubles.toArray(Period.GM0P20.getIMLs());

  public static void read(URL url) throws IOException {
    LittleEndianDataInputStream in =
        new LittleEndianDataInputStream(url.openStream());

    CurveContainer cc = new CurveContainer();

    // read names 6 * char(128)
    int n = 128;
    for (int i = 0; i < 6; i++) {
      byte[] nameDat = new byte[n];
      in.read(nameDat, 0, n);
      System.out.println(new String(nameDat));
    }
    float period = in.readFloat();
    int nX = in.readInt();
    Imt imt = (period == 0.0) ? Imt.PGA : Imt.fromPeriod(period);
    System.out.println("period: " + imt);
    System.out.println("nX: " + nX);

    // read x-vals real*4 * 20
    for (int i = 0; i < 20; i++) {
      double val = Maths.round(in.readFloat(), 3);
      System.out.println(val);
      // need to read 20 values to advance caret, but only save ones used
      // if (i<nX) cc.xs.add(val);
    }
    // System.out.println("xVals: " + cc.xs);

    // read extras real*4 * 10
    List<Double> extras = Lists.newArrayList();
    for (int i = 0; i < 10; i++) {
      double val = Maths.round(in.readFloat(), 2);
      extras.add(val);
    }
    System.out.println("extras: " + extras);

    for (int i = 0; i < 1000; i++) {
      System.out.println(in.readFloat());
    }

    in.close();
  }

  public static void write(
      CurveContainer curves,
      Imt imt,
      double[] xs,
      double spacing,
      String desc,
      File out) throws IOException {

    Metadata meta = new Metadata();
    meta.description = desc;
    meta.timestamp = (new Timestamp(System.currentTimeMillis())).toString();
    meta.imt = imt;
    meta.imls = xs;

    Files.createParentDirs(out);
    write(curves, spacing, out, meta);
  }

  public static void writeWus(
      CurveContainer curves,
      Imt imt,
      double[] xs,
      double spacing,
      String desc,
      File out) throws IOException {

    GriddedRegion regionWus = Regions.createRectangularGridded(
        "NSHMP Map Region",
        Location.create(LAT_MIN, LON_MIN),
        Location.create(LAT_MAX, LON_MAX),
        spacing, spacing,
        GriddedRegion.ANCHOR_0_0);

    CurveContainer ccWus = CurveContainer.create(regionWus, xs);
    ccWus.union(curves);

    Metadata meta = new Metadata();
    meta.description = desc;
    meta.timestamp = (new Timestamp(System.currentTimeMillis())).toString();
    meta.imt = imt;
    meta.imls = xs;

    Files.createParentDirs(out);
    writeWus(ccWus, spacing, out, meta);
  }

  private static void write(CurveContainer cc, double outSpacing, File file, Metadata meta)
      throws IOException {
    LittleEndianDataOutputStream out =
        new LittleEndianDataOutputStream(new FileOutputStream(file));

    /* write info lines 6 * char(128) */
    int n = 128;
    Charset charset = Charsets.US_ASCII;
    byte[] desc = Strings.padEnd(meta.description, n, ' ').getBytes(charset);
    byte[] ts = Strings.padEnd(meta.timestamp, n, ' ').getBytes(charset);
    byte[] dummy = Strings.padEnd("", n, ' ').getBytes(charset);
    out.write(desc);
    out.write(ts);
    for (int i = 0; i < 4; i++) {
      out.write(dummy);
    }

    double period = (meta.imt == Imt.PGA) ? 0.0 : meta.imt.period();
    out.writeFloat((float) period);
    out.writeInt(meta.imls.length);

    for (int i = 0; i < meta.imls.length; i++) {
      out.writeFloat((float) meta.imls[i]);
    }
    /*
     * Pad end of curve with 0s so that 20 values are present, even though fewer
     * may be used
     */
    int pad = 20 - meta.imls.length;
    for (int i = 0; i < pad; i++) {
      out.writeFloat(0.0f);
    }

    Bounds b = cc.region.bounds();

    double dLonMin = Math.rint(b.min().lon());
    double dLonMax = Math.rint(b.max().lon());
    double dLatMin = Math.rint(b.min().lat());
    double dLatMax = Math.rint(b.max().lat());

    /* grid info */
    float empty = -1.0f;
    float lonMin = (float) dLonMin; // 2
    float lonMax = (float) dLonMax; // 3
    float spacing = (float) outSpacing; // 4,7
    float latMin = (float) dLatMin; // 5
    float latMax = (float) dLatMax; // 6
    float nodeCt = 64005f; // 3
    float vs = 760.0f; // 9
    float sedDepth = 2.0f; // 10

    out.writeFloat(empty);
    out.writeFloat(lonMin);
    out.writeFloat(lonMax);
    out.writeFloat(spacing);
    out.writeFloat(latMin);
    out.writeFloat(latMax);
    out.writeFloat(spacing);
    out.writeFloat(nodeCt);
    out.writeFloat(vs);
    out.writeFloat(sedDepth);

    // may just be testing header
    if (cc == null) {
      out.close();
      return;
    }

    // write curves
    int nRows = (int) Math.rint((dLatMax - dLatMin) / outSpacing) + 1;
    int nCols = (int) Math.rint((dLonMax - dLonMin) / outSpacing) + 1;
    for (int i = 0; i < nRows; i++) {
      double lat = dLatMax - outSpacing * i;
      for (int j = 0; j < nCols; j++) {
        double lon = dLonMin + outSpacing * j;
        Location loc = Location.create(lat, lon);
        List<Double> vals = cc.getValues(loc);
        // // we compute one too many values for 0.3s; strip first value
        // to
        // // bring array in line with 5Hz
        // if (meta.period == Period.GM0P30) {
        // vals = vals.subList(1, vals.size());
        // }
        for (double val : vals) {
          out.writeFloat((float) val);
        }
      }
    }
    out.close();
  }

  private static final double LON_MIN = -125.0; // 2
  private static final double LON_MAX = -100.0; // 3
  // private static final double SPACING = 0.05; // 4,7
  private static final double LAT_MIN = 24.6; // 5
  private static final double LAT_MAX = 50.0; // 6

  private static void writeWus(CurveContainer cc, double outSpacing, File file, Metadata meta)
      throws IOException {
    LittleEndianDataOutputStream out =
        new LittleEndianDataOutputStream(new FileOutputStream(file));

    // write info lines 6 * char(128)
    int n = 128;
    Charset charset = Charsets.US_ASCII;
    byte[] desc = Strings.padEnd(meta.description, n, ' ').getBytes(charset);
    byte[] ts = Strings.padEnd(meta.timestamp, n, ' ').getBytes(charset);
    byte[] dummy = Strings.padEnd("", n, ' ').getBytes(charset);
    out.write(desc);
    out.write(ts);
    for (int i = 0; i < 4; i++) {
      out.write(dummy);
    }

    double period = (meta.imt == Imt.PGA) ? 0.0 : meta.imt.period();
    out.writeFloat((float) period);
    out.writeInt(meta.imls.length);

    for (int i = 0; i < meta.imls.length; i++) {
      out.writeFloat((float) meta.imls[i]);
    }
    // pad end of curve with 0s so that 20 values are present, even though
    // fewer may be used
    int pad = 20 - meta.imls.length;
    for (int i = 0; i < pad; i++) {
      out.writeFloat(0.0f);
    }

    // grid info
    float empty = -1.0f;
    float lonMin = (float) LON_MIN; // 2
    float lonMax = (float) LON_MAX; // 3
    float spacing = (float) outSpacing; // 4,7
    float latMin = (float) LAT_MIN; // 5
    float latMax = (float) LAT_MAX; // 6
    float nodeCt = 64005f; // 3
    float vs = 760.0f; // 9
    float sedDepth = 2.0f; // 10

    out.writeFloat(empty);
    out.writeFloat(lonMin);
    out.writeFloat(lonMax);
    out.writeFloat(spacing);
    out.writeFloat(latMin);
    out.writeFloat(latMax);
    out.writeFloat(spacing);
    out.writeFloat(nodeCt);
    out.writeFloat(vs);
    out.writeFloat(sedDepth);

    // may just be testing header
    if (cc == null) {
      out.close();
      return;
    }

    // write curves
    int nRows = (int) Math.rint((LAT_MAX - LAT_MIN) / outSpacing) + 1;
    int nCols = (int) Math.rint((LON_MAX - LON_MIN) / outSpacing) + 1;
    for (int i = 0; i < nRows; i++) {
      double lat = LAT_MAX - outSpacing * i;
      for (int j = 0; j < nCols; j++) {
        double lon = LON_MIN + outSpacing * j;
        Location loc = Location.create(lat, lon);
        List<Double> vals = cc.getValues(loc);
        // // we compute one too many values for 0.3s; strip first value
        // to
        // // bring array in line with 5Hz
        // if (meta.period == Period.GM0P30) {
        // vals = vals.subList(1, vals.size());
        // }
        for (double val : vals) {
          out.writeFloat((float) val);
        }
      }
    }
    out.close();
  }

  public static class Metadata {
    String description;
    String timestamp;
    Imt imt;
    double[] imls;

    double vs30 = 760.0;
    double basinDepth = 2.0;
    double spacing = 0.1;

    // TODO replace with Bounds??
    double lonMin = 0;
    double lonMax = 0;
    double latMin = 0;
    double latMax = 0;

  }

  private static final int MAX_IML_COUNT = 20;
  private static final int HEADER_OFFSET = 1664; // bytes
  private static final int INFO_LINE_SIZE = 128; // chars

  /**
   * Writes results to a NSHMP binary format file. NOTE this class will have
   * problems if there are more than 20 IMLs.
   */
  public static class Writer {

    private final Path path;
    private final Metadata meta;
    private final int curveCount;
    private final FileChannel channel;

    public Writer(Path path, Metadata meta) throws IOException {
      checkArgument(meta.imls.length <= MAX_IML_COUNT);
      this.path = path;
      this.meta = meta;
      this.curveCount = curveCount(meta);
      this.channel = FileChannel.open(path, WRITE);

      // init with 0's
      // int
      this.channel.write(createHeader());
      this.channel.write(ByteBuffer.allocate(curveCount * MAX_IML_COUNT));
    }

    /* Header occupies 1664 bytes total */
    private ByteBuffer createHeader() {
      ByteBuffer buf = ByteBuffer.allocate(HEADER_OFFSET).order(LITTLE_ENDIAN);

      /* Info lines: 6 lines * 128 chars * 2 bytes = 1536 */
      byte[] desc = Strings.padEnd(meta.description, INFO_LINE_SIZE, ' ').getBytes(UTF_8);
      byte[] time = Strings.padEnd(meta.timestamp, INFO_LINE_SIZE, ' ').getBytes(UTF_8);
      byte[] dummy = Strings.padEnd("", INFO_LINE_SIZE, ' ').getBytes(UTF_8);

      buf.put(desc)
          .put(time)
          .put(dummy)
          .put(dummy)
          .put(dummy)
          .put(dummy);

      /* Imt and Imls: (1 int + 21 floats) * 4 bytes = 88 */
      float period = (float) ((meta.imt == Imt.PGA) ? 0.0 : meta.imt.period());
      int imlCount = meta.imls.length;
      buf.putFloat(period)
          .putInt(imlCount);
      for (int i = 0; i < MAX_IML_COUNT; i++) {
        buf.putFloat(i < imlCount ? (float) meta.imls[i] : 0.0f);
      }

      /* Grid info: 10 floats * 4 bytes = 40 */
      buf.putFloat(-1.0f) // empty
          .putFloat((float) meta.lonMin)
          .putFloat((float) meta.lonMax)
          .putFloat((float) meta.spacing)
          .putFloat((float) meta.latMin)
          .putFloat((float) meta.latMax)
          .putFloat((float) meta.spacing)
          .putFloat(curveCount)
          .putFloat((float) meta.vs30)
          .putFloat((float) meta.basinDepth);

      return buf;
    }

    private static int curveCount(Metadata m) {
      int lonDim = (int) Math.rint((m.lonMax - m.lonMin) / m.spacing + 1);
      int latDim = (int) Math.rint((m.latMax - m.latMin) / m.spacing + 1);
      return lonDim * latDim;
    }

    /*
     * Compute the target position of a curve in a binary file. NSHMP binary
     * files index ascending in longitude, but descending in latitude.
     */
    private static int curveIndex(Metadata m, Location loc) {
      int rowIndex = (int) Math.rint((m.latMax - loc.lat()) / m.spacing);
      int colIndex = (int) Math.rint((loc.lon() - m.lonMin) / m.spacing);
      return rowIndex * MAX_IML_COUNT + colIndex;
    }

  }
  // // creates a WUS NSHMP curve container and populates it with the supplied
  // // curves; really just handles a fix for large PGA values being slightly
  // // different; SH extended the PGA range from
  // // ... 1.09 1.52 2.13 to
  // // ... 1.09 1.52 2.2 3.3
  // public static CurveContainer createNSHMP(
  // CurveContainer curves, double spacing, Imt imt) {
  //
  // GriddedRegion nshmpRegion = Regions.createRectangularGridded(
  // "NSHMP Map Region",
  // Location.create(LAT_MIN, LON_MIN),
  // Location.create(LAT_MAX, LON_MAX),
  // spacing, spacing,
  // GriddedRegion.ANCHOR_0_0);
  //
  // // if (p != Period.GM0P00) {
  // CurveContainer nshmpCC = CurveContainer.create(nshmpRegion,
  // imt.getIMLs());
  // nshmpCC.union(curves);
  // return nshmpCC;
  // // }
  //
  // // // for PGA
  // // // - create receiver container with 1 more x-value
  // // // - replace 2.13 with extrapolated 2.2 (idx=18)
  // // // - set extrapolated 3.3 (idx=19)
  // //
  // // CurveContainer pgaCC = CurveContainer.create(
  // // TestGrid.CA_NSHMP.grid(spacing), Doubles.asList(xPGA));
  // //
  // // List<Double> xSrc = Period.GM0P00.getIMLs();
  // // double[] xs = {xSrc.get(17), xSrc.get(18)};
  // //
  // // for (Location loc : shaCC) {
  // //
  // // List<Double> ySrc = shaCC.getValues(loc);
  // // List<Double> yDest = pgaCC.getValues(loc);
  // // // copy values at indices 0-17
  // // for (int i=0; i<18; i++) {
  // // yDest.set(i, ySrc.get(i));
  // // }
  // // if (ySrc.get(17) <= 0.0) continue;
  // // double[] ys = {ySrc.get(17), ySrc.get(18)};
  // // double interp2p2 = Interpolate.findLogLogY(xs, ys, 2.2);
  // // double interp3p3 = Interpolate.findLogLogY(xs, ys, 3.3);
  // // yDest.set(18, interp2p2);
  // // yDest.set(19, interp3p3);
  // // }
  // //
  // // CurveContainer nshmpCC = CurveContainer.create(nshmpRegion,
  // // Doubles.asList(xPGA));
  // // nshmpCC.union(pgaCC);
  // //
  // // return nshmpCC;
  // }

}
