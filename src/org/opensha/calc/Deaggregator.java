package org.opensha.calc;

import java.util.concurrent.Callable;

/**
 * Deaggregation.... hmmm... what to do.....
 * 
 * Basic deagg:
 * 		epsilon 
 * 		mag
 * 		dist
 * 
 * Geo deagg:
 * 		azimuth
 * 		
 * Banded deagg:
 * 		actual ground motion of each rupture
 * 
 * 
 * Other data to store:
 * 		mean event
 * 		modal event (2 epsilons
 * 
 * Need to consider UC3; whereas UC2 had specific characteristic ruptures
 * dominating hazard, UC3 effectively floats across the fault network.
 * 
 * Can we have geo-deagg map on a per sub-section basis?
 * 		NSHMP plots contributory columns at rRup?
 * 		
 * 		rRup for UC3 will have a spike at min(rRup) for contibuting ruptures
 * 			- this is fine and standard
 * 			- this will produce small tails around the main spike for the few
 * 				ruptures that contribute to hazard but whose min(rRup) endpoints
 * 				are close.
 * 			- alternatively, the contribution to hazard could be distributed
 * 				across all subsections
 * 
 * @author Peter Powers
 * @version $Id:$
 */
@Deprecated
class Deaggregator implements Callable<DeaggResult> {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO do nothing

	}
	
	private Deaggregator() {}
	
	/**
	 * Create a {@code Deaggregator}.
	 * 
	 * from list of gm means and std devs, and a target IML.
	 * @return
	 */
//	public static Deaggregator create(HazardCalcResultSet data) {
//		return null;
//	}


	@Override
	public DeaggResult call() throws Exception {
		return null;
		// TODO do nothing
		
	}

}
