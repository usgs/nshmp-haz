package gov.usgs.earthquake.nshmp.geo.json;

import java.util.List;

import com.google.common.collect.ImmutableList;

/**
 * A GeoJSON feature collection.
 * 
 * @author Brandon Clayton
 */
public final class FeatureCollection {

  @SuppressWarnings("unused") // for serialization only
  private final GeoJson.Type type = GeoJson.Type.FEATURE_COLLECTION;

  private final double[] bbox;
  private final List<Feature> features;

  FeatureCollection(List<Feature> features, double[] bbox) {
    this.features = features;
    this.bbox = bbox;
  }

  /**
   * The bounding box value array; may be {@code null}.
   */
  public double[] bbox() {
    return bbox;
  }

  /**
   * An immutable list of the features in this collection.
   */
  public List<Feature> features() {
    return ImmutableList.copyOf(features);
  }
}
