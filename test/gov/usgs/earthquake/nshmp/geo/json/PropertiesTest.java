package gov.usgs.earthquake.nshmp.json;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class PropertiesTest {

  @Rule
  public ExpectedException exception = ExpectedException.none();
  
  /**
   * Test {@link Polygon#getProperty()} to throw a 
   *    {@code NullPointerException} when a property is 
   *    not in the {@code Map<String, Object>}.
   */
  @Test
  public void getProperty_noProperty() {
    exception.expect(NullPointerException.class);
    
    Properties properties = Properties.builder()
        .title("My Title")
        .id("my_id")
        .build();
    
    properties.getProperty("badKey");
  }
  
  /**
   * Test certain objects are equal.
   */
  @Test
  public void equalsTest() {
    Properties properties = Properties.builder()
        .title("My Title")
        .id("myId")
        .put("linewidth", 2.5)
        .put("stroke-width", 2)
        .put("fill", "none")
        .put("testObject", new TestObject("name", 1, 1.5))
        .build();
    
    assertEquals(properties, properties);
    assertNotEquals(properties, null);
    assertNotEquals(properties, "test");
    
    String title = properties.getStringProperty("title");
    String id = properties.getStringProperty("id");
    String fill = properties.getStringProperty("fill");
    double linewidth = properties.getDoubleProperty("linewidth");
    int strokeWidth = properties.getIntProperty("stroke-width");
    TestObject testObject = properties.getProperty("testObject", TestObject.class);
    
    assertEquals("My Title", title);
    assertEquals("myId", id);
    assertEquals(2.5, linewidth, 0);
    assertEquals(2, strokeWidth, 0);
    assertEquals("none", fill);
    assertEquals("name", testObject.name);
    assertEquals(1, testObject.id, 0);
    assertEquals(1.5, testObject.weight, 1.5);
    assertEquals(properties.hasProperty("title"), true);
    assertEquals(properties.hasProperty("badKey"), false);
  }
 
  /**
   * Test class for {@link Properties#getProperty(String, Type)}.
   */
  static class TestObject {
    String name;
    int id;
    double weight;
    
    TestObject(String name, int id, double weight) {
      this.name = name;
      this.id = id;
      this.weight = weight;
    }
  }

}
