package org.opensha2.calc;

import static java.nio.ByteOrder.LITTLE_ENDIAN;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.WRITE;

import static org.opensha2.data.XySequence.emptyCopyOf;

import org.opensha2.calc.Deaggregation.ImtDeagg;
import org.opensha2.data.XySequence;
import org.opensha2.eq.model.Source;
import org.opensha2.eq.model.SourceSet;
import org.opensha2.eq.model.SourceType;
import org.opensha2.geo.Bounds;
import org.opensha2.geo.Location;
import org.opensha2.gmm.Gmm;
import org.opensha2.gmm.Imt;
import org.opensha2.internal.Parsing;
import org.opensha2.internal.Parsing.Delimiter;
import org.opensha2.mfd.Mfds;
import org.opensha2.util.Site;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Strings;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.primitives.Doubles;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Factory class for reducing and exporting various result types.
 *
 * @author Peter Powers
 */
public class Results {

  private static final String BINARY_SUFFIX = ".bin";
  private static final String TEXT_SUFFIX = ".csv";
  private static final String RATE_FMT = "%.8e";

  /*
   * Individual Hazard results only contain data relevant to the site of
   * interest (e.g. for the NSHM WUS models, hazard in San Fancisco is
   * influenced by slab sources whereas hazard in Los Angeles is not because it
   * is too far away). For consistency when outputting batches of results, files
   * are written for all source types and ground motion models supported by the
   * HazardModel being used. This yields curve sets that are consistent across
   * all locations in a batch, however, some of the curves may be empty.
   * Depending on the extents of a map or list of sites, some curve sets may
   * consist exclusively of zero-valued curves.
   */

  /*
   * TODO There is no good reason for this class to be public. It would be
   * better as a ResultsWriter, an instance of which would be obtained and
   * configured on a per-calculation basis to receive batches of results.
   * Although problems are unlikely, we're repeating a number of configuration
   * steps below and relying on the same config file coming with the first
   * result in each batch (below).
   */

  private static final String DEAGG_DIR = "deagg";
  private static final String GMM_DIR = "gmm";

  private static final OpenOption[] WRITE_OPTIONS = new OpenOption[] {};
  private static final OpenOption[] APPEND_OPTIONS = new OpenOption[] { APPEND };

  /*
   * TODO This should be refactored such that much of the work of compiling
   * hazard curves is performed in a HazardExport class, much like DeaggExport
   */

  public static void writeDeagg(
      Path dir,
      List<Deaggregation> batch,
      CalcConfig config) throws IOException {

    Deaggregation demo = batch.get(0);
    boolean namedSites = demo.site.name != Site.NO_NAME;
    boolean gmmDeagg = config.output.curveTypes.contains(CurveType.GMM);

    /*
     * Writing of Hazard results will have already created necessary Imt
     * directories.
     */
    for (Deaggregation deagg : batch) {
      String name = namedSites ? deagg.site.name : lonLatStr(deagg.site.location);
      for (Entry<Imt, ImtDeagg> imtEntry : deagg.deaggs.entrySet()) {

        /* Write total dataset. */
        Path imtDir = dir.resolve(imtEntry.getKey().name());
        Path imtDeaggDir = imtDir.resolve(DEAGG_DIR);
        Files.createDirectories(imtDeaggDir);
        ImtDeagg imtDeagg = imtEntry.getValue();
        DeaggDataset ddTotal = imtDeagg.totalDataset;
        DeaggConfig dc = imtDeagg.config;
        DeaggExport exporter = new DeaggExport(ddTotal, ddTotal, dc, "Total");
        exporter.write(imtDeaggDir, name);

        if (gmmDeagg) {
          for (Entry<Gmm, DeaggDataset> gmmEntry : imtDeagg.gmmDatasets.entrySet()) {
            Path gmmDir = imtDir.resolve(GMM_DIR)
                .resolve(DEAGG_DIR)
                .resolve(gmmEntry.getKey().name());
            Files.createDirectories(gmmDir);
            DeaggDataset ddGmm = gmmEntry.getValue();
            exporter = new DeaggExport(ddTotal, ddGmm, dc, gmmEntry.getKey().toString());
            exporter.write(gmmDir, name);
          }
        }
      }
    }
  }

  private static String lonLatStr(Location loc) {
    return new StringBuilder()
        .append(loc.lon())
        .append("_")
        .append(loc.lat())
        .toString();
  }

