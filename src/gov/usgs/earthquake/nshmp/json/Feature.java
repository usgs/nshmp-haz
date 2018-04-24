package gov.usgs.earthquake.nshmp.json;

import java.util.Map;

import gov.usgs.earthquake.nshmp.geo.Location;
import gov.usgs.earthquake.nshmp.geo.LocationList;
import gov.usgs.earthquake.nshmp.json.Geometry.Point;
import gov.usgs.earthquake.nshmp.json.Geometry.Polygon;

/**
 * Create a GeoJson {@code Feature} with {@link Geometry} and {@link Properties}.
 * See {@link #Feature(Geometry, Properties)} for an example.
 * <br><br>
 * 
 * Static factory classes are provided to create {@link Geometry.Point} 
 * 		and {@link Geometry.Polygon} (see each for examples):
 * 		<ul>
 * 			<li> {@link Feature#createPoint(Location, Properties)} </li> 
 * 			<li> {@link Feature#createPoint(double, double, Properties)} </li>
 * 			<li> {@link Feature#createPolygon(LocationList, Properties)} </li>
 * 		</ul>
 * 
 * @author Brandon Clayton
 */
public class Feature {
	/** The {@link Type} of GeoJson object: Feature */
	public final String type;
	/** The {@link Geometry} */
	public Geometry geometry;
	/** The {@link Properties} */
	public Map<String, Object> properties;
	
	/**
	 * Create a new instance of {@code Feature}.
	 * <br><br>
	 * 
	 * Example:
	 * <pre>
	 * {@code
	 * Location loc = Location.create(39.75, -105);
	 * Point point = new Geometry.Point(loc);
	 * Properties properties = Properties.builder().title("Golden").build();
	 * Feature feature = new Feature(point, properties);
	 * }  
	 * </pre>
	 * 
	 * @param geometry The {@link geometry} for the {@code Feature}.
	 * @param properties The {@link Properties} for the {@code Feature}.
	 */
	public Feature(Geometry geometry, Properties properties) {
		this.type = Type.FEATURE.toUpperCamelCase();
		this.geometry = geometry;
		this.properties = properties.attributes;
	}
	
	/**
	 * Static factory method to create a {@code Feature} with a {@link Geometry.Point} 
	 * 		{@code Geometry} given a latitude and longitude in degrees..
	 * <br><br>
	 * Example:
	 * <pre>
	 * {@code
	 * Properties properties = Properties.builder().title("Golden").build();
	 * Feature feature = Feature.createPoint(latitude, longitude, properties);
	 * }
	 * </pre>
	 * 
	 * @param latitude The latitude for the point.
	 * @param longitude The longitude for the point.
	 * @param properties The {@code Properties} ({@link Properties}).
	 * @return A new GeoJson {@code Feature}.
	 */
	public static Feature createPoint(
			double latitude, 
			double longitude, 
			Properties properties) {
		Point point = new Geometry.Point(latitude, longitude);
		return new Feature(point, properties);
	}
	
	/**
	 * Static factory method to create a {@code Feature} with a {@link Geometry.Point} 
	 * 		{@code Geometry} given a {@code Location}.
	 * <br><br>
	 * Example:
	 * <pre>
	 * {@code
	 * Location loc = Location.create(39.75, -105);
	 * Properties properties = Properties.builder().title("Golden").build();
	 * Feature feature = Feature.createPoint(loc, properties);
	 * }
	 * </pre>
	 * 
	 * @param loc - The {@code Location} ({@link Location}).
	 * @param properties - The {@code Properties} ({@link Properties}).
	 * @return A new GeoJson {@code Feature} ({@link Feature}).
	 */
	public static Feature createPoint(Location loc, Properties properties) {
		Point point = new Geometry.Point(loc);
		return new Feature(point, properties);
	}
	
	/**
	 * Static factory method to create a {@code Feature} with a {@link Geometry.Polygon} 
	 * 		{@code Geometry} given a {@link LocationList}.
	 * <br><br>
	 * Example:
	 * <pre>
	 * {@code
	 * LocationList locs = LocationList.builder()
	 *     .add(40, -120)
	 *     .add(38, -120)
	 *     .add(38, -122)
	 *     .add(40, -120)
	 *     .build();
	 * Properties properties = Properties.builder().title("Golden").build();
	 * Feature feature = Feature.createPolygon(locs, properties);
	 * }
	 * </pre>
	 * 
	 * @param locs - The {@code LocationList} ({@link LocationList}).
	 * @param properties - The {@code Properties} ({@link Properties}).
	 * @return A new GeoJson {@code Feature} ({@link Feature}).
	 */
	public static Feature createPolygon(LocationList locs, Properties properties) {
		Polygon polygon = new Geometry.Polygon(locs);
		return new Feature(polygon, properties);
	}
	
	/**
	 * Return a {@code String} in JSON format.
	 */
	@Override
	public String toString() {
		return Util.GSON.toJson(this);
	}
	
}
