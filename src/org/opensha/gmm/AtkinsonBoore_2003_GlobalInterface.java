package org.opensha.gmm;

/**
 * Concrete implementation of Atkinson &amp; Boore (2003) for global
 * subduction interface events.
 * 
 * @author Peter Powers
 * @see AtkinsonBoore_2003
 */
final class AtkinsonBoore_2003_GlobalInterface extends AtkinsonBoore_2003 {
	
	final static String NAME = AtkinsonBoore_2003.NAME +
		": Global Interface";
	
	AtkinsonBoore_2003_GlobalInterface(Imt imt) {
		super(imt);
	}

	@Override final boolean isGlobal() { return true; }

	@Override final boolean isSlab() { return false; }

}
