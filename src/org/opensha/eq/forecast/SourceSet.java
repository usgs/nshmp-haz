package org.opensha.eq.forecast;

import java.util.Iterator;

import org.opensha.geo.Location;
import org.opensha.util.Named;

/**
 * Wrapper class for groups of different {code Source}s. The {@code Source}s are
 * usually of a similar {code SourceType} and this intermediate layer between
 * {code Forecast} and {@code Source} is provided to support this grouping. The
 * use of the word 'Set' in the class name implies the {@code Source}s in a
 * {@code SourceSet} will be unique, but no steps are taken to enforce this.
 * 
 * @author Peter Powers
 */
public interface SourceSet<T extends Source> extends Named, Iterable<T>, Comparable<SourceSet<T>> {

	/**
	 * Returns the {@code SourceType} identifier.
	 * @return the {@code SourceType} identifier
	 */
	public SourceType type();

	/**
	 * Returns the number of {@code Source}s in this {@code SourceSet}.
	 * @return the number of {@code Source}s contained herein
	 */
	public int size();

	// TODO will all Source Set implementations support this?

	public double weight();

	public Iterable<T> locationIterable(Location loc);

}
