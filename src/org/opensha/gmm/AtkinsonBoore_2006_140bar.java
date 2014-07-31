package org.opensha.gmm;

/**
 * Concrete implementation of Atkinson &amp; Boore (2006) for a 140bar stress
 * drop.
 * 
 * @author Peter Powers
 * @see AtkinsonBoore_2006
 */
class AtkinsonBoore_2006_140bar extends AtkinsonBoore_2006 {

	static final String NAME = AtkinsonBoore_2006.NAME + ": 140 bar";

	private static final double STRESS = 140.0;
	private static final double SF2;

	static {
		SF2 = scaleFactorCalc(STRESS);
	}

	AtkinsonBoore_2006_140bar(Imt imt) {
		super(imt);
	}

	@Override double scaleFactor() {
		return SF2;
	}

}
