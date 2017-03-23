package org.opensha2;

import static java.util.concurrent.Executors.newFixedThreadPool;

import static org.opensha2.internal.TextUtils.NEWLINE;

import org.opensha2.calc.CalcConfig;
import org.opensha2.calc.Calcs;
import org.opensha2.calc.Deaggregation;
import org.opensha2.calc.Hazard;
import org.opensha2.calc.ResultHandler;
import org.opensha2.calc.Site;
import org.opensha2.calc.Sites;
import org.opensha2.calc.ThreadCount;
import org.opensha2.eq.model.HazardModel;
import org.opensha2.internal.Logging;

import com.google.common.base.Optional;
import com.google.common.base.Throwables;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.logging.FileHandler;
import java.util.logging.Logger;

/**
 * Compute probabilisitic seismic hazard at a {@link Site} from a
 * {@link HazardModel}.
 *
 * @author Peter Powers
 */
public class HazardCalc {

  /**
   * Entry point for a probabilisitic seismic hazard calculation.
   *
   * <p>Computing hazard curves requires at least 2, and at most 3, arguments.
   * At a minimum, the path to a model zip file or directory and the site(s) at
   * which to perform calculations must be specified. Under the 2-argument
   * scenario, model initialization and calculation configuration settings are
   * drawn from the config file that <i>must</i> reside at the root of the model
   * directory. Sites may be defined as a string, a CSV file, or a GeoJSON file.
   *
   * <p>To override any default or calculation configuration settings included
   * with the model, supply the path to another configuration file as a third
   * argument.
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

    if (argCount < 2 || argCount > 3) {
      return Optional.of(USAGE);
    }

    try {
      Logging.init();
      Logger log = Logger.getLogger(HazardCalc.class.getName());
      Path tempLog = createTempLog();
      FileHandler fh = new FileHandler(tempLog.getFileName().toString());
      fh.setFormatter(new Logging.ConsoleFormatter());
      log.getParent().addHandler(fh);

      log.info(PROGRAM + ": initializing...");
      Path modelPath = Paths.get(args[0]);
      HazardModel model = HazardModel.load(modelPath);

      CalcConfig config = model.config();
      if (argCount == 3) {
        Path userConfigPath = Paths.get(args[2]);
        config = CalcConfig.Builder.copyOf(model.config())
            .extend(CalcConfig.Builder.fromFile(userConfigPath))
            .build();
      }
      log.info(config.toString());

      log.info("");
      Sites sites = readSites(args[1], config, log);
      log.info("Sites: " + sites);

      Path out = calc(model, config, sites, log);
      log.info(PROGRAM + ": finished");

      // transfer log and write config
      fh.close();
      Files.move(tempLog, out.resolve(PROGRAM + ".log"));
      config.write(out);

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

  static Sites readSites(String arg, CalcConfig defaults, Logger log) {
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

  /*
   * Compute hazard curves using the supplied model, config, and sites. Method
   * returns the path to the directory where results were written.
   */
  private static Path calc(
      HazardModel model,
      CalcConfig config,
      Sites sites,
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

    ResultHandler handler = ResultHandler.create(config, sites, log);
    for (Site site : sites) {
      Hazard hazard = calc(model, config, site, executor);
      handler.add(hazard, Optional.<Deaggregation> absent());
      log.fine(hazard.toString());
    }
    handler.expire();

    log.info(String.format(
        PROGRAM + ": %s sites completed in %s",
        handler.resultsProcessed(), handler.elapsedTime()));

    if (threadCount != ThreadCount.ONE) {
      execSvc.shutdown();
    }
    return handler.outputDir();
  }

  static final String TMP_LOG = "nshmp-haz-log";

  static Path createTempLog() {
    Path logBase = Paths.get(".");
    Path logIncr = logBase.resolve(TMP_LOG);
    int i = 1;
    while (Files.exists(logIncr)) {
      logIncr = logBase.resolve(TMP_LOG + "-" + i);
      i++;
    }
    return logIncr;
  }

  /**
   * Compute hazard curves at a {@code site} for a {@code model} and
   * {@code config}. If an {@code executor} is supplied, it will be used to
   * distribute tasks; otherwise, the calculation will run on the current
   * thread. Be sure to shutdown any supplied executor after a calculation
   * completes.
   *
   * <p><b>Note:</b> any model initialization settings in {@code config} will be
   * ignored as the supplied model will already have been initialized.
   *
   * @param model to use
   * @param config calculation configuration
   * @param site of interest
   * @param executor to use ({@link Optional})
   * @return a {@code Hazard} object
   */
  public static Hazard calc(
      HazardModel model,
      CalcConfig config,
      Site site,
      Optional<Executor> executor) {
    try {
      return Calcs.hazard(model, config, site, executor);
    } catch (ExecutionException | InterruptedException e) {
      throw new RuntimeException(e);
    }
  }
  

  private static final String PROGRAM = HazardCalc.class.getSimpleName();
  private static final String USAGE_COMMAND =
      "java -cp nshmp-haz.jar org.opensha2.HazardCalc model sites [config]";
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
      .append("  'config' (optional) supplies a calculation configuration")
      .append(NEWLINE)
      .append(NEWLINE)
      .append("For more information, see:").append(NEWLINE)
      .append("  ").append(USAGE_URL1).append(NEWLINE)
      .append("  ").append(USAGE_URL2).append(NEWLINE)
      .append(NEWLINE)
      .toString();
}
