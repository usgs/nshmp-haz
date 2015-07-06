package org.opensha2.eq.model;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static java.lang.Math.min;
import static org.opensha2.eq.Magnitudes.validateMag;
import static org.opensha2.eq.fault.Faults.validateDepth;
import static org.opensha2.eq.fault.Faults.validateDip;
import static org.opensha2.eq.fault.Faults.validateRake;
import static org.opensha2.eq.fault.Faults.validateWidth;
import static org.opensha2.eq.model.Distance.Type.R_JB;
import static org.opensha2.eq.model.Distance.Type.R_RUP;
import static org.opensha2.eq.model.Distance.Type.R_X;
import static org.opensha2.geo.Locations.horzDistanceFast;

import java.util.BitSet;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import org.opensha2.calc.HazardInput;
import org.opensha2.calc.Site;
import org.opensha2.calc.SystemInputs;
import org.opensha2.data.DataUtils;
import org.opensha2.eq.fault.Faults;
import org.opensha2.eq.fault.surface.GriddedSurface;
import org.opensha2.eq.model.Distance.Type;
import org.opensha2.geo.Location;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.base.Throwables;
import com.google.common.collect.ArrayTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Table;
import com.google.common.primitives.Doubles;

/**
 * Wrapper class for related {@link SystemSource}s.
 * 
 * @author Peter Powers
 */
public class SystemSourceSet extends AbstractSourceSet<SystemSourceSet.SystemSource> {

	private final List<GriddedSurface> sections;
	private final List<BitSet> bitsets;
	private final List<Double> mags;
	private final List<Double> rates;
	private final List<Double> depths;
	private final List<Double> dips;
	private final List<Double> widths;
	private final List<Double> rakes;

	// NOTE the above Double lists are compact but mutable: Doubles.asList(...)

	private SystemSourceSet(String name, int id, double weight, GmmSet gmmSet,
			List<GriddedSurface> sections, List<BitSet> bitsets, List<Double> mags,
			List<Double> rates,
			List<Double> depths, List<Double> dips, List<Double> widths, List<Double> rakes) {

		super(name, id, weight, gmmSet);

		this.sections = sections;
		this.bitsets = bitsets;
		this.mags = mags;
		this.rates = rates;
		this.depths = depths;
		this.dips = dips;
		this.widths = widths;
		this.rakes = rakes;
	}

	@Override public SourceType type() {
		return SourceType.SYSTEM;
	}

	@Override public int size() {
		return bitsets.size();
	}

	@Override public Iterator<SystemSource> iterator() {
		return new Iterator<SystemSource>() {
			int caret = 0;

			@Override public boolean hasNext() {
				return caret < size();
			}

			@Override public SystemSource next() {
				return new SystemSource(caret++);
			}

			@Override public void remove() {
				throw new UnsupportedOperationException();
			}
		};
	}

	@Override public Predicate<SystemSource> distanceFilter(Location loc, double distance) {
		BitSet siteBitset = bitsetForLocation(loc, distance);
		return new BitsetFilter(siteBitset);
	}

	/**
	 * A single source in a fault system. These sources do not currently support
	 * rupture iteration.
	 * 
	 * <p>We skip the notion of a {@code Rupture} for now. Aleatory uncertainty
	 * on magnitude isn't required, but if it is, we'll alter this
	 * implementation to return List<GmmInput> per source rather than one
	 * GmmInput.</p>
	 */
	public final class SystemSource implements Source {

		private final int index;

		private SystemSource(final int index) {
			this.index = index;
		}

		@Override public int size() {
			return 1;
		}

		@Override public String name() {
			// TODO How to create name? SourceSet will need parent section names
			return "Unnamed fault system source";
		}

		@Override public Iterator<Rupture> iterator() {
			/*
			 * Rupture iterator not currently supported but may be in future if
			 * aleatory uncertainty is added to or required on source
			 * magnitudes. Currently system source:rupture is 1:1.
			 */
			throw new UnsupportedOperationException();
		}

		// @formatter:off
		private final BitSet bitset()    { return bitsets.get(index); }
		private final double magnitude() { return mags.get(index); }
		private final double rate()      { return rates.get(index); }
		private final double depth()     { return depths.get(index); }
		private final double dip()       { return dips.get(index); }
		private final double width()     { return widths.get(index); }
		private final double rake()      { return rakes.get(index); }
		// @formatter:on
	}

