package org.opensha.eq.forecast;

import java.util.Iterator;

import org.opensha.geo.Location;

/**
 * Add comments here
 *
 * @author Peter Powers
 */
public interface LocationIterable<T> {

	public Iterator<T> iterator(Location loc);
	
}
