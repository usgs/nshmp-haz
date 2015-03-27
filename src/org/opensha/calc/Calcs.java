package org.opensha.calc;

import static org.opensha.calc.AsyncCalc.toClusterCurves;
import static org.opensha.calc.AsyncCalc.toClusterGroundMotions;
import static org.opensha.calc.AsyncCalc.toClusterInputs;
import static org.opensha.calc.AsyncCalc.toGroundMotions;
import static org.opensha.calc.AsyncCalc.toHazardCurveSet;
import static org.opensha.calc.AsyncCalc.toHazardCurves;
import static org.opensha.calc.AsyncCalc.toHazardResult;
import static org.opensha.calc.AsyncCalc.toInputs;

import java.util.Map;
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

	// TODO (below) situations where multiple Imts are being processed, we
	// should short
	// circuit toInputs() as this step is independent of Imt

	/**
	 * Compute a hazard curve.
	 * 
	 * @param model to use
	 * @param config
	 * @param site of interest
	 * @throws InterruptedException
	 * @throws ExecutionException
	 */
	public static HazardResult hazardCurve(HazardModel model, CalcConfig config, Site site)
			throws InterruptedException, ExecutionException {

		AsyncList<HazardCurveSet> curveSetCollector = AsyncList.createWithCapacity(model.size());
		Map<Imt, ArrayXY_Sequence> modelCurves = config.logModelCurves();

		for (SourceSet<? extends Source> sourceSet : model) {

			if (sourceSet.type() == SourceType.CLUSTER) {

				ClusterSourceSet clusterSourceSet = (ClusterSourceSet) sourceSet;

				AsyncList<ClusterInputs> inputs = toClusterInputs(clusterSourceSet, site);
				if (inputs.isEmpty()) continue; // all sources out of range

				AsyncList<ClusterGroundMotions> groundMotions = toClusterGroundMotions(inputs,
					clusterSourceSet, config.imts);

				AsyncList<ClusterCurves> clusterCurves = toClusterCurves(groundMotions,
					modelCurves, config.sigmaModel, config.truncationLevel);

				ListenableFuture<HazardCurveSet> curveSet = toHazardCurveSet(clusterCurves,
					clusterSourceSet, modelCurves);

				curveSetCollector.add(curveSet);

			} else {

				AsyncList<HazardInputs> inputs = toInputs(sourceSet, site);
				if (inputs.isEmpty()) continue; // all sources out of range

				AsyncList<HazardGroundMotions> groundMotions = toGroundMotions(inputs, sourceSet,
					config.imts);

				AsyncList<HazardCurves> hazardCurves = toHazardCurves(groundMotions, modelCurves,
					config.sigmaModel, config.truncationLevel);

				ListenableFuture<HazardCurveSet> curveSet = toHazardCurveSet(hazardCurves,
					sourceSet, modelCurves);

				curveSetCollector.add(curveSet);

			}
		}
		
		ListenableFuture<HazardResult> futureResult = toHazardResult(curveSetCollector, modelCurves);
		
		return futureResult.get();

		// System.out.println(sw.stop().elapsed(TimeUnit.MILLISECONDS));
		//
		// return result;

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
