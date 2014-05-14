package org.opensha.gmm;

/**
 * Concrete implementation of Atkinson &amp; Boore (2003) for Cascadia
 * subduction interface events.
 * 
 * @author Peter Powers
 * @see AtkinsonBoore_2003
 */
final class AtkinsonBoore_2003_CascadiaInterface extends AtkinsonBoore_2003 {
	
	final static String NAME = AtkinsonBoore_2003.NAME +
		": Cascadia Interface";
			
	AtkinsonBoore_2003_CascadiaInterface(IMT imt) {
		super(imt);
	}

	@Override final boolean isGlobal() { return false; }

	@Override final boolean isSlab() { return false; }

}
