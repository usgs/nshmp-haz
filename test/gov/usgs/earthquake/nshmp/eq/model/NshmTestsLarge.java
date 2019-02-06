package gov.usgs.earthquake.nshmp.eq.model;

import static gov.usgs.earthquake.nshmp.internal.NshmpSite.BOSTON_MA;
import static gov.usgs.earthquake.nshmp.internal.NshmpSite.CHICAGO_IL;
import static gov.usgs.earthquake.nshmp.internal.NshmpSite.LOS_ANGELES_CA;
import static gov.usgs.earthquake.nshmp.internal.NshmpSite.NEW_MADRID_MO;
import static gov.usgs.earthquake.nshmp.internal.NshmpSite.NEW_ORLEANS_LA;
import static gov.usgs.earthquake.nshmp.internal.NshmpSite.NEW_YORK_NY;
import static gov.usgs.earthquake.nshmp.internal.NshmpSite.RENO_NV;
import static gov.usgs.earthquake.nshmp.internal.NshmpSite.SALT_LAKE_CITY_UT;
import static gov.usgs.earthquake.nshmp.internal.NshmpSite.SAN_FRANCISCO_CA;
import static gov.usgs.earthquake.nshmp.internal.NshmpSite.SEATTLE_WA;
import static org.junit.Assert.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import gov.usgs.earthquake.nshmp.HazardCalc;
import gov.usgs.earthquake.nshmp.calc.Hazard;
import gov.usgs.earthquake.nshmp.calc.HazardCalcs;
import gov.usgs.earthquake.nshmp.calc.Site;
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

  private static final Path MODEL_PATH = Paths.get("../");
  private static final String MODEL_ROOT = "nshm-";
  private static final Path DATA_PATH = Paths.get("test/gov/usgs/earthquake/nshmp/eq/model/data");

  @Test
  public void testCeus2008() throws Exception {
    testModel("ceus", 2008, CEUS_SITES);
  }

  @Test
  public void testCeus2014() throws Exception {
    testModel("ceus", 2014, CEUS_SITES);
  }

  @Test
  public void testWus2008() throws Exception {
    testModel("wus", 2008, WUS_SITES);
  }

  @Test
  public void testWus2014() throws Exception {
    testModel("wus", 2014, WUS_SITES);
  }

  private static void testModel(
      String region,
      int year,
      List<NamedLocation> locations) throws Exception {

    HazardModel model = loadModel(region, year);
    for (NamedLocation location : locations) {
      compareCurves(region, year, model, location);
    }
  }

  private static void compareCurves(
      String region,
      int year,
      HazardModel model,
      NamedLocation location) throws Exception {

    String actual = generateActual(model, location);
    String expected = readExpected(region, year, location);
    assertEquals(expected, actual);
  }

  private static String generateActual(
      HazardModel model,
      NamedLocation location) {

    Hazard hazard = HazardCalcs.hazard(
        model,
        model.config(),
        Site.builder().location(location).build(),
        EXEC);

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

  private static String resultFilename(
      String region,
      int year,
      NamedLocation loc) {

    return "e2e-" + region + "-" + year + "-" + loc.id() + ".json";
  }

  private static String readExpected(
      String region,
      int year,
      NamedLocation loc) throws Exception {

    String filename = resultFilename(region, year, loc);
    Path resultPath = DATA_PATH.resolve(filename);
    return new String(Files.readAllBytes(resultPath));
  }

  private static void writeExpected(
      String region,
      int year,
      NamedLocation loc,
      String json) throws Exception {

    String filename = resultFilename(region, year, loc);
    Path resultPath = DATA_PATH.resolve(filename);
    Files.write(resultPath, json.getBytes());
  }

  private static void writeExpecteds(
      String region,
      int year,
      List<NamedLocation> locations) throws Exception {

    HazardModel model = loadModel(region, year);
    for (NamedLocation location : locations) {
      String json = generateActual(model, location);
      writeExpected(region, year, location, json);
    }
  }

  public static void main(String[] args) throws Exception {
    
    /* Initialize and shut down executor to generate results. */
    setUpBeforeClass();
    
    writeExpecteds("ceus", 2008, CEUS_SITES);
    writeExpecteds("ceus", 2014, CEUS_SITES);
    writeExpecteds("wus", 2008, WUS_SITES);
    writeExpecteds("wus", 2014, WUS_SITES);
    
    tearDownAfterClass();
  }

}
