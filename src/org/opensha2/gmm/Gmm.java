package org.opensha2.gmm;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.lang.reflect.Constructor;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.opensha2.gmm.GmmInput.Constraints;
import org.opensha2.gmm.CeusMb.*;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.UncheckedExecutionException;

/**
 * {@link GroundMotionModel} (Gmm) identifiers. Use these to generate
 * {@link Imt}-specific instances via {@link Gmm#instance(Imt)} and related
 * methods. Single or corporate authored models are identified as
 * NAME_YR_FLAVOR; multi-author models as INITIALS_YR_FLAVOR. FLAVOR is only
 * used for those models with region specific implementations or other variants.
 * 
 * @author Peter Powers
 */
public enum Gmm {

	// TODO implement AB03 taper developed by SH; gms at 2s and 3s are much too
	// high at large distances

	// TODO AB06 has PGV clamp of 460m/s; is this correct? or specified
	// anywhere?

	// TODO Verify that Campbell03 imposes max(dtor,5); he does require rRup;
	// why is depth constrained as such in hazgrid? As with somerville, no depth
	// is
	// imposed in hazFX - make sure 0.01 as PGA is handled corectly; may require
	// change to period = 0.0

	// NGA-West1 NSHMP 2008

	/** @see BooreAtkinson_2008 */
	BA_08(
			BooreAtkinson_2008.class,
			BooreAtkinson_2008.NAME,
			BooreAtkinson_2008.COEFFS,
			BooreAtkinson_2008.CONSTRAINTS),

	/** @see CampbellBozorgnia_2008 */
	CB_08(
			CampbellBozorgnia_2008.class,
			CampbellBozorgnia_2008.NAME,
			CampbellBozorgnia_2008.COEFFS,
			CampbellBozorgnia_2008.CONSTRAINTS),

	/** @see ChiouYoungs_2008 */
	CY_08(
			ChiouYoungs_2008.class,
			ChiouYoungs_2008.NAME,
			ChiouYoungs_2008.COEFFS,
			ChiouYoungs_2008.CONSTRAINTS),

	// NGA-West2 NSHMP 2014

	/** @see AbrahamsonEtAl_2014 */
	ASK_14(
			AbrahamsonEtAl_2014.class,
			AbrahamsonEtAl_2014.NAME,
			AbrahamsonEtAl_2014.COEFFS,
			AbrahamsonEtAl_2014.CONSTRAINTS),

	/** @see BooreEtAl_2014 */
	BSSA_14(
			BooreEtAl_2014.class,
			BooreEtAl_2014.NAME,
			BooreEtAl_2014.COEFFS,
			BooreEtAl_2014.CONSTRAINTS),

	/** @see CampbellBozorgnia_2014 */
	CB_14(
			CampbellBozorgnia_2014.class,
			CampbellBozorgnia_2014.NAME,
			CampbellBozorgnia_2014.COEFFS,
			CampbellBozorgnia_2014.CONSTRAINTS),

	/** @see ChiouYoungs_2014 */
	CY_14(
			ChiouYoungs_2014.class,
			ChiouYoungs_2014.NAME,
			ChiouYoungs_2014.COEFFS,
			ChiouYoungs_2014.CONSTRAINTS),

	/** @see Idriss_2014 */
	IDRISS_14(
			Idriss_2014.class,
			Idriss_2014.NAME,
			Idriss_2014.COEFFS,
			Idriss_2014.CONSTRAINTS),

	// Subduction NSHMP 2008 2014

	/** @see AtkinsonBoore_2003 */
	AB_03_GLOB_INTER(
			AtkinsonBoore_2003.GlobalInterface.class,
			AtkinsonBoore_2003.GlobalInterface.NAME,
			AtkinsonBoore_2003.COEFFS_GLOBAL_INTERFACE,
			AtkinsonBoore_2003.CONSTRAINTS),

	/** @see AtkinsonBoore_2003 */
	AB_03_GLOB_SLAB(
			AtkinsonBoore_2003.GlobalSlab.class,
			AtkinsonBoore_2003.GlobalSlab.NAME,
			AtkinsonBoore_2003.COEFFS_GLOBAL_SLAB,
			AtkinsonBoore_2003.CONSTRAINTS),

