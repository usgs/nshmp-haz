package org.opensha.calc;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;

import org.opensha.eq.model.Source;

/**
 * Lightweight {@code List} wrapper of {@code TemporalGmmInput}s that contains a
 * reference to the parent source from which the inputs were derived. This
 * allows for downstream access to parent source properties. The {@code List}
 * may only be added to; all other optional operations of {@code AbstractList}
 * throw an {@code UnsupportedOperationException}.
 * 
 * @author Peter Powers
 */
class HazardInputs extends AbstractList<TemporalGmmInput> {

	Source parent;
	List<TemporalGmmInput> delegate;
	
	HazardInputs(Source parent) {
		this.parent = checkNotNull(parent);
		delegate = new ArrayList<>();
	}

	@Override public boolean add(TemporalGmmInput input) {
		return delegate.add(input);
	}
	
	@Override public TemporalGmmInput get(int index) {
		return delegate.get(index);
	}
	
	@Override public int size() {
		return delegate.size();
	}
}
