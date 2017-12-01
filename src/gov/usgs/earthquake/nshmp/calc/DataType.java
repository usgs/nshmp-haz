package gov.usgs.earthquake.nshmp.calc;

import gov.usgs.earthquake.nshmp.eq.model.SourceType;
import gov.usgs.earthquake.nshmp.gmm.Gmm;

/**
 * Data type identifiers. These are used to specify the different types of
 * hazard curves or other data that should be saved after a calculation is
 * complete.
 *
 * @author Peter Powers
 */
public enum DataType {

  /** Total hazard curves or magnitude-frequency distributions, etc. */
  TOTAL,

  /** {@linkplain Gmm Ground motion model} specific data. */
  GMM,

  /** Data by {@link SourceType}. */
  SOURCE,

  /**
   * Binary hazard curves. Binary curves may only be saved for map calculations
   * for which a map 'extents' region has been defined. See the <a href=
   * "https://github.com/usgs/nshmp-haz/wiki/Sites#geojson-format-geojson"
   * target="_top"> site specification</a> page for more details.
   * 
   * <p>The output format is a NSHMP file format that works with a number of
   * legacy fortran codes still in use. See <a
   * href="https://github.com/usgs/nshmp-haz-fortran"
   * target="_top">nshmp-haz-fortran</a>. Users should be aware that the format
   * has certain restrictions, for instance, the maximum number of intensity
   * measure levels that can be accomodated is 20. Buyer beware.
   */
  BINARY;
}
