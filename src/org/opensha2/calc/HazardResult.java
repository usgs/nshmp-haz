package org.opensha2.calc;

import static com.google.common.base.Preconditions.checkState;
import static org.opensha2.data.ArrayXY_Sequence.copyOf;
import static org.opensha2.eq.model.SourceType.CLUSTER;

import java.util.EnumMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.opensha2.data.ArrayXY_Sequence;
import org.opensha2.eq.model.SourceType;
import org.opensha2.gmm.Imt;

import com.google.common.base.StandardSystemProperty;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.SetMultimap;

/**
 * The result of a hazard calculation.
 * 
 * @author Peter Powers
 */
public final class HazardResult {

	final SetMultimap<SourceType, HazardCurveSet> sourceSetMap;
	final Map<Imt, ArrayXY_Sequence> totalCurves;

	private HazardResult(SetMultimap<SourceType, HazardCurveSet> sourceSetMap,
			Map<Imt, ArrayXY_Sequence> totalCurves) {
		this.sourceSetMap = sourceSetMap;
		this.totalCurves = totalCurves;
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
					// TODO ??
					// List<ClusterGroundMotions> cgmsList =
					// curveSet.clusterGroundMotionsList;
					// for (ClusterGroundMotions cgms : cgmsList) {
					// sb.append( "|--" + LF);
					// for (HazardGroundMotions hgms : cgms) {
					// sb.append("  |--" + LF);
					// for (HazardInput input : hgms.inputs) {
					// sb.append("    |--" + input + LF);
					// }
					// }
					// sb.append(LF);
					// }
					// sb.append(curveSet.clusterGroundMotionsList);

				} else {
					// sb.append(curveSet.hazardGroundMotionsList);
				}
			}
		}
		return sb.toString();
	}

	/**
	 * The total mean hazard curve.
	 */
	public Map<Imt, ArrayXY_Sequence> curves() {
		return totalCurves;
	}

	static Builder builder(Map<Imt, ArrayXY_Sequence> modelCurves) {
		return new Builder(modelCurves);
	}

	static class Builder {

		private static final String ID = "HazardResult.Builder";
		private boolean built = false;

		private ImmutableSetMultimap.Builder<SourceType, HazardCurveSet> resultMapBuilder;
		private Map<Imt, ArrayXY_Sequence> totalCurves;

		private Builder(Map<Imt, ArrayXY_Sequence> modelCurves) {
			totalCurves = new EnumMap<>(Imt.class);
			for (Entry<Imt, ArrayXY_Sequence> entry : modelCurves.entrySet()) {
				totalCurves.put(entry.getKey(), copyOf(entry.getValue()).clear());
			}
			resultMapBuilder = ImmutableSetMultimap.builder();
		}

		Builder addCurveSet(HazardCurveSet curveSet) {
			resultMapBuilder.put(curveSet.sourceSet.type(), curveSet);
			for (Entry<Imt, ArrayXY_Sequence> entry : curveSet.totalCurves.entrySet()) {
				totalCurves.get(entry.getKey()).add(entry.getValue());
			}
			return this;
		}

		HazardResult build() {
			// TODO totalCurves currently mutable; use ImmutableEnumMap
			// instead??
			checkState(!built, "This %s instance has already been used", ID);
			return new HazardResult(resultMapBuilder.build(), totalCurves);
		}

	}

	// TODO relocate
	public static Map<Imt, Map<SourceType, ArrayXY_Sequence>> totalsByType(HazardResult result) {

		ImmutableMap.Builder<Imt, Map<SourceType, ArrayXY_Sequence>> imtMapBuilder =
			ImmutableMap.builder();

		Map<Imt, ArrayXY_Sequence> curves = result.curves();
		Set<Imt> imts = curves.keySet();

		for (Imt imt : imts) {

			ArrayXY_Sequence modelCurve = copyOf(curves.get(imt)).clear();
			Map<SourceType, ArrayXY_Sequence> typeCurves = new EnumMap<>(SourceType.class);

			Multimap<SourceType, HazardCurveSet> curveSets = result.sourceSetMap;
			for (SourceType type : curveSets.keySet()) {
				ArrayXY_Sequence typeCurve = copyOf(modelCurve);
				for (HazardCurveSet curveSet : curveSets.get(type)) {
					ArrayXY_Sequence curve = curveSet.totalCurves.get(imt);
					typeCurve.add(curve);
				}
				typeCurves.put(type, typeCurve);
			}
			imtMapBuilder.put(imt, Maps.immutableEnumMap(typeCurves));
		}

		return imtMapBuilder.build();
	}
}
