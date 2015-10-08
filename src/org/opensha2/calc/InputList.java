package org.opensha2.calc;

import static org.opensha2.util.TextUtils.NEWLINE;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight {@code List} wrapper of {@code HazardInput}s. The {@code List}
 * may only be added to; all other optional operations of {@code AbstractList}
 * throw an {@code UnsupportedOperationException}.
 * 
 * @author Peter Powers
 */
public abstract class InputList extends AbstractList<HazardInput> {

	final List<HazardInput> delegate;
	double minDistance = Double.MAX_VALUE;

	/*
	 * minDistance is used to track the closest distance of any Rupture in a
	 * Source. This is used when multiple gmmSets for different distances are
	 * defined.
	 */

	InputList() {
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
	
	@Override public String toString() {
		StringBuilder sb = new StringBuilder("[").append(NEWLINE);
		for (HazardInput input : this) {
			sb.append(input).append(NEWLINE);
		}
		sb.append("]");
		return sb.toString();
	}

	abstract String parentName();

}
