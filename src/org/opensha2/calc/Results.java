package org.opensha2.calc;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static java.nio.ByteOrder.LITTLE_ENDIAN;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
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

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Optional;
import com.google.common.base.Stopwatch;
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
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Hazard calculation result exporter.
 *
 * @author Peter Powers
 */
public final class Results {

  private static final String DEAGG_DIR = "deagg";
  private static final String GMM_DIR = "gmm";
  private static final String BINARY_SUFFIX = ".bin";
  private static final String TEXT_SUFFIX = ".csv";
  private static final String RATE_FMT = "%.8e";

  private static final OpenOption[] WRITE_OPTIONS = new OpenOption[] {};
  private static final OpenOption[] APPEND_OPTIONS = new OpenOption[] { APPEND };

  private final Logger log;
  private final Path dir;
  private final CalcConfig config;
  private final boolean exportGmm;
  private final boolean exportSource;
  private final boolean exportBinary;

  private final Stopwatch batchWatch;
  private final Stopwatch totalWatch;
  private int batchCount = 1;
  private int resultCount = 1;

  private final boolean namedSites;
  private boolean firstBatch = true;
  private boolean used = false;

  private final List<Hazard> hazards;
  private final List<Deaggregation> deaggs;

  /*
   * Currently used when intializing binary files; the only variation in
   * metadata is for different Imt and their attendant IMLs, which are largely
   * unused when processed by other codes.
   */
  private final Metadata.Builder metaBuilder;
  private final Metadata metaDefault;

  private Results(CalcConfig config, Sites sites, Logger log) {
    this.log = log;
    this.dir = createOutputDir(config.output.directory);
    this.config = config;
    this.exportGmm = config.output.curveTypes.contains(CurveType.GMM);
    this.exportSource = config.output.curveTypes.contains(CurveType.SOURCE);
    this.exportBinary = config.output.curveTypes.contains(CurveType.BINARY);
    this.hazards = new ArrayList<>();
    this.deaggs = new ArrayList<>();

    Site demoSite = sites.iterator().next();
    this.namedSites = demoSite.name() != Site.NO_NAME;

    this.batchWatch = Stopwatch.createStarted();
    this.totalWatch = Stopwatch.createStarted();

    if (exportBinary) {
      checkState(exportBinary && sites.mapBounds().isPresent(),
          BINARY_EXTENTS_REQUIRED_MSSG);
      this.metaBuilder = Metadata.builder()
          .bounds(sites.mapBounds().get())
          .spacing(sites.mapSpacing().get())
          .description("nshmp-haz generated curves")
          .timestamp(new Timestamp(System.currentTimeMillis()).toString())
          .vs30(demoSite.vs30);
      this.metaDefault = metaBuilder.build();
    } else {
      metaBuilder = null;
      metaDefault = null;
    }
  }

  /**
   * Create a new results handler.
   * 
   * @param config that specifies output options and formats
   * @param sites reference to the sites to be processed (not retained)
   * @param log shared logging instance from calling class
   * @throws IllegalStateException if binary output has been specified in the
   *         {@code config} but the {@code sites} container does not specify map
   *         extents.
   */
  public static Results create(
      CalcConfig config,
      Sites sites,
      Logger log) {

    return new Results(config, sites, log);
  }

  /* Avoid clobbering exsting result directories via incrementing. */
  private static Path createOutputDir(Path dir) {
    int i = 1;
    Path incrementedDir = dir;
    while (Files.exists(incrementedDir)) {
      incrementedDir = incrementedDir.resolveSibling(dir.getFileName() + "-" + i);
      i++;
    }
    return incrementedDir;
  }

  /**
   * Add a Hazard and optional Deaggregation result to this handler.
   * 
   * @param hazard to add
   * @param deagg to add
   */
  public void add(Hazard hazard, Optional<Deaggregation> deagg) throws IOException {
    checkState(!used, "This result handler is expired");
    hazards.add(hazard);
    if (deagg.isPresent()) {
      deaggs.add(deagg.get());
    }
    if (hazards.size() == config.output.flushLimit) {
      flush();
      log.info(String.format(
          "     batch: %s in %s – %s sites in %s",
          batchCount, batchWatch, resultCount, totalWatch));
      batchWatch.reset().start();
      batchCount++;
    }
    resultCount++;
  }

  /**
   * Flush any stored Hazard and Deaggregation results to file, clearing
   */
  public void flush() throws IOException {
    if (!hazards.isEmpty()) {
      writeHazards();
      hazards.clear();
      firstBatch = false;
    }
    if (!deaggs.isEmpty()) {
      writeDeaggs();
      deaggs.clear();
    }
  }

  /**
   * Calls {@link #flush()} a final time, stops all timers and sets the state of
   * this {@code Results} instance to 'used'; no more results may be added.
   */
  public void expire() throws IOException {
    flush();
    batchWatch.stop();
    totalWatch.stop();
    used = true;
  }

