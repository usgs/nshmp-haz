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
import org.opensha.eq.forecast.Source;
import org.opensha.eq.forecast.SourceSet;
import org.opensha.gmm.Gmm;

/**
 * Container class for hazard curves derived from a {@code SourceSet}. Class
 * stores the {@code HazardGroundMotions}s associated with each {@code Source}
 * used in a hazard calculation and the combined curves for each
 * {@code GroundMotionModel} used. The {@code Builder} for this class is used to
 * aggregate the HazardCurves associated with each {@code Source} in a
 * {@code SourceSet}.
 * 
 * @author Peter Powers
 */
public class HazardCurveSet {

	final SourceSet<? extends Source> sourceSet;
	final List<HazardGroundMotions> groundMotionsList;
	final Map<Gmm, ArrayXY_Sequence> gmmCurveMap;

	private HazardCurveSet(SourceSet<? extends Source> sourceSet,
		List<HazardGroundMotions> groundMotionsList, Map<Gmm, ArrayXY_Sequence> gmmCurveMap) {
		this.sourceSet = sourceSet;
		this.groundMotionsList = groundMotionsList;
		this.gmmCurveMap = gmmCurveMap;
	}

	static Builder builder(SourceSet<? extends Source> sourceSet, ArrayXY_Sequence modelCurve) {
		return new Builder(sourceSet, modelCurve);
	}

	static class Builder {

		private static final String ID = "HazardCurveSet.Builder";
		private boolean built = false;

		private final SourceSet<? extends Source> sourceSet;
		private final List<HazardGroundMotions> groundMotionsList;
		private final Map<Gmm, ArrayXY_Sequence> gmmCurveMap;

		private Builder(SourceSet<? extends Source> sourceSet, ArrayXY_Sequence model) {
			this.sourceSet = sourceSet;
			groundMotionsList = new ArrayList<>();
			gmmCurveMap = new EnumMap<>(Gmm.class);
			for (Gmm gmm : sourceSet.groundMotionModels().gmms()) {
				gmmCurveMap.put(gmm, ArrayXY_Sequence.copyOf(model).clear());
			}
		}

		Builder addCurves(HazardCurves hazardCurves) {
			groundMotionsList.add(hazardCurves.groundMotions);
			for (Entry<Gmm, ArrayXY_Sequence> entry : hazardCurves.curveMap.entrySet()) {
				gmmCurveMap.get(entry.getKey()).add(entry.getValue());
			}
			return this;
		}

		HazardCurveSet build() {
			checkState(!built, "This %s instance has already been used", ID);
			return new HazardCurveSet(sourceSet, groundMotionsList, gmmCurveMap);
		}

	}

}