	/*
	 * Single use builder. Quirky behavior: Note that sections() must be called
	 * before any calls to indices(). All indices and data fields should be
	 * repeatedly called in order to ensure correctly ordered fields when
	 * iterating ruptures.
	 */
	static class Builder extends AbstractSourceSet.Builder {

		// Unfiltered UCERF3: FM31 = 253,706 FM32 = 305,709
		static final int RUP_SET_SIZE = 306000;

		static final String ID = "SystemSourceSet.Builder";

		private List<GriddedSurface> sections;
		private final List<BitSet> bitsets = Lists.newArrayListWithCapacity(RUP_SET_SIZE);
		private final List<Double> mags = Lists.newArrayListWithCapacity(RUP_SET_SIZE);
		private final List<Double> rates = Lists.newArrayListWithCapacity(RUP_SET_SIZE);
		private final List<Double> depths = Lists.newArrayListWithCapacity(RUP_SET_SIZE);
		private final List<Double> dips = Lists.newArrayListWithCapacity(RUP_SET_SIZE);
		private final List<Double> widths = Lists.newArrayListWithCapacity(RUP_SET_SIZE);
		private final List<Double> rakes = Lists.newArrayListWithCapacity(RUP_SET_SIZE);

		Builder sections(List<GriddedSurface> sections) {
			checkNotNull(sections, "Section surface list is null");
			checkArgument(sections.size() > 0, "Section surface list is empty");
			this.sections = sections;
			return this;
		}

		Builder indices(List<Integer> indices) {
			checkState(sections != null, "Indices may only be set after call to sections()");
			checkNotNull(indices, "Rupture index list is null");
			// NOTE we're doublechecking a UCERF3 rule that ruptures be composed
			// of at least 2 sections; this may not be the case in the future.
			checkArgument(indices.size() > 1, "Rupture index list must contain 2 or more values");
			bitsets.add(DataUtils.indicesToBits(indices, sections.size()));
			return this;
		}

		Builder mag(double mag) {
			mags.add(validateMag(mag));
			return this;
		}

		Builder rate(double rate) {
			// TODO better rate filtering ??
			checkArgument(Doubles.isFinite(rate), "Rate is not a finite value");
			rates.add(rate);
			return this;
		}

		Builder depth(double depth) {
			depths.add(validateDepth(depth));
			return this;
		}

		Builder dip(double dip) {
			dips.add(validateDip(dip));
			return this;
		}

		Builder width(double width) {
			widths.add(validateWidth(width));
			return this;
		}

		Builder rake(double rake) {
			rakes.add(validateRake(rake));
			return this;
		}

		@Override void validateState(String buildId) {
			super.validateState(buildId);

			checkState(sections.size() > 0, "%s no sections added", buildId);
			checkState(bitsets.size() > 0, "%s no index lists added", buildId);

			int target = bitsets.size();
			checkSize(mags.size(), target, buildId, "magnitudes");
			checkSize(rates.size(), target, buildId, "rates");
			checkSize(depths.size(), target, buildId, "depths");
			checkSize(dips.size(), target, buildId, "dips");
			checkSize(widths.size(), target, buildId, "widths");
			checkSize(rakes.size(), target, buildId, "rakes");
		}

		private static void checkSize(int size, int target, String classId, String dataId) {
			checkState(size == target, "%s too few %s [%s of %s]", classId, dataId, size, target);
		}

		SystemSourceSet build() {
			validateState(ID);

			return new SystemSourceSet(
				name,
				id,
				weight,
				gmmSet,
				ImmutableList.copyOf(sections),
				ImmutableList.copyOf(bitsets),
				Doubles.asList(Doubles.toArray(mags)),
				Doubles.asList(Doubles.toArray(rates)),
				Doubles.asList(Doubles.toArray(depths)),
				Doubles.asList(Doubles.toArray(dips)),
				Doubles.asList(Doubles.toArray(widths)),
				Doubles.asList(Doubles.toArray(rakes)));

		}
	}

