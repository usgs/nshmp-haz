package org.opensha.eq.forecast;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.opensha.eq.fault.Faults.validateStrike;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;

import org.opensha.eq.fault.FocalMech;
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
	
	// NOTE we're holding onto weight for reference, however, MFD
	// rates will have already been scaled in place. The weight value
	// may come in handy when trying to put together individual
	// logic tree branches

	// only available to parsers
	GridSourceSet(String name, Double weight, List<Location> locs,
		List<IncrementalMFD> mfds, MagScalingType magScaling, Map<FocalMech, Double> mechMap,
		NavigableMap<Double, Map<Double, Double>> magDepthMap) {

		this.name = name;
		this.weight = weight;
		this.locs = locs;
		this.mfds = mfds;
		this.magScaling = magScaling;
		this.mechMap = mechMap;
		this.magDepthMap = magDepthMap;
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
	public Iterator<PointSource> iterator() {
		return null;
	}
	
	// TODO grid sources allow for a single depth to be defined or 2 depths
	// with some cutoff magnitude between the two, no weighting; this should be
	// migrated to a mag-dependent depth weight map


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
	 * @formatting:off
	 * 
	 * GridSourceSets store lookup arrays for mfd magnitude indexing, depths,
	 * and depth weights. These arrays remove the need to do expensive lookups
	 * in a magDepthMap when iterating grid sources and ruptures. These are
	 * generally longer than required by grid source implementations as they
	 * span [mMin maxmMaxMag] of the entire GridSourceSet. Implementations will
	 * only ever reference those indices up to their individual mMax and
	 * obviating the need for individual sources to store these arrays, which
	 * would incur a lot of overhead for large (million+ node) GridSourceSets.
	 * 
	 * Given magDepthMap:
	 * [6.5 :: [1.0:0.4, 3.0:0.5, 5.0:0.1]; 10.0 :: [1.0:0.1, 5.0:0.9]]
	 * 
	 * and MFD:
	 * [5.0 5.5, 6.0, 6.5, 7.0]
	 * 
	 * The number of mag-depth combinations a grid source would iterate over is
	 * sum(m = MFD.mag(i) * nDepths(m)) = 3 * 3 + 2 * 2 = 13
	 * 
	 * (note: mag cutoffs in magDepthMap are always used as m < cutoff)
	 * 
	 * magDepthIndices[] : magnitude index in original MFD
	 * [  0,   0,   0,   1,   1,   1,   2,   2,   2,   3,   3,   4,   4]
	 * 
	 * magDepthDepths[] : depth for index
	 * [1.0, 3.0, 5.0, 1.0, 3.0, 5.0, 1.0, 3.0, 5.0, 1.0, 5.0, 1.0, 5.0]
	 * 
	 * magDepthWeights[] : depth weight for index
	 * [0.4, 0.5, 0.1, 0.4, 0.5, 0.1, 0.4, 0.5, 0.1, 0.1, 0.9, 0.1, 0.9]
	 * 
	 * @formatting:on
	 */
	int[] magDepthIndices;
	double[] magDepthDepths;
	double[] magDepthWeights;
	
	// TODO need to determine absolute mMax over all nodes on initialization
	// see builder/parser
			
	// TODO create master MFD for index arrays
	private IncrementalMFD masterMFD = null;
	
	private void initMagDepthData() {
		List<Integer> indices = Lists.newArrayList();
		List<Double> depths = Lists.newArrayList();
		List<Double> weights = Lists.newArrayList();
		for (int i = 0; i < masterMFD.getNum(); i++) {
			for (Map.Entry<Double, Double> entry : magDepthMap
				.higherEntry(masterMFD.getX(i)).getValue().entrySet()) {
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
		
	
	/*
	 * GridSourceSet builder; build() may only be called once; uses Doubles
	 * to ensure fields are initially null.
	 */
	static class Builder {

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
			checkArgument(weight >= 0.0 && weight <= 1.0, "weight [%s] must be between [0 1]",
				weight);
			this.weight = weight;
			return this;
		}
		
		Builder strike(double strike) {
			this.strike = validateStrike(strike);
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
			return new GridSourceSet(name, weight, locs, mfds, magScaling, mechMap, magDepthMap);
		}
		
	}

}
