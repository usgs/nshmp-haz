package org.opensha.gmm;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

import org.opensha.gmm.CEUS_Mb.AtkinsonBoore_2006_140bar_AB;
import org.opensha.gmm.CEUS_Mb.AtkinsonBoore_2006_140bar_J;
import org.opensha.gmm.CEUS_Mb.AtkinsonBoore_2006_200bar_AB;
import org.opensha.gmm.CEUS_Mb.AtkinsonBoore_2006_200bar_J;
import org.opensha.gmm.CEUS_Mb.Campbell_2003_AB;
import org.opensha.gmm.CEUS_Mb.Campbell_2003_J;
import org.opensha.gmm.CEUS_Mb.FrankelEtAl_1996_AB;
import org.opensha.gmm.CEUS_Mb.FrankelEtAl_1996_J;
import org.opensha.gmm.CEUS_Mb.SilvaEtAl_2002_AB;
import org.opensha.gmm.CEUS_Mb.SilvaEtAl_2002_J;
import org.opensha.gmm.CEUS_Mb.TavakoliPezeshk_2005_AB;
import org.opensha.gmm.CEUS_Mb.TavakoliPezeshk_2005_J;

import com.google.common.collect.Sets;

/**
 * Add comments here
 *
 * @author Peter Powers
 */
public enum TestEnum {
	
	// TODO review sub zTop rules; I believe there are places where zHyp is spoecified by a model but zTop is supplied instead
	//			Zhao in hazgrid uses zTop for zHyp
	// TODO implement AB03 taper developed by SH; gms at 2s and 3s are much too high at large distances
	// TODO sub GMMs were reweighted ??
	// TODO do deep GMMs saturate at 7.8 ???
	// TODO do SUB GMM's float downdip AND along strike, or jsut along strike?
	// TODO how to deal with CEUS distance cutoffs (@ 500km):
	//			- could specify applicable distances and weights in gmm.xml
	//			- could break sources in two, with distance-specific GMMS returning 0 if out of range (seems ugly)
	// TODO check CEUS clamp consistency; should be PGA = 3g, <=0.5s = 6g, else = 0g (which means no clamp applied)
	//			- Cb03 was (incorrectly) changed from 3g at 0.5s to 0g instead of 6g ??
	//			- Somerville has 6g clamp at 2s ???
	// TODO most CEUS GMM's have 0.4s coeffs that were linearly interpolated for special NRC project; consider removing them??
	// TODO AB06 has PGV clamp of 460m/s; is this correct? or specified anywhere?
	// TODO revisit hazgrid history to ensure that bugs/fixes from 2008 carried through to 2014 in Fortran
	// TODO Port GMM grid optimization tables
	//
	// TODO Ensure Atkinson sfac/gfac is implemented correctly
	// TODO amean11 (fortran) has wrong median clamp values and short period ranges
	// TODO Toro Mb-Mw conversion (has mb specific implementation
	// TODO finish gmm.xml
	// TODO is Atkinson Macias ok? finished?
	// TODO is there a citation for Atkinson distance decay
	//			mean = mean - 0.3 + 0.15(log(rJB))  (ln or log10 ??)
	// TODO ensure table lookups are using correct distance metric, some are rRup and some are rJB
	// TODO check Fortran minimums (this note may have been written just regarding GMM table lookups, Atkinson in particular)
	//			hazgrid A08' minR=1.8km; P11 minR = 1km; others?
	//			hazfx all (tables?) have minR = 0.11km
	// TODO doublecheck that SUB implementations are using the correct distance metric
	
	
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

	private TestEnum(Class<? extends GroundMotionModel> delegate) {
		this.delegate = delegate;

		// TODO cleanup; implement and test logging
		try {
//		System.out.println(this.name());
		name = (String) readField(delegate, "NAME");	
//		System.out.println(name);
		CoefficientContainer cc = (CoefficientContainer) readField(delegate, "CC");
		imts = cc.imtSet();
//		System.out.println(imts);
//		System.out.println();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	public static void main(String[] args) {
		GMM gmm = GMM.ASK_14;
		System.out.println(Arrays.toString(gmm.getClass().getConstructors()));
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
	
//	/**
//	 * Returns the {@code Set} of the intensity measure types ({@code IMT}s)
//	 * supported by this {@code GMM}.
//	 * @return the {@code Set} of supported {@code IMT}s
//	 */
//	public Set<IMT> supportedIMTs() {
//		return imts;
//	}
//
//	/**
//	 * Returns the {@code Set} of the intensity measure types ({@code IMT}s)
//	 * supported by all of the supplied {@code GMM}s.
//	 * @param gmms models for which to return common {@code IMT} supoort
//	 * @return the {@code Set} of supported {@code IMT}s
//	 */
//	public static Set<IMT> supportedIMTs(Collection<GMM> gmms) {
//		Set<IMT> imts = EnumSet.allOf(IMT.class);
//		for (GMM gmm : gmms) {
//			imts = Sets.intersection(imts, gmm.supportedIMTs());
//		}
//		return EnumSet.copyOf(imts);
//	}
//
//	/**
//	 * Returns the set of spectral acceleration {@code IMT}s that are supported
//	 * by this {@code GMM}.
//	 * @return a {@code Set} of spectral acceleration IMTs
//	 */
//	public Set<IMT> responseSpectrumIMTs() {
//		return Sets.intersection(imts, IMT.saIMTs());
//	}
//
//	/**
//	 * Returns the set of spectral acceleration {@code IMT}s that are common to
//	 * the supplied {@code Collection}.
//	 * @param gmms ground motion models
//	 * @return a {@code Set} of common spectral acceleration {@code IMT}s
//	 */
//	public static Set<IMT> responseSpectrumIMTs(Collection<GMM> gmms) {
//		return Sets.intersection(supportedIMTs(gmms), IMT.saIMTs());
//	}
//

}
