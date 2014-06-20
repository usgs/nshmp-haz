package org.opensha.gmm;

import java.lang.reflect.Constructor;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import org.opensha.gmm.CeusMb.*;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.UncheckedExecutionException;

/**
 * {@link GroundMotionModel} (Gmm) identifiers. Use these to generate
 * {@link Imt}-specific instances via {@link Gmm#instance(Imt)}. Single or
 * corporate authored models are identified as NAME_YR_FLAVOR; multi-author
 * models as INITIALS_YR_FLAVOR. FLAVOR is only used for those models with
 * region specific implementations or other variants.
 * 
 * @author Peter Powers
 */
public enum Gmm {

	// TODO implement AB03 taper developed by SH; gms at 2s and 3s are much too
	// high at large distances

	// TODO do deep GMMs saturate at 7.8 ???

	// TODO how to deal with CEUS distance cutoffs (@ 500km):
	// - could specify applicable distances and weights in gmm.xml
	// - could break sources in two, with distance-specific GMMS returning 0 if
	// out of range (seems ugly)

	// TODO check CEUS clamp consistency; should be PGA = 3g, <=0.5s = 6g, else
	// = 0g (which means no clamp applied)
	// - Cb03 was (incorrectly) changed from 3g at 0.5s to 0g instead of 6g ??
	// - Somerville has 6g clamp at 2s ???

	// TODO most CEUS Gmm's have 0.4s coeffs that were linearly interpolated for
	// special NRC project; consider removing them??

	// TODO AB06 has PGV clamp of 460m/s; is this correct? or specified
	// anywhere?

	// TODO revisit hazgrid history to ensure that bugs/fixes from 2008 carried
	// through to 2014 in Fortran

	// TODO Port Gmm grid optimization tables

	// TODO Ensure Atkinson sfac/gfac is implemented correctly
	// TODO amean11 (fortran) has wrong median clamp values and short period
	// ranges

	// TODO is Atkinson Macias ok? finished?
	// TODO is there a citation for Atkinson distance decay
	// mean = mean - 0.3 + 0.15(log(rJB)) (ln or log10 ??)

	// TODO ensure table lookups are using correct distance metric, some are
	// rRup and some are rJB

	// TODO check Fortran minimums (this note may have been written just
	// regarding Gmm table lookups, Atkinson in particular)
	// hazgrid A08' minR=1.8km; P11 minR = 1km; others?
	// hazfx all (tables?) have minR = 0.11km

	// TODO z1p0 in CY08 - this is now always km, CY08 needs updating (from m)

	// * TODO Verify that Campbell03 imposes max(dtor,5); he does require rRup;
	// why is
	// * depth constrained as such in hazgrid? As with somerville, no depth is
	// imposed
	// * in hazFX - make sure 0.01 as PGA is handled corectly; may require
	// change to
	// * period = 0.0

	// NGA-West1 NSHMP 2008

	/** @see BooreAtkinson_2008 */
	BA_08(BooreAtkinson_2008.class, BooreAtkinson_2008.NAME, BooreAtkinson_2008.CC),

	/** @see CampbellBozorgnia_2008 */
	CB_08(CampbellBozorgnia_2008.class, CampbellBozorgnia_2008.NAME, CampbellBozorgnia_2008.CC),

	/** @see ChiouYoungs_2008 */
	CY_08(ChiouYoungs_2008.class, ChiouYoungs_2008.NAME, ChiouYoungs_2008.CC),

	// NGA-West2 NSHMP 2014

	/** @see AbrahamsonEtAl_2014 */
	ASK_14(AbrahamsonEtAl_2014.class, AbrahamsonEtAl_2014.NAME, AbrahamsonEtAl_2014.CC),

	/** @see BooreEtAl_2014 */
	BSSA_14(BooreEtAl_2014.class, BooreEtAl_2014.NAME, BooreEtAl_2014.CC),

	/** @see CampbellBozorgnia_2014 */
	CB_14(CampbellBozorgnia_2014.class, CampbellBozorgnia_2014.NAME, CampbellBozorgnia_2014.CC),

