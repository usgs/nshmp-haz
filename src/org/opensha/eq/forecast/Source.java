package org.opensha.eq.forecast;

import java.util.List;

import org.opensha.eq.fault.surface.GriddedSurface;
import org.opensha.eq.fault.surface.RuptureSurface;
import org.opensha.geo.Location;
import org.opensha.geo.LocationList;
import org.opensha.util.Named;

import com.google.common.base.Predicate;

/**
 * Add comments here
 * 
 * @author Peter Powers
 */
public interface Source extends Named, Iterable<Rupture> {

	// public RuptureSurface surface(); // do we really need access to the
	// surface after creation
	public int size();

	// public List<Rupture> getRuptureList(); // consider deleting; people can
	// make their own lists

	// TODO may find we want this in future but currently
	// there is no use case that absolutely requires indexing
	//
	// public Rupture getRupture(int idx); 

//	public double getMinDistance(Location loc); // TODO rename to
												// distanceTo(Loc)
	// public Location centroid(); // ???

	// public SourceType type();


	/**
	 * Returns whether this source is within {@code distance} of the supplied
	 * {@code Location}. This method performs a quick distance calculation and
	 * is used to determine whether this source should be included in a hazard
	 * calculation.
	 * @param loc {@code Location} of interest
	 * @param distance
	 * @return
	 */
//	public boolean isWithinDistanceOf(Location loc, double distance);
}