  /**
   * Write a {@code batch} of {@code HazardResult}s to files in the specified
   * directory, one for each {@link Imt} in the {@code batch}. See
   * {@link Files#write(Path, Iterable, java.nio.charset.Charset, OpenOption...)}
   * for details on {@code options}. If no {@code options} are specified, the
   * default behavior is to (over)write a new file. In this case a header row
   * will be written as well. Files are encoded as
   * {@link StandardCharsets#US_ASCII}, lat and lon values are formatted to 2
   * decimal places, and curve values are formatted to 8 significant figures.
   *
   * @param dir to write to
   * @param batch of results to write
   * @param append to existing file or create new
   * @throws IOException if a problem is encountered
   * @see Files#write(Path, Iterable, java.nio.charset.Charset, OpenOption...)
   */
  public static void writeResults(
      Path dir,
      List<Hazard> batch,
      boolean append) throws IOException {

    Hazard demo = batch.get(0);
    boolean firstBatch = !append;
    boolean namedSites = demo.site.name != Site.NO_NAME;
    boolean exportGmms = demo.config.output.curveTypes.contains(CurveType.GMM);
    boolean exportSources = demo.config.output.curveTypes.contains(CurveType.SOURCE);
    boolean exportBinary = demo.config.output.curveTypes.contains(CurveType.BINARY);

    Metadata meta = null; //new Metadata();
    ByteBuffer buffer = ByteBuffer.allocate(MAX_IML_COUNT).order(LITTLE_ENDIAN);

    Set<Gmm> gmms = gmmSet(demo.model);

    OpenOption[] options = append ? APPEND_OPTIONS : WRITE_OPTIONS;

    Function<Double, String> formatter = Parsing.formatDoubleFunction(RATE_FMT);
    if (demo.config.curve.valueType == CurveValue.POISSON_PROBABILITY) {
      formatter = Functions.compose(
          formatter,
          Mfds.annualRateToProbabilityConverter());
    }

    /* Line maps for ascii output; may or may not be used */
    Map<Imt, List<String>> totalLines = Maps.newEnumMap(Imt.class);
    Map<Imt, Map<SourceType, List<String>>> typeLines = Maps.newEnumMap(Imt.class);
    Map<Imt, Map<Gmm, List<String>>> gmmLines = Maps.newEnumMap(Imt.class);

    /* Curve maps for binary output; may or may not be used */
    Map<Imt, Map<Integer, XySequence>> totalCurves = Maps.newEnumMap(Imt.class);
    Map<Imt, Map<SourceType, Map<Integer, XySequence>>> typeCurves = Maps.newEnumMap(Imt.class);
    Map<Imt, Map<Gmm, Map<Integer, XySequence>>> gmmCurves = Maps.newEnumMap(Imt.class);

    /* Initialize line maps. */
    for (Imt imt : demo.totalCurves.keySet()) {

      List<String> lines = new ArrayList<>();
      totalLines.put(imt, lines);

      if (firstBatch) {
        Iterable<?> header = Iterables.concat(
            Lists.newArrayList(namedSites ? "name" : null, "lon", "lat"),
            demo.config.curve.modelCurves().get(imt).xValues());
        lines.add(Parsing.join(header, Delimiter.COMMA));
      }

      if (exportSources) {
        Map<SourceType, List<String>> typeMap = new EnumMap<>(SourceType.class);
        typeLines.put(imt, typeMap);
        for (SourceType type : demo.model.types()) {
          typeMap.put(type, Lists.newArrayList(lines));
        }
      }

      if (exportGmms) {
        Map<Gmm, List<String>> gmmMap = new EnumMap<>(Gmm.class);
        gmmLines.put(imt, gmmMap);
        for (Gmm gmm : gmms) {
          gmmMap.put(gmm, Lists.newArrayList(lines));
        }
      }
    }

    /* Initialize curve maps and binary output files. */
    if (exportBinary) {
      for (Imt imt : demo.totalCurves.keySet()) {
        totalCurves.put(imt, new HashMap<Integer, XySequence>());

        if (exportSources) {
          Map<SourceType, Map<Integer, XySequence>> typeMap = new EnumMap<>(SourceType.class);
          typeCurves.put(imt, typeMap);
          for (SourceType type : demo.model.types()) {
            typeMap.put(type, new HashMap<Integer, XySequence>());
          }
        }

        if (exportGmms) {
          Map<Gmm, Map<Integer, XySequence>> gmmMap = new EnumMap<>(Gmm.class);
          gmmCurves.put(imt, gmmMap);
          for (Gmm gmm : gmms) {
            gmmMap.put(gmm, new HashMap<Integer, XySequence>());
          }
        }
      }
    }

    /* Process batch */
    for (Hazard hazard : batch) {

      String name = namedSites ? hazard.site.name : null;
      Location location = hazard.site.location;
      int binIndex = curveIndex(meta.bounds, meta.spacing, location);

      List<String> locData = Lists.newArrayList(
          name,
          String.format("%.5f", hazard.site.location.lon()),
          String.format("%.5f", hazard.site.location.lat()));

      Map<Imt, Map<SourceType, XySequence>> curvesBySource =
          exportSources ? curvesBySource(hazard) : null;

      Map<Imt, Map<Gmm, XySequence>> curvesByGmm =
          exportGmms ? curvesByGmm(hazard) : null;

      for (Entry<Imt, XySequence> imtEntry : hazard.totalCurves.entrySet()) {
        Imt imt = imtEntry.getKey();

        XySequence totalCurve = imtEntry.getValue();
        Iterable<Double> emptyValues = Doubles.asList(new double[totalCurve.size()]);
        String emptyLine = toLine(locData, emptyValues, formatter);

        totalLines.get(imt).add(toLine(
            locData,
            imtEntry.getValue().yValues(),
            formatter));

        if (exportBinary) {
          totalCurves.get(imt).put(binIndex, totalCurve);
        }

        if (exportSources) {
          Map<SourceType, XySequence> sourceCurveMap = curvesBySource.get(imt);
          for (Entry<SourceType, List<String>> typeEntry : typeLines.get(imt).entrySet()) {
            SourceType type = typeEntry.getKey();
            String typeLine = emptyLine;
            if (sourceCurveMap.containsKey(type)) {
              XySequence typeCurve = sourceCurveMap.get(type);
              typeLine = toLine(locData, typeCurve.yValues(), formatter);
              if (exportBinary) {
                typeCurves.get(imt).get(type).put(binIndex, typeCurve);
              }
            }
            typeEntry.getValue().add(typeLine);
          }
        }

        if (exportGmms) {
          Map<Gmm, XySequence> gmmCurveMap = curvesByGmm.get(imt);
          for (Entry<Gmm, List<String>> gmmEntry : gmmLines.get(imt).entrySet()) {
            Gmm gmm = gmmEntry.getKey();
            String gmmLine = emptyLine;
            if (gmmCurveMap.containsKey(gmm)) {
              XySequence gmmCurve = gmmCurveMap.get(gmm);
              gmmLine = toLine(locData, gmmCurveMap.get(gmm).yValues(), formatter);
              if (exportBinary) {
                gmmCurves.get(imt).get(gmm).put(binIndex, gmmCurve);
              }
            }
            gmmEntry.getValue().add(gmmLine);
          }
        }
      }
    }

    /* write/append */
    for (Entry<Imt, List<String>> totalEntry : totalLines.entrySet()) {
      Imt imt = totalEntry.getKey();

      Path imtDir = dir.resolve(imt.name());
      Files.createDirectories(imtDir);
      Path totalFile = imtDir.resolve("total" + TEXT_SUFFIX);
      Files.write(totalFile, totalEntry.getValue(), US_ASCII, options);

      if (exportBinary) {
        Path totalBinFile = imtDir.resolve("total" + BINARY_SUFFIX);
        if (!Files.exists(totalBinFile)) {
          initBinary(totalBinFile, meta);
        }
        writeBinaryBatch(totalBinFile, totalCurves.get(imt), buffer);
      }

      if (exportSources) {
        Path typeDir = imtDir.resolve("source");
        Files.createDirectories(typeDir);
        for (Entry<SourceType, List<String>> typeEntry : typeLines.get(imt).entrySet()) {
          SourceType type = typeEntry.getKey();
          String filename = type.toString();
          Path typeFile = typeDir.resolve(filename + TEXT_SUFFIX);
          Files.write(typeFile, typeEntry.getValue(), US_ASCII, options);
          if (exportBinary) {
            Path typeBinFile = typeDir.resolve(filename + BINARY_SUFFIX);
            if (!Files.exists(typeBinFile)) {
              initBinary(typeBinFile, meta);
            }
            writeBinaryBatch(typeBinFile, typeCurves.get(imt).get(type), buffer);
          }
        }

      }

      if (exportGmms) {
        Path gmmDir = imtDir.resolve("gmm");
        Files.createDirectories(gmmDir);
        for (Entry<Gmm, List<String>> gmmEntry : gmmLines.get(imt).entrySet()) {
          Gmm gmm = gmmEntry.getKey();
          String filename = gmm.name();
          Path gmmFile = gmmDir.resolve(filename + TEXT_SUFFIX);
          Files.write(gmmFile, gmmEntry.getValue(), US_ASCII, options);
          if (exportBinary) {
            Path gmmBinFile = gmmDir.resolve(filename + BINARY_SUFFIX);
            if (!Files.exists(gmmBinFile)) {
              initBinary(gmmBinFile, meta);
            }
            writeBinaryBatch(gmmBinFile, gmmCurves.get(imt).get(gmm), buffer);
          }
        }
      }
    }
  }

