package gov.usgs.earthquake.nshmp.calc;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static gov.usgs.earthquake.nshmp.calc.ValueFormat.POISSON_PROBABILITY;
import static gov.usgs.earthquake.nshmp.data.XySequence.emptyCopyOf;
import static java.nio.ByteOrder.LITTLE_ENDIAN;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Logger;

import com.google.common.base.Stopwatch;
import com.google.common.base.Strings;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.primitives.Doubles;

import gov.usgs.earthquake.nshmp.calc.Deaggregation.ImtDeagg;
import gov.usgs.earthquake.nshmp.calc.HazardExport.Metadata.Builder;
import gov.usgs.earthquake.nshmp.data.XySequence;
import gov.usgs.earthquake.nshmp.eq.model.HazardModel;
import gov.usgs.earthquake.nshmp.eq.model.Source;
import gov.usgs.earthquake.nshmp.eq.model.SourceSet;
import gov.usgs.earthquake.nshmp.eq.model.SourceType;
import gov.usgs.earthquake.nshmp.geo.Bounds;
import gov.usgs.earthquake.nshmp.geo.Location;
import gov.usgs.earthquake.nshmp.gmm.Gmm;
import gov.usgs.earthquake.nshmp.gmm.Imt;
import gov.usgs.earthquake.nshmp.internal.Parsing;
import gov.usgs.earthquake.nshmp.internal.Parsing.Delimiter;
import gov.usgs.earthquake.nshmp.mfd.Mfds;

/**
 * Hazard calculation result exporter.
 *
 * @author Peter Powers
 */
public final class HazardExport {

  /*
   * Developer notes
   * 
   * Export of gmm and source type is unreliable for results where multiple
   * models may have been run (e.g. Hazard2018) because the model.types() and
   * model.gmms() methods will only reflect a single model.
   */

  static final String DEAGG_DIR = "deagg";
  static final String GMM_DIR = "gmm";
  static final String TYPE_DIR = "source";
  static final String CURVE_FILE_ASCII = "curves.csv";
  static final String CURVE_FILE_BINARY = "curves.bin";
  static final String VALUE_FMT = "%.8e";

  private final Logger log;
  private final Path dir;
  private final HazardModel model;
  private final CalcConfig config;
  private final boolean exportGmm;
  private final boolean exportSource;
  private final boolean exportBinary;

  private final Function<Double, String> valueFormatter;

  private final Stopwatch batchWatch;
  private final Stopwatch totalWatch;
  private int batchCount = 0;
  private int resultCount = 0;

  private final boolean namedSites;
  private boolean used = false;

  /* Only used for binary file export. */
  private final Map<Imt, Metadata> metaMap;

  private HazardExport(
      HazardModel model,
      CalcConfig config,
      Sites sites,
      Logger log) throws IOException {

    this.log = log;
    this.dir = createOutputDir(config.output.directory);
    this.model = model;
    this.config = config;
    this.exportGmm = config.output.dataTypes.contains(DataType.GMM);
    this.exportSource = config.output.dataTypes.contains(DataType.SOURCE);
    this.exportBinary = config.output.dataTypes.contains(DataType.BINARY);

    Function<Double, String> formatter = Parsing.formatDoubleFunction(VALUE_FMT);
    this.valueFormatter = (config.hazard.valueFormat == POISSON_PROBABILITY)
        ? Mfds.annualRateToProbabilityConverter().andThen(formatter)
        : formatter;

    Site demoSite = sites.iterator().next();
    this.namedSites = demoSite.name() != Site.NO_NAME;

    this.batchWatch = Stopwatch.createStarted();
    this.totalWatch = Stopwatch.createStarted();

    this.metaMap = new EnumMap<>(Imt.class);
    init(sites);
  }

  /**
   * Create a new results handler.
   * 
   * @param model being run
   * @param config that specifies output options and formats
   * @param sites reference to the sites to be processed (not retained)
   * @param log shared logging instance from calling class
   * @throws IllegalStateException if binary output has been specified in the
   *         {@code config} but the {@code sites} container does not specify map
   *         extents.
   */
  public static HazardExport create(
      HazardModel model,
      CalcConfig config,
      Sites sites,
      Logger log) throws IOException {

    return new HazardExport(model, config, sites, log);
  }

