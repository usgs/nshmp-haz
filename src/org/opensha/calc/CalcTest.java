package org.opensha.calc;

import static org.opensha.calc.AsyncCalc.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.opensha.data.ArrayXY_Sequence;
import org.opensha.eq.model.ClusterSourceSet;
import org.opensha.eq.model.HazardModel;
import org.opensha.eq.model.Source;
import org.opensha.eq.model.SourceSet;
import org.opensha.eq.model.SourceType;
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
class CalcTest {


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


//	private static String testModel = "../nshmp-model-dev/models/2008/Western US test";
	private static String testModel = "../nshmp-model-dev/models/2008/Western US.zip";
//	private static String testModel = "/Users/pmpowers/projects/git/nshmp-model-dev/models/2008/Western US.zip";
//	private static String testModel = "../nshmp-model-dev/models/2008/Central & Eastern US";

	// @formatter: off
	
	// private static String testModel =
	// "../nshmp-model-dev/models/2008/Western US";
	//
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		HazardModel model = testLoad();

//		runSites(model, Imt.PGA);
		runSites(model, Imt.SA0P2);
//		runSites(model, Imt.SA1P0);
//		runSites(model, Imt.SA2P0);
		
		// try {
		// AsyncCalc hcm = AsyncCalc.create();
		// String path = "tmp/NSHMP08-noRedux/California/Fault/bFault.gr.xml";
		// HazardModel f = HazardModel.fromSingleSourceSet(path);
		// Site s = Site.create(Location.create(34.05, -118.25));
		// Imt imt = Imt.PGA;
		// hcm.calc(f, gmmMapWUS(), s, imt);
		// } catch (Exception e) {
		// e.printStackTrace();
		// }
		 
		System.exit(0);

	}
	
	static void runSites(HazardModel model, Imt imt) {

//		Site site = Site.create(NehrpTestCity.MEMPHIS.location());
		Site site = Site.create(NehrpTestCity.LOS_ANGELES.location());
		HazardResult result = testCalc(model, site, imt);
		System.out.println(result);
		System.out.println(result.totalCurve);
		

//		// oakland
//		site = Site.create(Location.create(37.8044, -122.2708));
//		result = testCalc(model, site, imt);
//		System.out.println(result);
//		
//		// sacramento
//		site = Site.create(Location.create(38.5556, -121.4689));
//		result = testCalc(model, site, imt);
//		System.out.println(result);
//
//		// back to la
//		site = Site.create(Location.create(34.05, -118.25));
//		result = testCalc(model, site, imt);
//		System.out.println(result);

	}

	public static HazardModel testLoad() {
		try {
			return HazardModel.load(testModel, "HazardModel load test");
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
	
	public static HazardResult testCalc(HazardModel model, Site site, Imt imt) {

		ArrayXY_Sequence modelCurve = ArrayXY_Sequence.create(Utils.NSHM_0P2_IMLS, null);

		try {
			// hcm.calc(model, s, imt);
			Stopwatch sw = Stopwatch.createStarted();
			
			// TODO need to check which SourceSets return no results

			AsyncList<HazardCurveSet> curveSetCollector = AsyncList.createWithCapacity(model.size());
			
			for (SourceSet<? extends Source> sourceSet : model) {

				if (sourceSet.type() == SourceType.CLUSTER) {
					
					ClusterSourceSet clusterSourceSet = (ClusterSourceSet) sourceSet;

					AsyncList<ClusterInputs> inputs = toClusterInputs(clusterSourceSet, site);
					if (inputs.isEmpty()) continue; // all sources out of range

					AsyncList<ClusterGroundMotions> groundMotions = toClusterGroundMotions(inputs, clusterSourceSet, imt);
					
					AsyncList<ClusterCurves> clusterCurves = toClusterCurves(groundMotions, modelCurve);
					
					ListenableFuture<HazardCurveSet> curveSet = toHazardCurveSet(clusterCurves, clusterSourceSet, modelCurve);
					
					curveSetCollector.add(curveSet);
					
				} else {

					AsyncList<HazardInputs> inputs = toInputs(sourceSet, site);
					if (inputs.isEmpty()) continue; // all sources out of range
					
					AsyncList<HazardGroundMotions> groundMotions = toGroundMotions(inputs, sourceSet, imt);
					
					AsyncList<HazardCurves> hazardCurves = toHazardCurves(groundMotions, modelCurve);
					
					ListenableFuture<HazardCurveSet> curveSet = toHazardCurveSet(hazardCurves, sourceSet, modelCurve);
					
					curveSetCollector.add(curveSet);
					
				}
				
			}

			ListenableFuture<HazardResult> futureResult = toHazardResult(curveSetCollector, modelCurve);
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
