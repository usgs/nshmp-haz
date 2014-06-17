package org.opensha.gmm;

/**
 * Concrete implementation of Youngs et al. (1997) for subduction intraslab
 * events.
 * 
 * @author Peter Powers
 * @see YoungsEtAl_1997
 */
final class YoungsEtAl_1997_Slab extends YoungsEtAl_1997 {

	static final String NAME = YoungsEtAl_1997.NAME + ": Interface";

	YoungsEtAl_1997_Slab(Imt imt) {
		super(imt);
	}

	@Override final boolean isSlab() {
		return true;
	}

}
