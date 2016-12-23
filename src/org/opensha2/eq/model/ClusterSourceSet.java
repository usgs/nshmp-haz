package org.opensha2.eq.model;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import static org.opensha2.eq.model.SourceType.CLUSTER;

import org.opensha2.geo.Location;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;

import java.util.Iterator;
import java.util.List;

/**
 * Container class for related {@link ClusterSource}s.
 *
 * @author Peter Powers
 * @see ClusterSource
 */
public class ClusterSourceSet extends AbstractSourceSet<ClusterSource> {

  /*
   * TODO sourceMap should replace sources once all sources have associated ID's
   */
  private final List<ClusterSource> sources;
  private final ListMultimap<Integer, ClusterSource> sourceMap;

  ClusterSourceSet(
      String name,
      int id,
      double weight,
      List<ClusterSource> sources,
      GmmSet gmmSet) {

    super(name, id, weight, gmmSet);
    this.sources = sources;

    /* TODO refactor to builder */
    ImmutableListMultimap.Builder<Integer, ClusterSource> b = ImmutableListMultimap.builder();
    for (ClusterSource source : sources) {
      b.put(source.id(), source);
    }
    sourceMap = b.build();
  }

  /**
   * Return the source associated with the supplied id.
   * @param id of source to retrieve
   */
  public List<ClusterSource> source(int id) {
    return sourceMap.get(id);
  }

  @Override
  public Iterator<ClusterSource> iterator() {
    return sources.iterator();
  }

  @Override
  public int size() {
    return sources.size();
  }

  @Override
  public SourceType type() {
    return CLUSTER;
  }

  @Override
  public Predicate<ClusterSource> distanceFilter(final Location loc,
      final double distance) {
    return new Predicate<ClusterSource>() {

      private final Predicate<FaultSource> filter = new FaultSourceSet.DistanceFilter(loc,
          distance);

      @Override
      public boolean apply(ClusterSource cs) {
        return Iterables.any(cs.faults, filter);
      }

      @Override
      public String toString() {
        return "ClusterSourceSet.DistanceFilter [ " + filter.toString() + " ]";
      }
    };
  }

  /* Single use builder */
  static class Builder extends AbstractSourceSet.Builder {

    private static final String ID = "ClusterSourceSet.Builder";

    private final List<ClusterSource> sources = Lists.newArrayList();

    Builder source(ClusterSource source) {
      sources.add(checkNotNull(source, "ClusterSource is null"));
      return this;
    }

    @Override
    void validateState(String buildId) {
      super.validateState(buildId);
      checkState(sources.size() > 0, "%s source list is empty", buildId);
    }

    ClusterSourceSet buildClusterSet() {
      validateState(ID);
      return new ClusterSourceSet(name, id, weight, sources, gmmSet);
    }
  }

}