	/** @see AtkinsonBoore_2003 */
	AB_03_GLOB_SLAB_LOW_SAT(
			AtkinsonBoore_2003.GlobalSlabLowMagSaturation.class,
			AtkinsonBoore_2003.GlobalSlabLowMagSaturation.NAME,
			AtkinsonBoore_2003.COEFFS_GLOBAL_SLAB,
			AtkinsonBoore_2003.CONSTRAINTS),

	/** @see AtkinsonBoore_2003 */
	AB_03_CASC_INTER(
			AtkinsonBoore_2003.CascadiaInterface.class,
			AtkinsonBoore_2003.CascadiaInterface.NAME,
			AtkinsonBoore_2003.COEFFS_CASC_INTERFACE,
			AtkinsonBoore_2003.CONSTRAINTS),

	/** @see AtkinsonBoore_2003 */
	AB_03_CASC_SLAB(
			AtkinsonBoore_2003.CascadiaSlab.class,
			AtkinsonBoore_2003.CascadiaSlab.NAME,
			AtkinsonBoore_2003.COEFFS_CASC_SLAB,
			AtkinsonBoore_2003.CONSTRAINTS),

	/** @see AtkinsonBoore_2003 */
	AB_03_CASC_SLAB_LOW_SAT(
			AtkinsonBoore_2003.CascadiaSlabLowMagSaturation.class,
			AtkinsonBoore_2003.CascadiaSlabLowMagSaturation.NAME,
			AtkinsonBoore_2003.COEFFS_CASC_SLAB,
			AtkinsonBoore_2003.CONSTRAINTS),

	/** @see AtkinsonMacias_2009 */
	AM_09_INTER(
			AtkinsonMacias_2009.class,
			AtkinsonMacias_2009.NAME,
			AtkinsonMacias_2009.COEFFS,
			AtkinsonMacias_2009.CONSTRAINTS),

	/** @see BcHydro_2012 */
	BCHYDRO_12_INTER(
			BcHydro_2012.Interface.class,
			BcHydro_2012.Interface.NAME,
			BcHydro_2012.COEFFS,
			BcHydro_2012.CONSTRAINTS),

	/** @see BcHydro_2012 */
	BCHYDRO_12_SLAB(
			BcHydro_2012.Slab.class,
			BcHydro_2012.Slab.NAME,
			BcHydro_2012.COEFFS,
			BcHydro_2012.CONSTRAINTS),

	/** @see YoungsEtAl_1997 */
	YOUNGS_97_INTER(
			YoungsEtAl_1997.Interface.class,
			YoungsEtAl_1997.Interface.NAME,
			YoungsEtAl_1997.COEFFS,
			YoungsEtAl_1997.CONSTRAINTS),

	/** @see YoungsEtAl_1997 */
	YOUNGS_97_SLAB(
			YoungsEtAl_1997.Slab.class,
			YoungsEtAl_1997.Slab.NAME,
			YoungsEtAl_1997.COEFFS,
			YoungsEtAl_1997.CONSTRAINTS),

	/** @see ZhaoEtAl_2006 */
	ZHAO_06_INTER(
			ZhaoEtAl_2006.Interface.class,
			ZhaoEtAl_2006.Interface.NAME,
			ZhaoEtAl_2006.COEFFS,
			ZhaoEtAl_2006.CONSTRAINTS),

	/** @see ZhaoEtAl_2006 */
	ZHAO_06_SLAB(
			ZhaoEtAl_2006.Slab.class,
			ZhaoEtAl_2006.Slab.NAME,
			ZhaoEtAl_2006.COEFFS,
			ZhaoEtAl_2006.CONSTRAINTS),

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
	AB_06_PRIME(
			AtkinsonBoore_2006p.class,
			AtkinsonBoore_2006p.NAME,
			AtkinsonBoore_2006p.COEFFS,
			AtkinsonBoore_2006p.CONSTRAINTS),

