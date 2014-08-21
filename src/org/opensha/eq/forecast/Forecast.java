package org.opensha.eq.forecast;

import static com.google.common.base.Preconditions.checkState;
import static org.opensha.util.TextUtils.validateName;
import static com.google.common.base.StandardSystemProperty.LINE_SEPARATOR;

import java.util.Iterator;

import org.opensha.gmm.GroundMotionModel;
import org.opensha.util.Named;

import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.SetMultimap;

/**
 * An earthquake hazard {@code Forecast} is the top-level wrapper for earthquake
 * {@link Source} definitions and attendant {@link GroundMotionModel}s used in
 * probabilisitic seismic hazard analyses (PSHAs) and related calculations. A
 * {@code Forecast} contains of a number of {@link SourceSet}s that define
 * logical groupings of sources by {@link SourceType}. Each {@code SourceSet}
 * carries with it references to the {@code GroundMotionModel}s and associated
 * weights to be used when evaluating hazard.
 * 
 * @author Peter Powers
 * @see Source
 * @see SourceSet
 * @see SourceType
 */
public final class Forecast implements Iterable<SourceSet<? extends Source>>, Named {

	// TODO deal with javadoc warnings in build.xml
	
	// TODO INDEXED_FAULT to SYSTEM
	
	// TODO how to set PointSourceType, see GridSourceSet
	// TODO cases where general pointSource is used??

	// TODO expose FloatStyle
	// TODO SUB check rake handling

	// TODO SUB merge | multiply weight and rate

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

	private final String name;
	private final SetMultimap<SourceType, SourceSet<? extends Source>> sourceSetMap;

	private Forecast(String name, SetMultimap<SourceType, SourceSet<? extends Source>> sourceSetMap) {
		this.name = name;
		this.sourceSetMap = sourceSetMap;
	}

	/**
	 * Load a {@code Forecast} from a directory or Zip file specified by the
	 * supplied {@code path}.
	 * 
	 * <p>For more information on a Forecast directory and file structure, see
	 * the <a
	 * href="https://github.com/usgs/nshmp-haz/wiki/Earthquake-Source-Models"
	 * >nshmp-haz wiki</a>.</p>
	 * 
	 * <p><b>Notes:</b> Forecast loading is not thread safe. This method can
	 * potentially throw a wide variety of exceptions, all of which will be
	 * logged in detail but propogated as a plain checked {@code Exception}.</p>
	 * 
	 * @param path to {@code Forecast} directory or Zip file
	 * @param name for the {@code Forecast}
	 * @return a newly instantiated {@code Forecast}
	 * @throws Exception if an error occurs
	 */
	public static Forecast load(String path, String name) throws Exception {
		return Loader.load(path, name);
	}

	/**
	 * The number of {@code SourceSet}s in this {@code Forecast}.
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
		return "Forecast: " + name + LINE_SEPARATOR.value() + sourceSetMap.toString();
	}

	/**
	 * Returns a new {@link Builder}.
	 */
	static Builder builder() {
		return new Builder();
	}

	static class Builder {

		static final String ID = "Forecast.Builder";
		boolean built = false;

		// ImmutableSetMultimap.Builder preserves value addition order
		private ImmutableSetMultimap.Builder<SourceType, SourceSet<? extends Source>> sourceMapBuilder;
		private SetMultimap<SourceType, SourceSet<? extends Source>> sourceSetMap;
		private String name;

		private Builder() {
			sourceMapBuilder = ImmutableSetMultimap.builder();
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
			built = true;

		}

		Forecast build() {
			sourceSetMap = sourceMapBuilder.build();
			validateState(ID);
			return new Forecast(name, sourceSetMap);
		}
	}

}
