package org.opensha2;

import static java.nio.file.StandardOpenOption.APPEND;
import static java.util.concurrent.Executors.newFixedThreadPool;

import static org.opensha2.internal.TextUtils.NEWLINE;

import org.opensha2.calc.CalcConfig;
import org.opensha2.calc.Calcs;
import org.opensha2.calc.Deaggregation;
import org.opensha2.calc.Hazard;
import org.opensha2.calc.Results;
import org.opensha2.calc.ThreadCount;
import org.opensha2.eq.model.HazardModel;
import org.opensha2.internal.Logging;
import org.opensha2.util.Site;
import org.opensha2.util.Sites;

import com.google.common.base.Optional;
import com.google.common.base.Stopwatch;
import com.google.common.base.Throwables;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.logging.FileHandler;
import java.util.logging.Logger;

/**
 * Deaggregate probabilisitic seismic hazard at a {@link Site}.
 *
 * @author Peter Powers
 */
public class DeaggCalc {

  /**
   * Entry point for deaggregating probabilisitic seismic hazard.
   * 
   * <p>Deaggregating siesmic hazard is largeley identical to a hazard
   * calculation except that a return period (in years) must be supplied as an
   * additional argument after the 'site(s)' argument. See the
   * {@link HazardCalc#main(String[]) HazardCalc program} for more information
   * on required parameters.
   * 
   * <p>Please refer to the nshmp-haz <a
   * href="https://github.com/usgs/nshmp-haz/wiki" target="_top">wiki</a> for
   * comprehensive descriptions of source models, configuration files, site
   * files, and hazard calculations.
   *
   * @see <a href="https://github.com/usgs/nshmp-haz/wiki/Building-&-Running"
   *      target="_top"> nshmp-haz wiki</a>
   * @see <a href="https://github.com/usgs/nshmp-haz/tree/master/etc/examples"
   *      target="_top"> example calculations</a>
   */
  public static void main(String[] args) {

    /* Delegate to run which has a return value for testing. */

    Optional<String> status = run(args);
    if (status.isPresent()) {
      System.err.print(status.get());
      System.exit(1);
    }
    System.exit(0);
  }

  static Optional<String> run(String[] args) {
    int argCount = args.length;

    if (argCount < 3 || argCount > 4) {
      return Optional.of(USAGE);
    }

    try {
      Logging.init();
      Logger log = Logger.getLogger(DeaggCalc.class.getName());
      Path tempLog = HazardCalc.createTempLog();
      FileHandler fh = new FileHandler(tempLog.getFileName().toString());
      fh.setFormatter(new Logging.ConsoleFormatter());
      log.getParent().addHandler(fh);

      log.info(PROGRAM + ": initializing...");
      Path modelPath = Paths.get(args[0]);
      HazardModel model = HazardModel.load(modelPath);

      CalcConfig config = model.config();
      if (argCount == 4) {
        Path userConfigPath = Paths.get(args[3]);
        config = CalcConfig.Builder.copyOf(model.config())
            .extend(CalcConfig.Builder.fromFile(userConfigPath))
            .build();
      }
      log.info(config.toString());

      log.info("");
      Iterable<Site> sites = readSites(args[1], config, log);
      log.info("Sites: " + sites);

      double returnPeriod = Double.valueOf(args[2]);

      Path out = calc(model, config, sites, returnPeriod, log);

      // transfer log and write config
      Files.move(tempLog, out.resolve(PROGRAM + ".log"));
      config.write(out);

      log.info(PROGRAM + ": finished");
      return Optional.absent();

    } catch (Exception e) {
      StringBuilder sb = new StringBuilder()
          .append(NEWLINE)
          .append(PROGRAM + ": error").append(NEWLINE)
          .append(" Arguments: ").append(Arrays.toString(args)).append(NEWLINE)
          .append(NEWLINE)
          .append(Throwables.getStackTraceAsString(e))
          .append(USAGE);
      return Optional.of(sb.toString());
    }
  }

  private static Iterable<Site> readSites(String arg, CalcConfig defaults, Logger log) {
    try {
      if (arg.toLowerCase().endsWith(".csv")) {
        Path path = Paths.get(arg);
        log.info("Site file: " + path.toAbsolutePath().normalize());
        return Sites.fromCsv(path, defaults);
      }
      if (arg.toLowerCase().endsWith(".geojson")) {
        Path path = Paths.get(arg);
        log.info("Site file: " + path.toAbsolutePath().normalize());
        return Sites.fromJson(path, defaults);
      }
      return Sites.fromString(arg, defaults);
    } catch (Exception e) {
      throw new IllegalArgumentException(NEWLINE + "    sites = \"" + arg +
          "\" must either be a 3 to 7 argument," + NEWLINE +
          "    comma-delimited string, or specify a path to a *.csv or *.geojson file",
          e);
    }
  }

  private static final OpenOption[] WRITE_OPTIONS = new OpenOption[] {};
  private static final OpenOption[] APPEND_OPTIONS = new OpenOption[] { APPEND };

