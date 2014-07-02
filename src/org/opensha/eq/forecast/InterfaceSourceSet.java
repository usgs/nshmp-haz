package org.opensha.eq.forecast;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.opensha.data.DataUtils.validateWeight;
import static org.opensha.geo.Locations.horzDistanceFast;
import static org.opensha.util.TextUtils.validateName;

import java.util.Iterator;
import java.util.List;

import org.opensha.eq.fault.scaling.MagScalingType;
import org.opensha.eq.forecast.FaultSourceSet.Builder;
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

			@Override public boolean apply(InterfaceSource source) {
				return horzDistanceFast(loc, source.trace.first()) <= distance ||
					horzDistanceFast(loc, source.trace.last()) <= distance ||
					horzDistanceFast(loc, source.lowerTrace.first()) <= distance ||
					horzDistanceFast(loc, source.lowerTrace.last()) <= distance;
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

		@Override Builder magScaling(MagScalingType magScaling) {
			super.magScaling(magScaling);
			return this;
		}

		InterfaceSourceSet buildSubductionSet() {
			validateState(ID);
			return new InterfaceSourceSet(name, weight, magScaling, gmmSet, sources);
		}
	}

}
