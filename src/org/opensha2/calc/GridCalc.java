package org.opensha2.calc;

import static org.opensha2.gmm.Gmm.*;
import static java.util.Arrays.copyOfRange;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.opensha2.data.DataTable;
import org.opensha2.data.DataTables;
import org.opensha2.data.XySequence;
import org.opensha2.eq.fault.FocalMech;
import org.opensha2.eq.model.GmmSet;
import org.opensha2.eq.model.GridSourceSet;
import org.opensha2.eq.model.PointSources;
import org.opensha2.geo.Location;
import org.opensha2.geo.LocationList;
import org.opensha2.geo.Locations;
import org.opensha2.gmm.Gmm;
import org.opensha2.gmm.GmmInput;
import org.opensha2.gmm.GmmInput.Constraints;
import org.opensha2.gmm.GmmInput.Field;
import org.opensha2.gmm.GroundMotionModel;
import org.opensha2.gmm.Idriss_2014;
import org.opensha2.gmm.Imt;
import org.opensha2.gmm.ScalarGroundMotion;
import org.opensha2.mfd.IncrementalMfd;
import org.opensha2.mfd.Mfds;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.primitives.Doubles;

/**
 * Handler for hazard calculations where grid source ground motions have been
 * precomputed for set magnitudes and distance bins
 *
 * @author Peter Powers
 */
@Deprecated
public class GridCalc {

	// TODO reorganize, clean, privatize
	
	// private static LoadingCache<Key, V> cache;

	// cache = CacheBuilder.newBuilder().build(new CacheLoader<Imt,
	// GroundMotionModel>() {
	// @Override public GroundMotionModel load(Imt imt) throws Exception {
	// return createInstance(imt);
	// }
	// });

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

	static final class GroundMotions {

		final DataTable μTable;
		final DataTable σTable;

		GroundMotions(DataTable μTable, DataTable σTable) {
			this.μTable = μTable;
			this.σTable = σTable;
		}
	}

	
	static final class GridTables {

		final Map<Gmm, Map<SourceStyle, GroundMotions>> groundMotions;

		GridTables(Map<Gmm, Map<SourceStyle, GroundMotions>> groundMotions) {
			this.groundMotions = groundMotions;
		}

		static GridTables create(GridSourceSet sourceSet, GmmSet gmmSet, Imt imt, Vs30 vs30) {

			Map<Gmm, Map<SourceStyle, GroundMotions>> gmmTables = new EnumMap<>(Gmm.class);
			
			double rMin = 0.0;
			double rMax = gmmSet.maxDistance();
			double rΔ = distanceDiscretization(rMax);
//			double[] distances = DataTable.Builder.create().rows(rMin, rMax, rΔ).rows();
			double[] distances = DataTables.keys(rMin, rMax, rΔ);
			
			LocationList locations = null;
//			LocationList locations = LocationList.create(
//				SRC_LOC,
//				Doubles.asList(distances),
//				SRC_TO_SITE_AZIMUTH);

			List<Site> siteList = createSiteList(locations, vs30);

			boolean multiMech = isMultiMech(gmmSet.gmms());
			Map<FocalMech, Double> mechWtMap = multiMech ? MULTI_MECH_MAP : SS_MECH_MAP;

			List<InputList> inputsList = PointSources.finiteInputs(
				siteList,
				SRC_LOC,
				GRID_MFD,
				mechWtMap,
				sourceSet);

			for (Gmm gmm : gmmSet.gmms()) {

				List<InputListGroundMotions> groundMotionsList = computeGroundMotions(
					gmm.instance(imt),
					inputsList);

				Map<SourceStyle, DataTable.Builder> μBuilders = initBuilders(rMin, rMax, rΔ, multiMech);
				Map<SourceStyle, DataTable.Builder> σBuilders = initBuilders(rMin, rMax, rΔ, multiMech);

				for (int i = 0; i < groundMotionsList.size(); i++) {

					double rowKey = distances[i];

					InputListGroundMotions groundMotions = groundMotionsList.get(i);

					for (SourceStyle style : SourceStyle.values()) {

						int start = style.index();
						int end = start + MAGS.length;
						double startMag = MAGS[0];

						DataTable.Builder μBuilder = μBuilders.get(style);
						double[] μValues = copyOfRange(groundMotions.μs, start, end);
						μBuilder.add(rowKey, startMag, μValues);

						DataTable.Builder σBuilder = σBuilders.get(style);
						double[] σValues = copyOfRange(groundMotions.σs, start, end);
						σBuilder.add(rowKey, startMag, σValues);

						if (!multiMech && style == SourceStyle.STRIKE_SLIP) break;
					}
				}

				Map<SourceStyle, GroundMotions> groundMotionTables = new EnumMap<>(
					SourceStyle.class);
				
				for (SourceStyle style : SourceStyle.values()) {
					GroundMotions groundMotions = new GroundMotions(
						μBuilders.get(style).build(),
						σBuilders.get(style).build());
					groundMotionTables.put(style, groundMotions);
					if (!multiMech && style == SourceStyle.STRIKE_SLIP) break;
				}
				
				gmmTables.put(gmm, groundMotionTables);
			}
			return new GridTables(gmmTables);
		}
		