	/** @see ChiouYoungs_2014 */
	CY_14(ChiouYoungs_2014.class, ChiouYoungs_2014.NAME, ChiouYoungs_2014.CC),

	/** @see Idriss_2014 */
	IDRISS_14(Idriss_2014.class, Idriss_2014.NAME, Idriss_2014.CC),

	// Subduction NSHMP 2008 2014

	/** @see AtkinsonBoore_2003 */
	AB_03_GLOB_INTER(AtkinsonBoore_2003_GlobalInterface.class,
			AtkinsonBoore_2003_GlobalInterface.NAME, AtkinsonBoore_2003.CC),

	/** @see AtkinsonBoore_2003 */
	AB_03_GLOB_SLAB(AtkinsonBoore_2003_GlobalSlab.class, AtkinsonBoore_2003_GlobalSlab.NAME,
			AtkinsonBoore_2003.CC),

	/** @see AtkinsonBoore_2003 */
	AB_03_CASC_INTER(AtkinsonBoore_2003_CascadiaInterface.class,
			AtkinsonBoore_2003_CascadiaInterface.NAME, AtkinsonBoore_2003.CC),

	/** @see AtkinsonBoore_2003 */
	AB_03_CASC_SLAB(AtkinsonBoore_2003_CascadiaSlab.class, AtkinsonBoore_2003_CascadiaSlab.NAME,
			AtkinsonBoore_2003.CC),

	/** @see AtkinsonMacias_2009 */
	AM_09_INTER(AtkinsonMacias_2009.class, AtkinsonMacias_2009.NAME, AtkinsonMacias_2009.CC),

	/** @see BcHydro_2012 */
	BCHYDRO_12_INTER(BcHydro_2012_Interface.class, BcHydro_2012_Interface.NAME, BcHydro_2012.CC),

	/** @see BcHydro_2012 */
	BCHYDRO_12_SLAB(BcHydro_2012_Slab.class, BcHydro_2012_Slab.NAME, BcHydro_2012.CC),

	/** @see YoungsEtAl_1997 */
	YOUNGS_97_INTER(YoungsEtAl_1997_Interface.class, YoungsEtAl_1997_Interface.NAME,
			YoungsEtAl_1997.CC),

	/** @see YoungsEtAl_1997 */
	YOUNGS_97_SLAB(YoungsEtAl_1997_Slab.class, YoungsEtAl_1997_Slab.NAME, YoungsEtAl_1997.CC),

	/** @see ZhaoEtAl_2006 */
	ZHAO_06_INTER(ZhaoEtAl_2006_Interface.class, ZhaoEtAl_2006_Interface.NAME, ZhaoEtAl_2006.CC),

	/** @see ZhaoEtAl_2006 */
	ZHAO_06_SLAB(ZhaoEtAl_2006_Slab.class, ZhaoEtAl_2006_Slab.NAME, ZhaoEtAl_2006.CC),

	/*
	 * Base implementations of the Gmm used in the 2008 CEUS model all work with
	 * and assume magnitude = Mw. The method converter() is provided to allow
	 * subclasses to impose a conversion if necessary.
	 * 
	 * All CEUS models impose a clamp on median ground motions; see
	 * GmmUtils.ceusMeanClip()
	 */

	// Stable continent (CEUS) NSHMP 2008 2014

	/** @see AtkinsonBoore_2006p */
	AB_06_PRIME(AtkinsonBoore_2006p.class, AtkinsonBoore_2006p.NAME, AtkinsonBoore_2006p.CC),

	/** @see AtkinsonBoore_2006 */
	AB_06_140BAR(AtkinsonBoore_2006_140bar.class, AtkinsonBoore_2006_140bar.NAME,
			AtkinsonBoore_2006.CC),

	/** @see AtkinsonBoore_2006 */
	AB_06_200BAR(AtkinsonBoore_2006_200bar.class, AtkinsonBoore_2006_200bar.NAME,
			AtkinsonBoore_2006.CC),

