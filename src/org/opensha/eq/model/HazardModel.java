package org.opensha.eq.model;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.opensha.util.TextUtils.validateName;
import static com.google.common.base.StandardSystemProperty.LINE_SEPARATOR;

import java.nio.file.Path;
import java.util.Iterator;
import java.util.Properties;

import org.opensha.eq.fault.surface.RuptureFloating;
import org.opensha.eq.model.AreaSource.GridScaling;
import org.opensha.gmm.GroundMotionModel;
import org.opensha.util.Named;

import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.SetMultimap;

/**
 * An earthquake hazard {@code HazardModel} is the top-level wrapper for
 * earthquake {@link Source} definitions and attendant {@link GroundMotionModel}
 * s used in probabilisitic seismic hazard analyses (PSHAs) and related
 * calculations. A {@code HazardModel} contains of a number of {@link SourceSet}
 * s that define logical groupings of sources by {@link SourceType}. Each
 * {@code SourceSet} carries with it references to the {@code GroundMotionModel}
 * s and associated weights to be used when evaluating hazard.
 * 
 * @author Peter Powers
 * @see Source
 * @see SourceSet
 * @see SourceType
 */
public final class HazardModel implements Iterable<SourceSet<? extends Source>>, Named {

	// TODO recheck 'float'-ing rupture tracking; e.g. GR MFD is set to false;
	// that
	// can't be right; and what about magScaling to go with float?

	// TODO see InterfaceSource todo; Container2D needs getTopRow and
	// getBottomRow

	// TODO where/how to apply CEUS clamps

	// TODO need to revisit the application of uncertainty when minM < 6.5
	// e.g. 809a Pine Valley graben in orwa.c.in

	// TODO Is current approach in SYstemSourceSet for precomputing distances
	// correct? Double check that the individual sections are scaled by aseis
	// not after once built into ruptures.

	// TODO not sure why I changed surface implementations to project down dip
	// from
	// zero to zTop, but it was wrong. currently sesmogenic depth is ignored,
	// but
	// may need this for system sources; should zTop be encoded into trace
	// depths?

	// TODO document object relationships via transforms in calc package

	// TODO expose FloatStyle
	// TODO SUB check rake handling

	// TODO SUB different MSRs are specified in 2014 configs (geomatrix alone
	// no longer used); check that this has carried through
	// correctly to 2008

	// TODO UCERF3 xml (indexedFaultSource) needs to have aftershock correction
	// either imposed in file (better) of be imlemented in Parser
	// or Loader (worse)
	//
	// TODO CEUS mMax zoning
	// TODO depth varying deep source; zones --> parse
	// TODO do deep GMMs saturate/apped at 7.8 ?? I believe all are but need to
	// ensure

	// TODO perhaps add notes on CEUS fixed strike sources having vertical
	// faults with no mechanism (strike-slip implied)
	// rJB, rRup, rX all based on line regardeless of dip

	// TODO are any M=6.0 finite source representation cutoffs being used in
	// 2014 fortran

	// TODO need to implement masking of CA shear sources in XML; only keep
	// those that are outside
	// CA boundary

	// TODO check if AtkinsonMacias using BooreAtkin siteAmp to get non-rock
	// site response

	// TODO deep sources: dtor matrix keyed to longitude?? is this specified in
	// config files?
	// TODO check ORegon branches: Portland nested inside all OR?

	// TODO epiphany!!! Although it will be a little more work, if we have a
	// multi-distance
	// GmmSet model, we will do all calculations; the far gmms are currently
	// required to be a subset of the near
	// gmms. Even without this requirement, we would compute ground motions for
	// the master set of gmms.
	// Only when recombining scalar ground motions into hazard curves will we
	// chose those values
	// required at different distances as we will have the associated GmmInputs
	// handy

	// TODO perhaps we process a config.xml file at the root of
	// a HazardModel to pick up name and other calc configuration data

	// TODO are slabSource depths validated?

	// TODO change Charsets to StandardCharsets and replace US_ASCII with UTF-8

	private final String name;
	private final SetMultimap<SourceType, SourceSet<? extends Source>> sourceSetMap;
	private final Config config;

	private HazardModel(String name, Config config,
		SetMultimap<SourceType, SourceSet<? extends Source>> sourceSetMap) {
		this.name = name;
		this.config = config;
		this.sourceSetMap = sourceSetMap;
	}

	/**
	 * Load a {@code HazardModel} from a directory or Zip file specified by the
	 * supplied {@code path}.
	 * 
	 * <p>For more information on a HazardModel directory and file structure,
	 * see the <a
	 * href="https://github.com/usgs/nshmp-haz/wiki/Earthquake-Source-Models"
	 * >nshmp-haz wiki</a>.</p>
	 * 
	 * <p><b>Notes:</b> HazardModel loading is not thread safe. Also, there are
	 * a wide variety of exceptions that may be encountered when loading a
	 * model. In most cases, the exception will be logged and the JVM will
	 * exit.</p>
	 * 
	 * @param path to {@code HazardModel} directory or Zip file
	 * @param name for the {@code HazardModel}
	 * @return a newly instantiated {@code HazardModel}
	 */
	public static HazardModel load(Path path, String name) {
		return Loader.load(path, name);
	}

	/**
	 * The number of {@code SourceSet}s in this {@code HazardModel}.
	 */
	public int size() {
		return sourceSetMap.size();
	}

	@Override public Iterator<SourceSet<? extends Source>> iterator() {
		return sourceSetMap.values().iterator();
	}

	@Override public String name() {
		return name;
	}

	@Override public String toString() {
		return "HazardModel: " + name + LINE_SEPARATOR.value() + sourceSetMap.toString();
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
		private Config config;
		private String name;

		private Builder() {
			sourceMapBuilder = ImmutableSetMultimap.builder();
		}

		Builder config(Properties props) {
			config = new HazardModel.Config(props);
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

	final static class Config {

		public final double GRIDDED_SURFACE_UNIT;
		public final AreaSource.GridScaling AREA_SOURCE_SCALING;
		public final RuptureFloating RUPTURE_FLOATING_MODEL;

		private Config(Properties props) {
			checkState(!checkNotNull(props).isEmpty(), "Properties table is null or empty");
			GRIDDED_SURFACE_UNIT = Double.valueOf(props.getProperty("GRIDDED_SURFACE_UNIT"));
			AREA_SOURCE_SCALING = GridScaling.valueOf(props.getProperty("AREA_SOURCE_SCALING"));
			RUPTURE_FLOATING_MODEL = RuptureFloating.valueOf(props
				.getProperty("RUPTURE_FLOATING_MODEL"));
		}

	}

}
