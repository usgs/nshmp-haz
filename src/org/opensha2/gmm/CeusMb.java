package org.opensha2.gmm;

import static org.opensha2.gmm.MagConverter.MB_TO_MW_JOHNSTON;
import static org.opensha2.gmm.MagConverter.MB_TO_MW_ATKIN_BOORE;

/**
 * Wrapper class for CEUS Gmm flavors that do magnitude conversions from
 * m<sub>b</sub> to M<sub>w</sub>. For use with 2008 CEUS grid sources only.
 * 
 * @author Peter Powers
 */
class CeusMb {

  private static final String J_NAME = ": mb [J]";
  private static final String AB_NAME = ": mb [AB]";

  // @formatter:off
	
	static final class AtkinsonBoore_2006_140bar_J extends AtkinsonBoore_2006.StressDrop_140bar {
		static final String NAME = AtkinsonBoore_2006.StressDrop_140bar.NAME + J_NAME;
		AtkinsonBoore_2006_140bar_J(Imt imt) { super(imt); }
		@Override public MagConverter converter() { return MB_TO_MW_JOHNSTON; }
	}
	
	static final class AtkinsonBoore_2006_140bar_AB extends AtkinsonBoore_2006.StressDrop_140bar {
		static final String NAME = AtkinsonBoore_2006.StressDrop_140bar.NAME + AB_NAME;
		AtkinsonBoore_2006_140bar_AB(Imt imt) { super(imt); }
		@Override public MagConverter converter() { return MB_TO_MW_ATKIN_BOORE; }
	}

	
	
	static final class AtkinsonBoore_2006_200bar_J extends AtkinsonBoore_2006.StressDrop_200bar {
		static final String NAME = AtkinsonBoore_2006.StressDrop_200bar.NAME + J_NAME;
		AtkinsonBoore_2006_200bar_J(Imt imt) { super(imt); }
		@Override public MagConverter converter() { return MB_TO_MW_JOHNSTON; }
	}
	
	static final class AtkinsonBoore_2006_200bar_AB extends AtkinsonBoore_2006.StressDrop_200bar {
		static final String NAME = AtkinsonBoore_2006.StressDrop_200bar.NAME + AB_NAME;
		AtkinsonBoore_2006_200bar_AB(Imt imt) { super(imt); }
		@Override public MagConverter converter() { return MB_TO_MW_ATKIN_BOORE; }
	}

	
	
	static final class Campbell_2003_J extends Campbell_2003 {
		static final String NAME = Campbell_2003.NAME + J_NAME;
		Campbell_2003_J(Imt imt) { super(imt); }
		@Override public MagConverter converter() { return MB_TO_MW_JOHNSTON; }
	}
	
	static final class Campbell_2003_AB extends Campbell_2003 {
		static final String NAME = Campbell_2003.NAME + AB_NAME;
		Campbell_2003_AB(Imt imt) { super(imt); }
		@Override public MagConverter converter() { return MB_TO_MW_ATKIN_BOORE; }
	}

	
	
	static final class FrankelEtAl_1996_J extends FrankelEtAl_1996 {
		static final String NAME = FrankelEtAl_1996.NAME + J_NAME;
		FrankelEtAl_1996_J(Imt imt) { super(imt); }
		@Override public MagConverter converter() { return MB_TO_MW_JOHNSTON; }
	}
	
	static final class FrankelEtAl_1996_AB extends FrankelEtAl_1996 {
		static final String NAME = FrankelEtAl_1996.NAME + AB_NAME;
		FrankelEtAl_1996_AB(Imt imt) { super(imt); }
		@Override public MagConverter converter() { return MB_TO_MW_ATKIN_BOORE; }
	}

	
	
	static final class SilvaEtAl_2002_J extends SilvaEtAl_2002 {
		static final String NAME = SilvaEtAl_2002.NAME + J_NAME;
		SilvaEtAl_2002_J(Imt imt) { super(imt); }
		@Override public MagConverter converter() { return MB_TO_MW_JOHNSTON; }
	}
	
	static final class SilvaEtAl_2002_AB extends SilvaEtAl_2002 {
		static final String NAME = SilvaEtAl_2002.NAME + AB_NAME;
		SilvaEtAl_2002_AB(Imt imt) { super(imt); }
		@Override public MagConverter converter() { return MB_TO_MW_ATKIN_BOORE; }
	}
	
	
	
	static final class TavakoliPezeshk_2005_J extends TavakoliPezeshk_2005 {
		static final String NAME = TavakoliPezeshk_2005.NAME + J_NAME;
		TavakoliPezeshk_2005_J(Imt imt) { super(imt); }
		@Override public MagConverter converter() { return MB_TO_MW_JOHNSTON; }
	}
	
	static final class TavakoliPezeshk_2005_AB extends TavakoliPezeshk_2005 {
		static final String NAME = TavakoliPezeshk_2005.NAME + AB_NAME;
		TavakoliPezeshk_2005_AB(Imt imt) { super(imt); }
		@Override public MagConverter converter() { return MB_TO_MW_ATKIN_BOORE; }
	}
	
}