	/** @see Atkinson_2008p */
	ATKINSON_08_PRIME(Atkinson_2008p.class, Atkinson_2008p.NAME, Atkinson_2008p.CC),

	/** @see Campbell_2003 */
	CAMPBELL_03(Campbell_2003.class, Campbell_2003.NAME, Campbell_2003.CC),

	/** @see FrankelEtAl_1996 */
	FRANKEL_96(FrankelEtAl_1996.class, FrankelEtAl_1996.NAME, FrankelEtAl_1996.CC),

	/** @see PezeshkEtAl_2011 */
	PEZESHK_11(PezeshkEtAl_2011.class, PezeshkEtAl_2011.NAME, PezeshkEtAl_2011.CC),

	/** @see SilvaEtAl_2002 */
	SILVA_02(SilvaEtAl_2002.class, SilvaEtAl_2002.NAME, SilvaEtAl_2002.CC),

	/** @see SomervilleEtAl_2001 */
	SOMERVILLE_01(SomervilleEtAl_2001.class, SomervilleEtAl_2001.NAME, SomervilleEtAl_2001.CC),

	/** @see TavakoliPezeshk_2005 */
	TP_05(TavakoliPezeshk_2005.class, TavakoliPezeshk_2005.NAME, TavakoliPezeshk_2005.CC),

	/** @see ToroEtAl_1997 */
	TORO_97_MW(ToroEtAl_1997_Mw.class, ToroEtAl_1997_Mw.NAME, ToroEtAl_1997.CC),

	// Johnston mag converting flavors of CEUS, NSHMP 2008

	/** @see AtkinsonBoore_2006 */
	AB_06_140BAR_J(AtkinsonBoore_2006_140bar_J.class, AtkinsonBoore_2006_140bar_J.NAME,
			AtkinsonBoore_2006.CC),

	/** @see AtkinsonBoore_2006 */
	AB_06_200BAR_J(AtkinsonBoore_2006_200bar_J.class, AtkinsonBoore_2006_200bar_J.NAME,
			AtkinsonBoore_2006.CC),

	/** @see Campbell_2003 */
	CAMPBELL_03_J(Campbell_2003_J.class, Campbell_2003_J.NAME, Campbell_2003.CC),

	/** @see FrankelEtAl_1996 */
	FRANKEL_96_J(FrankelEtAl_1996_J.class, FrankelEtAl_1996_J.NAME, FrankelEtAl_1996.CC),

	/** @see SilvaEtAl_2002 */
	SILVA_02_J(SilvaEtAl_2002_J.class, SilvaEtAl_2002_J.NAME, SilvaEtAl_2002.CC),

	/** @see TavakoliPezeshk_2005 */
	TP_05_J(TavakoliPezeshk_2005_J.class, TavakoliPezeshk_2005_J.NAME, TavakoliPezeshk_2005.CC),

	// Atkinson Boore mag converting flavors of CEUS, NSHMP 2008

	/** @see AtkinsonBoore_2006 */
	AB_06_140BAR_AB(AtkinsonBoore_2006_140bar_AB.class, AtkinsonBoore_2006_140bar_AB.NAME,
			AtkinsonBoore_2006.CC),

	/** @see AtkinsonBoore_2006 */
	AB_06_200BAR_AB(AtkinsonBoore_2006_200bar_AB.class, AtkinsonBoore_2006_200bar_AB.NAME,
			AtkinsonBoore_2006.CC),

	/** @see Campbell_2003 */
	CAMPBELL_03_AB(Campbell_2003_AB.class, Campbell_2003_AB.NAME, Campbell_2003.CC),

	/** @see FrankelEtAl_1996 */
	FRANKEL_96_AB(FrankelEtAl_1996_AB.class, FrankelEtAl_1996_AB.NAME, FrankelEtAl_1996.CC),

	/** @see SilvaEtAl_2002 */
	SILVA_02_AB(SilvaEtAl_2002_AB.class, SilvaEtAl_2002_AB.NAME, SilvaEtAl_2002.CC),

	/** @see TavakoliPezeshk_2005 */
	TP_05_AB(TavakoliPezeshk_2005_AB.class, TavakoliPezeshk_2005_AB.NAME, TavakoliPezeshk_2005.CC),

