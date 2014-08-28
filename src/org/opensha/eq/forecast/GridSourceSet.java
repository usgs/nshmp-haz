package org.opensha.eq.forecast;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.opensha.data.DataUtils.validateWeight;
import static org.opensha.eq.Magnitudes.MAX_MAG;
import static org.opensha.eq.fault.Faults.validateStrike;
import static org.opensha.util.TextUtils.validateName;
import static org.opensha.eq.forecast.PointSourceType.*;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;

import org.opensha.eq.fault.Faults;
import org.opensha.eq.fault.FocalMech;
import org.opensha.eq.fault.scaling.MagLengthRelationship;
import org.opensha.eq.fault.scaling.MagScalingRelationship;
import org.opensha.eq.fault.scaling.MagScalingType;
import org.opensha.geo.Location;
import org.opensha.geo.Locations;
import org.opensha.mfd.IncrementalMfd;

import com.google.common.base.Predicate;
import com.google.common.collect.Lists;
import com.google.common.primitives.Doubles;
import com.google.common.primitives.Ints;

/**
 * A wrapper class for related, evenly-spaced {@link PointSource}s with varying
 * magnitudes derived from a {@link IncrementalMfd} at each grid node.
 * 
 * @author Peter Powers
 */
public class GridSourceSet extends AbstractSourceSet<PointSource> {

	private final List<Location> locs;
	private final List<IncrementalMfd> mfds;
	private final List<Map<FocalMech, Double>> mechMaps;
	 final NavigableMap<Double, Map<Double, Double>> magDepthMap;
	private final double strike;
	final List<Double> magMaster;

	/*
	 * Most grid sources have the same focal mech map everywhere; in these
	 * cases, mechMaps will have been created using Collections.nCopies() with
	 * minimal overhead.
	 */

	final MagLengthRelationship mlr;

	PointSourceType ptSrcType;

	// only available to parsers
	private GridSourceSet(String name, Double weight, MagScalingType msrType, GmmSet gmmSet,
		List<Location> locs, List<IncrementalMfd> mfds, List<Map<FocalMech, Double>> mechMaps,
		NavigableMap<Double, Map<Double, Double>> magDepthMap, double strike, List<Double> magMaster) {

		super(name, weight, msrType, gmmSet);
		this.locs = locs;
		this.mfds = mfds;
		this.mechMaps = mechMaps;
		this.magDepthMap = magDepthMap;
		this.strike = strike;
		this.magMaster = magMaster;
		initMagDepthData();

		MagScalingRelationship msr = msrType.instance();
		// TODO need to develop standard approach to using mag area
		// relationships
		checkState(msr instanceof MagLengthRelationship,
			"Only mag-length relationships are supported at this time");
		mlr = (MagLengthRelationship) msr;

		ptSrcType = Double.isNaN(strike) ? FINITE : FIXED_STRIKE;
	}

	@Override public SourceType type() {
		return SourceType.GRID;
	}

	@Override public int size() {
		return locs.size();
	}

	@Override public Predicate<PointSource> distanceFilter(final Location loc, final double distance) {
		return new Predicate<PointSource>() {
			private final Predicate<Location> filter = Locations.distanceAndRectangleFilter(loc,
				distance);

			@Override public boolean apply(PointSource source) {
				return filter.apply(source.loc);
			}
			
			@Override public String toString() {
				return "GridSourceSet.DistanceFilter[ " + filter.toString() + " ]";
			}
		};
	}

	@Override public Iterator<PointSource> iterator() {
		return new Iterator<PointSource>() {
			int caret = 0;

			@Override public boolean hasNext() {
				return caret < locs.size();
			}

			@Override public PointSource next() {
				return getSource(caret++);
			}

			@Override public void remove() {
				throw new UnsupportedOperationException();
			}
		};
	}

	private PointSource getSource(int idx) {
		switch (ptSrcType) {
			case POINT:
				return new PointSource(this, locs.get(idx), mfds.get(idx), mechMaps.get(idx));
			case FINITE:
				return new PointSourceFinite(this, locs.get(idx), mfds.get(idx), mechMaps.get(idx));
			case FIXED_STRIKE:
				return new PointSourceFixedStrike(this, locs.get(idx), mfds.get(idx),
					mechMaps.get(idx), strike);
		}
		return null;
	}

	// @formatter:off
	