  /**
   * The number of hazard [and deagg] results passed to this handler thus far.
   */
  public int resultsProcessed() {
    return resultCount;
  }

  /**
   * The number of {@code Hazard} results this handler is currently storing.
   */
  public int size() {
    return hazards.size();
  }

  /**
   * A string representation of the time duration that this result handler has
   * been running.
   */
  public String elapsedTime() {
    return totalWatch.toString();
  }

  /**
   * The target output directory established by this handler.
   */
  public Path outputDir() {
    return dir;
  }

  /*
   * Write the current list of {@code Hazard}s to file.
   */
  private void writeHazards() throws IOException {

    Hazard demo = hazards.get(0);

    ByteBuffer buffer = ByteBuffer.allocate(CURVE_SIZE).order(LITTLE_ENDIAN);

    Set<Gmm> gmms = gmmSet(demo.model);

    OpenOption[] options = !firstBatch ? APPEND_OPTIONS : WRITE_OPTIONS;

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

      if (exportSource) {
        Map<SourceType, List<String>> typeMap = new EnumMap<>(SourceType.class);
        typeLines.put(imt, typeMap);
        for (SourceType type : demo.model.types()) {
          typeMap.put(type, Lists.newArrayList(lines));
        }
      }

      if (exportGmm) {
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

        if (exportSource) {
          Map<SourceType, Map<Integer, XySequence>> typeMap = new EnumMap<>(SourceType.class);
          typeCurves.put(imt, typeMap);
          for (SourceType type : demo.model.types()) {
            typeMap.put(type, new HashMap<Integer, XySequence>());
          }
        }

        if (exportGmm) {
          Map<Gmm, Map<Integer, XySequence>> gmmMap = new EnumMap<>(Gmm.class);
          gmmCurves.put(imt, gmmMap);
          for (Gmm gmm : gmms) {
            gmmMap.put(gmm, new HashMap<Integer, XySequence>());
          }
        }
      }
    }

