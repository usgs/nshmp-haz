package org.opensha.eq.forecast;

import static com.google.common.base.Preconditions.checkState;
import static org.opensha.util.TextUtils.validateName;
import static com.google.common.base.StandardSystemProperty.LINE_SEPARATOR;

import java.util.Iterator;

import org.opensha.util.Named;

import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.SetMultimap;

/**
 * A {@code Forecast} is the top-level wrapper for earthquake source definitions
 * used in probabilisitic seismic hazard analyses and calculations (PSHAs). A
 * {@code Forecast} contains of a number of {@code SourceSet}s that define
 * logical groupings of sources by {@code SourceType}.
 * 
 * TODO (below) maybe not
 * <p>Iteration of {@code SourceSet}s occurs in the order that
 * {@code SourceType}s are declared in their {@code enum} class and then by the
 * order they were added to this {@code Forecast}</p>
 * 
 * @author Peter Powers
 * @see SourceSet
 * @see SourceType
 */
public final class Forecast implements Iterable<SourceSet<? extends Source>>, Named {

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

	// TODO does this need to be a SetMultimap
	private Forecast(String name, SetMultimap<SourceType, SourceSet<? extends Source>> sourceSetMap) {
		this.name = name;
		this.sourceSetMap = sourceSetMap;
	}

	/**
	 * Returns the number of {@code SourceSet}s in this {@code Forecast}.
	 */
	public int size() {
		return sourceSetMap.size();
	}

	@Override public Iterator<SourceSet<? extends Source>> iterator() {
		return sourceSetMap.values().iterator();

		// TODO clean
		// TODO check if above delegate sorts on keys as well
		// probably doesn't really matter
		// Set<SourceType> types = Sets.newEnumSet(sourceSetMap.keySet(),
		// SourceType.class);
		// List<Iterator<SourceSet<? extends Source>>> iterators =
		// Lists.newArrayList();
		// for (SourceType type : types) {
		// iterators.add(sourceSetMap.get(type).iterator());
		// }
		// return Iterators.concat(iterators.iterator());
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
	public static Builder builder() {
		return new Builder();
	}

	static class Builder {

		static final String ID = "Forecast.Builder";
		boolean built = false;

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
