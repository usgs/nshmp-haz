package org.opensha.eq.model;

import org.opensha.geo.Location;
import org.opensha.gmm.GroundMotionModel;
import org.opensha.util.Named;

import com.google.common.base.Predicate;

/**
 * Wrapper class for groups of {@link Source}s of similar {@link SourceType}.
 * The use of the word 'Set' in the class name implies the {@code Source}s in a
 * {@code SourceSet} will be unique, but no steps are taken to enforce this.
 * 
 * @author Peter Powers
 */
public interface SourceSet<T extends Source> extends Named, Iterable<T>, Comparable<SourceSet<T>> {

	/**
	 * The {@code SourceType} identifier.
	 */
	public SourceType type();

	/**
	 * The number of {@code Source}s in this {@code SourceSet}.
	 */
	public int size();

	/**
	 * The weight applicable to this {@code SourceSet}.
	 */
	public double weight();

	/**
	 * Returns an {@code Iterable} over those {@code Source}s that are within
	 * the cutoff distance of the supplied {@code Location}. The cutoff distance
	 * is derived from the {@code GroundMotionModel}s associated with this
	 * {@code SourceSet}.
	 * @param loc {@code Location} of interest
	 */
	public Iterable<T> locationIterable(Location loc);

	/**
	 * Returns a {@link Predicate} that evaluates to {@code true} if this source
	 * is within {@code distance} of the supplied {@link Location}. This
	 * {@code Predicate} performs a quick distance calculation and is used to
	 * determine whether this source should be included in a hazard calculation.
	 * @param loc {@code Location} of interest
	 * @param distance limit
	 */
	public Predicate<T> distanceFilter(Location loc, double distance);

	/**
	 * Returns the {@link GroundMotionModel}s associated with this {@code SourceSet} as
	 * a {@link GmmSet}.
	 * @see GmmSet
	 */
	public GmmSet groundMotionModels();
}
