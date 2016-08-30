package org.opensha2.calc;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.StandardSystemProperty.LINE_SEPARATOR;

import org.opensha2.gmm.Gmm;
import org.opensha2.gmm.Imt;
import org.opensha2.gmm.ScalarGroundMotion;

import com.google.common.collect.Maps;
import com.google.common.primitives.Doubles;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Container class for scalar ground motions associated with individual
 * {@code Source}s in a {@code SourceSet}.
 *
 * @author Peter Powers
 */
final class GroundMotions {

  /*
   * NOTE the inputList supplied to Builder is immutable but the mean and sigma
   * lists it builds are not; builder backs mean and sigma lists with double[].
   * Nor are the mean and sigma maps immutable.
   *
   * TODO It would be nice to have an immutable variant of a double[] backed
   * list, but would require copying values on build().
   */

  final InputList inputs;
  final Map<Imt, Map<Gmm, List<Double>>> μLists;
  final Map<Imt, Map<Gmm, List<Double>>> σLists;

  private GroundMotions(
      InputList inputs,
      Map<Imt, Map<Gmm, List<Double>>> μLists,
      Map<Imt, Map<Gmm, List<Double>>> σLists) {
    this.inputs = inputs;
    this.μLists = μLists;
    this.σLists = σLists;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder(getClass().getSimpleName());
    sb.append(" [").append(inputs.parentName()).append("]");
    sb.append(": ").append(LINE_SEPARATOR.value());
    for (int i = 0; i < inputs.size(); i++) {
      sb.append(inputs.get(i));
      sb.append(" ");
      for (Entry<Imt, Map<Gmm, List<Double>>> imtEntry : μLists.entrySet()) {
        Imt imt = imtEntry.getKey();
        sb.append(imt.name()).append(" [");
        for (Entry<Gmm, List<Double>> gmmEntry : imtEntry.getValue().entrySet()) {
          Gmm gmm = gmmEntry.getKey();
          sb.append(gmm.name()).append(" ");
          sb.append(String.format("μ=%.3f", gmmEntry.getValue().get(i))).append(" ");
          sb.append(String.format("σ=%.3f", σLists.get(imt).get(gmm).get(i))).append(" ");
        }
        sb.append("] ");
      }
      sb.append(LINE_SEPARATOR.value());
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
    Map<Imt, Map<Gmm, List<Double>>> keyModel = groundMotions.get(0).μLists;
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
    private final Map<Imt, Map<Gmm, List<Double>>> means;
    private final Map<Imt, Map<Gmm, List<Double>>> sigmas;

    private Builder(InputList inputs, Set<Imt> imts, Set<Gmm> gmms) {
      checkArgument(inputs.size() > 0);
      checkArgument(gmms.size() > 0);
      this.inputs = inputs;
      means = initValueMaps(imts, gmms, inputs.size());
      sigmas = initValueMaps(imts, gmms, inputs.size());
      size = imts.size() * gmms.size() * inputs.size();
    }

    // TODO refactor to set()
    Builder add(Imt imt, Gmm gmm, ScalarGroundMotion sgm, int index) {
      checkState(addCount < size, "This %s instance is already full", ID);
      means.get(imt).get(gmm).set(index, sgm.mean());
      sigmas.get(imt).get(gmm).set(index, sgm.sigma());
      addCount++;
      return this;
    }

    GroundMotions build() {
      checkState(!built, "This %s instance has already been used", ID);
      checkState(addCount == size, "Only %s of %s entries have been added", addCount, size);
      built = true;
      return new GroundMotions(inputs, means, sigmas);
    }

    static Map<Imt, Map<Gmm, List<Double>>> initValueMaps(
        Set<Imt> imts,
        Set<Gmm> gmms,
        int size) {

      Map<Imt, Map<Gmm, List<Double>>> imtMap = Maps.newEnumMap(Imt.class);
      for (Imt imt : imts) {
        Map<Gmm, List<Double>> gmmMap = Maps.newEnumMap(Gmm.class);
        for (Gmm gmm : gmms) {
          gmmMap.put(gmm, Doubles.asList(new double[size]));
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
      int startIndex = 0;
      for (GroundMotions gm : groundMotions) {
        addCount += setValues(means, gm.μLists, sigmas, gm.σLists, startIndex);
        startIndex += gm.inputs.size();
      }
      return this;
    }

    private static int setValues(
        Map<Imt, Map<Gmm, List<Double>>> μTargetMap,
        Map<Imt, Map<Gmm, List<Double>>> μValueMap,
        Map<Imt, Map<Gmm, List<Double>>> σTargetMap,
        Map<Imt, Map<Gmm, List<Double>>> σValueMap,
        int startIndex) {

      int setCount = 0;
      for (Entry<Imt, Map<Gmm, List<Double>>> imtEntry : μTargetMap.entrySet()) {
        Imt imt = imtEntry.getKey();
        for (Entry<Gmm, List<Double>> gmmEntry : imtEntry.getValue().entrySet()) {
          Gmm gmm = gmmEntry.getKey();

          List<Double> μTarget = gmmEntry.getValue();
          List<Double> μValues = μValueMap.get(imt).get(gmm);
          List<Double> σTarget = σTargetMap.get(imt).get(gmm);
          List<Double> σValues = σValueMap.get(imt).get(gmm);

          setCount += μValues.size();

          for (int i = 0; i < μValues.size(); i++) {
            int targetIndex = startIndex + i;
            μTarget.set(targetIndex, μValues.get(i));
            σTarget.set(targetIndex, σValues.get(i));
          }
        }
      }
      return setCount;
    }

  }
}
