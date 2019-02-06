package gov.usgs.earthquake.nshmp.eq.model;

import static org.junit.Assert.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.Test;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import gov.usgs.earthquake.nshmp.HazardCalc;
import gov.usgs.earthquake.nshmp.calc.Hazard;
import gov.usgs.earthquake.nshmp.calc.HazardCalcs;
import gov.usgs.earthquake.nshmp.calc.Site;
import gov.usgs.earthquake.nshmp.internal.NshmpSite;
import gov.usgs.earthquake.nshmp.util.NamedLocation;

/**
 * Class for scaled down tests that exercise the primary hazard calculation
 * packages and pipeline without incurring the overhead of loading national
 * scale models.
 * 
 * @author Peter Powers
 */
@SuppressWarnings("javadoc")
public class NshmTestsSmall {

  /*
   * These tests use project relative file paths to read/write directly to/from
   * the source tree.
   */
  private static Gson GSON = new GsonBuilder()
      .setPrettyPrinting()
      .create();

  private static final Path DATA_PATH = Paths.get("test/gov/usgs/earthquake/nshmp/eq/model/data");

  @Test
  public void testFault() throws Exception {
    compareCurves("fault-wus", NshmpSite.SALT_LAKE_CITY_UT);
  }

  private static void compareCurves(String id, NamedLocation loc) throws Exception {
    String expected = readExpected(id, loc);
    String actual = generateActual(id, loc);
    assertEquals(expected, actual);
  }

  private static final String MODEL_SUFFIX = "-model";
  private static final String RESULT_SUFFIX = "-result.txt";

  private static String generateActual(String id, NamedLocation loc) throws Exception {
    Path modelPath = DATA_PATH.resolve(id + MODEL_SUFFIX);
    System.out.println(modelPath.toAbsolutePath());
    HazardModel model = Loader.load(modelPath);
    ExecutorService exec = Executors.newSingleThreadExecutor();
    Site site = Site.builder()
        .location(loc)
        .build();
    Hazard hazard = HazardCalcs.hazard(
        model,
        model.config(),
        site,
        exec);
    exec.shutdown();
    return GSON.toJson(hazard.curves());
  }

  private static String readExpected(String id, NamedLocation loc) throws Exception {
    Path resultPath = DATA_PATH.resolve(id + RESULT_SUFFIX);
    return new String(Files.readAllBytes(resultPath));
  }

  private static void writeExpected(String id, NamedLocation loc) throws Exception {
    Path resultPath = DATA_PATH.resolve(id + RESULT_SUFFIX);
    String result = generateActual(id, loc);
    Files.write(resultPath, result.getBytes());
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
