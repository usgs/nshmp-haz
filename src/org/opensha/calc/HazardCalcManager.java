package org.opensha.calc;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.opensha.calc.tasks.Task;
import org.opensha.calc.tasks.Transforms;
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
import org.opensha.gmm.IMT;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

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

	private ExecutorService ex;

	// calculations are processed by type fault > cluster? > gridded
	// within each type 'SourceSets' are processed
	//
	// e.g.

	private HazardCalcManager() {
		// init thread mgr
		int numProc = Runtime.getRuntime().availableProcessors();
		ex = Executors.newFixedThreadPool(numProc);
	}

	public static HazardCalcManager create() {
		HazardCalcManager hc = new HazardCalcManager();
		// hc.erfList = erfList;
		// hc.site = site;
		// hc.period = period;
		// hc.epiUncert = epiUncert;
		return hc;
	}

	// ERF (iterable, indexed? no indexing is messy)
	// -- SourceSets (collections of faults or grid sources; maybe these
	// should only be independent groups at initialization; however,
	// we will probably want to be able to disable certain sources
	// by type, regions, etc...[using Predicates]; this could (should?)
	// be done at the source level; however, filtering at SourceSet
	// level would possible be more efficient... e.g. wholesale
	// elimination of grid sources; would still have to drill down
	// to, say, selectively eliminate certain fault sources)
	// -- Sources (can have weights; iterable; indexed? could be)
	// -- Ruptures (can have weights)

	// source Mfds will all be scaled by local, source set, and any other
	// branching
	// weights when iterating.

	public void calc(Forecast forecast, Map<Gmm, Double> gmmWtMap, Site site, IMT imt)
			throws InterruptedException, ExecutionException {
		// TODO this wont work

		// each type of source should do the following, understanding that a
		// multi-threaded ExecutorService is available:

		// Step 1: filter sources > quick max distance
		// -- callable returns Source

		// Step 2: build rupture data container
		// -- callable returns List<GmmInput>
		// -- floating ruptures
		// -- multiple mags and Mfds
		// -- may want to handle special case of indexed faultSourceSet
		// however if aleatory uncertainty on mag is turned on then
		// a list for each would still be appropriate

		// Step 3: compute ground motions

		// Step 4: process ground motions into hazard

		// rupRef? mag rRup rJB

		// create new map of GMMs and instances -- only hazard curve building
		// and summation/assembly cares about the weights of each Gmm, to get
		// raw results (mean, std) all we need are instances

		Map<Gmm, GroundMotionModel> gmmMap = Maps.newEnumMap(Gmm.class);
		for (Gmm gmm : gmmWtMap.keySet()) {
			gmmMap.put(gmm, gmm.instance(imt));
		}
		// TODO single Gmm instances are cached and shared

		// List<Source> sources;
		for (SourceSet<? extends Source> srcSet : forecast) {
			SourceType type = srcSet.type();
			switch (type) {
				case FAULT:
					doFaultCalc((FaultSourceSet) srcSet, gmmMap, site);
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
	
	private GroundMotionCalcResultSet doCalc(SourceSet<Source> sources, Site site) {

		Iterable<Source> locIter = sources.locationIterable(site.loc);
		
		Task<Source, List<GmmInput>> inputs = new Task<Source, List<GmmInput>>(
				locIter, Transforms.sourceInitializerSupplier(site), ex);
		
//		Task<List<GmmInput>, GroundMotionCalcResult> gmResults =
//				new Task<List<GmmInput>, GroundMotionCalcResult>(
//						inputs, Transforms.sourceInitializerSupplier(site), ex)
		
		
		
		return null;
	}

	private GroundMotionCalcResultSet doFaultCalc(FaultSourceSet sources,
			Map<Gmm, GroundMotionModel> gmms, Site site) throws InterruptedException,
			ExecutionException {

		// Quick distance filter: TODO this will be done by the locationIterator
		// distance comes from the Gmm model

		// TODO the sourceSets hold their GMM_Manager (name??)

		// Currently set to use an ECS; if ecs.take().get() returns null,
		// source will be skipped. Don't like returning null; alternative may
		// be to run quick distance filter in a single thread using a
		// predicate or some such.

		double dist = 200.0; // this needs to come from gmm.xml

		// deprecate; use locationIterator
		CompletionService<FaultSource> qdCS = new ExecutorCompletionService<FaultSource>(ex);
		int qdCount = 0;
		for (FaultSource source : sources) {
			qdCS.submit(Transforms.newQuickDistanceFilter(source, site.loc, dist));
			qdCount++;
		}

		// Gmm input initializer:
		CompletionService<List<GmmInput>> gmSrcCS = new ExecutorCompletionService<List<GmmInput>>(ex);
		int gmSrcCount = 0;
		for (int i = 0; i < qdCount; i++) {
			FaultSource source = qdCS.take().get();
			if (source != null) {
				gmSrcCS.submit(Transforms.newFaultCalcInitializer(source, site));
				gmSrcCount++;
			}
		}

		// Ground motion calculation:
		CompletionService<GroundMotionCalcResult> gmCS = new ExecutorCompletionService<GroundMotionCalcResult>(
			ex);
		int gmCount = 0;
		for (int i = 0; i < gmSrcCount; i++) {
			List<GmmInput> inputs = gmSrcCS.take().get();
			for (GmmInput input : inputs) {
				gmCS.submit(Transforms.newGroundMotionCalc(gmms, input));
				gmCount++;
			}
		}

		// Final results assembly:
		GroundMotionCalcResultSet results = GroundMotionCalcResultSet.create(gmms.keySet(), gmCount);
		for (int i = 0; i < gmCount; i++) {
			GroundMotionCalcResult result = gmCS.take().get();
			results.add(result);
		}

		return results;
	}

}
