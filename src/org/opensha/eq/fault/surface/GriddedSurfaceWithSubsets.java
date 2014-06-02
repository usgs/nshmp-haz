package org.opensha.eq.fault.surface;

import static org.opensha.geo.Locations.linearDistanceFast;
import static org.opensha.geo.Locations.location;
import static org.opensha.geo.LocationVector.createWithPlunge;
import static com.google.common.base.Preconditions.checkArgument;
import static java.math.RoundingMode.HALF_UP;

import java.math.RoundingMode;
import java.util.Iterator;
import java.util.List;

import org.opensha.eq.fault.Faults;
import org.opensha.eq.forecast.Distances;
import org.opensha.geo.GeoTools;
import org.opensha.geo.Location;
import org.opensha.geo.LocationList;
import org.opensha.geo.Locations;
import org.opensha.geo.LocationVector;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.math.DoubleMath;

/**
 * <b>Title:</b> GriddedSurfaceWithSubsets.   <br>
 * <b>Description: This creates an GriddedSurface
 * representation of the fault using a scheme described by Mark Stirling
 * to Ned Field in 2001, where grid points are projected down dip at
 * an angle perpendicular to the end-points of the faultTrace (or in aveDipDir
 * if provided using the appropriate constructor).
</b> <br>
 * @author Ned Field.
 */

public class GriddedSurfaceWithSubsets extends AbstractGriddedSurfaceWithSubsets {
	
	private LocationList faultTrace;
	private double upperSeismogenicDepth = Double.NaN;
	private double lowerSeismogenicDepth = Double.NaN;
	private double aveDip;

	private double aveDipDir = Double.NaN;


	/**
	 * This applies the  grid spacing exactly as given (trimming any remainder from the ends),
	 * and applies the ave-dip direction as computed from the faultTrace.
	 */
	public GriddedSurfaceWithSubsets(LocationList faultTrace, double aveDip, double upperSeismogenicDepth,
			double lowerSeismogenicDepth, double strikeSpacing, double dipSpacing) {

		double length = faultTrace.length();
		double gridSpacingAlong = length/Math.ceil(length/strikeSpacing);
		double downDipWidth = (lowerSeismogenicDepth-upperSeismogenicDepth)/Math.sin(aveDip * GeoTools.TO_RAD);
		double gridSpacingDown = downDipWidth/Math.ceil(downDipWidth/dipSpacing);

		set(faultTrace, aveDip, upperSeismogenicDepth, lowerSeismogenicDepth, gridSpacingAlong, gridSpacingDown);

		createEvenlyGriddedSurface();
	}

	private void set(LocationList faultTrace, double aveDip, double upperSeismogenicDepth,
			double lowerSeismogenicDepth, double gridSpacingAlong, double gridSpacingDown)	{
		this.faultTrace =faultTrace;
		this.aveDip =aveDip;
		this.upperSeismogenicDepth = upperSeismogenicDepth;
		this.lowerSeismogenicDepth =lowerSeismogenicDepth;
		this.gridSpacingAlong = gridSpacingAlong;
		this.gridSpacingDown = gridSpacingDown;
		this.sameGridSpacing = true;
		if(gridSpacingDown != gridSpacingAlong) sameGridSpacing = false;
	}


	private void assertValidState() {

		// TODO revisit; should only need to validate derived values; all value
		// from constructor should be valid
		
//		checkNotNull(faultTrace, "Fault Trace is null");
//		if( faultTrace == null ) throw new FaultException(C + "Fault Trace is null");

		Faults.validateDip(aveDip);
		Faults.validateDepth(lowerSeismogenicDepth);
		Faults.validateDepth(upperSeismogenicDepth);
		checkArgument(upperSeismogenicDepth < lowerSeismogenicDepth);
		
		checkArgument(!Double.isNaN(gridSpacingAlong), "invalid gridSpacing");
//		if( gridSpacingAlong == Double.NaN ) throw new FaultException(C + "invalid gridSpacing");

		double depth = faultTrace.first().depth();
		checkArgument(depth <= upperSeismogenicDepth,
			"depth on faultTrace locations %s must be <= upperSeisDepth %s",
			depth, upperSeismogenicDepth);
		//		if(depth > upperSeismogenicDepth)
//			throw new FaultException(C + "depth on faultTrace locations must be < upperSeisDepth");

		for (Location loc : faultTrace) {
			if (loc.depth() != depth) {
				checkArgument(loc.depth() == depth, "All depth on faultTrace locations must be equal");
//				throw new FaultException(C + ":All depth on faultTrace locations must be equal");
			}
		}
	}

