package org.opensha.gmm;

/**
 * Concrete implementation of the BC Hydro model by Addo, Abrahamson, &amp;
 * Youngs (2012) for subduction interface events.
 * 
 * @author Peter Powers
 * @see BcHydro_2012
 */
final class BcHydro_2012_Interface extends BcHydro_2012 {
	
	static final String NAME = BcHydro_2012.NAME + ": Interface";

	BcHydro_2012_Interface(IMT imt) {
		super(imt);
	}

	@Override final boolean isSlab() { return false; }

}
