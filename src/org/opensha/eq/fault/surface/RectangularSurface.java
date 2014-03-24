package org.opensha.eq.fault.surface;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import static org.opensha.geo.GeoTools.TO_RAD;

import org.opensha.eq.fault.Faults;
import org.opensha.eq.forecast.Distances;
import org.opensha.geo.Location;
import org.opensha.geo.LocationVector;
import org.opensha.geo.Locations;

/**
 * Add comments here
 *
 * @author Peter Powers
 */
public class RectangularSurface implements RuptureSurface {

	// TODO because we don't really need a lot of the details required by RuptureSurface,
	// let's compute these things on every call rather than taking up memory with stored data
	// most are required once to build disntance calc transforms
	
	Location p1;
	Location p2;
	double dip;
	double width;
	Location centroid;
	
	RectangularSurface(Location p1, Location p2, double dip, double width) {
		checkArgument(checkNotNull(p1).equals(checkNotNull(p2)));
		checkArgument(p1.depth() != p2.depth(),
			"Depths are different [%s, %s]", p1.depth(), p2.depth());
		
		this.dip = dip;
		this.width = width;
		centroid = calcCentroid();
	}
	
	// @formatter:off
	@Override public double strike() { return Locations.azimuth(p1, p2); }
	@Override public double dip() { return dip; }
	@Override public double dipDirection() { return Faults.dipDirection(p1, p2); }
	@Override public double length() { return Locations.horzDistance(p1, p2); }
	@Override public double width() { return width; }
	@Override public double area() { return length() * width; }
	@Override public double depth() { return p1.depth(); }
	@Override public Location centroid() { return centroid; }
	// @formatter:on
	
	@Override
	public Distances distanceTo(Location loc) {
		
		return null;
		
		
		// TODO do nothing

	}
	
	private Location calcCentroid() {
		double strikeRad = Faults.strikeRad(p1, p2);
		Location traceCenter = Locations.location(p1, strikeRad, length() / 2);
		double dipDirRad = Faults.dipDirectionRad(strikeRad);
		LocationVector toCentroid = LocationVector.createWithPlunge(dipDirRad,
			dip * TO_RAD, width / 2);
		return Locations.location(traceCenter, toCentroid);
	}

}
