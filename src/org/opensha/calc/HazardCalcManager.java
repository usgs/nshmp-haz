package org.opensha.calc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.opensha.data.ArrayXY_Sequence;
import org.opensha.eq.forecast.ClusterSourceSet;
import org.opensha.eq.forecast.Forecast;
import org.opensha.eq.forecast.Source;
import org.opensha.eq.forecast.SourceSet;
import org.opensha.eq.forecast.SourceType;
import org.opensha.gmm.Gmm;
import org.opensha.gmm.GroundMotionModel;
import org.opensha.gmm.Imt;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

/**
 * Hazard calculation manager.
 * 
 * Design goals:
 * 
 * 1) Remove statefulness (that requires synchronization) from EqkRupture and
 * FaultSurface heirarchies.
 * 
 * 2) Provide multithreaded single site hazard calculations that easily scale to
 * HPC resources for multiple sites; sources separated onto threads, not
 * locations
 * 
 * @author Peter Powers
 * @version $Id:$
 */
public class HazardCalcManager {

	private static final ExecutorService EX;

	static {
		int numProc = Runtime.getRuntime().availableProcessors();
		EX = Executors.newFixedThreadPool(numProc);
	}

	private HazardCalcManager() {}

	public static HazardCalcManager create() {
		HazardCalcManager hc = new HazardCalcManager();
		return hc;
	}

	public void calc(Forecast forecast, Site site, Imt imt) throws InterruptedException,
			ExecutionException {

		for (SourceSet<? extends Source> srcSet : forecast) {
			SourceType type = srcSet.type();
			switch (type) {
				case CLUSTER:
					System.out.println("Skipping cluster: " + srcSet.name());
					break;
				default:

			}
		}
	}

	// TODO epiphany!!! Although it will be a little more work, if we have a
	// multi-distance
	// GmmSet model, we will do all calculations; the far gmms are currently
	// required to be a subset of the near
	// gmms. Even without this requirement, we would compute ground motions for
	// the master set of gmms.
	// Only when recombining scalar ground motions into hazard curves will we
	// chose those values
	// required at different distances as we will have the associated GmmInputs
	// handy

	// The methods below spread tasksa cross different threads with decreasing
	// fine-grainedness

	/*
	 * Process a SourceSet splitting out all tasks as Futures.
	 */
	List<ListenableFuture<GroundMotionSet>> toGroundMotions1(SourceSet<? extends Source> sources,
			Site site, Imt imt) {

		// get ground motion models
		Map<Gmm, GroundMotionModel> gmmInstances = Gmm.instances(sources.groundMotionModels()
			.gmms(), imt);

		// set up reusable transforms
		Function<Source, GmmInputList> sourceToInputs = Transforms.sourceToInputs(site);
		Function<GmmInputList, GroundMotionSet> inputsToGroundMotions = Transforms
			.inputsToGroundMotions(gmmInstances);

		// ground motion set aggregator
		List<ListenableFuture<GroundMotionSet>> gmsFutures = Lists.newArrayList();

		for (Source source : sources.locationIterable(site.loc)) {

			// wrap Source in ListenableFuture for Futures.transform()
			ListenableFuture<Source> srcFuture = Futures.immediateFuture(source);

			// transform source to inputs
			ListenableFuture<GmmInputList> gmmInputs = Futures.transform(srcFuture, sourceToInputs,
				EX);

			// transform inputs to ground motions
			ListenableFuture<GroundMotionSet> gmResults = Futures.transform(gmmInputs,
				inputsToGroundMotions, EX);

			gmsFutures.add(gmResults);
		}

		return gmsFutures;
	}

