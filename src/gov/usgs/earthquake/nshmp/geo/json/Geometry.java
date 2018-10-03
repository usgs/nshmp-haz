package gov.usgs.earthquake.nshmp.geo.json;

import gov.usgs.earthquake.nshmp.geo.Location;
import gov.usgs.earthquake.nshmp.geo.LocationList;
import gov.usgs.earthquake.nshmp.geo.json.GeoJson.Type;

/*
 * Package utility class for creating GeoJSON geometries with structurally
 * different coordinate backing arrays.
 * 
 * @author Peter Powers
 */
@SuppressWarnings("unused")
class Geometry<T> {

  private GeoJson.Type type;
  private T coordinates;

  Geometry(GeoJson.Type type, T coordinates) {
    this.type = type;
    this.coordinates = coordinates;
  }

  /* Point geometry. */
  static Geometry<double[]> point(Location point) {
    return new Geometry<>(Type.POINT, toCoordinateArray(point));
  }

  /* LineString geometry. */
  static Geometry<double[][]> lineString(LocationList line) {
    return new Geometry<>(GeoJson.Type.LINE_STRING, toCoordinateArray(line));
  }

  /* polygon geometry. */
  static Geometry<double[][][]> polygon(LocationList exterior, LocationList... interiors) {
    // TODO check interiors are interior and non-intersecting
    double[][][] coords = new double[interiors.length + 1][][];
    coords[0] = toCoordinateArray(exterior);
    for (int i = 0; i < interiors.length; i++) {
      coords[i + 1] = toCoordinateArray(interiors[i]);
    }
    return new Geometry<>(GeoJson.Type.POLYGON, coords);
  }

  /*
   * Developer note: As long as the geojson coordinate array structure is unique
   * to GeoJson these converters should stay in this package.
   */

  /* Location --> [] */
  static double[] toCoordinateArray(Location location) {
    return new double[] { location.lon(), location.lat() };
  }

  /* LocationList --> [][] */
  static double[][] toCoordinateArray(LocationList locations) {
    double[][] coords = new double[locations.size()][];
    for (int i = 0; i < locations.size(); i++) {
      coords[i] = toCoordinateArray(locations.get(i));
    }
    return coords;
  }

}
