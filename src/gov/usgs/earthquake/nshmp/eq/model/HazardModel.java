package gov.usgs.earthquake.nshmp.eq.model;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static gov.usgs.earthquake.nshmp.internal.TextUtils.NEWLINE;
import static gov.usgs.earthquake.nshmp.internal.TextUtils.validateName;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Set;

import org.xml.sax.SAXException;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import com.google.common.collect.Streams;

import gov.usgs.earthquake.nshmp.calc.CalcConfig;
import gov.usgs.earthquake.nshmp.gmm.Gmm;
import gov.usgs.earthquake.nshmp.gmm.GroundMotionModel;
import gov.usgs.earthquake.nshmp.util.Named;

/**
 * A {@code HazardModel} is the top-level wrapper for earthquake {@link Source}
 * definitions and attendant {@link GroundMotionModel}s used in probabilisitic
 * seismic hazard analyses (PSHAs) and related calculations. A
 * {@code HazardModel} contains of a number of {@link SourceSet}s that define
 * logical groupings of sources by {@link SourceType}. Each {@code SourceSet}
 * carries with it references to the {@code GroundMotionModel}s and associated
 * weights to be used when evaluating hazard.
 *
 * @author Peter Powers
 * @see Source
 * @see SourceSet
 * @see SourceType
 */
public final class HazardModel implements Iterable<SourceSet<? extends Source>>, Named {

  // high priority
  // TODO grid sources; map against all matching defaultMfd types

  // TODO Is current approach in SYstemSourceSet for precomputing distances
  // correct? Double check that the individual sections are scaled by aseis
  // not after once built into ruptures.

  // TODO UCERF3 xml (indexedFaultSource) needs to have aftershock correction
  // either imposed in file (better) of be imlemented in Parser
  // or Loader (worse)

  // TODO are any M=6.0 finite source representation cutoffs being used in
  // 2014 fortran

  // low priority
  // TODO test low rate shortcut in FaultSource
  // "if (rate < 1e-14) continue; // shortcut low rates"

  // TODO need to revisit the application of uncertainty when minM < 6.5
  // e.g. 809a Pine Valley graben in orwa.c.in

  private final String name;
  private final SetMultimap<SourceType, SourceSet<? extends Source>> sourceSetMap;
  private final CalcConfig config;

  private HazardModel(String name, CalcConfig config,
      SetMultimap<SourceType, SourceSet<? extends Source>> sourceSetMap) {
    this.name = name;
    this.config = config;
    this.sourceSetMap = sourceSetMap;
  }

  /**
   * Load a {@code HazardModel} from a directory or Zip file specified by the
   * supplied {@code path}.
   *
   * <p>For more information on a HazardModel directory and file structure, see
   * the <a
   * href="https://github.com/usgs/nshmp-haz/wiki/Earthquake-Source-Models"
   * target="_top">nshmp-haz wiki</a>.
   *
   * <p><b>Notes:</b> HazardModel loading is not thread safe. There are also a
   * wide variety of exceptions that may be encountered when loading a model.
   * Exceptions are generally logged and propagated back to calling application
   * to handle termination.
   *
   * @param path to {@code HazardModel} directory or Zip file
   * @return a newly instantiated {@code HazardModel}
   */
  public static HazardModel load(Path path) throws SAXException, IOException {
    return Loader.load(path);
  }

  /**
   * The number of {@code SourceSet}s in this {@code HazardModel}.
   */
  public int size() {
    return sourceSetMap.size();
  }

  @Override
  public Iterator<SourceSet<? extends Source>> iterator() {
    return sourceSetMap.values().iterator();
  }

  @Override
  public String name() {
    return name;
  }

  /**
   * The set of {@code Gmm}s used by this model.
   */
  public Set<Gmm> gmms() {
    return Streams.stream(this)
        .flatMap(ss -> ss.groundMotionModels().gmms().stream())
        .collect(Sets.toImmutableEnumSet());
  }

  /**
   * The set of {@code SourceType}s included in this model.
   */
  public Set<SourceType> types() {
    return sourceSetMap.keySet();
  }

  /**
   * Return the default calculation configuration. This may be overridden.
   */
  public CalcConfig config() {
    return config;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("HazardModel: ");
    sb.append(name).append(NEWLINE);
    for (SourceSet<? extends Source> sourceSet : this) {
      String typeStr = "  " + sourceSet.type() + " Source Set";
      sb.append(Strings.padEnd(typeStr, 24, ' '));
      sb.append(sourceSet);
      sb.append(NEWLINE);
    }
    return sb.toString();
  }

  /**
   * Returns a new {@link Builder}.
   */
  static Builder builder() {
    return new Builder();
  }

  static class Builder {

    static final String ID = "HazardModel.Builder";
    boolean built = false;

    // ImmutableSetMultimap.Builder preserves value addition order
    private ImmutableSetMultimap.Builder<SourceType, SourceSet<? extends Source>> sourceMapBuilder;
    private SetMultimap<SourceType, SourceSet<? extends Source>> sourceSetMap;
    private CalcConfig config;
    private String name;

    private Builder() {
      sourceMapBuilder = ImmutableSetMultimap.builder();
    }

    Builder config(CalcConfig config) {
      this.config = checkNotNull(config);
      return this;
    }

    Builder name(String name) {
      this.name = validateName(name);
      return this;
    }

    Builder sourceSet(SourceSet<? extends Source> source) {
      sourceMapBuilder.put(source.type(), source);
      return this;
    }

    private void validateState(String mssgID) {
      checkState(!built, "This %s instance as already been used", mssgID);
      checkState(name != null, "%s name not set", mssgID);
      checkState(sourceSetMap.size() > 0, "%s has no source sets", mssgID);
      checkState(config != null, "%s config not set", mssgID);
      built = true;
    }

    HazardModel build() {
      sourceSetMap = sourceMapBuilder.build();
      validateState(ID);
      return new HazardModel(name, config, sourceSetMap);
    }
  }

}