  /* Prep binary headers and initialize output directories. */
  private void init(Sites sites) throws IOException {

    if (exportBinary) {
      checkState(exportBinary && sites.mapBounds().isPresent(), BINARY_EXTENTS_REQUIRED_MSSG);
      Builder metaBuilder = Metadata.builder()
          .bounds(sites.mapBounds().get())
          .spacing(sites.mapSpacing().get())
          .description("nshmp-haz generated curves")
          .timestamp(new Timestamp(System.currentTimeMillis()).toString())
          .vs30(sites.iterator().next().vs30);
      for (Entry<Imt, XySequence> entry : config.hazard.modelCurves().entrySet()) {
        Imt imt = entry.getKey();
        Metadata meta = metaBuilder
            .imt(imt)
            .imls(entry.getValue().xValues())
            .build();
        this.metaMap.put(imt, meta);
      }
    }

    for (Imt imt : config.hazard.imts) {

      Path imtDir = dir.resolve(imt.name());
      Files.createDirectory(imtDir);
      Path totalFile = imtDir.resolve(CURVE_FILE_ASCII);

      Iterable<?> headerValues = Iterables.concat(
          Lists.newArrayList(namedSites ? "name" : null, "lon", "lat"),
          config.hazard.modelCurves().get(imt).xValues());
      String header = Parsing.join(headerValues, Delimiter.COMMA);
      writeLine(totalFile, header);

      Metadata meta = null;

      if (exportBinary) {
        Path totalBinFile = imtDir.resolve(CURVE_FILE_BINARY);
        meta = metaMap.get(imt);
        initBinary(totalBinFile, meta);
      }

      if (exportSource) {
        Path typeParent = imtDir.resolve(TYPE_DIR);
        Files.createDirectory(typeParent);
        for (SourceType type : model.types()) {
          Path typeDir = typeParent.resolve(type.name());
          Files.createDirectory(typeDir);
          Path typeFile = typeDir.resolve(CURVE_FILE_ASCII);
          writeLine(typeFile, header);
          if (exportBinary) {
            Path typeBinFile = typeDir.resolve(CURVE_FILE_BINARY);
            initBinary(typeBinFile, meta);
          }
        }
      }

      if (exportGmm) {
        Path gmmParent = imtDir.resolve(GMM_DIR);
        Files.createDirectory(gmmParent);
        for (Gmm gmm : model.gmms()) {
          Path gmmDir = gmmParent.resolve(gmm.name());
          Files.createDirectory(gmmDir);
          Path gmmFile = gmmDir.resolve(CURVE_FILE_ASCII);
          writeLine(gmmFile, header);
          if (exportBinary) {
            Path gmmBinFile = gmmDir.resolve(CURVE_FILE_BINARY);
            initBinary(gmmBinFile, meta);
          }
        }
      }
    }
  }

  /* Avoid clobbering exsting result directories via incrementing. */
  static Path createOutputDir(Path dir) throws IOException {
    int i = 1;
    Path incrementedDir = dir;
    while (Files.exists(incrementedDir)) {
      incrementedDir = incrementedDir.resolveSibling(dir.getFileName() + "-" + i);
      i++;
    }
    Files.createDirectories(incrementedDir);
    return incrementedDir;
  }

  /**
   * Write a hazard result.
   * 
   * @param hazard to write
   */
  public void write(Hazard hazard) throws IOException {
    write(hazard, Optional.empty());
  }

  /**
   * Write a hazard and deagg result.
   * 
   * @param hazard to write
   * @param deagg to write (optional)
   */
  public void write(Hazard hazard, Optional<Deaggregation> deagg) throws IOException {
    checkState(!used, "This result handler is expired");
    writeHazard(hazard);
    if (deagg.isPresent()) {
      writeDeagg(deagg.get());
    }
    resultCount++;
    if (resultCount % 10 == 0) {
      batchCount++;
      log.info(String.format(
          "     batch: %s in %s – %s sites in %s",
          batchCount, batchWatch, resultCount, totalWatch));
      batchWatch.reset().start();
    }
  }

