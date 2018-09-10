package gov.usgs.earthquake.nshmp.geo.json;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import gov.usgs.earthquake.nshmp.geo.LocationList;

/**
 * JUnit testing for {@link FeatureCollection} class.
 * 
 * @author Brandon Clayton
 */
public class FeatureCollectionTest {
 
  private static Properties properties = Properties.builder()
      .title("Title")
      .id("id")
      .build();
 
  @Rule
  public ExpectedException exception = ExpectedException.none();
 
  /**
   * Test {@link FeatureCollection#builder()} to throw an
   *    {@code IllegalStateException} when the {@code List<Feature>} 
   *    is empty; when build is called before adding a {@link Feature}.
   */
  @Test 
  public void builder_emptyFeatureList() {
    exception.expect(IllegalStateException.class);
    
    FeatureCollection.builder().build();
  }

  /**
   * Test the {@link FeatureCollection#builder()} to throw a 
   *    {@code NullPointerException} when {@code null} is added as a 
   *    {@link Feature}.
   */
  @Test 
  public void builder_nullFeature() {
    exception.expect(NullPointerException.class);
    
    Feature feature = null;
    FeatureCollection.builder()
        .add(feature)
        .build();
  }
  
  /**
   * Test {@link FeatureCollection#read(java.io.InputStreamReader)}
   *    to throw a {@code NullPointerException} when the 
   *    {@code InputStreamReader} is {@code null}.
   * @throws IOException 
   */
  @Test
  public void read_nullPath() throws IOException {
    exception.expect(NullPointerException.class);
    
    Path path = null;
    FeatureCollection.read(path);
  }

  @Test 
  public void read_badPath() throws IOException {
    exception.expect(IllegalArgumentException.class);
    
    Path path = Paths.get("badPath");
    FeatureCollection.read(path);
  }

  /**
   * Test {@link FeatureCollection#read(java.io.InputStreamReader)}
   *    to throw a {@code NullPointerException} when the 
   *    {@code InputStreamReader} is {@code null}.
   * @throws IOException 
   */
  @Test
  public void read_nullUrl() throws IOException {
    exception.expect(IllegalArgumentException.class);
    
    URL url = null;
    FeatureCollection.read(url);
  }
 
  /**
   * Test {@link FeatureCollection#read(java.io.InputStreamReader)}
   *    to throw a {@code NullPointerException} when the 
   *    {@code InputStreamReader} is {@code null}.
   */
  @Test
  public void read_inputStreamNull() {
    exception.expect(NullPointerException.class);
    
    InputStreamReader reader = null;
    FeatureCollection.read(reader);
  }
 
  /**
   * Test {@link FeatureCollection#write(Path)} to throw a
   *    {@code NoSuchFileException} using a fake {@code Path}.  
   */
  @Test
  public final void write_badPath() throws IOException {
    exception.expect(NoSuchFileException.class);
    
    FeatureCollection fc = FeatureCollection.builder()
        .addPoint(40, -120, properties)
        .build();
    
    Path out = Paths.get("notRealPath").resolve("test.geojson");
    
    fc.write(out);
  }
  
  /**
   * Test {@link FeatureCollection#write(Path)} to throw a
   *    {@code NullPointerException} when the {@code Path} is {@code null}. 
   */
  @Test
  public final void write_nullPath() throws IOException {
    exception.expect(NullPointerException.class);
    
    FeatureCollection fc = FeatureCollection.builder()
        .addPoint(40, -120, properties)
        .build();
    
    Path out = null; 
    
    fc.write(out);
  }

  /**
   * Create a {@link FeatureCollection}, write that 
   *    {@code FeatureCollection}to a file, read the file 
   *    into a {@code FeatureCollection} and test that those
   *    are equal.
   * @throws IOException
   */
  @Test
  public void readWriteEquals() throws IOException {
    MultiPolygon multiPolygon = MultiPolygon.builder()
        .addPolygon(MultiPolygonTest.borderA, MultiPolygonTest.interiorA)
        .build();
    
    FeatureCollection fc = FeatureCollection.builder()
        .addMultiPolygon(multiPolygon, properties, Optional.of("MultiPolygon"), Optional.empty())
        .addPoint(40, -120, properties, Optional.of("Point"), Optional.empty())
        .addPolygon(
            PolygonTest.border, 
            properties,
            Optional.of("Polygon"), 
            Optional.empty(),
            PolygonTest.interior)
        .build();
    
    Path path = Paths.get("etc", "test.geojson");
    fc.write(path);
    
    FeatureCollection fcCheck = FeatureCollection.read(path);
    
    assertEquals(fc.toJsonString(), fcCheck.toJsonString());
    
    List<Feature> features = fc.getFeatures();
    List<Feature> featuresCheck = fcCheck.getFeatures();
    
    for (int jf = 0; jf < features.size(); jf++) {
      Feature feature = features.get(jf);
      Feature featureCheck = featuresCheck.get(jf);
      
      assertEquals(feature.getType(), featureCheck.getType());
      assertEquals(feature.getId(), featureCheck.getId());
      
      Geometry geom = feature.getGeometry();
      Geometry geomCheck = featureCheck.getGeometry();
      GeoJsonType type = geom.getType();
      
      assertEquals(geom.getType(), geomCheck.getType());
      
      switch (type) {
        case MULTI_POLYGON:
          List<Polygon> polygons = geom.asMultiPolygon().getPolygons();
          List<Polygon> polygonsCheck = geomCheck.asMultiPolygon().getPolygons();
         
          List<LocationList> mpInteriors = polygons.get(0).getInteriors();
          List<LocationList> mpInteriorsCheck = polygonsCheck.get(0).getInteriors();
          
          assertEquals(polygons.get(0).getBorder(), polygonsCheck.get(0).getBorder());
          assertEquals(mpInteriors.get(0), mpInteriorsCheck.get(0));
          assertEquals(polygons.get(0).toRegion(""), polygonsCheck.get(0).toRegion(""));
          break;
        case POLYGON:
          List<LocationList> pInteriors = geom.asPolygon().getInteriors();
          List<LocationList> pInteriorsCheck = geomCheck.asPolygon().getInteriors();
          
          assertEquals(geom.asPolygon().getBorder(), geomCheck.asPolygon().getBorder());
          assertEquals(pInteriors.get(0), pInteriorsCheck.get(0));
          assertEquals(geom.asPolygon().toRegion(""), geomCheck.asPolygon().toRegion(""));
          break;
        case POINT:
          assertEquals(geom.asPoint().getLocation(), geomCheck.asPoint().getLocation());
          break;
        default:
      }
    }
  }

}
