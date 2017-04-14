package org.opensha2.gmm;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import org.opensha2.gmm.CeusMb.AtkinsonBoore_2006_140bar_AB;
import org.opensha2.gmm.CeusMb.AtkinsonBoore_2006_140bar_J;
import org.opensha2.gmm.CeusMb.AtkinsonBoore_2006_200bar_AB;
import org.opensha2.gmm.CeusMb.AtkinsonBoore_2006_200bar_J;
import org.opensha2.gmm.CeusMb.Campbell_2003_AB;
import org.opensha2.gmm.CeusMb.Campbell_2003_J;
import org.opensha2.gmm.CeusMb.FrankelEtAl_1996_AB;
import org.opensha2.gmm.CeusMb.FrankelEtAl_1996_J;
import org.opensha2.gmm.CeusMb.SilvaEtAl_2002_AB;
import org.opensha2.gmm.CeusMb.SilvaEtAl_2002_J;
import org.opensha2.gmm.CeusMb.TavakoliPezeshk_2005_AB;
import org.opensha2.gmm.CeusMb.TavakoliPezeshk_2005_J;
import org.opensha2.gmm.GmmInput.Constraints;

import com.google.common.base.Predicate;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.UncheckedExecutionException;

import java.lang.reflect.Constructor;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

  /* Subduction Interface and Slab WUS 2008 2014, AK 2007 */

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

  /** @see Atkinson_2015 */
  ATKINSON_15(
      Atkinson_2015.class,
      Atkinson_2015.NAME,
      Atkinson_2015.COEFFS,
      Atkinson_2015.CONSTRAINTS),

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

  /* NGA-East */

  /** @see NgaEast_2016 */
  NGA_EAST_CENTER(
      NgaEast_2016.Center.class,
      NgaEast_2016.Center.NAME,
      NgaEast_2016.COEFFS_SIGMA_MID,
      NgaEast_2016.CONSTRAINTS),

  /** @see NgaEast_2016 */
  NGA_EAST_CENTER_NGAW2(
      NgaEast_2016.CenterNga.class,
      NgaEast_2016.CenterNga.NAME,
      NgaEast_2016.COEFFS_SIGMA_NGAW2,
      NgaEast_2016.CONSTRAINTS),

  /** @see NgaEast_2016 */
  NGA_EAST_50TH(
      NgaEast_2016.Percentile50th.class,
      NgaEast_2016.Percentile50th.NAME,
      NgaEast_2016.COEFFS_SIGMA_MID,
      NgaEast_2016.CONSTRAINTS),

  /** @see NgaEast_2016 */
  NGA_EAST_50TH_NGAW2(
      NgaEast_2016.Percentile50thNga.class,
      NgaEast_2016.Percentile50thNga.NAME,
      NgaEast_2016.COEFFS_SIGMA_NGAW2,
      NgaEast_2016.CONSTRAINTS),

  /** @see NgaEast_2016 */
  NGA_EAST_84TH(
      NgaEast_2016.Percentile84th.class,
      NgaEast_2016.Percentile84th.NAME,
      NgaEast_2016.COEFFS_SIGMA_MID,
      NgaEast_2016.CONSTRAINTS),

  /** @see NgaEast_2016 */
  NGA_EAST_84TH_NGAW2(
      NgaEast_2016.Percentile84thNga.class,
      NgaEast_2016.Percentile84thNga.NAME,
      NgaEast_2016.COEFFS_SIGMA_NGAW2,
      NgaEast_2016.CONSTRAINTS),

  /** @see NgaEast_2016 */
  NGA_EAST_98TH(
      NgaEast_2016.Percentile98th.class,
      NgaEast_2016.Percentile98th.NAME,
      NgaEast_2016.COEFFS_SIGMA_MID,
      NgaEast_2016.CONSTRAINTS),

  /** @see NgaEast_2016 */
  NGA_EAST_98TH_NGAW2(
      NgaEast_2016.Percentile98thNga.class,
      NgaEast_2016.Percentile98thNga.NAME,
      NgaEast_2016.COEFFS_SIGMA_NGAW2,
      NgaEast_2016.CONSTRAINTS),

  /** @see NgaEast_2016 */
  NGA_EAST_TOTAL(
      NgaEast_2016.Total.class,
      NgaEast_2016.Total.NAME,
      NgaEast_2016.COEFFS_SIGMA_MID,
      NgaEast_2016.CONSTRAINTS),

  /** @see NgaEast_2016 */
  NGA_EAST_TOTAL_NGAW2(
      NgaEast_2016.TotalNga.class,
      NgaEast_2016.TotalNga.NAME,
      NgaEast_2016.COEFFS_SIGMA_NGAW2,
      NgaEast_2016.CONSTRAINTS),

  /** @see NgaEast_2016 */
  NGA_EAST_SEED_1CCSP(
      NgaEast_2016.Seed_1CCSP.class,
      NgaEast_2016.Seed_1CCSP.NAME,
      NgaEast_2016.COEFFS_SIGMA_MID,
      NgaEast_2016.CONSTRAINTS),

  /** @see NgaEast_2016 */
  NGA_EAST_SEED_1CCSP_NGAW2(
      NgaEast_2016.SeedNga_1CCSP.class,
      NgaEast_2016.SeedNga_1CCSP.NAME,
      NgaEast_2016.COEFFS_SIGMA_NGAW2,
      NgaEast_2016.CONSTRAINTS),

  /** @see NgaEast_2016 */
  NGA_EAST_SEED_1CVSP(
      NgaEast_2016.Seed_1CVSP.class,
      NgaEast_2016.Seed_1CVSP.NAME,
      NgaEast_2016.COEFFS_SIGMA_MID,
      NgaEast_2016.CONSTRAINTS),

  /** @see NgaEast_2016 */
  NGA_EAST_SEED_1CVSP_NGAW2(
      NgaEast_2016.SeedNga_1CVSP.class,
      NgaEast_2016.SeedNga_1CVSP.NAME,
      NgaEast_2016.COEFFS_SIGMA_NGAW2,
      NgaEast_2016.CONSTRAINTS),

  /** @see NgaEast_2016 */
  NGA_EAST_SEED_2CCSP(
      NgaEast_2016.Seed_2CCSP.class,
      NgaEast_2016.Seed_2CCSP.NAME,
      NgaEast_2016.COEFFS_SIGMA_MID,
      NgaEast_2016.CONSTRAINTS),

  /** @see NgaEast_2016 */
  NGA_EAST_SEED_2CCSP_NGAW2(
      NgaEast_2016.SeedNga_2CCSP.class,
      NgaEast_2016.SeedNga_2CCSP.NAME,
      NgaEast_2016.COEFFS_SIGMA_NGAW2,
      NgaEast_2016.CONSTRAINTS),

  /** @see NgaEast_2016 */
  NGA_EAST_SEED_2CVSP(
      NgaEast_2016.Seed_2CVSP.class,
      NgaEast_2016.Seed_2CVSP.NAME,
      NgaEast_2016.COEFFS_SIGMA_MID,
      NgaEast_2016.CONSTRAINTS),

  /** @see NgaEast_2016 */
  NGA_EAST_SEED_2CVSP_NGAW2(
      NgaEast_2016.SeedNga_2CVSP.class,
      NgaEast_2016.SeedNga_2CVSP.NAME,
      NgaEast_2016.COEFFS_SIGMA_NGAW2,
      NgaEast_2016.CONSTRAINTS),

  
