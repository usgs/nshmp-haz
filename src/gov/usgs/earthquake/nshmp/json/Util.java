package gov.usgs.earthquake.nshmp.json;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import gov.usgs.earthquake.nshmp.geo.Location;
import gov.usgs.earthquake.nshmp.geo.LocationList;
import gov.usgs.earthquake.nshmp.util.Maths;

/**
 * Utility methods for the json package. 
 * 
 * @author Brandon Clayton
 */
public class Util {
	/** {@link Gson} for converting objects to JSON {@code String} */
	static final Gson GSON;
	/** Precision for rounding */
	static final int ROUND = 5;
	
	static {
		GSON = new GsonBuilder()
				.disableHtmlEscaping()
				.serializeNulls()
				.setPrettyPrinting()
				.create();
	}

	/**
	 * Convert a {@link Location} to a {@code double[]}.
	 * @param loc The {@code Location}.
	 * @return A {@code double[]}.
	 */
	static double[] toCoordinates(Location loc) {
		return new double[] {Maths.round(loc.lon(), ROUND), Maths.round(loc.lat(), ROUND)};
	}
	
	/**
	 * Convert a {@link LocationList} to a {@code double[][]}.
	 * @param locs The {@code LocationList}.
	 * @return A {@code double[][]}.
	 */
	static double[][] toCoordinates(LocationList locs) {
		double[][] coords = new double[locs.size()][2]; 
		
		for (int jl = 0; jl < locs.size(); jl++) {
			coords[jl] = toCoordinates(locs.get(jl));
		}
		
		return coords;
	}
	
	/** 
	 * Brute force compaction of coordinate array onto single line.
	 * 
	 * @param s The {@code String} to clean up.
	 * @return The cleaned up {@code String}.
	 */
  public static String cleanPoints(String s) {
    return s.replace(": [\n          ", ": [")
        .replace(",\n          ", ", ")
        .replace("\n        ]", "]") + "\n";
  }

	/** 
	 * Brute force compaction of coordinate array onto single line.
	 * 
	 * @param s The {@code String} to clean up.
	 * @return The cleaned up {@code String}.
	 */
  public static String cleanPoly(String s) {
    return s
        .replace("\n          [", "[")
        .replace("[\n              ", "[ ")
        .replace(",\n              ", ", ")
        .replace("\n            ]", " ]")
        .replace("\n        ]", "]") + "\n";
  }

}
