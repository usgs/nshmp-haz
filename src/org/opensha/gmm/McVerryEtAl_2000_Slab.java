package org.opensha.gmm;

import org.opensha.eq.TectonicSetting;

/**
 * Concrete implementation of McVerry et al. (2000) for subduction intraslab
 * sources.
 * 
 * @author Peter Powers
 * @see McVerryEtAl_2000
 */
final class McVerryEtAl_2000_Slab extends McVerryEtAl_2000 {

	final static String NAME = McVerryEtAl_2000.NAME + ": Slab";

	McVerryEtAl_2000_Slab(Imt imt) {
		super(imt);
	}

	@Override boolean isGeomean() {
		return false;
	}

	@Override TectonicSetting tectonicSetting() {
		return TectonicSetting.SUBDUCTION_INTRASLAB;
	}

}
