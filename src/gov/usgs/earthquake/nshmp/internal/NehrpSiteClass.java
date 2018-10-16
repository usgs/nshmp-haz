package gov.usgs.earthquake.nshmp.internal;

/**
 * Placeholder enum for likely move to Nehrp site class identifier instead of
 * Vs30.
 * 
 * <p>These site class identifiers map to NEHRP site clases, but the intent is
 * that they can be used more generally for models in other parts of the world
 * where the GMMs are not necessarily parameterized in terms of vs30 to define
 * site response. For instance, NZ/JP site classes might use A, B, C, and D as a
 * proxy for the local I, II, III, and IV identifiers. In the U.S., models will
 * need to specify the Vs30 value that each site class corresponds to. Although
 * the values were consistent over prior models, now that multiple site classes
 * are supported (in 2018) across the entire U.S., there have been changes
 * proposed for balloting by the BSSC to make the Vs30 definitions of site
 * classes consistent in how they are calculated.
 * 
 * @author Peter Powers
 */
enum SiteClass {

  /*
   * Notes on calculation of Vs30 for site class:
   * 
   * Question: Why is it that the soil shear wave velocity shown in the Unified
   * Hazard Tool is not equal to the average of the values shown in ASCE 7-10
   * table 20.3-1?
   * 
   * For instance: 259 m/s (Site Class D), from the Unified Hazard Tool, is not
   * equal to (600 ft/s + 1200 ft/s)/2 * .3048 = 274 m/s
   * 
   * Answer (Sanaz): we take the geometric mean: sqrt(1200*600)*0.3048 =
   * 258.6314 , which rounds to 259m/s.
   * 
   *
   */

  /* OLD Vs30, NEW Vs30 */

  /* 2000 2000 */
  A,

  /* 1500 1500 (new) */
  AB,

  /* 1150 1080 */
  B,

  /* 760 760 */
  BC,

  /* 537 530 */
  C,

  /* 360 365 */
  CD,

  /* 259 260 */
  D,

  /* 180 185 */
  DE,

  /* 150 150  (new) */
  E,

  ;
}
