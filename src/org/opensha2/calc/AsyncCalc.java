package org.opensha2.calc;

import static com.google.common.util.concurrent.Futures.allAsList;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static com.google.common.util.concurrent.Futures.transform;
import static org.opensha2.calc.AsyncList.createWithCapacity;
import static org.opensha2.calc.Transforms.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import org.opensha2.data.ArrayXY_Sequence;
import org.opensha2.eq.model.ClusterSource;
import org.opensha2.eq.model.ClusterSourceSet;
import org.opensha2.eq.model.Source;
import org.opensha2.eq.model.SourceSet;
import org.opensha2.gmm.Gmm;
import org.opensha2.gmm.GroundMotionModel;
import org.opensha2.gmm.Imt;

import com.google.common.base.Function;
import com.google.common.collect.Table;
import com.google.common.util.concurrent.ListenableFuture;

/**
 * Static utility methods for asynchronously performing the various steps in a
 * hazard calculation.
 * 
 * @author Peter Powers
 * @see Transforms
 * @see AsyncList
 */
final class AsyncCalc {

	private AsyncCalc() {}

	/**
	 * Convert a SourceSet to a List of future HazardInputs.
	 */
	static final AsyncList<HazardInputs> toInputs(
			final SourceSet<? extends Source> sourceSet,
			final Site site,
			final ExecutorService ex) {

		Function<Source, HazardInputs> function = new SourceToInputs(site);
		AsyncList<HazardInputs> result = AsyncList.create();
		for (Source source : sourceSet.iterableForLocation(site.location)) {
			result.add(transform(immediateFuture(source), function, ex));
		}
		return result;
	}

	/**
	 * Convert a List of future HazardInputs to a List of future
	 * HazardGroundMotions.
	 */
	static final AsyncList<HazardGroundMotions> toGroundMotions(
			final AsyncList<HazardInputs> inputsList,
			final SourceSet<? extends Source> sourceSet,
			final Set<Imt> imts,
			final ExecutorService ex) {

		Set<Gmm> gmms = sourceSet.groundMotionModels().gmms();
		Table<Gmm, Imt, GroundMotionModel> gmmInstances = Gmm.instances(gmms, imts);
		Function<HazardInputs, HazardGroundMotions> function = new InputsToGroundMotions(
			gmmInstances);
		AsyncList<HazardGroundMotions> result = createWithCapacity(inputsList.size());
		for (ListenableFuture<HazardInputs> hazardInputs : inputsList) {
			result.add(transform(hazardInputs, function, ex));
		}
		return result;
	}

	/**
	 * Convert a List of future HazardGroundMotions to a List of future
	 * HazardCurves.
	 */
	static final AsyncList<HazardCurves> toHazardCurves(
			final AsyncList<HazardGroundMotions> groundMotionsList,
			final Map<Imt, ArrayXY_Sequence> modelCurves,
			final ExceedanceModel sigmaModel,
			final double truncLevel,
			final ExecutorService ex) {

		Function<HazardGroundMotions, HazardCurves> function = new GroundMotionsToCurves(
			modelCurves,
			sigmaModel, truncLevel);
		AsyncList<HazardCurves> result = createWithCapacity(groundMotionsList.size());
		for (ListenableFuture<HazardGroundMotions> groundMotions : groundMotionsList) {
			result.add(transform(groundMotions, function, ex));
		}
		return result;
	}

	/**
	 * Reduce a List of future HazardCurves to a future HazardCurveSet.
	 */
	static final ListenableFuture<HazardCurveSet> toHazardCurveSet(
			final AsyncList<HazardCurves> curves,
			final SourceSet<? extends Source> sourceSet,
			final Map<Imt, ArrayXY_Sequence> modelCurves,
			final ExecutorService ex) {

		Function<List<HazardCurves>, HazardCurveSet> function = new CurveConsolidator(sourceSet,
			modelCurves);
		return transform(allAsList(curves), function, ex);
	}

