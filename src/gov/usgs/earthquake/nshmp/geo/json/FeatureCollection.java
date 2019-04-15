package gov.usgs.earthquake.nshmp.geo.json;

import java.util.List;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

/**
 * A GeoJSON feature collection.
 * 
 * @author Brandon Clayton
 * @author Peter Powers
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

  static final class Serializer implements JsonSerializer<FeatureCollection> {

    @Override
    public JsonElement serialize(
        FeatureCollection featureCollection,
        java.lang.reflect.Type typeOfSrc,
        JsonSerializationContext context) {

      /*
       * Serialize using Gson that includes Feature adapter but not
       * FeatureCollection adapter that would trigger recursive calls to this
       * serializer.
       */
      JsonObject jObj = GeoJson.GSON_FEATURE
          .toJsonTree(featureCollection)
          .getAsJsonObject();

      if (featureCollection.bbox() == null) {
        jObj.remove("bbox");
      }
      return jObj;
    }
  }

}