  private static String toLine(
      Iterable<String> location,
      Iterable<Double> values,
      Function<Double, String> formatter) {

    return Parsing.join(
        FluentIterable.from(location).append(Iterables.transform(values, formatter)),
        Delimiter.COMMA);
  }

  /**
   * Derive maps of curves by source type for each Imt in a {@code Hazard}
   * result.
   */
  public static Map<Imt, Map<SourceType, XySequence>> curvesBySource(Hazard hazard) {

    EnumMap<Imt, Map<SourceType, XySequence>> imtMap = Maps.newEnumMap(Imt.class);

    // initialize receiver
    Set<SourceType> types = hazard.sourceSetCurves.keySet();
    for (Entry<Imt, XySequence> entry : hazard.curves().entrySet()) {
      imtMap.put(entry.getKey(), initCurves(types, entry.getValue()));
    }

    for (Entry<SourceType, HazardCurveSet> curveSet : hazard.sourceSetCurves.entries()) {
      for (Entry<Imt, XySequence> typeTotals : curveSet.getValue().totalCurves.entrySet()) {
        imtMap.get(typeTotals.getKey())
            .get(curveSet.getKey())
            .add(typeTotals.getValue());
      }
    }
    return Maps.immutableEnumMap(imtMap);
  }

  /**
   * Derive maps of curves by ground motion model for each Imt in a
   * {@code Hazard} result.
   */
  public static Map<Imt, Map<Gmm, XySequence>> curvesByGmm(Hazard hazard) {

    EnumMap<Imt, Map<Gmm, XySequence>> imtMap = Maps.newEnumMap(Imt.class);

    // initialize receiver
    Iterable<SourceSet<? extends Source>> sources = Iterables.transform(
        hazard.sourceSetCurves.values(),
        CURVE_SET_TO_SOURCE_SET);
    Set<Gmm> gmms = gmmSet(sources);
    for (Entry<Imt, XySequence> entry : hazard.curves().entrySet()) {
      imtMap.put(entry.getKey(), initCurves(gmms, entry.getValue()));
    }

    for (HazardCurveSet curveSet : hazard.sourceSetCurves.values()) {
      for (Entry<Imt, Map<Gmm, XySequence>> imtEntry : curveSet.curveMap.entrySet()) {
        for (Entry<Gmm, XySequence> gmmEntry : imtEntry.getValue().entrySet()) {
          imtMap.get(imtEntry.getKey()).get(gmmEntry.getKey()).add(gmmEntry.getValue());
        }
      }
    }
    return Maps.immutableEnumMap(imtMap);
  }

