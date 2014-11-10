package org.opensha.eq.model;

import java.util.Iterator;

import org.opensha.geo.Location;

import com.google.common.base.Predicate;

/**
 * Container class for related, evenly-spaced subduction slab sources. Class
 * decorates a {@link GridSourceSet}.
 * 
 * @author Peter Powers
 * @see GridSourceSet
 */
public class SlabSourceSet implements SourceSet<PointSource> {

	private GridSourceSet delegate;

	SlabSourceSet(GridSourceSet delegate) {
		this.delegate = delegate;
	}

	@Override public int compareTo(SourceSet<PointSource> other) {
		return delegate.compareTo(other);
	}

	@Override public String name() {
		return delegate.name();
	}

	@Override public String toString() {
		return delegate.toString();
	}

	@Override public Iterator<PointSource> iterator() {
		return delegate.iterator();
	}

	@Override public SourceType type() {
		return SourceType.SLAB;
	}

	@Override public int size() {
		return delegate.size();
	}

	@Override public double weight() {
		return delegate.weight();
	}

	@Override public Iterable<PointSource> locationIterable(Location loc) {
		return delegate.locationIterable(loc);
	}

	@Override public GmmSet groundMotionModels() {
		return delegate.groundMotionModels();
	}

	@Override public Predicate<PointSource> distanceFilter(Location loc, double distance) {
		return delegate.distanceFilter(loc, distance);
	}

}
