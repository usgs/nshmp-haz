package org.opensha.gmm;

import org.opensha.eq.TectonicSetting;

/**
 * Concrete implementation of McVerry et al. (200) for volcanic sources.
 * 
 * @author Peter Powers
 * @see McVerryEtAl_2000
 */
final class McVerryEtAl_2000_Volcanic extends McVerryEtAl_2000 {

	final static String NAME = McVerryEtAl_2000.NAME + ": Volcanic";

	McVerryEtAl_2000_Volcanic(Imt imt) {
		super(imt);
	}

	@Override boolean isGeomean() {
		return false;
	}

	@Override TectonicSetting tectonicSetting() {
		return TectonicSetting.VOLCANIC;
	}

}