	/*
	 * System source calculation pipeline.
	 * 
	 * Rather than expose highly mutable bitsets and attendant logic that is
	 * used to generate HazardInputs from SystemSourceSets, we opt to locate
	 * transform Functions and related classes here.
	 * 
	 * System sources (e.g. UCERF3 ruptures) are composed of multiple small
	 * (~7km long x ~15km wide) adjacent fault sections in a large and dense
	 * fault network. Each source is defined by the indices of the participating
	 * fault sections, magnitude, average width, average rake, and average dip,
	 * among other parameters.
	 * 
	 * In order to filter ruptures and calculate distance parameters when doing
	 * a hazard calculation, one can treat each source as a finite fault. This
	 * ultimately requires careful handling of site-to-source calculations when
	 * trying to reduce redundant calculations because many sources share the
	 * same fault sections and sources will be handled one-at-a-time, in
	 * sequence.
	 * 
	 * Alternatively, one can approach the problem from an indexing standpoint,
	 * precomuting that data which will be required, and then mining it on a
	 * per-source basis, as follows:
	 * 
	 * 1) For each source, create a BitSet with size = nSections. Set the bits
	 * for each section that the source uses. [sourceBitSet]
	 * 
	 * 2) Create another BitSet with size = nSections. Set the bits for each
	 * section within the distance cutoff for a Site. Do this quickly using only
	 * the centroid of each fault section. [siteBitSet]
	 * 
	 * 3) Create and populate a Table<Metric, SectionIndex, Value> of distance
	 * metrics for each section in the siteBitSet.
	 * 
	 * 4) For each sourceBitSet, 'siteBitSet.intersects(sourceBitSet)' returns
	 * whether the source is close enough to the site to be considered.
	 * 
	 * 5) For each considered source, 'sourceBitSet AND siteBitSet' yields a
	 * BitSet that only includes set bits with indices in the distance metric
	 * table.
	 * 
	 * 6) For the relevant fault sections in each source, find the minimum
	 * distance metrics in the table (the rX value used is keyed to the minimum
	 * rRup).
	 * 
	 * 7) Build GmmInputs and proceed with hazard calculation.
	 * 
	 * Note on the above. Although one could argue that only rRup or rJb be
	 * calculated first, there are geometries for which min(rRup) != min(rJB);
	 * e.g. location on hanging wall of dipping fault that abuts a vertical
	 * fault... vertical might yield min(rRup) but min(rJB) would be 0 (over
	 * dipping fault).
	 * 
	 * Deaggregation considerations. TODO
	 */

	/*
	 * Thoughts on multithreading: on systems with few cores, performance is worse
	 * using the multi-threaded (ExecutorService based) implementation below.
	 * In most NSHMP casesmany other calculations also need to take place so it
	 * may be just as well to create system inputs on a single thread. Moreover,
	 * multiple system models would thne be run in parallel anyway as each
	 * SystemSourceSet would be farmed out to it's own thread.
	 * 
	 * TODO revisit this in context of performance tuning
	 */
	
	@Deprecated
	public static final class ToInputsMT implements Function<SystemSourceSet, SystemInputs> {

		private final Site site;
		private final ExecutorService executor;

		public ToInputsMT(final Site site, final ExecutorService executor) {
			this.site = site;
			this.executor = executor;
		}

		@Override public SystemInputs apply(final SystemSourceSet sourceSet) {

			try {

				// create Site BitSet
				double maxDistance = sourceSet.groundMotionModels().maxDistance();
				BitSet siteBitset = sourceSet.bitsetForLocation(site.location, maxDistance);

				// create and submit distance calculations
				CompletionService<Distance> dCS = new ExecutorCompletionService<Distance>(executor);
				Function<GriddedSurface, Distance> rTransform = new DistanceCalc(site.location);
				List<Integer> siteIndices = DataUtils.bitsToIndices(siteBitset);
				// need to track section indices
				Map<Future<Distance>, Integer> taskIndexMap = new HashMap<>();
				for (int i : siteIndices) {
					GriddedSurface surface = sourceSet.sections.get(i);
					Callable<Distance> rCalc = new DistanceCalcTask(rTransform, surface);
					taskIndexMap.put(dCS.submit(rCalc), i);
				}

				// fill distance table
				Table<Integer, Distance.Type, Double> rTable = ArrayTable.create(
					siteIndices,
					EnumSet.allOf(Distance.Type.class));
				for (int i = 0; i < siteIndices.size(); i++) {
					Future<Distance> result = dCS.take();
					int index = taskIndexMap.get(result);
					Distance r = result.get();
					Map<Distance.Type, Double> rRow = rTable.row(index);
					rRow.put(R_JB, r.rJB);
					rRow.put(R_RUP, r.rRup);
					rRow.put(R_X, r.rX);
				}

				// create and submit inputs
				CompletionService<HazardInput> iCS = new ExecutorCompletionService<HazardInput>(
					executor);
				Function<SystemSource, HazardInput> iTransform = new InputGenerator(rTable,
					siteBitset, site);
				SystemInputs inputs = new SystemInputs(sourceSet);
				Predicate<SystemSource> rFilter = new BitsetFilter(siteBitset);
				Iterable<SystemSource> sources = Iterables.filter(sourceSet, rFilter);
				int count = 0;
				for (SystemSource source : sources) {
					Callable<HazardInput> inputGenTask = new InputGeneratorTask(iTransform, source);
					iCS.submit(inputGenTask);
					count++;
					HazardInput input = inputGenTask.call();
					inputs.add(input);
				}

				// retrieve and compile inputs
				for (int i = 0; i < count; i++) {
					HazardInput input = iCS.take().get();
					inputs.add(input);
				}

				return inputs;

			} catch (Exception e) {
				Throwables.propagate(e);
				return null;
			}
		}
	}

