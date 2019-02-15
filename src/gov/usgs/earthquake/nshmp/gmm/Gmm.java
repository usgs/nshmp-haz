package gov.usgs.earthquake.nshmp.gmm;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.lang.reflect.Constructor;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.UncheckedExecutionException;

import gov.usgs.earthquake.nshmp.gmm.CeusMb.AtkinsonBoore_2006_140bar_AB;
import gov.usgs.earthquake.nshmp.gmm.CeusMb.AtkinsonBoore_2006_140bar_J;
import gov.usgs.earthquake.nshmp.gmm.CeusMb.AtkinsonBoore_2006_200bar_AB;
import gov.usgs.earthquake.nshmp.gmm.CeusMb.AtkinsonBoore_2006_200bar_J;
import gov.usgs.earthquake.nshmp.gmm.CeusMb.Campbell_2003_AB;
import gov.usgs.earthquake.nshmp.gmm.CeusMb.Campbell_2003_J;
import gov.usgs.earthquake.nshmp.gmm.CeusMb.FrankelEtAl_1996_AB;
import gov.usgs.earthquake.nshmp.gmm.CeusMb.FrankelEtAl_1996_J;
import gov.usgs.earthquake.nshmp.gmm.CeusMb.SilvaEtAl_2002_AB;
import gov.usgs.earthquake.nshmp.gmm.CeusMb.SilvaEtAl_2002_J;
import gov.usgs.earthquake.nshmp.gmm.CeusMb.TavakoliPezeshk_2005_AB;
import gov.usgs.earthquake.nshmp.gmm.CeusMb.TavakoliPezeshk_2005_J;
import gov.usgs.earthquake.nshmp.gmm.GmmInput.Constraints;

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

  /*
   * Developer notes:
   * 
   * Concrete GroundMotionModel implementations that are identified in enum
   * constructors below must implement a constructor taking a single IMT
   * argument.
   */

  // TODO implement AB03 taper developed by SH; gms at 2s and 3s are much too
  // high at large distances -- NOT NEEDED as we are dropping AB03 for multi-
  // point analyses (2014 r2)

  // TODO AB06 has PGV clamp of 460m/s; is this correct? or specified
  // anywhere?

  // TODO Verify that Campbell03 imposes max(dtor,5); he does require rRup;
  // why is depth constrained as such in hazgrid? As with somerville, no depth
  // is
  // imposed in hazFX - make sure 0.01 as PGA is handled corectly; may require
  // change to period = 0.0

  /* Active continent NGA-West1 WUS 2008 */

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

  /* Active continent NGA-West2 WUS 2014 */

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

  /*
   * Active continent NGA-West2 WUS 2018. These have basin amplification-only
   * effects implemented.
   */

  /** @see AbrahamsonEtAl_2014 */
  ASK_14_BASIN_AMP(
      AbrahamsonEtAl_2014.BasinAmp.class,
      AbrahamsonEtAl_2014.BasinAmp.NAME,
      AbrahamsonEtAl_2014.BasinAmp.COEFFS,
      AbrahamsonEtAl_2014.BasinAmp.CONSTRAINTS),

  /** @see BooreEtAl_2014 */
  BSSA_14_BASIN_AMP(
      BooreEtAl_2014.BasinAmp.class,
      BooreEtAl_2014.BasinAmp.NAME,
      BooreEtAl_2014.BasinAmp.COEFFS,
      BooreEtAl_2014.BasinAmp.CONSTRAINTS),

  /** @see CampbellBozorgnia_2014 */
  CB_14_BASIN_AMP(
      CampbellBozorgnia_2014.BasinAmp.class,
      CampbellBozorgnia_2014.BasinAmp.NAME,
      CampbellBozorgnia_2014.BasinAmp.COEFFS,
      CampbellBozorgnia_2014.BasinAmp.CONSTRAINTS),

  /** @see ChiouYoungs_2014 */
  CY_14_BASIN_AMP(
      ChiouYoungs_2014.BasinAmp.class,
      ChiouYoungs_2014.BasinAmp.NAME,
      ChiouYoungs_2014.BasinAmp.COEFFS,
      ChiouYoungs_2014.BasinAmp.CONSTRAINTS),

  /* Active Continent AK 2007, HI 1998 */

  /** @see AbrahamsonSilva_1997 */
  AS_97(
      AbrahamsonSilva_1997.class,
      AbrahamsonSilva_1997.NAME,
      AbrahamsonSilva_1997.COEFFS,
      AbrahamsonSilva_1997.CONSTRAINTS),

  /** @see BooreEtAl_1997 */
  BJF_97(
      BooreEtAl_1997.class,
      BooreEtAl_1997.NAME,
      BooreEtAl_1997.COEFFS,
      BooreEtAl_1997.CONSTRAINTS),

  /** @see Campbell_1997 */
  CAMPBELL_97(
      Campbell_1997.class,
      Campbell_1997.NAME,
      Campbell_1997.COEFFS,
      Campbell_1997.CONSTRAINTS),

  /** @see CampbellBozorgnia_2003 */
  CB_03(
      CampbellBozorgnia_2003.class,
      CampbellBozorgnia_2003.NAME,
      CampbellBozorgnia_2003.COEFFS,
      CampbellBozorgnia_2003.CONSTRAINTS),

  /** @see MunsonThurber_1997 */
  MT_97(
      MunsonThurber_1997.class,
      MunsonThurber_1997.NAME,
      MunsonThurber_1997.COEFFS,
      MunsonThurber_1997.CONSTRAINTS),

  /** @see SadighEtAl_1997 */
  SADIGH_97(
      SadighEtAl_1997.class,
      SadighEtAl_1997.NAME,
      SadighEtAl_1997.COEFFS_BC_HI,
      SadighEtAl_1997.CONSTRAINTS),

  /* Subduction Interface and Slab WUS 2008 2014 2018, AK 2007 */

  /** @see AtkinsonBoore_2003 */
  AB_03_GLOBAL_INTERFACE(
      AtkinsonBoore_2003.GlobalInterface.class,
      AtkinsonBoore_2003.GlobalInterface.NAME,
      AtkinsonBoore_2003.COEFFS_GLOBAL_INTERFACE,
      AtkinsonBoore_2003.CONSTRAINTS),

  /** @see AtkinsonBoore_2003 */
  AB_03_GLOBAL_SLAB(
      AtkinsonBoore_2003.GlobalSlab.class,
      AtkinsonBoore_2003.GlobalSlab.NAME,
      AtkinsonBoore_2003.COEFFS_GLOBAL_SLAB,
      AtkinsonBoore_2003.CONSTRAINTS),

  /** @see AtkinsonBoore_2003 */
  AB_03_GLOBAL_SLAB_LOW_SAT(
      AtkinsonBoore_2003.GlobalSlabLowMagSaturation.class,
      AtkinsonBoore_2003.GlobalSlabLowMagSaturation.NAME,
      AtkinsonBoore_2003.COEFFS_GLOBAL_SLAB,
      AtkinsonBoore_2003.CONSTRAINTS),

  /** @see AtkinsonBoore_2003 */
  AB_03_CASCADIA_INTERFACE(
      AtkinsonBoore_2003.CascadiaInterface.class,
      AtkinsonBoore_2003.CascadiaInterface.NAME,
      AtkinsonBoore_2003.COEFFS_CASCADIA_INTERFACE,
      AtkinsonBoore_2003.CONSTRAINTS),

  /** @see AtkinsonBoore_2003 */
  AB_03_CASCADIA_SLAB(
      AtkinsonBoore_2003.CascadiaSlab.class,
      AtkinsonBoore_2003.CascadiaSlab.NAME,
      AtkinsonBoore_2003.COEFFS_CASCADIA_SLAB,
      AtkinsonBoore_2003.CONSTRAINTS),

  /** @see AtkinsonBoore_2003 */
  AB_03_CASCADIA_SLAB_LOW_SAT(
      AtkinsonBoore_2003.CascadiaSlabLowMagSaturation.class,
      AtkinsonBoore_2003.CascadiaSlabLowMagSaturation.NAME,
      AtkinsonBoore_2003.COEFFS_CASCADIA_SLAB,
      AtkinsonBoore_2003.CONSTRAINTS),

  /** @see AtkinsonMacias_2009 */
  AM_09_INTERFACE(
      AtkinsonMacias_2009.class,
      AtkinsonMacias_2009.NAME,
      AtkinsonMacias_2009.COEFFS,
      AtkinsonMacias_2009.CONSTRAINTS),

  /** @see AtkinsonMacias_2009 */
  AM_09_INTERFACE_BASIN_AMP(
      AtkinsonMacias_2009.Basin.class,
      AtkinsonMacias_2009.Basin.NAME,
      AtkinsonMacias_2009.COEFFS,
      AtkinsonMacias_2009.CONSTRAINTS),

  /** @see AbrahamsonEtAl_2016 */
  AGA_16_INTERFACE(
      NgaSubductionUsgs_2018.Interface.class,
      NgaSubductionUsgs_2018.Interface.NAME,
      NgaSubductionUsgs_2018.COEFFS,
      NgaSubductionUsgs_2018.CONSTRAINTS),

  /** @see AbrahamsonEtAl_2016 */
  AGA_16_SLAB(
      NgaSubductionUsgs_2018.Slab.class,
      NgaSubductionUsgs_2018.Slab.NAME,
      NgaSubductionUsgs_2018.COEFFS,
      NgaSubductionUsgs_2018.CONSTRAINTS),

  /** @see AbrahamsonEtAl_2016 */
  AGA_16_INTERFACE_BASIN_AMP(
      NgaSubductionUsgs_2018.InterfaceCenter.class,
      NgaSubductionUsgs_2018.InterfaceCenter.NAME,
      NgaSubductionUsgs_2018.COEFFS,
      NgaSubductionUsgs_2018.CONSTRAINTS),

  /** @see AbrahamsonEtAl_2016 */
  AGA_16_SLAB_BASIN_AMP(
      NgaSubductionUsgs_2018.SlabCenter.class,
      NgaSubductionUsgs_2018.SlabCenter.NAME,
      NgaSubductionUsgs_2018.COEFFS,
      NgaSubductionUsgs_2018.CONSTRAINTS),

  /** @see BcHydro_2012 */
  BCHYDRO_12_INTERFACE(
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

  /** @see BcHydro_2012 */
  BCHYDRO_12_INTERFACE_BASIN_AMP(
      BcHydro_2012.BasinInterface.class,
      BcHydro_2012.BasinInterface.NAME,
      BcHydro_2012.COEFFS,
      BcHydro_2012.CONSTRAINTS),

  /** @see BcHydro_2012 */
  BCHYDRO_12_SLAB_BASIN_AMP(
      BcHydro_2012.BasinSlab.class,
      BcHydro_2012.BasinSlab.NAME,
      BcHydro_2012.COEFFS,
      BcHydro_2012.CONSTRAINTS),

  /** @see YoungsEtAl_1997 */
  YOUNGS_97_INTERFACE(
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
  ZHAO_06_INTERFACE(
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

  /** @see ZhaoEtAl_2006 */
  ZHAO_06_INTERFACE_BASIN_AMP(
      ZhaoEtAl_2006.BasinInterface.class,
      ZhaoEtAl_2006.BasinInterface.NAME,
      ZhaoEtAl_2006.COEFFS,
      ZhaoEtAl_2006.CONSTRAINTS),

  /** @see ZhaoEtAl_2006 */
  ZHAO_06_SLAB_BASIN_AMP(
      ZhaoEtAl_2006.BasinSlab.class,
      ZhaoEtAl_2006.BasinSlab.NAME,
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

  /* Stable continent CEUS 2008 2014 */

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

  /* Johnston mag converting flavors of CEUS 2008 */

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

  /* Atkinson Boore mag converting flavors of CEUS 2008 */

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

  /* Other */

  /** @see Atkinson_2010 */
  ATKINSON_10(
      Atkinson_2010.class,
      Atkinson_2010.NAME,
      Atkinson_2010.COEFFS,
      Atkinson_2010.CONSTRAINTS),

  /** @see Atkinson_2015 */
  ATKINSON_15(
      Atkinson_2015.class,
      Atkinson_2015.NAME,
      Atkinson_2015.COEFFS,
      Atkinson_2015.CONSTRAINTS),

  /** @see GraizerKalkan_2015 */
  GK_15(
      GraizerKalkan_2015.class,
      GraizerKalkan_2015.NAME,
      GraizerKalkan_2015.COEFFS,
      GraizerKalkan_2015.CONSTRAINTS),

  /** @see WongEtAl_2015 */
  WONG_15(
      WongEtAl_2015.class,
      WongEtAl_2015.NAME,
      WongEtAl_2015.COEFFS,
      WongEtAl_2015.CONSTRAINTS),

  /** @see ZhaoEtAl_2016 */
  ZHAO_16_SHALLOW_CRUST(
      ZhaoEtAl_2016.ShallowCrust.class,
      ZhaoEtAl_2016.ShallowCrust.NAME,
      ZhaoEtAl_2016.SITE_AMP,
      ZhaoEtAl_2016.CONSTRAINTS),

  /** @see ZhaoEtAl_2016 */
  ZHAO_16_UPPER_MANTLE(
      ZhaoEtAl_2016.UpperMantle.class,
      ZhaoEtAl_2016.UpperMantle.NAME,
      ZhaoEtAl_2016.SITE_AMP,
      ZhaoEtAl_2016.CONSTRAINTS),

  /** @see ZhaoEtAl_2016 */
  ZHAO_16_INTERFACE(
      ZhaoEtAl_2016.Interface.class,
      ZhaoEtAl_2016.Interface.NAME,
      ZhaoEtAl_2016.SITE_AMP,
      ZhaoEtAl_2016.CONSTRAINTS),

  /** @see ZhaoEtAl_2016 */
  ZHAO_16_SLAB(
      ZhaoEtAl_2016.Slab.class,
      ZhaoEtAl_2016.Slab.NAME,
      ZhaoEtAl_2016.SITE_AMP,
      ZhaoEtAl_2016.CONSTRAINTS),

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
      McVerryEtAl_2000.CONSTRAINTS),

  /* NGA-East for USGS */

  /** @see NgaEastUsgs_2017 */
  NGA_EAST_USGS_EPRI(
      NgaEastUsgs_2017.Usgs17_Sigma_Epri.class,
      NgaEastUsgs_2017.Usgs17_Sigma_Epri.NAME,
      NgaEastUsgs_2017.COEFFS_SIGMA_MID,
      NgaEastUsgs_2017.CONSTRAINTS),

  /** @see NgaEastUsgs_2017 */
  NGA_EAST_USGS_PANEL(
      NgaEastUsgs_2017.Usgs17_Sigma_Panel.class,
      NgaEastUsgs_2017.Usgs17_Sigma_Panel.NAME,
      NgaEastUsgs_2017.COEFFS_SIGMA_MID,
      NgaEastUsgs_2017.CONSTRAINTS),

  /** @see NgaEastUsgs_2017 */
  NGA_EAST_USGS(
      NgaEastUsgs_2017.Usgs17.class,
      NgaEastUsgs_2017.Usgs17.NAME,
      NgaEastUsgs_2017.COEFFS_SIGMA_MID,
      NgaEastUsgs_2017.CONSTRAINTS),

  /** @see NgaEastUsgs_2017 */
  NGA_EAST_USGS_1(
      NgaEastUsgs_2017.Sammons_1.class,
      NgaEastUsgs_2017.Sammons_1.NAME,
      NgaEastUsgs_2017.COEFFS_SIGMA_MID,
      NgaEastUsgs_2017.CONSTRAINTS),

  /** @see NgaEastUsgs_2017 */
  NGA_EAST_USGS_2(
      NgaEastUsgs_2017.Sammons_2.class,
      NgaEastUsgs_2017.Sammons_2.NAME,
      NgaEastUsgs_2017.COEFFS_SIGMA_MID,
      NgaEastUsgs_2017.CONSTRAINTS),

  /** @see NgaEastUsgs_2017 */
  NGA_EAST_USGS_3(
      NgaEastUsgs_2017.Sammons_3.class,
      NgaEastUsgs_2017.Sammons_3.NAME,
      NgaEastUsgs_2017.COEFFS_SIGMA_MID,
      NgaEastUsgs_2017.CONSTRAINTS),

  /** @see NgaEastUsgs_2017 */
  NGA_EAST_USGS_4(
      NgaEastUsgs_2017.Sammons_4.class,
      NgaEastUsgs_2017.Sammons_4.NAME,
      NgaEastUsgs_2017.COEFFS_SIGMA_MID,
      NgaEastUsgs_2017.CONSTRAINTS),

  /** @see NgaEastUsgs_2017 */
  NGA_EAST_USGS_5(
      NgaEastUsgs_2017.Sammons_5.class,
      NgaEastUsgs_2017.Sammons_5.NAME,
      NgaEastUsgs_2017.COEFFS_SIGMA_MID,
      NgaEastUsgs_2017.CONSTRAINTS),

  /** @see NgaEastUsgs_2017 */
  NGA_EAST_USGS_6(
      NgaEastUsgs_2017.Sammons_6.class,
      NgaEastUsgs_2017.Sammons_6.NAME,
      NgaEastUsgs_2017.COEFFS_SIGMA_MID,
      NgaEastUsgs_2017.CONSTRAINTS),

  /** @see NgaEastUsgs_2017 */
  NGA_EAST_USGS_7(
      NgaEastUsgs_2017.Sammons_7.class,
      NgaEastUsgs_2017.Sammons_7.NAME,
      NgaEastUsgs_2017.COEFFS_SIGMA_MID,
      NgaEastUsgs_2017.CONSTRAINTS),

  /** @see NgaEastUsgs_2017 */
  NGA_EAST_USGS_8(
      NgaEastUsgs_2017.Sammons_8.class,
      NgaEastUsgs_2017.Sammons_8.NAME,
      NgaEastUsgs_2017.COEFFS_SIGMA_MID,
      NgaEastUsgs_2017.CONSTRAINTS),

  /** @see NgaEastUsgs_2017 */
  NGA_EAST_USGS_9(
      NgaEastUsgs_2017.Sammons_9.class,
      NgaEastUsgs_2017.Sammons_9.NAME,
      NgaEastUsgs_2017.COEFFS_SIGMA_MID,
      NgaEastUsgs_2017.CONSTRAINTS),

  /** @see NgaEastUsgs_2017 */
  NGA_EAST_USGS_10(
      NgaEastUsgs_2017.Sammons_10.class,
      NgaEastUsgs_2017.Sammons_10.NAME,
      NgaEastUsgs_2017.COEFFS_SIGMA_MID,
      NgaEastUsgs_2017.CONSTRAINTS),

  /** @see NgaEastUsgs_2017 */
  NGA_EAST_USGS_11(
      NgaEastUsgs_2017.Sammons_11.class,
      NgaEastUsgs_2017.Sammons_11.NAME,
      NgaEastUsgs_2017.COEFFS_SIGMA_MID,
      NgaEastUsgs_2017.CONSTRAINTS),

  /** @see NgaEastUsgs_2017 */
  NGA_EAST_USGS_12(
      NgaEastUsgs_2017.Sammons_12.class,
      NgaEastUsgs_2017.Sammons_12.NAME,
      NgaEastUsgs_2017.COEFFS_SIGMA_MID,
      NgaEastUsgs_2017.CONSTRAINTS),

  /** @see NgaEastUsgs_2017 */
  NGA_EAST_USGS_13(
      NgaEastUsgs_2017.Sammons_13.class,
      NgaEastUsgs_2017.Sammons_13.NAME,
      NgaEastUsgs_2017.COEFFS_SIGMA_MID,
      NgaEastUsgs_2017.CONSTRAINTS),

  /** @see NgaEastUsgs_2017 */
  NGA_EAST_USGS_14(
      NgaEastUsgs_2017.Sammons_14.class,
      NgaEastUsgs_2017.Sammons_14.NAME,
      NgaEastUsgs_2017.COEFFS_SIGMA_MID,
      NgaEastUsgs_2017.CONSTRAINTS),

  /** @see NgaEastUsgs_2017 */
  NGA_EAST_USGS_15(
      NgaEastUsgs_2017.Sammons_15.class,
      NgaEastUsgs_2017.Sammons_15.NAME,
      NgaEastUsgs_2017.COEFFS_SIGMA_MID,
      NgaEastUsgs_2017.CONSTRAINTS),

  /** @see NgaEastUsgs_2017 */
  NGA_EAST_USGS_16(
      NgaEastUsgs_2017.Sammons_16.class,
      NgaEastUsgs_2017.Sammons_16.NAME,
      NgaEastUsgs_2017.COEFFS_SIGMA_MID,
      NgaEastUsgs_2017.CONSTRAINTS),

  /** @see NgaEastUsgs_2017 */
  NGA_EAST_USGS_17(
      NgaEastUsgs_2017.Sammons_17.class,
      NgaEastUsgs_2017.Sammons_17.NAME,
      NgaEastUsgs_2017.COEFFS_SIGMA_MID,
      NgaEastUsgs_2017.CONSTRAINTS),

  /* NGA-East USGS Seed Tree */

  /** @see NgaEastUsgs_2017 */
  NGA_EAST_USGS_SEEDS(
      NgaEastUsgs_2017.UsgsSeeds.class,
      NgaEastUsgs_2017.UsgsSeeds.NAME,
      NgaEastUsgs_2017.COEFFS_SIGMA_MID,
      NgaEastUsgs_2017.CONSTRAINTS),

  /** @see NgaEastUsgs_2017 */
  NGA_EAST_USGS_SEEDS_EPRI(
      NgaEastUsgs_2017.UsgsSeedsEpri.class,
      NgaEastUsgs_2017.UsgsSeedsEpri.NAME,
      NgaEastUsgs_2017.COEFFS_SIGMA_MID,
      NgaEastUsgs_2017.CONSTRAINTS),

  /* NGA-East Seed Models */

  /** @see NgaEastUsgs_2017 */
  NGA_EAST_SEED_1CCSP(
      NgaEastUsgs_2017.Seed_1CCSP.class,
      NgaEastUsgs_2017.Seed_1CCSP.NAME,
      NgaEastUsgs_2017.COEFFS_SIGMA_MID,
      NgaEastUsgs_2017.CONSTRAINTS),

  /** @see NgaEastUsgs_2017 */
  NGA_EAST_SEED_1CVSP(
      NgaEastUsgs_2017.Seed_1CVSP.class,
      NgaEastUsgs_2017.Seed_1CVSP.NAME,
      NgaEastUsgs_2017.COEFFS_SIGMA_MID,
      NgaEastUsgs_2017.CONSTRAINTS),

  /** @see NgaEastUsgs_2017 */
  NGA_EAST_SEED_2CCSP(
      NgaEastUsgs_2017.Seed_2CCSP.class,
      NgaEastUsgs_2017.Seed_2CCSP.NAME,
      NgaEastUsgs_2017.COEFFS_SIGMA_MID,
      NgaEastUsgs_2017.CONSTRAINTS),

  /** @see NgaEastUsgs_2017 */
  NGA_EAST_SEED_2CVSP(
      NgaEastUsgs_2017.Seed_2CVSP.class,
      NgaEastUsgs_2017.Seed_2CVSP.NAME,
      NgaEastUsgs_2017.COEFFS_SIGMA_MID,
      NgaEastUsgs_2017.CONSTRAINTS),

  /** @see NgaEastUsgs_2017 */
  NGA_EAST_SEED_B_A04(
      NgaEastUsgs_2017.Seed_B_a04.class,
      NgaEastUsgs_2017.Seed_B_a04.NAME,
      NgaEastUsgs_2017.COEFFS_SIGMA_MID,
      NgaEastUsgs_2017.CONSTRAINTS),

  /** @see NgaEastUsgs_2017 */
  NGA_EAST_SEED_B_AB14(
      NgaEastUsgs_2017.Seed_B_ab14.class,
      NgaEastUsgs_2017.Seed_B_ab14.NAME,
      NgaEastUsgs_2017.COEFFS_SIGMA_MID,
      NgaEastUsgs_2017.CONSTRAINTS),

  /** @see NgaEastUsgs_2017 */
  NGA_EAST_SEED_B_AB95(
      NgaEastUsgs_2017.Seed_B_ab95.class,
      NgaEastUsgs_2017.Seed_B_ab95.NAME,
      NgaEastUsgs_2017.COEFFS_SIGMA_MID,
      NgaEastUsgs_2017.CONSTRAINTS),

  /** @see NgaEastUsgs_2017 */
  NGA_EAST_SEED_B_BCA10D(
      NgaEastUsgs_2017.Seed_B_bca10d.class,
      NgaEastUsgs_2017.Seed_B_bca10d.NAME,
      NgaEastUsgs_2017.COEFFS_SIGMA_MID,
      NgaEastUsgs_2017.CONSTRAINTS),

  /** @see NgaEastUsgs_2017 */
  NGA_EAST_SEED_B_BS11(
      NgaEastUsgs_2017.Seed_B_bs11.class,
      NgaEastUsgs_2017.Seed_B_bs11.NAME,
      NgaEastUsgs_2017.COEFFS_SIGMA_MID,
      NgaEastUsgs_2017.CONSTRAINTS),

  /** @see NgaEastUsgs_2017 */
  NGA_EAST_SEED_B_SGD02(
      NgaEastUsgs_2017.Seed_B_sgd02.class,
      NgaEastUsgs_2017.Seed_B_sgd02.NAME,
      NgaEastUsgs_2017.COEFFS_SIGMA_MID,
      NgaEastUsgs_2017.CONSTRAINTS),

  /** @see NgaEastUsgs_2017 */
  NGA_EAST_SEED_FRANKEL(
      NgaEastUsgs_2017.Seed_Frankel.class,
      NgaEastUsgs_2017.Seed_Frankel.NAME,
      NgaEastUsgs_2017.COEFFS_SIGMA_MID,
      NgaEastUsgs_2017.CONSTRAINTS),

  /** @see NgaEastUsgs_2017 */
  NGA_EAST_SEED_GRAIZER(
      NgaEastUsgs_2017.Seed_Graizer.class,
      NgaEastUsgs_2017.Seed_Graizer.NAME,
      NgaEastUsgs_2017.COEFFS_SIGMA_MID,
      NgaEastUsgs_2017.CONSTRAINTS),

  /** @see NgaEastUsgs_2017 */
  NGA_EAST_SEED_GRAIZER16(
      NgaEastUsgs_2017.SeedUpdate_Graizer16.class,
      NgaEastUsgs_2017.SeedUpdate_Graizer16.NAME,
      NgaEastUsgs_2017.COEFFS_SIGMA_MID,
      NgaEastUsgs_2017.CONSTRAINTS),

  /** @see NgaEastUsgs_2017 */
  NGA_EAST_SEED_GRAIZER17(
      NgaEastUsgs_2017.SeedUpdate_Graizer17.class,
      NgaEastUsgs_2017.SeedUpdate_Graizer17.NAME,
      NgaEastUsgs_2017.COEFFS_SIGMA_MID,
      NgaEastUsgs_2017.CONSTRAINTS),

  /** @see NgaEastUsgs_2017 */
  NGA_EAST_SEED_HA15(
      NgaEastUsgs_2017.Seed_HA15.class,
      NgaEastUsgs_2017.Seed_HA15.NAME,
      NgaEastUsgs_2017.COEFFS_SIGMA_MID,
      NgaEastUsgs_2017.CONSTRAINTS),

  /** @see NgaEastUsgs_2017 */
  NGA_EAST_SEED_PEER_EX(
      NgaEastUsgs_2017.Seed_PEER_EX.class,
      NgaEastUsgs_2017.Seed_PEER_EX.NAME,
      NgaEastUsgs_2017.COEFFS_SIGMA_MID,
      NgaEastUsgs_2017.CONSTRAINTS),

  /** @see NgaEastUsgs_2017 */
  NGA_EAST_SEED_PEER_GP(
      NgaEastUsgs_2017.Seed_PEER_GP.class,
      NgaEastUsgs_2017.Seed_PEER_GP.NAME,
      NgaEastUsgs_2017.COEFFS_SIGMA_MID,
      NgaEastUsgs_2017.CONSTRAINTS),

  /** @see NgaEastUsgs_2017 */
  NGA_EAST_SEED_PZCT15_M1SS(
      NgaEastUsgs_2017.Seed_PZCT15_M1SS.class,
      NgaEastUsgs_2017.Seed_PZCT15_M1SS.NAME,
      NgaEastUsgs_2017.COEFFS_SIGMA_MID,
      NgaEastUsgs_2017.CONSTRAINTS),

  /** @see NgaEastUsgs_2017 */
  NGA_EAST_SEED_PZCT15_M2ES(
      NgaEastUsgs_2017.Seed_PZCT15_M2ES.class,
      NgaEastUsgs_2017.Seed_PZCT15_M2ES.NAME,
      NgaEastUsgs_2017.COEFFS_SIGMA_MID,
      NgaEastUsgs_2017.CONSTRAINTS),

  /** @see NgaEastUsgs_2017 */
  NGA_EAST_SEED_SP15(
      NgaEastUsgs_2017.Seed_SP15.class,
      NgaEastUsgs_2017.Seed_SP15.NAME,
      NgaEastUsgs_2017.COEFFS_SIGMA_MID,
      NgaEastUsgs_2017.CONSTRAINTS),

  /** @see NgaEastUsgs_2017 @see ShahjoueiPezeshk_2016 */
  NGA_EAST_SEED_SP16(
      ShahjoueiPezeshk_2016.class,
      ShahjoueiPezeshk_2016.NAME,
      ShahjoueiPezeshk_2016.COEFFS,
      ShahjoueiPezeshk_2016.CONSTRAINTS),

  /** @see NgaEastUsgs_2017 */
  NGA_EAST_SEED_YA15(
      NgaEastUsgs_2017.Seed_YA15.class,
      NgaEastUsgs_2017.Seed_YA15.NAME,
      NgaEastUsgs_2017.COEFFS_SIGMA_MID,
      NgaEastUsgs_2017.CONSTRAINTS),

  /* CEUS 2014 with NGA-East sigma model. */

  /** @see NgaEastUsgs_2017 */
  @Deprecated
  CEUS14_NGAE_SIGMA_AB_06_PRIME(
      NgaEastHybrid.SigmaSwap.AB06p.class,
      NgaEastHybrid.SigmaSwap.AB06p.NAME,
      AtkinsonBoore_2006p.COEFFS,
      NgaEastUsgs_2017.CONSTRAINTS),

  /** @see NgaEastUsgs_2017 */
  @Deprecated
  CEUS14_NGAE_SIGMA_ATKINSON_08_PRIME(
      NgaEastHybrid.SigmaSwap.A08p.class,
      NgaEastHybrid.SigmaSwap.A08p.NAME,
      Atkinson_2008p.COEFFS,
      NgaEastUsgs_2017.CONSTRAINTS),

  /** @see NgaEastUsgs_2017 */
  @Deprecated
  CEUS14_NGAE_SIGMA_CAMPBELL_03(
      NgaEastHybrid.SigmaSwap.Camp03.class,
      NgaEastHybrid.SigmaSwap.Camp03.NAME,
      Campbell_2003.COEFFS,
      NgaEastUsgs_2017.CONSTRAINTS),

  /** @see NgaEastUsgs_2017 */
  @Deprecated
  CEUS14_NGAE_SIGMA_FRANKEL_96(
      NgaEastHybrid.SigmaSwap.Fea96.class,
      NgaEastHybrid.SigmaSwap.Fea96.NAME,
      FrankelEtAl_1996.COEFFS,
      NgaEastUsgs_2017.CONSTRAINTS),

  /** @see NgaEastUsgs_2017 */
  @Deprecated
  CEUS14_NGAE_SIGMA_PEZESHK_11(
      NgaEastHybrid.SigmaSwap.Pezeshk11.class,
      NgaEastHybrid.SigmaSwap.Pezeshk11.NAME,
      PezeshkEtAl_2011.COEFFS,
      NgaEastUsgs_2017.CONSTRAINTS),

  /** @see NgaEastUsgs_2017 */
  @Deprecated
  CEUS14_NGAE_SIGMA_SILVA_02(
      NgaEastHybrid.SigmaSwap.Silva02.class,
      NgaEastHybrid.SigmaSwap.Silva02.NAME,
      SilvaEtAl_2002.COEFFS,
      NgaEastUsgs_2017.CONSTRAINTS),

  /** @see NgaEastUsgs_2017 */
  @Deprecated
  CEUS14_NGAE_SIGMA_SOMERVILLE_01(
      NgaEastHybrid.SigmaSwap.Somer01.class,
      NgaEastHybrid.SigmaSwap.Somer01.NAME,
      SomervilleEtAl_2001.COEFFS,
      NgaEastUsgs_2017.CONSTRAINTS),

  /** @see NgaEastUsgs_2017 */
  @Deprecated
  CEUS14_NGAE_SIGMA_TP_05(
      NgaEastHybrid.SigmaSwap.TP05.class,
      NgaEastHybrid.SigmaSwap.TP05.NAME,
      TavakoliPezeshk_2005.COEFFS,
      NgaEastUsgs_2017.CONSTRAINTS),

  /** @see NgaEastUsgs_2017 */
  @Deprecated
  CEUS14_NGAE_SIGMA_TORO_97_MW(
      NgaEastHybrid.SigmaSwap.Toro97.class,
      NgaEastHybrid.SigmaSwap.Toro97.NAME,
      ToroEtAl_1997.COEFFS_MW,
      NgaEastUsgs_2017.CONSTRAINTS),

  /* CEUS 2014 with NGA-East siteamp model. */

  /** @see NgaEastUsgs_2017 */
  @Deprecated
  CEUS14_NGAE_SITE_AB_06_PRIME(
      NgaEastHybrid.SiteSwap.AB06p.class,
      NgaEastHybrid.SiteSwap.AB06p.NAME,
      AtkinsonBoore_2006p.COEFFS,
      NgaEastUsgs_2017.CONSTRAINTS),

  /** @see NgaEastUsgs_2017 */
  @Deprecated
  CEUS14_NGAE_SITE_ATKINSON_08_PRIME(
      NgaEastHybrid.SiteSwap.A08p.class,
      NgaEastHybrid.SiteSwap.A08p.NAME,
      Atkinson_2008p.COEFFS,
      NgaEastUsgs_2017.CONSTRAINTS),

  /** @see NgaEastUsgs_2017 */
  @Deprecated
  CEUS14_NGAE_SITE_CAMPBELL_03(
      NgaEastHybrid.SiteSwap.Camp03.class,
      NgaEastHybrid.SiteSwap.Camp03.NAME,
      Campbell_2003.COEFFS,
      NgaEastUsgs_2017.CONSTRAINTS),

  /** @see NgaEastUsgs_2017 */
  @Deprecated
  CEUS14_NGAE_SITE_FRANKEL_96(
      NgaEastHybrid.SiteSwap.Fea96.class,
      NgaEastHybrid.SiteSwap.Fea96.NAME,
      FrankelEtAl_1996.COEFFS,
      NgaEastUsgs_2017.CONSTRAINTS),

  /** @see NgaEastUsgs_2017 */
  @Deprecated
  CEUS14_NGAE_SITE_PEZESHK_11(
      NgaEastHybrid.SiteSwap.Pezeshk11.class,
      NgaEastHybrid.SiteSwap.Pezeshk11.NAME,
      PezeshkEtAl_2011.COEFFS,
      NgaEastUsgs_2017.CONSTRAINTS),

  /** @see NgaEastUsgs_2017 */
  @Deprecated
  CEUS14_NGAE_SITE_SILVA_02(
      NgaEastHybrid.SiteSwap.Silva02.class,
      NgaEastHybrid.SiteSwap.Silva02.NAME,
      SilvaEtAl_2002.COEFFS,
      NgaEastUsgs_2017.CONSTRAINTS),

  /** @see NgaEastUsgs_2017 */
  @Deprecated
  CEUS14_NGAE_SITE_SOMERVILLE_01(
      NgaEastHybrid.SiteSwap.Somer01.class,
      NgaEastHybrid.SiteSwap.Somer01.NAME,
      SomervilleEtAl_2001.COEFFS,
      NgaEastUsgs_2017.CONSTRAINTS),

  /** @see NgaEastUsgs_2017 */
  @Deprecated
  CEUS14_NGAE_SITE_TP_05(
      NgaEastHybrid.SiteSwap.TP05.class,
      NgaEastHybrid.SiteSwap.TP05.NAME,
      TavakoliPezeshk_2005.COEFFS,
      NgaEastUsgs_2017.CONSTRAINTS),

  /** @see NgaEastUsgs_2017 */
  @Deprecated
  CEUS14_NGAE_SITE_TORO_97_MW(
      NgaEastHybrid.SiteSwap.Toro97.class,
      NgaEastHybrid.SiteSwap.Toro97.NAME,
      ToroEtAl_1997.COEFFS_MW,
      NgaEastUsgs_2017.CONSTRAINTS),

  /* Combined: must be declared after any dependent models aabove. */

  /**
   * 2014 CEUS weight-averaged GMM. This is the fault-variant that includes
   * Somerville (2011).
   */
  COMBINED_CEUS_2014(
      CombinedGmm.Ceus2014.class,
      CombinedGmm.Ceus2014.NAME,
      CombinedGmm.Ceus2014.COEFFS,
      CombinedGmm.Ceus2014.CONSTRAINTS),

  /**
   * 2018 CEUS weight-averaged GMM.
   */
  COMBINED_CEUS_2018(
      CombinedGmm.Ceus2018.class,
      CombinedGmm.Ceus2018.NAME,
      CombinedGmm.Ceus2018.COEFFS,
      CombinedGmm.Ceus2018.CONSTRAINTS),

  /**
   * 2014 CEUS weight-averaged GMM.
   */
  COMBINED_CEUS_2014_NGAE_SIGMA(
      CombinedGmm.Ceus2014_NgaEastSigma.class,
      CombinedGmm.Ceus2014_NgaEastSigma.NAME,
      CombinedGmm.Ceus2014_NgaEastSigma.COEFFS,
      CombinedGmm.Ceus2018.CONSTRAINTS),

  /**
   * 2014 CEUS weight-averaged GMM.
   */
  COMBINED_CEUS_2014_NGAE_SITE(
      CombinedGmm.Ceus2014_NgaEastSite.class,
      CombinedGmm.Ceus2014_NgaEastSite.NAME,
      CombinedGmm.Ceus2014_NgaEastSite.COEFFS,
      CombinedGmm.Ceus2018.CONSTRAINTS),

  /**
   * 2018 WUS weight-averaged GMM. These are the basin-amplifying flavors of
   * NGA-West2 and does not include Idriss.
   */
  COMBINED_WUS_2014_41(
      CombinedGmm.Wus2014_4p1.class,
      CombinedGmm.Wus2014_4p1.NAME,
      CombinedGmm.Wus2014_4p1.COEFFS,
      CombinedGmm.Wus2014_4p1.CONSTRAINTS),

  /**
   * 2018 WUS weight-averaged GMM. These are the basin-amplifying flavors of
   * NGA-West2 and does not include Idriss.
   */
  COMBINED_WUS_2014_42(
      CombinedGmm.Wus2014_4p2.class,
      CombinedGmm.Wus2014_4p2.NAME,
      CombinedGmm.Wus2014_4p2.COEFFS,
      CombinedGmm.Wus2014_4p2.CONSTRAINTS),

  /**
   * 2018 WUS weight-averaged GMM. These are the basin-amplifying flavors of
   * NGA-West2 and does not include Idriss.
   */
  COMBINED_WUS_2018(
      CombinedGmm.Wus2018.class,
      CombinedGmm.Wus2018.NAME,
      CombinedGmm.Wus2018.COEFFS,
      CombinedGmm.Wus2018.CONSTRAINTS);

  private final Class<? extends GroundMotionModel> delegate;
  private final String name;
  private final Set<Imt> imts;
  private final Constraints constraints;
  private final LoadingCache<Imt, GroundMotionModel> cache;

  private Gmm(
      Class<? extends GroundMotionModel> delegate,
      String name,
      CoefficientContainer coeffs,
      Constraints constraints) {

    this.delegate = delegate;
    this.name = name;
    this.constraints = constraints;
    imts = coeffs.imts();
    cache = CacheBuilder.newBuilder().build(new CacheLoader<Imt, GroundMotionModel>() {
      @Override
      public GroundMotionModel load(Imt imt) {
        return createInstance(imt);
      }
    });
  }

  private GroundMotionModel createInstance(Imt imt) {
    checkArgument(this.imts.contains(checkNotNull(imt)),
        "Gmm: %s does not support Imt: %s", this.name(), imt.name());
    try {
      Constructor<? extends GroundMotionModel> con = delegate.getDeclaredConstructor(Imt.class);
      GroundMotionModel gmm = con.newInstance(imt);
      return gmm;
    } catch (Exception e) {
      throw new RuntimeException(
          "Problem loading GMM cache; gmm: " + this.name() + " imt: " + imt, e);
    }
  }

  /**
   * Retreive an instance of a {@code GroundMotionModel}, either by creating a
   * new one, or fetching from a cache.
   *
   * @param imt of the retreived instance
   * @throws UncheckedExecutionException if there is an instantiation problem
   */
  public GroundMotionModel instance(Imt imt) {
    return cache.getUnchecked(imt);
  }

  /**
   * Retrieve an immutable map of {@code GroundMotionModel} instances, either by
   * creating new ones, or fetching them from a cache.
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
   * Retrieve immutable maps of {@code GroundMotionModel} instances for a range
   * of {@code Imt}s, either by creating new ones, or fetching them from a
   * cache.
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

  @Override
  public String toString() {
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
   * Return the set of spectral acceleration {@code Imt}s that are supported by
   * this {@code Gmm}.
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
   * Return the {@code Set} of {@code Gmm}s that support the supplied
   * {@code Imt}.
   * 
   * @param imt for which to return the {@code Gmm}s that support it
   */
  public static Set<Gmm> supportedGmms(final Imt imt) {
    return Sets.newEnumSet(Iterables.filter(
        EnumSet.allOf(Gmm.class),
        new Predicate<Gmm>() {
          @Override
          public boolean test(Gmm gmm) {
            return gmm.imts.contains(imt);
          }
        }::test),
        Gmm.class);
  }

  /**
   * Return the input {@code Constraints} for this {@code Gmm}.
   */
  public Constraints constraints() {
    return constraints;
  }

  @SuppressWarnings("javadoc")
  public enum Group {

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

    WUS_14_ACTIVE_CRUST(
        "2014 Active Crust (WUS)",
        ImmutableList.of(
            ASK_14,
            BSSA_14,
            CB_14,
            CY_14,
            IDRISS_14)),

    WUS_18_ACTIVE_CRUST(
        "2018 Active Crust (WUS)",
        ImmutableList.of(
            ASK_14_BASIN_AMP,
            BSSA_14_BASIN_AMP,
            CB_14_BASIN_AMP,
            CY_14_BASIN_AMP)),

    WUS_14_INTERFACE(
        "2014 Subduction Interface (WUS)",
        ImmutableList.of(
            AB_03_GLOBAL_INTERFACE,
            AM_09_INTERFACE,
            BCHYDRO_12_INTERFACE,
            ZHAO_06_INTERFACE)),

    WUS_18_INTERFACE(
        "2018 Subduction Interface (WUS) beta",
        ImmutableList.of(
            AM_09_INTERFACE_BASIN_AMP,
            BCHYDRO_12_INTERFACE_BASIN_AMP,
            AGA_16_INTERFACE_BASIN_AMP,
            ZHAO_06_INTERFACE_BASIN_AMP)),

    WUS_14_SLAB(
        "2014 Subduction Intraslab (WUS)",
        ImmutableList.of(
            AB_03_CASCADIA_SLAB_LOW_SAT,
            AB_03_GLOBAL_SLAB_LOW_SAT,
            BCHYDRO_12_SLAB,
            ZHAO_06_SLAB)),

    WUS_18_SLAB(
        "2018 Subduction Intraslab (WUS) beta",
        ImmutableList.of(
            BCHYDRO_12_SLAB_BASIN_AMP,
            AGA_16_SLAB_BASIN_AMP,
            ZHAO_06_SLAB_BASIN_AMP)),

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

    WUS_08_ACTIVE_CRUST(
        "2008 Active Crust (WUS)",
        ImmutableList.of(
            BA_08,
            CB_08,
            CY_08)),

    WUS_08_INTERFACE(
        "2008 Subduction Interface (WUS)",
        ImmutableList.of(
            AB_03_GLOBAL_INTERFACE,
            YOUNGS_97_INTERFACE,
            ZHAO_06_INTERFACE)),

    WUS_08_SLAB(
        "2008 Subduction Intraslab (WUS)",
        ImmutableList.of(
            AB_03_CASCADIA_SLAB,
            AB_03_GLOBAL_SLAB,
            YOUNGS_97_SLAB)),

    AK_07_ACTIVE_CRUST(
        "2007 Active Crust (AK)",
        ImmutableList.of(
            AS_97,
            BJF_97,
            CB_03,
            SADIGH_97)),

    AK_07_INTERFACE(
        "2007 Subduction Interface (AK)",
        ImmutableList.of(
            YOUNGS_97_INTERFACE,
            SADIGH_97)),

    AK_07_SLAB(
        "2007 Subduction Intraslab (AK)",
        ImmutableList.of(
            YOUNGS_97_SLAB,
            AB_03_GLOBAL_SLAB)),

    HI_98(
        "1998 Active Volcanic (HI)",
        ImmutableList.of(
            BJF_97,
            CAMPBELL_97,
            MT_97,
            SADIGH_97,
            YOUNGS_97_SLAB)),

    OTHER(
        "Others",
        ImmutableList.of(
            GK_15,
            WONG_15,
            ZHAO_16_SHALLOW_CRUST,
            ZHAO_16_UPPER_MANTLE,
            ZHAO_16_INTERFACE,
            ZHAO_16_SLAB,
            ATKINSON_10,
            ATKINSON_15,
            AB_03_CASCADIA_INTERFACE,
            MCVERRY_00_CRUSTAL,
            MCVERRY_00_INTERFACE,
            MCVERRY_00_SLAB,
            MCVERRY_00_VOLCANIC)),

    COMBINED(
        "Combined Models",
        ImmutableList.of(
            COMBINED_CEUS_2014,
            COMBINED_CEUS_2014_NGAE_SIGMA,
            COMBINED_CEUS_2014_NGAE_SITE,
            COMBINED_CEUS_2018,
            COMBINED_WUS_2014_41,
            COMBINED_WUS_2014_42,
            COMBINED_WUS_2018)),

    NGA_EAST(
        "NGA-East USGS Combined",
        ImmutableList.of(
            NGA_EAST_USGS_EPRI,
            NGA_EAST_USGS_PANEL,
            NGA_EAST_USGS,
            NGA_EAST_USGS_SEEDS,
            NGA_EAST_USGS_SEEDS_EPRI)),

    NGA_EAST_SIGMA_SITE(
        "NGA-East USGS Sigma & Site Studies",
        ImmutableList.of(
            CEUS14_NGAE_SIGMA_AB_06_PRIME,
            CEUS14_NGAE_SIGMA_ATKINSON_08_PRIME,
            CEUS14_NGAE_SIGMA_CAMPBELL_03,
            CEUS14_NGAE_SIGMA_FRANKEL_96,
            CEUS14_NGAE_SIGMA_PEZESHK_11,
            CEUS14_NGAE_SIGMA_SILVA_02,
            CEUS14_NGAE_SIGMA_SOMERVILLE_01,
            CEUS14_NGAE_SIGMA_TP_05,
            CEUS14_NGAE_SIGMA_TORO_97_MW,
            CEUS14_NGAE_SITE_AB_06_PRIME,
            CEUS14_NGAE_SITE_ATKINSON_08_PRIME,
            CEUS14_NGAE_SITE_CAMPBELL_03,
            CEUS14_NGAE_SITE_FRANKEL_96,
            CEUS14_NGAE_SITE_PEZESHK_11,
            CEUS14_NGAE_SITE_SILVA_02,
            CEUS14_NGAE_SITE_SOMERVILLE_01,
            CEUS14_NGAE_SITE_TP_05,
            CEUS14_NGAE_SITE_TORO_97_MW)),

    NGA_EAST_SAMMONS2(
        "NGA-East USGS Sammons",
        ImmutableList.of(
            NGA_EAST_USGS_1,
            NGA_EAST_USGS_2,
            NGA_EAST_USGS_3,
            NGA_EAST_USGS_4,
            NGA_EAST_USGS_5,
            NGA_EAST_USGS_6,
            NGA_EAST_USGS_7,
            NGA_EAST_USGS_8,
            NGA_EAST_USGS_9,
            NGA_EAST_USGS_10,
            NGA_EAST_USGS_11,
            NGA_EAST_USGS_12,
            NGA_EAST_USGS_13,
            NGA_EAST_USGS_14,
            NGA_EAST_USGS_15,
            NGA_EAST_USGS_16,
            NGA_EAST_USGS_17)),

    NGA_EAST_SEEDS(
        "NGA-East Seeds",
        ImmutableList.of(
            NGA_EAST_SEED_1CCSP,
            NGA_EAST_SEED_1CVSP,
            NGA_EAST_SEED_2CCSP,
            NGA_EAST_SEED_2CVSP,
            // NGA_EAST_SEED_ANC15,
            NGA_EAST_SEED_B_A04,
            NGA_EAST_SEED_B_AB14,
            NGA_EAST_SEED_B_AB95,
            NGA_EAST_SEED_B_BCA10D,
            NGA_EAST_SEED_B_BS11,
            NGA_EAST_SEED_B_SGD02,
            NGA_EAST_SEED_FRANKEL,
            NGA_EAST_SEED_GRAIZER,
            NGA_EAST_SEED_GRAIZER16,
            NGA_EAST_SEED_GRAIZER17,
            NGA_EAST_SEED_HA15,
            NGA_EAST_SEED_PEER_EX,
            NGA_EAST_SEED_PEER_GP,
            NGA_EAST_SEED_PZCT15_M1SS,
            NGA_EAST_SEED_PZCT15_M2ES,
            NGA_EAST_SEED_SP15,
            NGA_EAST_SEED_SP16,
            NGA_EAST_SEED_YA15));

    private final String name;
    private final List<Gmm> gmms;

    private Group(String name, List<Gmm> gmms) {
      this.name = name;
      this.gmms = gmms;
    }

    @Override
    public String toString() {
      return name;
    }

    /**
     * Return an immutable list of the Gmms in this group, sorted alphabetically
     * by their display name.
     */
    public List<Gmm> gmms() {
      return gmms;
    }
  }

}
