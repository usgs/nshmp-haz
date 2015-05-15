package org.opensha.calc;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;

import org.opensha.eq.model.ClusterSource;

/**
 * Lightweight {@code List} wrapper of {@code HazardGroundMotions}s
 * corresponding to the {@code FaultSource}s that make up a
 * {@code ClusterSource}. This class propogates a reference to the parent
 * {@code ClusterSource} so that its rate and weight are available. The
 * {@code List} may only be added to; all other optional operations of
 * {@code AbstractList} throw an {@code UnsupportedOperationException}.
 * 
 * @author Peter Powers
 */
final class ClusterGroundMotions extends AbstractList<HazardGroundMotions> {

	final ClusterSource parent;
	final List<HazardGroundMotions> delegate;
	double minDistance = Double.MAX_VALUE;

	ClusterGroundMotions(ClusterSource parent) {
		this.parent = parent;
		delegate = new ArrayList<>();
	}

	@Override public boolean add(HazardGroundMotions groundMotions) {
		minDistance = Math.min(minDistance, groundMotions.inputs.minDistance);
		return delegate.add(groundMotions);
	}

	@Override public HazardGroundMotions get(int index) {
		return delegate.get(index);
	}

	@Override public int size() {
		return delegate.size();
	}
}