	/*
	 * GridSourceSets store lookup arrays for mfd magnitude indexing, depths,
	 * and depth weights. These arrays remove the need to do expensive lookups
	 * in a magDepthMap when iterating grid sources and ruptures. These are
	 * generally longer than required by grid source implementations as they
	 * span [mMin mMax] of the entire GridSourceSet. Implementations will
	 * only ever reference those indices up to their individual mMax, obviating
	 * the need for individual sources to store these arrays.
	 * 
	 * Given magDepthMap: [6.5 :: [1.0:0.4, 3.0:0.5, 5.0:0.1]; 10.0 :: [1.0:0.1, 5.0:0.9]]
	 * 
	 * and MFD: [5.0, 5.5, 6.0, 6.5, 7.0]
	 * 
	 * The number of mag-depth combinations a grid source would iterate over is:
	 * sum(m = MFD.mag(i) * nDepths(m)) = 3 * 3 + 2 * 2 = 13
	 * 
	 * (note: mag cutoffs in magDepthMap are always used as m < cutoff)
	 * 
	 * magDepthIndices[] : magnitude index in original MFD [ 0, 0, 0, 1, 1, 1, 2, 2, 2, 3, 3, 4, 4]
	 * 
	 * magDepthDepths[] : depth for index [1.0, 3.0, 5.0, 1.0, 3.0, 5.0, 1.0, 3.0, 5.0, 1.0, 5.0, 1.0, 5.0]
	 * 
	 * magDepthWeights[] : depth weight for index [0.4, 0.5, 0.1, 0.4, 0.5, 0.1, 0.4, 0.5, 0.1, 0.1, 0.9, 0.1, 0.9]
	 * 
	 */

	// @formatter:on

	// available to point source implementations
	List<Integer> magDepthIndices;
	List<Double> magDepthDepths;
	List<Double> magDepthWeights;

	private void initMagDepthData() {
		List<Integer> indices = Lists.newArrayList();
		List<Double> depths = Lists.newArrayList();
		List<Double> weights = Lists.newArrayList();
		for (int i = 0; i < magMaster.size(); i++) {
			Map.Entry<Double, Map<Double, Double>> magEntry = magDepthMap.higherEntry(magMaster
				.get(i));
			for (Map.Entry<Double, Double> entry : magEntry.getValue().entrySet()) {
				indices.add(i);
				depths.add(entry.getKey());
				weights.add(entry.getValue());
			}
		}
		magDepthIndices = Ints.asList(Ints.toArray(indices));
		magDepthDepths = Doubles.asList(Doubles.toArray(depths));
		magDepthWeights = Doubles.asList(Doubles.toArray(weights));
	}

	// TODO MagDepthMap
	// -- each magnitude cutoff has associated map of depths and weights
	// -- mags as keys? could have problems but may be ok if only accessed
	// when using keySet or entrySet
	// Map<Double, Map<Double, Double>> <-- Map<magCutoff, Map<depth, weight>
	// -- depth maps sorted
	//

	// single depth: [10.0 :: [depth : 1.0 ]]
	// NSHMP depths: [6.5 :: [1.0 : 0.0, 5.0 : 1.0], 10.0 :: [1.0 : 1.0, 5.0 :
	// 0.0]]

	// Builder accomodates overriding a default mechMap to support UC3
	// grid sources; may add others later TODO document

	/* Single use builder. */
	static class Builder {

		// build() may only be called once
		// use Double to ensure fields are initially null

		private static final String ID = "GridSourceSet.Builder";
		private boolean built = false;

		private String name;
		private Double weight;
		private Double strike;
		private MagScalingType magScaling;
		private GmmSet gmmSet;
		private NavigableMap<Double, Map<Double, Double>> magDepthMap;
		private Map<FocalMech, Double> mechMap;

		private List<Location> locs = Lists.newArrayList();
		private List<IncrementalMfd> mfds = Lists.newArrayList();
		private List<Map<FocalMech, Double>> mechMaps = Lists.newArrayList();
		private List<Double> magMaster;

		Builder name(String name) {
			this.name = validateName(name);
			return this;
		}

		Builder weight(double weight) {
			this.weight = validateWeight(weight);
			return this;
		}

		Builder gmms(GmmSet gmmSet) {
			this.gmmSet = checkNotNull(gmmSet, "Gmm set is null");
			return this;
		}

		Builder strike(double strike) {
			// unkown strike allowed for grid sources
			this.strike = Double.isNaN(strike) ? strike : validateStrike(strike);
			return this;
		}

