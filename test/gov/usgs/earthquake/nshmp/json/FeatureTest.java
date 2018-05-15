package gov.usgs.earthquake.nshmp.json;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.google.common.base.Optional;

import gov.usgs.earthquake.nshmp.geo.Location;
import gov.usgs.earthquake.nshmp.geo.LocationList;

public class FeatureTest {
  
  private static Properties properties = Properties.builder()
      .title("Title")
      .id("id")
      .build();
  
  @Rule
  public ExpectedException exception = ExpectedException.none();
 
  /**
   * Test {@link Feature#createPoint(Properties, Location, Optional)} to throw
   *    a {@code NullPointerException} when a {@code null}
   *    {@code Location} is supplied.
   */
  @Test
  public void createPoint_nullLocation() {
    exception.expect(NullPointerException.class);
    
    Feature.createPoint(properties, null, Optional.absent());
  }
  
  /**
   * Test {@link Feature#createPoint(Properties, double, double, Optional)}
   *     to throw an {@code IllegalArgumentException} when a out
   *    of range latitude is supplied.
   */
  @Test
  public void createPoint_badLatitude() {
    exception.expect(IllegalArgumentException.class);
    
    Feature.createPoint(properties, 500.0, -120, Optional.absent());
  }
  
  /**
   * Test {@link Feature#createPoint(Properties, double, double, Optional)}
   *    to throw an {@code IllegalArgumentException} when a out
   *    of range longitude is supplied.
   */
  @Test 
  public void createPoint_badLongitude() {
    exception.expect(IllegalArgumentException.class);
    
    Feature.createPoint(properties, 40, 500.0, Optional.absent());
  }
  
  /**
   * Test {@link Feature#createPolygon(
   *    Properties, Optional, LocationList, LocationList...)} to 
   *    throw a {@code NullPointerException} when 
   *    supplied a {@code null} {@code LocationList}.
   */
  @Test 
  public void createPolygon_nullLocationList() {
    exception.expect(NullPointerException.class);
    
    Feature.createPolygon(properties, Optional.absent(), null);
  }
  
  /**
   * Test {@link Feature#createPolygon(
   *    Properties, Optional, LocationList, LocationList...)} to
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
    
    Feature.createPolygon(properties, Optional.absent(), locs);
  }
  
  /**
   * Test {@link Feature#Feature(Properties, Geometry, Optional)}
   *    to throw a {@code NullPointerException} when a {@code null}
   *    {@link Geometry} is supplied.
   */
  @Test
  public void feature_nullGeometry() {
    exception.expect(NullPointerException.class);
    
    new Feature(properties, null, Optional.absent());
  }
  
  /**
   * Test {@link Feature#Feature(Properties, Geometry, Optional)}
   *    to throw a {@code NullPointerException} when a {@code null}
   *    {@link Properties} is supplied.
   */
  @Test
  public void feature_nullProperties() {
    exception.expect(NullPointerException.class);
    
    Point geometry = new Point(40, -120);
    new Feature(null, geometry, Optional.absent());
  }
 
  /**
   * Test equals with a {@link Point} {@link Geometry}.
   */
  @Test
  public void featurePointEquals() {
    Location loc = Location.create(40, -122);
    Point point = new Point(loc);
    
    String strId = "test";
    int intId = 5;
    
    Feature feature = new Feature(properties, point, Optional.of(strId));
    Feature feature2 = Feature.createPoint(properties, loc, Optional.of(intId));
    Feature feature3 = Feature.createPoint(properties, 40, -122, Optional.absent());
    
    assertEquals(loc, feature.getGeometry().asPoint().getLocation());
    assertEquals(loc, feature2.getGeometry().asPoint().getLocation());
    assertEquals(loc, feature3.getGeometry().asPoint().getLocation());
    
    assertEquals(strId, feature.getId());
    assertEquals(intId, feature2.getNumericId(), 0);
    assertEquals("", feature3.getId());
  }

  /**
   * Test equals with a {@link Polygon} {@link Geometry}.
   */
  @Test
  public void featurePolygonEquals() {
    Polygon polygon =  new Polygon(PolygonTest.border, PolygonTest.interior);
    
    Feature feature = new Feature(properties, polygon, Optional.absent());
    Feature feature2 = Feature.createPolygon(
        properties, 
        Optional.absent(), 
        PolygonTest.border, 
        PolygonTest.interior);
    
    assertEquals(PolygonTest.border, feature.getGeometry().asPolygon().getBorder());
    assertEquals(PolygonTest.border, feature2.getGeometry().asPolygon().getBorder());
    assertEquals(
        PolygonTest.interior, 
        feature.getGeometry().asPolygon().getInteriors().get(0));
    assertEquals(
        PolygonTest.interior, 
        feature2.getGeometry().asPolygon().getInteriors().get(0));
  }
  
  /**
   * Test equals with a {@link MultiPolygon} {@link Geometry}.
   */
  @Test 
  public void featureMultiPolygonEquals() {
    MultiPolygon multiPolygon = MultiPolygon.builder()
        .addPolygon(MultiPolygonTest.borderA, MultiPolygonTest.interiorA)
        .addPolygon(MultiPolygonTest.borderB)
        .build();
    
    Feature feature = new Feature(properties, multiPolygon, Optional.absent());
    Feature feature1 = Feature.createMultiPolygon(
        properties, multiPolygon, Optional.absent());
    Feature feature2 = Feature.createMultiPolygon(
        properties, multiPolygon.getPolygons(), Optional.absent());
   
    List<Polygon> polygons = feature.getGeometry().asMultiPolygon().getPolygons();
    List<Polygon> polygons2 = feature1.getGeometry().asMultiPolygon().getPolygons();
    List<Polygon> polygons3 = feature2.getGeometry().asMultiPolygon().getPolygons();
    
    assertEquals(MultiPolygonTest.borderA, polygons.get(0).getBorder());
    assertEquals(MultiPolygonTest.borderA, polygons2.get(0).getBorder());
    assertEquals(MultiPolygonTest.borderA, polygons3.get(0).getBorder());
    
    assertEquals(MultiPolygonTest.interiorA, polygons.get(0).getInteriors().get(0));
    assertEquals(MultiPolygonTest.interiorA, polygons2.get(0).getInteriors().get(0));
    assertEquals(MultiPolygonTest.interiorA, polygons3.get(0).getInteriors().get(0));
    
    assertEquals(MultiPolygonTest.borderB, polygons.get(1).getBorder());
    assertEquals(MultiPolygonTest.borderB, polygons2.get(1).getBorder());
    assertEquals(MultiPolygonTest.borderB, polygons3.get(1).getBorder());
  }
  
  /**
   * Test certain objects are equal
   */
  @Test
  public void equalsTest() {
    Feature feature = Feature.createPoint(properties, 40, -120, Optional.absent());
    
    Properties propCheck = feature.getProperties();
    
    assertEquals(
        properties.getStringProperty("title"), 
        propCheck.getStringProperty("title"));
    assertEquals(
        properties.getStringProperty("id"),
        propCheck.getStringProperty("id"));
    
    assertEquals(GeoJsonType.FEATURE, feature.getType());
  }
  
}
