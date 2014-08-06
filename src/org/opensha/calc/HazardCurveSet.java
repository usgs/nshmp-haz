package org.opensha.calc;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.opensha.data.ArrayXY_Sequence;
import org.opensha.gmm.Gmm;

/**
 * Container class for hazard curves derived from a {@code SourceSet}. Class
 * stores the {@code GroundMotionSet}s associated with each {@code Source} and
 * the compiled curves for each {@code GroundMotionModel} that was used.
 * 
 * @author Peter Powers
 */
public class HazardCurveSet {

	final List<GroundMotionSet> groundMotions;
	final Map<Gmm, ArrayXY_Sequence> curves;
	
	private HazardCurveSet(List<GroundMotionSet> groundMotions, Map<Gmm, ArrayXY_Sequence> curves) {
		this.groundMotions = groundMotions;
		this.curves = curves;
	}

	static Builder builder(ArrayXY_Sequence model, Set<Gmm> gmms) {
		return new Builder(model, gmms);
	}

	static class Builder {

		private static final String ID = "HazardCurveSet.Builder";
		private boolean built = false;

		private final List<GroundMotionSet> groundMotions;
		private final Map<Gmm, ArrayXY_Sequence> curves;

		private Builder(ArrayXY_Sequence model, Set<Gmm> gmms) {
			groundMotions = new ArrayList<>();
			curves = new EnumMap<>(Gmm.class);
			for (Gmm gmm : gmms) {
				curves.put(gmm, ArrayXY_Sequence.copyOf(model).clear());
			}
		}

		Builder add(GroundMotionSet gms, Map<Gmm, ArrayXY_Sequence> curveMap) {
			groundMotions.add(checkNotNull(gms));
			for (Entry<Gmm, ArrayXY_Sequence> entry : curveMap.entrySet()) {
				curves.get(entry.getKey()).add(entry.getValue());
			}
			return this;
		}

		HazardCurveSet build() {
			checkState(!built, "This %s instance has already been used", ID);
			return new HazardCurveSet(groundMotions, curves);
		}

	}

}
