package org.opensha.gmm;

/**
 * Concrete implementation of Youngs et al. (1997) for subduction interface
 * events.
 * 
 * @author Peter Powers
 * @see YoungsEtAl_1997
 */
final class YoungsEtAl_1997_Interface extends YoungsEtAl_1997 {
	
	static final String NAME = YoungsEtAl_1997.NAME + ": Interface";

	YoungsEtAl_1997_Interface(IMT imt) {
		super(imt);
	}

	@Override final boolean isSlab() { return false; }

	public static void main(String[] args) {
		System.out.println(YoungsEtAl_1997_Interface.NAME);
	}
}
