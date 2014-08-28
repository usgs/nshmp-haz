package org.opensha.calc;

import static com.google.common.base.Preconditions.checkState;
import static org.opensha.eq.model.SourceType.CLUSTER;

import org.opensha.eq.model.SourceType;

import com.google.common.base.StandardSystemProperty;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.SetMultimap;

/**
 * The result of a hazard calculation.
 * 
 * @author Peter Powers
 */
public final class HazardResult {

	final SetMultimap<SourceType, HazardCurveSet> sourceSetMap;

	HazardResult(SetMultimap<SourceType, HazardCurveSet> sourceSetMap) {
		this.sourceSetMap = sourceSetMap;
	}

	static Builder builder() {
		return new Builder();
	}

	@Override public String toString() {
		String LF = StandardSystemProperty.LINE_SEPARATOR.value();
		StringBuilder sb = new StringBuilder("HazardResult:");
		sb.append(LF);
		for (SourceType type : sourceSetMap.keySet()) {
			sb.append(type).append("SourceSet:").append(LF);
			for (HazardCurveSet curveSet : sourceSetMap.get(type)) {
				sb.append("  ").append(curveSet.sourceSet);
				int used = (curveSet.sourceSet.type() == CLUSTER)
					? curveSet.clusterGroundMotionsList.size() : curveSet.groundMotionsList.size();
				sb.append("Used: ").append(used);
				sb.append(LF);
				
				if (curveSet.sourceSet.type() == CLUSTER) {
					
				} else {
					sb.append(curveSet.gmmCurveMap);
				}
			}
		}
		return sb.toString();
	}

	static class Builder {

		private static final String ID = "HazardResult.Builder";
		private boolean built = false;

		private ImmutableSetMultimap.Builder<SourceType, HazardCurveSet> resultMapBuilder;

		private Builder() {
			resultMapBuilder = ImmutableSetMultimap.builder();
		}

		Builder addCurveSet(HazardCurveSet curveSet) {
			resultMapBuilder.put(curveSet.sourceSet.type(), curveSet);
			return this;
		}

		HazardResult build() {
			checkState(!built, "This %s instance has already been used", ID);
			return new HazardResult(resultMapBuilder.build());
		}

	}
}
