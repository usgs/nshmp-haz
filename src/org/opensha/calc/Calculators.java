package org.opensha.calc;

import static com.google.common.util.concurrent.Futures.allAsList;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static com.google.common.util.concurrent.Futures.transform;
import static org.opensha.calc.AsyncList.createWithCapacity;
import static org.opensha.calc.Transforms.clustersToGroundMotions;
import static org.opensha.calc.Transforms.curveConsolidator;
import static org.opensha.calc.Transforms.curveSetConsolidator;
import static org.opensha.calc.Transforms.groundMotionsToCurves;
import static org.opensha.calc.Transforms.inputsToGroundMotions;
import static org.opensha.calc.Transforms.sourceToInputs;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.opensha.data.ArrayXY_Sequence;
import org.opensha.eq.forecast.ClusterSourceSet;
import org.opensha.eq.forecast.Source;
import org.opensha.eq.forecast.SourceSet;
import org.opensha.gmm.Gmm;
import org.opensha.gmm.GroundMotionModel;
import org.opensha.gmm.Imt;

import com.google.common.base.Function;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

/**
 * Static utility methods for asynchronously performing the various steps in a
 * hazard calculation.
 * 
 * @author Peter Powers
 * @see Transforms
 */
final class Calculators {
	private Calculators() {}

	private static final ExecutorService EX;

	static {
		int numProc = Runtime.getRuntime().availableProcessors();
		EX = Executors.newFixedThreadPool(numProc);
	}

	/*
	 * Convert a SourceSet to a List of GmmInputLists, one for each source
	 * within range.
	 */
	static AsyncList<HazardInputs> toInputs(SourceSet<? extends Source> sourceSet, Site site) {
		Function<Source, HazardInputs> function = sourceToInputs(site);
		AsyncList<HazardInputs> result = AsyncList.create();
		for (Source source : sourceSet.locationIterable(site.loc)) {
			result.add(transform(immediateFuture(source), function, EX));
		}
		return result;
	}

	/*
	 * Convert a List of GmmInputLists to a List of GroundMotionSets.
	 */
	static AsyncList<HazardGroundMotions> toGroundMotions(AsyncList<HazardInputs> inputLists,
			SourceSet<? extends Source> sourceSet, Imt imt) {
		Map<Gmm, GroundMotionModel> gmmInstances = Gmm.instances(sourceSet.groundMotionModels()
			.gmms(), imt);
		Function<HazardInputs, HazardGroundMotions> function = inputsToGroundMotions(gmmInstances);
		AsyncList<HazardGroundMotions> result = createWithCapacity(inputLists.size());
		for (ListenableFuture<HazardInputs> inputs : inputLists) {
			result.add(transform(inputs, function, EX));
		}
		return result;
	}

	/*
	 * Convert a List of GroundMotionSets to a List of hazard curve maps, with
	 * one curve per GroundMotionModel.
	 */
	static AsyncList<HazardCurves> toHazardCurves(AsyncList<HazardGroundMotions> groundMotions,
			ArrayXY_Sequence model) {
		Function<HazardGroundMotions, HazardCurves> function = groundMotionsToCurves(model);
		AsyncList<HazardCurves> result = createWithCapacity(groundMotions.size());
		for (ListenableFuture<HazardGroundMotions> gmSet : groundMotions) {
			result.add(transform(gmSet, function, EX));
		}
		return result;
	}

	/*
	 * Combine a List of HazardCurves into a HazardCurveSet.
	 */
	static ListenableFuture<HazardCurveSet> toHazardCurveSet(AsyncList<HazardCurves> curves,
			SourceSet<? extends Source> sourceSet, ArrayXY_Sequence model) {
		Function<List<HazardCurves>, HazardCurveSet> function = curveConsolidator(sourceSet, model);
		return transform(allAsList(curves), function, EX);
	}

	/*
	 * Combine a List of HazardCurveSets into a HazardResult.
	 */
	static ListenableFuture<HazardResult> toHazardResult(AsyncList<HazardCurveSet> curveSets) {
		Function<List<HazardCurveSet>, HazardResult> function = curveSetConsolidator();
		return transform(allAsList(curveSets), function, EX);
	}

	/*
	 * Process a ClusterSourceSet to a List of HazardGroundMotions Lists,
	 * wrapped in a ListenableFuture.
	 */
	static ListenableFuture<List<List<HazardGroundMotions>>> toClusterGroundMotions(
			ClusterSourceSet sourceSet, Site site, Imt imt) {

		// List (outer) --> clusters (geometry variants)
		// List (inner) --> faults (sections)
		// HazardGroundMotions --> magnitude variants

		return transform(immediateFuture(sourceSet), clustersToGroundMotions(site, imt), EX);
	}

	static ListenableFuture<Map<Gmm, ArrayXY_Sequence>> toClusterCurve(
			List<HazardGroundMotions> groundMotions, ArrayXY_Sequence model) {

		// List --> faults (sections)
		// HazardGroundMotions --> magnitude variants

		return Futures.transform(Futures.immediateFuture(groundMotions),
			Transforms.clusterGroundMotionsToCurves(model), EX);
	}

