package org.opensha2.eq.model;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.util.Iterator;
import java.util.List;

import org.opensha2.geo.Location;
import org.opensha2.geo.Locations;

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

  private FaultSourceSet(
      String name,
      int id,
      double weight,
      List<FaultSource> sources,
      GmmSet gmmSet) {

    super(name, id, weight, gmmSet);
    this.sources = sources;
  }

  @Override
  public Iterator<FaultSource> iterator() {
    return sources.iterator();
  }

  @Override
  public int size() {
    return sources.size();
  }

  @Override
  public SourceType type() {
    return SourceType.FAULT;
  }

  @Override
  public Predicate<FaultSource> distanceFilter(Location loc, double distance) {
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

    @Override
    public boolean apply(FaultSource source) {
      return filter.apply(source.trace.first()) || filter.apply(source.trace.last());
    }

    @Override
    public String toString() {
      return "FaultSourceSet.DistanceFilter[ " + filter.toString() + " ]";
    }
  }

  /* Single use builder. */
  static class Builder extends AbstractSourceSet.Builder {

    static final String ID = "FaultSourceSet.Builder";

    final List<FaultSource> sources = Lists.newArrayList();

    Builder source(FaultSource source) {
      sources.add(checkNotNull(source, "FaultSource is null"));
      return this;
    }

    @Override
    void validateState(String buildId) {
      super.validateState(buildId);
    }

    FaultSourceSet buildFaultSet() {
      validateState(ID);
      // this is here because calls up from interface
      // will fail in this.validateState()
      checkState(sources.size() > 0, "%s source list is empty", ID);
      return new FaultSourceSet(name, id, weight, sources, gmmSet);
    }
  }

}
