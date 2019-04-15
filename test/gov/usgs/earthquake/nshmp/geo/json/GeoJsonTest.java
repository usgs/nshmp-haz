package gov.usgs.earthquake.nshmp.geo.json;

import static gov.usgs.earthquake.nshmp.geo.json.Properties.Style.DESCRIPTION;
import static gov.usgs.earthquake.nshmp.geo.json.Properties.Style.FILL;
import static gov.usgs.earthquake.nshmp.geo.json.Properties.Style.FILL_OPACITY;
import static gov.usgs.earthquake.nshmp.geo.json.Properties.Style.MARKER_COLOR;
import static gov.usgs.earthquake.nshmp.geo.json.Properties.Style.MARKER_SIZE;
import static gov.usgs.earthquake.nshmp.geo.json.Properties.Style.MARKER_SYMBOL;
import static gov.usgs.earthquake.nshmp.geo.json.Properties.Style.STROKE;
import static gov.usgs.earthquake.nshmp.geo.json.Properties.Style.STROKE_OPACITY;
import static gov.usgs.earthquake.nshmp.geo.json.Properties.Style.STROKE_WIDTH;
import static gov.usgs.earthquake.nshmp.geo.json.Properties.Style.TITLE;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonIOException;
import com.google.gson.JsonParser;

import gov.usgs.earthquake.nshmp.geo.Location;
import gov.usgs.earthquake.nshmp.geo.LocationList;
import gov.usgs.earthquake.nshmp.geo.json.GeoJson.Builder;
import gov.usgs.earthquake.nshmp.geo.json.GeoJson.Type;

@SuppressWarnings("javadoc")
public class GeoJsonTest {

  private static final String FEATURE_COLLECTION_FILENAME = "feature-collection.geojson";
  private static final String FEATURE_FILENAME = "feature.geojson";
  private static final String PROPERTIES_FILENAME = "properties.geojson";
  private static final String TEST_FILENAME = "test.geojson";
  private static final String NOFILE_FILENAME = "nofile.geojson";

  private static final Location TEST_POINT = Location.create(34, -117);

  private static final LocationList TEST_LINE = LocationList.create(
      Location.create(34, -117),
      Location.create(35, -118),
      Location.create(37, -116),
      Location.create(38, -117));

  private static final double[] BBOX = new double[] { -118, 34, -116, 38 };

  private static final Builder FC_BUILDER = GeoJson.builder()
      .bbox(BBOX)
      .add(Feature.point(TEST_POINT)
          .id("featureId")
          .properties(ImmutableMap.of(
              "id", 1,
              "title", "Feature Title",
              "color", "#ff0080"))
          .build())
      .add(Feature.lineString(TEST_LINE)
          .build())
      .add(Feature.polygon(TEST_LINE)
          .id(3)
          .bbox(BBOX)
          .build());

  private static final Feature.Builder F_BUILDER_1 = Feature.point(TEST_POINT)
      .id("featureId")
      .properties(ImmutableMap.of(
          "id", 1,
          "title", "Feature Title",
          "color", "#ff0080"));

  private static final Feature.Builder F_BUILDER_2 = Feature.point(TEST_POINT)
      .id("featureId")
      .properties(ImmutableMap.of(
          "id", 1,
          "title", "Feature Title",
          "color", "#ff0080"));

  @Rule
  public TemporaryFolder testFolder = new TemporaryFolder();

  @SuppressWarnings("unused")
  @Test(expected = JsonIOException.class)
  public void testReadUrlException() throws MalformedURLException {
    String basUrlBase = Resources.getResource(GeoJsonTest.class, ".").toString();
    URL badUrl = new URL(basUrlBase + NOFILE_FILENAME);
    FeatureCollection fc = GeoJson.from(badUrl).toFeatureCollection();
  }

  @SuppressWarnings("unused")
  @Test(expected = JsonIOException.class)
  public void testReadPathException() throws URISyntaxException, MalformedURLException {
    String basUrlBase = Resources.getResource(GeoJsonTest.class, ".").toString();
    URL badUrl = new URL(basUrlBase + NOFILE_FILENAME);
    Path badPath = new File(badUrl.toURI()).toPath();
    FeatureCollection fc = GeoJson.from(badPath).toFeatureCollection();
  }

