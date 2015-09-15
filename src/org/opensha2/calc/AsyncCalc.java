package org.opensha2.calc;

import static com.google.common.util.concurrent.Futures.allAsList;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static com.google.common.util.concurrent.Futures.transform;
import static org.opensha2.calc.AsyncList.createWithCapacity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

import org.opensha2.calc.Transforms.ClusterCurveConsolidator;
import org.opensha2.calc.Transforms.ClusterGroundMotionsToCurves;
import org.opensha2.calc.Transforms.ClusterInputsToGroundMotions;
import org.opensha2.calc.Transforms.ClusterSourceToInputs;
import org.opensha2.calc.Transforms.CurveConsolidator;
import org.opensha2.calc.Transforms.CurveSetConsolidator;
import org.opensha2.calc.Transforms.GroundMotionsToCurves;
import org.opensha2.calc.Transforms.InputsToGroundMotions;
import org.opensha2.calc.Transforms.SourceToInputs;
import org.opensha2.data.ArrayXY_Sequence;
import org.opensha2.eq.model.ClusterSource;
import org.opensha2.eq.model.ClusterSourceSet;
import org.opensha2.eq.model.HazardModel;
import org.opensha2.eq.model.Source;
import org.opensha2.eq.model.SourceSet;
import org.opensha2.eq.model.SystemSourceSet;
import org.opensha2.gmm.Gmm;
import org.opensha2.gmm.GroundMotionModel;
import org.opensha2.gmm.Imt;

