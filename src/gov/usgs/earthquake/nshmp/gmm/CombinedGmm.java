package gov.usgs.earthquake.nshmp.gmm;

import static gov.usgs.earthquake.nshmp.gmm.Gmm.AB_06_PRIME;
import static gov.usgs.earthquake.nshmp.gmm.Gmm.ASK_14;
import static gov.usgs.earthquake.nshmp.gmm.Gmm.ASK_14_BASIN_AMP;
import static gov.usgs.earthquake.nshmp.gmm.Gmm.ATKINSON_08_PRIME;
import static gov.usgs.earthquake.nshmp.gmm.Gmm.BSSA_14;
import static gov.usgs.earthquake.nshmp.gmm.Gmm.BSSA_14_BASIN_AMP;
import static gov.usgs.earthquake.nshmp.gmm.Gmm.CAMPBELL_03;
import static gov.usgs.earthquake.nshmp.gmm.Gmm.CB_14;
import static gov.usgs.earthquake.nshmp.gmm.Gmm.CB_14_BASIN_AMP;
import static gov.usgs.earthquake.nshmp.gmm.Gmm.CY_14;
import static gov.usgs.earthquake.nshmp.gmm.Gmm.CY_14_BASIN_AMP;
import static gov.usgs.earthquake.nshmp.gmm.Gmm.FRANKEL_96;
import static gov.usgs.earthquake.nshmp.gmm.Gmm.IDRISS_14;
import static gov.usgs.earthquake.nshmp.gmm.Gmm.NGA_EAST_USGS;
import static gov.usgs.earthquake.nshmp.gmm.Gmm.NGA_EAST_USGS_SEEDS;
import static gov.usgs.earthquake.nshmp.gmm.Gmm.PEZESHK_11;
import static gov.usgs.earthquake.nshmp.gmm.Gmm.SILVA_02;
import static gov.usgs.earthquake.nshmp.gmm.Gmm.SOMERVILLE_01;
import static gov.usgs.earthquake.nshmp.gmm.Gmm.TORO_97_MW;
import static gov.usgs.earthquake.nshmp.gmm.Gmm.TP_05;

import java.util.Map;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableMap;

import gov.usgs.earthquake.nshmp.gmm.GmmInput.Constraints;

/**
 * Convenience class for combined GMM implementations that compute weight
 * averaged ground motions and sigmas for a logic tree of ground motion models.
 * These are NOT intended for use in hazard calculations, but are made available
 * to support comparitive deterministic analysis of GMM logic trees.
 */
class CombinedGmm implements GroundMotionModel {

  private static final String NAME = "Combined: ";
  private final Map<GroundMotionModel, Double> gmms;

  /* Supply map of ground motion models initialized to the required IMT. */
  private CombinedGmm(Imt imt, Map<Gmm, Double> gmms) {
    this.gmms = instancesForImt(imt, gmms);
  }

  @Override
  public ScalarGroundMotion calc(GmmInput in) {

    Map<ScalarGroundMotion, Double> sgms = gmms.entrySet().stream()
        .collect(Collectors.toMap(
            entry -> entry.getKey().calc(in),
            Map.Entry::getValue));

    /*
     * Mean values are converted to linear space for combining and returned to
     * natural log space upon return.
     */
    double μ = sgms.entrySet().stream()
        .collect(Collectors.summingDouble(
            entry -> Math.exp(entry.getKey().mean()) * entry.getValue()));

    double σ = sgms.entrySet().stream()
        .collect(Collectors.summingDouble(
            entry -> entry.getKey().sigma() * entry.getValue()));

    return DefaultScalarGroundMotion.create(Math.log(μ), σ);
  }

  static Map<GroundMotionModel, Double> instancesForImt(
      Imt imt,
      Map<Gmm, Double> gmms) {

    return gmms.entrySet().stream()
        .collect(Collectors.toMap(
            entry -> entry.getKey().instance(imt),
            Map.Entry::getValue));
  }

  /*
   * Implementations. For each, coefficients are only used to get the set of
   * supported IMTs and therefore reference a model that supports the
   * intersection of all support IMTs.
   */

  static final Map<Gmm, Double> CEUS_2014_FAULT = ImmutableMap.<Gmm, Double> builder()
      .put(AB_06_PRIME, 0.22)
      .put(ATKINSON_08_PRIME, 0.08)
      .put(CAMPBELL_03, 0.11)
      .put(FRANKEL_96, 0.06)
      .put(PEZESHK_11, 0.15)
      .put(SILVA_02, 0.06)
      .put(SOMERVILLE_01, 0.1)
      .put(TP_05, 0.11)
      .put(TORO_97_MW, 0.11)
      .build();

