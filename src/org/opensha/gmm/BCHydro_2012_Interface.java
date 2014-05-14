package org.opensha.gmm;

/**
 * Concrete implementation of the BC Hydro model by Addo, Abrahamson, &amp;
 * Youngs (2012) for subduction interface events.
 * 
 * @author Peter Powers
 * @see BCHydro_2012
 */
final class BCHydro_2012_Interface extends BCHydro_2012 {
	
	static final String NAME = BCHydro_2012.NAME + ": Interface";

	BCHydro_2012_Interface(IMT imt) {
		super(imt);
	}

	@Override final boolean isSlab() { return false; }

}