  /**
   * Calls {@link #flush()} a final time, stops all timers and sets the state of
   * this {@code Results} instance to 'used'; no more results may be added.
   */
  public void expire() throws IOException {
    batchWatch.stop();
    totalWatch.stop();
    used = true;
  }

  /**
   * The number of hazard [and deagg] results passed to this handler thus far.
   */
  public int resultCount() {
    return resultCount;
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

  /* Write the supplied hazard and possible deagg results to file(s). */
  private void writeHazard(Hazard hazard) throws IOException {

    Set<Gmm> gmms = hazard.model.gmms();
    Set<SourceType> types = hazard.model.types();

    String name = namedSites ? hazard.site.name : null;
    Location location = hazard.site.location;

    List<String> locData = Lists.newArrayList(
        name,
        String.format("%.5f", location.lon()),
        String.format("%.5f", location.lat()));

    Map<Imt, Map<SourceType, XySequence>> curvesBySource = exportSource
        ? curvesBySource(hazard)
        : null;

    Map<Imt, Map<Gmm, XySequence>> curvesByGmm = exportGmm
        ? curvesByGmm(hazard)
        : null;

    for (Entry<Imt, XySequence> imtEntry : hazard.totalCurves.entrySet()) {
      XySequence totalCurve = imtEntry.getValue();

      /* Utility empty line to write if source or gmm is missing at site */
      Iterable<Double> emptyValues = Doubles.asList(new double[totalCurve.size()]);
      String emptyLine = toLine(locData, emptyValues, valueFormatter);

      Imt imt = imtEntry.getKey();
      Path imtDir = dir.resolve(imt.name());

      String totalLine = toLine(locData, totalCurve.yValues(), valueFormatter);

      Path totalFile = imtDir.resolve(CURVE_FILE_ASCII);
      writeLine(totalFile, totalLine, APPEND);

      Metadata meta = null;
      int binIndex = -1;
      if (exportBinary) {
        meta = metaMap.get(imt);
        binIndex = curveIndex(meta.bounds, meta.spacing, location);
        Path totalBinFile = imtDir.resolve(CURVE_FILE_BINARY);
        writeBinary(totalBinFile, meta, totalCurve, binIndex);
      }

      if (exportSource) {
        Map<SourceType, XySequence> sourceCurveMap = curvesBySource.get(imt);
        Path typeParent = imtDir.resolve(TYPE_DIR);
        for (SourceType type : types) {
          Path typeDir = typeParent.resolve(type.name());
          Path typeFile = typeDir.resolve(CURVE_FILE_ASCII);
          String typeLine = emptyLine;
          if (sourceCurveMap.containsKey(type)) {
            XySequence typeCurve = sourceCurveMap.get(type);
            typeLine = toLine(locData, typeCurve.yValues(), valueFormatter);
            if (exportBinary) {
              Path typeBinFile = typeDir.resolve(CURVE_FILE_BINARY);
              writeBinary(typeBinFile, meta, typeCurve, binIndex);
            }
          }
          writeLine(typeFile, typeLine, APPEND);
        }
      }

      if (exportGmm) {
        Map<Gmm, XySequence> gmmCurveMap = curvesByGmm.get(imt);
        Path gmmParent = imtDir.resolve(GMM_DIR);
        for (Gmm gmm : gmms) {
          Path gmmDir = gmmParent.resolve(gmm.name());
          Path gmmFile = gmmDir.resolve(CURVE_FILE_ASCII);
          String gmmLine = emptyLine;
          if (gmmCurveMap.containsKey(gmm)) {
            XySequence gmmCurve = gmmCurveMap.get(gmm);
            gmmLine = toLine(locData, gmmCurve.yValues(), valueFormatter);
            if (exportBinary) {
              Path gmmBinFile = gmmDir.resolve(CURVE_FILE_BINARY);
              writeBinary(gmmBinFile, meta, gmmCurve, binIndex);
            }
          }
          writeLine(gmmFile, gmmLine, APPEND);
        }
      }
    }
  }

  /*
   * Write the current list of {@code Deaggregation}s to file.
   */
  private void writeDeagg(Deaggregation deagg) throws IOException {

    /*
     * Writing of Hazard results will have already created necessary Imt, Gmm,
     * and SourceType directories.
     */
    String name = namedSites ? deagg.site.name : lonLatStr(deagg.site.location);
    for (Entry<Imt, ImtDeagg> imtEntry : deagg.deaggs.entrySet()) {

      /* Write total dataset. */
      ImtDeagg imtDeagg = imtEntry.getValue();
      DeaggDataset ddTotal = imtDeagg.totalDataset;
      DeaggConfig dc = imtDeagg.config;
      DeaggExport exporter = new DeaggExport(ddTotal, ddTotal, dc, "Total", false, true, true);
      Path imtDir = dir.resolve(imtEntry.getKey().name());
      Path totalDir = imtDir.resolve(DEAGG_DIR);
      Files.createDirectories(totalDir);
      exporter.toFile(totalDir, name);

      if (exportSource) {
        for (Entry<SourceType, DeaggDataset> typeEntry : imtDeagg.typeDatasets.entrySet()) {
          SourceType type = typeEntry.getKey();
          Path typeDir = imtDir.resolve(TYPE_DIR)
              .resolve(type.name())
              .resolve(DEAGG_DIR);
          DeaggDataset ddType = typeEntry.getValue();
          exporter = new DeaggExport(ddTotal, ddType, dc, type.toString(), false, true, true);
          exporter.toFile(typeDir, name);
        }
      }

      if (exportGmm) {
        for (Entry<Gmm, DeaggDataset> gmmEntry : imtDeagg.gmmDatasets.entrySet()) {
          Gmm gmm = gmmEntry.getKey();
          Path gmmDir = imtDir.resolve(GMM_DIR)
              .resolve(gmm.name())
              .resolve(DEAGG_DIR);
          DeaggDataset ddGmm = gmmEntry.getValue();
          exporter = new DeaggExport(ddTotal, ddGmm, dc, gmm.toString(), false, true, true);
          exporter.toFile(gmmDir, name);
        }
      }
    }
  }

  private static String toLine(
      Iterable<String> location,
      Iterable<Double> values,
      Function<Double, String> formatter) {

    return Parsing.join(
        FluentIterable.from(location).append(
            Iterables.transform(
                values,
                formatter::apply)),
        Delimiter.COMMA);
  }

  private static String lonLatStr(Location loc) {
    return new StringBuilder()
        .append(loc.lon())
        .append("_")
        .append(loc.lat())
        .toString();
  }

  /* No options will write a new file, supply APPEND to append. */
  static void writeLine(Path path, String line, OpenOption... options)
      throws IOException {

    Files.write(
        path,
        (line + System.lineSeparator()).getBytes(UTF_8),
        options);
  }

  /**
   * Derive maps of curves by {@code SourceType} for each {@code Imt}.
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
   * Derive maps of curves by {@code Gmm} for each {@code Imt} in a
   * {@code Hazard} result.
   */
  public static Map<Imt, Map<Gmm, XySequence>> curvesByGmm(Hazard hazard) {

    EnumMap<Imt, Map<Gmm, XySequence>> imtMap = Maps.newEnumMap(Imt.class);

    /* Initialize receiver */
    Iterable<SourceSet<? extends Source>> sources = Iterables.transform(
        hazard.sourceSetCurves.values(),
        CURVE_SET_TO_SOURCE_SET::apply);
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

  /*
   * Scan the supplied source sets for the set of all GMMs used.
   * 
   * Note that this is used when consolidating gmm result curves. Results may
   * not span the entire range of GMMs in a model so this is commonly a subset
   */
  // TODO Stream
  private static Set<Gmm> gmmSet(final Iterable<SourceSet<? extends Source>> sourceSets) {
    return Sets.immutableEnumSet(
        FluentIterable.from(sourceSets).transformAndConcat(
            new Function<SourceSet<? extends Source>, Set<Gmm>>() {
              @Override
              public Set<Gmm> apply(SourceSet<? extends Source> sourceSet) {
                return sourceSet.groundMotionModels().gmms();
              }
            }::apply));
  }

  /* Initalize a map of curves, one entry for each of the supplied enum key. */
  // TODO Stream
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
            }::apply));
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

  private static final int HEADER_MAX_IMLS = 20;
  private static final int HEADER_OFFSET = 896; // bytes
  private static final int INFO_LINE_SIZE = 128; // chars

  static final String BINARY_EXTENTS_REQUIRED_MSSG =
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
    final int curveByteSize;
    final ByteBuffer buffer;

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
      this.curveByteSize = imls.size() * 4;
      this.buffer = ByteBuffer.allocate(curveByteSize).order(LITTLE_ENDIAN);
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
        checkArgument(imls.size() <= HEADER_MAX_IMLS,
            "Binary output only supports <= 20 IMLs");
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

  private void writeBinary(
      Path path,
      Metadata meta,
      XySequence curve,
      int curveIndex) throws IOException {
    FileChannel channel = FileChannel.open(path, WRITE);
    int position = HEADER_OFFSET + curveIndex * meta.curveByteSize;
    toBuffer(curve, meta.buffer);
    channel.write(meta.buffer, position);
    channel.close();
  }

  private static void toBuffer(XySequence curve, ByteBuffer buffer) {
    buffer.clear();
    for (double y : curve.yValues()) {
      buffer.putFloat((float) y);
    }
    buffer.flip();
  }

  private void initBinary(Path path, Metadata meta) throws IOException {
    FileChannel channel = FileChannel.open(path, CREATE, TRUNCATE_EXISTING, WRITE);
    ByteBuffer header = createHeader(meta);
    header.flip();
    channel.write(header);
    /* Initialize with zero-valued curves. */
    channel.write(ByteBuffer.allocate(meta.gridSize * meta.curveByteSize));
    channel.close();
  }

  /* Header occupies 1664 bytes total */
  private static ByteBuffer createHeader(Metadata m) {
    ByteBuffer buffer = ByteBuffer.allocate(HEADER_OFFSET).order(LITTLE_ENDIAN);

    /* Info lines: 6 lines * 128 chars (1 byte) = 768 */
    byte[] desc = Strings.padEnd(m.description, INFO_LINE_SIZE, ' ').getBytes(UTF_8);
    byte[] time = Strings.padEnd(m.timestamp, INFO_LINE_SIZE, ' ').getBytes(UTF_8);
    byte[] dummy = Strings.padEnd("", INFO_LINE_SIZE, ' ').getBytes(UTF_8);

    buffer.put(desc)
        .put(time)
        .put(dummy)
        .put(dummy)
        .put(dummy)
        .put(dummy);

    /* Imt and Imls: (1 int + 21 floats) * 4 bytes = 88 */
    float period = (float) ((m.imt == Imt.PGA) ? 0.0 : (m.imt == Imt.PGV) ? -1.0 : m.imt.period());
    int imlCount = m.imls.size();
    buffer.putFloat(period)
        .putInt(imlCount);
    for (int i = 0; i < HEADER_MAX_IMLS; i++) {
      buffer.putFloat(i < imlCount ? m.imls.get(i).floatValue() : 0.0f);
    }

    /* Grid info: 10 floats * 4 bytes = 40 */
    buffer.putFloat(-1.0f) // empty
        .putFloat((float) m.bounds.min().lon())
        .putFloat((float) m.bounds.max().lon())
        .putFloat((float) m.spacing)
        .putFloat((float) m.bounds.min().lat())
        .putFloat((float) m.bounds.max().lat())
        .putFloat((float) m.spacing)
        .putFloat(m.gridSize)
        .putFloat((float) m.vs30)
        .putFloat((float) m.basin);

    return buffer;
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
    int columnCount = (int) Math.rint((b.max().lon() - b.min().lon()) / spacing) + 1;
    int rowIndex = (int) Math.rint((b.max().lat() - loc.lat()) / spacing);
    int colIndex = (int) Math.rint((loc.lon() - b.min().lon()) / spacing);
    return rowIndex * columnCount + colIndex;
  }

}
