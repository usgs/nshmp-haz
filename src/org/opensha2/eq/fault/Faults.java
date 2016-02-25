package org.opensha2.eq.fault;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.Math.sin;
import static org.opensha2.data.Data.checkInRange;
import static org.opensha2.geo.GeoTools.PI_BY_2;
import static org.opensha2.geo.GeoTools.TO_RAD;
import static org.opensha2.geo.GeoTools.TWOPI;
import static org.opensha2.geo.Locations.azimuth;
import static org.opensha2.geo.Locations.azimuthRad;
import static org.opensha2.geo.Locations.horzDistance;
import static org.opensha2.geo.Locations.linearDistanceFast;
import static org.opensha2.geo.Locations.location;

import java.util.ArrayList;
import java.util.List;

import org.opensha2.data.Data;
import org.opensha2.geo.Location;
import org.opensha2.geo.LocationList;
import org.opensha2.geo.LocationVector;
import org.opensha2.geo.Locations;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Range;

/**
 * Fault utilities.
 * 
 * @author Peter Powers
 */
public final class Faults {

	/**
	 * The {@link Range} of valid fault strikes: [0 ‥ 360]°, inclusive.
	 */
	public static final Range<Double> STRIKE_RANGE = Range.closed(0.0, 360.0);

	/**
	 * The {@link Range} of valid fault dips: [0 ‥ 90]°, inclusive.
	 */
	public static final Range<Double> DIP_RANGE = Range.closed(0.0, 90.0);

	/**
	 * The {@link Range} of valid fault rakes: [180 ‥ 180]°, inclusive.
	 */
	public static final Range<Double> RAKE_RANGE = Range.closed(-180.0, 180.0);

	// TODO adjust Faults.CRUSTAL_DEPTH_RANGE CB14 restricts to 20 km
	// and the PEER database is pretty comprehensive
	
	/**
	 * The {@link Range} of valid crustal rupture depths: [0 ‥ 40] km,
	 * inclusive.
	 */
	public static final Range<Double> CRUSTAL_DEPTH_RANGE = Range.closed(0.0, 40.0);

	/**
	 * The {@link Range} of valid crustal rupture widths: (0 ‥ 60] km,
	 * exclusive, inclusive.
	 */
	public static final Range<Double> CRUSTAL_WIDTH_RANGE = Range.openClosed(0.0, 60.0);

	/**
	 * The {@link Range} of valid intraslab rupture depths: [20 ‥ 700] km,
	 * inclusive.
	 */
	public static final Range<Double> SLAB_DEPTH_RANGE = Range.closed(20.0, 700.0);

	/**
	 * The {@link Range} of valid interface rupture depths: [0 ‥ 60] km,
	 * inclusive.
	 */
	public static final Range<Double> INTERFACE_DEPTH_RANGE = Range.closed(0.0, 60.0);

	/**
	 * The {@link Range} of valid interface rupture widths: (0 ‥ 200] km,
	 * exclusive, inclusive.
	 */
	public static final Range<Double> INTERFACE_WIDTH_RANGE = Range.openClosed(0.0, 200.0);

	/**
	 * Verifies that {@code dip} is within {@link #DIP_RANGE}.
	 * 
	 * @param dip to validate
	 * @return the supplied dip for use inline
	 * @throws IllegalArgumentException if {@code dip} is out of range
	 * @see Data#checkInRange(Range, String, double)
	 */
	public static double validateDip(double dip) {
		return checkInRange(DIP_RANGE, "Dip", dip);
	}

	/**
	 * Verifies that {@code strike} is within {@link #STRIKE_RANGE}.
	 * 
	 * @param strike to validate
	 * @return the supplied strike for use inline
	 * @throws IllegalArgumentException if {@code strike} is out of range
	 * @see Data#checkInRange(Range, String, double)
	 */
	public static double validateStrike(double strike) {
		return checkInRange(STRIKE_RANGE, "Strike", strike);
	}

	/**
	 * Verifies that {@code rake} is within {@link #RAKE_RANGE}.
	 * 
	 * @param rake to validate
	 * @return the supplied rake for use inline
	 * @throws IllegalArgumentException if {@code rake} is out of range
	 * @see Data#checkInRange(Range, String, double)
	 */
	public static double validateRake(double rake) {
		return checkInRange(RAKE_RANGE, "Rake", rake);
	}

