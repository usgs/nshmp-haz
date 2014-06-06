package org.opensha.eq.forecast;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.opensha.eq.fault.Faults.validateStrike;

import static org.opensha.data.DataUtils.validateWeight;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;

import org.opensha.eq.fault.FocalMech;
import org.opensha.eq.fault.scaling.MagLengthRelationship;
import org.opensha.eq.fault.scaling.MagScalingRelationship;
import org.opensha.eq.fault.scaling.MagScalingType;
import org.opensha.geo.Location;
import org.opensha.mfd.IncrementalMFD;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.primitives.Doubles;
import com.google.common.primitives.Ints;

/**
 * A set of evenly spaced (point) {@code Source}s with varying magnitudes
 * derived a GR MFD at each grid node.
 * @author Peter Powers
 */
public class GridSourceSet implements SourceSet<PointSource> {

	private final String name;
	private final double weight;
	private final List<Location> locs;
	private final List<IncrementalMFD> mfds;
	private final MagScalingType magScaling;
	private final Map<FocalMech, Double> mechMap;
	private final NavigableMap<Double, Map<Double, Double>> magDepthMap;
	private final double strike;
	
	final MagLengthRelationship mlr;
	
	// TODO tmp
	PointSourceType ptSrcType = PointSourceType.FIXED_STRIKE;
	
	// NOTE we're holding onto weight for reference, however, MFD
	// rates will have already been scaled in place. The weight value
	// may come in handy when trying to put together individual
	// logic tree branches

	// only available to parsers
	private GridSourceSet(String name, Double weight, List<Location> locs,
		List<IncrementalMFD> mfds, MagScalingType magScaling, Map<FocalMech, Double> mechMap,
		NavigableMap<Double, Map<Double, Double>> magDepthMap, double strike) {

		this.name = name;
		this.weight = weight;
		this.locs = locs;
		this.mfds = mfds;
		this.magScaling = magScaling;
		this.mechMap = mechMap;
		this.magDepthMap = magDepthMap;
		this.strike = strike;
		
		MagScalingRelationship msr = magScaling.instance();
		// TODO need to develop standard approach to using mag area relationships
		checkState(msr instanceof MagLengthRelationship,
			"Only mag-length relationships are supported at this time");
		mlr = (MagLengthRelationship) msr;
	}
	
	@Override
	public String name() {
		return name;
	}

	@Override
	public SourceType type() {
		return SourceType.GRID;
	}
	
	@Override
	public int size() {
		return locs.size();
	}

	@Override
	public double weight() {
		return weight;
	}

	@Override
	public Iterable<PointSource> locationIterable(Location loc) {
		// TODO
		return null;
	}	
	
	@Override
	public Iterator<PointSource> iterator() {
		// @formatter:off
		return new Iterator<PointSource>() {
			int caret = 0;
			@Override public boolean hasNext() { return caret < locs.size(); }
			@Override public PointSource next() { return getSource(caret++); }
			@Override public void remove() { throw new UnsupportedOperationException(); }
		};
		// @formatter:on
	}
	
	private PointSource getSource(int idx) {
		switch(ptSrcType) {
			case POINT:
				return new PointSource(this, locs.get(idx), mfds.get(idx), mechMap);
			case FINITE:
				return new PointSourceFinite(this, locs.get(idx), mfds.get(idx), mechMap);
			case FIXED_STRIKE:
				return new PointSourceFixedStrike(this, locs.get(idx), mfds.get(idx), mechMap, strike);
		}
		return null;
	}

	public Iterator<Source> iteratorForLocation(Location loc, double dist) {
		// compute min-max lat and lon
		// TODO what happens when distance exceeds allowed lat lon range

		// create radian-based location bounding rect; using Region is overkill
		// to simply perform contains testing
		
//		return new Iterator() {
		// Location swCorner = Location.create(minLatLoc.lat(),
		// minLonLoc.lon());

		// double maxLat

		// TODO gridSourceSet should have a Region defined
		// pre-get the Rectangle2D bounds
		// can do intersects test between source region and location rect
		// perhaps Forecast gridSrcSet iterator also takes a location

//		return Iterators.filter(iterator, predicate)
		return null;
	}
	
	// can/should be passed in from GridSourceSet; should be final
	// length can be larger than magDepthCount
	// only up to magDepthIndices[magDepthCount] will be used
	// use to find the correct original mfd index
	
