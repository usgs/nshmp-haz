package gov.usgs.earthquake.nshmp.geo.json;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableList;

import gov.usgs.earthquake.nshmp.geo.Location;
import gov.usgs.earthquake.nshmp.geo.LocationList;

/**
 * Create a GeoJson {@code FeatureCollection}. See
 *    {@link Builder} for an example. 
 *  <br><br>
 * 
 * A GeoJson {@code FeatureCollection} is a GeoJson object with {@link #type}
 *    "FeatureCollection" and a single member {@link #features}. The
 *    {@link #features} member is a {@code List} of {@link Feature}s containing
 *    a {@link Geometry} of {@link Point}(s) and/or {@link Polygon}(s). 
 * <br><br>
 * 
 * The {@code List} of {@link Feature}s can be a mix of
 *    {@link Point}(s) and {@link Polygon}(s). 
 * <br><br>
 *
 * Convenience methods are added to easily read and write GeoJson files:
 *    <ul>
 *      <li> Write to a file: {@link FeatureCollection#write(Path)} </li>
 *      <li> Read a file: {@link FeatureCollection#read(InputStreamReader)} </li>
 *    </ul>
 * 
 * @author Brandon Clayton
 */
public class FeatureCollection implements GeoJson, Iterable<Feature> {
  /** The {@link GeoJsonType} of GeoJson object: FeatureCollection */
  private final String type;
  /** The bounding box for a {@code FeaetureCollection} */
  private final double[] bbox;
  /** The {@code List} of {@link Feature}s. */
  private final List<Feature> features;

  /**
   * Return a new instance of a GeoJson {@code FeatureCollection}
   *    using the {@link #builder()}. 
   */
  private FeatureCollection(Builder builder) {
    this.type = GeoJsonType.FEATURE_COLLECTION.toUpperCamelCase();
    this.bbox = builder.bbox;
    this.features = builder.features;
  }

  /**
   * Return a {@code double[]} representing the bounding
   *    box for the {@code FeatureCollection}. 
   * <br>
   * 
   * If not bounding box was set, returns {@code null}.
   * @return The bounding box.
   */
  public double[] getBbox() {
    return bbox != null ? bbox : null;
  }

  /**
   * Return a {@code ImmutableList<Feature>} representing the 
   *    {@link Feature}s
   *    
   * @return The {@code Feature}s
   */
  public ImmutableList<Feature> getFeatures() {
    checkState(features.size() > 0, "Feature array is empty");
    return ImmutableList.copyOf(features); 
  }
 
  /**
   * Return the {@link GeoJsonType} representing the {@code FeatureCollection}.
   * @return The {@code GeoJsonType}.
   */ 
  @Override
  public GeoJsonType getType() {
    return GeoJsonType.getEnum(type);
  }
 
  /**
   * Return a {@code String} in JSON format.
   */
  @Override
  public String toJsonString() {
    Boolean hasAllPoint = checkGeometryTypes(features, GeoJsonType.POINT);
    
    if (hasAllPoint) {
      return JsonUtil.cleanPoints(JsonUtil.GSON.toJson(this));
    } else {
      return JsonUtil.cleanPoly(JsonUtil.GSON.toJson(this));
    }
  }
 
  /**
   * Return the {@code Iterator<Feature>}. 
   */
  @Override
  public Iterator<Feature> iterator() {
    return getFeatures().iterator();
  }

  /**
   * Read in a GeoJson {@code FeatureCollection} from a
   *    {@code InputStreamReader}. 
   * <br><br>
   * 
   * Example:
   * 
   * <pre>
   *   String urlStr = "url of GeoJson FeatureCollection file";
   *   URL url = new URL(urlStr);
   *   InputStreamReader reader = new InputStreamReader(url.openStream());
   *   FeatureCollection fc = FeatureCollection.read(reader);
   * 
   *   Feature singleFeature = fc.getFeatures().get(0);
   *   Point point = (Point) singleFeature.getGeometry();
   *   double[] coords = point.getCoordinates();
   *   Location loc = point.getLocation();
   *   Properties properties = singleFeature.getProperties();
   * </pre>
   * 
   * @param reader The {@code InputStreamReader}
   * @return A new instance of a {@code FeatureCollection}.
   */
  public static FeatureCollection read(InputStreamReader reader) {
    checkNotNull(reader, "Input stream cannot be null");
    return JsonUtil.GSON.fromJson(reader, FeatureCollection.class);
  }

