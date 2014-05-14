package org.opensha.gmm;

/**
 * Concrete implementation of Atkinson &amp; Boore (2003) for global
 * subduction intraslab events.
 * 
 * @author Peter Powers
 * @see AtkinsonBoore_2003
 */
final class AtkinsonBoore_2003_GlobalSlab extends AtkinsonBoore_2003 {
	
	final static String NAME = AtkinsonBoore_2003.NAME + ": Global Slab";

	AtkinsonBoore_2003_GlobalSlab(IMT imt) {
		super(imt);
	}

	@Override final boolean isGlobal() { return true; }

	@Override final boolean isSlab() { return true; }

}
