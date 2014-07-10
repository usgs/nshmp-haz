package org.opensha.gmm;

import org.opensha.eq.TectonicSetting;

/**
 * Concrete implementation of McVerry et al. (2000) for active crustal fault
 * sources.
 * 
 * @author Peter Powers
 * @see McVerryEtAl_2000
 */
final class McVerryEtAl_2000_Crustal extends McVerryEtAl_2000 {

	final static String NAME = McVerryEtAl_2000.NAME + ": Crustal";

	McVerryEtAl_2000_Crustal(Imt imt) {
		super(imt);
	}

	@Override boolean isGeomean() {
		return false;
	}

	@Override TectonicSetting tectonicSetting() {
		return TectonicSetting.ACTIVE_SHALLOW_CRUST;
	}

}
