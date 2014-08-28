package org.opensha.eq.model;

import static com.google.common.base.Strings.padEnd;

import org.opensha.eq.fault.scaling.MagScalingType;
import org.opensha.geo.Location;

import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
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
	private final MagScalingType msrType; // TODO consider removing; not used
	private final GmmSet gmmSet;

	// NOTE we're holding onto weight for reference, however, MFD
	// rates will have already been scaled in place. TODO is this true?

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
		sb.append(" Name: ").append(padEnd(name, 27, ' '));
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