	/*
	 * Process a SourceSet reducing the Source --> GmmInput --> GroundMotion to
	 * a single transform.
	 */
	List<ListenableFuture<GroundMotionSet>> toGroundMotions2(SourceSet<? extends Source> sources,
			Site site, Imt imt) {

		// get ground motion models
		Map<Gmm, GroundMotionModel> gmmInstances = Gmm.instances(sources.groundMotionModels()
			.gmms(), imt);

		Function<Source, GroundMotionSet> transform = Functions.compose(
			Transforms.inputsToGroundMotions(gmmInstances), Transforms.sourceToInputs(site));

		// ground motion set aggregator
		List<ListenableFuture<GroundMotionSet>> gmsFutures = Lists.newArrayList();

		for (Source source : sources.locationIterable(site.loc)) {
			gmsFutures.add(Futures.transform(Futures.immediateFuture(source), transform, EX));
		}

		return gmsFutures;
	}

	/*
	 * Process a SourceSet on a single thread.
	 */
	List<GroundMotionSet> toGroundMotions3(SourceSet<? extends Source> sources, Site site, Imt imt) {

		// get ground motion models
		Map<Gmm, GroundMotionModel> gmmInstances = Gmm.instances(sources.groundMotionModels()
			.gmms(), imt);

		// set up reusable transforms
		Function<Source, GmmInputList> sourceToInputs = Transforms.sourceToInputs(site);
		Function<GmmInputList, GroundMotionSet> inputsToGroundMotions = Transforms
			.inputsToGroundMotions(gmmInstances);

		List<GroundMotionSet> gmsList = new ArrayList<>();

		for (Source source : sources.locationIterable(site.loc)) {

			// alt:
			// GmmInputList gmmInputs = sourceToInputs.apply(source);
			// GroundMotionSet gmResults =
			// inputsToGroundMotions.apply(gmmInputs);
			// gmSetList.add(gmResults);

			gmsList.add(inputsToGroundMotions.apply(sourceToInputs.apply(source)));

		}
		return gmsList;
	}

	/*
	 * Process a SourceSet on its own thread.
	 */
	ListenableFuture<List<GroundMotionSet>> toGroundMotions4(SourceSet<? extends Source> sources,
			Site site, Imt imt) {

		return Futures.transform(Futures.immediateFuture(sources),
			Transforms.sourcesToGroundMotions(site, imt), EX);
	}

	/*
	 * Compute a hazard curve for a List of GroundMotionSets associated with a
	 * SourceSet
	 */
	List<ListenableFuture<Map<Gmm, ArrayXY_Sequence>>> toHazardCurves(
			List<ListenableFuture<GroundMotionSet>> groundMotionList) {

		ArrayXY_Sequence modelCurve = ArrayXY_Sequence.create(Utils.NSHM_IMLS, null);

		Function<GroundMotionSet, Map<Gmm, ArrayXY_Sequence>> groundMotionsToCurves = Transforms
			.groundMotionsToCurves(modelCurve);

		List<ListenableFuture<Map<Gmm, ArrayXY_Sequence>>> curveFutures = Lists.newArrayList();

		for (ListenableFuture<GroundMotionSet> gmSet : groundMotionList) {
			curveFutures.add(Futures.transform(gmSet, groundMotionsToCurves, EX));
		}

		return curveFutures;
	}
	
	

	/*
	 * Process a ClusterSourceSet to a List of GroundMotionSet Lists, wrapped in
	 * a ListenableFuture.
	 */
	public ListenableFuture<List<List<GroundMotionSet>>> toClusterGroundMotions(
			ClusterSourceSet sources, Site site, Imt imt) {

		// List (outer) --> clusters (geometry variants)
		// List (inner) --> faults (sections)
		// GroundMotionSet --> magnitude variants

		return Futures.transform(Futures.immediateFuture(sources),
			Transforms.clustersToGroundMotions(site, imt), EX);
	}

	public ListenableFuture<Map<Gmm, ArrayXY_Sequence>> toClusterCurve(
			List<GroundMotionSet> groundMotions, ArrayXY_Sequence model) throws Exception {

		// List (inner) --> faults (sections)
		// GroundMotionSet --> magnitude variants

		return Futures.transform(Futures.immediateFuture(groundMotions),
			Transforms.clusterGroundMotionsToCurves(model), EX);
	}

	// TODO where/how to apply CEUS clamps

}
