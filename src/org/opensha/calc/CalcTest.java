package org.opensha.calc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.opensha.eq.forecast.ClusterSourceSet;
import org.opensha.eq.forecast.Forecast;
import org.opensha.eq.forecast.Loader;
import org.opensha.eq.forecast.Source;
import org.opensha.eq.forecast.SourceSet;
import org.opensha.eq.forecast.SourceType;
import org.opensha.geo.Location;
import org.opensha.gmm.Gmm;
import org.opensha.gmm.Imt;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

/**
 * Add comments here
 * 
 * @author Peter Powers
 */
public class CalcTest {

	private static String testModel = "../nshmp-forecast-dev/forecasts/Test";
//	private static String testModel = "../nshmp-forecast-dev/forecasts/2008/Western US";
//
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		Forecast forecast = testLoad();
		testCalc(forecast);

		// try {
		// HazardCalcManager hcm = HazardCalcManager.create();
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

	public static void testCalc(Forecast forecast) {
		try {
			Site s = Site.create(Location.create(34.05, -118.25));
			Imt imt = Imt.PGA;
			HazardCalcManager hcm = HazardCalcManager.create();
			// hcm.calc(forecast, s, imt);
			Stopwatch sw = Stopwatch.createStarted();
			
			List<GroundMotionSet> collector = new ArrayList<>();
			for (SourceSet<? extends Source> sources : forecast) {
				if (sources.type() == SourceType.CLUSTER) {
					
					ClusterSourceSet clusters = (ClusterSourceSet) sources;
					List<List<GroundMotionSet>> gmsLists = hcm.toClusterGroundMotions(clusters, s, imt).get();
					for (List<GroundMotionSet> gmsList : gmsLists) {
						collector.addAll(gmsList);
//						for (GroundMotionSet gms : gmsList) {
//							System.out.println(clusters.name() + ": " + gms);
//						}
					}
				} else {
				List<GroundMotionSet> gmsList = hcm.toGroundMotions4(sources, s, imt).get();
				collector.addAll(gmsList);
				
//				List<GroundMotionSet> gmsList = Futures.allAsList(r1).get();
//				for (GroundMotionSet gms : gmsList) {
//					System.out.println(gms);
				}
			}
			System.out.println(sw.stop().elapsed(TimeUnit.MILLISECONDS));

		} catch (Exception e) {
			System.err.println("** Exiting **");
			System.err.println();
			System.err.println();
			System.err.println("Original stack...");
			e.printStackTrace();
			// return null;
		}
	}

}