	/*
	 * @formatter:off
	 * 
	 * GridSourceSets store lookup arrays for mfd magnitude indexing, depths,
	 * and depth weights. These arrays remove the need to do expensive lookups
	 * in a magDepthMap when iterating grid sources and ruptures. These are
	 * generally longer than required by grid source implementations as they
	 * span [mMin maxmMaxMag] of the entire GridSourceSet. Implementations will
	 * only ever reference those indices up to their individual mMax, obviating
	 * the need for individual sources to store these arrays, which would incur
	 * a lot of overhead for large (million+ node) GridSourceSets.
	 * 
	 * Given magDepthMap: [6.5 :: [1.0:0.4, 3.0:0.5, 5.0:0.1]; 10.0 :: [1.0:0.1, 5.0:0.9]]
	 * 
	 * and MFD: [5.0, 5.5, 6.0, 6.5, 7.0]
	 * 
	 * The number of mag-depth combinations a grid source would iterate over is:
	 *     sum(m = MFD.mag(i) * nDepths(m)) = 3 * 3 + 2 * 2 = 13
	 * 
	 * (note: mag cutoffs in magDepthMap are always used as m < cutoff)
	 * 
	 * magDepthIndices[] : magnitude index in original MFD [ 0, 0, 0, 1, 1, 1, 2, 2, 2, 3, 3, 4, 4]
	 * 
	 * magDepthDepths[] : depth for index [1.0, 3.0, 5.0, 1.0, 3.0, 5.0, 1.0, 3.0, 5.0, 1.0, 5.0, 1.0, 5.0]
	 * 
	 * magDepthWeights[] : depth weight for index [0.4, 0.5, 0.1, 0.4, 0.5, 0.1, 0.4, 0.5, 0.1, 0.1, 0.9, 0.1, 0.9]
	 * 
	 * @formatter:on
	 */
	
	
	int[] magDepthIndices;
	double[] magDepthDepths;
	double[] magDepthWeights;
	
	// TODO need to determine absolute mMax over all nodes on initialization
	// see builder/parser
			
	// TODO create master MFD for index arrays; actually don't need an mfd just the 
	// array of magnitudes to pull the correct depth distributions below
	private IncrementalMFD masterMFD = null;
	
	private void initMagDepthData() {
		List<Integer> indices = Lists.newArrayList();
		List<Double> depths = Lists.newArrayList();
		List<Double> weights = Lists.newArrayList();
		for (int i = 0; i < masterMFD.getNum(); i++) {
			double mag = masterMFD.getX(i);
			Map.Entry<Double, Map<Double, Double>> magEntry = magDepthMap.higherEntry(mag);
			for (Map.Entry<Double, Double> entry : magEntry.getValue().entrySet()) {
				indices.add(i);
				depths.add(entry.getKey());
				weights.add(entry.getValue());
			}
		}
		magDepthIndices = Ints.toArray(indices);
		magDepthDepths = Doubles.toArray(depths);
		magDepthWeights = Doubles.toArray(weights);
	}

	
	// TODO MagDepthMap
	// 	-- each magnitude cutoff has associated map of depths and weights
	//	-- mags as keys? could have problems but may be ok if only accessed
	//		when using keySet or entrySet
	// Map<Double, Map<Double, Double>>  <-- Map<magCutoff, Map<depth, weight>
	//  -- depth maps sorted 
	//
	
	// single depth: [10.0 :: [depth : 1.0 ]]
	// NSHMP depths: [6.5  :: [1.0 : 0.0, 5.0 : 1.0], 10.0 :: [1.0 : 1.0, 5.0 : 0.0]]
		
	
	static class Builder {

		// build() may only be called once
		// use Double to ensure fields are initially null

		private static final String ID = "GridSourceSet.Builder";
		private boolean built = false;

		private String name;
		private Double weight;
		private Double strike;
		private MagScalingType magScaling;
		private NavigableMap<Double, Map<Double, Double>> magDepthMap;
		private Map<FocalMech, Double> mechMap;

		private List<Location> locs = Lists.newArrayList();
		private List<IncrementalMFD> mfds = Lists.newArrayList();
		
		Builder name(String name) {
			checkArgument(!Strings.nullToEmpty(name).trim().isEmpty(),
				"Name may not be empty or null");
			this.name = name;
			return this;
		}
		
		Builder weight(double weight) {
			this.weight = validateWeight(weight);
			return this;
		}
		
		Builder strike(double strike) {
			// unkown strike allowed for grid sources
			this.strike = Double.isNaN(strike)? strike : validateStrike(strike);
			return this;
		}

		
		Builder magScaling(MagScalingType magScaling) {
			this.magScaling = checkNotNull(magScaling, "");
			return this;
		}
		
		Builder depthMap(NavigableMap<Double, Map<Double, Double>> magDepthMap) {
			// the structure of the map will have been fully validated by parser
			// TODO at a minimum there must be one mag key that is >= MAX_MAG
			this.magDepthMap = checkNotNull(magDepthMap);
			return this;
		}
				
		Builder mechs(Map<FocalMech, Double> mechs) {
			this.mechMap = checkNotNull(mechs);
			return this;
		}

		Builder location(Location loc, IncrementalMFD mfd) {
			this.mfds.add(checkNotNull(mfd, "MFD is null"));
			this.locs.add(checkNotNull(loc, "Location is null"));
			return this;
		}
		
		GridSourceSet build() {
			checkState(!built, "This %s instance as already been used", ID);

			checkState(name != null, "%s name not set", ID);
			checkState(weight != null, "%s weight not set", ID);
			checkState(strike != null, "%s strike not set", ID);
			checkState(!locs.isEmpty(), "%s has no locations", ID);
			checkState(!mfds.isEmpty(), "%s has no MFDs", ID);
			checkState(magScaling != null, "%s has no mag-scaling relation set", ID);
			checkState(magDepthMap != null, "%s mag-depth-weight map not set", ID);
			checkState(mechMap != null, "%s focal mech map not set", ID);
			
			built = true;
			return new GridSourceSet(name, weight, locs, mfds, magScaling, mechMap, magDepthMap,
				strike);
		}
		
	}

}
