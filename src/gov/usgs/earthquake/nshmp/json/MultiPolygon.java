package gov.usgs.earthquake.nshmp.json;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableList;

import gov.usgs.earthquake.nshmp.geo.LocationList;
import gov.usgs.earthquake.nshmp.geo.Region;

/**
 * Create a {@code MultiPolygon} GeoJson {@link Geometry}.
 * <br><br>
 *
 * Each {@link Polygon} in the {@code MultiPolygon} supports a border 
 *    and interiors.
 * <br><br>
 * 
 * See {@link Builder} for example.
 * <br><br>
 * 
 * NOTE: The border is assumed to be defined in
 *    counterclockwise direction while any interior is assumed to 
 *    be defined clockwise as per GeoJson specification.
 *   
 * @author Brandon Clayton
 */
public class MultiPolygon implements Geometry {
  /** The {@link GeoJsonType} of GeoJson {@link Geometry}: MultiPolygon*/
  private final String type;
  /** The coordinates that represent the {@code MultiPolygon} */
  private ImmutableList<ImmutableList<double[][]>> coordinates;
  /** A {@code ImmutableList<Polygon>} representing each {@link Polygon} */
  transient private ImmutableList<Polygon> polygons;
  
  private MultiPolygon(Builder builder) {
    this.type = GeoJsonType.MULTI_POLYGON.toUpperCamelCase();
    this.coordinates = builder.coordinates.build();
    this.polygons = builder.polygons.build();
  }

  @Override
  /**
   * Return the coordinates as a 
   *    {@code ImmutableList<ImmutableList<double[][]>>} from 
   *    a GeoJson {@code MultiPolygon}.
   * @return The coordinates.
   */
  public ImmutableList<ImmutableList<double[][]>> getCoordinates() {
    return coordinates;
  }

  /**
   * Return a {@code ImmutableList<Polygon>} representing all 
   *    {@link Polygon}s.
   * @return The {@code Polygon}s.
   */
  public ImmutableList<Polygon> getPolygons() {
    return polygons;
  }

  @Override
  /**
   * Return the {@link GeoJsonType} representing the {@code MultiPolygon}.
   * @return The {@code GeoJsonType}.
   */  
  public GeoJsonType getType() {
    return GeoJsonType.getEnum(type);
  }

  @Override
  /**
   * Return a {@code String} in JSON format.
   * @return The JSON {@code String}
   */
  public String toJsonString() {
    return Util.cleanPoly(Util.GSON.toJson(this, MultiPolygon.class));
  }

  /**
   * Return a {@code ImmutableList} of {@link Region}s. Each 
   *    {@code Region} represents a {@link Polygon} inside the 
   *    {@code MultiPolygon}.
   *    
   * @param name The {@code Region} name; can be empty {@code String}.
   * @return The {@code Region}s.
   */
  public ImmutableList<Region> toRegion(String name) {
    ImmutableList.Builder<Region> regions = new ImmutableList.Builder<>();
    
    for (Polygon polygon : polygons) {
      regions.add(polygon.toRegion(name));
    }
    
    return regions.build();
  }
  
  /**
   * Return a new {@link Builder}.
   * @return The {@code Builder}.
   */
  public static Builder builder() {
    return new Builder();
  }
  
  /**
   * {@link MultiPolygon} {@code Builder} to build a new 
   *    {@code MultiPolygon}.
   * <br><br>
   *
   * Easily add a {@link Polygon} to the {@code MultiPolygon}:
   *    <ul>
   *      <li> {@link #addPolygon(LocationList, LocationList...)} </li>
   *      <li> {@link #addPolygon(Polygon)} </li>
   *    </ul>
   *      
   * NOTE: The border is assumed to be defined in
   *    counterclockwise direction while any interior is assumed to 
   *    be defined clockwise as per GeoJson specification.
   * <br><br>
   * 
   * Example:
   * <pre>
   * LocationList borderA = LocationList.builder()
   *     .add(2.0, 102.0)
   *     .add(2.0, 103.0)
   *     .add(3.0, 103.0)
   *     .add(3.0, 102.0)
   *     .add(2.0, 102.0)
   *     .build();
   *
   * LocationList borderB = LocationList.builder()
   *     .add(0.0, 100.0)
   *     .add(0.0, 101.0)
   *     .add(1.0, 101.0)
   *     .add(1.0, 100.0)
   *     .add(0.0, 100.0)
   *     .build();
   * 
   * LocationList interiorB = LocationList.builder()
   *     .add(0.8, 100.8)
   *     .add(0.2, 100.8)
   *     .add(0.2, 100.2)
   *     .add(0.8, 100.2)
   *     .add(0.8, 100.8)
   *     .build();
   * 
   * MultiPolygon multiPolygon = MultiPolygon.builder()
   *     .addPolygon(borderA)
   *     .addPolygon(borderB, interiorB)
   *     .build();
   * </pre>
   * 
   * @author Brandon Clayton
   */
  public static class Builder {
    /** {@code ImmutableList} of all {@link Polygon}s */
    private ImmutableList.Builder<Polygon> polygons = new ImmutableList.Builder<>();
    /** The coordinates representing each {@link Polygon} */
    private ImmutableList.Builder<ImmutableList<double[][]>> coordinates = 
        new ImmutableList.Builder<>();
    
    private Builder() {}
   
    /**
     * Return a new {@link MultiPolygon} with added {@link Polygon}s.
     * @return The {@code MultiPolygon}.
     */
    public MultiPolygon build() {
      checkState(!polygons.build().isEmpty(), "Polygons cannot be empty");
      return new MultiPolygon(this);
    }

    /**
     * Add a {@link Polygon} with a border and interiors.
     * 
     * @param border The border of the {@code Polygon}.
     * @param interiors The interiors of the {@code Polygon}.
     * @return The {@code Builder} to be chainable.
     */
    public Builder addPolygon(LocationList border, LocationList... interiors) {
      Polygon polygon = new Polygon(border, interiors);
     
      LocationList[] interiorArray = polygon.getInteriors().toArray(new LocationList[0]);
      
      ImmutableList<double[][]> polygonCoords = new ImmutableList.Builder<double[][]>()
          .add(Util.toCoordinates(polygon.getBorder()))
          .addAll(Util.toCoordinates(interiorArray))
          .build();
      
      coordinates.add(polygonCoords);
      polygons.add(polygon);
      
      return this;
    }
   
    /**
     * Add a {@link Polygon}.
     *  
     * @param polygon The {@code Polygon}.
     * @return The {@code Builder} to be chainable.
     */
    public Builder addPolygon(Polygon polygon) {
      checkNotNull(polygon, "Polygon cannot be null");
      
      polygons.add(polygon);
     
      LocationList[] interiorArray = polygon.getInteriors().toArray(new LocationList[0]);
      
      ImmutableList<double[][]> polygonCoords = new ImmutableList.Builder<double[][]>()
          .add(Util.toCoordinates(polygon.getBorder()))
          .addAll(Util.toCoordinates(interiorArray))
          .build();
      
      coordinates.add(polygonCoords);
      
      return this;
    }
   
  }

}
