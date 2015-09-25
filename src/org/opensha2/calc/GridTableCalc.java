package org.opensha2.calc;

import org.opensha2.eq.model.GmmSet;
import org.opensha2.gmm.Gmm;
import org.opensha2.gmm.GroundMotionModel;
import org.opensha2.gmm.Imt;

/**
 * Handler for hazard calculations where grid source ground motions have been
 * precomputed for set magnitudes and distance bins
 *
 * @author Peter Powers
 */
@Deprecated
class GridTableCalc {

	/*
	 * If, for a basic HazardResult, we want to be able to give a per-source-set
	 * decomposition by ground motion model, or just a decomposition of the
	 * total curve, we'll need to have a table of the curves for every model.
	 * 
	 * If not necessary, then can have table of total curves and table of mean
	 * (and sigma?) for each model. Just mean is necessary for deaggeregation
	 * epsilon
	 * 
	 * OK... so...
	 * 
	 * Preliminary implementations of grid source optimizations modeled after
	 * the NSHMP Fortran codes porecomputed median curves in distance and
	 * magnitude (using a weighted combination of Gmms) and then performed
	 * lookups for each source, aggregating a total curve along the way. This
	 * approach is lossy in that data for individual Gmms is lost, and it was
	 * never extended to support deaggregation where ground motion mean and
	 * sigma are required.
	 * 
	 * Further consideration of these issues suggests that, rather than
	 * agregating curves along the way, we should build a separate table in
	 * magnitude and distance of rates while looping over sources. At the end,
	 * curves could be computed once for each distance and magnitude bin.
	 * Although full curves for each Gmm could be precomputed, the time to loop
	 * over the rate table may not be significant enough to warrant the memory
	 * overhead (bear in mind that's a lot of curves when considering large
	 * logic trees of Gmms and numerous periods).
	 * 
	 * There is also the additional issue of additional epistemic uncertinaty on
	 * ground motions, which does not need to be considered here if building
	 * magnitude-distance rate tables.
	 * 
	 * There is the additional issue of different focal mechanisms. FOr NGAW2
	 * and the WUS, we would need to have 5 curves per gmm and r/m bin: 2
	 * reverse, 2 normal 1 strike slip
	 * 
	 * Precomputed curves may still be warranted for map calculations where Gmm
	 * specific data and deaggregation are irrelevant.
	 */

	/*
	 * need to specify magnitude and distance discretization
	 * 
	 * cache by GmmSet alone (need to maintain internal map by period) - or
	 * another internal cahce; a cache of caches sounds ugly and keeps the table
	 * management class simpler
	 * 
	 * of cache by GmmSet and Imt
	 * 
	 * class names GroundMotionCache cahce of ground motion tables in distance
	 * and magnitude
	 * 
	 * How do we tell if hanging wall is needed?
	 */

	GridTableCalc(GmmSet gmmSet) {

		// no hanging wall
		for (Gmm gmm : gmmSet.gmms()) {
			
		}
		GroundMotionModel gmm = Gmm.ASK_14.instance(Imt.PGA);
		
//		DataTableBuilder builder = DataTableBuilder.create().
		
		// hanging wall
	}
	
	public enum SourceStyle {
		STRIKE_SLIP,
		REVERSE_FOOTWALL,
		REVERSE_HANGINGWALL,
		NORMAL_FOOTWALL,
		NORMAL_HANGINGWALL;
	}
}
