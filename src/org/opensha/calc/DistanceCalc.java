package org.opensha.calc;

import java.util.concurrent.Callable;

import org.opensha.eq.fault.surface.IndexedFaultSurface;
import org.opensha.eq.forecast.Distances;
import org.opensha.geo.Location;

/**
 * Computes the different distances to a source.
 * @author Peter Powers
 */
final class DistanceCalc implements Callable<Distances> {

	private final IndexedFaultSurface surface;
	private final Location loc;
	
	DistanceCalc(IndexedFaultSurface surface, Location loc) {
		this.surface = surface;
		this.loc = loc;
	}
	
	@Override
	public Distances call() throws Exception {
		return surface.distanceTo(loc);
	}

}
