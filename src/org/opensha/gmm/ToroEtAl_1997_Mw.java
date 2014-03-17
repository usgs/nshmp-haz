package org.opensha.gmm;

/**
 * Concrete implementation of Toro et al. (1997) for use moment magnitudes.
 *
 * @author Peter Powers
 */
final class ToroEtAl_1997_Mw extends ToroEtAl_1997 {

	public 	static final String NAME = ToroEtAl_1997.NAME + ": Mw";

	ToroEtAl_1997_Mw(IMT imt) {
		super(imt);
	}
	
	@Override boolean isMw() { return true; }

}