	/**
	 * Creates the Stirling Gridded Surface from the Simple Fault Data
	 * @throws FaultException
	 */
	private void createEvenlyGriddedSurface() {

//		if( D ) System.out.println("Starting createEvenlyGriddedSurface");

		assertValidState();

		final int numSegments = faultTrace.size() - 1;
		final double avDipRadians = aveDip * GeoTools.TO_RAD;
		final double gridSpacingCosAveDipRadians = gridSpacingDown * Math.cos( avDipRadians );
		final double gridSpacingSinAveDipRadians = gridSpacingDown * Math.sin( avDipRadians );

		double[] segmentLenth = new double[numSegments];
		double[] segmentAzimuth = new double[numSegments]; // in radians
		double[] segmentCumLenth = new double[numSegments];

		double cumDistance = 0;
		int i = 0;

		Location firstLoc;
		Location lastLoc;
		double aveDipDirectionRad;
		// Find ave dip direction (defined by end locations):
			if( Double.isNaN(aveDipDir) ) {
				aveDipDirectionRad = Faults.dipDirectionRad(faultTrace);
			} else {
				aveDipDirectionRad = aveDipDir * GeoTools.TO_RAD;
			}

//			if(D) System.out.println("\taveDipDirection = " + aveDipDirectionRad * GeoTools.TO_DEG);


			// Iterate over each Location in Fault Trace
			// Calculate distance, cumulativeDistance and azimuth for
			// each segment
			Iterator<Location> it = faultTrace.iterator();
			firstLoc = it.next();
			lastLoc = firstLoc;
			Location loc = null;
			LocationVector dir = null;
			while( it.hasNext() ){

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
			double downDipWidth = (lowerSeismogenicDepth-upperSeismogenicDepth)/Math.sin( avDipRadians );

			// Calculate the number of rows and columns
			int rows = 1 + Math.round((float) (downDipWidth/gridSpacingDown));
			int cols = 1 + Math.round((float) (segmentCumLenth[numSegments - 1] / gridSpacingAlong));


//			if(D) System.out.println("numLocs: = " + faultTrace.size());
//			if(D) System.out.println("numSegments: = " + numSegments);
//			if(D) System.out.println("firstLoc: = " + firstLoc);
//			if(D) System.out.println("lastLoc(): = " + lastLoc);
//			if(D) System.out.println("downDipWidth: = " + downDipWidth);
//			if(D) System.out.println("totTraceLength: = " + segmentCumLenth[ numSegments - 1]);
//			if(D) System.out.println("numRows: = " + rows);
//			if(D) System.out.println("numCols: = " + cols);


			// Create GriddedSurface
			int segmentNumber, ith_row, ith_col = 0;
			double distanceAlong, distance, hDistance, vDistance;
			//location object
			Location location1;


			//initialize the num of Rows and Cols for the container2d object that holds
			setNumRowsAndNumCols(rows,cols);


			// Loop over each column - ith_col is ith grid step along the fault trace
//			if( D ) System.out.println("   Iterating over columns up to " + cols );
			while( ith_col < cols ){

//				if( D ) System.out.println("   ith_col = " + ith_col);

				// calculate distance from column number and grid spacing
				distanceAlong = ith_col * gridSpacingAlong;
//				if( D ) System.out.println("   distanceAlongFault = " + distanceAlong);

				// Determine which segment distanceAlong is in
				segmentNumber = 1;
				while( segmentNumber <= numSegments && distanceAlong > segmentCumLenth[ segmentNumber - 1] ){
					segmentNumber++;
				}
				// put back in last segment if grid point has just barely stepped off the end
				if( segmentNumber == numSegments+1) segmentNumber--;

//				if( D ) System.out.println("   segmentNumber " + segmentNumber );

				// Calculate the distance from the last segment point
				if ( segmentNumber > 1 ) distance = distanceAlong - segmentCumLenth[ segmentNumber - 2 ];
				else distance = distanceAlong;
//				if( D ) System.out.println("   distanceFromLastSegPt " + distance );

				// Calculate the grid location along fault trace and put into grid
				location1 = faultTrace.get( segmentNumber - 1 );
				//            dir = new LocationVector(0, distance, segmentAzimuth[ segmentNumber - 1 ], 0);
//				dir = new LocationVector(segmentAzimuth[ segmentNumber - 1 ], distance, 0);
				dir = LocationVector.create(segmentAzimuth[ segmentNumber - 1 ], distance, 0);

				// location on the trace
				Location traceLocation = Locations.location( location1, dir  );

				// get location at the top of the fault surface
				Location topLocation;
				if(traceLocation.depth() < upperSeismogenicDepth) {
					//                vDistance = traceLocation.getDepth() - upperSeismogenicDepth;
					vDistance = upperSeismogenicDepth - traceLocation.depth();
					hDistance = vDistance / Math.tan( avDipRadians );
					//                dir = new LocationVector(vDistance, hDistance, aveDipDirection, 0);
//					dir = new LocationVector(aveDipDirection, hDistance, vDistance);
					dir = LocationVector.create(aveDipDirectionRad, hDistance, vDistance);
					topLocation = Locations.location( traceLocation, dir );
				}
				else
					topLocation = traceLocation;

				set(0, ith_col, Location.copyOf(topLocation));
//				if( D ) System.out.println("   (x,y) topLocation = (0, " + ith_col + ") " + topLocation );

				// Loop over each row - calculating location at depth along the fault trace
				ith_row = 1;
				while(ith_row < rows){

//					if( D ) System.out.println("   ith_row = " + ith_row);

					// Calculate location at depth and put into grid
					hDistance = ith_row * gridSpacingCosAveDipRadians;
					//                vDistance = -ith_row * gridSpacingSinAveDipRadians;
					vDistance = ith_row * gridSpacingSinAveDipRadians;

					//                dir = new LocationVector(vDistance, hDistance, aveDipDirection, 0);
//					dir = new LocationVector(aveDipDirection, hDistance, vDistance);
					dir = LocationVector.create(aveDipDirectionRad, hDistance, vDistance);

					Location depthLocation = Locations.location( topLocation, dir );
					set(ith_row, ith_col, Location.copyOf(depthLocation));
//					if( D ) System.out.println("    (x,y) depthLocation = (" + ith_row + ", " + ith_col + ") " + depthLocation );

					ith_row++;
				}
				ith_col++;
			}

//			if( D ) System.out.println("Ending createEvenlyGriddedSurface");

			/*
        // test for fittings surfaces exactly
        if((float)(faultTrace.getTraceLength()-getSurfaceLength()) != 0.0)
        	System.out.println(faultTrace.getName()+"\n\t"+
        		"LengthDiff="+(float)(faultTrace.getTraceLength()-getSurfaceLength())+
        		"\t"+(float)faultTrace.getTraceLength()+"\t"+(float)getSurfaceLength()+"\t"+getNumCols()+"\t"+(float)getGridSpacingAlongStrike()+
        		"\n\tWidthDiff="+(float)(downDipWidth-getSurfaceWidth())
        		+"\t"+(float)(downDipWidth)+"\t"+(float)getSurfaceWidth()+"\t"+getNumRows()+"\t"+(float)getGridSpacingDownDip());
			 */
	}
	
	// Surely the creation of a gridded surface can be easier...
	// ... and how on EARTH did we lose track of width which is defined for EVERY fault ?!?!
	
	public void create(LocationList trace, double dip, double width, double spacing) {
		double dipRad = dip * GeoTools.TO_RAD;
		double dipDirRad = Faults.dipDirectionRad(trace);
		LocationList resampled = LocationList.resampledFrom(trace, spacing);
		int nCol = resampled.size();
		// strike-parallel row count, NOT including trace 
		int nRow = DoubleMath.roundToInt(width / spacing, HALF_UP);
		double dRow = width / nRow;
		setNumRowsAndNumCols(nRow, nCol);
		
		int iCol = 0;
		for (Location loc : resampled) {
			set(0, iCol, loc);
			double downDipDist = dRow;
			for (int iRow=1; iRow <= nRow; iRow++) {
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
	 * @return
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
//				LocationVector dir = vector(lastLoc, nextLoc);
//				dir.setHorzDistance(dir.getHorzDistance() * remainingLength /
//					length);
//				dir.setVertDistance(dir.getVertDistance() * remainingLength /
//					length);
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
		double dist = linearDistanceFast(
			trace.last(),
			resampLocs.get(resampLocs.size() - 1));
		if (dist > resampInt / 2)
			resampLocs.add(trace.last());

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
		 * double d =
		 * Locations.getTotalDistance(resampTrace.getLocationAt(i-1),
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

		// TODO is resampled trace name used? can't it be acquired from a wrapping source?
//		return FaultTrace.create("resampled " + trace.name(),
//			LocationList.create(resampLocs));
		return LocationList.create(resampLocs);
	}

	
	/**
	 * Override the parent with a version with fewer points
	 */
	@Override
	public LocationList getPerimeter() {
		
//		LocationList topTrace = new LocationList();
		List<Location> topLocs = Lists.newArrayList();
//		LocationList botTrace = new LocationList();
		List<Location> botLocs = Lists.newArrayList();
		final double avDipRadians = aveDip * GeoTools.TO_RAD;
		double aveDipDirectionRad;
		if( Double.isNaN(aveDipDir) ) {
			aveDipDirectionRad = Faults.dipDirectionRad(faultTrace);
		} else {
			aveDipDirectionRad = aveDipDir * GeoTools.TO_RAD;
		}
		for(Location traceLoc:faultTrace) {
			double vDistance = upperSeismogenicDepth - traceLoc.depth();
			double hDistance = vDistance / Math.tan( avDipRadians );
//			LocationVector dir = new LocationVector(aveDipDirection, hDistance, vDistance);
			LocationVector dir = LocationVector.create(aveDipDirectionRad, hDistance, vDistance);
			Location topLoc = Locations.location( traceLoc, dir );
			topLocs.add(topLoc);
			
			vDistance = lowerSeismogenicDepth - traceLoc.depth();
			hDistance = vDistance / Math.tan( avDipRadians );
//			dir = new LocationVector(aveDipDirection, hDistance, vDistance);
			dir = LocationVector.create(aveDipDirectionRad, hDistance, vDistance);
			Location botLoc = Locations.location( traceLoc, dir );
			botLocs.add(botLoc);
		}
		
		// now make and close the list
		List<Location> perimiter = Lists.newArrayList();
		perimiter.addAll(topLocs);
		perimiter.addAll(Lists.reverse(botLocs));
		perimiter.add(topLocs.get(0));
		return LocationList.create(perimiter);
	}



	/**
	 * Maine method to test this class (found a bug using it)
	 * @param args
	 */
	public static void main(String args[]) {

		double test = 4%4.1;
		System.out.println(test);
		//        double aveDip = 15;
		//        double upperSeismogenicDepth = 9.1;
		//        double lowerSeismogenicDepth =15.2;
		//        double gridSpacing=1.0;
		//        FaultTrace faultTrace = new FaultTrace("Great Valley 13");
		//        // TO SEE THE POTENTIAL BUG IN THIS CLASS, CHANGE VALUE OF "faultTraceDepth" to 0
		//        double faultTraceDepth = 0;
		//        faultTrace.addLocation(new Location(36.3547, -120.358, faultTraceDepth));
		//        faultTrace.addLocation(new Location(36.2671, -120.254, faultTraceDepth));
		//        faultTrace.addLocation(new Location(36.1499, -120.114, faultTraceDepth));
		//        GriddedSurfaceWithSubsets griddedSurface = new GriddedSurfaceWithSubsets(faultTrace, aveDip,
		//        		upperSeismogenicDepth, lowerSeismogenicDepth, gridSpacing);
		//        System.out.println("******Fault Trace*********");
		//        System.out.println(faultTrace);
		//        Iterator it = griddedSurface.getLocationsIterator();
		//        System.out.println("*******Evenly Gridded Surface************");
		//        while(it.hasNext()){
		//            Location loc = (Location)it.next();
		//            System.out.println(loc.getLatitude()+","+loc.getLongitude()+","+loc.getDepth());
		//        }

		// for N-S strike and E dip, this setup showed that prior to fixing
		// Locations.getLocation() the grid of the fault actually
		// starts to the left of the trace, rather than to the right.

		/*
        double aveDip = 30;
        double upperSeismogenicDepth = 5;
        double lowerSeismogenicDepth = 15;
        double gridSpacing=5;
        FaultTrace faultTrace = new FaultTrace("Test");
        faultTrace.add(new Location(20.0, -120, 0));
        faultTrace.add(new Location(20.2, -120, 0));
        GriddedSurfaceWithSubsets griddedSurface = new GriddedSurfaceWithSubsets(faultTrace, aveDip,
        		upperSeismogenicDepth, lowerSeismogenicDepth, gridSpacing);
        System.out.println("******Fault Trace*********");
        System.out.println(faultTrace);
        Iterator<Location> it = griddedSurface.getLocationsIterator();
        System.out.println("*******Evenly Gridded Surface************");
        while(it.hasNext()){
            Location loc = (Location)it.next();
            System.out.println(loc.getLatitude()+","+loc.getLongitude()+","+loc.getDepth());
        }
		 */
	}

	@Override
	public double dipDirection() {
		return aveDipDir;
	}

	@Override
	public double dip() {
		return aveDip;
	}

	@Override
	public double depth() {
		return upperSeismogenicDepth;
	}

	@Override
	public double strike() {
		return Faults.strike(faultTrace);
	}


	@Override
	public Location centroid() {
		throw new UnsupportedOperationException("Implement me!");
		// TODO can this be moved up to abstract???
	}

	// TODO is this really necessary; compund rupture surface?
	@Override
	public LocationList getUpperEdge() {
		// check that the location depths in faultTrace are same as
		// upperSeismogenicDepth
		double aveTraceDepth = 0;
		for (Location loc : faultTrace)
			aveTraceDepth += loc.depth();
		aveTraceDepth /= faultTrace.size();
		double diff = Math.abs(aveTraceDepth - upperSeismogenicDepth); // km
		if (diff < 0.001) return faultTrace;
		throw new RuntimeException(
			" method not yet implemented where depths in the " +
				"trace differ from upperSeismogenicDepth (and projecting will create " +
				"loops for FrankelGriddedSurface projections; aveTraceDepth=" +
				aveTraceDepth + "\tupperSeismogenicDepth=" +
				upperSeismogenicDepth);
	}

}
