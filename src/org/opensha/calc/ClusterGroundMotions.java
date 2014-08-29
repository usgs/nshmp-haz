package org.opensha.calc;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight {@code List} wrapper of {@code HazardGroundMotions}s
 * corresponding to the {@code FaultSource}s that make up a
 * {@code ClusterSource}. The {@code List} may only be added to; all other
 * optional operations of {@code AbstractList} throw an
 * {@code UnsupportedOperationException}.
 * 
 * @author Peter Powers
 */
final class ClusterGroundMotions extends AbstractList<HazardGroundMotions> {

	final List<HazardGroundMotions> delegate;

	ClusterGroundMotions() {
		delegate = new ArrayList<>();
	}

	@Override public boolean add(HazardGroundMotions groundMotions) {
		return delegate.add(groundMotions);
	}

	@Override public HazardGroundMotions get(int index) {
		return delegate.get(index);
	}

	@Override public int size() {
		return delegate.size();
	}
}