		Builder magScaling(MagScalingType magScaling) {
			this.magScaling = checkNotNull(magScaling, "MagScaling is null");
			return this;
		}

		Builder depthMap(NavigableMap<Double, Map<Double, Double>> magDepthMap, SourceType type) {
			checkNotNull(magDepthMap, "MagDepthMap is null");
			checkArgument(magDepthMap.size() > 0, "MagDepthMap must have at least one entry");
			// the structure of the map and its weights will have been fully
			// validated by parser; still need to check that depths are
			// appropriate; 'type' indicates how to validate depths across
			// wrapper classes
			validateDepths(magDepthMap, type);
			// there must be at least one mag key that is >= MAX_MAG
			validateMagCutoffs(magDepthMap);
			this.magDepthMap = magDepthMap;
			return this;
		}

		Builder mechs(Map<FocalMech, Double> mechMap) {
			// weights will have already been checked
			checkArgument(!checkNotNull(mechMap).isEmpty());
			checkArgument(mechMap.size() == 3);
			this.mechMap = mechMap;
			return this;
		}

		Builder magMaster(List<Double> magMaster) {
			/*
			 * TODO mfds should validate against magMaster
			 * or create mag master locally, but that is hard
			 */
			checkArgument(checkNotNull(magMaster).size() > 0);
			this.magMaster = magMaster;
			return this;
		}

		Builder location(Location loc, IncrementalMfd mfd) {
			this.mfds.add(checkNotNull(mfd, "MFD is null"));
			this.locs.add(checkNotNull(loc, "Location is null"));
			return this;
		}

		Builder location(Location loc, IncrementalMfd mfd, Map<FocalMech, Double> mechMap) {
			this.mfds.add(checkNotNull(mfd, "MFD is null"));
			this.locs.add(checkNotNull(loc, "Location is null"));
			checkArgument(!checkNotNull(mechMap).isEmpty());
			checkArgument(mechMap.size() == 3);
			this.mechMaps.add(mechMap);
			return this;
		}

		void validateDepths(Map<Double, Map<Double, Double>> magDepthMap, SourceType type) {
			for (Map<Double, Double> magMap : magDepthMap.values()) {
				for (double depth : magMap.keySet()) {
					switch (type) {
						case GRID:
							Faults.validateDepth(depth);
							break;
						case SLAB:
							Faults.validateSlabDepth(depth);
							break;
						default:
							throw new IllegalStateException(type + " not a grid source type");
					}
				}
			}
		}

		void validateMagCutoffs(Map<Double, Map<Double, Double>> magDepthMap) {
			for (double mag : magDepthMap.keySet()) {
				if (mag >= MAX_MAG) return;
			}
			throw new IllegalStateException("MagDepthMap must contain at least one M\u2265" +
				MAX_MAG);
		}

		void validateState(String id) {
			checkState(!built, "This %s instance as already been used", id);
			checkState(name != null, "%s name not set", id);
			checkState(weight != null, "%s weight not set", id);
			checkState(strike != null, "%s strike not set", id);
			checkState(!locs.isEmpty(), "%s has no locations", id);
			checkState(!mfds.isEmpty(), "%s has no Mfds", id);
			checkState(magScaling != null, "%s has no mag-scaling relation set", id);
			checkState(magDepthMap != null, "%s mag-depth-weight map not set", id);
			checkState(mechMap != null, "%s focal mech map not set", id);
			checkState(gmmSet != null, "%s ground motion models not set", id);
			checkState(magMaster != null, "%s master magnitude list not set", id);

			/*
			 * Validate size of mechMaps; size could get out of sync if mixed
			 * calls to location(...) were made; one can imagine a future use
			 * case where a default is required with an override in a few
			 * locations; for now, if custom mechMaps are required, there must
			 * be one for each node. If no custom maps supplied populate
			 * mechMaps with nCopies (singleton list with multiple elements)
			 */
			if (!mechMaps.isEmpty()) {
				checkState(mechMaps.size() == locs.size(),
					"%s only %s of %s focal mech maps were added", id, mechMaps.size(), locs.size());
			} else {
				mechMaps = Collections.nCopies(locs.size(), mechMap);
			}

			built = true;
		}

		GridSourceSet build() {
			validateState(ID);
			return new GridSourceSet(name, weight, magScaling, gmmSet, locs, mfds, mechMaps,
				magDepthMap, strike, magMaster);
		}

	}

}
