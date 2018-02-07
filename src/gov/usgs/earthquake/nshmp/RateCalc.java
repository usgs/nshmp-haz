package gov.usgs.earthquake.nshmp;

import static gov.usgs.earthquake.nshmp.internal.TextUtils.NEWLINE;
import static java.util.concurrent.Executors.newFixedThreadPool;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.logging.FileHandler;
import java.util.logging.Logger;

import gov.usgs.earthquake.nshmp.calc.CalcConfig;
import gov.usgs.earthquake.nshmp.calc.EqRate;
import gov.usgs.earthquake.nshmp.calc.EqRateExport;
import gov.usgs.earthquake.nshmp.calc.Site;
import gov.usgs.earthquake.nshmp.calc.Sites;
import gov.usgs.earthquake.nshmp.calc.ThreadCount;
import gov.usgs.earthquake.nshmp.eq.model.HazardModel;
import gov.usgs.earthquake.nshmp.internal.Logging;

/**
 * Compute earthquake rates or Poisson probabilities from a {@link HazardModel}.
 *
 * @author Peter Powers
 */
public class RateCalc {

  /**
   * Entry point for the calculation of earthquake rates and probabilities.
   *
   * <p>Computing earthquake rates requires at least 2, and at most 3,
   * arguments. At a minimum, the path to a model zip file or directory and the
   * site(s) at which to perform calculations must be specified. Under the
   * 2-argument scenario, model initialization and calculation configuration
   * settings are drawn from the config file that <i>must</i> reside at the root
   * of the model directory. Sites may be defined as a string, a CSV file, or a
   * GeoJSON file.
   *
   * <p>To override any default or calculation configuration settings included
   * with the model, supply the path to another configuration file as a third
   * argument.
   *
   * <p>Please refer to the nshmp-haz <a
   * href="https://github.com/usgs/nshmp-haz/wiki" target="_top">wiki</a> for
   * comprehensive descriptions of source models, configuration files, site
   * files, and earthquake rate calculations.
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
    Path tmpLog = HazardCalc.createTempLog();

    try {
      FileHandler fh = new FileHandler(tmpLog.getFileName().toString());
      fh.setFormatter(new Logging.ConsoleFormatter());
      log.getParent().addHandler(fh);

      log.info(PROGRAM + ": " + HazardCalc.VERSION);
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
      Sites sites = HazardCalc.readSites(args[1], config, log);
      log.info("Sites: " + sites);

      Path out = calc(model, config, sites, log);
      log.info(PROGRAM + ": finished");

      /* Transfer log and write config, windows requires fh.close() */
      fh.close();
      Files.move(tmpLog, out.resolve(PROGRAM + ".log"));
      config.write(out);

      return Optional.empty();

    } catch (Exception e) {
      return HazardCalc.handleError(e, log, tmpLog, args, PROGRAM, USAGE);
    }
  }

  /*
   * Compute earthquake rates or probabilities using the supplied model, config,
   * and sites. Method returns the path to the directory where results were
   * written.
   * 
   * Unlike hazard calculations, which spread work out over multiple threads for
   * a single calculation, rate calculations are single threaded. Concurrent
   * calculations for multiple sites are handled below.
   */
  private static Path calc(
      HazardModel model,
      CalcConfig config,
      Sites sites,
      Logger log) throws IOException, ExecutionException, InterruptedException {

    ThreadCount threadCount = config.performance.threadCount;
    EqRateExport export = null;
    if (threadCount != ThreadCount.ONE) {
      ExecutorService poolExecutor = newFixedThreadPool(threadCount.value());
      ListeningExecutorService executor = MoreExecutors.listeningDecorator(poolExecutor);
      log.info("Threads: " + ((ThreadPoolExecutor) poolExecutor).getCorePoolSize());
      log.info(PROGRAM + ": calculating ...");
      export = concurrentCalc(model, config, sites, log, executor);
      executor.shutdown();
    } else {
      log.info("Threads: Running on calling thread");
      log.info(PROGRAM + ": calculating ...");
      export = EqRateExport.create(config, sites, log);
      for (Site site : sites) {
        EqRate rate = calc(model, config, site);
        export.add(rate);
      }
    }
    export.expire();

    log.info(String.format(
        PROGRAM + ": %s sites completed in %s",
        export.resultsProcessed(), export.elapsedTime()));

    return export.outputDir();
  }

  private static EqRateExport concurrentCalc(
      HazardModel model,
      CalcConfig config,
      Sites sites,
      Logger log,
      ListeningExecutorService executor)
      throws InterruptedException, ExecutionException, IOException {

    EqRateExport export = EqRateExport.create(config, sites, log);

    int batchSize = config.output.flushLimit;
    int submitted = 0;
    List<ListenableFuture<EqRate>> rateFutures = new ArrayList<>(batchSize);

    /*
     * Although the approach below may not fully leverage all processors if
     * there are one or more longer-running calcs in the batch, processing
     * batches of locations to a List preserves submission order; as opposed to
     * using FutureCallbacks, which will reorder sites on export.
     */
    for (Site site : sites) {
      Callable<EqRate> task = EqRate.callable(model, config, site);
      rateFutures.add(executor.submit(task));
      submitted++;

      if (submitted == batchSize) {
        List<EqRate> rateList = Futures.allAsList(rateFutures).get();
        export.addAll(rateList);
        submitted = 0;
        rateFutures.clear();
      }
    }
    List<EqRate> lastBatch = Futures.allAsList(rateFutures).get();
    export.addAll(lastBatch);

    return export;
  }

  /**
   * Compute earthquake rates at a {@code site} for a {@code model} and
   * {@code config}.
   *
   * <p><b>Note:</b> any model initialization settings in {@code config} will be
   * ignored as the supplied model will already have been initialized.
   *
   * @param model to use
   * @param config calculation configuration
   * @param site of interest
   */
  public static EqRate calc(
      HazardModel model,
      CalcConfig config,
      Site site) {

    return EqRate.create(model, config, site);
  }

  private static final String PROGRAM = RateCalc.class.getSimpleName();
  private static final String USAGE_COMMAND =
      "java -cp nshmp-haz.jar gov.usgs.earthquake.nshmp.RateCalc model sites [config]";
  private static final String USAGE_URL1 = "https://github.com/usgs/nshmp-haz/wiki";
  private static final String USAGE_URL2 = "https://github.com/usgs/nshmp-haz/tree/master/etc";
  private static final String SITE_STRING = "name,lon,lat";

  private static final String USAGE = new StringBuilder()
      .append(NEWLINE)
      .append(PROGRAM).append(" [").append(HazardCalc.VERSION).append("]").append(NEWLINE)
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