  @Test
  public void testRead() throws URISyntaxException, IOException {

    FeatureCollection fc;
    Feature f;

    /* URL */

    URL fcJsonUrl = Resources.getResource(
        GeoJsonTest.class,
        FEATURE_COLLECTION_FILENAME);
    fc = GeoJson.from(fcJsonUrl).toFeatureCollection();
    checkFeatureCollection(fc);

    URL fJsonUrl = Resources.getResource(
        GeoJsonTest.class,
        FEATURE_FILENAME);
    f = GeoJson.from(fJsonUrl).toFeature();
    assertEquals(TEST_POINT, f.asPoint());

    /* Path */

    Path fcJsonPath = new File(fcJsonUrl.toURI()).toPath();
    fc = GeoJson.from(fcJsonPath).toFeatureCollection();
    checkFeatureCollection(fc);

    Path fJsonPath = new File(fJsonUrl.toURI()).toPath();
    f = GeoJson.from(fJsonPath).toFeature();
    assertEquals(TEST_POINT, f.asPoint());

    /* String */

    String fcJsonString = Resources.toString(fcJsonUrl, StandardCharsets.UTF_8);
    fc = GeoJson.from(fcJsonString).toFeatureCollection();
    checkFeatureCollection(fc);

    String fJsonString = Resources.toString(fJsonUrl, StandardCharsets.UTF_8);
    f = GeoJson.from(fJsonString).toFeature();
    assertEquals(TEST_POINT, f.asPoint());
  }

  /* This hits most read methods in classes of the json package. */
  private void checkFeatureCollection(FeatureCollection fc) {

    List<Feature> features = fc.features();
    assertEquals(3, features.size());
    assertArrayEquals(BBOX, fc.bbox(), 0.0);

    Feature f1 = features.get(0);
    assertEquals("featureId", f1.idAsString());
    assertEquals(Type.POINT, f1.type());
    Properties f1Props = f1.properties();
    assertEquals("#ff0080", f1Props.getString("color"));
    assertEquals(1.0, f1Props.getDouble("id"), 0.0);
    assertEquals(1, f1Props.getInt("id"));

    Feature f3 = features.get(2);
    assertEquals(3, f3.idAsInt());
    assertArrayEquals(BBOX, f3.bbox(), 0.0);
  }

  @Test
  public void testWrite() throws IOException {

    /*
     * This also tests the removal of null 'id' and 'bbox' members on
     * serialization.
     */

    /* Feature collection JSON direct from builder. */
    JsonParser parser = new JsonParser();
    JsonElement jsonExpected = parser.parse(FC_BUILDER.toJson());

    /* Write to file and read back in. */
    Path out = testFolder.newFile(TEST_FILENAME).toPath();
    FC_BUILDER.write(out);
    JsonElement jsonActual = parser.parse(new String(Files.readAllBytes(out)));

    /*
     * Note that the JsonObject.equals() is tolerant of object members being
     * reordered as a Map comparison is taking place under the hood. However, if
     * JsonArray elements are reordered, equals tests fail because a List is is
     * being compared.
     */
    assertEquals(jsonExpected, jsonActual);

    /* Feature JSON direct from builder. */
    jsonExpected = parser.parse(F_BUILDER_1.toJson());

    /* Write to file and read back in. */
    F_BUILDER_2.write(out);
    jsonActual = parser.parse(new String(Files.readAllBytes(out)));
    assertEquals(jsonExpected, jsonActual);
  }

  @Test(expected = IllegalStateException.class)
  public void testWriteEmptyBuilder() throws URISyntaxException, MalformedURLException {
    GeoJson.builder().toJson();
  }

  /* Test the toFeatureCollection() and Feature.as*() methods. */
  @Test
  public void testFeatureConversion() {
    FeatureCollection fc = GeoJson.from(FC_BUILDER.toJson()).toFeatureCollection();
    List<Feature> features = fc.features();
    assertEquals(TEST_POINT, features.get(0).asPoint());
    assertEquals(TEST_LINE, features.get(1).asLineString());
    assertEquals(TEST_LINE, features.get(2).asPolygonBorder());
  }

  @Test
  public void testFeatureCollectionBboxRemove() {
    String json = GeoJson.builder()
        .add(Feature.point(TEST_POINT).build())
        .toJson();
    FeatureCollection fc = GeoJson.from(json).toFeatureCollection();
    assertNull(fc.bbox());
  }
  
  @Test
  public void testFeatureId() {
    Feature f = GeoJson.from(Feature.point(TEST_POINT).toJson()).toFeature();
    assertFalse(f.hasId());
    f = GeoJson.from(Feature.point(TEST_POINT).id("test").toJson()).toFeature();
    assertTrue(f.hasId());
  }
  