	static ListenableFuture<Map<Gmm, ArrayXY_Sequence>> toClusterCurve2(
		List<HazardGroundMotions> groundMotions, ArrayXY_Sequence model) {

	// List --> faults (sections)
	// HazardGroundMotions --> magnitude variants

	return Futures.transform(Futures.immediateFuture(groundMotions),
		Transforms.clusterGroundMotionsToCurves(model), EX);
}


	// TODO clean
	/*
	 * Process a SourceSet splitting out all tasks as Futures.
	 */
//	@Deprecated List<ListenableFuture<HazardGroundMotions>> toGroundMotions1(
//			SourceSet<? extends Source> sources, Site site, Imt imt) {
//
//		// get ground motion models
//		Map<Gmm, GroundMotionModel> gmmInstances = Gmm.instances(sources.groundMotionModels()
//			.gmms(), imt);
//
//		// set up reusable transforms
//		Function<Source, HazardInputs> sourceToInputs = Transforms.sourceToInputs(site);
//		Function<HazardInputs, HazardGroundMotions> inputsToGroundMotions = Transforms
//			.inputsToGroundMotions(gmmInstances);
//
//		// ground motion set aggregator
//		List<ListenableFuture<HazardGroundMotions>> gmsFutures = Lists.newArrayList();
//
//		for (Source source : sources.locationIterable(site.loc)) {
//
//			// wrap Source in ListenableFuture for Futures.transform()
//			ListenableFuture<Source> srcFuture = Futures.immediateFuture(source);
//
//			// transform source to inputs
//			ListenableFuture<HazardInputs> gmmInputs = Futures.transform(srcFuture, sourceToInputs,
//				EX);
//
//			// transform inputs to ground motions
//			ListenableFuture<HazardGroundMotions> gmResults = Futures.transform(gmmInputs,
//				inputsToGroundMotions, EX);
//
//			gmsFutures.add(gmResults);
//		}
//
//		return gmsFutures;
//	}
//
//	/*
//	 * Process a SourceSet reducing the Source --> GmmInput --> GroundMotion to
//	 * a single transform.
//	 */
//	@Deprecated List<ListenableFuture<HazardGroundMotions>> toGroundMotions2(
//			SourceSet<? extends Source> sources, Site site, Imt imt) {
//
//		// get ground motion models
//		Map<Gmm, GroundMotionModel> gmmInstances = Gmm.instances(sources.groundMotionModels()
//			.gmms(), imt);
//
//		Function<Source, HazardGroundMotions> transform = Functions.compose(
//			Transforms.inputsToGroundMotions(gmmInstances), Transforms.sourceToInputs(site));
//
//		// ground motion set aggregator
//		List<ListenableFuture<HazardGroundMotions>> gmsFutures = Lists.newArrayList();
//
//		for (Source source : sources.locationIterable(site.loc)) {
//			gmsFutures.add(Futures.transform(Futures.immediateFuture(source), transform, EX));
//		}
//
//		return gmsFutures;
//	}
//
//	/*
//	 * Process a SourceSet on a single thread.
//	 */
//	@Deprecated List<HazardGroundMotions> toGroundMotions3(SourceSet<? extends Source> sources,
//			Site site, Imt imt) {
//
//		// get ground motion models
//		Map<Gmm, GroundMotionModel> gmmInstances = Gmm.instances(sources.groundMotionModels()
//			.gmms(), imt);
//
//		// set up reusable transforms
//		Function<Source, HazardInputs> sourceToInputs = Transforms.sourceToInputs(site);
//		Function<HazardInputs, HazardGroundMotions> inputsToGroundMotions = Transforms
//			.inputsToGroundMotions(gmmInstances);
//
//		List<HazardGroundMotions> gmsList = new ArrayList<>();
//
//		for (Source source : sources.locationIterable(site.loc)) {
//
//			// alt:
//			// HazardInputs gmmInputs = sourceToInputs.apply(source);
//			// HazardGroundMotions gmResults =
//			// inputsToGroundMotions.apply(gmmInputs);
//			// gmSetList.add(gmResults);
//
//			gmsList.add(inputsToGroundMotions.apply(sourceToInputs.apply(source)));
//
//		}
//		return gmsList;
//	}
//
//	/*
//	 * Process a SourceSet on its own thread.
//	 */
//	@Deprecated ListenableFuture<List<HazardGroundMotions>> toGroundMotions4(
//			SourceSet<? extends Source> sources, Site site, Imt imt) {
//
//		return Futures.transform(Futures.immediateFuture(sources),
//			Transforms.sourcesToGroundMotions(site, imt), EX);
//	}
//
//	/*
//	 * Compute a hazard curve for a List of GroundMotionSets associated with a
//	 * SourceSet
//	 */
//	@Deprecated List<ListenableFuture<HazardCurves>> toHazardCurves(
//			List<ListenableFuture<HazardGroundMotions>> groundMotionList) {
//
//		ArrayXY_Sequence modelCurve = ArrayXY_Sequence.create(Utils.NSHM_IMLS, null);
//
//		Function<HazardGroundMotions, HazardCurves> groundMotionsToCurves = Transforms
//			.groundMotionsToCurves(modelCurve);
//
//		List<ListenableFuture<HazardCurves>> curveFutures = Lists.newArrayList();
//
//		for (ListenableFuture<HazardGroundMotions> gmSet : groundMotionList) {
//			curveFutures.add(Futures.transform(gmSet, groundMotionsToCurves, EX));
//		}
//
//		return curveFutures;
//	}


}
