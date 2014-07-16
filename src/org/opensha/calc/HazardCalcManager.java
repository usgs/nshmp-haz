package org.opensha.calc;

import static com.google.common.util.concurrent.MoreExecutors.listeningDecorator;
import static java.util.concurrent.Executors.newFixedThreadPool;
import static java.lang.Runtime.getRuntime;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.opensha.data.ArrayXY_Sequence;
import org.opensha.data.XY_Sequence;
import org.opensha.eq.forecast.ClusterSource;
import org.opensha.eq.forecast.ClusterSourceSet;
import org.opensha.eq.forecast.FaultSource;
import org.opensha.eq.forecast.FaultSourceSet;
import org.opensha.eq.forecast.Forecast;
import org.opensha.eq.forecast.Rupture;
import org.opensha.eq.forecast.Source;
import org.opensha.eq.forecast.SourceSet;
import org.opensha.eq.forecast.SourceType;
import org.opensha.geo.Location;
import org.opensha.gmm.Gmm;
import org.opensha.gmm.GmmInput;
import org.opensha.gmm.GroundMotionModel;
import org.opensha.gmm.Imt;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

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

	private static ExecutorService ex;

	static {
		int numProc = Runtime.getRuntime().availableProcessors();
		ex = Executors.newFixedThreadPool(numProc);
	}

	private HazardCalcManager() {}

	public static HazardCalcManager create() {
		HazardCalcManager hc = new HazardCalcManager();
		return hc;
	}

	public void calc(Forecast forecast, Site site, Imt imt) throws InterruptedException,
			ExecutionException {

		// List<Source> sources;
		for (SourceSet<? extends Source> srcSet : forecast) {
			SourceType type = srcSet.type();
			switch (type) {
				case FAULT:
					// doFaultCalc((FaultSourceSet) srcSet, gmmMap, site);
					break;
				case GRID:
					break;
				case AREA:
					break;
				case INTERFACE:
					break;
				case CLUSTER:
					break;
				default:
					throw new IllegalArgumentException("SourceType not handled: " + type.name());
			}
		}

		// Build RuptureData object -- contains lists of ruptures,
		// quick distance filter

		// Distance

		// list of valid rupture indices
		// needs DistanceFilter (can be mag dependent but doesn't have to be
		// List<Integer> indices = filterRuptures();
		// System.out.println("Rupture count: " + indices.size());

		// calculate mean and std dev for each GMPE
		// HazardCalcResultSet hcrs;

		// calculate total curve

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

	/*
	 * Processes a SourceSet to a List of GroundMotionSets, wrapped in a
	 * ListenableFuture.
	 */
	public List<ListenableFuture<GroundMotionSet>> toGroundMotions(SourceSet<? extends Source> sourceSet, Site site,
			Imt imt) throws ExecutionException, InterruptedException {

		// get ground motion models
		Map<Gmm, GroundMotionModel> gmmInstances = Gmm.instances(sourceSet.groundMotionModels()
			.gmms(), imt);

		// set up reusable transforms
		AsyncFunction<Source, List<GmmInput>> sourceToInputs = Transforms.sourceToInputs(site);
		AsyncFunction<List<GmmInput>, GroundMotionSet> inputsToGroundMotions = Transforms
			.inputsToGroundMotions(gmmInstances);

		// ground motion set aggregator
		List<ListenableFuture<GroundMotionSet>> futuresList = Lists.newArrayList();

		for (Source source : sourceSet.locationIterable(site.loc)) {

			// for the sake of consistency, we wrap Sources in ListenableFutures
			// so that we're only ever using Futures.transform() and don't need
			// an instance of a ListeningExecutorService
			ListenableFuture<Source> srcFuture = Futures.immediateFuture(source);

			// transform source to inputs
			ListenableFuture<List<GmmInput>> gmmInputs = Futures.transform(srcFuture,
				sourceToInputs, ex);

			// transform inputs to ground motions
			ListenableFuture<GroundMotionSet> gmResults = Futures.transform(gmmInputs,
				inputsToGroundMotions, ex);

			futuresList.add(gmResults);
		}

		return futuresList; //Futures.allAsList(futuresList);
	}

	
	
	
	/*
	 * Processes a ClusterSourceSet to a List of GroundMotionSet Lists, wrapped
	 * in a ListenableFuture.
	 */
	public ListenableFuture<List<List<GroundMotionSet>>> toClusterGroundMotions(
			ClusterSourceSet sourceSet, Site site, Imt imt) {

		List<ListenableFuture<List<GroundMotionSet>>> clusterMotions = Lists.newArrayList();

		for (ClusterSource cluster : sourceSet) {
			FaultSourceSet faults = cluster.faults();
//			clusterMotions.add(toGroundMotions(faults, site, imt));
		}

		return Futures.allAsList(clusterMotions);
	}

	
	
	
	public List<ListenableFuture<Map<Gmm, ArrayXY_Sequence>>> toMeanHazardCurve(
			List<ListenableFuture<GroundMotionSet>> groundMotionList) throws ExecutionException, InterruptedException {

		ArrayXY_Sequence modelCurve = ArrayXY_Sequence.create(Utils.NSHM_IMLS, null);

		AsyncFunction<GroundMotionSet, Map<Gmm, ArrayXY_Sequence>> groundMotionsToCurves = Transforms
			.groundMotionsToCurves(modelCurve);

		List<ListenableFuture<Map<Gmm, ArrayXY_Sequence>>> futuresList = Lists.newArrayList();

		for (ListenableFuture<GroundMotionSet> gmSet : groundMotionList) {
			futuresList.add(Futures.transform(gmSet, groundMotionsToCurves, ex));
		}

		return futuresList; //Futures.allAsList(futuresList);
	}

	public void toClusterCurve(ListenableFuture<List<List<GroundMotionSet>>> asyncGroundMotions,
			XY_Sequence model) throws Exception {

		// List (outer) --> clusters (geometry variants)
		// List (inner) --> faults (sections)
		// GroundMotionSet --> magnitude variants
		List<List<GroundMotionSet>> clusters = asyncGroundMotions.get();

		for (List<GroundMotionSet> cluster : clusters) {
			for (GroundMotionSet fault : cluster) {

				// compute PE curve
			}
		}

	}

	// TODO where/how to apply CEUS clamps

	// TODO hmmm... we don't seem to have rupture rate data

	private static Map<Gmm, ArrayXY_Sequence> clusterFaultPE(GroundMotionSet gmSet,
			ArrayXY_Sequence model) {

		Map<Gmm, ArrayXY_Sequence> peMap = Maps.newEnumMap(Gmm.class);

		for (Gmm gmm : gmSet.means.keySet()) {
			List<Double> means = gmSet.means.get(gmm);
			List<Double> sigmas = gmSet.sigmas.get(gmm);
			// ArrayXY_Sequence
			// for (int i = 0; i < gmSet.inputs.size(); i++) {
			// ArrayXY_Sequence imls = ArrayXY_Sequence.copyOf(model);
			// Utils.setExceedProbabilities(imls, means.get(i), sigmas.get(i),
			// false, 0.0);
			// imls.sc
			// }
			// TODO FIX and FINISH
		}
		return null;

	}

	// reference clusterCalc from NSHMP2008

	// private static DiscretizedFunc clusterCalc(
	// DiscretizedFunc f,
	// Site s,
	// ScalarIMR imr,
	// ClusterERF erf) {
	//
	// double maxDistance = erf.getMaxDistance();
	// Utils.zeroFunc(f); //zero for aggregating results
	// DiscretizedFunc peFunc = f.deepClone();
	//
	// for (ClusterSource cs : erf.getSources()) { // geom variants
	//
	// // apply distance cutoff to source
	// double dist = cs.getMinDistance(s);
	// if (dist > maxDistance) {
	// continue;
	// }
	// // assemble list of PE curves for each cluster segment
	// List<DiscretizedFunc> fltFuncList = Lists.newArrayList();
	//
	// for (FaultSource fs : cs.getFaultSources()) { // segments
	// DiscretizedFunc fltFunc = peFunc.deepClone();
	// Utils.zeroFunc(fltFunc);
	// // agregate weighted PE curves for mags on each segment
	// for (int i=0; i < fs.getNumRuptures(); i++) { // mag variants
	// imr.setEqkRupture(fs.getRupture(i));
	// imr.getExceedProbabilities(peFunc);
	// double weight = fs.getMFDs().get(i).getY(0) * cs.getRate();
	// peFunc.scale(weight);
	// Utils.addFunc(fltFunc, peFunc);
	// } // end mag
	// fltFuncList.add(fltFunc);
	// } // end segments
	//
	// // compute joint PE, scale by geom weight, scale by rate (1/RP),
	// // and add to final result
	// DiscretizedFunc fOut = calcClusterExceedProb(fltFuncList);
	// double rateAndWeight = cs.getWeight() / cs.getRate();
	// fOut.scale(rateAndWeight);
	// Utils.addFunc(f, fOut);
	// } // end geom
	// return f;
	// }

	// ListenableFuture<List<GroundMotionSet>> gmResultsTmp =
	// Futures.allAsList(futuresList);

	// try {
	// List<GroundMotionSet> gmResultsList = gmResultsTmp.get();
	// System.out.println(gmResultsList.size());
	// // GroundMotionSet gms = gmResultsList.get(0);
	// // System.out.println(gms.means.size());
	// // System.out.println(gms.means);
	// // System.out.println(gms.sigmas.size());
	// // System.out.println(gms.sigmas);
	//
	// } catch (Exception e) {
	// e.printStackTrace();
	// }
	// // so now for each source we should have
	//
	// }

}
