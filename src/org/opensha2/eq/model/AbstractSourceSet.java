package org.opensha2.eq.model;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Strings.padEnd;
import static org.opensha2.data.Data.checkWeight;
import static org.opensha2.util.TextUtils.validateName;

import org.opensha2.geo.Location;

import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Ordering;

/**
 * Skeletal {@code SourceSet} implementation.
 * 
 * @author Peter Powers
 */
abstract class AbstractSourceSet<T extends Source> implements SourceSet<T> {

	private final String name;
	private final int id;
	private final double weight;
	private final GmmSet gmmSet;

	AbstractSourceSet(String name, int id, double weight, GmmSet gmmSet) {
		this.name = name;
		this.id = id;
		this.weight = weight;
		this.gmmSet = gmmSet;
	}

	@Override public int compareTo(SourceSet<T> other) {
		return Ordering.natural().compare(this.name(), other.name());
	}

	@Override public String name() {
		return name;
	}

	@Override public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(" Id: ").append(padEnd(Integer.toString(id), 8, ' '));
		sb.append("Name: ").append(padEnd(name(), 38, ' '));
		sb.append("Size: ").append(padEnd(Integer.toString(size()), 8, ' '));
		sb.append("Weight: ").append(padEnd(Double.toString(weight), 12, ' '));
		return sb.toString();
	}
	
	@Override public int id() {
		return id;
	}

	@Override public double weight() {
		return weight;
	}

	@Override public GmmSet groundMotionModels() {
		return gmmSet;
	}

	@Override public Iterable<T> iterableForLocation(Location loc) {
		Predicate<T> filter = distanceFilter(loc, gmmSet.maxDistance());
		return FluentIterable.from(this).filter(filter);
	}
	
	static abstract class Builder {
		
		boolean built = false;

		String name;
		Integer id;
		Double weight;
		GmmSet gmmSet;

		Builder name(String name) {
			this.name = validateName(name);
			return this;
		}

		Builder id(int id) {
			this.id = id;
			return this;
		}

		Builder weight(double weight) {
			this.weight = checkWeight(weight);
			return this;
		}

		Builder gmms(GmmSet gmmSet) {
			this.gmmSet = checkNotNull(gmmSet);
			return this;
		}

		void validateState(String buildId) {
			checkState(!built, "This %s instance has already been used", buildId);
			built = true;
			checkState(name != null, "%s name not set", buildId);
			checkState(id != null, "%s id not set", buildId);
			checkState(weight != null, "%s weight not set", buildId);
			checkState(gmmSet != null, "%s ground motion models not set", buildId);
		}

	}

}