  /**
   * Read in a GeoJson {@code FeatureCollection} from a {@code Path}. 
   * <br><br>
   * 
   * Example:
   * 
   * <pre>
   *   Path path = Paths.get("etc", "test.geojson");
   *   FeatureCollection fc = FeatureCollection.read(path);
   * 
   *   Feature singleFeature = fc.getFeatures().get(0);
   *   Point point = (Point) singleFeature.getGeometry();
   *   double[] coords = point.getCoordinates();
   *   Location loc = point.getLocation();
   *   Properties properties = singleFeature.getProperties();
   * </pre>
   * 
   * @param path The {@code Path}
   * @return A new instance of a {@code FeatureCollection}.
   * @throws IOException The {@code IOException}.
   */
  public static FeatureCollection read(Path path) throws IOException {
    checkArgument(Files.exists(path), "File [%s] does not exist", path); 
    BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8);
    FeatureCollection fc = JsonUtil.GSON.fromJson(reader, FeatureCollection.class);
    reader.close();
    
    return fc;
  }
 
  /**
   * Read in a GeoJson {@code FeatureCollection} from a {@code URL}. 
   * <br><br>
   * 
   * Example:
   * 
   * <pre>
   *   String urlStr = "url of GeoJson FeatureCollection file";
   *   URL url = new URL(urlStr);
   *   FeatureCollection fc = FeatureCollection.read(url);
   * 
   *   Feature singleFeature = fc.getFeatures().get(0);
   *   Point point = (Point) singleFeature.getGeometry();
   *   double[] coords = point.getCoordinates();
   *   Location loc = point.getLocation();
   *   Properties properties = singleFeature.getProperties();
   * </pre>
   * 
   * @param url The {@code URL}.
   * @return A new instance of a {@code FeatureCollection}.
   * @throws IOException The {@code IOException}.
   */
  public static FeatureCollection read(URL url) throws IOException {
    checkArgument(url != null, "URL cannot be null");
    BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()));
    FeatureCollection fc = JsonUtil.GSON.fromJson(reader, FeatureCollection.class);
    reader.close();
    
    return fc;
  }
  
  /**
   * Write a {@code FeatureCollection} to a file.
   * <br><br>
   * 
   * Example:
   * <pre>
   *  Properties properties = Properties.builder()
   *      .title("Title")
   *      .id("id")
   *      .build();
   *  FeatureCollection fc = FeatureCollection.builder()
   *      .createPoint(properties, 40, -120)
   *      .build();
   *  Path out = Paths.get("etc").resolve("test.geojson");
   *  fc.write(out);
   * </pre>
   *
   * @param out The {@code Path} to write the file.
   * @throws IOException The {@code IOException}. 
   */
  public void write(Path out) throws IOException {
    checkNotNull(out, "Path cannot be null");
    String json = toJsonString();
    Files.write(out, json.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Return a new instance of {@link Builder}.
   * @return New {@link Builder}.
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Convenience builder to build a new instance of a {@link FeatureCollection}.
   * <br><br>
   *  
   * Example:
   * <pre>
   *   // Build properties 
   *   Properties properties = Properties.builder()
   *       .title("Golden")
   *       .id("golden")
   *       .build();
   *       
   *   // Build a FeatureCollection
   *   FeatureCollection fc = FeatureCollection.builder()
   *       .addPoint(39.75, -105, properties)
   *       .build();
   * </pre>
   * 
   * @author Brandon Clayton
   */
  public static class Builder {
    private List<Feature> features = new ArrayList<>(); 
    private double[] bbox;

    private Builder() {}

    /**
     * Return a new instance of a {@link FeatureCollection}.
     * @return New {@link FeatureCollection}.
     */
    public FeatureCollection build() {
      checkState(!features.isEmpty(), "List of features cannot be empty");
      return new FeatureCollection(this);
    }

    /**
     * Add a {@link Feature} to the {@link FeatureCollection#features}
     *    {@code List}.
     *    
     * @param feature The {@code Feature} to add.
     * @return Return the {@code Builder} to make chainable.
     */
    public Builder add(Feature feature) {
      checkNotNull(feature, "A feature cannot be null");
      features.add(feature);
      return this;
    }
    
    /**
     * Add a {@link Feature} with a {@link Geometry} 
     *    to the {@link FeatureCollection#features} {@code List}.
     *    
     * @param geometry The {@code Geometry}.
     * @param properties The {@link Properties}.
     * @return Return the {@code Builder} to make chainable.
     */
    public Builder add(Geometry geometry, Properties properties) {
      Feature feature = Feature.builder()
          .addGeometry(geometry)
          .properties(properties)
          .build();
      
      features.add(feature);
      return this;
    }
    
    /**
     * Add a {@link Feature} with a {@link Geometry} 
     *    to the {@link FeatureCollection#features} {@code List}.
     *    
     * @param geometry The {@code Geometry}.
     * @param properties The {@link Properties}.
     * @param featureId An {@code Optional} ({@code String} or {@code int})
     *    id for the {@code Feature}.
     * @param featureBbox An {@code Optional} {@code double[]} representing the 
     *    bounding box for a {@code Feature}.
     * @return Return the {@code Builder} to make chainable.
     */
    public Builder add(
        Geometry geometry, 
        Properties properties, 
        Optional<?> featureId,
        Optional<double[]> featureBbox) {
      Feature.Builder feature = Feature.builder()
          .addGeometry(geometry)
          .properties(properties);
      
      ifPresent(feature, featureId, featureBbox);
      
      features.add(feature.build());
      return this;
    }
  
    /**
     * Set the {@code FeatureCollection} bounding box.
     * 
     * @param bbox The bounding box.
     * @return Return the {@code Builder} to make chainable.
     */
    public Builder bbox(double[] bbox) {
      this.bbox = bbox;
      return this;
    }
    
    /**
     * Add a {@link Feature} with {@link Geometry} of {@link MultiPolygon} 
     *    to the {@link FeatureCollection#features} {@code List}.
     *    
     * @param multiPolygon The {@code MultiPolygon}.
     * @param properties The {@link Properties}.
     * @return Return the {@code Builder} to make chainable.
     */
    public Builder addMultiPolygon(MultiPolygon multiPolygon, Properties properties) {
      Feature feature = Feature.builder()
          .addMultiPolygon(multiPolygon)
          .properties(properties)
          .build();
      
      features.add(feature);
      return this;
    }
    
    /**
     * Add a {@link Feature} with {@link Geometry} of {@link MultiPolygon} 
     *    to the {@link FeatureCollection#features} {@code List}.
     *    
     * @param multiPolygon The {@code MultiPolygon}.
     * @param properties The {@link Properties}.
     * @param featureId An {@code Optional} ({@code String} or {@code int})
     *    id for the {@code Feature}.
     * @param featureBbox An {@code Optional} {@code double[]} representing the 
     *    bounding box for a {@code Feature}.
     * @return Return the {@code Builder} to make chainable.
     */
    public Builder addMultiPolygon(
        MultiPolygon multiPolygon,
        Properties properties, 
        Optional<?> featureId,
        Optional<double[]> featureBbox) {
      Feature.Builder feature = Feature.builder()
          .addMultiPolygon(multiPolygon)
          .properties(properties);
      
      ifPresent(feature, featureId, featureBbox);
          
      features.add(feature.build());
      return this;
    }
    
    /**
     * Add a {@link Feature} with {@link Geometry} of {@link MultiPolygon} 
     *    to the {@link FeatureCollection#features} {@code List}.
     *    
     * @param polygons A {@code List} of {@link Polygon}s. 
     * @param properties The {@link Properties}.
     * @return Return the {@code Builder} to make chainable.
     */
    public Builder addMultiPolygon(List<Polygon> polygons, Properties properties) {
      Feature feature = Feature.builder()
          .addMultiPolygon(polygons)
          .properties(properties)
          .build();
      
      features.add(feature);
      return this;
    }

    /**
     * Add a {@link Feature} with {@link Geometry} of {@link MultiPolygon} 
     *    to the {@link FeatureCollection#features} {@code List}.
     *    
     * @param polygons A {@code List} of {@link Polygon}s. 
     * @param properties The {@link Properties}.
     * @param featureId An {@code Optional} ({@code String} or {@code int})
     *    id for the {@code Feature}.
     * @param featureBbox An {@code Optional} {@code double[]} representing the 
     *    bounding box for a {@code Feature}.
     * @return Return the {@code Builder} to make chainable.
     */
    public Builder addMultiPolygon(
        List<Polygon> polygons, 
        Properties properties,
        Optional<?> featureId,
        Optional<double[]> featureBbox) {
      Feature.Builder feature = Feature.builder()
          .addMultiPolygon(polygons)
          .properties(properties);
      
      ifPresent(feature, featureId, featureBbox);
      
      features.add(feature.build());
      return this;
    }

    /**
     * Add a {@link Feature} with {@link Geometry} of {@link Point} to
     *    the {@link FeatureCollection#features} {@code List}.
     *    
     * @param loc The {@link Location} of the point.
     * @param properties The {@link Properties} of the point.
     * @return Return the {@code Builder} to make chainable.
     */
    public Builder addPoint(Location loc, Properties properties) {
      Feature feature = Feature.builder()
          .addPoint(loc)
          .properties(properties)
          .build();
      
      features.add(feature);
      return this;
    }

    /**
     * Add a {@link Feature} with {@link Geometry} of {@link Point} to
     *    the {@link FeatureCollection#features} {@code List}.
     *    
     * @param loc The {@link Location} of the point.
     * @param properties The {@link Properties} of the point.
     * @param featureId An {@code Optional} ({@code String} or {@code int})
     *    id for the {@code Feature}.
     * @param featureBbox An {@code Optional} {@code double[]} representing the 
     *    bounding box for a {@code Feature}.
     * @return Return the {@code Builder} to make chainable.
     */
    public Builder addPoint(
        Location loc, 
        Properties properties,
        Optional<?> featureId,
        Optional<double[]> featureBbox) {
      Feature.Builder feature = Feature.builder()
          .addPoint(loc)
          .properties(properties);
      
      ifPresent(feature, featureId, featureBbox);
      
      features.add(feature.build());
      return this;
    }

    /**
     * Add a {@link Feature} with {@link Geometry} of {@link Point} to
     *    the {@link FeatureCollection#features} {@code List}.
     *    
     * @param latitude The latitude of the point.
     * @param longitude The longitude of the point.
     * @param properties The {@link Properties} of the point.
     * @return Return the {@code Builder} to make chainable.
     */
    public Builder addPoint(double latitude, double longitude, Properties properties) {
      Feature feature = Feature.builder()
          .addPoint(latitude, longitude)
          .properties(properties)
          .build();
      
      features.add(feature);
      return this;
    }

    /**
     * Add a {@link Feature} with {@link Geometry} of {@link Point} to
     *    the {@link FeatureCollection#features} {@code List}.
     *    
     * @param latitude The latitude of the point.
     * @param longitude The longitude of the point.
     * @param properties The {@link Properties} of the point.
     * @param featureId An {@code Optional} ({@code String} or {@code int})
     *    id for the {@code Feature}.
     * @param featureBbox An {@code Optional} {@code double[]} representing the 
     *    bounding box for a {@code Feature}.
     * @return Return the {@code Builder} to make chainable.
     */
    public Builder addPoint(
        double latitude, 
        double longitude, 
        Properties properties,
        Optional<?> featureId,
        Optional<double[]> featureBbox) {
      Feature.Builder feature = Feature.builder()
          .addPoint(latitude, longitude)
          .properties(properties);
      
      ifPresent(feature, featureId, featureBbox);
      
      features.add(feature.build());
      return this;
    }

    /**
     * Add a {@link Feature} with {@link Geometry} of {@link Polygon}
     *    to the {@link FeatureCollection#features} {@code List}.
     *    
     * @param border The border of the {@code Polygon} 
     * @param properties The {@link Properties} of the polygon.
     * @param interiors The interiors of the {@code Polygon}
     * @return Return the {@code Builder} to make chainable.
     */
    public Builder addPolygon(
        LocationList border,
        Properties properties,
        LocationList... interiors) {
      Feature feature = Feature.builder()
          .addPolygon(border, interiors)
          .properties(properties)
          .build();
      
      features.add(feature);
      return this;
    }
    
    /**
     * Add a {@link Feature} with {@link Geometry} of {@link Polygon}
     *    to the {@link FeatureCollection#features} {@code List}.
     *    
     * @param border The border of the {@code Polygon} 
     * @param properties The {@link Properties} of the polygon.
     * @param featureId An {@code Optional} ({@code String} or {@code int})
     *    id for the {@code Feature}.
     * @param featureBbox An {@code Optional} {@code double[]} representing the 
     *    bounding box for a {@code Feature}.
     * @param interiors The interiors of the {@code Polygon}
     * @return Return the {@code Builder} to make chainable.
     */
    public Builder addPolygon(
        LocationList border,
        Properties properties,
        Optional<?> featureId,
        Optional<double[]> featureBbox,
        LocationList... interiors) {
      Feature.Builder feature = Feature.builder()
          .addPolygon(border, interiors)
          .properties(properties);
      
      ifPresent(feature, featureId, featureBbox);
      
      features.add(feature.build());
      return this;
    }
    
    /**
     * Add a {@link Feature} with {@link Geometry} of {@link Polygon}
     *    to the {@link FeatureCollection#features} {@code List}.
     *    
     * @param polygon The {@code Polygon} 
     * @param properties The {@link Properties} of the polygon.
     * @return Return the {@code Builder} to make chainable.
     */
    public Builder addPolygon(Polygon polygon, Properties properties) {
      Feature feature = Feature.builder()
          .addPolygon(polygon)
          .properties(properties)
          .build();
      
      features.add(feature);
      return this;
    }
    
    /**
     * Add a {@link Feature} with {@link Geometry} of {@link Polygon}
     *    to the {@link FeatureCollection#features} {@code List}.
     *    
     * @param polygon The {@code Polygon} 
     * @param properties The {@link Properties} of the polygon.
     * @param featureId An {@code Optional} ({@code String} or {@code int})
     *    id for the {@code Feature}.
     * @param featureBbox An {@code Optional} {@code double[]} representing the 
     *    bounding box for a {@code Feature}.
     * @return Return the {@code Builder} to make chainable.
     */
    public Builder addPolygon(
        Polygon polygon, 
        Properties properties,
        Optional<?> featureId,
        Optional<double[]> featureBbox) {
      Feature.Builder feature = Feature.builder()
          .addPolygon(polygon)
          .properties(properties);
      
      ifPresent(feature, featureId, featureBbox);

      features.add(feature.build());
      return this;
    }
   
    private void ifPresent(Feature.Builder feature, Optional<?> id, Optional<double[]> bbox) {
      if (id.isPresent()) feature.id(JsonUtil.GSON.toJsonTree(id.get()));
      if (bbox.isPresent()) feature.bbox(bbox.get());
    }
  }

  /**
   * Check to see if all {@code Feature}s in the {@code FeatureCollection}
   *    have the same {@code Geometry}.
   *    
   * @param features The features
   * @param geometryType The geometry type
   * @return Whether it has all same geometry
   */
  private static Boolean checkGeometryTypes(
      List<Feature> features, 
      GeoJsonType geometryType) {
    return features.stream()
        .map(d -> d.getGeometry().getType())
        .allMatch(d -> d.equals(geometryType));
  }

}
