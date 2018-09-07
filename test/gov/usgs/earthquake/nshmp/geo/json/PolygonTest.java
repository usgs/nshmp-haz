package gov.usgs.earthquake.nshmp.json;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import gov.usgs.earthquake.nshmp.geo.LocationList;
import gov.usgs.earthquake.nshmp.geo.Region;

public class PolygonTest {
  
  @Rule
  public ExpectedException exception = ExpectedException.none();
  
    static LocationList border = LocationList.builder()
        .add(0.0, 100.0)
        .add(0.0, 101.0)
        .add(1.0, 101.0)
        .add(1.0, 100.0)
        .add(0.0, 100.0)
        .build();
        
    static LocationList interior = LocationList.builder()
        .add(0.8, 100.8)
        .add(0.2, 100.8)
        .add(0.2, 100.2)
        .add(0.8, 100.2)
        .add(0.8, 100.8)
        .build();  
  
  /**
   * Test {@link Polygon#Polygon(LocationList, LocationList...)} to throw a 
   *    {@code NullPointerException} when a {@code null}
   *    {@code LocationList} is supplied.
   */
  @Test
  public void polygon_nullLocationList() {
    exception.expect(NullPointerException.class);
    
    new Polygon(null);
  }

  /**
   * Test {@link Polygon#Polygon(LocationList, LocationList...)} to 
   *    throw an {@code IllegalArgumentException} if the {@code LocationList}
   *    does not have enough positions to make a {@code Polygon}.
   */
  @Test
  public void polygon_notEnoughPositions() {
    exception.expect(IllegalArgumentException.class);
    
    LocationList locs = LocationList.builder()
        .add(40, -120)
        .add(38, -120)
        .add(40, -120)
        .build();
    
    new Polygon(locs);
  }
 
  /**
   * Test {@link Polygon#Polygon(LocationList, LocationList...)} to 
   *    throw an {@code IllegalArgumentException} if the 
   *    interior is not contained in the border.
   */
  @Test
  public void polygon_interiorNotContained() {
    exception.expect(IllegalArgumentException.class);
    
    // Swap border and interior
    new Polygon(interior, border);
  }

  /**
   * Test {@link Polygon#asMultiPolygon()} to throw 
   *    an {@code UnsupportedOperationException}.
   */
  @Test
  public void geometryAsMultiPolygon() {
    exception.expect(UnsupportedOperationException.class);
   
    Polygon polygon = new Polygon(border, interior);
    polygon.asMultiPolygon();
  }
  
  /**
   * Test {@link Polygon#asPoint()} to throw 
   *    an {@code UnsupportedOperationException}.
   */
  @Test
  public void geometryAsPoint() {
    exception.expect(UnsupportedOperationException.class);
   
    Polygon polygon = new Polygon(border, interior);
    polygon.asPoint();
  }
  
  /**
   * Test that certain objects are equal
   */
  @Test
  public void equalsTest() {
    Polygon polygon = new Polygon(border, interior);
    Region region = polygon.toRegion("");
    
    assertEquals(polygon, polygon);
    assertEquals(polygon, polygon.asPolygon());
    assertNotEquals(polygon, null);
    assertNotEquals(polygon, "test");
    
    assertEquals(border, polygon.getBorder());
    assertEquals(interior, polygon.getInteriors().get(0));
    assertEquals(GeoJsonType.POLYGON, polygon.getType());
    assertEquals(border.bounds(), region.bounds());
    assertEquals(interior.bounds(), region.interiors().get(0).bounds());
    
    LocationList borderNotClosed = LocationList.builder()
        .add(0.0, 100.0)
        .add(0.0, 101.0)
        .add(1.0, 101.0)
        .add(1.0, 100.0)
        .build();
    
    Polygon polygon2 = new Polygon(borderNotClosed);
    
    assertNotEquals(borderNotClosed.first(), borderNotClosed.last());
    assertEquals(polygon2.getBorder().first(), polygon2.getBorder().last());
  }
  
}
