package org.opensha.calc.tasks;

import java.util.Map;
import java.util.concurrent.Callable;

import org.opensha.calc.GroundMotionCalcResult;
import org.opensha.calc.ScalarGroundMotion;
import org.opensha.gmm.Gmm;
import org.opensha.gmm.GmmInput;
import org.opensha.gmm.GroundMotionModel;

import com.google.common.collect.Maps;

/**
 * Passes {@code GmmInput} data to {@code Gmm}s to compute ground motions.
 * @author Peter Powers
 */
final class GroundMotionCalc implements Callable<GroundMotionCalcResult> {

	private final Map<Gmm, GroundMotionModel> gmmInstanceMap;
	private final GmmInput input;

	GroundMotionCalc(Map<Gmm, GroundMotionModel> gmmInstanceMap, GmmInput input) {
		this.gmmInstanceMap = gmmInstanceMap;
		this.input = input;
	}

	@Override
	public GroundMotionCalcResult call() throws Exception {
		Map<Gmm, ScalarGroundMotion> gmMap = Maps.newEnumMap(Gmm.class);
		for (Gmm gmm : gmmInstanceMap.keySet()) {
			gmMap.put(gmm, gmmInstanceMap.get(gmm).calc(input));
		}
		return GroundMotionCalcResult.create(input, gmMap);
	}

}
