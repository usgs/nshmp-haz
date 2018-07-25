package gov.usgs.earthquake.nshmp.gmm;

import static com.google.common.base.Preconditions.checkArgument;
import static gov.usgs.earthquake.nshmp.data.Data.checkWeights;

import java.util.Map;
import java.util.Map.Entry;

import com.google.common.collect.ImmutableMap;

/**
 * Class provides internal support for extrapolation to long spectral periods.
 * 
 * <p>Extrapolation requires a logic tree of reference models, the highest
 * spectral period supported by the Gmm of interest and reference models, and
 * the target spectral period. Ratios are computed internally using log
 * ground-motion.
 * 
 * @author Peter Powers
 */
class ExtrapolatedGmm implements GroundMotionModel {

  private final GroundMotionModel gmm;
  private final Map<GroundMotionModel, Double> refsCommon;
  private final Map<GroundMotionModel, Double> refsTarget;

  ExtrapolatedGmm(
      Gmm gmm,
      Imt targetImt,
      Imt commonImt,
      Map<Gmm, Double> refs) {

    /*
     * Gmm weights map must sum to 1.0 and the supplied Gmms must support the
     * max and target IMTs.
     */

    checkArgument(targetImt.isSA());
    checkArgument(commonImt.isSA());
    checkArgument(targetImt.ordinal() > commonImt.ordinal());
    checkWeights(refs.values(), false);

    this.gmm = gmm.instance(commonImt);

    /* Reference GMMs at max IMT. */
    this.refsCommon = refs.entrySet()
        .stream()
        .collect(ImmutableMap.toImmutableMap(
            e -> e.getKey().instance(commonImt),
            e -> e.getValue()));

    /* Reference GMMs at target IMT. */
    this.refsTarget = refs.entrySet()
        .stream()
        .collect(ImmutableMap.toImmutableMap(
            e -> e.getKey().instance(targetImt),
            e -> e.getValue()));
  }

  @Override
  public ScalarGroundMotion calc(GmmInput in) {

    /* Reference ground motion at common IMT. */
    double μRefCommon = 0.0;
    double σRefCommon = 0.0;
    for (Entry<GroundMotionModel, Double> e : refsCommon.entrySet()) {
      ScalarGroundMotion sgm = e.getKey().calc(in);
      double weight = e.getValue();
      μRefCommon += sgm.mean() * weight;
      σRefCommon += sgm.sigma() * weight;
    }

    /* Reference ground motion at target IMT. */
    double μRefTarget = 0.0;
    double σRefTarget = 0.0;
    for (Entry<GroundMotionModel, Double> e : refsTarget.entrySet()) {
      ScalarGroundMotion sgm = e.getKey().calc(in);
      double weight = e.getValue();
      μRefTarget += sgm.mean() * weight;
      σRefTarget += sgm.sigma() * weight;
    }

    /* Ground motion of GMM of interest at common IMT. */
    ScalarGroundMotion sgm = gmm.calc(in);
    double μScale = sgm.mean() / μRefCommon;
    double σScale = sgm.sigma() / σRefCommon;

    return DefaultScalarGroundMotion.create(
        μRefTarget * μScale,
        σRefTarget * σScale);
  }
}
