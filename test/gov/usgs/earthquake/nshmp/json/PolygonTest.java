package gov.usgs.earthquake.nshmp.json;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import gov.usgs.earthquake.nshmp.geo.LocationList;

public class PolygonTest {
  
  @Rule
  public ExpectedException exception = ExpectedException.none();
  
  /**
   * Test {@link Polygon#Polygon(LocationList)} to throw a 
   *    {@code NullPointerException} when a {@code null}
   *    {@code LocationList} is supplied.
   */
  @Test
  public void polygon_nullLocationList() {
    exception.expect(NullPointerException.class);
    
    new Polygon(null);
  }

  /**
   * Test {@link Polygon#Polygon(LocationList)} to throw an
   *    {@code IllegalArgumentException} if the {@code LocationList}
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
   * Test that certain objects are equal
   */
  @Test
  public void equalsTest() {
    LocationList locs = LocationList.builder()
        .add(40, -120)
        .add(38, -120)
        .add(38, -118)
        .add(40, -118)
        .add(40, -120)
        .build();
    
    Polygon polygon = new Polygon(locs);
    
    assertEquals(polygon, polygon);
    assertNotEquals(polygon, null);
    assertNotEquals(polygon, "test");
    assertEquals(locs, polygon.toLocationList());
    assertEquals(polygon.type, polygon.getType());
    assertEquals(polygon.coordinates, polygon.getCoordinates());
  
    LocationList locsNotClosed = LocationList.builder()
        .add(40, -120)
        .add(38, -120)
        .add(38, -118)
        .add(40, -118)
        .build();
    
    Polygon polygonNotClosed = new Polygon(locsNotClosed);
    LocationList locsCheck = polygonNotClosed.toLocationList();
    
    assertNotEquals(locsCheck, locsNotClosed);
    assertEquals(locsCheck.first(), locsCheck.last());
  }
  
}
