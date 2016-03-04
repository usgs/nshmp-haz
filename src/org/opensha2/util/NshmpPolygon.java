package org.opensha2.util;

import org.opensha2.geo.LocationList;

/**
 * Commonly used geographic polygons used by the NSHMP.
 *
 * @author Peter Powers
 */
@SuppressWarnings("javadoc")
public enum NshmpPolygon {

	CONTERMINOUS_US(Data.US_COORDS),
	NSHMP_CEUS_BOUNDS(Data.CEUS_BOUNDS),
	NSHMP_WUS_BOUNDS(Data.WUS_BOUNDS);

	private final LocationList coordinates;

	private NshmpPolygon(double[][] coords) {
		this.coordinates = createPolygon(coords);
	}

	/**
	 * Return a list of locations containing the coordinates of this polygon.
	 */
	public LocationList coordinates() {
		return coordinates;
	}

	private static LocationList createPolygon(double[][] coords) {
		LocationList.Builder locs = LocationList.builder();
		for (double[] coord : coords) {
			locs.add(coord[1], coord[0]);
		}
		return locs.build();
	}

	private static class Data {

		// @formatter:off

		private static final double[][] CEUS_BOUNDS = {
	        { -115.0, 24.6 },
			{  -65.0, 50.0 }
		};

		private static final double[][] WUS_BOUNDS = {
	        { -125.0, 24.6 },
			{ -100.0, 50.0 }
		};
		
		private static final double[][] US_COORDS = {
			{  -97.7, 25.6 },
			{  -96.9, 25.6 },
			{  -96.9, 27.6 },
			{  -93.5, 29.5 },
			{  -90.6, 28.7 },
			{  -88.9, 28.7 },
			{  -88.5, 30.0 },
			{  -86.2, 30.0 },
			{  -85.3, 29.4 },
			{  -84.9, 29.4 },
			{  -84.0, 29.8 },
			{  -83.0, 28.7 },
			{  -83.1, 27.7 },
			{  -82.0, 25.8 },
			{  -82.0, 24.4 },
			{  -81.2, 24.4 },
			{  -80.0, 25.0 },
			{  -79.7, 26.8 },
			{  -81.1, 30.7 },
			{  -80.7, 31.8 },
			{  -75.1, 35.2 },
			{  -75.1, 35.7 },
			{  -75.6, 36.9 },
			{  -73.4, 40.4 },
			{  -69.8, 41.1 },
			{  -69.8, 42.2 },
			{  -70.4, 42.4 },
			{  -70.4, 43.0 },
			{  -69.9, 43.6 },
			{  -68.8, 43.7 },
			{  -66.7, 44.7 },
			{  -67.5, 46.0 },
			{  -67.5, 47.1 },
			{  -68.4, 47.6 },
			{  -69.4, 47.6 },
			{  -71.8, 45.2 },
			{  -75.1, 45.2 },
			{  -76.7, 44.1 },
			{  -76.6, 43.7 },
			{  -77.0, 43.5 },
			{  -78.8, 43.5 },
			{  -79.4, 43.3 },
			{  -79.2, 42.8 },
			{  -82.2, 41.7 },
			{  -82.7, 41.7 },
			{  -82.9, 42.1 },
			{  -82.3, 42.6 },
			{  -82.2, 43.1 },
			{  -83.3, 46.4 },
			{  -88.4, 48.5 },
			{  -89.6, 48.2 },
			{  -94.4, 49.0 },
			{  -94.6, 49.6 },
			{  -95.5, 49.6 },
			{  -95.5, 49.2 },
			{ -123.7, 49.2 },
			{ -123.7, 48.6 },
			{ -125.3, 48.6 },
			{ -124.6, 46.2 },
			{ -125.0, 43.2 },
			{ -125.0, 40.0 },
			{ -119.9, 33.0 },
			{ -117.0, 32.3 },
			{ -115.0, 32.3 },
			{ -111.1, 31.1 },
			{ -107.9, 31.1 },
			{ -107.9, 31.5 },
			{ -106.6, 31.5 },
			{ -105.1, 30.2 },
			{ -104.8, 29.4 },
			{ -103.3, 28.7 },
			{ -103.0, 28.7 },
			{ -102.4, 29.5 },
			{ -101.5, 29.5 },
			{  -99.2, 26.1 },
			{  -97.7, 25.6 }
		};
	}
	
}
