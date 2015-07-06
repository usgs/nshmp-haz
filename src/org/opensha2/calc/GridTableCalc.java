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
	 * decomposition by ground motion model, or just a decomposition of the total curve,
	 * we'll need to have a table of the curves for every model.
	 * 
	 * If not necessary, then can have table of total curves and table of mean (and sigma?)
	 * for each model. Just mean is necessary for deaggeregation epsilon
	 * 
	 */

}
