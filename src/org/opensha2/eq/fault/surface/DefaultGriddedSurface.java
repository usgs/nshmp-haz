package org.opensha2.eq.fault.surface;

import static com.google.common.base.Preconditions.checkState;
import static java.math.RoundingMode.HALF_UP;

import static org.opensha2.data.Data.checkInRange;
import static org.opensha2.eq.fault.Faults.validateDepth;
import static org.opensha2.eq.fault.Faults.validateDip;
import static org.opensha2.eq.fault.Faults.validateInterfaceWidth;
import static org.opensha2.eq.fault.Faults.validateStrike;
import static org.opensha2.eq.fault.Faults.validateTrace;
import static org.opensha2.geo.GeoTools.TO_DEG;
import static org.opensha2.geo.GeoTools.TO_RAD;
import static org.opensha2.geo.LocationVector.createWithPlunge;
import static org.opensha2.geo.Locations.linearDistanceFast;
import static org.opensha2.geo.Locations.location;

import org.opensha2.eq.fault.Faults;
import org.opensha2.geo.Location;
import org.opensha2.geo.LocationList;
import org.opensha2.geo.LocationVector;
import org.opensha2.geo.Locations;

import com.google.common.collect.Lists;
import com.google.common.collect.Range;
import com.google.common.math.DoubleMath;

import java.util.Iterator;
import java.util.List;

/**
 * Default {@link GriddedSurface} implementation. Gridded surfaces are
 * constructed by evenly discretizing a fault trace and projecting that trace
 * down-dip in a direction normal to the average-strike of the trace, here
 * defined as a line connecting the first and last endpoints of the trace.
 * 
 * <p>New gridded surfaces can only be created via a {@linkplain Builder
 * builder}, which provides a variety of construction options.
 * 
 * TODO review documentation once builder docs written. There are circumstances
 * where a dip direction may be specified (e.g. UCERF3 subsections) and the
 * above staement rendered false.
 * 
 * @author Ned Field
 * @author Peter Powers
 */
public class DefaultGriddedSurface extends AbstractGriddedSurface {

  private final LocationList trace;
  private final double depth;
  private final double dipRad;
  private final double dipDirRad;
  private final double width;
  private final Location centroid;

  private DefaultGriddedSurface(LocationList trace, double dipRad, double dipDirRad,
      double depth, double width, double strikeSpacing, double dipSpacing) {

    this.trace = trace;
    this.depth = depth;
    this.dipRad = dipRad;
    this.dipDirRad = dipDirRad;
    this.width = width;

    // compute actual (best fit) spacings
    double length = trace.length();
    this.strikeSpacing = length / Math.ceil(length / strikeSpacing);
    this.dipSpacing = width / Math.ceil(width / dipSpacing);

    createEvenlyGriddedSurface();

    centroid = Locations.centroid(this);
  }

