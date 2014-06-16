package org.opensha.gmm;

/**
 * Concrete implementation of Atkinson &amp; Boore (2003) for Cascadia
 * subduction intraslab events.
 * 
 * @author Peter Powers
 * @see AtkinsonBoore_2003
 */
final class AtkinsonBoore_2003_CascadiaSlab extends AtkinsonBoore_2003 {
	
	final static String NAME = AtkinsonBoore_2003.NAME +
		": Cascadia Slab";

	AtkinsonBoore_2003_CascadiaSlab(Imt imt) {
		super(imt);
	}

	@Override final boolean isGlobal() { return false; }

	@Override final boolean isSlab() { return true; }

}
