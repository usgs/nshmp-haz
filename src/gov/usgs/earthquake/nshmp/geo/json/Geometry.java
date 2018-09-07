package gov.usgs.earthquake.nshmp.geo.json;

/**
 * Interface for the different GeoJson {@code Geometry} types.
 * <br>
 * Current {@code Geometry} types defined:
 *    <ul>
 *      <li> {@link Point} </li>
 *      <li> {@link Polygon} </li>
 *    </ul>
 * <br>
 * 
 * A GeoJson {@code Geometry} contains two members:
 *    <ul>
 *      <li> type: a {@link GeoJsonType} </li>
 *      <li> coordinates: different types of array configurations </li>
 *    </ul>
 * The "type" member must be one of the {@code GeoJsonType}s.
 * <br>
 * 
 * The "coordinates" member is some type of array. The interface defines the 
 *    coordinates as a generic {@code Object}. To obtain the more specific type of
 *    array it is required to cast to the more specific {@code Geometry} type.
 * <br><br>
 * 
 * Example:
 * <pre>
 * Properties properties = Properties.builder()
 *    .title("test")
 *    .id("id")
 *    .build();
 *    
 * Feature feature = Feature.createPoint(properties, 40, -120);
 * 
 * Point point = (Point) feature.getGeometry();
 * 
 * double[] coords = point.getCoordinates();
 * </pre>
 * 
 * @author Brandon Clayton
 */
public interface Geometry extends GeoJson {
 
  /** 
   * Return a generic {@code Object} representing the coordinates
   *    of the {@code Geometry}.
   * <br>
   * 
   * It is best to cast to a specific {@code Geometry} type to obtain the 
   *    coordinates:
   *    <ul>
   *      <li> {@link Point} </li>
   *      <li> {@link Polygon} </li>
   *    </ul>
   * <br><br>
   * 
   * Example:
   * <pre>
   * Properties properties = Properties.builder()
   *    .title("test")
   *    .id("id")
   *    .build();
   *    
   * Feature feature = Feature.createPoint(properties, 40, -120);
   * 
   * Point point = (Point) feature.geometry;
   * 
   * double[] coords = point.getCoordinates();
   * </pre>
   * 
   * @return An {@code Object} representing the coordinates.
   */
  public Object getCoordinates();

  /**
   * Return {@code Geometry} as a {@link MultiPolygon}.
   * @return The {@code MultiPolygon}.
   * @throws UnsupportedOperationException If {@link GeoJsonType} does not equal 
   *    the correct {@code Geometry}.
   */
  public default MultiPolygon asMultiPolygon() {
    if (this.getType().equals(GeoJsonType.MULTI_POLYGON)) {
      return (MultiPolygon) this;
    } else {
      throw new UnsupportedOperationException("Geometry is not a multi-polygon");
    }
  }
  
  /**
   * Return {@code Geometry} as a {@link Point}.
   * @return The {@code Point}.
   * @throws UnsupportedOperationException If {@link GeoJsonType} does not equal 
   *    the correct {@code Geometry}.
   */
  public default Point asPoint() {
    if (this.getType().equals(GeoJsonType.POINT)) {
      return (Point) this;
    } else {
      throw new UnsupportedOperationException("Geometry is not a point");
    }
  }
  
  /**
   * Return {@code Geometry} as a {@link Polygon}.
   * @return The {@code Polygon}.
   * @throws UnsupportedOperationException If {@link GeoJsonType} does not equal 
   *    the correct {@code Geometry}.
   */
  public default Polygon asPolygon() {
    if (this.getType().equals(GeoJsonType.POLYGON)) {
      return (Polygon) this;
    } else {
      throw new UnsupportedOperationException("Geometry is not a polygon");
    }
  }
  
}