	/** @see AtkinsonBoore_2006 */
	AB_06_140BAR(
			AtkinsonBoore_2006.StressDrop_140bar.class,
			AtkinsonBoore_2006.StressDrop_140bar.NAME,
			AtkinsonBoore_2006.COEFFS_A,
			AtkinsonBoore_2006.CONSTRAINTS),

	/** @see AtkinsonBoore_2006 */
	AB_06_200BAR(
			AtkinsonBoore_2006.StressDrop_200bar.class,
			AtkinsonBoore_2006.StressDrop_200bar.NAME,
			AtkinsonBoore_2006.COEFFS_A,
			AtkinsonBoore_2006.CONSTRAINTS),

	/** @see Atkinson_2008p */
	ATKINSON_08_PRIME(
			Atkinson_2008p.class,
			Atkinson_2008p.NAME,
			Atkinson_2008p.COEFFS,
			Atkinson_2008p.CONSTRAINTS),

	/** @see Campbell_2003 */
	CAMPBELL_03(
			Campbell_2003.class,
			Campbell_2003.NAME,
			Campbell_2003.COEFFS,
			Campbell_2003.CONSTRAINTS),

	/** @see FrankelEtAl_1996 */
	FRANKEL_96(
			FrankelEtAl_1996.class,
			FrankelEtAl_1996.NAME,
			FrankelEtAl_1996.COEFFS,
			FrankelEtAl_1996.CONSTRAINTS),

	/** @see PezeshkEtAl_2011 */
	PEZESHK_11(
			PezeshkEtAl_2011.class,
			PezeshkEtAl_2011.NAME,
			PezeshkEtAl_2011.COEFFS,
			PezeshkEtAl_2011.CONSTRAINTS),

	/** @see SilvaEtAl_2002 */
	SILVA_02(
			SilvaEtAl_2002.class,
			SilvaEtAl_2002.NAME,
			SilvaEtAl_2002.COEFFS,
			SilvaEtAl_2002.CONSTRAINTS),

	/** @see SomervilleEtAl_2001 */
	SOMERVILLE_01(
			SomervilleEtAl_2001.class,
			SomervilleEtAl_2001.NAME,
			SomervilleEtAl_2001.COEFFS,
			SomervilleEtAl_2001.CONSTRAINTS),

	/** @see TavakoliPezeshk_2005 */
	TP_05(
			TavakoliPezeshk_2005.class,
			TavakoliPezeshk_2005.NAME,
			TavakoliPezeshk_2005.COEFFS,
			TavakoliPezeshk_2005.CONSTRAINTS),

	/** @see ToroEtAl_1997 */
	TORO_97_MW(
			ToroEtAl_1997.Mw.class,
			ToroEtAl_1997.Mw.NAME,
			ToroEtAl_1997.COEFFS_MW,
			ToroEtAl_1997.CONSTRAINTS),

	// Johnston mag converting flavors of CEUS, NSHMP 2008

	/** @see AtkinsonBoore_2006 */
	AB_06_140BAR_J(
			AtkinsonBoore_2006_140bar_J.class,
			AtkinsonBoore_2006_140bar_J.NAME,
			AtkinsonBoore_2006.COEFFS_A,
			AtkinsonBoore_2006.CONSTRAINTS),

	/** @see AtkinsonBoore_2006 */
	AB_06_200BAR_J(
			AtkinsonBoore_2006_200bar_J.class,
			AtkinsonBoore_2006_200bar_J.NAME,
			AtkinsonBoore_2006.COEFFS_A,
			AtkinsonBoore_2006.CONSTRAINTS),

	/** @see Campbell_2003 */
	CAMPBELL_03_J(
			Campbell_2003_J.class,
			Campbell_2003_J.NAME,
			Campbell_2003.COEFFS,
			Campbell_2003.CONSTRAINTS),

	/** @see FrankelEtAl_1996 */
	FRANKEL_96_J(
			FrankelEtAl_1996_J.class,
			FrankelEtAl_1996_J.NAME,
			FrankelEtAl_1996.COEFFS,
			FrankelEtAl_1996.CONSTRAINTS),

