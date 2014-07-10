package org.opensha.gmm;

import org.opensha.util.Parsing;

/**
 * Style-of-faulting identifier.
 * @author Peter Powers
 */
public enum FaultStyle {
	
	// TODO can this be reconciled with FocalMech

	/** Strike-slip fault identifier. */
	STRIKE_SLIP,
	
	/** Normal fault identifier. */
	NORMAL,
	
	/** Reverse fault identifier. */
	REVERSE,
	
	/** Reverse-oblique identifier. */
	REVERSE_OBLIQUE,
	
	/** Unknown fault style identifier. */
	UNKNOWN;
	
	@Override
	public String toString() {
		return Parsing.enumLabelWithDashes(this);
	}

}