//  /** @see NgaEast_2016 */
//  NGA_EAST_SEED_ANC15(
//      NgaEast_2016.Seed_ANC15.class,
//      NgaEast_2016.Seed_ANC15.NAME,
//      NgaEast_2016.COEFFS_SIGMA_MID,
//      NgaEast_2016.CONSTRAINTS),

//  /** @see NgaEast_2016 */
//  NGA_EAST_SEED_ANC15_NGAW2(
//      NgaEast_2016.SeedNga_ANC15.class,
//      NgaEast_2016.SeedNga_ANC15.NAME,
//      NgaEast_2016.COEFFS_SIGMA_NGAW2,
//      NgaEast_2016.CONSTRAINTS),
  
  /** @see NgaEast_2016 */
  NGA_EAST_SEED_B_a04(
      NgaEast_2016.Seed_B_a04.class,
      NgaEast_2016.Seed_B_a04.NAME,
      NgaEast_2016.COEFFS_SIGMA_MID,
      NgaEast_2016.CONSTRAINTS),

  /** @see NgaEast_2016 */
  NGA_EAST_SEED_B_a04_NGAW2(
      NgaEast_2016.SeedNga_B_a04.class,
      NgaEast_2016.SeedNga_B_a04.NAME,
      NgaEast_2016.COEFFS_SIGMA_NGAW2,
      NgaEast_2016.CONSTRAINTS),

  /** @see NgaEast_2016 */
  NGA_EAST_SEED_B_ab14(
      NgaEast_2016.Seed_B_ab14.class,
      NgaEast_2016.Seed_B_ab14.NAME,
      NgaEast_2016.COEFFS_SIGMA_MID,
      NgaEast_2016.CONSTRAINTS),

  /** @see NgaEast_2016 */
  NGA_EAST_SEED_B_ab14_NGAW2(
      NgaEast_2016.SeedNga_B_ab14.class,
      NgaEast_2016.SeedNga_B_ab14.NAME,
      NgaEast_2016.COEFFS_SIGMA_NGAW2,
      NgaEast_2016.CONSTRAINTS),

  /** @see NgaEast_2016 */
  NGA_EAST_SEED_B_ab95(
      NgaEast_2016.Seed_B_ab95.class,
      NgaEast_2016.Seed_B_ab95.NAME,
      NgaEast_2016.COEFFS_SIGMA_MID,
      NgaEast_2016.CONSTRAINTS),

  /** @see NgaEast_2016 */
  NGA_EAST_SEED_B_ab95_NGAW2(
      NgaEast_2016.SeedNga_B_ab95.class,
      NgaEast_2016.SeedNga_B_ab95.NAME,
      NgaEast_2016.COEFFS_SIGMA_NGAW2,
      NgaEast_2016.CONSTRAINTS),

  /** @see NgaEast_2016 */
  NGA_EAST_SEED_B_bca10d(
      NgaEast_2016.Seed_B_bca10d.class,
      NgaEast_2016.Seed_B_bca10d.NAME,
      NgaEast_2016.COEFFS_SIGMA_MID,
      NgaEast_2016.CONSTRAINTS),

  /** @see NgaEast_2016 */
  NGA_EAST_SEED_B_bca10d_NGAW2(
      NgaEast_2016.SeedNga_B_bca10d.class,
      NgaEast_2016.SeedNga_B_bca10d.NAME,
      NgaEast_2016.COEFFS_SIGMA_NGAW2,
      NgaEast_2016.CONSTRAINTS),

  /** @see NgaEast_2016 */
  NGA_EAST_SEED_B_bs11(
      NgaEast_2016.Seed_B_bs11.class,
      NgaEast_2016.Seed_B_bs11.NAME,
      NgaEast_2016.COEFFS_SIGMA_MID,
      NgaEast_2016.CONSTRAINTS),

  /** @see NgaEast_2016 */
  NGA_EAST_SEED_B_bs11_NGAW2(
      NgaEast_2016.SeedNga_B_bs11.class,
      NgaEast_2016.SeedNga_B_bs11.NAME,
      NgaEast_2016.COEFFS_SIGMA_NGAW2,
      NgaEast_2016.CONSTRAINTS),

  /** @see NgaEast_2016 */
  NGA_EAST_SEED_B_sgd02(
      NgaEast_2016.Seed_B_sgd02.class,
      NgaEast_2016.Seed_B_sgd02.NAME,
      NgaEast_2016.COEFFS_SIGMA_MID,
      NgaEast_2016.CONSTRAINTS),

  /** @see NgaEast_2016 */
  NGA_EAST_SEED_B_sgd02_NGAW2(
      NgaEast_2016.SeedNga_B_sgd02.class,
      NgaEast_2016.SeedNga_B_sgd02.NAME,
      NgaEast_2016.COEFFS_SIGMA_NGAW2,
      NgaEast_2016.CONSTRAINTS),

  /** @see NgaEast_2016 */
  NGA_EAST_SEED_Frankel(
      NgaEast_2016.Seed_Frankel.class,
      NgaEast_2016.Seed_Frankel.NAME,
      NgaEast_2016.COEFFS_SIGMA_MID,
      NgaEast_2016.CONSTRAINTS),

  /** @see NgaEast_2016 */
  NGA_EAST_SEED_Frankel_NGAW2(
      NgaEast_2016.SeedNga_Frankel.class,
      NgaEast_2016.SeedNga_Frankel.NAME,
      NgaEast_2016.COEFFS_SIGMA_NGAW2,
      NgaEast_2016.CONSTRAINTS),

  /** @see NgaEast_2016 */
  NGA_EAST_SEED_Graizer(
      NgaEast_2016.Seed_Graizer.class,
      NgaEast_2016.Seed_Graizer.NAME,
      NgaEast_2016.COEFFS_SIGMA_MID,
      NgaEast_2016.CONSTRAINTS),

  /** @see NgaEast_2016 */
  NGA_EAST_SEED_Graizer_NGAW2(
      NgaEast_2016.SeedNga_Graizer.class,
      NgaEast_2016.SeedNga_Graizer.NAME,
      NgaEast_2016.COEFFS_SIGMA_NGAW2,
      NgaEast_2016.CONSTRAINTS),

  /** @see NgaEast_2016 */
  NGA_EAST_SEED_HA15(
      NgaEast_2016.Seed_HA15.class,
      NgaEast_2016.Seed_HA15.NAME,
      NgaEast_2016.COEFFS_SIGMA_MID,
      NgaEast_2016.CONSTRAINTS),

  /** @see NgaEast_2016 */
  NGA_EAST_SEED_HA15_NGAW2(
      NgaEast_2016.SeedNga_HA15.class,
      NgaEast_2016.SeedNga_HA15.NAME,
      NgaEast_2016.COEFFS_SIGMA_NGAW2,
      NgaEast_2016.CONSTRAINTS),

  /** @see NgaEast_2016 */
  NGA_EAST_SEED_PEER_EX(
      NgaEast_2016.Seed_PEER_EX.class,
      NgaEast_2016.Seed_PEER_EX.NAME,
      NgaEast_2016.COEFFS_SIGMA_MID,
      NgaEast_2016.CONSTRAINTS),

  /** @see NgaEast_2016 */
  NGA_EAST_SEED_PEER_EX_NGAW2(
      NgaEast_2016.SeedNga_PEER_EX.class,
      NgaEast_2016.SeedNga_PEER_EX.NAME,
      NgaEast_2016.COEFFS_SIGMA_NGAW2,
      NgaEast_2016.CONSTRAINTS),

  /** @see NgaEast_2016 */
  NGA_EAST_SEED_PEER_GP(
      NgaEast_2016.Seed_PEER_GP.class,
      NgaEast_2016.Seed_PEER_GP.NAME,
      NgaEast_2016.COEFFS_SIGMA_MID,
      NgaEast_2016.CONSTRAINTS),

  /** @see NgaEast_2016 */
  NGA_EAST_SEED_PEER_GP_NGAW2(
      NgaEast_2016.SeedNga_PEER_GP.class,
      NgaEast_2016.SeedNga_PEER_GP.NAME,
      NgaEast_2016.COEFFS_SIGMA_NGAW2,
      NgaEast_2016.CONSTRAINTS),

  /** @see NgaEast_2016 */
  NGA_EAST_SEED_PZCT15_M1SS(
      NgaEast_2016.Seed_PZCT15_M1SS.class,
      NgaEast_2016.Seed_PZCT15_M1SS.NAME,
      NgaEast_2016.COEFFS_SIGMA_MID,
      NgaEast_2016.CONSTRAINTS),

  /** @see NgaEast_2016 */
  NGA_EAST_SEED_PZCT15_M1SS_NGAW2(
      NgaEast_2016.SeedNga_PZCT15_M1SS.class,
      NgaEast_2016.SeedNga_PZCT15_M1SS.NAME,
      NgaEast_2016.COEFFS_SIGMA_NGAW2,
      NgaEast_2016.CONSTRAINTS),

  /** @see NgaEast_2016 */
  NGA_EAST_SEED_PZCT15_M2ES(
      NgaEast_2016.Seed_PZCT15_M2ES.class,
      NgaEast_2016.Seed_PZCT15_M2ES.NAME,
      NgaEast_2016.COEFFS_SIGMA_MID,
      NgaEast_2016.CONSTRAINTS),

  /** @see NgaEast_2016 */
  NGA_EAST_SEED_PZCT15_M2ES_NGAW2(
      NgaEast_2016.SeedNga_PZCT15_M2ES.class,
      NgaEast_2016.SeedNga_PZCT15_M2ES.NAME,
      NgaEast_2016.COEFFS_SIGMA_NGAW2,
      NgaEast_2016.CONSTRAINTS),

  /** @see NgaEast_2016 */
  NGA_EAST_SEED_SP15(
      NgaEast_2016.Seed_SP15.class,
      NgaEast_2016.Seed_SP15.NAME,
      NgaEast_2016.COEFFS_SIGMA_MID,
      NgaEast_2016.CONSTRAINTS),

  /** @see NgaEast_2016 */
  NGA_EAST_SEED_SP15_NGAW2(
      NgaEast_2016.SeedNga_SP15.class,
      NgaEast_2016.SeedNga_SP15.NAME,
      NgaEast_2016.COEFFS_SIGMA_NGAW2,
      NgaEast_2016.CONSTRAINTS),

  /** @see NgaEast_2016 */
  NGA_EAST_SEED_YA15(
      NgaEast_2016.Seed_YA15.class,
      NgaEast_2016.Seed_YA15.NAME,
      NgaEast_2016.COEFFS_SIGMA_MID,
      NgaEast_2016.CONSTRAINTS),

  /** @see NgaEast_2016 */
  NGA_EAST_SEED_YA15_NGAW2(
      NgaEast_2016.SeedNga_YA15.class,
      NgaEast_2016.SeedNga_YA15.NAME,
      NgaEast_2016.COEFFS_SIGMA_NGAW2,
      NgaEast_2016.CONSTRAINTS);

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
      @Override
      public GroundMotionModel load(Imt imt) throws Exception {
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
          public boolean apply(Gmm gmm) {
            return gmm.imts.contains(imt);
          }
        }),
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

    WUS_14_INTERFACE(
        "2014 Subduction Interface (WUS)",
        ImmutableList.of(
            AB_03_GLOB_INTER,
            AM_09_INTER,
            BCHYDRO_12_INTER,
            ZHAO_06_INTER)),

    WUS_14_SLAB(
        "2014 Subduction Intraslab (WUS)",
        ImmutableList.of(
            AB_03_CASC_SLAB_LOW_SAT,
            AB_03_GLOB_SLAB_LOW_SAT,
            BCHYDRO_12_SLAB,
            ZHAO_06_SLAB)),

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
            AB_03_GLOB_INTER,
            YOUNGS_97_INTER,
            ZHAO_06_INTER)),

    WUS_08_SLAB(
        "2008 Subduction Intraslab (WUS)",
        ImmutableList.of(
            AB_03_CASC_SLAB,
            AB_03_GLOB_SLAB,
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
            YOUNGS_97_INTER,
            SADIGH_97)),

    AK_07_SLAB(
        "2007 Subduction Intraslab (AK)",
        ImmutableList.of(
            YOUNGS_97_SLAB,
            AB_03_GLOB_SLAB)),

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
            ATKINSON_15,
            AB_03_CASC_INTER,
            MCVERRY_00_CRUSTAL,
            MCVERRY_00_INTERFACE,
            MCVERRY_00_SLAB,
            MCVERRY_00_VOLCANIC)),

    NGA_EAST(
        "NGA-East",
        ImmutableList.of(
            NGA_EAST_CENTER,
            NGA_EAST_50TH,
            NGA_EAST_84TH,
            NGA_EAST_98TH,
            NGA_EAST_TOTAL)),