	@Deprecated
	public static final class ToInputsOld implements Function<SystemSourceSet, SystemInputs> {

		private final Site site;

		public ToInputsOld(final Site site) {
			this.site = site;
		}

		@Override public SystemInputs apply(final SystemSourceSet sourceSet) {

			try {
				// create Site BitSet
				double maxDistance = sourceSet.groundMotionModels().maxDistance();
				BitSet siteBitset = sourceSet.bitsetForLocation(site.location, maxDistance);

				// create and fill distance table
				List<Integer> siteIndices = DataUtils.bitsToIndices(siteBitset);
				Table<Integer, Distance.Type, Double> rTable = ArrayTable.create(
					siteIndices,
					EnumSet.allOf(Distance.Type.class));
				Function<GriddedSurface, Distance> rTransform = new DistanceCalc(site.location);
				for (int i : siteIndices) {
					GriddedSurface surface = sourceSet.sections.get(i);
					Callable<Distance> rCalc = new DistanceCalcTask(rTransform, surface);
					Distance r = rCalc.call();
					Map<Distance.Type, Double> rRow = rTable.row(i);
					rRow.put(R_JB, r.rJB);
					rRow.put(R_RUP, r.rRup);
					rRow.put(R_X, r.rX);
				}

				// create inputs
				Function<SystemSource, HazardInput> inputGenerator = new InputGenerator(
					rTable, siteBitset, site);
				SystemInputs inputs = new SystemInputs(sourceSet);
				Predicate<SystemSource> rFilter = new BitsetFilter(siteBitset);
				Iterable<SystemSource> sources = Iterables.filter(sourceSet, rFilter);
				for (SystemSource source : sources) {
					Callable<HazardInput> inputGenTask = new InputGeneratorTask(
						inputGenerator,
						source);
					HazardInput input = inputGenTask.call();
					inputs.add(input);
				}

				return inputs;

			} catch (Exception e) {
				Throwables.propagate(e);
				return null;
			}
		}
	}
	
	/*
	 * TODO This is a more compact form of the original ToInputs that was structured to
	 * be more easily repurposed as a multithreaded implementation. i.e. using Callables
	 * etc... Turns out it's more likely a single threaded approach will be more performant
	 * anticipating that multipl system models will be run in parallel
	 *
	 */
	public static final class ToInputs implements Function<SystemSourceSet, SystemInputs> {

		private final Site site;

		public ToInputs(final Site site) {
			this.site = site;
		}

