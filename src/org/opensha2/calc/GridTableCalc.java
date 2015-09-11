package org.opensha2.calc;

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
	 * Further consideration of these issues suggests that, rather than agregating curves
	 * along the way, we should build a separate table in magnitude and distance of rates
	 * while looping over sources. At the end, curves could be computed once for each distance
	 * and magnitude bin. Although full curves for each Gmm could be precomputed, the time to loop over the rate
	 * table may not be significant enough to warrant the memory overhead (bear in mind that's a lot of
	 * curves when considering large logic trees of Gmms and numerous periods).
	 * 
	 * Precomputed curves may still be warranted for map calculations where Gmm specific
	 * data and deaggregation are irrelevant.
	 * 
	 */
	
	
	/*
	 * Requirements for a GmmSet:
	 * 
	 */

}
