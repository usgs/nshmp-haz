package org.opensha.calc;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.opensha.data.ArrayXY_Sequence;
import org.opensha.eq.forecast.Source;
import org.opensha.eq.forecast.SourceSet;
import org.opensha.eq.forecast.SourceType;
import org.opensha.gmm.Gmm;

/**
 * Container class for hazard curves derived from a {@code SourceSet}. Class
 * stores the {@code HazardGroundMotions}s associated with each {@code Source}
 * used in a hazard calculation and the combined curves for each
 * {@code GroundMotionModel} used. The {@code Builder} for this class is used to
 * aggregate the HazardCurves associated with each {@code Source} in a
 * {@code SourceSet}.
 * 
 * <p>A HazardCurveSet is compatible with all types of {@code SourceSet}s,
 * including {@code ClusterSourceSet}s, which are handled differently in hazard
 * calcualtions. This container marks a point in the calculation pipeline where
 * results from cluster and other sources may be recombined into a single
 * {@code HazardResult}, regardless of {@code SourceSet.type()} for all relevant
 * {@code SourceSet}s.</p>
 * 
 * @author Peter Powers
 */
public class HazardCurveSet {

	final SourceSet<? extends Source> sourceSet;
	final List<HazardGroundMotions> groundMotionsList;
	final List<List<HazardGroundMotions>> clusterGroundMotionsList;
	final Map<Gmm, ArrayXY_Sequence> gmmCurveMap;

	private HazardCurveSet(SourceSet<? extends Source> sourceSet,
		List<HazardGroundMotions> groundMotionsList,
		List<List<HazardGroundMotions>> clusterGroundMotionsList,
		Map<Gmm, ArrayXY_Sequence> gmmCurveMap) {

		this.sourceSet = sourceSet;
		this.groundMotionsList = groundMotionsList;
		this.clusterGroundMotionsList = clusterGroundMotionsList;
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
		private final List<List<HazardGroundMotions>> clusterGroundMotionsList;
		private final Map<Gmm, ArrayXY_Sequence> gmmCurveMap;

		private Builder(SourceSet<? extends Source> sourceSet, ArrayXY_Sequence model) {
			this.sourceSet = sourceSet;
			if (sourceSet.type() == SourceType.CLUSTER) {
				groundMotionsList = null;
				clusterGroundMotionsList = new ArrayList<>();
			} else {
				groundMotionsList = new ArrayList<>();
				clusterGroundMotionsList = null;
			}
			gmmCurveMap = new EnumMap<>(Gmm.class);
			for (Gmm gmm : sourceSet.groundMotionModels().gmms()) {
				gmmCurveMap.put(gmm, ArrayXY_Sequence.copyOf(model).clear());
			}
		}

		Builder addCurves(HazardCurves hazardCurves) {
			checkNotNull(groundMotionsList, "%s was intialized with a ClusterSourceSet", ID);
			groundMotionsList.add(hazardCurves.groundMotions);
			for (Entry<Gmm, ArrayXY_Sequence> entry : hazardCurves.curveMap.entrySet()) {
				gmmCurveMap.get(entry.getKey()).add(entry.getValue());
			}
			return this;
		}

		Builder addCurves(ClusterHazardCurves clusterHazardCurves) {
			checkNotNull(clusterGroundMotionsList, "%s was not intialized with a ClusterSourceSet",
				ID);
			clusterGroundMotionsList.add(clusterHazardCurves.groundMotionsList);
			for (Entry<Gmm, ArrayXY_Sequence> entry : clusterHazardCurves.curveMap.entrySet()) {
				gmmCurveMap.get(entry.getKey()).add(entry.getValue());
			}
			return this;
		}

		HazardCurveSet build() {
			checkState(!built, "This %s instance has already been used", ID);
			built = true;
			return new HazardCurveSet(sourceSet, groundMotionsList, clusterGroundMotionsList,
				gmmCurveMap);
		}

	}

}
