package org.opensha.gmm;

/**
 * Concrete implementation of the BC Hydro model by Addo, Abrahamson, &amp;
 * Youngs (2012) for subduction intraslab events.
 * 
 * @author Peter Powers
 * @see BCHydro_2012
 */
final class BCHydro_2012_Slab extends BCHydro_2012 {
	
	public static final String NAME = BCHydro_2012.NAME + ": Slab";

	BCHydro_2012_Slab(IMT imt) {
		super(imt);
	}

	@Override final boolean isSlab() { return true; }

}
