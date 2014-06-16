package org.opensha.gmm;

/**
 * Concrete implementation of the BC Hydro model by Addo, Abrahamson, &amp;
 * Youngs (2012) for subduction intraslab events.
 * 
 * @author Peter Powers
 * @see BcHydro_2012
 */
final class BcHydro_2012_Slab extends BcHydro_2012 {
	
	static final String NAME = BcHydro_2012.NAME + ": Slab";

	BcHydro_2012_Slab(IMT imt) {
		super(imt);
	}

	@Override final boolean isSlab() { return true; }

}
