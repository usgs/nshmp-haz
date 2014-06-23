package org.opensha.eq.forecast;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.opensha.data.DataUtils.validateWeight;
import static org.opensha.geo.Locations.horzDistanceFast;
import static org.opensha.util.TextUtils.validateName;

import java.util.Iterator;
import java.util.List;

import org.opensha.eq.fault.scaling.MagScalingType;
import org.opensha.geo.Location;

import com.google.common.base.Predicate;
import com.google.common.collect.Lists;

/**
 * Wrapper class for groups of related {@code FaultSource}s.
 *
 * @author Peter Powers
 * @see FaultSource
 */
public class FaultSourceSet extends AbstractSourceSet<FaultSource> {

	private final List<FaultSource> sources;
	
	private FaultSourceSet(String name, double weight, MagScalingType msrType,
		List<FaultSource> sources, GmmSet gmmSet) {
		super(name, weight, msrType, gmmSet);
		this.sources = sources;
	}
	
	@Override public Iterator<FaultSource> iterator() {
		return sources.iterator();
	}

	@Override public int size() {
		return sources.size();
	}

	@Override public SourceType type() {
		return SourceType.FAULT;
	}

	@Override public Predicate<FaultSource> distanceFilter(Location loc, double distance) {
		return new DistanceFilter(loc, distance);
	}
	
	static class DistanceFilter implements Predicate<FaultSource> {

		private static final String ID = "FaultSourceSet.DistanceFilter";
		final Location loc;
		final double distance;

		DistanceFilter(Location loc, double distance) {
			this.loc = loc;
			this.distance = distance;
		}

		@Override public boolean apply(FaultSource fs) {
			return horzDistanceFast(loc, fs.trace.first()) <= distance ||
				horzDistanceFast(loc, fs.trace.last()) <= distance;
		}
		
		@Override public String toString() {
			return ID + " [location: " + loc + ", distance: " + distance + "]";
		}
	}

	static class Builder {

		// build() may only be called once
		// use Doubles to ensure fields are initially null

		static final String ID = "FaultSourceSet.Builder";
		boolean built = false;

		String name;
		Double weight;
		MagScalingType magScaling;
		GmmSet gmmSet;
		List<FaultSource> sources = Lists.newArrayList();

		Builder name(String name) {
			this.name = validateName(name);
			return this;
		}
		
		Builder weight(double weight) {
			this.weight = validateWeight(weight);
			return this;
		}

		Builder gmms(GmmSet gmmSet) {
			this.gmmSet = checkNotNull(gmmSet);
			return this;
		}

		Builder magScaling(MagScalingType magScaling) {
			this.magScaling = checkNotNull(magScaling, "MagScalingType is null");
			return this;
		}
		
		Builder source(FaultSource source) {
			sources.add(checkNotNull(source, "FaultSource is null"));
			return this;
		}

		void validateState(String id) {
			checkState(!built, "This %s instance as already been used", id);
			checkState(name != null, "%s name not set", id);
			checkState(weight != null, "%s weight not set", id);
			checkState(magScaling != null, "%s mag-scaling relation not set", id);
			checkState(gmmSet != null, "%s ground motion models not set", id);
			built = true;
		}

		FaultSourceSet buildFaultSet() {
			validateState(ID);
			return new FaultSourceSet(name, weight, magScaling, sources, gmmSet);
		}
	}

}
