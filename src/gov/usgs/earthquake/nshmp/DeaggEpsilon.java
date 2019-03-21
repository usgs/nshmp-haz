package gov.usgs.earthquake.nshmp;

import static com.google.common.base.Preconditions.checkArgument;
import static gov.usgs.earthquake.nshmp.internal.TextUtils.NEWLINE;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
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
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

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
 * Application will process a list of sites for which the risk-targetd response
 * spectra is supplied, deaggregating the hazard at each spectral period at the
 * supplied ground motion. The set of IMTs processed is dictated by the set
 * defined in the sites file.
 *
 * @author Peter Powers
 */
public class DeaggEpsilon {

  private static final Gson GSON = new GsonBuilder()
      .serializeSpecialFloatingPointValues()
      .serializeNulls()
      .create();

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
      Path wusPath = modelPath.resolve("Western US");
      Path ceusPath = modelPath.resolve("Central & Eastern US");

      HazardModel wusModel = HazardModel.load(wusPath);
      HazardModel ceusModel = HazardModel.load(ceusPath);

      log.info("");
      Path siteFile = Paths.get(args[1]);
      log.info("Site and spectra file: " + siteFile.toAbsolutePath().normalize());
      checkArgument(siteFile.toString().endsWith(".csv"), "Only *.csv site files supported");

      int colsToSkip = headerCount(siteFile);
      List<Imt> imts = readImtList(siteFile, colsToSkip);

      CalcConfig wusConfig = wusModel.config();
      CalcConfig ceusConfig = ceusModel.config();
      if (argCount == 3) {
        Path userConfigPath = Paths.get(args[2]);
        wusConfig = CalcConfig.Builder.copyOf(wusModel.config())
            .extend(CalcConfig.Builder.fromFile(userConfigPath))
            .imts(EnumSet.copyOf(imts))
            .build();
        ceusConfig = CalcConfig.Builder.copyOf(ceusModel.config())
            .extend(CalcConfig.Builder.fromFile(userConfigPath))
            .imts(EnumSet.copyOf(imts))
            .build();
      }
      log.info(wusConfig.toString());

      List<Site> sites = ImmutableList.copyOf(Sites.fromCsv(siteFile, wusConfig, true));
      log.info("Sites: " + sites.size());

      log.info("Site data columns: " + colsToSkip);
      List<Map<Imt, Double>> imtImlMaps = readSpectra(siteFile, imts, colsToSkip);
      log.info("Spectra: " + imtImlMaps.size());

      checkArgument(sites.size() == imtImlMaps.size(), "Sites and spectra lists different sizes");

      Path out = calc(wusModel, wusConfig, ceusModel, ceusConfig, sites, imtImlMaps, log);

      log.info(PROGRAM + ": finished");

      /* Transfer log and write config, windows requires fh.close() */
      fh.close();
      Files.move(tmpLog, out.resolve(PROGRAM + ".log"));
      wusConfig.write(out);

