package gov.usgs.earthquake.nshmp.calc;

import static com.google.common.base.Preconditions.checkState;
import static java.nio.file.StandardOpenOption.APPEND;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import java.util.logging.Logger;

import com.google.common.base.Stopwatch;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.primitives.Doubles;

import gov.usgs.earthquake.nshmp.data.XySequence;
import gov.usgs.earthquake.nshmp.eq.model.HazardModel;
import gov.usgs.earthquake.nshmp.eq.model.SourceType;
import gov.usgs.earthquake.nshmp.geo.Location;
import gov.usgs.earthquake.nshmp.internal.Parsing;
import gov.usgs.earthquake.nshmp.internal.Parsing.Delimiter;

/**
 * Earthquake rate and probability exporter.
 *
 * @author Peter Powers
 */
public final class EqRateExport {

  private static final String RATE_FORMAT = "%.8g";
  private static final String PROB_FORMAT = "%.2f";
  private static final String RATE_FILE = "rates.csv";
  private static final String PROB_FILE = "probs.csv";

  private final Logger log;
  private final Path dir;
  private final String file;
  private final String valueFormat;
  private final HazardModel model;
  private final boolean exportSource;

  private final Stopwatch batchWatch;
  private final Stopwatch totalWatch;
  private int batchCount = 0;
  private int resultCount = 0;

  private final boolean namedSites;
  private boolean firstRecord = true;
  private boolean used = false;

  private EqRateExport(
      HazardModel model,
      CalcConfig config,
      Sites sites,
      Logger log) throws IOException {

    // whether or not rates or probabilities have been calculated
    boolean rates = config.rate.valueFormat == ValueFormat.ANNUAL_RATE;

    this.log = log;
    this.dir = HazardExport.createOutputDir(config.output.directory);
    this.file = rates ? RATE_FILE : PROB_FILE;
    this.valueFormat = rates ? RATE_FORMAT : PROB_FORMAT;
    this.model = model;
    this.exportSource = config.output.dataTypes.contains(DataType.SOURCE);

    Site demoSite = sites.iterator().next();
    this.namedSites = demoSite.name() != Site.NO_NAME;

    this.batchWatch = Stopwatch.createStarted();
    this.totalWatch = Stopwatch.createStarted();
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
  public static EqRateExport create(
      HazardModel model,
      CalcConfig config,
      Sites sites,
      Logger log) throws IOException {

    return new EqRateExport(model, config, sites, log);
  }

  /**
   * Add an {@code EqRate} to this exporter.
   * 
   * @param rate data container to add
   */
  public void write(EqRate rate) throws IOException {
    checkState(!used, "This result handler is expired");
    writeRate(rate);
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
   * Flushes any remaining results, stops all timers and sets the state of this
   * exporter to 'used'; no more results may be added.
   */
  public void expire() throws IOException {
    batchWatch.stop();
    totalWatch.stop();
    used = true;
  }

  /**
   * The number of rate results passed to this handler thus far.
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

  /*
   * Write the current list of {@code EqRate}s to file.
   */
  private void writeRate(EqRate rate) throws IOException {

    Iterable<Double> emptyValues = Doubles.asList(new double[rate.totalMfd.size()]);
    Function<Double, String> formatter = Parsing.formatDoubleFunction(valueFormat);

    /* Can't init output until we have a demo rate record for mfd x-values */
    if (firstRecord) {
      init(rate);
      firstRecord = false;
    }

    String name = namedSites ? rate.site.name : null;
    Location location = rate.site.location;

    List<String> locData = Lists.newArrayList(
        name,
        String.format("%.5f", location.lon()),
        String.format("%.5f", location.lat()));

    String totalLine = toLine(locData, rate.totalMfd.yValues(), formatter);

    String emptyLine = toLine(locData, emptyValues, formatter);

    /* write/append */
    Path totalFile = dir.resolve(file);
    HazardExport.writeLine(totalFile, totalLine, APPEND);
    if (exportSource) {
      Path parentDir = dir.resolve(HazardExport.TYPE_DIR);
      for (SourceType type : model.types()) {
        Path typeFile = parentDir.resolve(type.name()).resolve(file);
        String typeLine = emptyLine;
        if (rate.typeMfds.containsKey(type)) {
          XySequence typeRate = rate.typeMfds.get(type);
          typeLine = toLine(locData, typeRate.yValues(), formatter);
        }
        HazardExport.writeLine(typeFile, typeLine, APPEND);
      }
    }
  }

  private void init(EqRate demo) throws IOException {
    Iterable<?> headerElements = Iterables.concat(
        Lists.newArrayList(namedSites ? "name" : null, "lon", "lat"),
        demo.totalMfd.xValues());
    String header = Parsing.join(headerElements, Delimiter.COMMA);
    Path totalFile = dir.resolve(file);
    HazardExport.writeLine(totalFile, header);

    if (exportSource) {
      Path parentDir = dir.resolve(HazardExport.TYPE_DIR);
      Files.createDirectory(parentDir);
      // TODO get source types from model
      for (SourceType type : model.types()) {
        Path typeDir = parentDir.resolve(type.name());
        Files.createDirectory(typeDir);
        Path typeFile = typeDir.resolve(file);
        HazardExport.writeLine(typeFile, header);
      }
    }
  }

  private static String toLine(
      Iterable<String> location,
      Iterable<Double> values,
      Function<Double, String> formatter) {

    return Parsing.join(
        FluentIterable.from(location)
            .append(Iterables.transform(
                values,
                formatter::apply)),
        Delimiter.COMMA);
  }

}