	/**
	 * Reduce a List of future HazardCurveSets into a future HazardResult.
	 */
	static final ListenableFuture<HazardResult> toHazardResult(
			final AsyncList<HazardCurveSet> curveSets,
			final Map<Imt, ArrayXY_Sequence> modelCurves,
			final ExecutorService ex) {

		Function<List<HazardCurveSet>, HazardResult> function = new CurveSetConsolidator(
			modelCurves);
		return transform(allAsList(curveSets), function, ex);
	}

	/*
	 * Cluster sources below...
	 * 
	 * ClusterSourceSets contain ClusterSources, each of which references a
	 * FaultSourceSet containing one or more fault representations for the
	 * ClusterSource.
	 * 
	 * e.g. for New Madrid, each ClusterSourceSet has 5 ClusterSources, one for
	 * each position variant of the model. For each position variant there is
	 * one FaultSourceSet containing the FaultSources in the cluster, each of
	 * which may have one, or more, magnitude or other variants represented by
	 * its internal List of IncrementalMfds.
	 */

	/**
	 * Convert a ClusterSourceSet to a List of future HazardInputs Lists.
	 */
	static final AsyncList<ClusterInputs> toClusterInputs(
			final ClusterSourceSet sourceSet,
			final Site site,
			final ExecutorService ex) {

		Function<ClusterSource, ClusterInputs> function = new ClusterSourceToInputs(site);
		AsyncList<ClusterInputs> result = AsyncList.create();
		for (ClusterSource source : sourceSet.iterableForLocation(site.location)) {
			result.add(transform(immediateFuture(source), function, ex));
		}
		return result;
	}

	/**
	 * Convert a List of future HazardInputs Lists to a List of future
	 * HazardGroundMotions Lists.
	 */
	static final AsyncList<ClusterGroundMotions> toClusterGroundMotions(
			final AsyncList<ClusterInputs> inputsList,
			final ClusterSourceSet sourceSet,
			final Set<Imt> imts,
			final ExecutorService ex) {

		Set<Gmm> gmms = sourceSet.groundMotionModels().gmms();
		Table<Gmm, Imt, GroundMotionModel> gmmInstances = Gmm.instances(gmms, imts);
		Function<ClusterInputs, ClusterGroundMotions> function = new ClusterInputsToGroundMotions(
			gmmInstances);
		AsyncList<ClusterGroundMotions> result = createWithCapacity(inputsList.size());
		for (ListenableFuture<ClusterInputs> inputs : inputsList) {
			result.add(transform(inputs, function, ex));
		}
		return result;
	}

	/**
	 * Convert a List of future HazardGroundMotions Lists to a List of future
	 * ClusterCurves.
	 */
	static final AsyncList<ClusterCurves> toClusterCurves(
			final AsyncList<ClusterGroundMotions> clusterGroundMotions,
			final Map<Imt, ArrayXY_Sequence> modelCurves,
			final ExceedanceModel sigmaModel,
			final double truncLevel,
			final ExecutorService ex) {

		Function<ClusterGroundMotions, ClusterCurves> function = new ClusterGroundMotionsToCurves(
			modelCurves, sigmaModel, truncLevel);
		AsyncList<ClusterCurves> result = createWithCapacity(clusterGroundMotions.size());
		for (ListenableFuture<ClusterGroundMotions> groundMotions : clusterGroundMotions) {
			result.add(transform(groundMotions, function, ex));
		}
		return result;
	}

	/**
	 * Reduce a List of future ClusterCurves to a future HazardCurveSet.
	 */
	static final ListenableFuture<HazardCurveSet> toHazardCurveSet(
			final AsyncList<ClusterCurves> curvesList,
			final ClusterSourceSet clusterSourceSet,
			final Map<Imt, ArrayXY_Sequence> modelCurves,
			final ExecutorService ex) {

		Function<List<ClusterCurves>, HazardCurveSet> function = new ClusterCurveConsolidator(
			clusterSourceSet, modelCurves);
		return transform(allAsList(curvesList), function, ex);
	}

}
