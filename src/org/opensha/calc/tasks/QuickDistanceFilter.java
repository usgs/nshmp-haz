package org.opensha.calc.tasks;

import java.util.concurrent.Callable;

import org.opensha.eq.forecast.FaultSource;
import org.opensha.geo.Location;

/**
 * Filters ruptures by their quick minimum distance to a site.
 * @author Peter Powers
 */
final class QuickDistanceFilter implements Callable<FaultSource> {

	private final FaultSource src;
	private final Location loc;
	private final double dist;

	QuickDistanceFilter(FaultSource src, Location loc, double dist) {
		this.src = src;
		this.loc = loc;
		this.dist = dist;
	}

	@Override
	public FaultSource call() throws Exception {
		return src.getMinDistance(loc) <= dist ? src : null;
	}

}
