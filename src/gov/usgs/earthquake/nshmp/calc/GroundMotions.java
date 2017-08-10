package gov.usgs.earthquake.nshmp.calc;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static gov.usgs.earthquake.nshmp.internal.TextUtils.NEWLINE;

import com.google.common.collect.Maps;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import gov.usgs.earthquake.nshmp.gmm.Gmm;
import gov.usgs.earthquake.nshmp.gmm.Imt;
import gov.usgs.earthquake.nshmp.gmm.ScalarGroundMotion;

import java.util.Set;

/**
 * Container class for scalar ground motions associated with individual
 * {@code Source}s in a {@code SourceSet}.
 *
 * @author Peter Powers
 */
final class GroundMotions {

  /*
   * NOTE the inputList supplied to Builder is immutable but the
   * ScalarGroundMotion map it builds is not.
   */

  final InputList inputs;
  final Map<Imt, Map<Gmm, List<ScalarGroundMotion>>> gmMap;

  private GroundMotions(
      InputList inputs,
      Map<Imt, Map<Gmm, List<ScalarGroundMotion>>> gmMap) {

    this.inputs = inputs;
    this.gmMap = gmMap;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder(getClass().getSimpleName());
    sb.append(" [").append(inputs.parentName()).append("]");
    sb.append(":").append(NEWLINE);
    for (Entry<Imt, Map<Gmm, List<ScalarGroundMotion>>> imtEntry : gmMap.entrySet()) {
      sb.append(" ");
      Imt imt = imtEntry.getKey();
      for (Entry<Gmm, List<ScalarGroundMotion>> gmmEntry : imtEntry.getValue().entrySet()) {
        sb.append(imt.name()).append(" [ ");
        Gmm gmm = gmmEntry.getKey();
        sb.append(gmm.name()).append(" ");
        for (ScalarGroundMotion sgm : gmmEntry.getValue()) {
          sb.append(String.format("μ=%.3f", sgm.mean())).append(" ");
          sb.append(String.format("σ=%.3f", sgm.sigma())).append(" ");
        }
        sb.append("] ");
      }
      sb.append(NEWLINE);
    }
    return sb.toString();
  }

  static Builder builder(InputList inputs, Set<Imt> imts, Set<Gmm> gmms) {
    return new Builder(inputs, imts, gmms);
  }

  /*
   * Combine GroundMotions resulting from InputList partitioning. The original
   * InputList is required as a convenience (the supplied GroundMotions contain
   * sublists of this list). The original list is also used as a size check
   * against the combined result.
   */
  static GroundMotions combine(InputList inputs, List<GroundMotions> groundMotions) {
    Map<Imt, Map<Gmm, List<ScalarGroundMotion>>> keyModel = groundMotions.get(0).gmMap;
    Set<Imt> imtKeys = keyModel.keySet();
    Set<Gmm> gmmKeys = keyModel.get(imtKeys.iterator().next()).keySet();
    return builder(inputs, imtKeys, gmmKeys)
        .combine(groundMotions)
        .build();
  }

  static class Builder {

    private static final String ID = "GroundMotions.Builder";
    private boolean built = false;
    private final int size;
    private int addCount = 0;

    private final InputList inputs;
    private final Map<Imt, Map<Gmm, List<ScalarGroundMotion>>> gmMap;

    private Builder(InputList inputs, Set<Imt> imts, Set<Gmm> gmms) {
      checkArgument(inputs.size() > 0);
      checkArgument(gmms.size() > 0);
      this.inputs = inputs;
      gmMap = initGmMap(imts, gmms, inputs.size());
      size = imts.size() * gmms.size() * inputs.size();
    }

    Builder add(Imt imt, Gmm gmm, ScalarGroundMotion sgm) {
      checkState(addCount < size, "This %s instance is already full", ID);
      gmMap.get(imt).get(gmm).add(sgm);
      addCount++;
      return this;
    }

    GroundMotions build() {
      checkState(!built, "This %s instance has already been used", ID);
      checkState(addCount == size, "Only %s of %s entries have been added", addCount, size);
      built = true;
      GroundMotions gms = new GroundMotions(inputs, gmMap);
      return gms;
    }

    static Map<Imt, Map<Gmm, List<ScalarGroundMotion>>> initGmMap(
        Set<Imt> imts,
        Set<Gmm> gmms,
        int size) {

      Map<Imt, Map<Gmm, List<ScalarGroundMotion>>> imtMap = Maps.newEnumMap(Imt.class);
      for (Imt imt : imts) {
        Map<Gmm, List<ScalarGroundMotion>> gmmMap = Maps.newEnumMap(Gmm.class);
        for (Gmm gmm : gmms) {
          gmmMap.put(gmm, new ArrayList<ScalarGroundMotion>(size));
        }
        imtMap.put(imt, gmmMap);
      }
      return imtMap;
    }

    /*
     * For internal use only. Combines multiple ordered GroundMotions that
     * result from InputList partitioning. Can only be called once after
     * intializing the builder with the original master InputList.
     */
    private Builder combine(List<GroundMotions> groundMotions) {
      for (GroundMotions gms : groundMotions) {
        addCount += addValues(gmMap, gms.gmMap);
      }
      return this;
    }

    private static int addValues(
        Map<Imt, Map<Gmm, List<ScalarGroundMotion>>> sgmTargetMap,
        Map<Imt, Map<Gmm, List<ScalarGroundMotion>>> sgmValueMap) {

      int setCount = 0;
      for (Entry<Imt, Map<Gmm, List<ScalarGroundMotion>>> imtEntry : sgmTargetMap.entrySet()) {
        Imt imt = imtEntry.getKey();
        for (Entry<Gmm, List<ScalarGroundMotion>> gmmEntry : imtEntry.getValue().entrySet()) {
          Gmm gmm = gmmEntry.getKey();
          List<ScalarGroundMotion> sgmTarget = gmmEntry.getValue();
          List<ScalarGroundMotion> sgmValues = sgmValueMap.get(imt).get(gmm);
          sgmTarget.addAll(sgmValues);
          setCount += sgmValues.size();
        }
      }
      return setCount;
    }
  }

}
