package gov.usgs.earthquake.nshmp.gmm;

import static gov.usgs.earthquake.nshmp.gmm.Gmm.AB_06_PRIME;
import static gov.usgs.earthquake.nshmp.gmm.Gmm.ATKINSON_08_PRIME;
import static gov.usgs.earthquake.nshmp.gmm.Gmm.CAMPBELL_03;
import static gov.usgs.earthquake.nshmp.gmm.Gmm.FRANKEL_96;
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
 * Temporary convenience class that computes weight averaged ground motions and
 * sigmas of the 2014 CEUS GMMs. Fault variant that includes Somerville.
 */
class NshmCeus2014 implements GroundMotionModel {

  static final String NAME = "2014 CEUS Combined";

  static final Constraints CONSTRAINTS = FrankelEtAl_1996.CONSTRAINTS;
  static final CoefficientContainer COEFFS = FrankelEtAl_1996.COEFFS;

  private static final Map<Gmm, Double> GMM_WT_MAP = ImmutableMap.<Gmm, Double> builder()
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

  private final Map<GroundMotionModel, Double> gmms;

  NshmCeus2014(Imt imt) {
    gmms = GMM_WT_MAP.entrySet().stream()
        .collect(Collectors.toMap(
            entry -> entry.getKey().instance(imt),
            Map.Entry::getValue));
  }

  @Override
  public ScalarGroundMotion calc(GmmInput in) {

    Map<ScalarGroundMotion, Double> sgms = gmms.entrySet().stream()
        .collect(Collectors.toMap(
            entry -> entry.getKey().calc(in),
            Map.Entry::getValue));

    double μ = sgms.entrySet().stream()
        .collect(Collectors.summingDouble(
            entry -> entry.getKey().mean() * entry.getValue()));

    double σ = sgms.entrySet().stream()
        .collect(Collectors.summingDouble(
            entry -> entry.getKey().sigma() * entry.getValue()));

    return DefaultScalarGroundMotion.create(μ, σ);
  }
}
