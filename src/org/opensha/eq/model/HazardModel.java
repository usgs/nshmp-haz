package org.opensha.eq.model;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.opensha.util.TextUtils.validateName;
import static org.opensha.util.TextUtils.NEWLINE;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Properties;

import org.opensha.calc.GaussTruncation;
import org.opensha.eq.fault.surface.RuptureFloating;
import org.opensha.eq.model.AreaSource.GridScaling;
import org.opensha.gmm.GroundMotionModel;
import org.opensha.util.Named;

import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.SetMultimap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;

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

	// high priority
	// TODO grid sources; map against all matching defaultMfd types
	// TODO need to handle cal_fl(oater): no down dip;
	// TODO clean up floater specification; props or source model
	// float attribute is only permitted for SINGLE mfds because NSHM
	// prescribed that some characteristic ruptures float; really should just
	// let a mag-scaling relation determine whether a rupture fills or floats
	// -- I think we just need to do a sensitivity study wherein, the
	// attribute is removed and we pass every characteristic rupture through
	// its magScaling relation to determine if more than 1 rupture is needed
	// -- alternatively, we just extract those faults with SINGLE mfds that
	// float out to their own file such theat their source properties are
	// consistent

	// TODO where/how to apply CEUS clamps

	// TODO I think current FaultSystemSource conversions incorrectly include
	// depths with fault section traces. Traces are always at surface.

	// TODO Is current approach in SYstemSourceSet for precomputing distances
	// correct? Double check that the individual sections are scaled by aseis
	// not after once built into ruptures.

	// TODO not sure why I changed surface implementations to project down dip
	// from zero to zTop, but it was wrong. currently sesmogenic depth is
	// ignored,
	// but may need this for system sources; should zTop be encoded into trace
	// depths?

	// TODO UCERF3 xml (indexedFaultSource) needs to have aftershock correction
	// either imposed in file (better) of be imlemented in Parser
	// or Loader (worse)

	// TODO are any M=6.0 finite source representation cutoffs being used in
	// 2014 fortran

	// TODO implement CEUS gmm distance reweighting in calc
	// always compute all curves, just combine based on distance weights;
	// I think this is being done in HazardCurveSet.Builder already

	// low priority
	// TODO change vs30 to Integer?
	// TODO test low rate shortcut in FaultSource
	// "if (rate < 1e-14) continue; // shortcut low rates"
	// TODO need to revisit the application of uncertainty when minM < 6.5
	// e.g. 809a Pine Valley graben in orwa.c.in

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
		return "HazardModel: " + name + NEWLINE + sourceSetMap.toString();
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

		Builder config(Config config) {
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
