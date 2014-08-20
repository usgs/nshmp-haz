package org.opensha.eq.forecast;

import org.opensha.mfd.IncrementalMfd;
import org.opensha.util.Named;

/**
 * An earthquake {@code Source}; usually some physical or pseudo-representation
 * of a fault and associated {@link IncrementalMfd}s governing the size and rate
 * of all possible {@link Rupture}s.
 * 
 * @author Peter Powers
 */
public interface Source extends Named, Iterable<Rupture> {

	/**
	 * The number of {@link Rupture}s this {@code Source} represents.
	 */
	public int size();

}
