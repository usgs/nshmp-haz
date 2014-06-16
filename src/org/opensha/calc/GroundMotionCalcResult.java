package org.opensha.calc;

import java.util.Map;

import org.opensha.gmm.Gmm;
import org.opensha.gmm.GMM_Input;

/**
 * Add comments here
 * 
 * @author Peter Powers
 */
public final class GroundMotionCalcResult {

	final GMM_Input input;
	final Map<Gmm, ScalarGroundMotion> gmMap;

	private GroundMotionCalcResult(GMM_Input input,
		Map<Gmm, ScalarGroundMotion> gmMap) {
		this.input = input;
		this.gmMap = gmMap;
	}

	public static final GroundMotionCalcResult create(GMM_Input input,
			Map<Gmm, ScalarGroundMotion> gmMap) {
		return new GroundMotionCalcResult(input, gmMap);
	}
}
