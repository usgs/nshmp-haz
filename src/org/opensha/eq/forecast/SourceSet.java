package org.opensha.eq.forecast;

import java.util.Iterator;

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
public interface SourceSet<T extends Source> extends Named, Iterable<T> {
	
//	private SourceType type;
	
	/**
	 * @return
	 */
	public SourceType type();
	
	// SourceSets should also be grouped by region

}
