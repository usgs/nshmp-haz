package org.opensha.calc;

import java.util.Map;

import org.opensha.gmm.Gmm;
import org.opensha.gmm.GmmInput;

/**
 * Add comments here
 * 
 * @author Peter Powers
 */
public final class GroundMotionCalcResult {

	final GmmInput input;
	final Map<Gmm, ScalarGroundMotion> gmMap;

	private GroundMotionCalcResult(GmmInput input,
		Map<Gmm, ScalarGroundMotion> gmMap) {
		this.input = input;
		this.gmMap = gmMap;
	}

	public static final GroundMotionCalcResult create(GmmInput input,
			Map<Gmm, ScalarGroundMotion> gmMap) {
		return new GroundMotionCalcResult(input, gmMap);
	}
}
