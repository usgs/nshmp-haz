package gov.usgs.earthquake.nshmp.json;

import static org.junit.Assert.assertEquals;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.google.gson.JsonElement;

public class UtilTest {
  
  private static TestClass testClass = new TestClass();
  private static JsonElement jsonEl = JsonUtil.GSON.toJsonTree(testClass, TestClass.class);

  @Rule
  public ExpectedException exception = ExpectedException.none();
 
  /**
   * Test {@link JsonUtil#getJsonObject(JsonElement, String)} to throw
   *    an {@code IllegalArgumentException} when a bad path to
   *    an object is given.
   */
  @Test
  public void badPath() {
    exception.expect(IllegalArgumentException.class);
    
    String jsonPath = "bad.path.to.object";
    JsonUtil.getJsonObject(jsonEl, jsonPath);
  }
  
  /**
   * Test {@link JsonUtil#getJsonObject(JsonElement, String)} to throw
   *    an {@code IllegalArgumentException} when a bad end of path to
   *    an object is given.
   */
  @Test
  public void badPathAtEnd() {
    exception.expect(IllegalArgumentException.class);

    String jsonPath = "parameters.width.badValue";
    JsonUtil.getJsonObject(jsonEl, jsonPath);
  }
  
  /**
   * Equals test.
   */
  @Test
  public void equalsTest() {
    String jsonPath = "parameters.width.value";
    
    JsonElement widthEl = JsonUtil.getJsonObject(jsonEl, jsonPath);
    double width = widthEl.getAsDouble();
    
    JsonElement widthEl2 = JsonUtil.getJsonObject(jsonEl, "parameters", "width", "value");
    double width2 = widthEl2.getAsDouble();
    
    assertEquals(testClass.parameters.width.value, width, 0);
    assertEquals(width, width2, 0);
  }
 
  /**
   * Test class width structrue:
   * <pre>
   *  TestClass testClass = new TestClass();
   *  
   *  double width = testClass.parameters.width.value;
   * </pre>
   */
  private static class TestClass {
    Parameters parameters = new Parameters();
  }
  
  private static class Parameters {
    Width width = new Width();
  }
  
  private static class Width {
    double value = 20.0;
  } 
  
}