	// - not specified
	/** @see ToroEtAl_1997 */
	TORO_97_MB(ToroEtAl_1997_Mb.class, ToroEtAl_1997_Mb.NAME, ToroEtAl_1997.CC);

	// Other TODO clean?
	// GK_2013(GraizerKalkan_2013.class);

	// TODO all the methods of this class need argument checking and unit tests

	private final Class<? extends GroundMotionModel> delegate;
	private final String name;
	private final Set<Imt> imts;
	private final LoadingCache<Imt, GroundMotionModel> cache;

	private Gmm(Class<? extends GroundMotionModel> delegate, String name, CoefficientContainer cc) {
		this.delegate = delegate;
		this.name = name;
		imts = cc.imtSet();
		cache = CacheBuilder.newBuilder().build(new CacheLoader<Imt, GroundMotionModel>() {
			@Override public GroundMotionModel load(Imt imt) throws Exception {
				return createInstance(imt);
			}
		});
	}

	private GroundMotionModel createInstance(Imt imt) throws Exception {
		Constructor<? extends GroundMotionModel> con = delegate.getDeclaredConstructor(Imt.class);
		GroundMotionModel gmm = con.newInstance(imt);
		return gmm;
	}

	/**
	 * Retreives an instance of a {@code GroundMotionModel}, either by creating
	 * a new one, or fetching from a cache.
	 * @param imt intensity measure type of instance
	 * @return the model implementation
	 * @throws UncheckedExecutionException if there is an instantiation problem
	 */
	public GroundMotionModel instance(Imt imt) {
		return cache.getUnchecked(imt);
	}

	/**
	 * Retrieves multiple {@code GroundMotionModel} instances, either by
	 * creating a new ones, or fetching them from a cache.
	 * @param gmms to retieve
	 * @param imt
	 * @return a {@code Map} of {@code GroundMotionModel} instances
	 * @throws UncheckedExecutionException if there is an instantiation problem
	 */
	public static Map<Gmm, GroundMotionModel> instances(Set<Gmm> gmms, Imt imt) {
		Map<Gmm, GroundMotionModel> instances = Maps.newEnumMap(Gmm.class);
		for (Gmm gmm : gmms) {
			instances.put(gmm, gmm.instance(imt));
		}
		return instances;
	}

	@Override public String toString() {
		return name;
	}

	/**
	 * Returns the {@code Set} of the intensity measure types ({@code Imt}s)
	 * supported by this {@code Gmm}.
	 * @return the {@code Set} of supported {@code Imt}s
	 */
	public Set<Imt> supportedIMTs() {
		return imts;
	}

	/**
	 * Returns the {@code Set} of the intensity measure types ({@code Imt}s)
	 * supported by all of the supplied {@code Gmm}s.
	 * @param gmms models for which to return common {@code Imt} supoort
	 * @return the {@code Set} of supported {@code Imt}s
	 */
	public static Set<Imt> supportedIMTs(Collection<Gmm> gmms) {
		Set<Imt> imts = EnumSet.allOf(Imt.class);
		for (Gmm gmm : gmms) {
			imts = Sets.intersection(imts, gmm.supportedIMTs());
		}
		return EnumSet.copyOf(imts);
	}

	/**
	 * Returns the set of spectral acceleration {@code Imt}s that are supported
	 * by this {@code Gmm}.
	 * @return a {@code Set} of spectral acceleration IMTs
	 */
	public Set<Imt> responseSpectrumIMTs() {
		return Sets.intersection(imts, Imt.saImts());
	}

	/**
	 * Returns the set of spectral acceleration {@code Imt}s that are common to
	 * the supplied {@code Collection}.
	 * @param gmms ground motion models
	 * @return a {@code Set} of common spectral acceleration {@code Imt}s
	 */
	public static Set<Imt> responseSpectrumIMTs(Collection<Gmm> gmms) {
		return Sets.intersection(supportedIMTs(gmms), Imt.saImts());
	}

}
