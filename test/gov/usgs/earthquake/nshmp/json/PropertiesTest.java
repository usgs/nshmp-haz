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
   * Test {@link Properties#builder()} to throw a 
   *    {@code IllegalStateException} when no title is set.
   */
  @Test
  public void builder_noTitle() {
    exception.expect(IllegalStateException.class);
    
    Properties.builder()
        .id("my_id")
        .put("linewidth", 2.5)
        .build();
  }
  
  /**
   * Test {@link Properties#builder()} to throw a 
   *    {@code IllegalStateException} when no id is set.
   */
  @Test
  public void builder_noId() {
    exception.expect(IllegalStateException.class);
    
    Properties.builder()
        .title("My Title")
        .put("linewidth", 2.5)
        .build();
  }
 
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
        .id("my_id")
        .put("linewidth", 2.5)
        .put("stroke-width", 2)
        .put("fill", "none")
        .build();
    
    assertEquals(properties, properties);
    assertNotEquals(properties, null);
    assertNotEquals(properties, "test");
    
    String title = properties.getStringProperty("title");
    String id = properties.getStringProperty("id");
    String fill = properties.getStringProperty("fill");
    double linewidth = properties.getDoubleProperty("linewidth");
    int strokeWidth = properties.getIntProperty("stroke-width");
    
    assertEquals("My Title", title);
    assertEquals("my_id", id);
    assertEquals(2.5, linewidth, 0);
    assertEquals(2, strokeWidth, 0);
    assertEquals("none", fill);
  }

}