  /*
   * Compute hazard curves using the supplied model, config, and sites. Method
   * returns the path to the directory where results were written.
   * 
   * TODO consider refactoring to supply an Optional<Double> retrun period to
   * HazardCalc.calc() that will trigger deaggregations if the value is present.
   */
  private static Path calc(
      HazardModel model,
      CalcConfig config,
      Iterable<Site> sites,
      double returnPeriod,
      Logger log) throws IOException {

    ExecutorService execSvc = null;
    ThreadCount threadCount = config.performance.threadCount;
    if (threadCount != ThreadCount.ONE) {
      execSvc = newFixedThreadPool(threadCount.value());
      log.info("Threads: " + ((ThreadPoolExecutor) execSvc).getCorePoolSize());
    } else {
      log.info("Threads: Running on calling thread");
    }
    Optional<Executor> executor = Optional.<Executor> fromNullable(execSvc);

    log.info(PROGRAM + ": calculating ...");
    Stopwatch batchWatch = Stopwatch.createStarted();
    Stopwatch totalWatch = Stopwatch.createStarted();
    int batchCount = 0;
    int siteCount = 0;

    List<Hazard> hazardResults = new ArrayList<>();
    List<Deaggregation> deaggResults = new ArrayList<>();
    boolean firstBatch = true;

    Path outDir = HazardCalc.createOutputDir(config.output.directory);

    for (Site site : sites) {
      Hazard hazard = HazardCalc.calc(model, config, site, executor);
      hazardResults.add(hazard);
      Deaggregation deagg = calc(hazard, returnPeriod);
      deaggResults.add(deagg);
      if (deaggResults.size() == config.output.flushLimit) {
        OpenOption[] opts = firstBatch ? WRITE_OPTIONS : APPEND_OPTIONS;
        firstBatch = false;
        Results.writeResults(outDir, hazardResults, opts);
        Results.writeDeagg(outDir, deaggResults, config);
        log.info(String.format(
            "    batch: %s in %s – %s sites in %s",
            batchCount, batchWatch, siteCount, totalWatch));
        hazardResults.clear();
        deaggResults.clear();
        batchWatch.reset().start();
        batchCount++;
      }
      siteCount++;
    }
    // write final batch
    if (!deaggResults.isEmpty()) {
      OpenOption[] opts = firstBatch ? WRITE_OPTIONS : APPEND_OPTIONS;
      Results.writeResults(outDir, hazardResults, opts);
      Results.writeDeagg(outDir, deaggResults, config);
    }
    log.info(String.format(
        PROGRAM + ": %s sites completed in %s",
        siteCount, totalWatch));

    if (threadCount != ThreadCount.ONE) {
      execSvc.shutdown();
    }

    return outDir;
  }

  /**
   * Deaggregate probabilistic seismic hazard at the supplied return period (in
   * years). Deaggregation currently runs on a single thread.
   * 
   * <p>Call this method with the {@link Hazard} result of
   * {@link HazardCalc#calc(HazardModel, CalcConfig, Site, Optional)} to which
   * you supply the calculation settings and sites of interest that will also be
   * used for deaggregation.
   *
   * <p><b>Note:</b> any model initialization settings in {@code config} will be
   * ignored as the supplied model will already have been initialized.
   *
   * @param returnPeriod at which to deaggregate
   * @return a {@code Deaggregation} object
   */
  public static Deaggregation calc(
      Hazard hazard,
      double returnPeriod) {
    return Calcs.deaggregation(hazard, returnPeriod);
  }

  private static final String PROGRAM = DeaggCalc.class.getSimpleName();
  private static final String USAGE_COMMAND =
      "java -cp nshmp-haz.jar org.opensha2.DeaggCalc model sites returnPeriod [config]";
  private static final String USAGE_URL1 = "https://github.com/usgs/nshmp-haz/wiki";
  private static final String USAGE_URL2 = "https://github.com/usgs/nshmp-haz/tree/master/etc";
  private static final String SITE_STRING = "name,lon,lat[,vs30,vsInf[,z1p0,z2p5]]";

  private static final String USAGE = new StringBuilder()
      .append(NEWLINE)
      .append(PROGRAM).append(" usage:").append(NEWLINE)
      .append("  ").append(USAGE_COMMAND).append(NEWLINE)
      .append(NEWLINE)
      .append("Where:").append(NEWLINE)
      .append("  'model' is a model zip file or directory")
      .append(NEWLINE)
      .append("  'sites' is either:")
      .append(NEWLINE)
      .append("     - a string, e.g. ").append(SITE_STRING)
      .append(NEWLINE)
      .append("       (site class and basin terms are optional)")
      .append(NEWLINE)
      .append("       (escape any spaces or enclose string in double-quotes)")
      .append(NEWLINE)
      .append("     - or a *.csv file or *.geojson file of site data")
      .append(NEWLINE)
      .append("  'returnPeriod', in years, is a time horizon of interest")
      .append(NEWLINE)
      .append("     - e.g. one might enter 2475 to represent a 2% in 50 year probability")
      .append(NEWLINE)
      .append("  'config' (optional) supplies a calculation configuration")
      .append(NEWLINE)
      .append(NEWLINE)
      .append("For more information, see:").append(NEWLINE)
      .append("  ").append(USAGE_URL1).append(NEWLINE)
      .append("  ").append(USAGE_URL2).append(NEWLINE)
      .append(NEWLINE)
      .toString();

}
