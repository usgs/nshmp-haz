package org.opensha2.calc;

import java.util.List;
import java.util.Map;

import org.opensha2.data.ArrayXY_Sequence;
import org.opensha2.eq.fault.FocalMech;
import org.opensha2.eq.model.GmmSet;
import org.opensha2.gmm.Gmm;

import com.google.common.collect.Table;

/**
 * A {@code HazardTable} manages ground motions and corresponding curves that
 * have been precomputed for set magnitudes and distance bins. These tables are
 * used to speed up hazard calculations for gridded seismicity sources.
 * 
 * @author Peter Powers
 */
class HazardTable {

	private final GmmSet gmmSet;

	private HazardTable(GmmSet gmmSet) {
		this.gmmSet = gmmSet;
	}

	// one approach (ceus)
	private Map<Gmm, List<List<Double>>> μMap;
	private Map<Gmm, List<List<Double>>> σMap;
	private Map<Gmm, List<List<ArrayXY_Sequence>>> curveMap;
	private List<List<ArrayXY_Sequence>> totalCurveList;
	// or (wus)
	private Table<Gmm, FocalMech, List<List<Double>>> μTable;
	private Table<Gmm, FocalMech, List<List<Double>>> σTable;
	private Table<Gmm, FocalMech, List<List<ArrayXY_Sequence>>> curveTable;
	private Map<FocalMech, List<List<ArrayXY_Sequence>>> totalCurveMap;

	// so, when looping inputs and compiling ground motions

	/*
	 * while the above format is compact, we will be looping over sources and
	 * would be required to pull references by gmm.
	 * 
	 * if we reference primarily by index, we'd need objects that encapsulte all
	 * data for a
	 * 
	 * like GroundMotionTables, lets store all data in 2D arrays indexed by R
	 * then M
	 */

	/*
	 * GRID-like sources that won't use table lookups:
	 * 
	 * skipping slab; slabs will ultimately be represented with continuously
	 * depth varying models so unless one wants to take on the task of
	 * developing mag-distance-depth tables, there is probably little point
	 * given the more limited areal extent of slab sources; this also remains
	 * true for fixed strike sources
	 */

}
