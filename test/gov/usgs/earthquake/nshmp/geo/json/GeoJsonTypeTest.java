package gov.usgs.earthquake.nshmp.geo.json;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class GeoJsonTypeTest {
  
  @Rule
  public ExpectedException exception = ExpectedException.none();
 
  /**
   * Test {@link GeoJsonType#getEnum(String)} to throw a
   *    {@code IllegalStateException} when a {@code String}
   *    does not match a {@code GeoJsonType}.
   */
  @Test
  public void getEnum_badString() {
    exception.expect(IllegalStateException.class);
    
    GeoJsonType.getEnum("badEnum");
  }
  
  /**
   * Test {@link GeoJsonType#getEnum(String)} to throw a
   *    {@code IllegalStateException} when a {@code null} {@code String}
   *    is supplied. 
   */
  @Test
  public void getEnum_nullString() {
    exception.expect(IllegalStateException.class);
    
    GeoJsonType.getEnum(null);
  }
  
  /**
   * Test equals.
   */
  @Test
  public void equalsTest() {
    GeoJsonType geoJsonType = GeoJsonType
        .getEnum(GeoJsonType.FEATURE.toUpperCamelCase());
    
    assertEquals(GeoJsonType.FEATURE, geoJsonType);
    assertNotEquals(GeoJsonType.FEATURE, null);
    assertNotEquals(GeoJsonType.FEATURE, "test");
  }

}