//    NGA_EAST_NGAW2(
//        "NGA-East (NGAW2 sigma)",
//        ImmutableList.of(
//            NGA_EAST_CENTER_NGAW2,
//            NGA_EAST_50TH_NGAW2,
//            NGA_EAST_84TH_NGAW2,
//            NGA_EAST_98TH_NGAW2,
//            NGA_EAST_TOTAL_NGAW2)),

    NGA_EAST_SEEDS(
        "NGA-East Seed Models",
        ImmutableList.of(
            NGA_EAST_SEED_1CCSP,
            NGA_EAST_SEED_1CVSP,
            NGA_EAST_SEED_2CCSP,
            NGA_EAST_SEED_2CVSP,
//            NGA_EAST_SEED_ANC15,
            NGA_EAST_SEED_B_a04,
            NGA_EAST_SEED_B_ab14,
            NGA_EAST_SEED_B_ab95,
            NGA_EAST_SEED_B_bca10d,
            NGA_EAST_SEED_B_bs11,
            NGA_EAST_SEED_B_sgd02,
            NGA_EAST_SEED_Frankel,
            NGA_EAST_SEED_Graizer,
            NGA_EAST_SEED_HA15,
            NGA_EAST_SEED_PEER_EX,
            NGA_EAST_SEED_PEER_GP,
            NGA_EAST_SEED_PZCT15_M1SS,
            NGA_EAST_SEED_PZCT15_M2ES,
            NGA_EAST_SEED_SP15,
            NGA_EAST_SEED_YA15));

