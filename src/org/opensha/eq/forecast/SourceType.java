package org.opensha.eq.forecast;

import org.opensha.util.Parsing;

/**
 * Identifier for different earthquake source types.
 * @author Peter Powers
 */
public enum SourceType {

	/** Finite fault source type identifier. */
	FAULT, 
	
	/** Gridded (background) seismicity source type. */
	GRID,

	/** Subduction interface source type. */
	SUBDUCTION,
	
	/** Cluster source type. */
	CLUSTER,
	
	/** Area source type. */
	AREA;
	
	@Override
	public String toString() {
		return Parsing.capitalize(name());
	}
			
}
