package org.opensha.eq.forecast;

import java.util.List;

import org.opensha.eq.fault.surface.EvenlyGriddedSurface;
import org.opensha.eq.fault.surface.RuptureSurface;
import org.opensha.geo.Location;
import org.opensha.geo.LocationList;
import org.opensha.util.Named;

/**
 * Add comments here
 *
 * @author Peter Powers
 */
public interface Source extends Named, Iterable<Rupture> {

//	public RuptureSurface surface(); // do we really need access to the surface after creation
	public int size();
	public List<Rupture> getRuptureList(); // consider deleting; people can make their own lists
	public Rupture getRupture(int idx);
	public double getMinDistance(Location loc); // TODO rename to distanceTo(Loc)
//	public Location centroid(); // ???
	
//	public SourceType type(); 
}
