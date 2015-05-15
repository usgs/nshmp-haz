package org.opensha2.calc;

import static org.opensha2.calc.AsyncCalc.toClusterCurves;
import static org.opensha2.calc.AsyncCalc.toClusterGroundMotions;
import static org.opensha2.calc.AsyncCalc.toClusterInputs;
import static org.opensha2.calc.AsyncCalc.toGroundMotions;
import static org.opensha2.calc.AsyncCalc.toHazardCurveSet;
import static org.opensha2.calc.AsyncCalc.toHazardCurves;
import static org.opensha2.calc.AsyncCalc.toHazardResult;
import static org.opensha2.calc.AsyncCalc.toInputs;

import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.opensha2.data.ArrayXY_Sequence;
import org.opensha2.eq.model.ClusterSourceSet;
import org.opensha2.eq.model.HazardModel;
import org.opensha2.eq.model.Source;
import org.opensha2.eq.model.SourceSet;
import org.opensha2.eq.model.SourceType;
import org.opensha2.gmm.Imt;

import com.google.common.util.concurrent.ListenableFuture;

/**
 * Static probabilistic seismic hazard analysis calculators.
 * 
 * @author Peter Powers
 */
public class Calcs {

	private static final ExecutorService EX;

	static {
		int numProc = Runtime.getRuntime().availableProcessors();
		EX = Executors.newFixedThreadPool(numProc);
	}

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

				AsyncList<ClusterInputs> inputs = toClusterInputs(clusterSourceSet, site, EX);
				if (inputs.isEmpty()) continue; // all sources out of range

				AsyncList<ClusterGroundMotions> groundMotions = toClusterGroundMotions(inputs,
					clusterSourceSet, config.imts, EX);

				AsyncList<ClusterCurves> clusterCurves = toClusterCurves(groundMotions,
					modelCurves, config.exceedanceModel, config.truncationLevel, EX);

				ListenableFuture<HazardCurveSet> curveSet = toHazardCurveSet(clusterCurves,
					clusterSourceSet, modelCurves, EX);

				curveSetCollector.add(curveSet);

			} else {

				AsyncList<HazardInputs> inputs = toInputs(sourceSet, site, EX);
				if (inputs.isEmpty()) continue; // all sources out of range

				AsyncList<HazardGroundMotions> groundMotions = toGroundMotions(inputs, sourceSet,
					config.imts, EX);

				AsyncList<HazardCurves> hazardCurves = toHazardCurves(groundMotions, modelCurves,
					config.exceedanceModel, config.truncationLevel, EX);

				ListenableFuture<HazardCurveSet> curveSet = toHazardCurveSet(hazardCurves,
					sourceSet, modelCurves, EX);

				curveSetCollector.add(curveSet);

			}
		}

		ListenableFuture<HazardResult> futureResult = toHazardResult(curveSetCollector,
			modelCurves, EX);

		return futureResult.get();

	}

	public static void shutdown() {
		EX.shutdown();
	}

}
