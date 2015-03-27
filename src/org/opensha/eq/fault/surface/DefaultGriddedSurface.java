package org.opensha.eq.fault.surface;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static java.math.RoundingMode.HALF_UP;
import static org.opensha.data.DataUtils.validate;
import static org.opensha.eq.fault.Faults.validateDepth;
import static org.opensha.eq.fault.Faults.validateDip;
import static org.opensha.eq.fault.Faults.validateStrike;
import static org.opensha.eq.fault.Faults.validateTrace;
import static org.opensha.eq.fault.Faults.validateInterfaceWidth;
import static org.opensha.geo.LocationVector.createWithPlunge;
import static org.opensha.geo.Locations.linearDistanceFast;
import static org.opensha.geo.Locations.location;

import static org.opensha.geo.GeoTools.*;

import java.util.Iterator;
import java.util.List;

import org.opensha.eq.fault.Faults;
import org.opensha.geo.GeoTools;
import org.opensha.geo.Location;
import org.opensha.geo.LocationList;
import org.opensha.geo.LocationVector;
import org.opensha.geo.Locations;

import com.google.common.collect.Lists;
import com.google.common.collect.Range;
import com.google.common.math.DoubleMath;

/**
 * <b>Title:</b> DefaultGriddedSurface. <br> <b>Description: This creates an
 * GriddedSurface representation of the fault using a scheme described by Mark
 * Stirling to Ned Field in 2001, where grid points are projected down dip at an
 * angle perpendicular to the end-points of the trace (or in dipDir if provided
 * using the appropriate constructor). </b> <br>
 * @author Ned Field.
 * 
 *         TODO doc trace assumed to be at depth=0? TODO do trace depths all
 *         need to be the same; condidtion used to be imposed in
 *         assertValidState
 */

public class DefaultGriddedSurface extends AbstractGriddedSurface {

	private final LocationList trace;
	private final double depth;
	// private double lowerDepth;// = Double.NaN;
	private final double dipRad;
	private final double dipDirRad;
	private final double width;

	// surface is initialized with a dip direction in radians; this may
	// be normal to Faults.strike(trace), but may not be; in any event,
	// we do not want to recompute it internally.

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
		// double downDipWidth = (lowerDepth-depth)/Math.sin(dip *
		// GeoTools.TO_RAD);
		this.dipSpacing = width / Math.ceil(width / dipSpacing);

		// set(trace, dip, depth, lowerDepth, strikeSpacing, dipSpacing);
		// this.lowerDepth = lowerDepth;
		// this.strikeSpacing = gridSpacingAlong;
		// this.gridSpacingDown = dipSpacing;
		// this.sameGridSpacing = true;
		// if(dipSpacing != gridSpacingAlong) sameGridSpacing = false;

