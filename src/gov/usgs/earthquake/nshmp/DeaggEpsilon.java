package gov.usgs.earthquake.nshmp;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static gov.usgs.earthquake.nshmp.internal.TextUtils.NEWLINE;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.logging.FileHandler;
import java.util.logging.Logger;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.MoreExecutors;

import gov.usgs.earthquake.nshmp.calc.CalcConfig;
import gov.usgs.earthquake.nshmp.calc.Deaggregation;
import gov.usgs.earthquake.nshmp.calc.Hazard;
import gov.usgs.earthquake.nshmp.calc.HazardCalcs;
import gov.usgs.earthquake.nshmp.calc.Site;
import gov.usgs.earthquake.nshmp.calc.Sites;
import gov.usgs.earthquake.nshmp.calc.ThreadCount;
import gov.usgs.earthquake.nshmp.eq.model.HazardModel;
import gov.usgs.earthquake.nshmp.gmm.Imt;
import gov.usgs.earthquake.nshmp.internal.Logging;

/**
 * Custom application to support 2018 integration into building codes.
 * Application will process a list of sites for which the risk-targetd respoinse
 * spectra is supplied, deaggregating the hazard at each spectral period at the
 * supplied ground motion.
 *
 * @author Peter Powers
 */
public class DeaggEpsilon {

  /**
   * Entry point for the application.
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
    Logger log = Logger.getLogger(DeaggCalc.class.getName());
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
        Path userConfigPath = Paths.get(args[3]);
        config = CalcConfig.Builder.copyOf(model.config())
            .extend(CalcConfig.Builder.fromFile(userConfigPath))
            .build();
      }
      log.info(config.toString());
      
      log.info("");
      Path siteFile = Paths.get(args[1]);
      checkArgument(siteFile.endsWith(".csv"), "Only *.csv site files supported");
      log.info("Site and spectra file: " + siteFile.toAbsolutePath().normalize());
      
      List<Imt> imts = readImtList(siteFile);
      checkState(
          imts.containsAll(config.hazard.imts),
          "Config IMTs missing from sites file: " + Sets.intersection(
              EnumSet.copyOf(imts), 
              config.hazard.imts));   
      
      List<Site> sites = ImmutableList.copyOf(Sites.fromCsv(siteFile, config, true));
      log.info("Sites: " + sites.size());
      
      List<Map<Imt, Double>> imtImlMaps = readSpectra(siteFile, imts);

      Path out = calc(model, config, sites, imtImlMaps, log);
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

  private static List<Imt> readImtList(Path path) throws IOException {
    String header = Files.lines(path).findFirst().get();
    return Splitter.on(',')
        .trimResults()
        .splitToList(header)
        .stream()
        .skip(2)
        .map(Imt::valueOf)
        .collect(ImmutableList.toImmutableList());
  }
  
  private static List<Map<Imt, Double>> readSpectra(Path path, List<Imt> imts) throws IOException {
    return Files.lines(path)
        .skip(1)
        .map(s -> readSpectra(imts, s))
        .collect(ImmutableList.toImmutableList());
  }

  
  private static Map<Imt, Double> readSpectra(List<Imt> imts, String line) {

    double[] imls = Splitter.on(',')
        .trimResults()
        .splitToList(line)
        .stream()
        .skip(2)
        .mapToDouble(Double::valueOf)
        .toArray();

    EnumMap<Imt, Double> imtImlMap = new EnumMap<>(Imt.class);
    for (int i = 0; i < imts.size(); i++) {
      imtImlMap.put(imts.get(i), imls[i]);
    }
    return imtImlMap;
  }

  /*
   * Compute hazard curves using the supplied model, config, and sites. Method
   * returns the path to the directory where results were written.
   * 
   * TODO consider refactoring to supply an Optional<Double> return period to
   * HazardCalc.calc() that will trigger deaggregations if the value is present.
   */
  private static Path calc(
      HazardModel model,
      CalcConfig config,
      List<Site> sites,
      List<Map<Imt, Double>> rtrSpectra,
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

    // HazardExport handler = HazardExport.create(config, sites, log);

    for (int i=0; i<sites.size(); i++) {
      
      Hazard hazard = HazardCalcs.hazard(model, config, sites.get(i), exec);
      Deaggregation deagg = Deaggregation.atImls(hazard, rtrSpectra.get(i), exec);
      
      //handler.add(hazard, Optional.of(deagg));
      log.fine(hazard.toString());
    }
    //handler.expire();

//    log.info(String.format(
//        PROGRAM + ": %s sites completed in %s",
//        handler.resultsProcessed(), handler.elapsedTime()));

    exec.shutdown();
    return null; //handler.outputDir();
  }

  private static final String PROGRAM = DeaggEpsilon.class.getSimpleName();
  private static final String USAGE_COMMAND =
      "java -cp nshmp-haz.jar gov.usgs.earthquake.nshmp.DeaggEPsilon model sites-spectra [config]";

  private static final String USAGE = new StringBuilder()
      .append(NEWLINE)
      .append(PROGRAM).append(" [").append(HazardCalc.VERSION).append("]").append(NEWLINE)
      .append(NEWLINE)
      .append("Usage:").append(NEWLINE)
      .append("  ").append(USAGE_COMMAND).append(NEWLINE)
      .append(NEWLINE)
      .append("Where:").append(NEWLINE)
      .append("  'model' is a model directory")
      .append(NEWLINE)
      .append("  'sites-spectra' is a *.csv file of locations and risk-targeted response spectra")
      .append(NEWLINE)
      .append("     - Header: lon,lat,PGA,SA0P01,SA0P02,...")
      .append(NEWLINE)
      .append("       (specrtral periods must be ascending)")
      .append(NEWLINE)
      .append("  'config' (optional) supplies a calculation configuration")
      .append(NEWLINE)
      .toString();

}
