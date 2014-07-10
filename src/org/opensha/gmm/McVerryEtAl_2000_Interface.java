package org.opensha.gmm;

import org.opensha.eq.TectonicSetting;

/**
 * Concrete implementation of McVerry et al. (2000) for subduction interface
 * sources.
 * 
 * @author Peter Powers
 * @see McVerryEtAl_2000
 */
final class McVerryEtAl_2000_Interface extends McVerryEtAl_2000 {

	final static String NAME = McVerryEtAl_2000.NAME + ": Interface";

	McVerryEtAl_2000_Interface(Imt imt) {
		super(imt);
	}

	@Override boolean isGeomean() {
		return false;
	}

	@Override TectonicSetting tectonicSetting() {
		return TectonicSetting.SUBDUCTION_INTERFACE;
	}

}
