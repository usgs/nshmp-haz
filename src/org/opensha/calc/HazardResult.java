package org.opensha.calc;

import static com.google.common.base.Preconditions.checkState;
import static org.opensha.data.ArrayXY_Sequence.copyOf;
import static org.opensha.eq.model.SourceType.CLUSTER;

import org.opensha.data.ArrayXY_Sequence;
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
	final ArrayXY_Sequence totalCurve;

	private HazardResult(SetMultimap<SourceType, HazardCurveSet> sourceSetMap,
		ArrayXY_Sequence totalCurve) {
		this.sourceSetMap = sourceSetMap;
		this.totalCurve = totalCurve;
	}

	@Override public String toString() {
		String LF = StandardSystemProperty.LINE_SEPARATOR.value();
		StringBuilder sb = new StringBuilder("HazardResult:");
		sb.append(LF);
		for (SourceType type : sourceSetMap.keySet()) {
			sb.append(type).append("SourceSet:").append(LF);
			for (HazardCurveSet curveSet : sourceSetMap.get(type)) {
				sb.append("  ").append(curveSet.sourceSet);
				// @formatter:off
				int used = (curveSet.sourceSet.type() == CLUSTER) ?
					curveSet.clusterGroundMotionsList.size() :
					curveSet.hazardGroundMotionsList.size();
				sb.append("Used: ").append(used);
				sb.append(LF);
				// @formatter:on

				if (curveSet.sourceSet.type() == CLUSTER) {
					// List<ClusterGroundMotions> cgmsList =
					// curveSet.clusterGroundMotionsList;
					// for (ClusterGroundMotions cgms : cgmsList) {
					// sb.append( "|--" + LF);
					// for (HazardGroundMotions hgms : cgms) {
					// sb.append("  |--" + LF);
					// for (TemporalGmmInput input : hgms.inputs) {
					// sb.append("    |--" + input + LF);
					// }
					// }
					// sb.append(LF);
					// }
					// sb.append(curveSet.clusterGroundMotionsList);

				} else {
//					 sb.append(curveSet.hazardGroundMotionsList);
				}
			}
		}
		return sb.toString();
	}

	/**
	 * The total mean hazard curve.
	 */
	public ArrayXY_Sequence curve() {
		return totalCurve;
	}
	
	static Builder builder(ArrayXY_Sequence modelCurve) {
		return new Builder(modelCurve);
	}

	static class Builder {

		private static final String ID = "HazardResult.Builder";
		private boolean built = false;

		private ImmutableSetMultimap.Builder<SourceType, HazardCurveSet> resultMapBuilder;
		private ArrayXY_Sequence totalCurve;

		private Builder(ArrayXY_Sequence modelCurve) {
			totalCurve = copyOf(modelCurve).clear();
			resultMapBuilder = ImmutableSetMultimap.builder();
		}

		Builder addCurveSet(HazardCurveSet curveSet) {
			resultMapBuilder.put(curveSet.sourceSet.type(), curveSet);
			totalCurve.add(curveSet.totalCurve);
			return this;
		}

		HazardResult build() {
			checkState(!built, "This %s instance has already been used", ID);
			return new HazardResult(resultMapBuilder.build(), totalCurve);
		}

	}
}
