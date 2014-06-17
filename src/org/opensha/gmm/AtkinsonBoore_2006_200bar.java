package org.opensha.gmm;

/**
 * Concrete implementation of Atkinson &amp; Boore (2006) for a 200bar stress
 * drop.
 * 
 * @author Peter Powers
 * @see AtkinsonBoore_2006
 */
class AtkinsonBoore_2006_200bar extends AtkinsonBoore_2006 {

	static final String NAME = AtkinsonBoore_2006.NAME + ": 200 bar";

	private static final double STRESS = 200;
	private static final double SF2;

	static {
		SF2 = scaleFactorCalc(STRESS);
	}

	AtkinsonBoore_2006_200bar(Imt imt) {
		super(imt);
	}

	@Override double scaleFactor() {
		return SF2;
	}

}