		createEvenlyGriddedSurface();
	}

	// private void set(LocationList faultTrace, double dip, double depth,
	// double lowerDepth, double strikeSpacing, double dipSpacing) {
	// this.trace =faultTrace;
	// this.aveDip =dip;
	// this.upperSeismogenicDepth = depth;
	// this.lowerSeismogenicDepth =lowerDepth;
	// this.gridSpacingAlong = strikeSpacing;
	// this.gridSpacingDown = dipSpacing;
	// this.sameGridSpacing = true;
	// if(dipSpacing != strikeSpacing) sameGridSpacing = false;
	// }
	//

	// private void assertValidState() {

	// TODO revisit; should only need to validate derived values; all value
	// from constructor should be valid

	// checkNotNull(trace, "Fault Trace is null");
	// if( trace == null ) throw new FaultException(C + "Fault Trace is null");

	// Faults.validateDip(dip);
	// Faults.validateDepth(lowerDepth);
	// Faults.validateDepth(depth);
	// checkArgument(depth < lowerDepth);

	// checkArgument(!Double.isNaN(strikeSpacing), "invalid gridSpacing");
	// if( strikeSpacing == Double.NaN ) throw new FaultException(C +
	// "invalid gridSpacing");

	// double depth = trace.first().depth();
	// checkArgument(depth <= depth,
	// "depth on trace locations %s must be <= upperSeisDepth %s",
	// depth, depth);
	// if(depth > depth)
	// throw new FaultException(C +
	// "depth on trace locations must be < upperSeisDepth");

	// for (Location loc : trace) {
	// if (loc.depth() != depth) {
	// checkArgument(loc.depth() == depth,
	// "All depth on trace locations must be equal");
	// // throw new FaultException(C +
	// ":All depth on trace locations must be equal");
	// }
	// }
	// }

	/**
	 * Returns a new {@code DefaultGriddedSurface.Builder}.
	 */
	public static Builder builder() {
		return new Builder();
	}

	@SuppressWarnings("javadoc")
	public static class Builder {

		// build() may only be called once
		// use Doubles to ensure fields are initially null

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
			dipSpacing = validate(SPACING_RANGE, "Spacing", spacing);
			strikeSpacing = spacing;
			return this;
		}

		public Builder spacing(double dipSpacing, double strikeSpacing) {
			this.dipSpacing = validate(SPACING_RANGE, "Dip Spacing", dipSpacing);
			this.strikeSpacing = validate(SPACING_RANGE, "Strike Spacing", strikeSpacing);
			return this;
		}

		private void validateState(String id) {
			checkState(!built, "This %s instance as already been used", id);
			checkState(trace != null, "%s trace not set", id);
			checkState(dipRad != null, "%s dip not set", id);
			checkState(depth != null, "%s depth not set", id);
			
			checkState((width != null) ^ (lowerDepth != null), "%s width or lowerDepth not set", id);
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

		// if( D ) System.out.println("Starting createEvenlyGriddedSurface");

		// assertValidState();

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
		// double aveDipDirectionRad;
		// // Find ave dip direction (defined by end locations):
		// if( Double.isNaN(dipDir) ) {
		// aveDipDirectionRad = Faults.dipDirectionRad(trace);
		// } else {
		// aveDipDirectionRad = dipDir * GeoTools.TO_RAD;
		// }

		// if(D) System.out.println("\taveDipDirection = " + aveDipDirectionRad
		// * GeoTools.TO_DEG);

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

		// Calculate down dip width
		// double downDipWidth = (lowerDepth-depth)/Math.sin( avDipRadians );

		// Calculate the number of rows and columns
		int rows = 1 + Math.round((float) (width / dipSpacing));
		int cols = 1 + Math.round((float) (segmentCumLenth[numSegments - 1] / strikeSpacing));

		// if(D) System.out.println("numLocs: = " + trace.size());
		// if(D) System.out.println("numSegments: = " + numSegments);
		// if(D) System.out.println("firstLoc: = " + firstLoc);
		// if(D) System.out.println("lastLoc(): = " + lastLoc);
		// if(D) System.out.println("downDipWidth: = " + downDipWidth);
		// if(D) System.out.println("totTraceLength: = " + segmentCumLenth[
		// numSegments - 1]);
		// if(D) System.out.println("numRows: = " + rows);
		// if(D) System.out.println("numCols: = " + cols);

		// Create GriddedSurface
		int segmentNumber, ith_row, ith_col = 0;
		double distanceAlong, distance, hDistance, vDistance;
		// location object
		Location location1;

		// initialize the num of Rows and Cols for the container2d object that
		// holds
		setNumRowsAndNumCols(rows, cols);

		// Loop over each column - ith_col is ith grid step along the fault
		// trace
		// if( D ) System.out.println("   Iterating over columns up to " + cols
		// );
		while (ith_col < cols) {

			// if( D ) System.out.println("   ith_col = " + ith_col);

			// calculate distance from column number and grid spacing
			distanceAlong = ith_col * strikeSpacing;
			// if( D ) System.out.println("   distanceAlongFault = " +
			// distanceAlong);

			// Determine which segment distanceAlong is in
			segmentNumber = 1;
			while (segmentNumber <= numSegments &&
				distanceAlong > segmentCumLenth[segmentNumber - 1]) {
				segmentNumber++;
			}
			// put back in last segment if grid point has just barely stepped
			// off the end
			if (segmentNumber == numSegments + 1) segmentNumber--;

			// if( D ) System.out.println("   segmentNumber " + segmentNumber );

			// Calculate the distance from the last segment point
			if (segmentNumber > 1)
				distance = distanceAlong - segmentCumLenth[segmentNumber - 2];
			else
				distance = distanceAlong;
			// if( D ) System.out.println("   distanceFromLastSegPt " + distance
			// );

			// Calculate the grid location along fault trace and put into grid
			location1 = trace.get(segmentNumber - 1);
			// dir = new LocationVector(0, distance, segmentAzimuth[
			// segmentNumber - 1 ], 0);
			// dir = new LocationVector(segmentAzimuth[ segmentNumber - 1 ],
			// distance, 0);
			dir = LocationVector.create(segmentAzimuth[segmentNumber - 1], distance, 0);

			// location on the trace
			Location traceLocation = Locations.location(location1, dir);

			// get location at the top of the fault surface
//			Location topLocation;
//			if (traceLocation.depth() < depth) {
//				// vDistance = traceLocation.getDepth() - depth;
//				vDistance = depth - traceLocation.depth();
//				hDistance = vDistance / Math.tan(dipRad);
//				// dir = new LocationVector(vDistance, hDistance,
//				// aveDipDirection, 0);
//				// dir = new LocationVector(aveDipDirection, hDistance,
//				// vDistance);
//				dir = LocationVector.create(dipDirRad, hDistance, vDistance);
//				topLocation = Locations.location(traceLocation, dir);
//			} else
//				topLocation = traceLocation;

			// TODO above was improperly edited; buried traces were incorrectly
			// being projected doewn dip; upperSeisDepth was refactored
			// out but perhaps will have to be reintroduced
			Location topLocation = Location.create(traceLocation.lat(), traceLocation.lon(), depth);

			set(0, ith_col, Location.copyOf(topLocation));
			// if( D ) System.out.println("   (x,y) topLocation = (0, " +
			// ith_col + ") " + topLocation );

			// Loop over each row - calculating location at depth along the
			// fault trace
			ith_row = 1;
			while (ith_row < rows) {

				// if( D ) System.out.println("   ith_row = " + ith_row);

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
				set(ith_row, ith_col, Location.copyOf(depthLocation));
				// if( D ) System.out.println("    (x,y) depthLocation = (" +
				// ith_row + ", " + ith_col + ") " + depthLocation );

				ith_row++;
			}
			ith_col++;
		}

		// if( D ) System.out.println("Ending createEvenlyGriddedSurface");

		/*
		 * // test for fittings surfaces exactly
		 * if((float)(trace.getTraceLength()-getSurfaceLength()) != 0.0)
		 * System.out.println(trace.getName()+"\n\t"+
		 * "LengthDiff="+(float)(trace.getTraceLength()-getSurfaceLength())+
		 * "\t"
		 * +(float)trace.getTraceLength()+"\t"+(float)getSurfaceLength()+"\t"
		 * +getNumCols()+"\t"+(float)getGridSpacingAlongStrike()+
		 * "\n\tWidthDiff="+(float)(downDipWidth-getSurfaceWidth())
		 * +"\t"+(float)
		 * (downDipWidth)+"\t"+(float)getSurfaceWidth()+"\t"+getNumRows
		 * ()+"\t"+(float)getGridSpacingDownDip());
		 */
	}

	// Surely the creation of a gridded surface can be easier...
	// ... and how on EARTH did we lose track of width which is defined for
	// EVERY fault ?!?!
	// TODO this is missing use of zTop
	// TODO revisit this needs to be compared against current
	// createEvenlyGriddedSurface()
	public void create(LocationList trace, double dip, double width, double spacing) {
		double dipRad = dip * TO_RAD;
		double dipDirRad = Faults.dipDirectionRad(trace);
		LocationList resampled = LocationList.resampledFrom(trace, spacing);
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
	 * This resamples the trace into num subsections of equal length (final
	 * number of points in trace is num+1). However, note that these subsections
	 * of are equal length on the original trace, and that the final subsections
	 * will be less than that if there is curvature in the original between the
	 * points (e.g., corners getting cut).
	 * @param trace
	 * @param num - number of subsections
	 */
	public static LocationList resampleTrace(LocationList trace, int num) {
		double resampInt = trace.length() / num;
		// FaultTrace resampTrace = new FaultTrace("resampled "+trace.name());
		List<Location> resampLocs = Lists.newArrayList();
		resampLocs.add(trace.first()); // add the first location
		double remainingLength = resampInt;
		Location lastLoc = trace.first();
		int NextLocIndex = 1;
		while (NextLocIndex < trace.size()) {
			Location nextLoc = trace.get(NextLocIndex);
			double length = linearDistanceFast(lastLoc, nextLoc);
			if (length > remainingLength) {
				// set the point
				// LocationVector dir = vector(lastLoc, nextLoc);
				// dir.setHorzDistance(dir.getHorzDistance() * remainingLength /
				// length);
				// dir.setVertDistance(dir.getVertDistance() * remainingLength /
				// length);
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

		// make sure we got the last one (might be missed because of numerical
		// precision issues?)
		double dist = linearDistanceFast(trace.last(), resampLocs.get(resampLocs.size() - 1));
		if (dist > resampInt / 2) resampLocs.add(trace.last());

		/* Debugging Stuff **************** */
		/*
		 * // write out each to check System.out.println("RESAMPLED"); for(int
		 * i=0; i<resampTrace.size(); i++) { Location l =
		 * resampTrace.getLocationAt(i);
		 * System.out.println(l.getLatitude()+"\t"+
		 * l.getLongitude()+"\t"+l.getDepth()); }
		 * 
		 * System.out.println("ORIGINAL"); for(int i=0; i<trace.size(); i++) {
		 * Location l = trace.getLocationAt(i);
		 * System.out.println(l.getLatitude(
		 * )+"\t"+l.getLongitude()+"\t"+l.getDepth()); }
		 * 
		 * // write out each to check
		 * System.out.println("target resampInt="+resampInt+"\tnum sect="+num);
		 * System.out.println("RESAMPLED"); double ave=0, min=Double.MAX_VALUE,
		 * max=Double.MIN_VALUE; for(int i=1; i<resampTrace.size(); i++) {
		 * double d = Locations.getTotalDistance(resampTrace.getLocationAt(i-1),
		 * resampTrace.getLocationAt(i)); ave +=d; if(d<min) min=d; if(d>max)
		 * max=d; } ave /= resampTrace.size()-1;
		 * System.out.println("ave="+ave+"\tmin="
		 * +min+"\tmax="+max+"\tnum pts="+resampTrace.size());
		 * 
		 * 
		 * System.out.println("ORIGINAL"); ave=0; min=Double.MAX_VALUE;
		 * max=Double.MIN_VALUE; for(int i=1; i<trace.size(); i++) { double d =
		 * Locations.getTotalDistance(trace.getLocationAt(i-1),
		 * trace.getLocationAt(i)); ave +=d; if(d<min) min=d; if(d>max) max=d; }
		 * ave /= trace.size()-1;
		 * System.out.println("ave="+ave+"\tmin="+min+"\tmax="
		 * +max+"\tnum pts="+trace.size());
		 * 
		 * /* End of debugging stuff *******************
		 */

		// TODO is resampled trace name used? can't it be acquired from a
		// wrapping source?
		// return FaultTrace.create("resampled " + trace.name(),
		// LocationList.create(resampLocs));
		return LocationList.create(resampLocs);
	}

	/**
	 * Override the parent with a version with fewer points
	 */
	@Override public LocationList getPerimeter() {

		// LocationList topTrace = new LocationList();
		List<Location> topLocs = Lists.newArrayList();
		// LocationList botTrace = new LocationList();
		List<Location> botLocs = Lists.newArrayList();
		// final double avDipRadians = dip * GeoTools.TO_RAD;
		// double aveDipDirectionRad;
		// if( Double.isNaN(dipDir) ) {
		// aveDipDirectionRad = Faults.dipDirectionRad(trace);
		// } else {
		// aveDipDirectionRad = dipDir * GeoTools.TO_RAD;
		// }
		double lowerDepth = depth + width * Math.sin(dipRad);

		for (Location traceLoc : trace) {
			
//			// TODO ignoring seismogenic depth for now
//			double vDistance = upperSeisDepth - traceLoc.depth();
//			double hDistance = vDistance / Math.tan(dipRad);
//			
//			// LocationVector dir = new LocationVector(aveDipDirection, hDistance, vDistance);
//			LocationVector dir = LocationVector.create(dipDirRad, hDistance, vDistance);
//			Location topLoc = Locations.location(traceLoc, dir);
//			topLocs.add(topLoc);

			Location topLoc = Location.create(traceLoc.lat(), traceLoc.lon(), depth);
			topLocs.add(topLoc);
			
			double vDistance = lowerDepth - depth; //traceLoc.depth();
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

//	/**
//	 * Maine method to test this class (found a bug using it)
//	 * @param args
//	 */
//	public static void main(String args[]) {
//
//		double test = 4 % 4.1;
//		System.out.println(test);
//		// double dip = 15;
//		// double depth = 9.1;
//		// double lowerDepth =15.2;
//		// double gridSpacing=1.0;
//		// FaultTrace trace = new FaultTrace("Great Valley 13");
//		// // TO SEE THE POTENTIAL BUG IN THIS CLASS, CHANGE VALUE OF
//		// "faultTraceDepth" to 0
//		// double faultTraceDepth = 0;
//		// trace.addLocation(new Location(36.3547, -120.358, faultTraceDepth));
//		// trace.addLocation(new Location(36.2671, -120.254, faultTraceDepth));
//		// trace.addLocation(new Location(36.1499, -120.114, faultTraceDepth));
//		// DefaultGriddedSurface griddedSurface = new
//		// DefaultGriddedSurface(trace, dip,
//		// depth, lowerDepth, gridSpacing);
//		// System.out.println("******Fault Trace*********");
//		// System.out.println(trace);
//		// Iterator it = griddedSurface.getLocationsIterator();
//		// System.out.println("*******Evenly Gridded Surface************");
//		// while(it.hasNext()){
//		// Location loc = (Location)it.next();
//		// System.out.println(loc.getLatitude()+","+loc.getLongitude()+","+loc.getDepth());
//		// }
//
//		// for N-S strike and E dip, this setup showed that prior to fixing
//		// Locations.getLocation() the grid of the fault actually
//		// starts to the left of the trace, rather than to the right.
//
//		/*
//		 * double dip = 30; double depth = 5; double lowerDepth = 15; double
//		 * gridSpacing=5; FaultTrace trace = new FaultTrace("Test");
//		 * trace.add(new Location(20.0, -120, 0)); trace.add(new Location(20.2,
//		 * -120, 0)); DefaultGriddedSurface griddedSurface = new
//		 * DefaultGriddedSurface(trace, dip, depth, lowerDepth,
//		 * gridSpacing); System.out.println("******Fault Trace*********");
//		 * System.out.println(trace); Iterator<Location> it =
//		 * griddedSurface.getLocationsIterator();
//		 * System.out.println("*******Evenly Gridded Surface************");
//		 * while(it.hasNext()){ Location loc = (Location)it.next();
//		 * System.out.println
//		 * (loc.getLatitude()+","+loc.getLongitude()+","+loc.getDepth()); }
//		 */
//	}

	@Override public double dipDirection() {
		return dipDirRad * TO_DEG;
	}

	@Override public double dip() {
		return dipRad * TO_DEG;
	}
	
	@Override public double dipRad() {
		return dipRad;
	}

	@Override public double depth() {
		return depth;
	}

	@Override public double strike() {
		return Faults.strike(trace);
	}

	@Override public Location centroid() {
		throw new UnsupportedOperationException("Implement me!");
		// TODO can this be moved up to abstract???
	}

	// TODO is this really necessary; compund rupture surface?
	// this is clutter and hides AbstractGriddedSurface.getUpperEdge()
	// that does what we want it to do when building a compound surface from
	// section surfaces that we know will have uniform zTop
//	@Override public LocationList getUpperEdge() {
//		// check that the location depths in trace are same as
//		// depth
//		double aveTraceDepth = 0;
//		for (Location loc : trace)
//			aveTraceDepth += loc.depth();
//		aveTraceDepth /= trace.size();
//		double diff = Math.abs(aveTraceDepth - depth); // km
//		if (diff < 0.001) return trace;
//		throw new RuntimeException(" method not yet implemented where depths in the " +
//			"trace differ from depth (and projecting will create " +
//			"loops for FrankelGriddedSurface projections; aveTraceDepth=" + aveTraceDepth +
//			"\tupperSeismogenicDepth=" + depth);
//	}

}
