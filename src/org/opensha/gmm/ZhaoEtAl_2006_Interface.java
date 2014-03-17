package org.opensha.gmm;

/**
 * Concrete implementation of Zhao et al. (2006) for subduction interface
 * events.
 * 
 * @author Peter Powers
 * @see ZhaoEtAl_2006
 */
final class ZhaoEtAl_2006_Interface extends ZhaoEtAl_2006 {
	
	public static final String NAME = ZhaoEtAl_2006.NAME + ": Interface";

	ZhaoEtAl_2006_Interface(IMT imt) {
		super(imt);
	}

	@Override final boolean isSlab() { return false; }

}
