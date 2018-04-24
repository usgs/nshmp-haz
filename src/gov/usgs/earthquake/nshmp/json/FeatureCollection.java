package gov.usgs.earthquake.nshmp.json;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import gov.usgs.earthquake.nshmp.geo.Location;
import gov.usgs.earthquake.nshmp.geo.LocationList;

/**
 * Create a GeoJson {@code FeatureCollection}. See {@link #FeatureCollection(List)} 
 * 		for an example.
 * <br><br>
 * 
 * A GeoJson {@code FeatureCollection} is a GeoJson object with {@link #type} 
 * 		"FeatureCollection" and a single member {@link #features}. 
 * 		The {@link #features} member is a {@code List} of {@link Feature}s containing
 * 		{@link Geometry.Point}(s) and/or {@link Geometry.Polygon}(s).
 * <br><br>
 * 
 * The {@code List} of {@link Feature}s can be a mix of {@link Geometry.Point}(s) and
 * 		{@link Geometry.Polygon}(s). 
 * <br><br>
 * 
 * A {@link Builder} is supplied for ease of adding
 * 		{@link Feature}s and creating {@link Geometry}:
 * 		<ul>
 * 			<li> {@link Builder#add(Feature)} </li>
 * 			<li> {@link Builder#createPoint(Location, Properties)} </li>
 * 			<li> {@link Builder#createPoint(double, double, Properties)} </li>
 * 			<li> {@link Builder#createPolygon(LocationList, Properties)} </li>
 * 		</ul>
 * 		See {@link Builder} for example.
 * 
 * @author Brandon Clayton
 */
public class FeatureCollection {
	/** The {@link Type} of GeoJson object: FeatureCollection */
	public String type;
	/** The {@code List} of {@link Feature}s. */
	public List<Feature> features;

	/**
	 * Return a new instance of a GeoJson {@code FeatureCollection}.
	 * <br><br>
	 * 
	 * Example:
	 * <pre>
	 * {@code
	 * Location loc = Location.create(39.75, -105);
	 * Properties properties = Properties.builder().title("Golden").build();
	 * Feature feature = Feature.createPoint(loc, properties);
	 * List<Feature> features = new ArrayList<>();
	 * list.add(feature);
	 * FeatureCollection fc = new FeatureCollection(features);
	 * }
	 * </pre>
	 * 
	 * @param features The {@code List} of {@link Feature}s.
	 */
	public FeatureCollection(List<Feature> features) {
		this.type = Type.FEATURE_COLLECTION.toUpperCamelCase();
		this.features = features;
	}
	
	/**
	 * Read in a GeoJson {@code FeatureCollection} from a {@code InputStreamReader}.
	 * <br><br>
	 * 
	 * Example:
	 * <pre>
	 * {@code 
	 * String urlStr = "url of GeoJson FeatureCollection file";
	 * URL url = new URL(urlStr);
	 * InputStreamReader reader = new InputStreamReader(url.openStream());
	 * FeatureCollection fc = FeatureCollection.read(reader);
	 * 
	 * Feature singleFeature = fc.features.get(0);
	 * Point point = (Point) singleFeature.geometry;
	 * double[] coords = point.coordinates;
	 * Properties properties = Properties.builder()
	 *     .putAll(singleFeature.properties)
	 *     .build();
	 * }
	 * </pre>
	 * 
	 * @param reader The {@code InputStreamReader}
	 * @return A new instance of a {@code FeatureCollection}.
	 */
	public static FeatureCollection read(InputStreamReader reader) {
		return Util.GSON.fromJson(reader, FeatureCollection.class);
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
	 * Easily add {@link Feature}s to a {@code List} by:
	 * 		<ul>
	 * 			<li> {@link Builder#add(Feature)} </li>
	 * 			<li> {@link Builder#createPoint(Location, Properties)} </li>
	 * 			<li> {@link Builder#createPoint(double, double, Properties)} </li>
	 * 			<li> {@link Builder#createPolygon(LocationList, Properties)} </li>
	 * 		</ul>
	 * <br><br>
	 * 
	 * Example:
	 * <pre>
	 * {@code
	 * Properties properties = Properties.builder()
	 *     .title("Golden")
	 *     .id("golden")
	 *     .build();
	 * FeatureCollection fc = FeatureCollection.builder()
	 *     .createPoint(39.75, -105, properties)
	 *     .build();
	 * }
	 * </pre>
	 * 
	 * @author Brandon Clayton
	 */
	public static class Builder {
		private List<Feature> features = new ArrayList<>();
		
		private Builder() {}
	
		/**
		 * Return a new instance of a {@link FeatureCollection}.
		 * @return New {@link FeatureCollection}.
		 */
		public FeatureCollection build() {
			if (this.features.isEmpty()) {
				throw new IllegalStateException("List of features can not be empty"); 
			}
			return new FeatureCollection(this.features);
		}
		
		/**
		 * Add a {@link Feature} to the {@link FeatureCollection#features} {@code List}.
		 * @param feature The {@code Feature} to add.
		 * @return Return the {@code Builder} to make chainable.
		 */
		public Builder add(Feature feature) {
			this.features.add(feature);
			return this;
		}
	
		/**
		 * Add a {@link Feature} with {@link Geometry} of {@link Geometry.Point} 
		 * 		to the {@link FeatureCollection#features} {@code List}.
		 * @param loc The {@link Location} of the point.
		 * @param properties The {@link Properties} of the point.
		 * @return Return the {@code Builder} to make chainable.
		 */
		public Builder createPoint(Location loc, Properties properties) {
			this.features.add(Feature.createPoint(loc, properties));
			return this;
		}
		
		/**
		 * Add a {@link Feature} with {@link Geometry} of {@link Geometry.Point}
		 * 		to the {@link FeatureCollection#features} {@code List}.
		 * @param latitude The latitude of the point.
		 * @param longitude The longitude of the point.
		 * @param properties The {@link Properties} of the point.
		 * @return Return the {@code Builder} to make chainable.
		 */
		public Builder createPoint(double latitude, double longitude, Properties properties) {
			this.features.add(Feature.createPoint(latitude, longitude, properties));
			return this;
		}
		
		/**
		 * Add a {@link Feature} with {@link Geometry} of {@link Geometry.Polygon} 
		 * 		to the {@link FeatureCollection#features} {@code List}.
		 * @param locs The {@link LocationList} of the polygon.
		 * @param properties The {@link Properties} of the polygon.
		 * @return Return the {@code Builder} to make chainable.
		 */
		public Builder createPolygon(LocationList locs, Properties properties) {
			this.features.add(Feature.createPolygon(locs, properties));
			return this;
		}
	}
	
	/**
	 * Return a {@code String} in JSON format.
	 */
	@Override
	public String toString() {
		return Util.GSON.toJson(this);
	}
	
}