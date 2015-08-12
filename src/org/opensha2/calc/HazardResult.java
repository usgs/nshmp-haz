package org.opensha2.calc;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.opensha2.data.ArrayXY_Sequence.copyOf;
import static org.opensha2.eq.model.SourceType.CLUSTER;
import static org.opensha2.eq.model.SourceType.SYSTEM;

import java.util.EnumMap;
import java.util.Map;
import java.util.Map.Entry;

import org.opensha2.data.ArrayXY_Sequence;
import org.opensha2.eq.model.SourceType;
import org.opensha2.gmm.Imt;

import com.google.common.base.StandardSystemProperty;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.SetMultimap;

/**
 * The result of a hazard calculation. This container class is public for
 * reference by external packages but is not directly modifiable, nor it's field
 * accessible. The {@link Results} class provides HazardResult exporting and
 * processing utilities.
 * 
 * @author Peter Powers
 * @see Results
 */
public final class HazardResult {

	final SetMultimap<SourceType, HazardCurveSet> sourceSetMap;
	final Map<Imt, ArrayXY_Sequence> totalCurves;
	final Site site;

	private HazardResult(SetMultimap<SourceType, HazardCurveSet> sourceSetMap,
			Map<Imt, ArrayXY_Sequence> totalCurves, Site site) {
		this.sourceSetMap = sourceSetMap;
		this.totalCurves = totalCurves;
		this.site = site;
	}

	@Override public String toString() {
		String LF = StandardSystemProperty.LINE_SEPARATOR.value();
		StringBuilder sb = new StringBuilder("HazardResult:");
		sb.append(LF);
		for (SourceType type : sourceSetMap.keySet()) {
			sb.append(type).append("SourceSet:").append(LF);
			for (HazardCurveSet curveSet : sourceSetMap.get(type)) {
				sb.append("  ").append(curveSet.sourceSet);
				int used = (type == CLUSTER) ? curveSet.clusterGroundMotionsList.size() :
					(type == SYSTEM) ? curveSet.hazardGroundMotionsList.get(0).inputs.size() :
						curveSet.hazardGroundMotionsList.size();

				sb.append("Used: ").append(used);
				sb.append(LF);

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

	static Builder builder(Map<Imt, ArrayXY_Sequence> modelCurves, Site site) {
		return new Builder(modelCurves, site);
	}

	static class Builder {

		private static final String ID = "HazardResult.Builder";
		private boolean built = false;

		private Site site;
		private ImmutableSetMultimap.Builder<SourceType, HazardCurveSet> resultMapBuilder;
		private Map<Imt, ArrayXY_Sequence> totalCurves;

		private Builder(Map<Imt, ArrayXY_Sequence> modelCurves, Site site) {
			this.site = checkNotNull(site);
			checkNotNull(modelCurves);
			totalCurves = new EnumMap<>(Imt.class);
			for (Entry<Imt, ArrayXY_Sequence> entry : modelCurves.entrySet()) {
				totalCurves.put(entry.getKey(), copyOf(entry.getValue()).clear());
			}
			resultMapBuilder = ImmutableSetMultimap.builder();
		}

		Builder site(Site site) {
			checkState(this.site == null, "%s site already set", ID);
			checkNotNull(site);
			return this;
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
			return new HazardResult(resultMapBuilder.build(), totalCurves, site);
		}

	}

}
