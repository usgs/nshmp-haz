package org.opensha.eq.forecast;

import java.io.File;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Iterators;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Sets;

/**
 * A {@code Forecast} is the top-level wrapper for earthquake source definitions
 * used in probabilisitic seismic hazard analyses and calculations (PSHAs). A
 * {@code Forecast} contains of a number of {@code SourceSet}s that define
 * logical groupings of sources by type and iterates over them in the order that
 * {@code SourceType}s are declared.
 * SourceSets by type
 * 
 * ruptures to be used in a .
 * 
 * <p>Iteration of {@code SourceSet}s occurs in the order that {@code SourceType}s
 * are declared in their {@code enum} class and then by the order they were added
 * to this {@code Forecast}</p>
 * 
 * @author Peter Powers
 * @see SourceSet
 * @see SourceType
 */
//public final class Forecast<T extends SourceSet<? extends Source>> implements
//		Iterable<T> {

public final class Forecast implements Iterable<SourceSet<? extends Source>> {

	// TODO SUB check rake handling
	// TODO SUB test trace.size == lowerTrace.size
	// TODO SUB merge | multiply weight and rate
	// TODO SUB reverse subduction trace defs for 2014; they are NOT rhr
	// TODO SUB different MSRs are specified in 2014 configs (geomatrix alone
	//			no longer used); check that this has carried through
	//			correctly to 2008
	//
	// TODO specify mag-scaling relations in config files for floating rupture sizes
	//			GR implicitely floats and requires MagScalingType
	//			CH floating may be specified and would reuire MagScalingType -- note
	//			FaultSourceParser.buildSingle() ignores 'floats'
	//
	// TODO ALWAYS USE WEIGHTS -- parsers should throw error if missing
	// TODO UCERF3 xml (indexedFaultSource) needs to have aftershock correction
	//			either imposed in file (better) of be imlemented in Parser
	//			or Loader (worse)
	//
	// TODO CEUS mMax zoning
	// TODO depth varying deep source; zones --> parse
	// TODO do deep GMMs saturate/apped at 7.8 ?? I believe all are but need to ensure
	// TODO perhaps add notes on CEUS fixed strike sources having vertical faults with no mechanism (strike-slip implied)
	//			rJB, rRup, rX all based on line regardeless of dip
	// TODO revisit cleanStrikeSlip in FautlConverter. Is this necessary any more?
	// TODO add names to SourceSets
	// TODO are any M=6.0 finite source representation cutoffs being used in 2014 fortran
	// TODO need to implement masking of CA shear sources in XML; only keep those that are outside
	//			CA boundary
	// TODO consider optional depth attribute for FaultSource <Geometry> that could be used to place upperSeisDepth
	//			below (possibly buried) upper fault trace (effectively migrating top of rupture in dip direction)
	//			NSHMP uses trace top
	// TODO New Madrid cluster model - check 2014 SSC cluster model (from notes: long vs. short ?? 3 fualts?)
	// TODO Wasatch cluster model - branches on dip rather than location
	// TODO check if AtkinsonMacias using BooreAtkin siteAmp to get non-rock site response
	// TODO deep sources: dtor matrix keyed to longitude?? is this specified in config files?
	// TODO check ORegon branches: Portland nested inside all OR?
	//
	// TODO should MFD weights in XML ever be allowed to be skipped, or should we always be ultra explicit ??
	// TODO grid <defaults> don't need 'floats' attribute
	// TODO review MFD defaults, which fields MUST be defined?
	// TODO revisit weights ingrid sources; do grid sources allow multiple defautls? methinks yes.
	//
	// TODO DeltaC1 implementation in BC_Hydro
	

	// sourceSet type map
	private Multimap<SourceType, SourceSet<? extends Source>> srcTypeMap = 
			ArrayListMultimap.create();
	
//	private Iterable<SourceSet<? extends Source>> sources;
	
	/**
	 * Initializes a {@code Forecast} from a directory structure of
	 * configuration files.
	 * @param dir
	 */
	public static void create(File dir) {
		
	}
	
	public static Forecast fromSingleSourceSet(String path) {
		FaultSourceSet fss = Loader.load(path);
		Forecast f = new Forecast();
		f.srcTypeMap.put(fss.type(), fss);
		return f;
	}
	
	//	private 
	public int size() {
		return 0;
	}


	// should be able to provide 'filteredIterator' method, the iterator() method
	// would delegate to the filtered variant with a null or 'do nothing' filter
	
	// 
//	public Iterable<SourceSet> filteredIterable(Set<SourceType> types) {
//		
//		return null;
//	}
	
	@Override
	public Iterator<SourceSet<? extends Source>> iterator() {
		Set<SourceType> types = Sets.newEnumSet(srcTypeMap.keySet(),
			SourceType.class);
		List<Iterator<SourceSet<? extends Source>>> iterators = Lists
			.newArrayList();
		for (SourceType type : types) {
			iterators.add(srcTypeMap.get(type).iterator());
		}
		return Iterators.concat(iterators.iterator());
	}
	
}
