package org.opensha2.calc;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;

import org.opensha2.eq.model.ClusterSource;

/**
 * Lightweight {@code List} wrapper of {@code HazardInput}s corresponding to the
 * {@code FaultSource}s that make up a {@code ClusterSource}. This class
 * contains a reference to the parent {@code ClusterSource} from which the
 * inputs were derived. This allows for downstream access to parent source
 * properties. The {@code List} may only be added to; all other optional
 * operations of {@code AbstractList} throw an
 * {@code UnsupportedOperationException}.
 * 
 * @author Peter Powers
 */
final class ClusterInputs extends AbstractList<SourceInputList> {

	final ClusterSource parent;
	final List<SourceInputList> delegate;

	ClusterInputs(ClusterSource parent) {
		this.parent = checkNotNull(parent);
		delegate = new ArrayList<>();
	}

	@Override public boolean add(SourceInputList inputs) {
		return delegate.add(inputs);
	}

	@Override public SourceInputList get(int index) {
		return delegate.get(index);
	}

	@Override public int size() {
		return delegate.size();
	}
}
