package org.opensha2.calc;

import static com.google.common.base.Preconditions.checkState;

import static org.opensha2.data.XySequence.copyOf;
import static org.opensha2.data.XySequence.immutableCopyOf;

import org.opensha2.data.XySequence;
import org.opensha2.gmm.Gmm;
import org.opensha2.gmm.Imt;

import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Iterables;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Container class for the combined hazard curves derived from the
 * {@code Rupture}s in an individual {@code Source}, one for each
 * {@code GroundMotionModel} and {@code Imt} of interest. The curves will have
 * been scaled by the associated Mfd or rupture weights, but not by
 * {@code GroundMotionModel} weights.
 *
 * @author Peter Powers
 */
final class HazardCurves {

  final GroundMotions groundMotions;
  final Map<Imt, Map<Gmm, XySequence>> curveMap;

  private HazardCurves(
      GroundMotions groundMotions,
      Map<Imt, Map<Gmm, XySequence>> curveMap) {
    this.groundMotions = groundMotions;
    this.curveMap = curveMap;
  }

  static Builder builder(GroundMotions groundMotions) {
    return new Builder(groundMotions);
  }

  /*
   * Specialized constructor that creates a single HazardCurves from the results
   * of processing a partitioned InputList.
   */
  static HazardCurves combine(InputList inputs, List<HazardCurves> curvesList) {
    List<GroundMotions> groundMotionsList = FluentIterable.from(curvesList)
        .transform(new Function<HazardCurves, GroundMotions>() {
          @Override
          public GroundMotions apply(HazardCurves curves) {
            return curves.groundMotions;
          }
        })
        .toList();
    GroundMotions groundMotions = GroundMotions.combine(inputs, groundMotionsList);
    return builder(groundMotions)
        .combine(curvesList)
        .build();
  }

  static class Builder {

    private static final String ID = "HazardCurves.Builder";
    private boolean built = false;

    private final GroundMotions groundMotions;
    private final Map<Imt, Map<Gmm, XySequence>> curveMap;

    private Builder(GroundMotions groundMotions) {
      this.groundMotions = groundMotions;
      curveMap = new EnumMap<>(Imt.class);
      for (Imt imt : groundMotions.μLists.keySet()) {
        Map<Gmm, XySequence> gmmMap = new EnumMap<>(Gmm.class);
        curveMap.put(imt, gmmMap);
      }
    }

    /* Put an immutable copy of the supplied curve. */
    Builder addCurve(Imt imt, Gmm gmm, XySequence curve) {
      // TODO refactor to set or put
      curveMap.get(imt).put(gmm, immutableCopyOf(curve));
      return this;
    }

    HazardCurves build() {
      checkState(!built, "This %s instance has already been used", ID);
      // TODO check that all gmms have been set? it'll be difficult to
      // track whether all curves for all inputs have been added
      built = true;
      return new HazardCurves(groundMotions, curveMap);
    }

    /*
     * For internal use only. Combines multiple ordered HazardCurves that result
     * from InputList partitioning. Can only be called once after intializing
     * the builder with a combined GroundMotions object previously created from
     * the supplied HazardCurves.
     */
    private Builder combine(List<HazardCurves> curvesList) {
      Map<Imt, Map<Gmm, XySequence>> model = curvesList.get(0).curveMap;
      for (Entry<Imt, Map<Gmm, XySequence>> imtEntry : model.entrySet()) {
        Imt imt = imtEntry.getKey();
        for (Entry<Gmm, XySequence> gmmEntry : imtEntry.getValue().entrySet()) {
          Gmm gmm = gmmEntry.getKey();
          XySequence sequence = copyOf(gmmEntry.getValue());
          for (HazardCurves curves : Iterables.skip(curvesList, 1)) {
            sequence.add(curves.curveMap.get(imt).get(gmm));
          }
          addCurve(imt, gmm, sequence);
        }
      }
      return this;
    }
  }

}
