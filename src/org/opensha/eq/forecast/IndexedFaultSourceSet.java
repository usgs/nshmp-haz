package org.opensha.eq.forecast;

import static org.opensha.eq.forecast.DistanceType.*;

import java.util.BitSet;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.opensha.calc.Site;
import org.opensha.calc.tasks.Tasks;
import org.opensha.data.DataUtils;
import org.opensha.eq.fault.surface.IndexedFaultSurface;
import org.opensha.eq.fault.surface.RuptureSurface;
import org.opensha.geo.Location;
import org.opensha.geo.Locations;
import org.opensha.gmm.GMM_Source;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.ArrayTable;
import com.google.common.collect.Iterables;
import com.google.common.collect.Table;

/**
 * Add comments here
 *
 * @author Peter Powers
 */
public class IndexedFaultSourceSet {

	// immutable ordered list of FaultSubsectionData
	//	final List<RuptureSurface> sections;
//	List<IndexedFaultSource> sources;
	
	// initial implementation:
	
	// for a given Location, determine which fault sections it contains
	// not sure the fastest way to do this:
	//		1) distance filter subsection centroids
	//		2) filter/list sources that intersect valid sections (BitSet OR )
	//		3) required sections (BitSet OR all subsect BitSets)
	//		4) build distance data for required sections
	//			(this has potential optimizations... we really only need rJB and
	//			rRup for those sections that are closest to Site. To match UC3
	//			we need to calculate Rx using all subSects, but it would probably
	//			be better to area-weight average the closest sections)
	
	
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
	
	
	static class IndexedFaultSourceCalc {
		
		List<IndexedFaultSource> sources;
		
		// TODO temp
		int cores = Runtime.getRuntime().availableProcessors();
		private ExecutorService ex = Executors.newFixedThreadPool(cores);
		
		Location loc;
		double distance;
		List<IndexedFaultSurface> sections; // TODO immutable, stored outside calcualtor
		
		// required sections
		
		BitSet sectionBits;
		
		// distance Table<DistanceType, SectionIndex, Value>
		private Table<DistanceType, Integer, Double> rTable;
		
		public IndexedFaultSourceCalc(Location loc, double distance) {
			this.loc = loc;
			this.distance = distance;
		}
		
		// set bits of sections within distance
		private void calc(Site site) throws InterruptedException, ExecutionException {
			// NOTE: actual BitSet may be longer (see API docs)
			sectionBits = new BitSet(sections.size());
			CompletionService<Distances> dCS = new ExecutorCompletionService<Distances>(ex);

			// build bitset of section indices to use and submit distance calcs
			SurfaceFilter filter = new SurfaceFilter(loc, distance);
			for (IndexedFaultSurface subsection : Iterables.filter(sections, filter)) {
				sectionBits.set(subsection.index());
				// pass subsection to executor
				dCS.submit(Tasks.newDistanceCalc(subsection, loc));
			}

			// build receiver Table -- must be done after sectionBits are set
			// we do not ever copy this to an ImmutableTable because it is never
			// exposed, however, caution should be exercised so as not to
			// modify it inadvertantly
			rTable = ArrayTable.create(
				EnumSet.allOf(DistanceType.class),
				DataUtils.bitsToIndices(sectionBits));

			// use cardinality of bitset do collect distance calc results
			int sectCount = sectionBits.cardinality();
			for (int i = 0; i < sectCount; i++) {
				Distances dist = dCS.take().get();
				rTable.put(R_JB, dist.index, dist.rJB);
				rTable.put(R_RUP, dist.index, dist.rRup);
				rTable.put(R_X, dist.index, dist.rX);
			}
			
			// TODO ?? make rTable immutable at this point; would require copying
			
			CompletionService<GMM_Source> gmSrcCS = 
					new ExecutorCompletionService<GMM_Source>(ex);

			// assemble GMM_Source data for required sources
			IndexedSourceFilter srcFilter = new IndexedSourceFilter(sectionBits);
			int gmSrcCount = 0;
			for (IndexedFaultSource source : sources) {
				List<Integer> sectionIDs = srcFilter.apply(source);
				if (sectionIDs != null) {
					gmSrcCS.submit(Tasks.newIndexedFaultCalcInitializer(source, site, rTable, sectionIDs));
					gmSrcCount++;
				}
			}
			
			// rest of calc should be same as for fault now that we have a CS
			// from which we can take() GMM_Sources

		}
		
	}
	
	/*
	 * Does the work of ANDing bitsets of sections in IndexedFaultSources with
	 * the bitsets corresponding to a pool of IndexedFaultSections to determine
	 * if the supplied source should be used or not.
	 */
	private final static class IndexedSourceFilter implements
			Function<IndexedFaultSource, List<Integer>> {
		private final BitSet includeBits;
		private final BitSet resultBits;
		private IndexedSourceFilter(BitSet includeBits) {
			this.includeBits = includeBits;
			resultBits = new BitSet(includeBits.size());
		}
		@Override
		public final List<Integer> apply(IndexedFaultSource source) {
			// before each apply, clear and reset resultBits
			resultBits.clear();
			resultBits.or(includeBits);
			resultBits.and(source.bits);
			if (resultBits.isEmpty()) return null;
			return DataUtils.bitsToIndices(resultBits);
		}
	}
	
	private final static class SurfaceFilter implements Predicate<RuptureSurface> {
		private final Predicate<Location> distFilter;
		private SurfaceFilter(Location loc, double distance) {
			distFilter = Locations.distanceFilter(loc, distance);
		}
		@Override
		public final boolean apply(RuptureSurface surface) {
			return distFilter.apply(surface.centroid());
		}
	}
	
}
