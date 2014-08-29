package org.opensha.calc;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.opensha.data.ArrayXY_Sequence;
import org.opensha.eq.model.Source;
import org.opensha.eq.model.SourceSet;
import org.opensha.eq.model.SourceType;
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
final class HazardCurveSet {

	final SourceSet<? extends Source> sourceSet;
	final List<HazardGroundMotions> hazardGroundMotionsList;
	final List<ClusterGroundMotions> clusterGroundMotionsList;
	final Map<Gmm, ArrayXY_Sequence> gmmCurveMap;

	private HazardCurveSet(SourceSet<? extends Source> sourceSet,
		List<HazardGroundMotions> hazardGroundMotionsList,
		List<ClusterGroundMotions> clusterGroundMotionsList, Map<Gmm, ArrayXY_Sequence> gmmCurveMap) {

		this.sourceSet = sourceSet;
		this.hazardGroundMotionsList = hazardGroundMotionsList;
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
		private final List<HazardGroundMotions> hazardGroundMotionsList;
		private final List<ClusterGroundMotions> clusterGroundMotionsList;
		private final Map<Gmm, ArrayXY_Sequence> gmmCurveMap;

		private Builder(SourceSet<? extends Source> sourceSet, ArrayXY_Sequence model) {
			this.sourceSet = sourceSet;
			if (sourceSet.type() == SourceType.CLUSTER) {
				clusterGroundMotionsList = new ArrayList<>();
				hazardGroundMotionsList = null;
			} else {
				hazardGroundMotionsList = new ArrayList<>();
				clusterGroundMotionsList = null;
			}
			gmmCurveMap = new EnumMap<>(Gmm.class);
			for (Gmm gmm : sourceSet.groundMotionModels().gmms()) {
				gmmCurveMap.put(gmm, ArrayXY_Sequence.copyOf(model).clear());
			}
		}

		Builder addCurves(HazardCurves hazardCurves) {
			checkNotNull(hazardGroundMotionsList, "%s was intialized with a ClusterSourceSet", ID);
			hazardGroundMotionsList.add(hazardCurves.groundMotions);
			for (Entry<Gmm, ArrayXY_Sequence> entry : hazardCurves.curveMap.entrySet()) {
				gmmCurveMap.get(entry.getKey()).add(entry.getValue());
			}
			return this;
		}

		Builder addCurves(ClusterCurves clusterCurves) {
			checkNotNull(clusterGroundMotionsList, "%s was not intialized with a ClusterSourceSet",
				ID);
			clusterGroundMotionsList.add(clusterCurves.clusterGroundMotions);
			for (Entry<Gmm, ArrayXY_Sequence> entry : clusterCurves.curveMap.entrySet()) {
				gmmCurveMap.get(entry.getKey()).add(entry.getValue());
			}
			return this;
		}

		HazardCurveSet build() {
			checkState(!built, "This %s instance has already been used", ID);
			built = true;
			return new HazardCurveSet(sourceSet, hazardGroundMotionsList, clusterGroundMotionsList,
				gmmCurveMap);
		}

	}

}
