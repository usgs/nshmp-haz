package org.opensha2.eq.model;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.opensha2.eq.Magnitudes.*;
import static org.opensha2.eq.fault.Faults.validateStrike;
import static org.opensha2.eq.model.PointSourceType.FIXED_STRIKE;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;

import org.opensha2.data.DataTable;
import org.opensha2.data.Data;
import org.opensha2.data.XySequence;
import org.opensha2.eq.fault.Faults;
import org.opensha2.eq.fault.FocalMech;
import org.opensha2.eq.fault.surface.RuptureScaling;
import org.opensha2.eq.model.PointSource.DepthModel;
import org.opensha2.geo.Location;
import org.opensha2.geo.Locations;
import org.opensha2.mfd.IncrementalMfd;
import org.opensha2.mfd.Mfds;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.primitives.Doubles;

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
	final RuptureScaling rupScaling;
	private final List<Map<FocalMech, Double>> mechMaps;
	final DepthModel depthModel; // package exposure for parser logging
	private final double strike;
	private final PointSourceType sourceType;

	final double mMin;
	final double mMax;
	final double Δm;

	/*
	 * TODO We need to (will) impose strict min and delta mag constraints.
	 * Default MFDs will be checked for agreement. We should change pure INCR
	 * mfds (defained by mags[] and rates[]) to min, max, delta and validate
	 * that those 3 params yield a size equivalent to rates.length.
	 */

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
			NavigableMap<Double, Map<Double, Double>> magDepthMap,
			double maxDepth,
			double strike,
			RuptureScaling rupScaling,
			PointSourceType sourceType,
			double mMin,
			double mMax,
			double Δm) {

		super(name, id, weight, gmmSet);
		this.locs = locs;
		this.mfds = mfds;
		this.mechMaps = mechMaps;
		this.strike = strike;
		this.rupScaling = rupScaling;
		this.sourceType = sourceType;

		this.mMin = mMin;
		this.mMax = mMax;
		this.Δm = Δm;

		/*
		 * TODO there are too many assumptions built into this; whose to say
		 * ones bin spacing should be only be in the hundredths?
		 * 
		 * Where did this come from anyway? Are mag deltas really all that
		 * strange
		 * 
		 * We should read precision of supplied mMin and mMax and delta and use
		 * largest for formatting
		 * 
		 * TODO in the case of single combined/flattened MFDs, mags may not be
		 * uniformly spaced. Can this be refactored
		 */
		double cleanDelta = Double.valueOf(String.format("%.2f", Δm));
		double[] mags = Data.buildCleanSequence(mMin, mMax, cleanDelta, true, 2);
		depthModel = DepthModel.create(magDepthMap, Doubles.asList(mags), maxDepth);
	}

	@Override
	public SourceType type() {
		return SourceType.GRID;
	}

	/**
	 * The {@link PointSource} representation used by this source set.
	 */
	public PointSourceType sourceType() {
		return sourceType;
	}

	@Override
	public int size() {
		return locs.size();
	}

	@Override
	public Predicate<PointSource> distanceFilter(final Location loc, final double distance) {
		return new DistanceFilter(loc, distance);
	}

	/* Not inlined for use by area sources */
	static final class DistanceFilter implements Predicate<PointSource> {
		private final Predicate<Location> filter;

		DistanceFilter(Location loc, double distance) {
			filter = Locations.distanceAndRectangleFilter(loc, distance);
		}

		@Override
		public boolean apply(PointSource source) {
			return filter.apply(source.loc);
		}

		@Override
		public String toString() {
			return "GridSourceSet.DistanceFilter[ " + filter.toString() + " ]";
		}
	}

	@Override
	public Iterator<PointSource> iterator() {
		return new Iterator<PointSource>() {
			int caret = 0;

			@Override
			public boolean hasNext() {
				return caret < locs.size();
			}

			@Override
			public PointSource next() {
				return getSource(caret++);
			}

			@Override
			public void remove() {
				throw new UnsupportedOperationException();
			}
		};
	}

	private PointSource getSource(int index) {

		/*
		 * TODO Stricter rules regarding what sorts of default mfds can be used
		 * with grid sources (in an individual grid source set) will allow
		 * parsers to create XySequence based MFDs directly using copyOf so as
		 * to not create zillions of mag arrays.
		 */
		Location loc = locs.get(index);
		XySequence mfd = Mfds.toSequence(mfds.get(index));
		Map<FocalMech, Double> mechMap = mechMaps.get(index);

		switch (sourceType) {
			case POINT:
				return new PointSource(loc, mfd, mechMap, rupScaling, depthModel);

			case FINITE:
				return new PointSourceFinite(loc, mfd, mechMap, rupScaling, depthModel);

			case FIXED_STRIKE:
				return new PointSourceFixedStrike(loc, mfd, mechMap, rupScaling, depthModel,
					strike);

			default:
				throw new IllegalStateException("Unhandled point source type");
		}
	}

	// Builder accomodates overriding a default mechMap to support UC3
	// grid sources; may add others later TODO document

	/* Single use builder. */
	static class Builder extends AbstractSourceSet.Builder {

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

		private Double mMin;
		private Double mMax;
		private Double Δm;

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

		Builder mfdData(double mMin, double mMax, double Δm) {
			// TODO need better validation here
			checkArgument(checkMagnitude(mMin) <= checkMagnitude(mMax));
			this.mMin = mMin;
			this.mMax = mMax;
			this.Δm = Δm;
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

		static void validateDepthMap(Map<Double, Map<Double, Double>> magDepthMap,
				SourceType type) {
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
				case AREA:
					Faults.validateDepth(depth);
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

		@Override
		void validateState(String buildId) {
			super.validateState(buildId);
			checkState(strike != null, "%s strike not set", buildId);
			checkState(sourceType != null, "%s source type not set", buildId);
			checkState(!locs.isEmpty(), "%s has no locations", buildId);
			checkState(!mfds.isEmpty(), "%s has no Mfds", buildId);
			checkState(rupScaling != null, "%s has no rupture-scaling relation set", buildId);
			checkState(magDepthMap != null, "%s mag-depth-weight map not set", buildId);
			checkState(maxDepth != null, "%s max depth not set", buildId);
			checkState(mechMap != null, "%s focal mech map not set", buildId);

			checkState(mMin != null, "%s min mag not set", buildId);
			checkState(mMax != null, "%s max mag not set", buildId);
			checkState(Δm != null, "%s delta mag not set", buildId);

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
					"%s only %s of %s focal mech maps were added", ID, mechMaps.size(),
					locs.size());
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
			return new GridSourceSet(name, id, weight, gmmSet, locs, mfds, mechMaps, magDepthMap,
				maxDepth, strike, rupScaling, sourceType, mMin, mMax, Δm);
		}

	}

	/*
	 * If, for a basic HazardResult, we want to be able to give a per-source-set
	 * decomposition by ground motion model, or just a decomposition of the
	 * total curve, we'll need to have a table of the curves for every model.
	 * 
	 * If not necessary, then can have table of total curves and table of mean
	 * (and sigma?) for each model. Just mean is necessary for deaggeregation
	 * epsilon
	 * 
	 * OK... so...
	 * 
	 * Preliminary implementations of grid source optimizations modeled after
	 * the NSHMP Fortran codes porecomputed median curves in distance and
	 * magnitude (using a weighted combination of Gmms) and then performed
	 * lookups for each source, aggregating a total curve along the way. This
	 * approach is lossy in that data for individual Gmms is lost, and it was
	 * never extended to support deaggregation where ground motion mean and
	 * sigma are required.
	 * 
	 * Further consideration of these issues suggests that, rather than
	 * aggregating curves along the way, we should build a separate table in
	 * magnitude and distance of rates while looping over sources. At the end,
	 * curves could be computed once for each distance and magnitude bin.
	 * Although full curves for each Gmm could be precomputed, the time to loop
	 * over the rate table may not be significant enough to warrant the memory
	 * overhead (bear in mind that's a lot of curves when considering large
	 * logic trees of Gmms and numerous periods).
	 * 
	 * There is also the additional issue of additional epistemic uncertinaty on
	 * ground motions, which does not need to be considered here if building
	 * magnitude-distance rate tables.
	 * 
	 * There is the additional issue of different focal mechanisms. For NGAW2
	 * and the WUS, we would need to have 5 curves per gmm and r/m bin: 2
	 * reverse, 2 normal 1 strike slip
	 * 
	 * Precomputed curves may still be warranted for map calculations where Gmm
	 * specific data and deaggregation are irrelevant.
	 */

	/*
	 * Why, you say?
	 * 
	 * Simply put, speed. In the 2014 CEUS NSHM, 1000km from a site nets about
	 * 30k sources, each of which has an associated MFD with up to 33 values
	 * (and that assumes the different mMax models have been collapsed
	 * together). So 990k curve calculations per GMM. However, if the rates of
	 * those sources are first aggregated into a matrix of distance (300) and
	 * magnitude (33) bins, then only 900 chazard curve calculations need be
	 * performed per GMM. Ha!
	 */

	/*
	 * need to specify magnitude and distance discretization
	 * 
	 * cache by GmmSet alone (need to maintain internal map by period) - or
	 * another internal cahce; a cache of caches sounds ugly and keeps the table
	 * management class simpler
	 * 
	 * of cache by GmmSet and Imt
	 * 
	 * class names GroundMotionCache cahce of ground motion tables in distance
	 * and magnitude
	 * 
	 * NOTE: (warning) current NSHM magDepthMaps define one depth per magnitude.
	 * If this changes to a distribution in the future, additional tables would
	 * be required, one for each depth.
	 * 
	 * where/hom to to get master mfd: for now lets used fixed discretization
	 * <500< and set range of M
	 */

	/*
	 * Order of operations:
	 * 
	 * Request table of ground motions from cache using GridSourceSet, GmmSet,
	 * and Imt If absent, cacheLoader will create table
	 * 
	 * Generate master input list based on m & r For each gmm, compute list of
	 * ScalarGroundMotions
	 */

	/*
	 * TODO hypothetical, calculations where any vs30 could be selected; jsut
	 * the rate binning approach likely achieves performance speed gains; onve
	 * variable vs30 and basin terms are allowed, there's really no way to
	 * manage precalculation of gmm gound motions If optimization is enabled, a
	 * GridSourceSet will delegate to a table when iterating sources
	 */

	public static Function<GridSourceSet, SourceSet<? extends Source>> toTableFunction(
			Location loc) {
		return new ToTable(loc);
	}

	private static class ToTable implements Function<GridSourceSet, SourceSet<? extends Source>> {
		private final Location loc;

		ToTable(Location loc) {
			this.loc = loc;
		}

		@Override
		public Table apply(GridSourceSet sources) {
			return new Table(sources, loc);
		}
	}

	/*
	 * Notes on dealing with mixedMech situations (e.g. UC3)
	 * 
	 * Need 3 tables (SS, R, N)
	 * 
	 * Rate in each R-M bin gets partitioned across three tables.
	 * 
	 * When building sources we could normalize the rates to create a mechMap on
	 * the fly and sum back to the total rate.
	 * 
	 * Alternatively, and preferably, we reconsolidate partitioned rates into a
	 * list
	 */

	/*
	 * TODO upgrade this to DataVolume to handle azimuth bins?? TODO split over
	 * focal mechs? required for UC3 grids
	 */

	/**
	 * Tabular implementation of a {@code GridSourceSet}. This class
	 * consolidates the point sources that influence hazard at a site using a
	 * magnitude-distance-rate {@code DataTable}, from which a list of sources
	 * is generated. A {@code Table} is created on a per-calculation basis and
	 * is unique to a location.
	 * 
	 * @see GridSourceSet#toTableFunction(Location)
	 */
	public static final class Table extends AbstractSourceSet<PointSource> {

		private final GridSourceSet parent;
		private final Location origin;
		private final List<PointSource> sources;

		private int maximumSize;
		private int parentCount;

		private Table(GridSourceSet parent, Location origin) {
			super(parent.name(), parent.id(), parent.weight(), parent.groundMotionModels());
			this.parent = parent;
			this.origin = origin;
			this.sources = initSources();
		}

		/**
		 * The number of sources drawn from a parent {@code GridSourceSet}
		 * during initialization.
		 */
		public int parentCount() {
			return parentCount;
		}

		/**
		 * The maximum number of sources that could be used to represent this
		 * {@code SourceSet}. This is equivalent to the number of rows in the
		 * {@code DataTable} used to when consolidating sources. The actual
		 * number of sources required is commonly less due to empty distance
		 * bins.
		 */
		public int maximumSize() {
			return maximumSize;
		}

		@Override
		public String name() {
			return parent.name() + " (opt)";
		}

		@Override
		public SourceType type() {
			return parent.type();
		}

		@Override
		public int size() {
			return parent.size();
		}

		@Override
		public Predicate<PointSource> distanceFilter(Location loc, double distance) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Iterator<PointSource> iterator() {
			throw new UnsupportedOperationException();
		}

		@Override
		public Iterable<PointSource> iterableForLocation(Location loc) {
			/*
			 * Ignore location; simply iterate over the list of sources. Source
			 * list will be empty if mfdTable is empty (all zeros).
			 */
			return sources;
		}

		private static final double SRC_TO_SITE_AZIMUTH = 0.0;

		private List<PointSource> initSources() {
			DataTable mfdTable = initMfdTable();
			List<Double> distances = mfdTable.rows();
			maximumSize = distances.size();
			ImmutableList.Builder<PointSource> b = ImmutableList.builder();
			for (double r : distances) {
				XySequence mfd = mfdTable.row(r);
				if (mfd.isEmpty()) continue;
				Location loc = Locations.location(origin, SRC_TO_SITE_AZIMUTH, r);
				b.add(PointSources.finitePointSource(
					loc,
					mfd,
					parent.mechMaps.get(0),
					parent.rupScaling,
					parent.depthModel));
			}
			return b.build();
		}

		private DataTable initMfdTable() {
			// table keys are specified as lowermost and uppermost bin edges
			double Δm = parent.Δm;
			double ΔmBy2 = Δm / 2.0;
			double mMin = parent.mMin - ΔmBy2;
			double mMax = parent.mMax + ΔmBy2;
			double rMax = parent.groundMotionModels().maxDistance();

			DataTable.Builder tableBuilder = DataTable.Builder.create()
				.rows(0.0, rMax, distanceDiscretization(rMax))
				.columns(mMin, mMax, Δm);

			for (PointSource source : parent.iterableForLocation(origin)) {
				double r = Locations.horzDistanceFast(origin, source.loc);
				tableBuilder.add(r, source.mfd);
				parentCount++;
			}

			DataTable table = tableBuilder.build();
			return table;
		}

		/*
		 * Return a distance dependent discretization. Currently this is fixed
		 * at 1km for r<400km and 5km for r>= 400km
		 */
		private static double distanceDiscretization(double r) {
			return r < 400.0 ? 1.0 : 5.0;
		}

	}

}
