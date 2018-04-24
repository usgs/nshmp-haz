package gov.usgs.earthquake.nshmp.json;

import com.google.common.collect.ImmutableList;

import gov.usgs.earthquake.nshmp.geo.Location;
import gov.usgs.earthquake.nshmp.geo.LocationList;

/**
 * Container class for different GeoJson geometries:
 * 	<ul>
 * 		<li> {@link Point} </li>
 * 		<li> {@link Polygon} </li>
 *  </ul>
 * @author Brandon Clayton
 */
public class Geometry {
	private String type;
	private Object coordinates;
	
	private Geometry() {}

	/**
	 * Create a GeoJson Point {@link Geometry}. 
	 * 		for an example.
	 *  
	 * @author Brandon Clayton
	 */
	public static class Point extends Geometry {
		/** The {@link Type} of GeoJson {@code Geometry}: Point */
		public final String type;
		/** The coordinates of the point */
		public double[] coordinates;
		
		/**
		 * Create a Point GeoJson {@code Geometry} with a {@link Location}.
		 * <br><br>
		 * 
		 * Example:
		 * <pre>
		 * {@code 
		 * Location loc = Location.create(40, -120);
		 * Point point = new Geometry.Point(loc);
		 * }
		 * </pre>
		 * 
		 * @param loc The {@code Location} ({@link Location}).
		 */
		public Point(Location loc) {
			this.type = Type.POINT.toUpperCamelCase();
			this.coordinates = Util.toCoordinates(loc);
		}
		
		/**
		 * Create a Point GeoJson {@code Geometry} with a latitude and longitude.
		 * <br><br>
		 * 
		 * Example:
		 * <pre>
		 * {@code 
		 * Point point = new Geometry.Point(40, -120);
		 * }
		 * </pre>
		 * 
		 * @param latitude The latitude in degrees
		 * @param longitude The longitude in degrees
		 */
		public Point(double latitude, double longitude) {
			Location loc = Location.create(latitude, longitude);
			this.type = Type.POINT.toUpperCamelCase();
			this.coordinates = Util.toCoordinates(loc);
		}
	}

	/**
	 * Create a GeoJson Polygon {@link Geometry} with a {@link LocationList}. 
	 * <br><br>
	 * 
	 * The {@code LocationList} should have the same values for the first and 
	 * 		last locations, if not it is added.
	 * 
	 * <br><br>
	 * NOTE: The {@code LocationList} is assumed to be defined in counterclockwise
	 * 		direction as per GeoJson specification.
	 * 
	 * 
	 * @author Brandon Clayton
	 */
	public static class Polygon extends Geometry {
		/** The {@link Type} of GeoJson {@code Geometry}: Polygon */
		public final String type;
		/** The coordinates of the polygon */
		public ImmutableList<double[][]> coordinates;
		
		/**
		 * Create a Polygon GeoJson {@code Geometry} with a 
		 * 		{@code LocationList} ({@link LocationList}).
		 * <br><br>
		 * 
		 * Example:
		 * <pre>
		 * {@code 
		 * LocationList locs = LocationList.builder()
		 *     .add(40, -120)
		 *     .add(38, -120)
		 *     .add(38, -122)
		 *     .add(40, -120)
		 *     .build();
		 * Point point = new Geometry.Point(locs);
		 * }
		 * </pre>
		 * 
		 * @param locs The {@link LocationList} for the polygon.
		 */
		public Polygon(LocationList locs) {
			locs = checkPolygonCoordinates(locs);
			this.type = Type.POLYGON.toUpperCamelCase();
			this.coordinates = ImmutableList.of(Util.toCoordinates(locs));
		}
	}
	
	/**
	 * Return a {@code String} in JSON format.
	 */
	@Override
	public String toString() {
		return Util.GSON.toJson(this);
	}
	
	/**
	 * Check whether the first and last {@code Location}s are the 
	 * 		same in the {@code LocationList}, if not add the first {@code Location}
	 * 		to the last spot.
	 * 
	 * @param locs The {@code LocationList} to check.
	 * @return The updated {@code LocationList}.
	 */
	private static LocationList checkPolygonCoordinates(LocationList locs) {
		Location firstLoc = locs.first();
		Location lastLoc = locs.last();
		
		if (!firstLoc.equals(lastLoc)) {
			LocationList updatedLocs = LocationList.builder()
					.addAll(locs)
					.add(firstLoc)
					.build();
			
			return updatedLocs;
		}
		
		return locs;
	}
	
}