  /* Need to allow Vs30=3000 through for comparison plots. */
  private static final Constraints CEUS_2014_CONSTRAINTS = Constraints.builder()
      .withDefaults()
      .build();

  /* Fault variant that includes Somerville. */
  static final class Ceus2014 extends CombinedGmm {

    static final String NAME = CombinedGmm.NAME + "CEUS 2014 (4.*)";
    static final Constraints CONSTRAINTS = CEUS_2014_CONSTRAINTS;
    static final CoefficientContainer COEFFS = FrankelEtAl_1996.COEFFS;

    Ceus2014(Imt imt) {
      super(imt, CEUS_2014_FAULT);
    }

    @Override
    public ScalarGroundMotion calc(GmmInput in) {
      GmmInput.Builder b = GmmInput.builder().fromCopy(in);
      b.vs30(in.vs30 <= 760.0 ? 760.0 : 2000.0);
      return super.calc(b.build());
    }
  }

  /* 5.0 */
  private static final Map<Gmm, Double> CEUS_2018 = ImmutableMap.<Gmm, Double> builder()
      .put(NGA_EAST_USGS, 0.667)
      .put(NGA_EAST_USGS_SEEDS, 0.333)
      .build();

  static final class Ceus2018 extends CombinedGmm {

    static final String NAME = CombinedGmm.NAME + "CEUS 2018 (5.0)";
    static final Constraints CONSTRAINTS = NgaEastUsgs_2017.CONSTRAINTS;
    static final CoefficientContainer COEFFS = NgaEastUsgs_2017.COEFFS_SIGMA_MID;

    Ceus2018(Imt imt) {
      super(imt, CEUS_2018);
    }
  }
  
  private static final Map<Gmm, Double> WUS_2014_4P1 = ImmutableMap.<Gmm, Double> builder()
      .put(ASK_14, 0.22)
      .put(BSSA_14, 0.22)
      .put(CB_14, 0.22)
      .put(CY_14, 0.22)
      .put(IDRISS_14, 0.12)
      .build();

  /* 4.1 */
  static final class Wus2014_4p1 extends CombinedGmm {

    static final String NAME = CombinedGmm.NAME + "WUS 2014 (4.1)";
    static final Constraints CONSTRAINTS = CampbellBozorgnia_2014.CONSTRAINTS;
    static final CoefficientContainer COEFFS = CampbellBozorgnia_2014.COEFFS;

    Wus2014_4p1(Imt imt) {
      super(imt, WUS_2014_4P1);
    }
  }

  private static final Map<Gmm, Double> WUS_2014_4P2 = ImmutableMap.<Gmm, Double> builder()
      .put(ASK_14, 0.25)
      .put(BSSA_14, 0.25)
      .put(CB_14, 0.25)
      .put(CY_14, 0.25)
      .build();

  /* 4.2 */
  static final class Wus2014_4p2 extends CombinedGmm {

    static final String NAME = CombinedGmm.NAME + "WUS 2014 (4.2, no Idriss)";
    static final Constraints CONSTRAINTS = CampbellBozorgnia_2014.CONSTRAINTS;
    static final CoefficientContainer COEFFS = CampbellBozorgnia_2014.COEFFS;

    Wus2014_4p2(Imt imt) {
      super(imt, WUS_2014_4P2);
    }
  }

  private static final Map<Gmm, Double> WUS_2018 = ImmutableMap.<Gmm, Double> builder()
      .put(ASK_14_BASIN_AMP, 0.25)
      .put(BSSA_14_BASIN_AMP, 0.25)
      .put(CB_14_BASIN_AMP, 0.25)
      .put(CY_14_BASIN_AMP, 0.25)
      .build();

  /* No Idriss. */
  static final class Wus2018 extends CombinedGmm {

    static final String NAME = CombinedGmm.NAME + "WUS 2018 (5.0, basin amp)";
    static final Constraints CONSTRAINTS = CampbellBozorgnia_2014.CONSTRAINTS;
    static final CoefficientContainer COEFFS = CampbellBozorgnia_2014.COEFFS;

    Wus2018(Imt imt) {
      super(imt, WUS_2018);
    }
  }

}
