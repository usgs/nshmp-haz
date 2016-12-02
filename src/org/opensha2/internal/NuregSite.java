package org.opensha2.internal;

import org.opensha2.geo.Location;
import org.opensha2.util.NamedLocation;

/**
 * CEUS-SSCn NRC NUREG demonstration sites. Most are also present in
 * {@code NshmpSite}, but with less precise coordinates.
 *
 * @author Peter Powers
 */
@SuppressWarnings("javadoc")
public enum NuregSite implements NamedLocation {

  CENTRAL_IL(-90.000, 40.000),
  CHATTANOOGA_TN(-85.255, 35.064),
  HOUSTON_TX(-95.363, 29.760),
  JACKSON_MS(-90.187, 32.312),
  MANCHESTER_NH(-71.463, 42.991),
  SAVANNAH_GA(-81.097, 32.082),
  TOPEKA_KS(-95.682, 39.047);

  private final Location loc;
  private final UsRegion state;

  private NuregSite(double lon, double lat) {
    this.loc = Location.create(lat, lon);
    this.state = UsRegion.valueOf(name().substring(name().lastIndexOf('_') + 1));
  }

  public UsRegion state() {
    return state;
  }

  @Override
  public Location location() {
    return loc;
  }

  @Override
  public String id() {
    return this.name();
  }

  @Override
  public String toString() {
    String label = Parsing.enumLabelWithSpaces(this, true);
    int stripIndex = label.lastIndexOf(' ');
    return label.substring(0, stripIndex) + " " + state.name();
  }

}
