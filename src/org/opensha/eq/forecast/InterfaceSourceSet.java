package org.opensha.eq.forecast;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.opensha.geo.Locations.horzDistanceFast;

import java.util.Iterator;
import java.util.List;

import org.opensha.eq.fault.scaling.MagScalingType;
import org.opensha.geo.Location;

import com.google.common.base.Predicate;
import com.google.common.collect.Lists;

/**
 * Wrapper class for groups of related {@code InterfaceSource}s.
 * 
 * @author Peter Powers
 * @see InterfaceSource
 */
public class InterfaceSourceSet extends AbstractSourceSet<InterfaceSource> {

	private final List<InterfaceSource> sources;

	private InterfaceSourceSet(String name, double weight, MagScalingType msrType, GmmSet gmmSet,
		List<InterfaceSource> sources) {
		super(name, weight, msrType, gmmSet);
		this.sources = sources;
	}

	@Override public Iterable<InterfaceSource> locationIterable(Location loc) {
		// TODO
		return null;
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

			@Override public boolean apply(InterfaceSource is) {
				return horzDistanceFast(loc, is.trace.first()) <= distance ||
					horzDistanceFast(loc, is.trace.last()) <= distance ||
					horzDistanceFast(loc, is.lowerTrace.first()) <= distance ||
					horzDistanceFast(loc, is.lowerTrace.last()) <= distance;
			}

		};
	}

	static class Builder extends FaultSourceSet.Builder {

		static final String ID = "InterfaceSourceSet.Builder";

		// type-specific field
		List<InterfaceSource> sources = Lists.newArrayList();

		// type-specific method
		Builder source(InterfaceSource source) {
			sources.add(checkNotNull(source, "InterfaceSource is null"));
			return this;
		}

		InterfaceSourceSet buildSubductionSet() {
			validateState(ID);
			return new InterfaceSourceSet(name, weight, magScaling, gmmSet, sources);
		}
	}

}
