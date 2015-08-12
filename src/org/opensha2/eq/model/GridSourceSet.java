package org.opensha2.eq.model;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.opensha2.eq.Magnitudes.MAX_MAG;
import static org.opensha2.eq.fault.Faults.validateStrike;
import static org.opensha2.eq.model.PointSourceType.FIXED_STRIKE;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;

import org.opensha2.eq.fault.Faults;
import org.opensha2.eq.fault.FocalMech;
import org.opensha2.eq.fault.surface.RuptureScaling;
import org.opensha2.eq.model.PointSource.DepthModel;
import org.opensha2.geo.Location;
import org.opensha2.geo.Locations;
import org.opensha2.mfd.IncrementalMfd;

import com.google.common.base.Predicate;
import com.google.common.collect.Lists;

/**
 * A container class for related, evenly-spaced {@link PointSource}s with
 * varying magnitudes and/or rates derived from an {@link IncrementalMfd} at
 * each grid node.
 * 
 * @author Peter Powers
 */
public class GridSourceSet extends AbstractSourceSet<PointSource> {

	private final List<Location> locs;
	private final List<IncrementalMfd> mfds;
	private final RuptureScaling rupScaling;
	private final List<Map<FocalMech, Double>> mechMaps;
	final DepthModel depthModel; // package exposure for parser logging
	private final double strike;
	private final PointSourceType sourceType;

	/*
	 * Most grid sources have the same focal mech map everywhere; in these
	 * cases, mechMaps will have been created using Collections.nCopies() with
	 * minimal overhead.
	 */

	private GridSourceSet(
			String name,
			int id,
			Double weight,
			GmmSet gmmSet,
			List<Location> locs,
			List<IncrementalMfd> mfds,
			List<Map<FocalMech, Double>> mechMaps,
			DepthModel depthModel,
			double strike,
			RuptureScaling rupScaling,
			PointSourceType sourceType) {

		super(name, id, weight, gmmSet);
		this.locs = locs;
		this.mfds = mfds;
		this.mechMaps = mechMaps;
		this.depthModel = depthModel;
		this.strike = strike;
		this.rupScaling = rupScaling;
		this.sourceType = sourceType;
	}

	@Override public SourceType type() {
		return SourceType.GRID;
	}

	@Override public int size() {
		return locs.size();
	}

	@Override public Predicate<PointSource> distanceFilter(final Location loc, final double distance) {
		return new DistanceFilter(loc, distance);
	}

	/* Not inlined for use by area sources */
	static final class DistanceFilter implements Predicate<PointSource> {
		private final Predicate<Location> filter;

		DistanceFilter(Location loc, double distance) {
			filter = Locations.distanceAndRectangleFilter(loc, distance);
		}

		@Override public boolean apply(PointSource source) {
			return filter.apply(source.loc);
		}

		@Override public String toString() {
			return "GridSourceSet.DistanceFilter[ " + filter.toString() + " ]";
		}
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
		switch (sourceType) {
			case POINT:
				return new PointSource(locs.get(idx), mfds.get(idx), mechMaps.get(idx), rupScaling,
					depthModel);
			case FINITE:
				return new PointSourceFinite(locs.get(idx), mfds.get(idx), mechMaps.get(idx),
					rupScaling, depthModel);
			case FIXED_STRIKE:
				return new PointSourceFixedStrike(locs.get(idx), mfds.get(idx), mechMaps.get(idx),
					rupScaling, depthModel, strike);
			default:
				throw new IllegalStateException("Unhandled point source type");
		}
	}

	// Builder accomodates overriding a default mechMap to support UC3
	// grid sources; may add others later TODO document

	/* Single use builder. */
	static class Builder extends AbstractSourceSet.Builder  {

		private static final String ID = "GridSourceSet.Builder";

		private Double strike;
		private PointSourceType sourceType;
		private RuptureScaling rupScaling;
		private NavigableMap<Double, Map<Double, Double>> magDepthMap;
		private Double maxDepth;
		private Map<FocalMech, Double> mechMap;

		private List<Location> locs = Lists.newArrayList();
		private List<IncrementalMfd> mfds = Lists.newArrayList();
		private List<Map<FocalMech, Double>> mechMaps = Lists.newArrayList();
		private List<Double> magMaster;

		Builder strike(double strike) {
			// unknown strike allowed for grid sources
			this.strike = Double.isNaN(strike) ? strike : validateStrike(strike);
			return this;
		}