    /* Process batch */
    for (Hazard hazard : hazards) {

      String name = namedSites ? hazard.site.name : null;
      Location location = hazard.site.location;

      List<String> locData = Lists.newArrayList(
          name,
          String.format("%.5f", hazard.site.location.lon()),
          String.format("%.5f", hazard.site.location.lat()));

      Map<Imt, Map<SourceType, XySequence>> curvesBySource =
          exportSource ? curvesBySource(hazard) : null;

      Map<Imt, Map<Gmm, XySequence>> curvesByGmm =
          exportGmm ? curvesByGmm(hazard) : null;

      for (Entry<Imt, XySequence> imtEntry : hazard.totalCurves.entrySet()) {
        Imt imt = imtEntry.getKey();

        XySequence totalCurve = imtEntry.getValue();
        Iterable<Double> emptyValues = Doubles.asList(new double[totalCurve.size()]);
        String emptyLine = toLine(locData, emptyValues, formatter);

        totalLines.get(imt).add(toLine(
            locData,
            imtEntry.getValue().yValues(),
            formatter));

        int binIndex = -1;
        if (exportBinary) {
          binIndex = curveIndex(metaDefault.bounds, metaDefault.spacing, location);
          totalCurves.get(imt).put(binIndex, totalCurve);
        }

        if (exportSource) {
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

        if (exportGmm) {
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
        writeBinaryBatch(totalBinFile, totalCurves.get(imt), buffer, imt);
      }

      if (exportSource) {
        Path typeDir = imtDir.resolve("source");
        Files.createDirectories(typeDir);
        for (Entry<SourceType, List<String>> typeEntry : typeLines.get(imt).entrySet()) {
          SourceType type = typeEntry.getKey();
          String filename = type.toString();
          Path typeFile = typeDir.resolve(filename + TEXT_SUFFIX);
          Files.write(typeFile, typeEntry.getValue(), US_ASCII, options);
          if (exportBinary) {
            Path typeBinFile = typeDir.resolve(filename + BINARY_SUFFIX);
            writeBinaryBatch(typeBinFile, typeCurves.get(imt).get(type), buffer, imt);
          }
        }
      }

      if (exportGmm) {
        Path gmmDir = imtDir.resolve("gmm");
        Files.createDirectories(gmmDir);
        for (Entry<Gmm, List<String>> gmmEntry : gmmLines.get(imt).entrySet()) {
          Gmm gmm = gmmEntry.getKey();
          String filename = gmm.name();
          Path gmmFile = gmmDir.resolve(filename + TEXT_SUFFIX);
          Files.write(gmmFile, gmmEntry.getValue(), US_ASCII, options);
          if (exportBinary) {
            Path gmmBinFile = gmmDir.resolve(filename + BINARY_SUFFIX);
            writeBinaryBatch(gmmBinFile, gmmCurves.get(imt).get(gmm), buffer, imt);
          }
        }
      }
    }
  }

  /*
   * Write the current list of {@code Deaggregation}s to file.
   */
  private void writeDeaggs() throws IOException {

    /*
     * Writing of Hazard results will have already created necessary Imt
     * directories.
     */
    for (Deaggregation deagg : deaggs) {
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

        if (exportGmm) {
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

  private static String toLine(
      Iterable<String> location,
      Iterable<Double> values,
      Function<Double, String> formatter) {

    return Parsing.join(
        FluentIterable.from(location).append(Iterables.transform(values, formatter)),
        Delimiter.COMMA);
  }

  private static String lonLatStr(Location loc) {
    return new StringBuilder()
        .append(loc.lon())
        .append("_")
        .append(loc.lat())
        .toString();
  }

  /* Derive maps of curves by source type for each Imt. */
  private static Map<Imt, Map<SourceType, XySequence>> curvesBySource(Hazard hazard) {

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

    /* Initialize receiver */
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

  /* Initalize a map of curves, one entry for each of the supplied enum key. */
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
  private static final int HEADER_OFFSET = 896; // bytes
  private static final int INFO_LINE_SIZE = 128; // chars

  private static final String BINARY_EXTENTS_REQUIRED_MSSG =
      "Binary output is only supported when map extents are defined\n" +
          "    See: https://github.com/usgs/nshmp-haz/wiki/Sites#map-regions";

  static final class Metadata {

    final Bounds bounds;
    final double spacing;
    final String description;
    final String timestamp;
    final Imt imt;
    final List<Double> imls;
    final double vs30;
    final double basin = 0.0;
    final int gridSize;

    static Builder builder() {
      return new Builder();
    }

    Metadata(
        Bounds bounds,
        double spacing,
        String description,
        String timestamp,
        Imt imt,
        List<Double> imls,
        double vs30,
        int gridSize) {

      this.bounds = bounds;
      this.spacing = spacing;
      this.description = description;
      this.timestamp = timestamp;
      this.imt = imt;
      this.imls = imls;
      this.vs30 = vs30;
      this.gridSize = gridSize;
    }

    static final class Builder {

      private Bounds bounds;
      private double spacing;
      private String description;
      private String timestamp;
      private Imt imt;
      private List<Double> imls;
      private double vs30;

      private Builder() {}

      Builder bounds(Bounds bounds) {
        this.bounds = bounds;
        return this;
      }

      Builder spacing(double spacing) {
        this.spacing = spacing;
        return this;
      }

      Builder description(String description) {
        this.description = description;
        return this;
      }

      Builder timestamp(String timestamp) {
        this.timestamp = timestamp;
        return this;
      }

      Builder imt(Imt imt) {
        this.imt = imt;
        return this;
      }

      Builder imls(List<Double> imls) {
        checkArgument(imls.size() <= MAX_IML_COUNT,
            "Binary output only supports <=20 IMLs");
        this.imls = imls;
        return this;
      }

      Builder vs30(double vs30) {
        this.vs30 = vs30;
        return this;
      }

      Metadata build() {

        return new Metadata(
            bounds,
            spacing,
            description,
            timestamp,
            imt,
            imls,
            vs30,
            gridSize(bounds, spacing));
      }
    }
  }

  private void writeBinaryBatch(
      Path path,
      Map<Integer, XySequence> curves,
      ByteBuffer buf,
      Imt imt) throws IOException {

    if (!Files.exists(path)) {
      initBinary(path, imt);
    }

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
    buf.flip();
  }

  private void initBinary(Path path, Imt imt) throws IOException {
    Metadata m = metaBuilder
        .imt(imt)
        .imls(config.curve.modelCurve(imt).xValues())
        .build();

    FileChannel channel = FileChannel.open(path, CREATE, TRUNCATE_EXISTING, WRITE);
    ByteBuffer header = createHeader(m);
    header.flip();
    channel.write(header);
    channel.write(ByteBuffer.allocate(m.gridSize * MAX_IML_COUNT));
    channel.close();
  }

  /* Header occupies 1664 bytes total */
  private static ByteBuffer createHeader(Metadata m) {
    ByteBuffer buf = ByteBuffer.allocate(HEADER_OFFSET).order(LITTLE_ENDIAN);

    /* Info lines: 6 lines * 128 chars (1 byte) = 768 */
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
    float period = (float) ((m.imt == Imt.PGA) ? 0.0 : (m.imt == Imt.PGV) ? -1.0 : m.imt.period());
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
        .putFloat(m.gridSize)
        .putFloat((float) m.vs30)
        .putFloat((float) m.basin);

    return buf;
  }

  private static int gridSize(Bounds b, double spacing) {
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
