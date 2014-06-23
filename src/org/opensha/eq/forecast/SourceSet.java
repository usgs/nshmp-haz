package org.opensha.eq.forecast;

import java.util.Iterator;

import org.opensha.geo.Location;
import org.opensha.util.Named;

import com.google.common.base.Predicate;

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

	public double weight();

	/**
	 * Returns an {@code Iterable} over those {@code Source}s that are within
	 * the cutoff distance of the supplied {@code Location}. The cutoff distance
	 * is derived from the {@code GroundMotionModel}s internally associated with
	 * this {@code SourceSet}.
	 * @param loc {@code Location} of interest
	 * @return a {@code Location} based {@code Iterable}
	 */
	public Iterable<T> locationIterable(Location loc);

	/**
	 * Returns a {@code Predicate} that evaluates to {@code true} if this source
	 * is within {@code distance} of the supplied {@code Location}. This
	 * {@code Predicate} performs a quick distance calculation and may be used
	 * to determine whether this source should be included in a hazard
	 * calculation.
	 * @param loc {@code Location} of interest
	 * @param distance
	 * @return
	 */
	public Predicate<T> distanceFilter(Location loc, double distance);

	// TODO comment; GmmSet isn't visible
	// this isn;t the best; need to see how we're going to use when computing hazard
	public GmmSet groundMotionModels();
}
