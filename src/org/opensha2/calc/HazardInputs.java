package org.opensha2.calc;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;

import org.opensha2.eq.model.Source;

/**
 * Lightweight {@code List} wrapper of {@code HazardInput}s that contains a
 * reference to the parent {@code Source} from which the inputs were derived.
 * This allows for downstream access to parent source properties. The
 * {@code List} may only be added to; all other optional operations of
 * {@code AbstractList} throw an {@code UnsupportedOperationException}.
 * 
 * @author Peter Powers
 */
final class HazardInputs extends AbstractList<HazardInput> {

	final Source parent;
	final List<HazardInput> delegate;
	double minDistance = Double.MAX_VALUE;
	
	/*
	 * minDistance is used to track the closest distance of any Rupture
	 * in a Source. This is used when multiple gmmSets for different
	 * distances are defined.
	 */

	HazardInputs(Source parent) {
		this.parent = checkNotNull(parent);
		delegate = new ArrayList<>();
	}

	@Override public boolean add(HazardInput input) {
		minDistance = Math.min(minDistance, input.rJB);
		return delegate.add(input);
	}

	@Override public HazardInput get(int index) {
		return delegate.get(index);
	}

	@Override public int size() {
		return delegate.size();
	}
}
