package org.opensha.calc;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.opensha.data.ArrayXY_Sequence.copyOf;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.opensha.data.ArrayXY_Sequence;
import org.opensha.eq.model.Source;
import org.opensha.eq.model.SourceSet;
import org.opensha.eq.model.SourceType;
import org.opensha.gmm.Gmm;
import org.opensha.gmm.Imt;

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
	final Map<Imt, Map<Gmm, ArrayXY_Sequence>> curveMap;
	final Map<Imt, ArrayXY_Sequence> totalCurves;

	private HazardCurveSet(SourceSet<? extends Source> sourceSet,
		List<HazardGroundMotions> hazardGroundMotionsList,
		List<ClusterGroundMotions> clusterGroundMotionsList,
		Map<Imt, Map<Gmm, ArrayXY_Sequence>> curveMap, Map<Imt, ArrayXY_Sequence> totalCurves) {

		this.sourceSet = sourceSet;
		this.hazardGroundMotionsList = hazardGroundMotionsList;
		this.clusterGroundMotionsList = clusterGroundMotionsList;
		this.curveMap = curveMap;
		this.totalCurves = totalCurves;
	}

	static Builder builder(SourceSet<? extends Source> sourceSet,
			Map<Imt, ArrayXY_Sequence> modelCurves) {
		return new Builder(sourceSet, modelCurves);
	}

	static class Builder {

		private static final String ID = "HazardCurveSet.Builder";
		private boolean built = false;

		private final Map<Imt, ArrayXY_Sequence> modelCurves;

		private final SourceSet<? extends Source> sourceSet;
		private final List<HazardGroundMotions> hazardGroundMotionsList;
		private final List<ClusterGroundMotions> clusterGroundMotionsList;
		private final Map<Imt, Map<Gmm, ArrayXY_Sequence>> curveMap;
		private final Map<Imt, ArrayXY_Sequence> totalCurves;

		private Builder(SourceSet<? extends Source> sourceSet,
			Map<Imt, ArrayXY_Sequence> modelCurves) {

			this.sourceSet = sourceSet;
			this.modelCurves = modelCurves;
			if (sourceSet.type() == SourceType.CLUSTER) {
				clusterGroundMotionsList = new ArrayList<>();
				hazardGroundMotionsList = null;
			} else {
				hazardGroundMotionsList = new ArrayList<>();
				clusterGroundMotionsList = null;
			}
			Set<Gmm> gmms = sourceSet.groundMotionModels().gmms();
			Set<Imt> imts = modelCurves.keySet();
			curveMap = new EnumMap<>(Imt.class);
			for (Imt imt : imts) {
				Map<Gmm, ArrayXY_Sequence> gmmMap = new EnumMap<>(Gmm.class);
				curveMap.put(imt, gmmMap);
				for (Gmm gmm : gmms) {
					ArrayXY_Sequence emptyCurve = copyOf(modelCurves.get(imt)).clear();
					gmmMap.put(gmm, emptyCurve);
				}
			}
			totalCurves = new EnumMap<>(Imt.class);
		}

		Builder addCurves(HazardCurves hazardCurves) {
			checkNotNull(hazardGroundMotionsList, "%s was intialized with a ClusterSourceSet", ID);
			hazardGroundMotionsList.add(hazardCurves.groundMotions);
			double distance = hazardCurves.groundMotions.inputs.minDistance;
			Map<Gmm, Double> gmmWeightMap = sourceSet.groundMotionModels().gmmWeightMap(distance);
			for (Entry<Imt, Map<Gmm, ArrayXY_Sequence>> imtEntry : hazardCurves.curveMap.entrySet()) {
				Map<Gmm, ArrayXY_Sequence> builderCurveMap = curveMap.get(imtEntry.getKey());
				for (Entry<Gmm, ArrayXY_Sequence> gmmEntry : imtEntry.getValue().entrySet()) {
					Gmm gmm = gmmEntry.getKey();
					double gmmWeight = gmmWeightMap.get(gmm);
					builderCurveMap.get(gmm).add(copyOf(gmmEntry.getValue()).multiply(gmmWeight));
				}
			}
			return this;
		}

		Builder addCurves(ClusterCurves clusterCurves) {
			checkNotNull(clusterGroundMotionsList, "%s was not intialized with a ClusterSourceSet",
				ID);
			clusterGroundMotionsList.add(clusterCurves.clusterGroundMotions);
			double clusterWeight = clusterCurves.clusterGroundMotions.parent.weight();
			double distance = clusterCurves.clusterGroundMotions.minDistance;
			Map<Gmm, Double> gmmWeightMap = sourceSet.groundMotionModels().gmmWeightMap(distance);
			for (Entry<Imt, Map<Gmm, ArrayXY_Sequence>> imtEntry : clusterCurves.curveMap
				.entrySet()) {
				Map<Gmm, ArrayXY_Sequence> builderCurveMap = curveMap.get(imtEntry.getKey());
				for (Entry<Gmm, ArrayXY_Sequence> gmmEntry : imtEntry.getValue().entrySet()) {
					Gmm gmm = gmmEntry.getKey();
					double totalWeight = gmmWeightMap.get(gmm) * clusterWeight;
					builderCurveMap.get(gmm).add(copyOf(gmmEntry.getValue()).multiply(totalWeight));
				}
			}
			return this;
		}

		HazardCurveSet build() {
			checkState(!built, "This %s instance has already been used", ID);
			built = true;
			computeFinal();
			return new HazardCurveSet(sourceSet, hazardGroundMotionsList, clusterGroundMotionsList,
				curveMap, totalCurves);
		}

		/*
		 * Create the final wieghted (Gmm) combined curve. The Gmm curves were
		 * scaled by their weights while building (above).
		 */
		private void computeFinal() {
			double sourceSetWeight = sourceSet.weight();
			for (Entry<Imt, Map<Gmm, ArrayXY_Sequence>> entry : curveMap.entrySet()) {
				Imt imt = entry.getKey();
				ArrayXY_Sequence totalCurve = copyOf(modelCurves.get(imt)).clear();
				for (ArrayXY_Sequence curve : entry.getValue().values()) {
					totalCurve.add(curve);
				}
				totalCurve.multiply(sourceSetWeight);
				totalCurves.put(imt, totalCurve);
			}
		}
	}

}