  /* Scan the supplied source sets for the set of all GMMs used. */
  private static Set<Gmm> gmmSet(final Iterable<SourceSet<? extends Source>> sourceSets) {
    return Sets.immutableEnumSet(
        FluentIterable.from(sourceSets).transformAndConcat(
            new Function<SourceSet<? extends Source>, Set<Gmm>>() {
              @Override
              public Set<Gmm> apply(SourceSet<? extends Source> sourceSet) {
                return sourceSet.groundMotionModels().gmms();
              }
            }));
  }

  /*
   * Initalize a map of curves, one entry for each of the supplied enum keys.
   */
  private static <K extends Enum<K>> Map<K, XySequence> initCurves(
      final Set<K> keys,
      final XySequence model) {
    return Maps.immutableEnumMap(
        FluentIterable.from(keys).toMap(
            new Function<K, XySequence>() {
              @Override
              public XySequence apply(K key) {
                return emptyCopyOf(model);
              }
            }));
  }

  private static final Function<HazardCurveSet, SourceSet<? extends Source>> CURVE_SET_TO_SOURCE_SET =
      new Function<HazardCurveSet, SourceSet<? extends Source>>() {
        @Override
        public SourceSet<? extends Source> apply(HazardCurveSet curves) {
          return curves.sourceSet;
        }
      };

  /*
   * Binary file export utilities.
   */