		/*
		 * the logic here is predicated on knowing the form and order of
		 * GmmInputs that are returned in an InputList generated from a
		 * FinitePointSource
		 * 
		 * I think we want to hold onto the InputLists that get generated
		 * 
		 * For now, we let the gmms dictate what tables to generate and use.
		 * Whereas we could also check the FocalMechMaps of each GridSourceSet,
		 * this is actually complicated because mech maps may be defined on a
		 * per-node basis, even though most have the same mch map everywhere.
		 * 
		 * Additional problem: vs30 and basin terms
		 * 
		 * Currently GridCalc only supports named Vs30 values. Does not support
		 * varying basin terms; default NaN only
		 * 
		 * TODO hypothetical, calculations where any vs30 could be selected;
		 * jsut the rate binning approach likely achieves performance speed
		 * gains; onve variable vs30 and basin terms are allowed, there's really
		 * no way to manage precalculation of gmm gound motions
		 */

	}

	/*
	 * Master MFD spanning the range of grid MFDs used in both the WUS and CEUS
	 * in 2008 and 2014. It's simpler to use an identical table for all
	 * SourceSets than vary tables on a per SourceSet basis.
	 * 
	 * 2008 ceus: 5.0 to 7.2 (J); 5.0 to 7.4 (AB) wus: 5.0 to 7.5
	 * 
	 * 2014 ceus: 4.7 to 8.0; wus: 5.0 to 7.5; 5.0 to 8.0 (tapered)
	 */
	private static final double M_MIN = 4.7;
	private static final double M_MAX = 8.0;
	private static final double M_Δ = 0.1;

	private static final double[] MAGS = DataTables.keys(M_MIN, M_MAX, M_Δ);
	private static final double[] RATES = new double[MAGS.length]; // empty
//	private static final IncrementalMfd GRID_MFD = Mfds.newIncrementalMFD(MAGS, RATES);
	private static final XySequence GRID_MFD = XySequence.createImmutable(MAGS, RATES);

	/*
	 * Style of faulting identifier ordered the same way that FinitePointSources
	 * iterate Ruptures. TODO tighter integration or relocation to PointSource?
	 */
	enum SourceStyle {
		STRIKE_SLIP,
		REVERSE_FOOTWALL,
		REVERSE_HANGINGWALL,
		NORMAL_FOOTWALL,
		NORMAL_HANGINGWALL;

		int index() {
			return ordinal() * MAGS.length;
		}
	}

	/*
	 * Return a distance dependent discretization. Currently this is fixed at
	 * 1km for r<400km and 5km for r>= 400km
	 */
	public static double distanceDiscretization(double r) {
		return r < 400.0 ? 1.0 : 5.0;
	}

	/*
	 * Returns true if at least one Gmm requires dip or rake data
	 */
	private static boolean isMultiMech(Set<Gmm> gmms) {
		for (Gmm gmm : gmms) {
			Constraints c = gmm.constraints();
			if (c.get(Field.DIP).isPresent() || c.get(Field.RAKE).isPresent()) return true;
		}
		return false;
	}

	/*
	 * Representative focal mech maps that will trigger the generation of
	 * footwall and hanging-wall flavors of GmmInputs, or not if strike-slip
	 * only.
	 */
	private static final Map<FocalMech, Double> SS_MECH_MAP = Maps.immutableEnumMap(
		ImmutableMap.<FocalMech, Double> builder()
			.put(FocalMech.STRIKE_SLIP, 1.0)
			.put(FocalMech.REVERSE, 0.0)
			.put(FocalMech.NORMAL, 0.0)
			.build());

	private static final Map<FocalMech, Double> MULTI_MECH_MAP = Maps.immutableEnumMap(
		ImmutableMap.<FocalMech, Double> builder()
			.put(FocalMech.STRIKE_SLIP, 0.3334)
			.put(FocalMech.REVERSE, 0.3333)
			.put(FocalMech.NORMAL, 0.3333)
			.build());

	private static final Location SRC_LOC = Location.create(0.0, 0.0);
	public static final double SRC_TO_SITE_AZIMUTH = 0.0;

	/*
	 * Create a list of sites, one at each distance from a source. To build
	 * generalized sources for grid tables, the source is placed at (0,0) and
	 * sites are created along a N-S line. This recovers accurate distances
	 * using fast distance calculation algorthms.
	 */
	private static List<Site> createSiteList(LocationList locs, Vs30 vs30) {
		List<Site> siteList = new ArrayList<>();
		Site.Builder siteBuilder = Site.builder().vs30(vs30.value());
		for (Location loc : locs) {
			siteBuilder.location(loc);
			siteList.add(siteBuilder.build());
		}
		return siteList;
	}

	private static List<InputListGroundMotions> computeGroundMotions(
			GroundMotionModel gmm,
			List<InputList> inputsList) {

		List<InputListGroundMotions> gmList = new ArrayList<>();
		for (InputList inputs : inputsList) {
			gmList.add(computeGroundMotions(gmm, inputs));
		}
		return gmList;
	}

