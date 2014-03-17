package org.opensha.gmm;

import java.lang.reflect.Constructor;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

import org.opensha.gmm.CEUS_Mb.*;

import com.google.common.collect.Sets;

/**
 * Ground motion model (GMM) identifiers that can supply instances of various
 * ground motion model implementations.
 *
 * @author Peter Powers
 */
@SuppressWarnings("javadoc")
public enum GMM {
	
	// @formatter:off

	// NGA-West1 NSHMP 2008
	BA_08(BooreAtkinson_2008.class),
	CB_08(CampbellBozorgnia_2008.class),
	CY_08(ChiouYoungs_2008.class),
	
	// NGA-West2 NSHMP 2014
	ASK_14(AbrahamsonEtAl_2014.class),
	BSSA_14(BooreEtAl_2014.class),
	CB_14(CampbellBozorgnia_2014.class),
	CY_14(ChiouYoungs_2014.class),
	IDRISS_14(Idriss_2014.class),
	
	// Subduction NSHMP 2008 2014
	AB_03_GLOB_INTER(AtkinsonBoore_2003_GlobalInterface.class),
	AB_03_GLOB_SLAB(AtkinsonBoore_2003_GlobalSlab.class),
	AB_03_CASC_INTER(AtkinsonBoore_2003_CascadiaInterface.class),
	AB_03_CASC_SLAB(AtkinsonBoore_2003_CascadiaSlab.class),
	AM_09_INTER(AtkinsonMacias_2009.class),
	BCHYDRO_12_INTER(BCHydro_2012_Interface.class),
	BCHYDRO_12_SLAB(BCHydro_2012_Slab.class),
	YOUNGS_97_INTER(YoungsEtAl_1997_Interface.class),
	YOUNGS_97_SLAB(YoungsEtAl_1997_Slab.class),
	ZHAO_06_INTER(ZhaoEtAl_2006_Interface.class),
	ZHAO_06_SLAB(ZhaoEtAl_2006_Slab.class),
	
	/*
	 * Base implementations of the GMM used in the 2008 CEUS model all work with
	 * and assume magnitude = Mw. The method converter() is provided to allow
	 * subclasses to impose a conversion if necessary.
	 * 
	 * All CEUS models impose a clamp on median ground motions; see
	 * GMM_Utils.ceusMeanClip()
	 */
	
	// Stable continent (CEUS) NSHMP 2008 2014
	AB_06_PRIME(AtkinsonBoore_2006p.class),
	AB_06_140BAR(AtkinsonBoore_2006_140bar.class),
	AB_06_200BAR(AtkinsonBoore_2006_200bar.class),
	ATKINSON_08_PRIME(Atkinson_2008p.class),
	CAMPBELL_03(Campbell_2003.class),
	FRANKEL_96(FrankelEtAl_1996.class),
	PEZESHK_11(PezeshkEtAl_2011.class),
	SILVA_02(SilvaEtAl_2002.class),
	SOMERVILLE_01(SomervilleEtAl_2001.class),
	TP_05(TavakoliPezeshk_2005.class),
	TORO_97_MW(ToroEtAl_1997_Mw.class),
	
	// mag converting flavors of CEUS, NSHMP 2008
	//		- Johnston
	AB_06_140BAR_J(AtkinsonBoore_2006_140bar_J.class),
	AB_06_200BAR_J(AtkinsonBoore_2006_200bar_J.class),
	CAMPBELL_03_J(Campbell_2003_J.class),
	FRANKEL_96_J(FrankelEtAl_1996_J.class),
	SILVA_02_J(SilvaEtAl_2002_J.class),
	TP_05_J(TavakoliPezeshk_2005_J.class),
	//		- Atkinson Boore
	AB_06_140BAR_AB(AtkinsonBoore_2006_140bar_AB.class),
	AB_06_200BAR_AB(AtkinsonBoore_2006_200bar_AB.class),
	CAMPBELL_03_AB(Campbell_2003_AB.class),
	FRANKEL_96_AB(FrankelEtAl_1996_AB.class),
	SILVA_02_AB(SilvaEtAl_2002_AB.class),
	TP_05_AB(TavakoliPezeshk_2005_AB.class),
	//		- not specified
	TORO_97_MB(ToroEtAl_1997_Mb.class);
	
	// Other
	// GK_2013(GraizerKalkan_2013.class);

	// @formatter:on
	
	private Class<? extends GroundMotionModel> delegate;
	private String name;
	private Set<IMT> imts;

	private GMM(Class<? extends GroundMotionModel> delegate) {
		this.delegate = delegate;

		// TODO cleanup; implement and test logging

//		System.out.println(this.name());
		name = (String) readField(delegate, "NAME");	
//		System.out.println(name);
		CoefficientContainer cc = (CoefficientContainer) readField(delegate, "CC");
		imts = cc.imtSet();
//		System.out.println(imts);
//		System.out.println();

	}
	
	private static Object readField(Class<? extends GroundMotionModel> clazz, String id) {
		try {
			return clazz.getField(id).get(null);
		} catch (NoSuchFieldException nsfe) {
			// TODO init logging
			System.err.println(errMssg(clazz.getName(), id));
			nsfe.printStackTrace();
			System.exit(1);
		} catch (IllegalAccessException iae) {
			System.err.println(errMssg(clazz.getName(), id));
			iae.printStackTrace();
			System.exit(1);
		}
		return null;
	}
	
	private static String errMssg(String clazz, String id) {
		return "Required '" + id + "' field is hidden in " + clazz;
	}
	
	@Override
	public String toString() {
		return name;
	}
		
	/**
	 * Creates a new implementation instance of this ground motion model.
	 * @param imt intensity measure type of instance
	 * @return the model implementation
	 */
	public GroundMotionModel instance(IMT imt) {
		try {
			Constructor<? extends GroundMotionModel> con = delegate
				.getDeclaredConstructor(IMT.class);
			GroundMotionModel gmm = con.newInstance(imt);
			return gmm;
		} catch (Exception e) {
			// TODO init logging
			e.printStackTrace();
			return null;
		}
	}
	
	/**
	 * Returns the {@code Set} of the intensity measure types (IMTs) supported
	 * by this GMM.
	 * @return the {@code Set} of supported IMTs
	 */
	public Set<IMT> supportedIMTs() {
		return imts;
	}
	
	/**
	 * Returns the {@code Set} of the intensity measure types (IMTs) supported
	 * by all of the supplied GMMs.
	 * @param gmms models for which to return common IMT supoort
	 * @return the {@code Set} of supported IMTs
	 */
	public static Set<IMT> supportedIMTs(Collection<GMM> gmms) {
		Set<IMT> imts = EnumSet.allOf(IMT.class);
		for (GMM gmm : gmms) {
			imts = Sets.intersection(imts, gmm.supportedIMTs());
		}
		return EnumSet.copyOf(imts);
	}
	
	/**
	 * Returns the set of spectral acceleration IMTs that are common to the
	 * supplied {@code Collection}.
	 * @param gmms ground motion models
	 * @return a {@code Set} of common spectral acceleration IMTs
	 */
	public static Set<IMT> responseSpectrumIMTs(Collection<GMM> gmms) {
		return Sets.intersection(supportedIMTs(gmms), IMT.saIMTs());
	}
	
}