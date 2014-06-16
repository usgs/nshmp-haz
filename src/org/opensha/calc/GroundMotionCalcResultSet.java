package org.opensha.calc;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.opensha.gmm.Gmm;
import org.opensha.gmm.GmmInput;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * Add comments here
 *
 * @author Peter Powers
 */
public final class GroundMotionCalcResultSet {

	List<GmmInput> inputs;
	Map<Gmm, List<Double>> means;
	Map<Gmm, List<Double>> stds;
	
	private GroundMotionCalcResultSet(Set<Gmm> gmms, int size) {
		inputs = Lists.newArrayListWithCapacity(size);
		means = Maps.newEnumMap(Gmm.class);
		stds = Maps.newEnumMap(Gmm.class);
		for (Gmm gmm : gmms) {
			List<Double> meanList = Lists.newArrayListWithCapacity(size);
			means.put(gmm, meanList);
			List<Double> stdList = Lists.newArrayListWithCapacity(size);
			means.put(gmm, stdList);
		}
	}
	
	static GroundMotionCalcResultSet create(Set<Gmm> gmms, int size) {
		return new GroundMotionCalcResultSet(gmms, size);
	}
	
	void add(GroundMotionCalcResult result) {
		inputs.add(result.input);
		for (Gmm gmm : result.gmMap.keySet()) {
			ScalarGroundMotion sgm = result.gmMap.get(gmm);
			means.get(gmm).add(sgm.mean());
			stds.get(gmm).add(sgm.stdDev());
		}
	}
}
