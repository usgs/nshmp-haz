package gov.usgs.earthquake.nshmp.eq.model;

import static org.junit.Assert.assertEquals;

import static gov.usgs.earthquake.nshmp.internal.NshmpSite.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import gov.usgs.earthquake.nshmp.HazardCalc;
import gov.usgs.earthquake.nshmp.calc.Hazard;
import gov.usgs.earthquake.nshmp.calc.Site;
import gov.usgs.earthquake.nshmp.internal.NshmpSite;
import gov.usgs.earthquake.nshmp.util.NamedLocation;

/**
 * Class for end-to-end tests of hazard calculations. These tests require
 * significant system resources to load source models, and source models are
 * required to be in adjacent repositories. These tests should be run
 * frequently, but not as part of continuous integration. Consider nightlies.
 * 
 * @see NshmTestsSmall
 * @author Peter Powers
 */
@SuppressWarnings("javadoc")
public class NshmTestsLarge {

  // static
  private static final List<Integer> COUS_YEARS = ImmutableList.of(2008, 2014); // TODO
                                                                                // add
                                                                                // 2018
  private static final List<String> COUS_REGION_IDS = ImmutableList.of("wus", "ceus");
  private static final Map<String, String> COUS_REGION_NAMES = ImmutableMap.of(
      COUS_REGION_IDS.get(0), "Western US",
      COUS_REGION_IDS.get(1), "Central & Eastern US");

  private static final List<NamedLocation> WUS_SITES = ImmutableList.of(
      LOS_ANGELES_CA,
      SAN_FRANCISCO_CA,
      SEATTLE_WA,
      SALT_LAKE_CITY_UT,
      RENO_NV);

  private static final List<NamedLocation> CEUS_SITES = ImmutableList.of(
      NEW_MADRID_MO,
      BOSTON_MA,
      NEW_YORK_NY,
      CHICAGO_IL,
      NEW_ORLEANS_LA);

  /*
   * These tests use project relative file paths to read/write directly to/from
   * the source tree.
   */
  private static final Gson GSON = new GsonBuilder()
      .setPrettyPrinting()
      .create();

  private static ExecutorService EXEC;

  @BeforeClass
  public static void setUpBeforeClass() throws Exception {
    int cores = Runtime.getRuntime().availableProcessors();
    EXEC = Executors.newFixedThreadPool(cores);
  }

  @AfterClass
  public static void tearDownAfterClass() throws Exception {
    EXEC.shutdown();
  }

  private static final Path MODEL_PATH = Paths.get("../../");
  private static final String MODEL_ROOT = "nshm-";
  private static final Path DATA_PATH = Paths.get("test/gov/usgs/earthquake/nshmp/eq/model/data");

  @Test
  public void testCeus2008() throws Exception {
//    ExecutorService exec = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    HazardModel model = loadModel("ceus", 2008);
    for (NamedLocation location : CEUS_SITES)
      
      
    }

  model=

  compareCurves("fault-wus", NshmpSite.SALT_LAKE_CITY_UT);
    
    exec.shutdown();
  }

  private static void runSites(HazardModel model, List<NamedLocation> sites) {
    for (NamedLocation location : CEUS_SITES) {
  }

  private static void compareCurves(
      HazardModel model,
      NamedLocation loc,
      ExecutorService exec) throws Exception {

    String actual = generateActual(model, loc, exec);

    String expected = readExpected(id, loc);
    // assertEquals(expected, actual);
  }

  // private static final String MODEL_SUFFIX = "-model";
  private static final String RESULT_SUFFIX = "-result.txt";

  private static String generateActual(
      String region,
      int year,
      List<NamedLocation> locations,
      Consumer<String> out) throws Exception {

    // Path modelPath = DATA_PATH.resolve(MODEL_PATH).resolve(MODEL_ROOT + ) +
    // MODEL_SUFFIX);
    // System.out.println(modelPath.toAbsolutePath());

    HazardModel model = loadModel(region, year);
    ExecutorService exec = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    Site.Builder site = Site.builder();

    for (NamedLocation location : locations) {
      Hazard hazard = HazardCalc.calc(
          model,
          model.config(),
          site.location(location).build(),
          Optional.of(exec));
      String json = GSON.toJson(hazard.curves());
      out.accept(json);
    }

    exec.shutdown();
    return null;
    // return GSON.toJson(hazard.curves());
  }

  private static String generateActual(
      HazardModel model,
      NamedLocation location,
      ExecutorService exec) {

    Hazard hazard = HazardCalc.calc(
        model,
        model.config(),
        Site.builder().location(location).build(),
        Optional.of(exec));
    return GSON.toJson(hazard.curves());
  }

  /* Load model handling cous = wus + ceus */
  private static HazardModel loadModel(String region, int year)
      throws Exception {

    Path modelPath;
    if (COUS_REGION_IDS.contains(region)) {
      modelPath = MODEL_PATH.resolve(MODEL_ROOT + "cous-" + year)
          .resolve(COUS_REGION_NAMES.get(region));
    } else {
      modelPath = MODEL_PATH.resolve(MODEL_ROOT + region + "-" + year);
    }
    return Loader.load(modelPath);
  }

  private static String readExpected(String region, int year, NamedLocation loc)
      throws Exception {
    String filename = resultFilename(region, year, loc);
    Path resultPath = DATA_PATH.resolve(filename);
    return new String(Files.readAllBytes(resultPath));
  }

  private static void writeExpected(String region, int year, NamedLocation loc, String json)
      throws Exception {
    String filename = resultFilename(region, year, loc);
    Path resultPath = DATA_PATH.resolve(filename);
    Files.write(resultPath, json.getBytes());
  }
  
  private static String resultFilename(String region, int year, NamedLocation loc) {
    return "e2e-" + region + "-" + year + "-" + loc.id() + RESULT_SUFFIX;
  }

  public static void main(String[] args) throws Exception {
    writeExpected("fault-wus", NshmpSite.SALT_LAKE_CITY_UT);
    /*
     * TODO currently have NaN problem with z serialization in config. It would
     * be nice if config could be serialized by default without having to use
     * it's own GSON instance (which handles NaN and urls)
     */

  }

}
