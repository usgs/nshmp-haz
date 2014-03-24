package org.opensha.calc;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.opensha.gmm.GMM;
import org.opensha.gmm.GMM_Source;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * Add comments here
 *
 * @author Peter Powers
 */
public final class GroundMotionCalcResultSet {

	List<GMM_Source> inputs;
	Map<GMM, List<Double>> means;
	Map<GMM, List<Double>> stds;
	
	private GroundMotionCalcResultSet(Set<GMM> gmms, int size) {
		inputs = Lists.newArrayListWithCapacity(size);
		means = Maps.newEnumMap(GMM.class);
		stds = Maps.newEnumMap(GMM.class);
		for (GMM gmm : gmms) {
			List<Double> meanList = Lists.newArrayListWithCapacity(size);
			means.put(gmm, meanList);
			List<Double> stdList = Lists.newArrayListWithCapacity(size);
			means.put(gmm, stdList);
		}
	}
	
	static GroundMotionCalcResultSet create(Set<GMM> gmms, int size) {
		return new GroundMotionCalcResultSet(gmms, size);
	}
	
	void add(GroundMotionCalcResult result) {
		inputs.add(result.input);
		for (GMM gmm : result.gmMap.keySet()) {
			ScalarGroundMotion sgm = result.gmMap.get(gmm);
			means.get(gmm).add(sgm.mean());
			stds.get(gmm).add(sgm.stdDev());
		}
	}
}
