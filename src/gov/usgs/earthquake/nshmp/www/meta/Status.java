package gov.usgs.earthquake.nshmp.www.meta;

/**
 * Service request status identifier.
 *
 * @author Peter Powers
 */
@SuppressWarnings("javadoc")
public enum Status {

  BUSY,
  ERROR,
  SUCCESS,
  USAGE;

  @Override
  public String toString() {
    return name().toLowerCase();
  }
}
