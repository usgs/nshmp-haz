package org.opensha2.eq.model;

import org.opensha2.geo.Location;
import org.opensha2.mfd.IncrementalMfd;
import org.opensha2.util.Named;

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
  int size();

  /**
   * The {@code SourceType} identifier.
   */
  SourceType type();

  /**
   * The {@code Location} of this source relative to the supplied {@code site}
   * location. The details of what this method returns are implementation
   * secific.
   */
  Location location(Location site);

}
