package org.opensha.gmm;

import java.util.Map;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

/**
 * Add comments here
 * 
 * Instances are created by GMM_Parser
 *
 * @author Peter Powers
 */
public class GMM_Calculator {

	private final double maxDistance;
	private final Map<GMM, Double> gmmMap;
//	private final Map<GMM, GroundMotionModel> instanceMap;
	
	GMM_Calculator(Map<GMM, Double> gmmMap, double maxDistance) {
		// TODO consider using guavas private pass through ImmutableEnumMap
		// ImmutableMap.copyOf(gmmMap)
		this.gmmMap = ImmutableMap.copyOf(gmmMap);
		this.maxDistance = maxDistance;
		
		// TODO need IMT to fetch instances
//		instanceMap = Maps.newEnumMap(GMM.class);
//		for (GMM gmm : gmmMap) {
//			instanceMap.put(gmm, gmm.instance(imt));
//		}
	}
	/**
	 * Returns the maximum distance for which this calculator is applicable
	 * @return
	 */
	public double maxDistance() {
		return maxDistance;
	}
	
	
}
