package org.opensha2.calc;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.opensha2.data.XySequence.emptyCopyOf;
import static org.opensha2.eq.model.SourceType.CLUSTER;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Map.Entry;

import org.opensha2.data.XySequence;
import org.opensha2.eq.model.GridSourceSet;
import org.opensha2.eq.model.HazardModel;
import org.opensha2.eq.model.Source;
import org.opensha2.eq.model.SourceSet;
import org.opensha2.eq.model.SourceType;
import org.opensha2.gmm.Imt;

import com.google.common.base.StandardSystemProperty;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Maps;
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

	// TODO refactor to just Hazard because that's what it is
	
	final SetMultimap<SourceType, HazardCurveSet> sourceSetMap;
	final Map<Imt, XySequence> totalCurves;
	final HazardModel model;
	final Site site;
	final CalcConfig config;

	private HazardResult(
			SetMultimap<SourceType, HazardCurveSet> sourceSetMap,
			Map<Imt, XySequence> totalCurves,
			HazardModel model,
			Site site,
			CalcConfig config) {

		this.sourceSetMap = sourceSetMap;
		this.totalCurves = totalCurves;
		this.model = model;
		this.site = site;
		this.config = config;
	}

	@Override public String toString() {
		String LF = StandardSystemProperty.LINE_SEPARATOR.value();
		StringBuilder sb = new StringBuilder("HazardResult:");
		sb.append(LF);
		for (SourceType type : EnumSet.copyOf(sourceSetMap.keySet())) {
			sb.append(type).append("SourceSet:").append(LF);
			for (HazardCurveSet curveSet : sourceSetMap.get(type)) {
				SourceSet<? extends Source> ss = curveSet.sourceSet;
				sb.append("  ").append(ss);
				sb.append("Used: ");
				switch (type) {
					case CLUSTER:
						sb.append(curveSet.clusterGroundMotionsList.size());
						break;
					case SYSTEM:
						sb.append(curveSet.hazardGroundMotionsList.get(0).inputs.size());
						break;
					case GRID:
						if (ss instanceof GridSourceSet.Table && config.optimizeGrids) {
							GridSourceSet.Table gsst = (GridSourceSet.Table) curveSet.sourceSet;
							sb.append(gsst.parentCount());
							sb.append(" (").append(curveSet.hazardGroundMotionsList.size());
							sb.append(" of ").append(gsst.maximumSize()).append(")");
							break;
						}
						sb.append(curveSet.hazardGroundMotionsList.size());
						break;
					default:
						sb.append(curveSet.hazardGroundMotionsList.size());
				}
				sb.append(LF);

				if (ss.type() == CLUSTER) {
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
	 * The total mean hazard curves for each calculated {@code Imt}.
	 */
	public Map<Imt, XySequence> curves() {
		return totalCurves;
	}

	/**
	 * The original configuration used to generate this result.
	 */
	public CalcConfig config() {
		return config;
	}

	static Builder builder(CalcConfig config) {
		return new Builder(config);
	}

	static class Builder {

		private static final String ID = "HazardResult.Builder";
		private boolean built = false;

		private HazardModel model;
		private Site site;
		private CalcConfig config;

		private ImmutableSetMultimap.Builder<SourceType, HazardCurveSet> resultMapBuilder;
		private Map<Imt, XySequence> totalCurves;

		private Builder(CalcConfig config) {
			this.config = checkNotNull(config);
			totalCurves = new EnumMap<>(Imt.class);
			for (Entry<Imt, XySequence> entry : config.logModelCurves.entrySet()) {
				totalCurves.put(entry.getKey(), emptyCopyOf(entry.getValue()));
			}
			resultMapBuilder = ImmutableSetMultimap.builder();
		}

		Builder site(Site site) {
			checkState(this.site == null, "%s site already set", ID);
			this.site = checkNotNull(site);
			return this;
		}

		Builder model(HazardModel model) {
			checkState(this.model == null, "%s model already set", ID);
			this.model = checkNotNull(model);
			return this;
		}

		Builder addCurveSet(HazardCurveSet curveSet) {
			resultMapBuilder.put(curveSet.sourceSet.type(), curveSet);
			for (Entry<Imt, XySequence> entry : curveSet.totalCurves.entrySet()) {
				totalCurves.get(entry.getKey()).add(entry.getValue());
			}
			return this;
		}

		private void validateState(String mssgID) {
			checkState(!built, "This %s instance has already been used", mssgID);
			checkState(site != null, "%s site not set", mssgID);
			checkState(model != null, "%s model not set", mssgID);
		}

		HazardResult build() {
			validateState(ID);
			return new HazardResult(
				resultMapBuilder.build(),
				Maps.immutableEnumMap(totalCurves),
				model,
				site,
				config);
		}

	}

}
