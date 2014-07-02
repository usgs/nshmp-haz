package org.opensha.eq.forecast;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.opensha.data.DataUtils.validateWeight;
import static org.opensha.eq.Magnitudes.validateMag;
import static org.opensha.eq.fault.Faults.validateDepth;
import static org.opensha.eq.fault.Faults.validateDip;
import static org.opensha.eq.fault.Faults.validateRake;
import static org.opensha.eq.fault.Faults.validateWidth;
import static org.opensha.geo.Locations.horzDistanceFast;
import static org.opensha.util.TextUtils.validateName;

import java.util.BitSet;
import java.util.Iterator;
import java.util.List;

import org.opensha.data.DataUtils;
import org.opensha.eq.fault.scaling.MagScalingType;
import org.opensha.eq.fault.surface.GriddedSurface;
import org.opensha.geo.Location;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.primitives.Doubles;

/**
 * Wrapper class for related {@link IndexedFaultSource}s
 * 
 * @author Peter Powers
 */
public class IndexedFaultSourceSet extends
		AbstractSourceSet<IndexedFaultSourceSet.IndexedFaultSource> {

	// rupture data
	private final List<GriddedSurface> sections;
	private final List<BitSet> bitsets;
	private final List<Double> mags;
	private final List<Double> rates;
	private final List<Double> depths;
	private final List<Double> dips;
	private final List<Double> widths;
	private final List<Double> rakes;

	/*
	 * NOTE: We skip the notion of a Rupture for now. Aleatory uncertainty on
	 * magnitude isn't required, but if it is, we'll alter this implementation
	 * to (somewhere) return List<GmmInput> per source rather than one GmmInput.
	 */

	/*
	 * NOTE: Issues for consideration.
	 * 
	 * For now, the attributes of UC3 indexed fault ruptures are derived from
	 * the binary solution files (area, width, dip, rake). However, for all UC3
	 * calculations to date, width and dip were derived from the values computed
	 * when CompoundSurfaces were initialized; in theory, the values are the
	 * same, but we're waiting for confirmation from Kevin on this. It would be
	 * preferable to not import all the fault section averaging code at present,
	 * although there may be a need for this later.
	 * 
	 * Nevermind (delete above); we don't have rupture dips or widths --
	 * exporter will use compoundSurface code and export values with ruptures
	 */

	private IndexedFaultSourceSet(String name, double weight, GmmSet gmmSet,
		List<GriddedSurface> sections, List<BitSet> bitsets, List<Double> mags, List<Double> rates,
		List<Double> depths, List<Double> dips, List<Double> widths, List<Double> rakes) {

		super(name, weight, MagScalingType.BUILT_IN, gmmSet);

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
		return SourceType.INDEXED_FAULT;
	}

	@Override public int size() {
		return bitsets.size();
	}

	@Override public Iterator<IndexedFaultSource> iterator() {
		return new Iterator<IndexedFaultSource>() {
			int caret = 0;

			@Override public boolean hasNext() {
				return caret < size();
			}

			@Override public IndexedFaultSource next() {
				return new IndexedFaultSource(caret++);
			}

			@Override public void remove() {
				throw new UnsupportedOperationException();
			}
		};
	}

	@Override public Predicate<IndexedFaultSource> distanceFilter(Location loc, double distance) {
		return new DistanceFilter(loc, distance);
	}

	private class DistanceFilter implements Predicate<IndexedFaultSource> {

		private static final String ID = "IndexedFaultSourceSet.DistanceFilter";
		private final Location loc;
		private final double distance;
		private final BitSet sectionBitsForLoc;

		DistanceFilter(Location loc, double distance) {
			this.loc = loc;
			this.distance = distance;
			sectionBitsForLoc = createSectionBitset();
		}

		private BitSet createSectionBitset() {
			BitSet bits = new BitSet(sections.size());
			int count = 0;
			for (GriddedSurface surface : sections) {
				bits.set(count++, horzDistanceFast(loc, surface.centroid()) <= distance);
			}
			return bits;
		}

		@Override public boolean apply(IndexedFaultSource source) {
			return sectionBitsForLoc.intersects(bitsets.get(source.index));
		}

		@Override public String toString() {
			return ID + " [location: " + loc + ", distance: " + distance + "]";
		}
	}

	// TODO package privacy??
	public class IndexedFaultSource implements Source {

		private final int index;

		IndexedFaultSource(int index) {
			this.index = index;
		}

		@Override public int size() {
			return 1;
		}

		@Override public String name() {
			return "Unnamed indexed fault source";
		}

		@Override public Iterator<Rupture> iterator() {
			throw new UnsupportedOperationException();
		}

	}

	// TODO clean
	// Duh - missed intersects method (above)that doesn't require copying
	// bitsets
	/*
	 * Does the work of ANDing bitsets of sections in IndexedFaultSources with
	 * the bitsets corresponding to a pool of IndexedFaultSections to determine
	 * if the supplied source should be used or not.
	 */
	// private final static class IndexedSourceFilter implements
	// Function<IndexedFaultSource, List<Integer>> {
	// private final BitSet includeBits;
	// private final BitSet resultBits;
	// private IndexedSourceFilter(BitSet includeBits) {
	// this.includeBits = includeBits;
	// resultBits = new BitSet(includeBits.size());
	// }
	// @Override
	// public final List<Integer> apply(IndexedFaultSource source) {
	// // before each apply, clear and reset resultBits
	// resultBits.clear();
	// resultBits.or(includeBits);
	// resultBits.and(source.bits); //TODO uncomment and FIX; NEED bitset
	// reference
	// if (resultBits.isEmpty()) return null;
	// return
	// }
	// }

	// private final static class SurfaceFilter implements
	// Predicate<RuptureSurface> {
	// private final Predicate<Location> distFilter;
	// private SurfaceFilter(Location loc, double distance) {
	// distFilter = Locations.distanceFilter(loc, distance);
	// }
	// @Override
	// public final boolean apply(RuptureSurface surface) {
	// return distFilter.apply(surface.centroid());
	// }
	// }
	//

	// immutable ordered list of FaultSubsectionData
	// final List<RuptureSurface> sections;
	// List<IndexedFaultSource> sources;

	// initial implementation:

	// for a given Location, determine which fault sections it contains
	// not sure the fastest way to do this:
	// 1) distance filter subsection centroids
	// 2) filter/list sources that intersect valid sections (BitSet OR )
	// 3) required sections (BitSet OR all subsect BitSets)
	// 4) build distance data for required sections
	// (this has potential optimizations... we really only need rJB and
	// rRup for those sections that are closest to Site. To match UC3
	// we need to calculate Rx using all subSects, but it would probably
	// be better to area-weight average the closest sections)

	// Distance calculation notes
	// We calculate rRup and rJB for all viable (<distance) sections as most
	// will probably be used by at least 1 source. Although one could argue
	// that only rRup or rJb be calculated first, there are geometries for which
	// min(rRup) != min(rJB); e.g. location on hanging wall of dipping fault
	// that abuts a vertical fault... vertical might yield min(rRup) but
	// min(rJB) would be 0 (over dipping fault).
	//
	// For rX, we just use the closest rRup; calculate rXs after doing rJB and
	// rRup.

	// for calcMgr
	//
	//

//	static class IndexedFaultSourceCalc {
//
//		List<IndexedFaultSource> sources;
//
//		// TODO temp
//		int cores = Runtime.getRuntime().availableProcessors();
//		private ExecutorService ex = Executors.newFixedThreadPool(cores);
//
//		Location loc;
//		double distance;
//		List<IndexedFaultSurface> sections; // TODO immutable, stored outside
//											// calcualtor
//
//		// required sections
//
//		BitSet sectionBits;
//
//		// distance Table<DistanceType, SectionIndex, Value>
//		private Table<DistanceType, Integer, Double> rTable;
//
//		public IndexedFaultSourceCalc(Location loc, double distance) {
//			this.loc = loc;
//			this.distance = distance;
//		}
//
//		// set bits of sections within distance
//		private void calc(Site site) throws InterruptedException, ExecutionException {
//			// NOTE: actual BitSet may be longer (see API docs)
//			sectionBits = new BitSet(sections.size());
//			CompletionService<Distances> dCS = new ExecutorCompletionService<Distances>(ex);
//
//			// build bitset of section indices to use and submit distance calcs
//			SurfaceFilter filter = new SurfaceFilter(loc, distance);
//			for (IndexedFaultSurface subsection : Iterables.filter(sections, filter)) {
//				sectionBits.set(subsection.index());
//				// pass subsection to executor
//				dCS.submit(Transforms.newDistanceCalc(subsection, loc));
//			}
//
//			// build receiver Table -- must be done after sectionBits are set
//			// we do not ever copy this to an ImmutableTable because it is never
//			// exposed, however, caution should be exercised so as not to
//			// modify it inadvertantly
//			rTable = ArrayTable.create(EnumSet.allOf(DistanceType.class),
//				DataUtils.bitsToIndices(sectionBits));
//
//			// use cardinality of bitset do collect distance calc results
//			int sectCount = sectionBits.cardinality();
//			for (int i = 0; i < sectCount; i++) {
//				Distances dist = dCS.take().get();
//				rTable.put(R_JB, dist.index, dist.rJB);
//				rTable.put(R_RUP, dist.index, dist.rRup);
//				rTable.put(R_X, dist.index, dist.rX);
//			}
//
//			// TODO ?? make rTable immutable at this point; would require
//			// copying
//
//			CompletionService<GmmInput> gmSrcCS = new ExecutorCompletionService<GmmInput>(ex);
//
//			// assemble GmmInput data for required sources
//			IndexedSourceFilter srcFilter = new IndexedSourceFilter(sectionBits);
//			int gmSrcCount = 0;
//			for (IndexedFaultSource source : sources) {
//				List<Integer> sectionIDs = srcFilter.apply(source);
//				if (sectionIDs != null) {
//					// TODO uncomment and fix
//					// gmSrcCS.submit(Transforms.newIndexedFaultCalcInitializer(source,
//					// site, rTable, sectionIDs));
//					gmSrcCount++;
//				}
//			}
//
//			// rest of calc should be same as for fault now that we have a CS
//			// from which we can take() GMM_Sources
//
//		}
//
//	}

	/*
	 * Quirky behavior: Note that sections() must be called before any calls to
	 * indices(). All indices and data fields should be repeatedly called in
	 * order to ensure correctly ordered fields when iterating ruptures.
	 */
	static class Builder {

		// build() may only be called once
		// use Doubles to ensure fields are initially null

		// Unfiltered UCERF3: FM31 = 253,706 FM32 = 305,709
		static final int RUP_SET_SIZE = 306000;

		static final String ID = "IndexedFaultSourceSet.Builder";
		boolean built = false;

		private String name;
		private Double weight;
		private GmmSet gmmSet;

		private List<GriddedSurface> sections;
		private final List<BitSet> bitsets = Lists.newArrayListWithCapacity(RUP_SET_SIZE);
		private final List<Double> mags = Lists.newArrayListWithCapacity(RUP_SET_SIZE);
		private final List<Double> rates = Lists.newArrayListWithCapacity(RUP_SET_SIZE);
		private final List<Double> depths = Lists.newArrayListWithCapacity(RUP_SET_SIZE);
		private final List<Double> dips = Lists.newArrayListWithCapacity(RUP_SET_SIZE);
		private final List<Double> widths = Lists.newArrayListWithCapacity(RUP_SET_SIZE);
		private final List<Double> rakes = Lists.newArrayListWithCapacity(RUP_SET_SIZE);

		Builder name(String name) {
			this.name = validateName(name);
			return this;
		}

		Builder weight(double weight) {
			this.weight = validateWeight(weight);
			return this;
		}

		Builder gmms(GmmSet gmmSet) {
			this.gmmSet = checkNotNull(gmmSet);
			return this;
		}

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

		void validateState(String id) {
			checkState(!built, "This %s instance as already been used", id);
			checkState(name != null, "%s name not set", id);
			checkState(weight != null, "%s weight not set", id);
			checkState(gmmSet != null, "%s ground motion models not set", id);

			checkState(sections.size() > 0, "%s no sections added", id);
			checkState(bitsets.size() > 0, "%s no index lists added", id);

			int target = bitsets.size();
			checkSize(mags.size(), target, id, "magnitudes");
			checkSize(rates.size(), target, id, "rates");
			checkSize(depths.size(), target, id, "depths");
			checkSize(dips.size(), target, id, "dips");
			checkSize(widths.size(), target, id, "widths");
			checkSize(rakes.size(), target, id, "rakes");
			built = true;
		}

		private static void checkSize(int size, int target, String classId, String dataId) {
			checkState(size == target, "%s too few %s [%s of %s]", classId, dataId, size, target);
		}

		// TODO consider wrapping Doubles.asList() Lists in
		// ForwardingList that overrides set(int, double)

		IndexedFaultSourceSet build() {
			validateState(ID);

			// @formatter:off
			return new IndexedFaultSourceSet(
				name, weight, gmmSet,
				ImmutableList.copyOf(sections),
				ImmutableList.copyOf(bitsets),
				Doubles.asList(Doubles.toArray(mags)),
				Doubles.asList(Doubles.toArray(rates)),
				Doubles.asList(Doubles.toArray(depths)),
				Doubles.asList(Doubles.toArray(dips)),
				Doubles.asList(Doubles.toArray(widths)),
				Doubles.asList(Doubles.toArray(rakes)));
			// @formatter:off
		}

	}
	
}