		@Override public SystemInputs apply(final SystemSourceSet sourceSet) {

			try {
				// create Site BitSet
				double maxDistance = sourceSet.groundMotionModels().maxDistance();
				BitSet siteBitset = sourceSet.bitsetForLocation(site.location, maxDistance);

				// create and fill distance table
				List<Integer> siteIndices = DataUtils.bitsToIndices(siteBitset);
				Table<Integer, Distance.Type, Double> rTable = ArrayTable.create(
					siteIndices,
					EnumSet.allOf(Distance.Type.class));
				for (int i : siteIndices) {
					Distance r = sourceSet.sections.get(i).distanceTo(site.location);
					Map<Distance.Type, Double> rRow = rTable.row(i);
					rRow.put(R_JB, r.rJB);
					rRow.put(R_RUP, r.rRup);
					rRow.put(R_X, r.rX);
				}

				// create inputs
				Function<SystemSource, HazardInput> inputGenerator = new InputGenerator(
					rTable, siteBitset, site);
				SystemInputs inputs = new SystemInputs(sourceSet);
				Predicate<SystemSource> rFilter = new BitsetFilter(siteBitset);
				Iterable<SystemSource> sources = Iterables.filter(sourceSet, rFilter);
				for (SystemSource source : sources) {
					inputs.add(inputGenerator.apply(source));
				}

				return inputs;

			} catch (Exception e) {
				Throwables.propagate(e);
				return null;
			}
		}
	}


	private static final class DistanceCalc implements Function<GriddedSurface, Distance> {

		private final Location loc;

		DistanceCalc(final Location loc) {
			this.loc = loc;
		}

		@Override public Distance apply(final GriddedSurface surface) {
			return surface.distanceTo(loc);
		}
	}

	@Deprecated
	private static class DistanceCalcTask implements Callable<Distance> {

		private final Function<GriddedSurface, Distance> calc;
		private final GriddedSurface surface;

		DistanceCalcTask(final Function<GriddedSurface, Distance> calc, final GriddedSurface surface) {
			this.calc = calc;
			this.surface = surface;
		}

		@Override public Distance call() {
			return calc.apply(surface);
		}
	}

	private static class BitsetFilter implements Predicate<SystemSource> {

		private static final String ID = "BitsetFilter";
		private final BitSet bitset;

		BitsetFilter(BitSet bitset) {
			this.bitset = bitset;
		}

		@Override public boolean apply(SystemSource source) {
			return bitset.intersects(source.bitset());
		}

		@Override public String toString() {
			return ID + " " + bitset;
		}
	}

	private static final class InputGenerator implements Function<SystemSource, HazardInput> {

		private final Table<Integer, Distance.Type, Double> rTable;
		private final BitSet siteBitset;
		private final Site site;

		InputGenerator(
				final Table<Integer, Distance.Type, Double> rTable,
				final BitSet siteBitset,
				final Site site) {

			this.rTable = rTable;
			this.siteBitset = siteBitset;
			this.site = site;
		}

		@Override public HazardInput apply(SystemSource source) {

			// create index list of relevant sections
			BitSet sectionBitset = (BitSet) source.bitset().clone();
			sectionBitset.and(siteBitset);
			List<Integer> sectionIndices = DataUtils.bitsToIndices(sectionBitset);

			// find r minima
			double rJB = Double.MAX_VALUE;
			double rRup = Double.MAX_VALUE;
			int rRupIndex = -1;
			for (int sectionIndex : sectionIndices) {
				Map<Type, Double> rRow = rTable.row(sectionIndex);
				rJB = min(rJB, rRow.get(R_JB));
				double rRupNew = rRow.get(R_RUP);
				if (rRupNew < rRup) {
					rRup = rRupNew;
					rRupIndex = sectionIndex;
				}
			}
			double rX = rTable.get(rRupIndex, R_X);

			double dip = source.dip();
			double width = source.width();
			double zTop = source.depth();
			double zHyp = Faults.hypocentralDepth(dip, width, zTop);

			return new HazardInput(
				source.rate(),
				source.magnitude(),
				rJB,
				rRup,
				rX,
				dip,
				width,
				zTop,
				zHyp,
				source.rake(),
				site.vs30,
				site.vsInferred,
				site.z1p0,
				site.z2p5);
		}
	}

	@Deprecated
	private static final class InputGeneratorTask implements Callable<HazardInput> {

		private final Function<SystemSource, HazardInput> calc;
		private final SystemSource source;

		InputGeneratorTask(final Function<SystemSource, HazardInput> calc, final SystemSource source) {
			this.calc = calc;
			this.source = source;
		}

		@Override public HazardInput call() {
			return calc.apply(source);
		}
	}

	private final BitSet bitsetForLocation(final Location loc, final double r) {
		BitSet bits = new BitSet(sections.size());
		int count = 0;
		for (GriddedSurface surface : sections) {
			bits.set(count++, horzDistanceFast(loc, surface.centroid()) <= r);
		}
		return bits;
	}

}
