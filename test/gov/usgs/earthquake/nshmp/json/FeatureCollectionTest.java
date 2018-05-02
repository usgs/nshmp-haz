package gov.usgs.earthquake.nshmp.json;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

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
   
    FeatureCollection.builder()
        .add(Feature.createPoint(40, -120, properties))
        .add(null)
        .build();
  }
  
  /**
   * Test {@link FeatureCollection#read(java.io.InputStreamReader)}
   *    to throw a {@code NullPointerException} when the 
   *    {@code InputStreamReader} is {@code null}.
   */
  @Test
  public void read_inputStreamNull() {
    exception.expect(NullPointerException.class);
    
    FeatureCollection.read(null);
  }
 
  /**
   * Test {@link FeatureCollection#write(Path)} to throw a
   *    {@code NoSuchFileException} using a fake {@code Path}.  
   */
  @Test
  public final void write_badPath() throws IOException {
    exception.expect(NoSuchFileException.class);
    
    FeatureCollection fc = FeatureCollection.builder()
        .createPoint(40, -120, properties)
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
        .createPoint(40, -120, properties)
        .build();
    
    Path out = null; 
    
    fc.write(out);
  }
 
}
