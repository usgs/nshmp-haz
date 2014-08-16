package org.opensha.calc;

import static org.opensha.calc.Calculators.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.opensha.data.ArrayXY_Sequence;
import org.opensha.eq.forecast.ClusterSourceSet;
import org.opensha.eq.forecast.Forecast;
import org.opensha.eq.forecast.Loader;
import org.opensha.eq.forecast.Source;
import org.opensha.eq.forecast.SourceSet;
import org.opensha.eq.forecast.SourceType;
import org.opensha.geo.Location;
import org.opensha.gmm.Gmm;
import org.opensha.gmm.Imt;

import com.google.common.base.CaseFormat;
import com.google.common.base.Stopwatch;
import com.google.common.base.Supplier;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimaps;
import com.google.common.primitives.Doubles;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

/**
 * Add comments here
 * 
 * @author Peter Powers
 */
public class CalcTest {


	// TODO where/how to apply CEUS clamps

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


//	private static String testModel = "../nshmp-forecast-dev/forecasts/Test";
	private static String testModel = "../nshmp-forecast-dev/forecasts/2008/Western US";

	// @formatter: off
	
	// private static String testModel =
	// "../nshmp-forecast-dev/forecasts/2008/Western US";
	//
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		Forecast forecast = testLoad();
		Site site = Site.create(Location.create(34.05, -118.25));
		Imt imt = Imt.PGA;

		HazardResult result = testCalc(forecast, site, imt);
		System.out.println(result.sourceSetMap);
		System.out.println(result);

		// oakland
		site = Site.create(Location.create(37.8044, -122.2708));
		result = testCalc(forecast, site, imt);
		
		// sacramento
		site = Site.create(Location.create(38.5556, -121.4689));
		result = testCalc(forecast, site, imt);

		// back to la
		site = Site.create(Location.create(34.05, -118.25));
		result = testCalc(forecast, site, imt);
		
		// try {
		// Calculators hcm = Calculators.create();
		// String path = "tmp/NSHMP08-noRedux/California/Fault/bFault.gr.xml";
		// Forecast f = Forecast.fromSingleSourceSet(path);
		// Site s = Site.create(Location.create(34.05, -118.25));
		// Imt imt = Imt.PGA;
		// hcm.calc(f, gmmMapWUS(), s, imt);
		// } catch (Exception e) {
		// e.printStackTrace();
		// }
		 
		System.exit(0);

	}

	public static Forecast testLoad() {
		try {
			return Loader.load(testModel, "Forecast load test");
		} catch (Exception e) {
			System.err.println("** Exiting **");
			System.err.println();
			System.err.println();
			System.err.println("Original stack...");
			e.printStackTrace();
			return null;
		}
	}

	// TODO how are empty results being handled ??
	
	public static HazardResult testCalc(Forecast forecast, Site site, Imt imt) {

		ArrayXY_Sequence model = ArrayXY_Sequence.create(Utils.NSHM_IMLS, null);

		try {
			// hcm.calc(forecast, s, imt);
			Stopwatch sw = Stopwatch.createStarted();
			
			// TODO need to check which SourceSets return no results

			AsyncList<HazardCurveSet> curveSetCollector = AsyncList.createWithCapacity(forecast.size());
			
			for (SourceSet<? extends Source> sourceSet : forecast) {

				if (sourceSet.type() == SourceType.CLUSTER) {

//					ClusterSourceSet clusterSourceSet = (ClusterSourceSet) sourceSet;
//
//					// List (outer) --> clusters (geometry variants)
//					// List (inner) --> faults (sections)
//					// HazardGroundMotions --> magnitude variants
//
//					ListenableFuture<List<List<HazardGroundMotions>>> groundMotions = toClusterGroundMotions(clusterSourceSet, site, imt);
//					
//					List<List<HazardGroundMotions>> gmsLists = toClusterGroundMotions(clusterSourceSet, site, imt).get();
//					
//					for (List<HazardGroundMotions> groundMotionList : gmsLists) {
//						// collector.addAll(gmsList);
//						// for (HazardGroundMotions gms : gmsList) {
//						// System.out.println(clusters.name() + ": " + gms);
//						// }
//
//						Map<Gmm, ArrayXY_Sequence> curves = toClusterCurve(groundMotionList, model).get();
//						// System.out.println(curves);
//
//						for (Entry<Gmm, ArrayXY_Sequence> entry : curves.entrySet()) {
//							System.out.println(entry.getKey().name());
//							System.out.println(entry.getValue());
//						}
//
//					}
//					
//					// each cluster 
//					System.out.println(gmsLists.size());
				} else {

					AsyncList<HazardInputs> inputs = toInputs(sourceSet, site);
//					if (inputs.isEmpty()) continue; // all sources out of range
					
					AsyncList<HazardGroundMotions> groundMotions = toGroundMotions(inputs, sourceSet, imt);
					
					AsyncList<HazardCurves> hazardCurves = toHazardCurves(groundMotions, model);
					
					ListenableFuture<HazardCurveSet> curveSet = toHazardCurveSet(hazardCurves, sourceSet, model);
					
					curveSetCollector.add(curveSet);
					
					// List<HazardGroundMotions> gmsList =
					// hcm.toGroundMotions4(sources, s, imt).get();
					// collector.addAll(gmsList);
//					hcm.toGroundMotions(hcm.toInputs(sourceSet, s));
					
//					List<ListenableFuture<HazardGroundMotions>> gmsFuturesList = hcm.toGroundMotions1(sourceSet, s, imt);

//					List<ListenableFuture<Map<Gmm, ArrayXY_Sequence>>> curvesFutures = hcm.toHazardCurves(gmsFuturesList);
//
//					List<Map<Gmm, ArrayXY_Sequence>> curveSetList = Futures.allAsList(curvesFutures).get();
//
//					
//					for (Map<Gmm, ArrayXY_Sequence> curveSet : curveSetList) {
//
//						for (Entry<Gmm, ArrayXY_Sequence> entry : curveSet.entrySet()) {
//							System.out.println(entry.getKey().name());
//							System.out.println(entry.getValue());
//						}
//					}

					// List<HazardGroundMotions> gmsList =
					// Futures.allAsList(r1).get();
					// for (HazardGroundMotions gms : gmsList) {
					// System.out.println(gms);
				}
				
			}
			ListenableFuture<HazardResult> futureResult = toHazardResult(curveSetCollector);
			System.out.println(sw.elapsed(TimeUnit.MILLISECONDS));

			HazardResult result = futureResult.get();
			System.out.println(sw.stop().elapsed(TimeUnit.MILLISECONDS));
			
			return result;

		} catch (Exception e) {
			System.err.println("** Exiting **");
			System.err.println();
			System.err.println();
			System.err.println("Original stack...");
			e.printStackTrace();
		}
		return null;
		
	}

}
