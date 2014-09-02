package org.opensha.calc;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.opensha.data.ArrayXY_Sequence.copyOf;

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
 * {@code GroundMotionModel} used.
 * 
 * <p>The {@code Builder} for this class is used to aggregate the HazardCurves
 * associated with each {@code Source} in a {@code SourceSet}, scaled by the
 * {@code SourceSet} weight. It also scales curves by their associated
 * {@code GroundMotionModel} weight, using distance-dependent weights when
 * appropriate. Note that this may lead to the dropping of some curves that are
 * not appropriate at large distances.</p>
 * 
 * <p>A HazardCurveSet is compatible with all types of {@code SourceSet}s,
 * including {@code ClusterSourceSet}s, which are handled differently in hazard
 * calculations. This container marks a point in the calculation pipeline where
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
	final ArrayXY_Sequence totalCurve;

	private HazardCurveSet(SourceSet<? extends Source> sourceSet,
		List<HazardGroundMotions> hazardGroundMotionsList,
		List<ClusterGroundMotions> clusterGroundMotionsList,
		Map<Gmm, ArrayXY_Sequence> gmmCurveMap, ArrayXY_Sequence totalCurve) {

		this.sourceSet = sourceSet;
		this.hazardGroundMotionsList = hazardGroundMotionsList;
		this.clusterGroundMotionsList = clusterGroundMotionsList;
		this.gmmCurveMap = gmmCurveMap;
		this.totalCurve = totalCurve;
	}

	static Builder builder(SourceSet<? extends Source> sourceSet, ArrayXY_Sequence modelCurve) {
		return new Builder(sourceSet, modelCurve);
	}

	static class Builder {

		private static final String ID = "HazardCurveSet.Builder";
		private boolean built = false;

		private final ArrayXY_Sequence modelCurve;

		private final SourceSet<? extends Source> sourceSet;
		private final List<HazardGroundMotions> hazardGroundMotionsList;
		private final List<ClusterGroundMotions> clusterGroundMotionsList;
		private final Map<Gmm, ArrayXY_Sequence> gmmCurveMap;
		private ArrayXY_Sequence totalCurve;

		private Builder(SourceSet<? extends Source> sourceSet, ArrayXY_Sequence modelCurve) {
			this.sourceSet = sourceSet;
			this.modelCurve = modelCurve;
			if (sourceSet.type() == SourceType.CLUSTER) {
				clusterGroundMotionsList = new ArrayList<>();
				hazardGroundMotionsList = null;
			} else {
				hazardGroundMotionsList = new ArrayList<>();
				clusterGroundMotionsList = null;
			}
			gmmCurveMap = new EnumMap<>(Gmm.class);
			for (Gmm gmm : sourceSet.groundMotionModels().gmms()) {
				gmmCurveMap.put(gmm, ArrayXY_Sequence.copyOf(modelCurve).clear());
			}
		}

		// TODO clean
//		Builder addCurves(HazardCurves hazardCurves) {
//			checkNotNull(hazardGroundMotionsList, "%s was intialized with a ClusterSourceSet", ID);
//			hazardGroundMotionsList.add(hazardCurves.groundMotions);
//			for (Entry<Gmm, ArrayXY_Sequence> entry : hazardCurves.curveMap.entrySet()) {
//				gmmCurveMap.get(entry.getKey()).add(entry.getValue());
//			}
//			return this;
//		}

		Builder addCurves(final HazardCurves hazardCurves) {
			checkNotNull(hazardGroundMotionsList, "%s was intialized with a ClusterSourceSet", ID);
			hazardGroundMotionsList.add(hazardCurves.groundMotions);
			double distance = hazardCurves.groundMotions.inputs.minDistance;
			Map<Gmm, Double> gmmWeightMap = sourceSet.groundMotionModels().gmmWeightMap(distance);
			for (Entry<Gmm, Double> entry : gmmWeightMap.entrySet()) {
				// copy so as to not mutate incoming curves
				ArrayXY_Sequence copy = copyOf(hazardCurves.curveMap.get(entry.getKey()));
				copy.multiply(entry.getValue());
				gmmCurveMap.get(entry.getKey()).add(copy);
			}
			return this;
		}

		Builder addCurves(final ClusterCurves clusterCurves) {
			checkNotNull(clusterGroundMotionsList, "%s was not intialized with a ClusterSourceSet",
				ID);
			clusterGroundMotionsList.add(clusterCurves.clusterGroundMotions);
			double weight = clusterCurves.clusterGroundMotions.parent.weight();
			double distance = clusterCurves.clusterGroundMotions.minDistance;
			Map<Gmm, Double> gmmWeightMap = sourceSet.groundMotionModels().gmmWeightMap(distance);
			for (Entry<Gmm, Double> entry : gmmWeightMap.entrySet()) {
				// copy so as to not mutate incoming curves
				ArrayXY_Sequence copy = copyOf(clusterCurves.curveMap.get(entry.getKey()));
				// scale by cluster and gmm wieght
				copy.multiply(weight).multiply(entry.getValue());
				gmmCurveMap.get(entry.getKey()).add(copy);
			}
			return this;
		}

		HazardCurveSet build() {
			checkState(!built, "This %s instance has already been used", ID);
			built = true;
			computeFinal();
			return new HazardCurveSet(sourceSet, hazardGroundMotionsList, clusterGroundMotionsList,
				gmmCurveMap, totalCurve);
		}

		/*
		 * Create the final wieghted (Gmm) combined curve. The Gmm curves were
		 * scaled by their weights in an earlier step.
		 */
		private void computeFinal() {
			ArrayXY_Sequence totalCurve = ArrayXY_Sequence.copyOf(modelCurve).clear();
			double sourceSetWeight = sourceSet.weight();
			for (ArrayXY_Sequence curve : gmmCurveMap.values()) {
				totalCurve.add(copyOf(curve).multiply(sourceSetWeight));
			}
			this.totalCurve = totalCurve;
		}
	}

}
