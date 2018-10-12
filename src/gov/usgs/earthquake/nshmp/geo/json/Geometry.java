package gov.usgs.earthquake.nshmp.geo.json;

import java.lang.reflect.Type;

import com.google.common.base.Converter;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.annotations.JsonAdapter;

import gov.usgs.earthquake.nshmp.geo.Location;
import gov.usgs.earthquake.nshmp.geo.LocationList;
import gov.usgs.earthquake.nshmp.util.Maths;

/*
 * Package utility class for creating GeoJSON geometries with structurally
 * different coordinate backing arrays.
 * 
 * In order to make GeoJSON coordinate arrays more compact, the GeoJson.GSON
 * instance registers the coordinate type adapter factory that processes
 * instances of double[] and puts the values on a single line. Geometry type
 * converters are used to parse serialized coordinate arrays back to object
 * types in the goe package. There are probably other, and perhaps more elegant,
 * ways to do the above, but the current implementation owrks for now.
 * 
 * @author Peter Powers
 */
class Geometry<T> {

  GeoJson.Type type;

  @JsonAdapter(CoordinatesSerializer.class)
  T coordinates;

  Geometry(GeoJson.Type type, T coordinates) {
    this.type = type;
    this.coordinates = coordinates;
  }

  /* Point geometry. */
  static Geometry<double[]> point(Location point) {
    return new Geometry<>(
        GeoJson.Type.POINT,
        POINT_CONVERTER.convert(point));
  }

  /* LineString geometry. */
  static Geometry<double[][]> lineString(LocationList line) {
    return new Geometry<>(
        GeoJson.Type.LINE_STRING,
        LINE_CONVERTER.convert(line));
  }

  /* Polygon geometry. */
  static Geometry<double[][][]> polygon(
      LocationList exterior,
      LocationList... interiors) {

    // TODO check interiors are interior and non-intersecting
    // TODO also check first-last repetition (linearRing checks)
    // TODO check poly min 3 points

    double[][][] coords = new double[interiors.length + 1][][];
    coords[0] = LINE_CONVERTER.convert(exterior);

    return new Geometry<>(
        GeoJson.Type.POLYGON,
        coords);
  }

  /*
   * Check that this geometry type matches the target type. This method
   * short-circuits an inevitable ClassCastException throwing an
   * UnsupportedOperationException instead.
   */
  private void checkType(GeoJson.Type target) {
    if (type != target) {
      throw new UnsupportedOperationException(
          String.format("Unable to return a GeoJSON %s as a %s.", type, target));
    }
  }

  Location toPoint() {
    checkType(GeoJson.Type.POINT);
    return POINT_CONVERTER.reverse().convert((double[]) coordinates);
  }

  LocationList toLineString() {
    checkType(GeoJson.Type.LINE_STRING);
    return LINE_CONVERTER.reverse().convert((double[][]) coordinates);
  }

  LocationList toPolygonBorder() {
    checkType(GeoJson.Type.POLYGON);
    return LINE_CONVERTER.reverse().convert(((double[][][]) coordinates)[0]);
  }

  /*
   * Coordinate converters.
   */

  private static final Converter<Location, double[]> POINT_CONVERTER = new PointConverter();
  private static final Converter<LocationList, double[][]> LINE_CONVERTER =
      new LineConverter();
  // private static final Converter<Region, double[][][]> POLYGON_CONVERTER =
  // new PolygonConverter();

  /* Location <--> [] */
  private static final class PointConverter extends Converter<Location, double[]> {

    @Override
    protected double[] doForward(Location location) {
      /*
       * TODO this rounding needs to be removed; wint be a problem once Location
       * updated
       */
      return new double[] {
          Maths.round(location.lon(), 5),
          Maths.round(location.lat(), 5)
      };
    }

    @Override
    protected Location doBackward(double[] coordinates) {
      return Location.create(coordinates[1], coordinates[0]);
    }
  }

  /* LocationList <--> List<[]> */
  private static final class LineConverter extends Converter<LocationList, double[][]> {

