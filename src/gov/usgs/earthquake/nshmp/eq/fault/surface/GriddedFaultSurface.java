package gov.usgs.earthquake.nshmp.eq.fault.surface;

import static com.google.common.base.Preconditions.checkState;
import static gov.usgs.earthquake.nshmp.data.Data.checkInRange;
import static gov.usgs.earthquake.nshmp.eq.Earthquakes.checkCrustalDepth;
import static gov.usgs.earthquake.nshmp.eq.Earthquakes.checkInterfaceWidth;
import static gov.usgs.earthquake.nshmp.eq.fault.Faults.checkDip;
import static gov.usgs.earthquake.nshmp.eq.fault.Faults.checkStrike;
import static gov.usgs.earthquake.nshmp.eq.fault.Faults.checkTrace;

import com.google.common.collect.Range;

import gov.usgs.earthquake.nshmp.eq.fault.Faults;
import gov.usgs.earthquake.nshmp.geo.LocationGrid;
import gov.usgs.earthquake.nshmp.geo.LocationList;
import gov.usgs.earthquake.nshmp.util.Maths;

/**
 * Add comments here
 *
 * @author Peter Powers
 */
class GriddedFaultSurface {

  private final LocationGrid grid;

  // TODO not sure these are really needed once surface is constructed
  private final double strikeSpacing;
  private final double dipSpacing;

  private GriddedFaultSurface(LocationGrid grid,
      double strikeSpacing,
      double dipSpacing) {

    this.grid = grid;
    this.strikeSpacing = strikeSpacing;
    this.dipSpacing = dipSpacing;
  }

  // public static GriddedFaultSurface create()

  /*
   * Document that width, whether computed from top-bottom or assigned, is
   * always then applied in the dip direction, either supplied or computed from
   * trace.
   */

  public Builder builder() {
    return new Builder();
  }

  /*
   * TODO document builder which will almost certainly be part of a public API
   *
   * TODO doc trace assumed to be at depth=0?
   *
   * TODO do trace depths all need to be the same; condidtion used to be imposed
   * in assertValidState
   *
   * TODO surface is initialized with a dip direction in radians; this may be
   * normal to Faults.strike(trace), but may not be; in any event, we do not
   * want to recompute it internally.
   *
   * TODO single-use builder
   *
   * TODO right-hand-rule
   *
   * TODO should surface only be a single row if width < dipSpacing/2
   */
  public static class Builder {

    private static final Range<Double> SPACING_RANGE = Range.closed(0.01, 20.0);
    private static final String ID = "GriddedFaultSurface.Builder";

    private boolean built = false;

    // required
    private LocationList trace;
    private Double dipRad;
    private Double depth;

    // conditional (either but not both)
    private Double width;
    private Double lowerDepth;

    // optional - dipDir may not necessarily be normal to strike
    private Double dipDirRad;

    // optional with defualts
    private double strikeSpacing = 1.0;
    private double dipSpacing = 1.0;

    private Builder() {}

    public Builder trace(LocationList trace) {
      this.trace = checkTrace(trace);
      return this;
    }

    public Builder dip(double dip) {
      this.dipRad = checkDip(dip) * Maths.TO_RADIANS;
      return this;
    }

    public Builder dipDir(double dipDir) {
      this.dipDirRad = checkStrike(dipDir) * Maths.TO_RADIANS;
      return this;
    }

    public Builder depth(double depth) {
      this.depth = checkCrustalDepth(depth);
      return this;
    }

    public Builder lowerDepth(double lowerDepth) {
      checkState(width == null, "Either lower depth or width may be set, but not both");
      this.lowerDepth = checkCrustalDepth(lowerDepth);
      return this;
    }

    public Builder width(double width) {
      checkState(lowerDepth == null, "Either width or lower depth may be set, but not both");
      // we don't know what the surface may be used to represent
      // so we validate against the largest (interface) values
      this.width = checkInterfaceWidth(width);
      return this;
    }

    public Builder spacing(double spacing) {
      return spacing(spacing, spacing);
    }

    public Builder spacing(double strike, double dip) {
      this.strikeSpacing = checkInRange(SPACING_RANGE, "Strike Spacing", strike);
      this.dipSpacing = checkInRange(SPACING_RANGE, "Dip Spacing", dip);
      return this;
    }

    private void validateState(String id) {
      checkState(!built, "This %s instance as already been used", id);
      checkState(trace != null, "%s trace not set", id);
      checkState(dipRad != null, "%s dip not set", id);
      checkState(depth != null, "%s depth not set", id);

      checkState((width != null) ^ (lowerDepth != null), "%s width or lowerDepth not set",
          id);
      if (lowerDepth != null && lowerDepth <= depth) {
        throw new IllegalStateException("Lower depth is above upper depth");
      }
      built = true;
    }

    public DefaultGriddedSurface build() {
      validateState(ID);
      if (dipDirRad == null) {
        dipDirRad = Faults.dipDirectionRad(trace);
      }
      if (width == null) {
        width = (lowerDepth - depth) / Math.sin(dipRad);
      }
      return null;
      // new DefaultGriddedSurface(trace, dipRad, dipDirRad, depth, width,
      // strikeSpacing, dipSpacing);
    }

  }
}