	/**
	 * Verifies that {@code depth} is within {@link #CRUSTAL_DEPTH_RANGE}.
	 * 
	 * @param depth to validate (positive down)
	 * @return the supplied depth for use inline
	 * @throws IllegalArgumentException if {@code depth} is out of range
	 * @see Data#checkInRange(Range, String, double)
	 */
	public static double validateDepth(double depth) {
		return checkInRange(CRUSTAL_DEPTH_RANGE, "Depth", depth);
	}

	/**
	 * Verifies that {@code depth} value is within {@link #SLAB_DEPTH_RANGE}.
	 * 
	 * @param depth to validate (positive down)
	 * @return the supplied depth for use inline
	 * @throws IllegalArgumentException if {@code depth} is out of range
	 * @see Data#checkInRange(Range, String, double)
	 */
	public static double validateSlabDepth(double depth) {
		return checkInRange(SLAB_DEPTH_RANGE, "Subduction Slab Depth", depth);
	}

	/**
	 * Verifies that {@code depth} is within {@link #INTERFACE_DEPTH_RANGE}.
	 * 
	 * @param depth to validate (positive down)
	 * @return the supplied depth for use inline
	 * @throws IllegalArgumentException if {@code depth} is out of range
	 * @see Data#checkInRange(Range, String, double)
	 */
	public static double validateInterfaceDepth(double depth) {
		return checkInRange(INTERFACE_DEPTH_RANGE, "Subduction Interface Depth", depth);
	}

	/**
	 * Verifies that {@code width} is within {@link #CRUSTAL_WIDTH_RANGE}.
	 * 
	 * @param width to validate
	 * @return the supplied width for use inline
	 * @throws IllegalArgumentException if {@code width} is out of range
	 * @see Data#checkInRange(Range, String, double)
	 */
	public static double validateWidth(double width) {
		return checkInRange(CRUSTAL_WIDTH_RANGE, "Width", width);
	}

	/**
	 * Verifies that {@code width} is within {@link #INTERFACE_WIDTH_RANGE}.
	 * 
	 * @param width to validate
	 * @return the supplied width for use inline
	 * @throws IllegalArgumentException if {@code width} is out of range
	 * @see Data#checkInRange(Range, String, double)
	 */
	public static double validateInterfaceWidth(double width) {
		return checkInRange(INTERFACE_WIDTH_RANGE, "Subduction Interface Width", width);
	}

	/**
	 * Ensures that a {@code LocationList} contains at least two points and is
	 * not {@code null}.
	 * 
	 * @param trace
	 * @return the supplied trace for use inline
	 */
	public static LocationList validateTrace(LocationList trace) {
		checkArgument(checkNotNull(trace).size() > 1, "Trace must have at least 2 points");
		return trace;
	}

	// /**
	// * Checks that the rake angle fits within the definition<p> <code>-180 <=
	// * rake <= 180</code><p>
	// * @param rake Angle to validate
	// * @throws InvalidRangeException Thrown if not valid angle
	// */
	// public static void assertValidRake(double rake)
	// throws InvalidRangeException {
	//
	// if (rake < -180)
	// throw new InvalidRangeException(S3 +
	// "Rake angle cannot be less than -180");
	// if (rake > 180)
	// throw new InvalidRangeException(S3 +
	// "Rake angle cannot be greater than 180");
	// }
	//
	// /**
	// * Returns the given angle in the range <code>-180 <= rake <= 180</code>
	// *
	// * @param angle
	// */
	// public static double getInRakeRange(double angle) {
	// while (angle > 180)
	// angle -= 360;
	// while (angle < -180)
	// angle += 180;
	// return angle;
	// }

	/**
	 * This subdivides the given fault trace into sub-traces that have the
	 * length as given (or less). This assumes all fault trace points are at the
	 * same depth.
	 * @param faultTrace
	 * @param maxSubSectionLen Maximum length of each subsection
	 * @return a {@code List} of subsection traces
	 */
	public static List<LocationList> getEqualLengthSubsectionTraces(LocationList faultTrace,
			double maxSubSectionLen) {
		return getEqualLengthSubsectionTraces(faultTrace, maxSubSectionLen, 1);
	}

