package gov.usgs.earthquake.nshmp.calc;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static gov.usgs.earthquake.nshmp.data.XySequence.emptyCopyOf;

import java.util.EnumMap;
import java.util.Map;
import java.util.Map.Entry;

import com.google.common.base.StandardSystemProperty;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.SetMultimap;

import gov.usgs.earthquake.nshmp.data.XySequence;
import gov.usgs.earthquake.nshmp.eq.model.GridSourceSet;
import gov.usgs.earthquake.nshmp.eq.model.HazardModel;
import gov.usgs.earthquake.nshmp.eq.model.Source;
import gov.usgs.earthquake.nshmp.eq.model.SourceSet;
import gov.usgs.earthquake.nshmp.eq.model.SourceType;
import gov.usgs.earthquake.nshmp.gmm.Imt;

/**
 * The result of a hazard calculation. This container class is public for
 * reference by external packages but is not directly modifiable, nor its fields
 * accessible. The {@link HazardExport} class provides {@code Hazard} exporting
 * and processing utilities.
 *
 * @author Peter Powers
 * @see HazardExport
 */
public final class Hazard {

  final SetMultimap<SourceType, HazardCurveSet> sourceSetCurves;
  final Map<Imt, XySequence> totalCurves;
  final HazardModel model;
  final Site site;
  final CalcConfig config;

  private Hazard(
      SetMultimap<SourceType, HazardCurveSet> sourceSetCurves,
      Map<Imt, XySequence> totalCurves,
      HazardModel model,
      Site site,
      CalcConfig config) {

    this.sourceSetCurves = sourceSetCurves;
    this.totalCurves = totalCurves;
    this.model = model;
    this.site = site;
    this.config = config;
  }

  @Override
  public String toString() {
    String LF = StandardSystemProperty.LINE_SEPARATOR.value();
    StringBuilder sb = new StringBuilder("HazardResult:");
    if (sourceSetCurves.keySet().isEmpty()) {
      sb.append(" empty, was the site out of range?");
      return sb.toString();
    }
    sb.append(LF);
    for (SourceType type : sourceSetCurves.keySet()) {
      sb.append(type).append("SourceSet:").append(LF);
      for (HazardCurveSet curveSet : sourceSetCurves.get(type)) {
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
            sb.append(GridSourceSet.sizeString(
                curveSet.sourceSet,
                curveSet.hazardGroundMotionsList.size()));
            break;
          default:
            sb.append(curveSet.hazardGroundMotionsList.size());
        }
        sb.append(LF);
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

  /**
   * Combine hazard from multiple independent models. The hazard object returned
   * by this method will only specify a parent 'model' if varargs only included
   * one model.
   * 
   * <p><b>Note:</b> When combining hazard results, the combined result inherits
   * the {@link CalcConfig} from the first model supplied in varargs. This may
   * mean that in the deaggregation of a combined model, the
   * {@link ExceedanceModel} used to compute hazard may not be the same as that
   * used to compute source contributions.
   * 
   * @return a combined {@code Hazard} that probably has a {@code null} parent
   *         model. If only 1 {@code Hazard} is supplied, method returns that
   *         supplied.
   */
  public static Hazard merge(Hazard... hazards) {
    checkArgument(hazards.length > 0);
    if (hazards.length == 1) {
      return hazards[0];
    }
    ImmutableSetMultimap.Builder<SourceType, HazardCurveSet> curveMapBuilder =
        ImmutableSetMultimap.builder();
    Map<Imt, XySequence> totalCurves = new EnumMap<>(Imt.class);
    for (Entry<Imt, XySequence> entry : hazards[0].config.hazard.logModelCurves().entrySet()) {
      totalCurves.put(entry.getKey(), emptyCopyOf(entry.getValue()));
    }
    for (Hazard hazard : hazards) {
      curveMapBuilder.putAll(hazard.sourceSetCurves);
      for (Entry<Imt, XySequence> entry : hazard.totalCurves.entrySet()) {
        totalCurves.get(entry.getKey()).add(entry.getValue());
      }
    }
    return new Hazard(
        curveMapBuilder.build(),
        totalCurves,
        null,
        hazards[0].site,
        hazards[0].config);
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

    private ImmutableSetMultimap.Builder<SourceType, HazardCurveSet> curveMapBuilder;
    private Map<Imt, XySequence> totalCurves;

    private Builder(CalcConfig config) {
      this.config = checkNotNull(config);
      totalCurves = new EnumMap<>(Imt.class);
      for (Entry<Imt, XySequence> entry : config.hazard.logModelCurves().entrySet()) {
        totalCurves.put(entry.getKey(), emptyCopyOf(entry.getValue()));
      }
      curveMapBuilder = ImmutableSetMultimap.builder();
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
      curveMapBuilder.put(curveSet.sourceSet.type(), curveSet);
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

    Hazard build() {
      validateState(ID);
      return new Hazard(
          curveMapBuilder.build(),
          Maps.immutableEnumMap(totalCurves),
          model,
          site,
          config);
    }
  }

}