	private static InputListGroundMotions computeGroundMotions(
			GroundMotionModel gmm,
			InputList inputs) {

		InputListGroundMotions gms = new InputListGroundMotions(inputs.size());
		for (int i = 0; i < inputs.size(); i++) {
			ScalarGroundMotion sgm = gmm.calc(inputs.get(i));
			gms.μs[i] = sgm.mean();
			gms.σs[i] = sgm.sigma();
		}
		return gms;
	}

	/*
	 * Intermediate data container that holds means and sigmas computed for an
	 * InputList. The data is ultimately further decomposed and this class is
	 * never retained.
	 */
	private static class InputListGroundMotions {

		double[] μs;
		double[] σs;

		InputListGroundMotions(int size) {
			μs = new double[size];
			σs = new double[size];
		}
	}

	/*
	 * Create map of builders. For strike-slip only case, a map with a single
	 * builder is returned.
	 */
	private static Map<SourceStyle, DataTable.Builder> initBuilders(
			double rMin,
			double rMax,
			double rΔ,
			boolean multiMech) {

		Map<SourceStyle, DataTable.Builder> builderMap = new EnumMap<>(SourceStyle.class);
		for (SourceStyle style : SourceStyle.values()) {
			builderMap.put(style, createGridBuilder(rMin, rMax, rΔ));
			if (!multiMech && style == SourceStyle.STRIKE_SLIP) break;
		}
		return builderMap;
	}

	private static DataTable.Builder createGridBuilder(double rMin, double rMax, double rΔ) {
		return DataTable.Builder.create()
			.rows(rMin, rMax, rΔ)
			.columns(M_MIN, M_MAX, M_Δ);
	}
	
	@Deprecated
	public static DataTable.Builder createGridBuilder(double rMax) {
		return createGridBuilder(0.0, rMax, distanceDiscretization(rMax));
	}
	
	public static DataTable.Builder createGridBuilder(
			double rMax,
			double mMin,
			double mMax,
			double Δm) {
		return DataTable.Builder.create()
				.rows(0.0, rMax, distanceDiscretization(rMax))
				.columns(mMin, mMax, Δm);
	}

	/*
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 */

	GridCalc(GmmSet gmmSet) {

		Set<Gmm> gmms = gmmSet.gmms();

		// no hanging wall
		for (Gmm gmm : gmms) {

		}

		GroundMotionModel gmm = Gmm.ASK_14.instance(Imt.PGA);

		// DataTableBuilder builder = DataTableBuilder.create().

		// hanging wall
	}

	public static void main(String[] args) {

		Set<Gmm> gmms_wus_08 = EnumSet.of(BA_08, CB_08, CY_08);
		Set<Gmm> gmms_wus_14 = EnumSet.of(ASK_14, BSSA_14, CB_14, CY_14, IDRISS_14);

		Set<Gmm> gmms_ceus_08 = EnumSet.of(
			AB_06_140BAR,
			AB_06_200BAR,
			CAMPBELL_03,
			FRANKEL_96,
			SILVA_02,
			SOMERVILLE_01,
			TP_05,
			TORO_97_MW);

		Set<Gmm> gmms_ceus_14 = EnumSet.of(
			AB_06_PRIME,
			ATKINSON_08_PRIME,
			CAMPBELL_03,
			FRANKEL_96,
			PEZESHK_11,
			SILVA_02,
			SOMERVILLE_01,
			TP_05,
			TORO_97_MW);

		System.out.println(isMultiMech(gmms_wus_08));
		System.out.println(isMultiMech(gmms_wus_14));
		System.out.println(isMultiMech(gmms_ceus_08));
		System.out.println(isMultiMech(gmms_ceus_14));
	}

	// TODO maybe add pure reverse or normal, we may have tables that don't care
	// about hw/fw

	/*
	 * This class will hold onto the key reference from the first of every
	 * unique GridSourceSet encountered. Many GridSourceSets share the same key.
	 */
	static class Key {

		private final GridSourceSet.Key gridKey;
		private final GmmSet gmmSet;
		private final Imt imt;
		private final Vs30 vs30;

		private final int hashCode;

		private Key(
				final GridSourceSet.Key gridKey,
				final GmmSet gmmSet,
				final Imt imt,
				final Vs30 vs30) {

			this.gridKey = gridKey;
			this.gmmSet = gmmSet;
			this.imt = imt;
			this.vs30 = vs30;

			hashCode = Objects.hash(gridKey, gmmSet, imt, vs30);
		}

		@Override public int hashCode() {
			return hashCode;
		}

		@Override public boolean equals(Object obj) {
			if (this == obj) return true;
			if (!(obj instanceof Key)) return false;
			Key that = (Key) obj;
			return Objects.equals(this.gridKey, that.gridKey) &&
				Objects.equals(this.gmmSet, that.gmmSet) &&
				Objects.equals(this.imt, that.imt) &&
				Objects.equals(this.vs30, that.vs30);
		}
	}
}