	/**
	 * This subdivides the given fault trace into sub-traces that have the
	 * length as given (or less). This assumes all fault trace points are at the
	 * same depth.
	 * @param faultTrace
	 * @param maxSubSectionLen Maximum length of each subsection
	 * @param minSubSections minimum number of sub sections to generate
	 * @return a {@code List} of subsection traces
	 */
	public static List<LocationList> getEqualLengthSubsectionTraces(LocationList faultTrace,
			double maxSubSectionLen, int minSubSections) {

		int numSubSections;
		List<LocationList> subSectionTraceList;

		// find the number of sub sections
		double numSubSec = faultTrace.length() / maxSubSectionLen;
		if (Math.floor(numSubSec) != numSubSec)
			numSubSections = (int) Math.floor(numSubSec) + 1;
		else
			numSubSections = (int) numSubSec;
		if (numSubSections < minSubSections) numSubSections = minSubSections;
		// find the length of each sub section
		double subSecLength = faultTrace.length() / numSubSections;
		double distance = 0, distLocs = 0;
		int numLocs = faultTrace.size();
		int index = 0;
		subSectionTraceList = Lists.newArrayList();
		Location prevLoc = faultTrace.get(index);
		while (index < numLocs && subSectionTraceList.size() < numSubSections) {
			// FaultTrace subSectionTrace = new
			// FaultTrace(faultTrace.name()+" "+(subSectionTraceList.size()+1));
			List<Location> subSectionLocs = Lists.newArrayList();
			subSectionLocs.add(prevLoc); // the first location
			++index;
			distance = 0;
			while (true && index < faultTrace.size()) {
				Location nextLoc = faultTrace.get(index);
				distLocs = horzDistance(prevLoc, nextLoc);
				distance += distLocs;
				if (distance < subSecLength) { // if sub section length is
												// greater than distance, then
												// get next point on trace
					prevLoc = nextLoc;
					subSectionLocs.add(prevLoc);
					++index;
				} else {
					// LocationVector direction = vector(prevLoc, nextLoc);
					// direction.setHorzDistance(subSecLength -
					// (distance - distLocs));
					LocationVector dirSrc = LocationVector.create(prevLoc, nextLoc);
					double hDist = subSecLength - (distance - distLocs);
					LocationVector direction = LocationVector.create(dirSrc.azimuth(), hDist,
						dirSrc.vertical());
					prevLoc = location(prevLoc, direction);
					subSectionLocs.add(prevLoc);
					--index;
					break;
				}
			}
			// TODO is name used in subTraces? Can we name traces after
			// returning list
			// String subsectionName = faultTrace.name() + " " +
			// (subSectionTraceList.size() + 1);
			// LocationList subSectionTrace =
			// LocationList.create(subsectionName,
			// LocationList.create(subSectionLocs));
			LocationList subSectionTrace = LocationList.create(subSectionLocs);
			subSectionTraceList.add(subSectionTrace);
		}
		return subSectionTraceList;
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
	 * This is a quick plot of the traces
	 * @param traces
	 */
	// public static void plotTraces(ArrayList<FaultTrace> traces) {
	// throw new RuntimeException(
	// "This doesn't work because our functions will reorder x-axis values"
	// +
	// "to monotonically increase (and remove duplicates - someone should fix this)");
	// /*
	// * ArrayList funcs = new ArrayList(); for(int t=0; t<traces.size();t++)
	// * { FaultTrace trace = traces.get(t); ArbitrarilyDiscretizedFunc
	// * traceFunc = new ArbitrarilyDiscretizedFunc(); for(int
	// * i=0;i<trace.size();i++) { Location loc= trace.getLocationAt(i);
	// * traceFunc.set(loc.getLongitude(), loc.getLatitude()); }
	// * traceFunc.setName(trace.getName()); funcs.add(traceFunc); }
	// * GraphWindow graph = new GraphWindow(funcs, "");
	// * ArrayList<PlotCurveCharacterstics> plotChars = new
	// * ArrayList<PlotCurveCharacterstics>(); /* plotChars.add(new
	// * PlotCurveCharacterstics
	// * (PlotColorAndLineTypeSelectorControlPanel.FILLED_CIRCLES,
	// * Color.BLACK, 4)); plotChars.add(new
	// * PlotCurveCharacterstics(PlotColorAndLineTypeSelectorControlPanel
	// * .SOLID_LINE, Color.BLUE, 2)); plotChars.add(new
	// * PlotCurveCharacterstics
	// * (PlotColorAndLineTypeSelectorControlPanel.SOLID_LINE, Color.BLUE,
	// * 1)); plotChars.add(new
	// * PlotCurveCharacterstics(PlotColorAndLineTypeSelectorControlPanel
	// * .SOLID_LINE, Color.BLUE, 1)); graph.setPlottingFeatures(plotChars);
	// * graph.setX_AxisLabel("Longitude"); graph.setY_AxisLabel("Latitude");
	// * graph.setTickLabelFontSize(12);
	// * graph.setAxisAndTickLabelFontSize(14); /* // to save files if(dirName
	// * != null) { String filename = ROOT_PATH+dirName+"/slipRates"; try {
	// * graph.saveAsPDF(filename+".pdf"); graph.saveAsPNG(filename+".png"); }
	// * catch (IOException e) { // TODO Auto-generated catch block
	// * e.printStackTrace(); } }
	// */
	//
	// }

	/**
	 * Returns an average of the given angles scaled by the distances between
	 * the corresponding locations. Note that this expects angles in degrees,
	 * and will return angles from 0 to 360 degrees.
	 * 
	 * @param locs locations for distance scaling
	 * @param angles angles in degrees corresponding to each pair of locations
	 */
	public static double getLengthBasedAngleAverage(LocationList locs, List<Double> angles) {
		Preconditions.checkArgument(locs.size() >= 2, "must have at least 2 locations!");
		Preconditions.checkArgument(angles.size() == locs.size() - 1,
			"must have exactly one fewer angles than location");

		ArrayList<Double> lengths = new ArrayList<Double>();

		for (int i = 1; i < locs.size(); i++)
			lengths.add(linearDistanceFast(locs.get(i), locs.get(i - 1)));

		return getScaledAngleAverage(lengths, angles);
	}

	/**
	 * Returns an average of the given angles scaled by the given scalars. Note
	 * that this expects angles in degrees, and will return angles from 0 to 360
	 * degrees.
	 * 
	 * @param scalars scalar weights for each angle (does not need to be
	 *        normalized)
	 * @param angles angles in degrees corresponding to each pair of locations
	 */
	public static double getScaledAngleAverage(List<Double> scalars, List<Double> angles) {
		Preconditions.checkArgument(scalars.size() >= 1, "must have at least 1 lengths!");
		Preconditions.checkArgument(angles.size() == scalars.size(),
			"must have exactly the same amount of lengths as angles");

		// see if we have an easy case, or a NaN
		if (angles.size() == 1) return angles.get(0);
		if (Double.isNaN(angles.get(0))) return Double.NaN;
		boolean equal = true;
		for (int i = 1; i < angles.size(); i++) {
			if (Double.isNaN(angles.get(i))) return Double.NaN;
			if (angles.get(i) != angles.get(0)) {
				equal = false;
			}
		}
		if (equal) return angles.get(0);

		double xdir = 0;
		double ydir = 0;
		for (int i = 0; i < scalars.size(); i++) {
			double scalar = scalars.get(i);
			double angle = angles.get(i);
			xdir += scalar * Math.cos(Math.toRadians(angle));
			ydir += scalar * Math.sin(Math.toRadians(angle));
		}

		double avg;

		if (xdir > 0 & ydir >= 0)
			avg = Math.toDegrees(Math.atan(ydir / xdir));
		else if (xdir > 0 & ydir < 0)
			avg = Math.toDegrees(Math.atan(ydir / xdir)) + 360;
		else if (xdir < 0)
			avg = Math.toDegrees(Math.atan(ydir / xdir)) + 180;
		else if (xdir == 0 & ydir > 0)
			avg = 90;
		else if (xdir == 0 & ydir < 0)
			avg = 270;
		else
			avg = 0; // if both xdir==0 & ydir=0

		while (avg > 360)
			avg -= 360;
		while (avg < 0)
			avg += 360;

		return avg;
	}

	/**
	 * Averages angles dealing with any -180/180 or 0/360 cut issues. Note that
	 * this expects angles in degrees, and will return angles from 0 to 360
	 * degrees.
	 * 
	 * @param angles
	 */
	public static double getAngleAverage(List<Double> angles) {
		ArrayList<Double> scalars = new ArrayList<Double>();
		for (int i = 0; i < angles.size(); i++)
			scalars.add(1d);
		return getScaledAngleAverage(scalars, angles);
	}

	/**
	 * Generic model for hypocentral depth returns a value that is halfway
	 * between the top and bottom of a fault, parameterized by its dip, width,
	 * and depth. This method performs no input validation.
	 * 
	 * @param dip of the fault plane
	 * @param width of the fault plane
	 * @param zTop depth to the fault plane
	 */
	public static double hypocentralDepth(double dip, double width, double zTop) {
		return zTop + sin(dip * TO_RAD) * width / 2.0;
	}

	/**
	 * Compute the strike in degrees of the supplied line, or trace, by
	 * connecting the first and last points in {@code locs}. Method forwards to
	 * {@link Locations#azimuth(Location, Location)}.
	 * 
	 * <p>This approach has been shown to be as accurate as length-weighted
	 * angle averaging and is significantly faster; see <a
	 * href="https://opensha.org/trac/wiki/StrikeDirectionMethods"
	 * >StrikeDirectionMethods</a> for more information.</p>
	 * 
	 * @param locs line for which to compute strike
	 * @return strike direction in the range [0°, 360°)
	 * @see #strikeRad(LocationList)
	 */
	public static double strike(LocationList locs) {
		return strike(locs.first(), locs.last());
	}

	/**
	 * Compute the strike in degrees of the line connecting {@code p1} to
	 * {@code p2}.
	 * @param p1 starting {@code Location}
	 * @param p2 ending {@code Location}
	 * @return strike direction in the range [0°, 360°)
	 * @see #strikeRad(Location, Location)
	 */
	public static double strike(Location p1, Location p2) {
		return azimuth(p1, p2);
	}

	/**
	 * Compute the strike in radians of the supplied line, or trace, by
	 * connecting the first and last points in {@code locs}. Method forwards to
	 * {@link Locations#azimuth(Location, Location)}.
	 * 
	 * <p>This approach has been shown to be as accurate as length-weighted
	 * angle averaging and is significantly faster; see <a
	 * href="https://opensha.org/trac/wiki/StrikeDirectionMethods"
	 * >StrikeDirectionMethods</a> for more information.</p>
	 * 
	 * @param locs line for which to compute strike
	 * @return strike direction in the range [0, 2π)
	 * @see #strike(LocationList)
	 */
	public static double strikeRad(LocationList locs) {
		return strikeRad(locs.first(), locs.last());
	}

	/**
	 * Compute the strike in degrees of the line connecting {@code p1} to
	 * {@code p2}.
	 * @param p1 starting {@code Location}
	 * @param p2 ending {@code Location}
	 * @return strike direction in the range [0, 2π)
	 * @see #strike(Location, Location)
	 */
	public static double strikeRad(Location p1, Location p2) {
		return azimuthRad(p1, p2);
	}

	/*
	 * This returns the average strike (weight average by length). public double
	 * getAveStrike() { ArrayList<Double> azimuths = new ArrayList<Double>();
	 * for (int i = 1; i < size(); i++) { azimuths.add(Locations.azimuth(get(i -
	 * 1), get(i))); } return Faults.getLengthBasedAngleAverage(this, azimuths);
	 * }
	 */

	/**
	 * Returns the dip direction for the supplied line/trace assuming the
	 * right-hand rule (strike + 90°).
	 * 
	 * @param locs line for which to compute dip direction
	 * @return dip direction in the range 0° and 360°)
	 */
	public static double dipDirection(LocationList locs) {
		return dipDirection(strike(locs));
	}

	public static double dipDirectionRad(LocationList locs) {
		return dipDirectionRad(strikeRad(locs));
	}

	public static double dipDirection(Location p1, Location p2) {
		return dipDirection(strike(p1, p2));
	}

	public static double dipDirectionRad(Location p1, Location p2) {
		return dipDirectionRad(strikeRad(p1, p2));
	}

	public static double dipDirection(double strike) {
		return (strike + 90.0) % 360.0;
	}

	public static double dipDirectionRad(double strikeRad) {
		return (strikeRad + PI_BY_2) % TWOPI;
	}

	/* <b>x</b>-axis unit normal vector [1,0,0] */
	private static final double[] VX_UNIT_NORMAL = { 1.0, 0.0, 0.0 };
	/* <b>y</b>-axis unit normal vector [0,1,0] */
	private static final double[] VY_UNIT_NORMAL = { 0.0, 1.0, 0.0 };
	/* <b>z</b>-axis unit normal vector [0,0,1] */
	private static final double[] VZ_UNIT_NORMAL = { 0.0, 0.0, 1.0 };

	/**
	 * Calculates a slip vector from strike, dip, and rake information provided.
	 * @param strikeDipRake array
	 * @return double[x,y,z] array for slip vector.
	 */
	public static double[] getSlipVector(double[] strikeDipRake) {
		// start with y-axis unit normal on a horizontal plane
		double[] startVector = VY_UNIT_NORMAL;
		// rotate rake amount about z-axis (negative axial rotation)
		double[] rakeRotVector = vectorMatrixMultiply(zAxisRotMatrix(-strikeDipRake[2]),
			startVector);
		// rotate dip amount about y-axis (negative axial rotation)
		double[] dipRotVector = vectorMatrixMultiply(yAxisRotMatrix(-strikeDipRake[1]),
			rakeRotVector);
		// rotate strike amount about z-axis (positive axial rotation)
		double[] strikeRotVector = vectorMatrixMultiply(zAxisRotMatrix(strikeDipRake[0]),
			dipRotVector);
		return strikeRotVector;
	}

	/*
	 * Multiplies the vector provided with a matrix. Useful for rotations.
	 * 
	 * @param matrix double[][] matrix (likely one of the rotation matrices from
	 * this class).
	 * 
	 * @param vector double[x,y,z] to be modified.
	 */
	private static double[] vectorMatrixMultiply(double[][] matrix, double[] vector) {
		double[] rotatedVector = new double[3];
		for (int i = 0; i < 3; i++) {
			rotatedVector[i] = vector[0] * matrix[i][0] + vector[1] * matrix[i][1] + vector[2] *
				matrix[i][2];
		}
		return rotatedVector;
	}

	/*
	 * Returns a rotation matrix about the x axis in a right-handed coordinate
	 * system for a given theta. Note that these are coordinate transformations
	 * and that a positive (anticlockwise) rotation of a vector is the same as a
	 * negative rotation of the reference frame.
	 * 
	 * @param theta axial rotation in degrees.
	 * 
	 * @return double[][] rotation matrix.
	 */
	private static double[][] xAxisRotMatrix(double theta) {
		// @formatter:off
		double thetaRad = Math.toRadians(theta);
		double[][] rotMatrix= {{ 1.0 ,                 0.0 ,                0.0 },
				{ 0.0 ,  Math.cos(thetaRad) , Math.sin(thetaRad) },
				{ 0.0 , -Math.sin(thetaRad) , Math.cos(thetaRad) }};
		return rotMatrix;
		// @formatter:on
	}

	/*
	 * Returns a rotation matrix about the y axis in a right-handed coordinate
	 * system for a given theta. Note that these are coordinate transformations
	 * and that a positive (anticlockwise) rotation of a vector is the same as a
	 * negative rotation of the reference frame.
	 * 
	 * @param theta axial rotation in degrees.
	 * 
	 * @return double[][] rotation matrix.
	 */
	private static double[][] yAxisRotMatrix(double theta) {
		// @formatter:off
		double thetaRad = Math.toRadians(theta);
		double[][] rotMatrix= {{ Math.cos(thetaRad) , 0.0 , -Math.sin(thetaRad) },
				{                0.0 , 1.0 ,                 0.0 },
				{ Math.sin(thetaRad) , 0.0 ,  Math.cos(thetaRad) }};
		return rotMatrix;
		// @formatter:on
	}

	/*
	 * Returns a rotation matrix about the z axis in a right-handed coordinate
	 * system for a given theta. Note that these are coordinate transformations
	 * and that a positive (anticlockwise) rotation of a vector is the same as a
	 * negative rotation of the reference frame.
	 * 
	 * @param theta axial rotation in degrees.
	 * 
	 * @return double[][] rotation matrix.
	 */
	private static double[][] zAxisRotMatrix(double theta) {
		// @formatter:off
		double thetaRad = Math.toRadians(theta);
		double[][] rotMatrix= {{  Math.cos(thetaRad) , Math.sin(thetaRad) , 0.0 },
				{ -Math.sin(thetaRad) , Math.cos(thetaRad) , 0.0 },
				{                 0.0 ,                0.0 , 1.0 }};
		return rotMatrix;
		// @formatter:on
	}

}