    @Override
    protected double[][] doForward(LocationList locations) {
      double[][] coords = new double[locations.size()][];
      for (int i = 0; i < locations.size(); i++) {
        coords[i] = POINT_CONVERTER.convert(locations.get(i));
      }
      return coords;
    }

    @Override
    protected LocationList doBackward(double[][] coordinates) {
      LocationList.Builder builder = LocationList.builder();
      for (double[] coordinate : coordinates) {
        builder.add(POINT_CONVERTER.reverse().convert(coordinate));
      }
      return builder.build();
    }
  }

  // /* Region <--> [][][] */
  // private static final class PolygonConverter extends Converter<Region,
  // double[][][]> {
  //
  // @Override
  // protected double[][][] doForward(Region region) {
  // double[][] coords = new double[locations.size()][];
  // for (int i = 0; i < locations.size(); i++) {
  // coords[i] = POINT_CONVERTER.convert(locations.get(i));
  // }
  // return coords;
  // }
  //
  // @Override
  // protected Region doBackward(double[][][] coordinates) {
  // LocationList border = LINE_CONVERTER.reverse().convert(coordinates[0]);
  // if (coordinates.length == 1) {
  // return Regions.
  // }
  // for (double[] point : coordinates) {
  // builder.add(point[1], point[0]);
  // }
  // return builder.build();
  // }
  // }

  static final class CoordinatesSerializer implements
      JsonSerializer<Object>,
      JsonDeserializer<Object> {

    @Override
    public Object deserialize(
        JsonElement json,
        Type typeOfT,
        JsonDeserializationContext context)
        throws JsonParseException {

      return readArray(json.getAsJsonArray());
    }

    /*
     * For reasons that aren't entirely clear, when attaching only the
     * deserializer above to the coordinates field, coordinates are then
     * serialized as an empty object. Only by also providing a serializer that
     * uses the current context to we get the expected output of variably
     * dimensioned arrays of coordinates.
     */

    @Override
    public JsonElement serialize(
        Object src,
        Type typeOfSrc,
        JsonSerializationContext context) {

      return context.serialize(src);
    }
  }

  /*
   * The methods below support deserilization of coordinate JsonArrays to their
   * primitive double[], double[][], etc counterparts. By default, GSON
   * deserilizes coordinates to List<Double>, List<List<Double>>, etc, which are
   * cumbersome.
   */
  private static Object readArray(JsonArray array) {
    switch (arrayDepth(array)) {
      case 1:
        return readCoordinates1D(array);
      case 2:
        return readCoordinates2D(array);
      case 3:
        return readCoordinates3D(array);
      default:
        throw new IllegalArgumentException("Coordinate parsing error");
        // case 4:
        // return readCoordinates4D(array);
    }
  }

  /* Recursively compute array depth/dimension. */
  private static int arrayDepth(JsonArray array) {
    if (array.get(0).isJsonArray()) {
      return 1 + arrayDepth(array.get(0).getAsJsonArray());
    }
    return 1;
  }

  /* Point */
  private static double[] readCoordinates1D(JsonArray array) {
    double[] coordinates = new double[array.size()];
    for (int i = 0; i < array.size(); i++) {
      coordinates[i] = array.get(i).getAsDouble();
    }
    return coordinates;
  }

  /* LineString, MultiPoint */
  private static double[][] readCoordinates2D(JsonArray array) {
    double[][] coordinates = new double[array.size()][];
    for (int i = 0; i < array.size(); i++) {
      coordinates[i] = readCoordinates1D(array.get(i).getAsJsonArray());
    }
    return coordinates;
  }

  /* Polygon, MultiLineString */
  private static double[][][] readCoordinates3D(JsonArray array) {
    double[][][] coordinates = new double[array.size()][][];
    for (int i = 0; i < array.size(); i++) {
      coordinates[i] = readCoordinates2D(array.get(i).getAsJsonArray());
    }
    return coordinates;
  }

  /* MultiPolygon */
  // private static double[][][][] readCoordinates4D(JsonArray array) {
  // double[][][][] coordinates = new double[array.size()][][][];
  // for (int i = 0; i < array.size(); i++) {
  // coordinates[i] = readCoordinates3D(array.get(i).getAsJsonArray());
  // }
  // return coordinates;
  // }

}
