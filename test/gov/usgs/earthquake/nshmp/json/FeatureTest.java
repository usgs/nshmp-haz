package gov.usgs.earthquake.nshmp.json;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import gov.usgs.earthquake.nshmp.geo.LocationList;

public class FeatureTest {
  
  private static Properties properties = Properties.builder()
      .title("Title")
      .id("id")
      .build();
  
  @Rule
  public ExpectedException exception = ExpectedException.none();
 
  /**
   * Test {@link Feature#createPoint(Location, Properties)} to throw
   *    a {@code NullPointerException} when a {@code null}
   *    {@code Location} is supplied.
   */
  @Test
  public void createPoint_nullLocation() {
    exception.expect(NullPointerException.class);
    
    Feature.createPoint(null, properties);
  }
  
  /**
   * Test {@link Feature#createPoint(double, double, Properties)} to throw
   *    an {@code IllegalArgumentException} when a out
   *    of range latitude is supplied.
   */
  @Test
  public void createPoint_badLatitude() {
    exception.expect(IllegalArgumentException.class);
    
    Feature.createPoint(500.0, -120, properties);
  }
  
  /**
   * Test {@link Feature#createPoint(double, double, Properties)} to throw
   *    an {@code IllegalArgumentException} when a out
   *    of range longitude is supplied.
   */
  @Test 
  public void createPoint_badLongitude() {
    exception.expect(IllegalArgumentException.class);
    
    Feature.createPoint(40, 500.0, properties);
  }
  
  /**
   * Test {@link Feature#createPolygon(LocationList, Properties)} to 
   *    throw a {@code NullPointerException} when 
   *    supplied a {@code null} {@code LocationList}.
   */
  @Test 
  public void createPolygon_nullLocationList() {
    exception.expect(NullPointerException.class);
    
    Feature.createPolygon(null, properties);
  }
  
  /**
   * Test {@link Feature#createPolygon(LocationList, Properties)} to
   *    throw a {@code IllegalArgumentException} when 
   *    a {@code LocationList} is supplied with less than 4 positions.
   */
  @Test 
  public void createPolygon_notEnoughPositions() {
    exception.expect(IllegalArgumentException.class);
    
    LocationList locs = LocationList.builder()
        .add(40, -120)
        .add(38, -120)
        .add(40, -120)
        .build();
    
    Feature.createPolygon(locs, properties);
  }
  
  /**
   * Test {@link Feature#Feature(Geometry, Properties)} to throw
   *    a {@code NullPointerException} when a {@code null}
   *    {@link Geometry} is supplied.
   */
  @Test
  public void feature_nullGeometry() {
    exception.expect(NullPointerException.class);
    
    new Feature(null, properties);
  }
  
  /**
   * Test {@link Feature#Feature(Geometry, Properties)} to throw
   *    a {@code NullPointerException} when a {@code null}
   *    {@link Properties} is supplied.
   */
  @Test
  public void feature_nullProperties() {
    exception.expect(NullPointerException.class);
    
    Point geometry = new Point(40, -120);
    new Feature(geometry, null);
  }

}
