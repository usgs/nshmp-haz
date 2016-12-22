package org.opensha2.eq.model;

import org.opensha2.geo.Location;
import org.opensha2.gmm.GroundMotionModel;
import org.opensha2.util.Named;

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
   * A unique integer id.
   */
  int id();

  /**
   * The {@code SourceType} identifier.
   */
  SourceType type();

  /**
   * The number of {@code Source}s in this {@code SourceSet}.
   */
  int size();

  /**
   * The weight applicable to this {@code SourceSet}.
   */
  double weight();

  /**
   * Return an {@code Iterable} over those {@code Source}s that are within the
   * cutoff distance of the supplied {@code Location}. The cutoff distance is
   * derived from the {@code GroundMotionModel}s associated with this
   * {@code SourceSet}.
   *
   * @param loc {@code Location} of interest
   */
  Iterable<T> iterableForLocation(Location loc);

  /**
   * Return an {@code Iterable} over those {@code Source}s that are within
   * {@code distance} of the supplied {@code Location}.
   *
   * @param loc {@code Location} of interest
   * @param distance limit
   */
  Iterable<T> iterableForLocation(Location loc, double distance);

  /**
   * Return a {@link Predicate} that evaluates to {@code true} if this source is
   * within {@code distance} of the supplied {@link Location}. This
   * {@code Predicate} performs a quick distance calculation and is used to
   * determine whether this source should be included in a hazard calculation.
   *
   * @param loc {@code Location} of interest
   * @param distance limit
   */
  Predicate<T> distanceFilter(Location loc, double distance);

  /**
   * Return the {@link GroundMotionModel}s associated with this
   * {@code SourceSet} as a {@link GmmSet}.
   *
   * @see GmmSet
   */
  GmmSet groundMotionModels();
}
