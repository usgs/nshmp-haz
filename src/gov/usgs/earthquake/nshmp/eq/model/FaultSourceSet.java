package gov.usgs.earthquake.nshmp.eq.model;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static gov.usgs.earthquake.nshmp.eq.model.SourceType.FAULT;

import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;

import gov.usgs.earthquake.nshmp.geo.Location;
import gov.usgs.earthquake.nshmp.geo.Locations;

/**
 * Container class for related {@link FaultSource}s.
 *
 * @author Peter Powers
 * @see FaultSource
 */
public class FaultSourceSet extends AbstractSourceSet<FaultSource> {

  /*
   * TODO sourceMap should replace sources once all sources have associated ID's
   */
  private final List<FaultSource> sources;
  private final ListMultimap<Integer, FaultSource> sourceMap;

  private FaultSourceSet(
      String name,
      int id,
      double weight,
      List<FaultSource> sources,
      GmmSet gmmSet) {

    super(name, id, weight, gmmSet);
    this.sources = sources;

    /*
     * TODO refactor to builder; multi map to handle dip variants with same id
     */
    ImmutableListMultimap.Builder<Integer, FaultSource> b = ImmutableListMultimap.builder();
    for (FaultSource source : sources) {
      b.put(source.id, source);
    }
    sourceMap = b.build();
  }

  /**
   * Return those sources associated with the supplied id.
   * @param id of sources to retrieve
   */
  public List<FaultSource> sources(int id) {
    return sourceMap.get(id);
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
    return FAULT;
  }

  @Override
  public Predicate<FaultSource> distanceFilter(Location loc, double distance) {
    return new DistanceFilter(loc, distance);
  }

  /* Not inlined for use by cluster sources */
  static class DistanceFilter implements Predicate<FaultSource> {
    private final Predicate<Location> filter;

    DistanceFilter(Location loc, double distance) {
      filter = Locations.distanceFilter(loc, distance);
    }

    @Override
    public boolean test(FaultSource source) {
      return filter.test(source.trace.first()) || filter.test(source.trace.last());
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
