package gov.usgs.earthquake.nshmp;

import static gov.usgs.earthquake.nshmp.internal.TextUtils.NEWLINE;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.logging.FileHandler;
import java.util.logging.Logger;

import com.google.common.base.Throwables;
import com.google.common.util.concurrent.MoreExecutors;

import gov.usgs.earthquake.nshmp.calc.CalcConfig;
import gov.usgs.earthquake.nshmp.calc.Hazard;
import gov.usgs.earthquake.nshmp.calc.HazardCalcs;
import gov.usgs.earthquake.nshmp.calc.HazardExport;
import gov.usgs.earthquake.nshmp.calc.Site;
import gov.usgs.earthquake.nshmp.calc.Sites;
import gov.usgs.earthquake.nshmp.calc.ThreadCount;
import gov.usgs.earthquake.nshmp.eq.model.HazardModel;
import gov.usgs.earthquake.nshmp.internal.Logging;

/**
 * Compute probabilisitic seismic hazard from a {@link HazardModel}.
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

    Logging.init();
    Logger log = Logger.getLogger(HazardCalc.class.getName());
    Path tmpLog = createTempLog();

    try {
      FileHandler fh = new FileHandler(tmpLog.getFileName().toString());
      fh.setFormatter(new Logging.ConsoleFormatter());
      log.getParent().addHandler(fh);

      log.info(PROGRAM + ": " + VERSION);
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

      /* Transfer log and write config, windows requires fh.close() */
      fh.close();
      Files.move(tmpLog, out.resolve(PROGRAM + ".log"));
      config.write(out);

      return Optional.empty();

    } catch (Exception e) {
      return handleError(e, log, tmpLog, args, PROGRAM, USAGE);
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
      throw new IllegalArgumentException(NEWLINE +
          "    sites = \"" + arg + "\" must either be a 3 to 7 argument," + NEWLINE +
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

    ExecutorService exec = null;
    ThreadCount threadCount = config.performance.threadCount;
    if (threadCount == ThreadCount.ONE) {
      exec = MoreExecutors.newDirectExecutorService();
      log.info("Threads: Running on calling thread");
    } else {
      exec = Executors.newFixedThreadPool(threadCount.value());
      log.info("Threads: " + ((ThreadPoolExecutor) exec).getCorePoolSize());
    }

    log.info(PROGRAM + ": calculating ...");

    HazardExport handler = HazardExport.create(model, config, sites, log);
    for (Site site : sites) {
      Hazard hazard = HazardCalcs.hazard(model, config, site, exec);
      handler.write(hazard);
      log.fine(hazard.toString());
    }
    handler.expire();

    log.info(String.format(
        PROGRAM + ": %s sites completed in %s",
        handler.resultCount(), handler.elapsedTime()));

    exec.shutdown();
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

  static Optional<String> handleError(
      Exception e,
      Logger log,
      Path logfile,
      String[] args,
      String program,
      String usage) {

    log.severe(NEWLINE + "** Exiting **");
    try {
      // cleanup; do nothing on failure
      Files.deleteIfExists(logfile);
    } catch (IOException ioe) {}
    StringBuilder sb = new StringBuilder()
        .append(NEWLINE)
        .append(program + ": error").append(NEWLINE)
        .append(" Arguments: ").append(Arrays.toString(args)).append(NEWLINE)
        .append(NEWLINE)
        .append(Throwables.getStackTraceAsString(e))
        .append(usage);
    return Optional.of(sb.toString());
  }

  /**
   * The Git application version. This version string applies to all other
   * nshnmp-haz applications.
   */
  public static final String VERSION = version();

  private static final String PROGRAM = HazardCalc.class.getSimpleName();
  private static final String USAGE_COMMAND =
      "java -cp nshmp-haz.jar gov.usgs.earthquake.nshmp.HazardCalc model sites [config]";
  private static final String USAGE_URL1 = "https://github.com/usgs/nshmp-haz/wiki";
  private static final String USAGE_URL2 = "https://github.com/usgs/nshmp-haz/tree/master/etc";
  private static final String SITE_STRING = "name,lon,lat[,vs30,vsInf[,z1p0,z2p5]]";

  private static String version() {
    String version = "unknown";
    /* Assume we're running from a jar. */
    try {
      InputStream is = HazardCalc.class.getResourceAsStream("/app.properties");
      Properties props = new Properties();
      props.load(is);
      is.close();
      version = props.getProperty("app.version");
    } catch (Exception e1) {
      /* Otherwise check for a repository. */
      Path gitDir = Paths.get(".git");
      if (Files.exists(gitDir)) {
        try {
          Process pr = Runtime.getRuntime().exec("git describe --tags");
          BufferedReader br = new BufferedReader(new InputStreamReader(pr.getInputStream()));
          version = br.readLine();
          br.close();
          /* Detached from repository. */
        } catch (Exception e2) {}
      }
    }
    return version;
  }

  private static final String USAGE = new StringBuilder()
      .append(NEWLINE)
      .append(PROGRAM).append(" [").append(VERSION).append("]").append(NEWLINE)
      .append(NEWLINE)
      .append("Usage:").append(NEWLINE)
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
