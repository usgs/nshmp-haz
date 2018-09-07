package gov.usgs.earthquake.nshmp.geo.json;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import gov.usgs.earthquake.nshmp.geo.Location;

public class PointTest {

  @Rule
  public ExpectedException exception = ExpectedException.none();
 
  /**
   * Test {@link Point#Point(Location)} throws a 
   *    {@code NullPointerException} when supplied a {@code null} 
   *    {@code Location}.
   */
  @Test
  public void point_nullLocation() {
    exception.expect(NullPointerException.class);
    
    new Point(null);
  }
  
  /**
   * Test {@link Point#Point(Location)} throws an
   *    {@code IllegalArgumentException} when a out of range
   *    latitude is supplied.
   */
  @Test 
  public void point_badLatitude() {
    exception.expect(IllegalArgumentException.class);
    
    new Point(500.0, -120);
  }
  
  /**
   * Test {@link Point#Point(Location)} throws an
   *    {@code IllegalArgumentException} when a out of range
   *    longitude is supplied.
   */
  @Test
  public void point_badLongitude() {
    exception.expect(IllegalArgumentException.class);
    
    new Point(40, 500.0);
  }
  
  /**
   * Test {@link Point#asMultiPolygon()} to throw 
   *    an {@code UnsupportedOperationException}.
   */
  @Test
  public void geometryAsMultiPolygon() {
    exception.expect(UnsupportedOperationException.class);
    
    Point point = new Point(40, -120);
    point.asMultiPolygon();
  }
  
  /**
   * Test {@link Point#asPolygon()} to throw 
   *    an {@code UnsupportedOperationException}.
   */
  @Test
  public void geometryAsPolygon() {
    exception.expect(UnsupportedOperationException.class);
    
    Point point = new Point(40, -120);
    point.asPolygon();
  }
  
  /**
   * Test certain objects are equal
   */
  @Test 
  public void equalsTest() {
    double lat = 40.58;
    double lon = -121.62;
    Location loc = Location.create(lat, lon);
    
    Point point = new Point(loc);
    Location locCheck = point.getLocation();
    double[] coords = point.getCoordinates();
   
    assertEquals(point, point);
    assertEquals(point, point.asPoint());
    assertNotEquals(point, null);
    assertNotEquals(point, "test");
    
    assertEquals(loc, locCheck);
    assertEquals(lat, coords[1], 0);
    assertEquals(lon, coords[0], 0);
    assertEquals(GeoJsonType.POINT, point.getType());
  }
  
}
