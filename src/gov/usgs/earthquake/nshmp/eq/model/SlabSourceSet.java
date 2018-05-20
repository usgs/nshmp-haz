package gov.usgs.earthquake.nshmp.eq.model;

import static gov.usgs.earthquake.nshmp.eq.model.SourceType.SLAB;

import java.util.function.Predicate;

import java.util.Iterator;

import gov.usgs.earthquake.nshmp.geo.Location;

/**
 * Container class for related, evenly-spaced subduction slab sources. Class
 * decorates a {@link GridSourceSet}.
 *
 * @author Peter Powers
 * @see GridSourceSet
 */
public final class SlabSourceSet implements SourceSet<PointSource> {

  private final GridSourceSet delegate;

  SlabSourceSet(GridSourceSet delegate) {
    this.delegate = delegate;
  }

  @Override
  public final int compareTo(SourceSet<PointSource> other) {
    return delegate.compareTo(other);
  }

  @Override
  public final String name() {
    return delegate.name();
  }

  @Override
  public int id() {
    return delegate.id();
  }

  @Override
  public final String toString() {
    return delegate.toString();
  }

  @Override
  public final Iterator<PointSource> iterator() {
    return delegate.iterator();
  }

  @Override
  public final SourceType type() {
    return SLAB;
  }

  @Override
  public final int size() {
    return delegate.size();
  }

  @Override
  public final double weight() {
    return delegate.weight();
  }

  @Override
  public final Iterable<PointSource> iterableForLocation(Location loc) {
    return delegate.iterableForLocation(loc);
  }

  @Override
  public final Iterable<PointSource> iterableForLocation(Location loc, double distance) {
    return delegate.iterableForLocation(loc, distance);
  }

  @Override
  public final GmmSet groundMotionModels() {
    return delegate.groundMotionModels();
  }

  @Override
  public final Predicate<PointSource> distanceFilter(Location loc, double distance) {
    return delegate.distanceFilter(loc, distance);
  }

}