      return Optional.empty();

    } catch (Exception e) {
      return HazardCalc.handleError(e, log, tmpLog, args, PROGRAM, USAGE);
    }
  }

  /* returns the number of site data columns are present. */
  private static int headerCount(Path path) throws IOException {
    String header = Files.lines(path).findFirst().get();
    Set<String> columns = ImmutableSet.copyOf(Splitter.on(',').trimResults().split(header));
    return Sets.intersection(columns, Site.KEYS).size();
  }

  private static List<Imt> readImtList(Path path, int colsToSkip) throws IOException {
    String header = Files.lines(path).findFirst().get();
    return Splitter.on(',')
        .trimResults()
        .splitToList(header)
        .stream()
        .skip(colsToSkip)
        .map(Imt::valueOf)
        .collect(ImmutableList.toImmutableList());
  }

  private static List<Map<Imt, Double>> readSpectra(Path path, List<Imt> imts, int colsToSkip)
      throws IOException {
    return Files.lines(path)
        .skip(1)
        .map(s -> readSpectra(imts, s, colsToSkip))
        .collect(ImmutableList.toImmutableList());
  }

  private static Map<Imt, Double> readSpectra(List<Imt> imts, String line, int colsToSkip) {

    double[] imls = Splitter.on(',')
        .trimResults()
        .splitToList(line)
        .stream()
        .skip(colsToSkip)
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
      HazardModel wusModel,
      CalcConfig wusConfig,
      HazardModel ceusModel,
      CalcConfig ceusConfig,
      List<Site> sites,
      List<Map<Imt, Double>> rtrSpectra,
      Logger log) throws IOException {

    ExecutorService exec = null;
    ThreadCount threadCount = wusConfig.performance.threadCount;
    if (threadCount == ThreadCount.ONE) {
      exec = MoreExecutors.newDirectExecutorService();
      log.info("Threads: Running on calling thread");
    } else {
      exec = Executors.newFixedThreadPool(threadCount.value());
      log.info("Threads: " + ((ThreadPoolExecutor) exec).getCorePoolSize());
    }

    log.info(PROGRAM + ": calculating ...");
    Path outDir = createOutputDir(wusConfig.output.directory);
    Path siteDir = outDir.resolve("vs30-" + (int) sites.get(0).vs30);
    Files.createDirectory(siteDir);

    for (int i = 0; i < sites.size(); i++) {

      Site site = sites.get(i);
      Map<Imt, Double> spectrum = rtrSpectra.get(i);

      Hazard wusHazard = null;
      if (site.location.lon() <= -100.0) {
        wusHazard = HazardCalcs.hazard(wusModel, wusConfig, site, exec);
      }
      Hazard ceusHazard = null;
      if (site.location.lon() > -115.0) {
        ceusHazard = HazardCalcs.hazard(ceusModel, ceusConfig, site, exec);
      }
      Hazard cousHazard = (wusHazard == null)
          ? ceusHazard
          : (ceusHazard == null)
              ? wusHazard
              : Hazard.merge(wusHazard, ceusHazard);

      Deaggregation deagg = Deaggregation.atImls(cousHazard, spectrum, exec);

      List<Response> responses = new ArrayList<>(spectrum.size());
      for (Imt imt : wusConfig.hazard.imts) {
        ResponseData imtMetadata = new ResponseData(
            ImmutableList.of(),
            site,
            imt,
            spectrum.get(imt));
        Response response = new Response(imtMetadata, deagg.toJsonCompact(imt));
        responses.add(response);
      }
      Result result = new Result(responses);

      String filename = String.format(
          "edeagg_%.2f_%.2f.json",
          site.location.lon(),
          site.location.lat());

      Path resultPath = siteDir.resolve(filename);
      Writer writer = Files.newBufferedWriter(resultPath);
      GSON.toJson(result, writer);
      writer.close();
      log.info(String.format("     %s of %s sites",i, sites.size()));
    }

    exec.shutdown();
    return siteDir;
  }

  private static class Result {

    final List<Response> response;

    Result(List<Response> response) {
      this.response = response;
    }
  }

  private static final class ResponseData {

    final List<String> models;
    final double longitude;
    final double latitude;
    final String imt;
    final double iml;
    final double vs30;

    ResponseData(List<String> models, Site site, Imt imt, double iml) {
      this.models = models;
      this.longitude = site.location.lon();
      this.latitude = site.location.lat();
      this.imt = imt.toString();
      this.iml = iml;
      this.vs30 = site.vs30;
    }
  }

  private static final class Response {

    final ResponseData metadata;
    final Object data;

    Response(ResponseData metadata, Object data) {
      this.metadata = metadata;
      this.data = data;
    }
  }

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

  private static final String PROGRAM = DeaggEpsilon.class.getSimpleName();
  private static final String USAGE_COMMAND =
      "java -cp nshmp-haz.jar gov.usgs.earthquake.nshmp.DeaggEpsilon model sites-spectra [config]";

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
      .append("       (spectral periods must be ascending)")
      .append(NEWLINE)
      .append("  'config' (optional) supplies a calculation configuration")
      .append(NEWLINE)
      .toString();

}