	/** @see SilvaEtAl_2002 */
	SILVA_02_J(
			SilvaEtAl_2002_J.class,
			SilvaEtAl_2002_J.NAME,
			SilvaEtAl_2002.COEFFS,
			SilvaEtAl_2002.CONSTRAINTS),

	/** @see TavakoliPezeshk_2005 */
	TP_05_J(
			TavakoliPezeshk_2005_J.class,
			TavakoliPezeshk_2005_J.NAME,
			TavakoliPezeshk_2005.COEFFS,
			TavakoliPezeshk_2005.CONSTRAINTS),

	// Atkinson Boore mag converting flavors of CEUS, NSHMP 2008

	/** @see AtkinsonBoore_2006 */
	AB_06_140BAR_AB(
			AtkinsonBoore_2006_140bar_AB.class,
			AtkinsonBoore_2006_140bar_AB.NAME,
			AtkinsonBoore_2006.COEFFS_A,
			AtkinsonBoore_2006.CONSTRAINTS),

	/** @see AtkinsonBoore_2006 */
	AB_06_200BAR_AB(
			AtkinsonBoore_2006_200bar_AB.class,
			AtkinsonBoore_2006_200bar_AB.NAME,
			AtkinsonBoore_2006.COEFFS_A,
			AtkinsonBoore_2006.CONSTRAINTS),

	/** @see Campbell_2003 */
	CAMPBELL_03_AB(
			Campbell_2003_AB.class,
			Campbell_2003_AB.NAME,
			Campbell_2003.COEFFS,
			Campbell_2003.CONSTRAINTS),

	/** @see FrankelEtAl_1996 */
	FRANKEL_96_AB(
			FrankelEtAl_1996_AB.class,
			FrankelEtAl_1996_AB.NAME,
			FrankelEtAl_1996.COEFFS,
			FrankelEtAl_1996.CONSTRAINTS),

	/** @see SilvaEtAl_2002 */
	SILVA_02_AB(
			SilvaEtAl_2002_AB.class,
			SilvaEtAl_2002_AB.NAME,
			SilvaEtAl_2002.COEFFS,
			SilvaEtAl_2002.CONSTRAINTS),

	/** @see TavakoliPezeshk_2005 */
	TP_05_AB(
			TavakoliPezeshk_2005_AB.class,
			TavakoliPezeshk_2005_AB.NAME,
			TavakoliPezeshk_2005.COEFFS,
			TavakoliPezeshk_2005.CONSTRAINTS),

	/** @see ToroEtAl_1997 */
	TORO_97_MB(
			ToroEtAl_1997.Mb.class,
			ToroEtAl_1997.Mb.NAME,
			ToroEtAl_1997.COEFFS_MW,
			ToroEtAl_1997.CONSTRAINTS),

	// Other

	/** @see Atkinson_2015 */
	ATKINSON_15(
			Atkinson_2015.class,
			Atkinson_2015.NAME,
			Atkinson_2015.COEFFS,
			Atkinson_2015.CONSTRAINTS),
			
	/** @see SadighEtAl_1997 */
	SADIGH_97(
			SadighEtAl_1997.class,
			SadighEtAl_1997.NAME,
			SadighEtAl_1997.COEFFS_BC_HI,
			SadighEtAl_1997.CONSTRAINTS),

	/** @see McVerryEtAl_2000 */
	MCVERRY_00_CRUSTAL(
			McVerryEtAl_2000.Crustal.class,
			McVerryEtAl_2000.Crustal.NAME,
			McVerryEtAl_2000.COEFFS_GM,
			McVerryEtAl_2000.CONSTRAINTS),

	/** @see McVerryEtAl_2000 */
	MCVERRY_00_INTERFACE(
			McVerryEtAl_2000.Interface.class,
			McVerryEtAl_2000.Interface.NAME,
			McVerryEtAl_2000.COEFFS_GM,
			McVerryEtAl_2000.CONSTRAINTS),