//    NGA_EAST_SEEDS_NGAW2(
//        "NGA-East Seed Models (NGAW2 sigma)",
//        ImmutableList.of(
//            NGA_EAST_SEED_1CCSP_NGAW2,
//            NGA_EAST_SEED_1CVSP_NGAW2,
//            NGA_EAST_SEED_2CCSP_NGAW2,
//            NGA_EAST_SEED_2CVSP_NGAW2,
//            NGA_EAST_SEED_ANC15_NGAW2,
//            NGA_EAST_SEED_B_a04_NGAW2,
//            NGA_EAST_SEED_B_ab14_NGAW2,
//            NGA_EAST_SEED_B_ab95_NGAW2,
//            NGA_EAST_SEED_B_bca10d_NGAW2,
//            NGA_EAST_SEED_B_bs11_NGAW2,
//            NGA_EAST_SEED_B_sgd02_NGAW2,
//            NGA_EAST_SEED_Frankel_NGAW2,
//            NGA_EAST_SEED_Graizer_NGAW2,
//            NGA_EAST_SEED_HA15_NGAW2,
//            NGA_EAST_SEED_PEER_EX_NGAW2,
//            NGA_EAST_SEED_PEER_GP_NGAW2,
//            NGA_EAST_SEED_PZCT15_M1SS_NGAW2,
//            NGA_EAST_SEED_PZCT15_M2ES_NGAW2,
//            NGA_EAST_SEED_SP15_NGAW2,
//            NGA_EAST_SEED_YA15_NGAW2));

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
