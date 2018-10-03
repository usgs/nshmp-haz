package gov.usgs.earthquake.nshmp.geo.json;

import java.util.List;

/**
 * A GeoJSON feature collection.
 * 
 * @author Brandon Clayton
 */
@SuppressWarnings("unused")
public final class FeatureCollection {

  private final GeoJson.Type type = GeoJson.Type.FEATURE_COLLECTION;

  /** The bounding box array; may be {@code null}. */
  public final double[] bbox;

  /** The features in this collection. */
  public final List<Feature> features;

  FeatureCollection(List<Feature> features, double[] bbox) {
    this.features = features;
    this.bbox = bbox;
  }
}
