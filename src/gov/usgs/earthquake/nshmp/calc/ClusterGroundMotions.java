package gov.usgs.earthquake.nshmp.calc;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;

import gov.usgs.earthquake.nshmp.eq.model.ClusterSource;

/**
 * Lightweight {@code List} wrapper of {@code GroundMotions}s corresponding to
 * the {@code FaultSource}s that make up a {@code ClusterSource}. This class
 * propogates a reference to the parent {@code ClusterSource} so that its rate
 * and weight are available. The {@code List} may only be added to; all other
 * optional operations of {@code AbstractList} throw an
 * {@code UnsupportedOperationException}.
 *
 * @author Peter Powers
 */
final class ClusterGroundMotions extends AbstractList<GroundMotions> {

  final ClusterSource parent;
  final List<GroundMotions> delegate;
  double minDistance = Double.MAX_VALUE;

  ClusterGroundMotions(ClusterSource parent) {
    this.parent = parent;
    delegate = new ArrayList<>();
  }

  @Override
  public boolean add(GroundMotions groundMotions) {
    minDistance = Math.min(minDistance, groundMotions.inputs.minDistance);
    return delegate.add(groundMotions);
  }

  @Override
  public GroundMotions get(int index) {
    return delegate.get(index);
  }

  @Override
  public int size() {
    return delegate.size();
  }
}
