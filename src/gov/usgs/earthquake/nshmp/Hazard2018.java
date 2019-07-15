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
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
import gov.usgs.earthquake.nshmp.eq.model.HazardModel;
import gov.usgs.earthquake.nshmp.internal.Logging;

/**
 * Compute probabilisitic seismic hazard for the 2018 Conterminous U.S. hazard
 * model. This class deals with teh joint calculation of hazard in WUS and CEUS
 * regions.
 *
 * @author Peter Powers
 */
public class Hazard2018 {

  /**
   * Entry point for a 2018 Conterminous U.S. hazard calculation.
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
    Logger log = Logger.getLogger(Hazard2018.class.getName());
    Path tmpLog = createTempLog();

    try {
      FileHandler fh = new FileHandler(tmpLog.getFileName().toString());
      fh.setFormatter(new Logging.ConsoleFormatter());
      log.getParent().addHandler(fh);

      log.info(PROGRAM + ": " + VERSION);
      Path modelPath = Paths.get(args[0]);
      Path wusPath = modelPath.resolve("Western US");
      Path ceusPath = modelPath.resolve("Central & Eastern US");

      HazardModel wusModel = HazardModel.load(wusPath);
      HazardModel ceusModel = HazardModel.load(ceusPath);

      CalcConfig wusConfig = wusModel.config();
      CalcConfig ceusConfig = ceusModel.config();
      if (argCount == 3) {
        CalcConfig.Builder userConfig = CalcConfig.Builder.fromFile(Paths.get(args[2]));
        wusConfig = CalcConfig.Builder.copyOf(wusModel.config())
            .extend(userConfig)
            .build();
        ceusConfig = CalcConfig.Builder.copyOf(ceusModel.config())
            .extend(userConfig)
            .build();
      }
      log.info(wusConfig.toString());

      log.info("");
      Sites sites = readSites(args[1], wusConfig, log);
      log.info("Sites: " + sites);

      Models models = new Models(wusModel, wusConfig, ceusModel, ceusConfig);
      Path out = calcControl(models, sites, log);

      // Path out = calc(model, config, sites, log);
      log.info(PROGRAM + ": finished");

      /* Transfer log and write config, windows requires fh.close() */
      fh.close();
      Files.move(tmpLog, out.resolve(PROGRAM + ".log"));
      wusConfig.write(out);

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
   * Developer notes:
   * 
   * This program will not correctly output results by source type and GMM due
   * to model combining; only one model is listed in a combined hazard result.
   * This needs to be fixed; see Hazard.merge() for additional notes.
   */
  private static Path calcControl(
      Models models,
      Sites sites,
      Logger log) throws IOException, InterruptedException, ExecutionException {

    int threadCount = models.wusConfig.performance.threadCount.value();
    final ExecutorService exec = initExecutor(threadCount);
    log.info("Threads: " + ((ThreadPoolExecutor) exec).getCorePoolSize());

    HazardExport handler = HazardExport.create(models.wusModel, models.wusConfig, sites, log);

    log.info(PROGRAM + ": calculating ...");

    CalcTask.Builder calcTask = new CalcTask.Builder(models, exec);
    WriteTask.Builder writeTask = new WriteTask.Builder(handler);

    Future<Path> out = null;
    for (Site site : sites) {
      Hazard hazard = calcTask.withSite(site).call();
      out = exec.submit(writeTask.withResult(hazard));
    }
    /* Block shutdown until last task is returned. */
    Path outputDir = out.get();

    handler.expire();
    exec.shutdown();
    log.info(String.format(
        PROGRAM + ": %s sites completed in %s",
        handler.resultCount(), handler.elapsedTime()));

    return outputDir;
  }

  private static ExecutorService initExecutor(int threadCount) {
    if (threadCount == 1) {
      return MoreExecutors.newDirectExecutorService();
    } else {
      return Executors.newFixedThreadPool(threadCount);
    }
  }

  private static final class Models {

    final HazardModel wusModel;
    final CalcConfig wusConfig;
    final HazardModel ceusModel;
    final CalcConfig ceusConfig;

    Models(
        HazardModel wusModel,
        CalcConfig wusConfig,
        HazardModel ceusModel,
        CalcConfig ceusConfig) {

      this.wusModel = wusModel;
      this.wusConfig = wusConfig;
      this.ceusModel = ceusModel;
      this.ceusConfig = ceusConfig;
    }
  }

  private static final class WriteTask implements Callable<Path> {

    final HazardExport handler;
    final Hazard hazard;

    WriteTask(HazardExport handler, Hazard hazard) {
      this.handler = handler;
      this.hazard = hazard;
    }

    @Override
    public Path call() throws IOException {
      handler.write(hazard);
      return handler.outputDir();
    }

    static class Builder {

      final HazardExport handler;

      Builder(HazardExport handler) {
        this.handler = handler;
      }

      /* Builds and returns the task. */
      WriteTask withResult(Hazard hazard) {
        return new WriteTask(handler, hazard);
      }
    }
  }

  private static final class CalcTask implements Callable<Hazard> {

    final Models models;
    final Executor exec;
    final Site site;

    CalcTask(Models models, Executor exec, Site site) {
      this.models = models;
      this.exec = exec;
      this.site = site;
    }

    @Override
    public Hazard call() {
      Hazard wusHazard = null;
      if (site.location.lon() <= -100.0) {
        wusHazard = HazardCalcs.hazard(models.wusModel, models.wusConfig, site, exec);
      }
      Hazard ceusHazard = null;
      if (site.location.lon() > -115.0) {
        ceusHazard = HazardCalcs.hazard(models.ceusModel, models.ceusConfig, site, exec);
      }
      Hazard cousHazard = (wusHazard == null)
          ? ceusHazard
          : (ceusHazard == null)
              ? wusHazard
              : Hazard.merge(wusHazard, ceusHazard);
      return cousHazard;
    }

    static class Builder {

      final Models models;
      final Executor exec;

      Builder(Models models, Executor exec) {
        this.models = models;
        this.exec = exec;
      }

      /* Builds and returns the task. */
      CalcTask withSite(Site site) {
        return new CalcTask(models, exec, site);
      }
    }
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

  private static final String PROGRAM = Hazard2018.class.getSimpleName();
  private static final String USAGE_COMMAND =
      "java -cp nshmp-haz.jar gov.usgs.earthquake.nshmp.Hazard2018 model sites [config]";
  private static final String USAGE_URL1 = "https://github.com/usgs/nshmp-haz/wiki";
  private static final String USAGE_URL2 = "https://github.com/usgs/nshmp-haz/tree/master/etc";
  private static final String SITE_STRING = "name,lon,lat[,vs30,vsInf[,z1p0,z2p5]]";

  private static String version() {
    String version = "unknown";
    /* Assume we're running from a jar. */
    try {
      InputStream is = Hazard2018.class.getResourceAsStream("/app.properties");
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
      .append("  'model' is a model directory")
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