		Builder sourceType(PointSourceType sourceType) {
			this.sourceType = checkNotNull(sourceType);
			return this;
		}

		Builder ruptureScaling(RuptureScaling rupScaling) {
			this.rupScaling = checkNotNull(rupScaling, "RupScaling is null");
			return this;
		}

		Builder depthMap(NavigableMap<Double, Map<Double, Double>> magDepthMap, SourceType type) {
			checkNotNull(magDepthMap, "MagDepthMap is null");
			checkArgument(magDepthMap.size() > 0, "MagDepthMap must have at least one entry");
			// the structure of the map and its weights will have been fully
			// validated by parser; still need to check that depths are
			// appropriate; 'type' indicates how to validate depths across
			// wrapper classes
			validateDepthMap(magDepthMap, type);
			// there must be at least one mag key that is >= MAX_MAG
			validateMagCutoffs(magDepthMap);
			this.magDepthMap = magDepthMap;
			return this;
		}

		Builder maxDepth(Double maxDepth, SourceType type) {
			this.maxDepth = checkNotNull(maxDepth, "Maximum depth is null");
			validateDepth(maxDepth, type);
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
			 * TODO mfds should validate against magMaster or create mag master
			 * locally, but that is hard
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

		static void validateDepthMap(Map<Double, Map<Double, Double>> magDepthMap, SourceType type) {
			for (Map<Double, Double> magMap : magDepthMap.values()) {
				for (double depth : magMap.keySet()) {
					validateDepth(depth, type);
				}
			}
		}

		static void validateDepth(double depth, SourceType type) {
			switch (type) {
				case GRID:
					Faults.validateDepth(depth);
					break;
				case SLAB:
					Faults.validateSlabDepth(depth);
					break;
				default:
					throw new IllegalStateException(type + " not a grid or related source type");
			}
		}

		static void validateMaxAndMapDepths(Map<Double, Map<Double, Double>> magDepthMap,
				double maxDepth, String id) {
			for (Map<Double, Double> magMap : magDepthMap.values()) {
				for (double depth : magMap.keySet()) {
					checkState(depth <= maxDepth, "%s mag-depth-weight map depth %s > %s", id,
						depth, maxDepth);
				}
			}
		}

		static void validateMagCutoffs(Map<Double, Map<Double, Double>> magDepthMap) {
			for (double mag : magDepthMap.keySet()) {
				if (mag >= MAX_MAG) return;
			}
			throw new IllegalStateException("MagDepthMap must contain at least one M ≥ " + MAX_MAG);
		}

		@Override void validateState(String buildId) {
			super.validateState(buildId);
			checkState(strike != null, "%s strike not set", buildId);
			checkState(sourceType != null, "%s source type not set", buildId);
			checkState(!locs.isEmpty(), "%s has no locations", buildId);
			checkState(!mfds.isEmpty(), "%s has no Mfds", buildId);
			checkState(rupScaling != null, "%s has no rupture-scaling relation set", buildId);
			checkState(magDepthMap != null, "%s mag-depth-weight map not set", buildId);
			checkState(maxDepth != null, "%s maximum depth not set", buildId);
			checkState(mechMap != null, "%s focal mech map not set", buildId);
			checkState(magMaster != null, "%s master magnitude list not set", buildId);

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
					"%s only %s of %s focal mech maps were added", ID, mechMaps.size(), locs.size());
			} else {
				mechMaps = Collections.nCopies(locs.size(), mechMap);
			}

			/*
			 * Validate depths. depths will already have been checked for
			 * consistency with allowable depths for different source types.
			 * Must also ensure that all depths (zTop) in the magDepthMap are <
			 * maxDepth.
			 */
			validateMaxAndMapDepths(magDepthMap, maxDepth, ID);

			/*
			 * Validate type agreement. If strike != NaN, type must be
			 * FIXED_STRIKE.
			 */
			if (!Double.isNaN(strike)) {
				checkState(sourceType == FIXED_STRIKE,
					"Source type must be FIXED_STRIKE for strike [%s]", strike);
			} else {
				checkState(sourceType != FIXED_STRIKE,
					"Source type FIXED_STRIKE invalid for strive [%s]", strike);
			}
		}

		GridSourceSet build() {
			validateState(ID);
			DepthModel depthModel = DepthModel.create(magMaster, magDepthMap, maxDepth);
			return new GridSourceSet(name, id, weight, gmmSet, locs, mfds, mechMaps, depthModel,
				strike, rupScaling, sourceType);
		}

	}

}
