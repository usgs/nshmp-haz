package gov.usgs.earthquake.nshmp.geo.json;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import gov.usgs.earthquake.nshmp.geo.LocationList;
import gov.usgs.earthquake.nshmp.geo.Region;

public class MultiPolygonTest {
  static LocationList borderA = LocationList.builder()
      .add(0.0, 100.0)
      .add(0.0, 101.0)
      .add(1.0, 101.0)
      .add(1.0, 100.0)
      .add(0.0, 100.0)
      .build();
  
  static LocationList interiorA = LocationList.builder()
      .add(0.8, 100.8)
      .add(0.2, 100.8)
      .add(0.2, 100.2)
      .add(0.8, 100.2)
      .add(0.8, 100.8)
      .build();

  static LocationList borderB = LocationList.builder()
      .add(2.0, 102.0)
      .add(2.0, 103.0)
      .add(3.0, 103.0)
      .add(3.0, 102.0)
      .add(2.0, 102.0)
      .build();
  
  @Rule
  public ExpectedException exception = ExpectedException.none();
 
  /**
   * Test {@link MultiPolygon#builder()} to throw an
   *    {@code IllegalStateException} when there is
   *    an empty {@code List<Polygon>}.
   */
  @Test
  public void builder_empty() {
    exception.expect(IllegalStateException.class);
    
    MultiPolygon.builder().build();
  }
  
  /**
   * Test {@link MultiPolygon#builder()} to throw an
   *    {@code NullPointerException} when the border is 
   *    {@code null}.
   */
  @Test 
  public void builder_nullBorder() {
    exception.expect(NullPointerException.class);
    
    MultiPolygon.builder().addPolygon(null, interiorA).build();
  }
  
  /**
   * Test {@link MultiPolygon#builder()} to throw an
   *    {@code NullPointerException} when the interior is 
   *    {@code null}.
   */
  @Test 
  public void builder_nullInterior() {
    exception.expect(NullPointerException.class);
  
    LocationList interior = null;
    MultiPolygon.builder().addPolygon(borderA, interior).build();
  }
 
  /**
   * Test {@link MultiPolygon#builder()} to throw an
   *    {@code NullPointerException} when a {@link Polygon} is 
   *    {@code null}.
   */
  @Test 
  public void builder_nullPolygon() {
    exception.expect(NullPointerException.class);
    
    MultiPolygon.builder().addPolygon(null).build();
  }
 
  /**
   * Test {@link MultiPolygon#asPoint()} to throw an 
   *    {@code UnsupportedOperationException}. 
   */
  @Test
  public void geometryAsPoint() {
    exception.expect(UnsupportedOperationException.class);
    
    MultiPolygon multiPolygon = MultiPolygon.builder()
        .addPolygon(borderA, interiorA)
        .addPolygon(borderB)
        .build();
    multiPolygon.asPoint();
  }
  
  /**
   * Test {@link MultiPolygon#asPolygon()} to throw an 
   *    {@code UnsupportedOperationException}. 
   */
  @Test
  public void geometryAsPolygon() {
    exception.expect(UnsupportedOperationException.class);
    
    MultiPolygon multiPolygon = MultiPolygon.builder()
        .addPolygon(borderA, interiorA)
        .addPolygon(borderB)
        .build();
    multiPolygon.asPolygon();
  }
  
  /**
   * Test certain objects are equal.
   */
  @Test 
  public void equalsTest() {
    Polygon polygonA = new Polygon(borderA, interiorA);
    Polygon polygonB = new Polygon(borderB);
   
    MultiPolygon multiPolygon = MultiPolygon.builder()
        .addPolygon(polygonA)
        .addPolygon(polygonB)
        .build();
    
    List<Polygon> polygons = multiPolygon.getPolygons();
    List<Region> regions = multiPolygon.toRegion("");
    
    assertEquals(multiPolygon, multiPolygon);
    assertEquals(multiPolygon, multiPolygon.asMultiPolygon());
  
    assertEquals(borderA, polygons.get(0).getBorder());
    assertEquals(interiorA, polygons.get(0).getInteriors().get(0));
    assertEquals(borderB, polygons.get(1).getBorder());
    assertEquals(polygonA, polygons.get(0));
    assertEquals(polygonB, polygons.get(1));
    assertEquals(polygonA.toRegion(""), regions.get(0));
    assertEquals(polygonB.toRegion(""), regions.get(1));
    assertEquals(GeoJsonType.MULTI_POLYGON, multiPolygon.getType());
  }
  
}