  /**
   * Return a new surface builder.
   */
  public static Builder builder() {
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
  @SuppressWarnings("javadoc")
  public static class Builder {

    private static final Range<Double> SPACING_RANGE = Range.closed(0.01, 20.0);

    private static final String ID = "DefaultGriddedSurface.Builder";
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
    private double dipSpacing = 1.0;
    private double strikeSpacing = 1.0;

    private Builder() {}

    public Builder trace(LocationList trace) {
      this.trace = validateTrace(trace);
      return this;
    }

    public Builder dip(double dip) {
      this.dipRad = validateDip(dip) * TO_RAD;
      return this;
    }

    public Builder dipDir(double dipDir) {
      this.dipDirRad = validateStrike(dipDir) * TO_RAD;
      return this;
    }

    public Builder depth(double depth) {
      this.depth = validateDepth(depth);
      return this;
    }

    public Builder lowerDepth(double lowerDepth) {
      checkState(width == null, "Either lower depth or width may be set, but not both");
      this.lowerDepth = validateDepth(lowerDepth);
      return this;
    }

    public Builder width(double width) {
      checkState(lowerDepth == null, "Either width or lower depth may be set, but not both");
      // we don't know what the surface may be used to represent
      // so we validate against the largest (interface) values
      this.width = validateInterfaceWidth(width);
      return this;
    }

    public Builder spacing(double spacing) {
      dipSpacing = checkInRange(SPACING_RANGE, "Spacing", spacing);
      strikeSpacing = spacing;
      return this;
    }

    public Builder spacing(double dipSpacing, double strikeSpacing) {
      this.dipSpacing = checkInRange(SPACING_RANGE, "Dip Spacing", dipSpacing);
      this.strikeSpacing = checkInRange(SPACING_RANGE, "Strike Spacing", strikeSpacing);
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
      if (dipDirRad == null) dipDirRad = Faults.dipDirectionRad(trace);
      if (width == null) width = (lowerDepth - depth) / Math.sin(dipRad);
      return new DefaultGriddedSurface(trace, dipRad, dipDirRad, depth, width,
        strikeSpacing, dipSpacing);
    }

  }

  /**
   * Creates the Stirling Gridded Surface from the Simple Fault Data
   * @throws FaultException
   */
  private void createEvenlyGriddedSurface() {

    final int numSegments = trace.size() - 1;
    // final double avDipRadians = dip * GeoTools.TO_RAD;
    final double gridSpacingCosAveDipRadians = dipSpacing * Math.cos(dipRad);
    final double gridSpacingSinAveDipRadians = dipSpacing * Math.sin(dipRad);

    double[] segmentLenth = new double[numSegments];
    double[] segmentAzimuth = new double[numSegments]; // in radians
    double[] segmentCumLenth = new double[numSegments];

    double cumDistance = 0;
    int i = 0;

    Location firstLoc;
    Location lastLoc;

    // Iterate over each Location in Fault Trace
    // Calculate distance, cumulativeDistance and azimuth for
    // each segment
    Iterator<Location> it = trace.iterator();
    firstLoc = it.next();
    lastLoc = firstLoc;
    Location loc = null;
    LocationVector dir = null;
    while (it.hasNext()) {

      loc = it.next();
      dir = LocationVector.create(lastLoc, loc);

      double azimuth = dir.azimuth();
      double distance = dir.horizontal();
      cumDistance += distance;

      segmentLenth[i] = distance;
      segmentAzimuth[i] = azimuth;
      segmentCumLenth[i] = cumDistance;

      i++;
      lastLoc = loc;

    }

    // Calculate the number of rows and columns
    int rows = 1 + Math.round((float) (width / dipSpacing));
    int cols = 1 + Math.round((float) (segmentCumLenth[numSegments - 1] / strikeSpacing));

    // Create GriddedSurface
    int segmentNumber, ith_row, ith_col = 0;
    double distanceAlong, distance, hDistance, vDistance;
    Location location1;

    // initialize the num of Rows and Cols for the container2d object
    setNumRowsAndNumCols(rows, cols);

    // Loop over each column - ith_col is ith grid step along the fault
    while (ith_col < cols) {

      // calculate distance from column number and grid spacing
      distanceAlong = ith_col * strikeSpacing;

      // Determine which segment distanceAlong is in
      segmentNumber = 1;
      while (segmentNumber <= numSegments &&
        distanceAlong > segmentCumLenth[segmentNumber - 1]) {
        segmentNumber++;
      }
      // put back in last segment if grid point has just barely stepped
      // off the end
      if (segmentNumber == numSegments + 1) segmentNumber--;

      // Calculate the distance from the last segment point
      if (segmentNumber > 1) {
        distance = distanceAlong - segmentCumLenth[segmentNumber - 2];
      } else {
        distance = distanceAlong;
      }

      // Calculate the grid location along fault trace and put into grid
      location1 = trace.get(segmentNumber - 1);
      dir = LocationVector.create(segmentAzimuth[segmentNumber - 1], distance, 0);

      // location on the trace
      Location traceLocation = Locations.location(location1, dir);

      // get location at the top of the fault surface
      // Location topLocation;
      // if (traceLocation.depth() < depth) {
      // // vDistance = traceLocation.getDepth() - depth;
      // vDistance = depth - traceLocation.depth();
      // hDistance = vDistance / Math.tan(dipRad);
      // // dir = new LocationVector(vDistance, hDistance,
      // // aveDipDirection, 0);
      // // dir = new LocationVector(aveDipDirection, hDistance,
      // // vDistance);
      // dir = LocationVector.create(dipDirRad, hDistance, vDistance);
      // topLocation = Locations.location(traceLocation, dir);
      // } else
      // topLocation = traceLocation;

      // TODO above was improperly edited; buried traces were incorrectly
      // being projected doewn dip; upperSeisDepth was refactored
      // out but perhaps will have to be reintroduced
      Location topLocation = Location.create(traceLocation.lat(), traceLocation.lon(), depth);

      set(0, ith_col, topLocation);
      // if( D ) System.out.println(" (x,y) topLocation = (0, " +
      // ith_col + ") " + topLocation );

      // Loop over each row - calculating location at depth along the
      // fault trace
      ith_row = 1;
      while (ith_row < rows) {

        // if( D ) System.out.println(" ith_row = " + ith_row);

        // Calculate location at depth and put into grid
        hDistance = ith_row * gridSpacingCosAveDipRadians;
        // vDistance = -ith_row * gridSpacingSinAveDipRadians;
        vDistance = ith_row * gridSpacingSinAveDipRadians;

        // dir = new LocationVector(vDistance, hDistance,
        // aveDipDirection, 0);
        // dir = new LocationVector(aveDipDirection, hDistance,
        // vDistance);
        dir = LocationVector.create(dipDirRad, hDistance, vDistance);

        Location depthLocation = Locations.location(topLocation, dir);
        set(ith_row, ith_col, depthLocation);
        // if( D ) System.out.println(" (x,y) depthLocation = (" +
        // ith_row + ", " + ith_col + ") " + depthLocation );

        ith_row++;
      }
      ith_col++;
    }

  }

  // Surely the creation of a gridded surface can be easier...
  // ... and how on EARTH did we lose track of width which is defined for
  // EVERY fault ?!?!
  // TODO this is missing use of zTop
  // TODO revisit this needs to be compared against current
  // createEvenlyGriddedSurface()
  @Deprecated // until proven useful or better
  public void create(LocationList trace, double dip, double width, double spacing) {
    double dipRad = dip * TO_RAD;
    double dipDirRad = Faults.dipDirectionRad(trace);
    LocationList resampled = trace.resample(spacing);
    int nCol = resampled.size();
    // strike-parallel row count, NOT including trace
    int nRow = DoubleMath.roundToInt(width / spacing, HALF_UP);
    // TODO should this be +1 ??
    double dRow = width / nRow;
    setNumRowsAndNumCols(nRow, nCol);

    int iCol = 0;
    for (Location loc : resampled) {
      set(0, iCol, loc);
      double downDipDist = dRow;
      for (int iRow = 1; iRow <= nRow; iRow++) {
        LocationVector v = createWithPlunge(dipDirRad, dipRad, downDipDist);
        set(iRow, iCol, Locations.location(loc, v));
      }
      iCol++;
    }
  }

  /**
   * This resamples the trace into num subsections of equal length (final number
   * of points in trace is num+1). However, note that these subsections of are
   * equal length on the original trace, and that the final subsections will be
   * less than that if there is curvature in the original between the points
   * (e.g., corners getting cut).
   * @param trace
   * @param num - number of subsections
   */
  public static LocationList resampleTrace(LocationList trace, int num) {
    double resampInt = trace.length() / num;
    List<Location> resampLocs = Lists.newArrayList();
    resampLocs.add(trace.first()); // add the first location
    double remainingLength = resampInt;
    Location lastLoc = trace.first();
    int NextLocIndex = 1;
    while (NextLocIndex < trace.size()) {
      Location nextLoc = trace.get(NextLocIndex);
      double length = linearDistanceFast(lastLoc, nextLoc);
      if (length > remainingLength) {
        LocationVector dirSrc = LocationVector.create(lastLoc, nextLoc);
        double hDist = dirSrc.horizontal() * remainingLength / length;
        double vDist = dirSrc.vertical() * remainingLength / length;
        LocationVector dir = LocationVector.create(dirSrc.azimuth(), hDist, vDist);
        Location loc = location(lastLoc, dir);
        resampLocs.add(loc);
        lastLoc = loc;
        remainingLength = resampInt;
        // Next location stays the same
      } else {
        lastLoc = nextLoc;
        NextLocIndex += 1;
        remainingLength -= length;
      }
    }

    // make sure we got the last one
    // (might be missed because of numerical precision issues?)
    double dist = linearDistanceFast(trace.last(), resampLocs.get(resampLocs.size() - 1));
    if (dist > resampInt / 2) resampLocs.add(trace.last());

    return LocationList.create(resampLocs);
  }

  /**
   * Override the parent with a version with fewer points
   */
  @Override
  public LocationList getPerimeter() {

    List<Location> topLocs = Lists.newArrayList();
    List<Location> botLocs = Lists.newArrayList();
    double lowerDepth = depth + width * Math.sin(dipRad);

    for (Location traceLoc : trace) {

      Location topLoc = Location.create(traceLoc.lat(), traceLoc.lon(), depth);
      topLocs.add(topLoc);

      double vDistance = lowerDepth - depth; // traceLoc.depth();
      double hDistance = vDistance / Math.tan(dipRad);
      // dir = new LocationVector(aveDipDirection, hDistance, vDistance);
      LocationVector dir = LocationVector.create(dipDirRad, hDistance, vDistance);
      Location botLoc = Locations.location(traceLoc, dir);
      botLocs.add(botLoc);
    }

    // now make and close the list
    List<Location> perimiter = Lists.newArrayList();
    perimiter.addAll(topLocs);
    perimiter.addAll(Lists.reverse(botLocs));
    perimiter.add(topLocs.get(0));
    return LocationList.create(perimiter);
  }

  @Override
  public double dipDirection() {
    return dipDirRad * TO_DEG;
  }

  @Override
  public double dip() {
    return dipRad * TO_DEG;
  }

  @Override
  public double dipRad() {
    return dipRad;
  }

  @Override
  public double depth() {
    return depth;
  }

  @Override
  public double strike() {
    return Faults.strike(trace);
  }

  @Override
  public Location centroid() {
    return centroid;
  }

}
