package org.opensha.eq.model;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.opensha.data.DataUtils.validateWeight;
import static org.opensha.util.TextUtils.validateName;

import java.util.Iterator;
import java.util.List;

import org.opensha.eq.fault.scaling.MagScalingType;
import org.opensha.geo.Location;
import org.opensha.geo.Locations;

import com.google.common.base.Predicate;
import com.google.common.collect.Lists;

/**
 * Container class for related {@link FaultSource}s.
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

	// TODO play around with performance of rectangle filtering or not
	// if distance is large (e.g.) the majority of sources will always
	// pass rect test.
	
	/* Not inlined for use by cluster sources */
	static class DistanceFilter implements Predicate<FaultSource> {
		private final Predicate<Location> filter;

		DistanceFilter(Location loc, double distance) {
			filter = Locations.distanceFilter(loc, distance);
		}

		@Override public boolean apply(FaultSource source) {
			return filter.apply(source.trace.first()) || filter.apply(source.trace.last());
		}

		@Override public String toString() {
			return "FaultSourceSet.DistanceFilter[ " + filter.toString() + " ]";
		}
	}

	/* Single use builder. */
	static class Builder {

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
