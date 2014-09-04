package org.opensha.calc;

import static org.opensha.calc.AsyncCalc.toClusterCurves;
import static org.opensha.calc.AsyncCalc.toClusterGroundMotions;
import static org.opensha.calc.AsyncCalc.toClusterInputs;
import static org.opensha.calc.AsyncCalc.toGroundMotions;
import static org.opensha.calc.AsyncCalc.toHazardCurveSet;
import static org.opensha.calc.AsyncCalc.toHazardCurves;
import static org.opensha.calc.AsyncCalc.toHazardResult;
import static org.opensha.calc.AsyncCalc.toInputs;

import java.util.concurrent.ExecutionException;

import org.opensha.data.ArrayXY_Sequence;
import org.opensha.eq.model.ClusterSourceSet;
import org.opensha.eq.model.HazardModel;
import org.opensha.eq.model.Source;
import org.opensha.eq.model.SourceSet;
import org.opensha.eq.model.SourceType;
import org.opensha.gmm.Imt;

import com.google.common.util.concurrent.ListenableFuture;

/**
 * Static probabilistic seismic hazard analysis calculators.
 * 
 * @author Peter Powers
 */
public class Calcs {

	/**
	 * Compute a hazard curve.
	 * 
	 * @param model to use
	 * @param imt intensity measure type
	 * @param site of interest
	 * @param imls sequence of intensity measure levels (x-values) to populate
	 * @throws InterruptedException
	 * @throws ExecutionException
	 */
	public static HazardResult hazardCurve(HazardModel model, Imt imt, Site site,
			ArrayXY_Sequence imls) throws InterruptedException, ExecutionException {

		AsyncList<HazardCurveSet> curveSetCollector = AsyncList.createWithCapacity(model.size());

		for (SourceSet<? extends Source> sourceSet : model) {

			if (sourceSet.type() == SourceType.CLUSTER) {

				ClusterSourceSet clusterSourceSet = (ClusterSourceSet) sourceSet;

				AsyncList<ClusterInputs> inputs = toClusterInputs(clusterSourceSet, site);
				if (inputs.isEmpty()) continue; // all sources out of range

				AsyncList<ClusterGroundMotions> groundMotions = toClusterGroundMotions(inputs,
					clusterSourceSet, imt);

				AsyncList<ClusterCurves> clusterCurves = toClusterCurves(groundMotions, imls);

				ListenableFuture<HazardCurveSet> curveSet = toHazardCurveSet(clusterCurves,
					clusterSourceSet, imls);

				curveSetCollector.add(curveSet);

			} else {

				AsyncList<HazardInputs> inputs = toInputs(sourceSet, site);
				if (inputs.isEmpty()) continue; // all sources out of range

				AsyncList<HazardGroundMotions> groundMotions = toGroundMotions(inputs, sourceSet,
					imt);

				AsyncList<HazardCurves> hazardCurves = toHazardCurves(groundMotions, imls);

				ListenableFuture<HazardCurveSet> curveSet = toHazardCurveSet(hazardCurves,
					sourceSet, imls);

				curveSetCollector.add(curveSet);

			}
		}

		ListenableFuture<HazardResult> futureResult = toHazardResult(curveSetCollector, imls);

		return futureResult.get();

//		System.out.println(sw.stop().elapsed(TimeUnit.MILLISECONDS));
//
//		return result;

		// TODO move timers
		// } catch (Exception e) {
		// System.err.println("** Exiting **");
		// System.err.println();
		// System.err.println();
		// System.err.println("Original stack...");
		// e.printStackTrace();
		// }
		// return null;

	}

}
