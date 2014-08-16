package org.opensha.eq.forecast;

import static com.google.common.base.Strings.padEnd;
import java.util.Map;

import org.opensha.eq.fault.scaling.MagScalingType;
import org.opensha.geo.Location;

import com.google.common.base.Predicate;
import com.google.common.base.Strings;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Maps;
import com.google.common.collect.Ordering;

/**
 * Skeletal {@code SourceSet} implementation.
 * 
 * @author Peter Powers
 */
abstract class AbstractSourceSet<T extends Source> implements SourceSet<T> {

	private static final Ordering<String> SORTER = Ordering.natural();

	private final String name;
	private final double weight;
	private final MagScalingType msrType;
	private final GmmSet gmmSet;

	// NOTE msrType is currently not exposed; nor is it used
	// TODO consider removing

	// NOTE we're holding onto weight for reference, however, MFD
	// rates will have already been scaled in place. The weight value
	// may come in handy when trying to put together individual
	// logic tree branches.

	AbstractSourceSet(String name, double weight, MagScalingType msrType, GmmSet gmmSet) {
		this.name = name;
		this.weight = weight;
		this.msrType = msrType;
		this.gmmSet = gmmSet;
	}

	@Override public int compareTo(SourceSet<T> other) {
		return SORTER.compare(this.name(), other.name());
	}

	@Override public String name() {
		return name;
	}
	
	@Override public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(" Name: ").append(padEnd(name, 24, ' '));
		sb.append("Size: ").append(padEnd(Integer.toString(size()), 8, ' '));
		sb.append("Weight: ").append(padEnd(Double.toString(weight), 12, ' '));
		return sb.toString();
	}

	@Override public double weight() {
		return weight;
	}

	@Override public GmmSet groundMotionModels() {
		return gmmSet;
	}
	
	@Override public Iterable<T> locationIterable(Location loc) {
		Predicate<T> filter = distanceFilter(loc, gmmSet.maxDistHi);
		return FluentIterable.from(this).filter(filter);
	}
	

}