  private static final int MAX_IML_COUNT = 20;
  private static final int CURVE_SIZE = MAX_IML_COUNT * 4; // bytes
  private static final int HEADER_OFFSET = 1664; // bytes
  private static final int INFO_LINE_SIZE = 128; // chars

  static final class Metadata {

    final Bounds bounds;
    final double spacing;
    final String description;
    final String timestamp;
    final Imt imt;
    final List<Double> imls;
    final double vs30;
    final double basin = 0.0;
    
    final int curveCount;

    Metadata(
        Bounds bounds,
        double spacing,
        String description,
        String timestamp,
        Imt imt,
        List<Double> imls,
        double vs30) {

      this.bounds = bounds;
      this.spacing = spacing;
      this.description = description;
      this.timestamp = timestamp;
      this.imt = imt;
      this.imls = imls;
      this.vs30 = vs30;
      
      this.curveCount = curveCount(bounds, spacing);
    }
  }

  private static void writeBinaryBatch(
      Path path,
      Map<Integer, XySequence> curves,
      ByteBuffer buf) throws IOException {

    FileChannel channel = FileChannel.open(path, WRITE);
    for (Entry<Integer, XySequence> entry : curves.entrySet()) {
      toBuffer(entry.getValue(), buf);
      int position = HEADER_OFFSET + entry.getKey() * CURVE_SIZE;
      channel.write(buf, position);
    }
    channel.close();
  }

  private static void toBuffer(XySequence curve, ByteBuffer buf) {
    buf.clear();
    for (double y : curve.yValues()) {
      buf.putFloat((float) y);
    }
  }
  
  private static void initBinary(Path path, Metadata m) throws IOException {
    FileChannel channel = FileChannel.open(path, WRITE);
    channel.write(createHeader(m));
    channel.write(ByteBuffer.allocate(m.curveCount * MAX_IML_COUNT));
    channel.close();
  }
  
  /* Header occupies 1664 bytes total */
  private static ByteBuffer createHeader(Metadata m) {
    ByteBuffer buf = ByteBuffer.allocate(HEADER_OFFSET).order(LITTLE_ENDIAN);

    /* Info lines: 6 lines * 128 chars * 2 bytes = 1536 */
    byte[] desc = Strings.padEnd(m.description, INFO_LINE_SIZE, ' ').getBytes(US_ASCII);
    byte[] time = Strings.padEnd(m.timestamp, INFO_LINE_SIZE, ' ').getBytes(US_ASCII);
    byte[] dummy = Strings.padEnd("", INFO_LINE_SIZE, ' ').getBytes(US_ASCII);

    buf.put(desc)
        .put(time)
        .put(dummy)
        .put(dummy)
        .put(dummy)
        .put(dummy);

    /* Imt and Imls: (1 int + 21 floats) * 4 bytes = 88 */
    float period = (float) ((m.imt == Imt.PGA) ? 0.0 : m.imt.period());
    int imlCount = m.imls.size();
    buf.putFloat(period)
        .putInt(imlCount);
    for (int i = 0; i < MAX_IML_COUNT; i++) {
      buf.putFloat(i < imlCount ? m.imls.get(i).floatValue() : 0.0f);
    }

    /* Grid info: 10 floats * 4 bytes = 40 */
    buf.putFloat(-1.0f) // empty
        .putFloat((float) m.bounds.min().lon())
        .putFloat((float) m.bounds.max().lon())
        .putFloat((float) m.spacing)
        .putFloat((float) m.bounds.min().lat())
        .putFloat((float) m.bounds.max().lat())
        .putFloat((float) m.spacing)
        .putFloat(m.curveCount)
        .putFloat((float) m.vs30)
        .putFloat((float) m.basin);

    return buf;
  }

  private static int curveCount(Bounds b, double spacing) {
    int lonDim = (int) Math.rint((b.max().lon() - b.min().lon()) / spacing) + 1;
    int latDim = (int) Math.rint((b.max().lat() - b.min().lat()) / spacing) + 1;
    return lonDim * latDim;
  }

  /*
   * Compute the target position of a curve in a binary file. NSHMP binary files
   * index ascending in longitude, but descending in latitude.
   */
  private static int curveIndex(Bounds b, double spacing, Location loc) {
    int rowIndex = (int) Math.rint((b.max().lat() - loc.lat()) / spacing);
    int colIndex = (int) Math.rint((loc.lon() - b.min().lon()) / spacing);
    return rowIndex * MAX_IML_COUNT + colIndex;
  }

}
