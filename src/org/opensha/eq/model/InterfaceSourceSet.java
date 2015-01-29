package org.opensha.eq.model;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Iterator;
import java.util.List;

import org.opensha.geo.Location;
import org.opensha.geo.Locations;

import com.google.common.base.Predicate;
import com.google.common.collect.Lists;

/**
 * Container class for groups of related {@link InterfaceSource}s.
 * 
 * @author Peter Powers
 * @see InterfaceSource
 */
public class InterfaceSourceSet extends AbstractSourceSet<InterfaceSource> {

	private final List<InterfaceSource> sources;

	private InterfaceSourceSet(String name, double weight, GmmSet gmmSet,
		List<InterfaceSource> sources) {
		super(name, weight, gmmSet);
		this.sources = sources;
	}

	@Override public Iterator<InterfaceSource> iterator() {
		return sources.iterator();
	}

	@Override public int size() {
		return sources.size();
	}

	@Override public SourceType type() {
		return SourceType.INTERFACE;
	}

	@Override public Predicate<InterfaceSource> distanceFilter(final Location loc,
			final double distance) {

		return new Predicate<InterfaceSource>() {
			private Predicate<Location> filter = Locations.distanceFilter(loc, distance);

			@Override public boolean apply(InterfaceSource source) {
				return filter.apply(source.trace.first()) || filter.apply(source.trace.last()) ||
					filter.apply(source.lowerTrace.first()) ||
					filter.apply(source.lowerTrace.last());
			}

			@Override public String toString() {
				return "InterfaceSourceSet.DistanceFilter[ " + filter.toString() + " ]";
			}
		};
	}

	/* Single use builder. */
	static class Builder extends FaultSourceSet.Builder {

		static final String ID = "InterfaceSourceSet.Builder";

		// type-specific field
		List<InterfaceSource> sources = Lists.newArrayList();

		// type-specific method
		Builder source(InterfaceSource source) {
			sources.add(checkNotNull(source, "InterfaceSource is null"));
			return this;
		}

		// overridden to support method chaining
		@Override Builder name(String name) {
			super.name(name);
			return this;
		}

		@Override Builder weight(double weight) {
			super.weight(weight);
			return this;
		}

		@Override Builder gmms(GmmSet gmmSet) {
			super.gmms(gmmSet);
			return this;
		}

		InterfaceSourceSet buildSubductionSet() {
			validateState(ID);
			return new InterfaceSourceSet(name, weight, gmmSet, sources);
		}
	}

}
