package org.opensha.gmm;

/**
 * Concrete implementation of Zhao et al. (2006) for subduction intraslab
 * events.
 * 
 * @author Peter Powers
 * @see ZhaoEtAl_2006
 */
final class ZhaoEtAl_2006_Slab extends ZhaoEtAl_2006 {
	
	static final String NAME = ZhaoEtAl_2006.NAME + ": Slab";

	ZhaoEtAl_2006_Slab(Imt imt) {
		super(imt);
	}

	@Override final boolean isSlab() { return true; }

}