  @Test(expected = IllegalStateException.class)
  public void testFeatureUsedBuilder() {
    Feature.Builder f = Feature.point(TEST_POINT);
    f.build();
    f.build();
  }

  @Test(expected = UnsupportedOperationException.class)
  public void testGeometryCheckType() {
    Feature.Builder f = Feature.point(TEST_POINT);
    f.build().asLineString();
  }

  /* Test geometry array parsing exception */
  @Test(expected = IllegalArgumentException.class)
  public void testGeometryReadArray() {
    String coords4d = "{\"type\": \"FeatureCollection\"," +
        "\"features\":[{\"type\": \"Feature\",\"geometry\": {\"type\": \"Point\"," +
        "\"coordinates\": [[[[-117,34]]]]},\"properties\": {}}]}";
    GeoJson.from(coords4d).toFeatureCollection();
  }

  @Test(expected = IllegalArgumentException.class)
  public void testBadBbox() {
    Feature.point(TEST_POINT).bbox(new double[0]);
  }

  @Test
  public void testProperties() {

    URL fJsonUrl = Resources.getResource(
        GeoJsonTest.class,
        PROPERTIES_FILENAME);
    Properties props = GeoJson.from(fJsonUrl).toFeature().properties();

    assertTrue(props.containsKey(DESCRIPTION));
    assertTrue(props.getBoolean("boolean"));
    assertEquals(42, props.getInt("id"));
    assertEquals(0.5, props.getDouble(FILL_OPACITY), 0.0);
    assertEquals("description", props.getString(DESCRIPTION));
    TestObject actual = props.get("object", TestObject.class);
    TestObject expected = new TestObject("test", ImmutableList.of(1.0, 2.0));
    assertEquals(expected.getClass(), actual.getClass());
    assertEquals(expected, actual);

    /* builder */
    Map<String, Object> bMap = buildMap();
    assertTrue(bMap.containsKey(DESCRIPTION.toString()));
    assertEquals(42, props.getInt("id"));
    assertEquals(0.5, props.getDouble(FILL_OPACITY), 0.0);
    assertEquals("description", props.get(DESCRIPTION.toString()));
  }

  private static Map<String, Object> buildMap() {
    return Properties.builder()
        .put(DESCRIPTION, "description")
        .put(FILL, "#112233")
        .put(FILL_OPACITY, 0.5)
        .put(MARKER_COLOR, "#332211")
        .put(MARKER_SIZE, "small")
        .put(MARKER_SYMBOL, "+")
        .put(STROKE, "#555555")
        .put(STROKE_OPACITY, 0.7)
        .put(STROKE_WIDTH, 1.0)
        .put(TITLE, "title")
        .put("id", (double) 42) // mimic GSON deserialization
        .put("boolean", true)
        .put("object", new TestObject("test", ImmutableList.of(1.0, 2.0)))
        .put("null-test", null)
        .build();
  }

  private static class TestObject {
    final String str;
    final List<Double> list;

    TestObject(String str, List<Double> list) {
      this.str = str;
      this.list = list;
    }

    @Override
    public boolean equals(Object obj) {
      TestObject other = (TestObject) obj;
      return this.list.equals(other.list) && this.str.equals(other.str);
    }
  }

  public static void main(String[] args) throws IOException {
    
    // TODO clean
    
    // TODO this will need updating on new Location serialization
    // [-118.00000000000001, 35.0] --> [-118.0, 35.0],

    /* Create test files */
//    Path out = Paths.get("tmp/json-tests");
//    Path path = out.resolve(FEATURE_COLLECTION_FILENAME);
//    FC_BUILDER.write(path);
//
//    out = Paths.get("tmp/json-tests");
//    path = out.resolve(PROPERTIES_FILENAME);
//    Feature.point(TEST_POINT)
//        .id("properties-test")
//        // .bbox(new double[] {0, 0, 1, 1})
//        .properties(buildMap())
//        .write(path);
//
//    out = Paths.get("tmp/json-tests");
//    path = out.resolve("props-null.geojson");
//    Feature.point(TEST_POINT)
//        .id("properties-test")
//        .write(path);

    Feature f = GeoJson.from(Feature.point(TEST_POINT).toJson()).toFeature();
    System.out.println(f.idAsString());
    System.out.println(f.idAsInt());

  }

}
