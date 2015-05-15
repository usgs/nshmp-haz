package org.opensha.calc;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;

import org.opensha.eq.model.SystemSourceSet;

/**
 * Lightweight {@code List} wrapper of {@code HazardInput}s that contains a
 * reference to the parent {@code SystemSourceSet} from which the inputs were
 * derived. This allows for downstream access to parent source properties. The
 * {@code List} may only be added to; all other optional operations of
 * {@code AbstractList} throw an {@code UnsupportedOperationException}.
 * 
 * <p>Presently, a {@code SystemSourceSet} consists of sources fo rwhich there
 * is only a single rupture. Note that this could change in the future if some
 * magnitude variability were imposed on each source.</p>
 * 
 * @author Peter Powers
 */
public final class SystemInputs extends AbstractList<HazardInput> {
// TODO package privacy
	// TODO how to get back to parent to mine info; index?
	// TODO need index reference
	
	final SystemSourceSet parent;
	final List<HazardInput> delegate;

	public SystemInputs(SystemSourceSet parent) {
		this.parent = checkNotNull(parent);
		delegate = new ArrayList<>();
	}

	@Override public boolean add(HazardInput input) {
		return delegate.add(input);
	}

	@Override public HazardInput get(int index) {
		return delegate.get(index);
	}

	@Override public int size() {
		return delegate.size();
	}
}
