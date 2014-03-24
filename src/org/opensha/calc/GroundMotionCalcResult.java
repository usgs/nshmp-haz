package org.opensha.calc;

import java.util.Map;

import org.opensha.gmm.GMM;
import org.opensha.gmm.GMM_Source;

/**
 * Add comments here
 * 
 * @author Peter Powers
 */
public final class GroundMotionCalcResult {

	final GMM_Source input;
	final Map<GMM, ScalarGroundMotion> gmMap;

	private GroundMotionCalcResult(GMM_Source input,
		Map<GMM, ScalarGroundMotion> gmMap) {
		this.input = input;
		this.gmMap = gmMap;
	}

	public static final GroundMotionCalcResult create(GMM_Source input,
			Map<GMM, ScalarGroundMotion> gmMap) {
		return new GroundMotionCalcResult(input, gmMap);
	}
}