	/** @see McVerryEtAl_2000 */
	MCVERRY_00_SLAB(
			McVerryEtAl_2000.Slab.class,
			McVerryEtAl_2000.Slab.NAME,
			McVerryEtAl_2000.COEFFS_GM,
			McVerryEtAl_2000.CONSTRAINTS),

	/** @see McVerryEtAl_2000 */
	MCVERRY_00_VOLCANIC(
			McVerryEtAl_2000.Volcanic.class,
			McVerryEtAl_2000.Volcanic.NAME,
			McVerryEtAl_2000.COEFFS_GM,
			McVerryEtAl_2000.CONSTRAINTS);

	private final Class<? extends GroundMotionModel> delegate;
	private final String name;
	private final Set<Imt> imts;
	private final Constraints constraints;
	private final LoadingCache<Imt, GroundMotionModel> cache;

	private Gmm(Class<? extends GroundMotionModel> delegate,
			String name,
			CoefficientContainer coeffs,
			Constraints constraints) {
		this.delegate = delegate;
		this.name = name;
		this.constraints = constraints;
		imts = coeffs.imts();
		cache = CacheBuilder.newBuilder().build(new CacheLoader<Imt, GroundMotionModel>() {
			@Override public GroundMotionModel load(Imt imt) throws Exception {
				return createInstance(imt);
			}
		});
	}

	private GroundMotionModel createInstance(Imt imt) throws Exception {
		checkArgument(this.imts.contains(checkNotNull(imt)),
			"Gmm: %s does not support Imt: %s", this.name(), imt.name());
		Constructor<? extends GroundMotionModel> con = delegate.getDeclaredConstructor(Imt.class);
		GroundMotionModel gmm = con.newInstance(imt);
		return gmm;
	}

	/**
	 * Retreive an instance of a {@code GroundMotionModel}, either by creating a
	 * new one, or fetching from a cache.
	 * 
	 * @param imt of the retreived instance
	 * @throws UncheckedExecutionException if there is an instantiation problem
	 */
	public GroundMotionModel instance(Imt imt) {
		// TODO we should probably use get() and implement better exception
		// handling
		return cache.getUnchecked(imt);
	}

	/**
	 * Retrieve an immutable map of {@code GroundMotionModel} instances, either
	 * by creating new ones, or fetching them from a cache.
	 * 
	 * @param imt of each retreived instance
	 * @param gmms to retrieve
	 * @throws UncheckedExecutionException if there is an instantiation problem
	 */
	public static Map<Gmm, GroundMotionModel> instances(Imt imt, Set<Gmm> gmms) {
		Map<Gmm, GroundMotionModel> instanceMap = Maps.newEnumMap(Gmm.class);
		for (Gmm gmm : gmms) {
			instanceMap.put(gmm, gmm.instance(imt));
		}
		return Maps.immutableEnumMap(instanceMap);
	}

	/**
	 * Retrieve immutable maps of {@code GroundMotionModel} instances for a
	 * range of {@code Imt}s, either by creating new ones, or fetching them from
	 * a cache.
	 * 
	 * @param imts to retrieve instances for
	 * @param gmms to retrieve
	 * @throws UncheckedExecutionException if there is an instantiation problem
	 */
	public static Map<Imt, Map<Gmm, GroundMotionModel>> instances(Set<Imt> imts, Set<Gmm> gmms) {
		Map<Imt, Map<Gmm, GroundMotionModel>> imtMap = Maps.newEnumMap(Imt.class);
		for (Imt imt : imts) {
			imtMap.put(imt, instances(imt, gmms));
		}
		return Maps.immutableEnumMap(imtMap);
	}

	@Override public String toString() {
		return name;
	}

	/**
	 * Return the {@code Set} of the intensity measure types ({@code Imt}s)
	 * supported by this {@code Gmm}.
	 */
	public Set<Imt> supportedIMTs() {
		return imts;
	}