import com.google.common.base.Function;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
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
	static final AsyncList<InputList> toInputs(
			final SourceSet<? extends Source> sourceSet,
			final Site site,
			final Executor ex) {

		Function<Source, InputList> function = new SourceToInputs(site);
		AsyncList<InputList> result = AsyncList.create();
		for (Source source : sourceSet.iterableForLocation(site.location)) {
			result.add(transform(immediateFuture(source), function, ex));
		}
		return result;
	}

	/**
	 * Convert a List of future HazardInputs to a List of future
	 * HazardGroundMotions.
	 */
	static final AsyncList<GroundMotions> toGroundMotions(
			final AsyncList<InputList> inputsList,
			final SourceSet<? extends Source> sourceSet,
			final Set<Imt> imts,
			final Executor ex) {

		Set<Gmm> gmms = sourceSet.groundMotionModels().gmms();
		Table<Gmm, Imt, GroundMotionModel> gmmInstances = Gmm.instances(gmms, imts);
		Function<InputList, GroundMotions> function = new InputsToGroundMotions(gmmInstances);
		AsyncList<GroundMotions> result = createWithCapacity(inputsList.size());
		for (ListenableFuture<InputList> hazardInputs : inputsList) {
			result.add(transform(hazardInputs, function, ex));
		}
		return result;
	}

	/**
	 * Convert a List of future HazardGroundMotions to a List of future
	 * HazardCurves.
	 */
	static final AsyncList<HazardCurves> toHazardCurves(
			final AsyncList<GroundMotions> groundMotionsList,
			final CalcConfig config,
			final Executor ex) {

		Function<GroundMotions, HazardCurves> function = new GroundMotionsToCurves(config);
		AsyncList<HazardCurves> result = createWithCapacity(groundMotionsList.size());
		for (ListenableFuture<GroundMotions> groundMotions : groundMotionsList) {
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
			final Executor ex) {

		Function<List<HazardCurves>, HazardCurveSet> function = new CurveConsolidator(sourceSet,
			modelCurves);
		return transform(allAsList(curves), function, ex);
	}

	/**
	 * Reduce a future HazardCurves to a future HazardCurveSet.
	 */
	@SuppressWarnings("unchecked") static final ListenableFuture<HazardCurveSet> toHazardCurveSet(
			final ListenableFuture<HazardCurves> curves,
			final SystemSourceSet sourceSet,
			final Map<Imt, ArrayXY_Sequence> modelCurves,
			final Executor ex) {

		Function<List<HazardCurves>, HazardCurveSet> function = new CurveConsolidator(
			sourceSet,
			modelCurves);
		return transform(allAsList(curves), function, ex);
	}

	/**
	 * Reduce a List of future HazardCurveSets into a future HazardResult.
	 */
	static final ListenableFuture<HazardResult> toHazardResult(
			final AsyncList<HazardCurveSet> curveSets,
			final CalcConfig config,
			final Site site,
			final HazardModel model,
			final Executor ex) {

		return transform(
			allAsList(curveSets),
			new CurveSetConsolidator(site, model, config),
			ex);
	}

	// single thread system calc
	static final HazardCurveSet systemToCurves(
			final SystemSourceSet sourceSet,
			final Site site,
			final CalcConfig config) {

		Stopwatch sw = Stopwatch.createStarted();

		Function<SystemSourceSet, InputList> inputFn = new SystemSourceSet.ToInputs(site);
		InputList inputs = inputFn.apply(sourceSet);
		System.out.println("Inputs: " + inputs.size() + "  " + sw);

		Set<Gmm> gmms = sourceSet.groundMotionModels().gmms();
		Table<Gmm, Imt, GroundMotionModel> gmmInstances = Gmm.instances(gmms, config.imts);
		Function<InputList, GroundMotions> gmFn = new InputsToGroundMotions(gmmInstances);
		GroundMotions groundMotions = gmFn.apply(inputs);
		System.out.println("GroundMotions: " + sw);

		Map<Imt, ArrayXY_Sequence> modelCurves = config.logModelCurves;
		Function<GroundMotions, HazardCurves> curveFn = new GroundMotionsToCurves(config);
		HazardCurves hazardCurves = curveFn.apply(groundMotions);
		System.out.println("HazardCurves: " + sw);

		Function<List<HazardCurves>, HazardCurveSet> consolidateFn = new CurveConsolidator(
			sourceSet, modelCurves);
		HazardCurveSet curveSet = consolidateFn.apply(ImmutableList.of(hazardCurves));
		System.out.println("CurveSet: " + sw);

		return curveSet;
	}
	
	// single thread calc
	static final List<HazardCurveSet> sourceSetToCurves(
			final SourceSet<? extends Source> sourceSet,
			final Site site,
			final CalcConfig config) {

//		Stopwatch sw = Stopwatch.createStarted();

		List<HazardCurveSet> curveSetList = new ArrayList<>();
		for (Source source : sourceSet.iterableForLocation(site.location)) {
			
			Function<Source, InputList> inputFn = new SourceToInputs(site);
			InputList inputs = inputFn.apply(source);
//			System.out.println("Inputs: " + inputs.size() + "  " + sw);

			// TODO why isn't this outside the loop??
			Set<Gmm> gmms = sourceSet.groundMotionModels().gmms(); 
			Table<Gmm, Imt, GroundMotionModel> gmmInstances = Gmm.instances(gmms, config.imts);
			Function<InputList, GroundMotions> gmFn = new InputsToGroundMotions(gmmInstances);
			GroundMotions groundMotions = gmFn.apply(inputs);
//			System.out.println("GroundMotions: " + sw);

			Map<Imt, ArrayXY_Sequence> modelCurves = config.logModelCurves;
			Function<GroundMotions, HazardCurves> curveFn = new GroundMotionsToCurves(config);
			HazardCurves hazardCurves = curveFn.apply(groundMotions);
//			System.out.println("HazardCurves: " + sw);

			Function<List<HazardCurves>, HazardCurveSet> consolidateFn = new CurveConsolidator(
				sourceSet, modelCurves);
			HazardCurveSet curveSet = consolidateFn.apply(ImmutableList.of(hazardCurves));
//			System.out.println("CurveSet: " + sw);

			curveSetList.add(curveSet);
		}

		return curveSetList;
	}


	/*
	 * System sources ...
	 * 
	 * SystemSourceSets contain many single sources which are handled
	 * collectively. See SystemSourceSet for more details. These methods are
	 * largely the same as their asyncList counterparts above, except that they
	 * only operate on a single large list, rather than lists of lists.
	 */

	/**
	 * Convert a SystemSourceSet to a future SystemInputs.
	 */
	static final ListenableFuture<InputList> toSystemInputs(
			final SystemSourceSet sourceSet,
			final Site site,
			final Executor ex) {

		return transform(
			immediateFuture(sourceSet),
			new SystemSourceSet.ToInputs(site),
			ex);
	}

	/**
	 * Convert a future List of SystemInputs to a future List of
	 * SystemGroundMotions.
	 */
	static final ListenableFuture<GroundMotions> toSystemGroundMotions(
			final ListenableFuture<InputList> inputs,
			final SystemSourceSet sourceSet,
			final Set<Imt> imts,
			final Executor ex) {

		Set<Gmm> gmms = sourceSet.groundMotionModels().gmms();
		Table<Gmm, Imt, GroundMotionModel> gmmInstances = Gmm.instances(gmms, imts);
		Function<InputList, GroundMotions> function = new InputsToGroundMotions(gmmInstances);
		return transform(inputs, function, ex);
	}

	/**
	 * Convert a future List SystemGroundMotions to a future List SystemCurves.
	 */
	static final ListenableFuture<HazardCurves> toSystemCurves(
			final ListenableFuture<GroundMotions> groundMotions,
			final CalcConfig config,
			final Executor ex) {

		Function<GroundMotions, HazardCurves> function = new GroundMotionsToCurves(config);
		return transform(groundMotions, function, ex);
	}

	/*
	 * Cluster sources ...
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
			final Executor ex) {

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
			final Executor ex) {

		Set<Gmm> gmms = sourceSet.groundMotionModels().gmms();
		Table<Gmm, Imt, GroundMotionModel> gmmInstances = Gmm.instances(gmms, imts);
		Function<ClusterInputs, ClusterGroundMotions> function =
			new ClusterInputsToGroundMotions(gmmInstances);
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
			final CalcConfig config,
			final Executor ex) {

		Function<ClusterGroundMotions, ClusterCurves> function =
			new ClusterGroundMotionsToCurves(config);
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
			final Executor ex) {

		Function<List<ClusterCurves>, HazardCurveSet> function = new ClusterCurveConsolidator(
			clusterSourceSet, modelCurves);
		return transform(allAsList(curvesList), function, ex);
	}

}
