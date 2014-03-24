package org.opensha.calc.tasks;

import java.util.Map;
import java.util.concurrent.Callable;

import org.opensha.calc.GroundMotionCalcResult;
import org.opensha.calc.ScalarGroundMotion;
import org.opensha.gmm.GMM;
import org.opensha.gmm.GMM_Source;
import org.opensha.gmm.GroundMotionModel;

import com.google.common.collect.Maps;

/**
 * Passes {@code GMM_Source} data to {@code GMM}s to compute ground motions.
 * @author Peter Powers
 */
final class GroundMotionCalc implements Callable<GroundMotionCalcResult> {

	private final Map<GMM, GroundMotionModel> gmmInstanceMap;
	private final GMM_Source input;

	GroundMotionCalc(Map<GMM, GroundMotionModel> gmmInstanceMap, GMM_Source input) {
		this.gmmInstanceMap = gmmInstanceMap;
		this.input = input;
	}

	@Override
	public GroundMotionCalcResult call() throws Exception {
		Map<GMM, ScalarGroundMotion> gmMap = Maps.newEnumMap(GMM.class);
		for (GMM gmm : gmmInstanceMap.keySet()) {
			gmMap.put(gmm, gmmInstanceMap.get(gmm).calc(input));
		}
		return GroundMotionCalcResult.create(input, gmMap);
	}

}
