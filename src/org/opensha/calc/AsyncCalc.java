package org.opensha.calc;

import static com.google.common.util.concurrent.Futures.allAsList;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static com.google.common.util.concurrent.Futures.transform;
import static org.opensha.calc.AsyncList.createWithCapacity;
import static org.opensha.calc.Transforms.clusterCurveConsolidator;
import static org.opensha.calc.Transforms.clusterGroundMotionsToCurves;
import static org.opensha.calc.Transforms.clusterInputsToGroundMotions;
import static org.opensha.calc.Transforms.clusterSourceToInputs;
import static org.opensha.calc.Transforms.curveConsolidator;
import static org.opensha.calc.Transforms.curveSetConsolidator;
import static org.opensha.calc.Transforms.groundMotionsToCurves;
import static org.opensha.calc.Transforms.inputsToGroundMotions;
import static org.opensha.calc.Transforms.sourceToInputs;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.opensha.data.ArrayXY_Sequence;
import org.opensha.eq.model.ClusterSource;
import org.opensha.eq.model.ClusterSourceSet;
import org.opensha.eq.model.Source;
import org.opensha.eq.model.SourceSet;
import org.opensha.gmm.Gmm;
import org.opensha.gmm.GroundMotionModel;
import org.opensha.gmm.Imt;

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
	static AsyncList<HazardInputs> toInputs(SourceSet<? extends Source> sourceSet, Site site,
			ExecutorService ex) {
		Function<Source, HazardInputs> function = sourceToInputs(site);
		AsyncList<HazardInputs> result = AsyncList.create();
		for (Source source : sourceSet.iterableForlocation(site.location)) {
			result.add(transform(immediateFuture(source), function, ex));
		}
		return result;
	}

	/**
	 * Convert a List of future HazardInputs to a List of future
	 * HazardGroundMotions.
	 */
	static AsyncList<HazardGroundMotions> toGroundMotions(AsyncList<HazardInputs> inputsList,
			SourceSet<? extends Source> sourceSet, Set<Imt> imts, ExecutorService ex) {
		Set<Gmm> gmms = sourceSet.groundMotionModels().gmms();
		Table<Gmm, Imt, GroundMotionModel> gmmInstances = Gmm.instances(gmms, imts);
		Function<HazardInputs, HazardGroundMotions> function = inputsToGroundMotions(gmmInstances);
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
	static AsyncList<HazardCurves> toHazardCurves(AsyncList<HazardGroundMotions> groundMotionsList,
			Map<Imt, ArrayXY_Sequence> modelCurves, ExceedanceModel sigmaModel, double truncLevel,
			ExecutorService ex) {
		Function<HazardGroundMotions, HazardCurves> function = groundMotionsToCurves(modelCurves,
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
	static ListenableFuture<HazardCurveSet> toHazardCurveSet(AsyncList<HazardCurves> curves,
			SourceSet<? extends Source> sourceSet, Map<Imt, ArrayXY_Sequence> modelCurves,
			ExecutorService ex) {
		Function<List<HazardCurves>, HazardCurveSet> function = curveConsolidator(sourceSet,
			modelCurves);
		return transform(allAsList(curves), function, ex);
	}

	/**
	 * Reduce a List of future HazardCurveSets into a future HazardResult.
	 */
	static ListenableFuture<HazardResult> toHazardResult(AsyncList<HazardCurveSet> curveSets,
			Map<Imt, ArrayXY_Sequence> modelCurves, ExecutorService ex) {
		Function<List<HazardCurveSet>, HazardResult> function = curveSetConsolidator(modelCurves);
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
	static AsyncList<ClusterInputs> toClusterInputs(ClusterSourceSet sourceSet, Site site,
			ExecutorService ex) {
		Function<ClusterSource, ClusterInputs> function = clusterSourceToInputs(site);
		AsyncList<ClusterInputs> result = AsyncList.create();
		for (ClusterSource source : sourceSet.iterableForlocation(site.location)) {
			result.add(transform(immediateFuture(source), function, ex));
		}
		return result;
	}

	/**
	 * Convert a List of future HazardInputs Lists to a List of future
	 * HazardGroundMotions Lists.
	 */
	static AsyncList<ClusterGroundMotions> toClusterGroundMotions(
			AsyncList<ClusterInputs> inputsList, ClusterSourceSet sourceSet, Set<Imt> imts,
			ExecutorService ex) {
		Set<Gmm> gmms = sourceSet.groundMotionModels().gmms();
		Table<Gmm, Imt, GroundMotionModel> gmmInstances = Gmm.instances(gmms, imts);
		Function<ClusterInputs, ClusterGroundMotions> function = clusterInputsToGroundMotions(gmmInstances);
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
	static AsyncList<ClusterCurves> toClusterCurves(
			AsyncList<ClusterGroundMotions> clusterGroundMotions,
			Map<Imt, ArrayXY_Sequence> modelCurves, ExceedanceModel sigmaModel, double truncLevel,
			ExecutorService ex) {
		Function<ClusterGroundMotions, ClusterCurves> function = clusterGroundMotionsToCurves(
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
	static ListenableFuture<HazardCurveSet> toHazardCurveSet(AsyncList<ClusterCurves> curvesList,
			ClusterSourceSet clusterSourceSet, Map<Imt, ArrayXY_Sequence> modelCurves,
			ExecutorService ex) {
		Function<List<ClusterCurves>, HazardCurveSet> function = clusterCurveConsolidator(
			clusterSourceSet, modelCurves);
		return transform(allAsList(curvesList), function, ex);
	}

}