	/**
	 * Return the {@code Set} of the intensity measure types ({@code Imt}s)
	 * supported by all of the supplied {@code Gmm}s.
	 * 
	 * @param gmms models for which to return common {@code Imt} support
	 */
	public static Set<Imt> supportedIMTs(Collection<Gmm> gmms) {
		Set<Imt> imts = EnumSet.allOf(Imt.class);
		for (Gmm gmm : gmms) {
			imts = Sets.intersection(imts, gmm.supportedIMTs());
		}
		return EnumSet.copyOf(imts);
	}

	/**
	 * Return the set of spectral acceleration {@code Imt}s that are supported
	 * by this {@code Gmm}.
	 */
	public Set<Imt> responseSpectrumIMTs() {
		return Sets.intersection(imts, Imt.saImts());
	}

	/**
	 * Return the set of spectral acceleration (SA) {@code Imt}s that are common
	 * to the supplied {@code Gmm}s.
	 * 
	 * @param gmms models for which to return common SA {@code Imt} support
	 */
	public static Set<Imt> responseSpectrumIMTs(Collection<Gmm> gmms) {
		return Sets.intersection(supportedIMTs(gmms), Imt.saImts());
	}

	/**
	 * Return the input {@code Constraints} for this {@code Gmm}.
	 */
	public Constraints constraints() {
		return constraints;
	}

	@SuppressWarnings("javadoc")
	public enum Group {

		WUS_14_ACTIVE_CRUST(
				"2014 Active Crust (WUS)",
				ImmutableList.of(
					ASK_14,
					BSSA_14,
					CB_14,
					CY_14)),

		CEUS_14_STABLE_CRUST(
				"2014 Stable Crust (CEUS)",
				ImmutableList.of(
					ATKINSON_08_PRIME,
					AB_06_PRIME,
					CAMPBELL_03,
					FRANKEL_96,
					PEZESHK_11,
					SILVA_02,
					SOMERVILLE_01,
					TP_05,
					TORO_97_MW)),

		WUS_14_INTERFACE(
				"2014 Subduction Interface (WUS)",
				ImmutableList.of(
					AB_03_GLOB_INTER,
					AM_09_INTER,
					BCHYDRO_12_INTER,
					ZHAO_06_INTER)),

		WUS_14_SLAB(
				"2014 WUS Subduction Intraslab (WUS)",
				ImmutableList.of(
					AB_03_CASC_SLAB_LOW_SAT,
					AB_03_GLOB_SLAB_LOW_SAT,
					BCHYDRO_12_SLAB,
					ZHAO_06_SLAB)),

		WUS_08_ACTIVE_CRUST(
				"2008 Active Crust (WUS)",
				ImmutableList.of(
					BA_08,
					CB_08,
					CY_08)),

		CEUS_08_STABLE_CRUST(
				"2008 Stable Crust (CEUS)",
				ImmutableList.of(
					AB_06_140BAR,
					AB_06_200BAR,
					CAMPBELL_03,
					FRANKEL_96,
					SILVA_02,
					SOMERVILLE_01,
					TP_05,
					TORO_97_MW)),

		WUS_08_INTERFACE(
				"2008 Subduction Interface (WUS)",
				ImmutableList.of(
					AB_03_GLOB_INTER,
					YOUNGS_97_INTER,
					ZHAO_06_INTER)),

		WUS_08_SLAB(
				"2008 Subduction Intraslab (WUS)",
				ImmutableList.of(
					AB_03_CASC_SLAB,
					AB_03_GLOB_SLAB,
					YOUNGS_97_SLAB)),

		OTHER(
				"Others",
				ImmutableList.of(
					ATKINSON_15,
					AB_03_CASC_INTER,
					MCVERRY_00_CRUSTAL,
					MCVERRY_00_INTERFACE,
					MCVERRY_00_SLAB,
					MCVERRY_00_VOLCANIC,
					SADIGH_97));

		private final String name;
		private final List<Gmm> gmms;

		private Group(String name, List<Gmm> gmms) {
			this.name = name;
			this.gmms = gmms;
		}

		@Override public String toString() {
			return name;
		}

		/**
		 * Return an immutable list of the Gmms in this group, sorted
		 * alphabetically by their display name.
		 */
		public List<Gmm> gmms() {
			return gmms;
		}
	}

}
