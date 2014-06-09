package org.opensha.eq.forecast;

import java.util.Iterator;

import org.opensha.geo.Location;

/**
 * A {@link GridSourceSet} decorator.
 * 
 * @author Peter Powers
 * @see GridSourceSet
 */
public class SlabSourceSet implements SourceSet<PointSource> {

	private GridSourceSet delegate;

	SlabSourceSet(GridSourceSet delegate) {
		this.delegate = delegate;
	}

	@Override public String name() {
		return delegate.name();
	}

	@Override public Iterator<PointSource> iterator() {
		return delegate.iterator();
	}

	@Override public SourceType type() {
		return delegate.type();
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

}
