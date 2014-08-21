package org.opensha.calc;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.EnumMap;

import org.opensha.eq.forecast.Rupture;
import org.opensha.geo.Location;

import com.google.common.base.Function;
import com.google.common.collect.Maps;

/**
 * TODO this has preliminary TRT and magnitude based distance filter code
 * (commented out)
 * 
 * @author Peter Powers
 * @version $Id:$
 */
@Deprecated
final class DistanceFilters {

	// no instantiation
	private DistanceFilters() {}

//	/**
//	 * Fixed distance filter.
//	 * @param loc of interest
//	 * @param r filter distance
//	 * @return a {@code Function} that returns {@code true} for ruptures
//	 */
//	public static Function<Rupture, Boolean> distanceFilter(
//			final Location loc, final double r) {
//		return new Function<Rupture, Boolean>() {
//			@Override
//			public Boolean apply(Rupture rup) {
//				return rup.getRuptureSurface()
//					.getDistanceJB(loc) < r;
//			}
//		};
//	}
//
//	/**
//	 * 
//	 * @return
//	 */
//	public static Function<Rupture, Boolean> nshmpRegionFilter() {
//		return new Function<Rupture, Boolean>() {
//			@Override
//			public Boolean apply(Rupture rup) {
//				return true;
//			}
//		};
//	}

	/*
	 * TectonicRegionType based distance filter.
	 */
//	private static class TRT_SourceFilter implements
//			Function<Source, Boolean> {
//		private Site site;
//		private EnumMap<TectonicRegionType, Double> trtMap;
//
//		private TRT_SourceFilter(Site site,
//			EnumMap<TectonicRegionType, Double> trtMap) {
//			this.site = site;
//			this.trtMap = initMap(trtMap);
//		}
//
//		@Override
//		public Boolean apply(Source src) {
//			double r = trtMap.get(src.getTectonicRegionType());
//			return src.getMinDistance(site) < r;
//		}
//
//		/*
//		 * If supplied trt map is null a new map with default cutoffs is
//		 * created; if supplied trt map is missing values, the necessary
//		 * defaults are added.
//		 */
//		private static EnumMap<TectonicRegionType, Double> initMap(
//				EnumMap<TectonicRegionType, Double> mapIn) {
//			EnumMap<TectonicRegionType, Double> mapOut = mapIn;
//			if (mapOut == null)
//				mapOut = Maps.newEnumMap(TectonicRegionType.class);
//			for (TectonicRegionType trt : TectonicRegionType.values()) {
//				if (!mapOut.containsKey(trt)) {
//					mapOut.put(trt, trt.defaultCutoffDist());
//				}
//			}
//			return mapOut;
//		}
//	}

	/*
	 * Preliminary distance filter that considers magnitude along with
	 * TectonicType. Current mag-curoff maps are keyed to TRT default
	 * cutoff values that arre in line with NSHMP cutoff values.
	 */
//	private static class TRT_RuptureFilter implements
//			Function<Rupture, Boolean> {
//		private Site site;
//		private EnumMap<TectonicRegionType, EnumMap<MagCutoff, Double>> trtMap;
//
//		private TRT_RuptureFilter(Site site) {
//			this.site = site;
//			this.trtMap = initMap();
//		}
//
//		@Override
//		public Boolean apply(Rupture rup) {
//			// TODO get TRT from rupture
//			TectonicRegionType bigDummy = TectonicRegionType.ACTIVE_SHALLOW;
//			MagCutoff magID = MagCutoff.forMag(rup.getMag());
//			double r = trtMap.get(bigDummy).get(magID);
//			// return rup.getRuptureSurface().getDistanceJB(
//			// site.getLocation()) < r;
//
//			throw new UnsupportedOperationException(
//				"Need to be able to get TRT from rupture");
//		}
//
//		/*
//		 * Builds the default TRT-Mag-Distance cutoff maps.
//		 */
//		private static EnumMap<TectonicRegionType, EnumMap<MagCutoff, Double>> initMap() {
//			EnumMap<TectonicRegionType, EnumMap<MagCutoff, Double>> trtMap = Maps
//				.newEnumMap(TectonicRegionType.class);
//			for (TectonicRegionType trt : TectonicRegionType.values()) {
//				EnumMap<MagCutoff, Double> magMap = (trt.defaultCutoffDist() < 500)
//					? HIGH_ATTEN_CUTOFF : LOW_ATTEN_CUTOFF;
//				trtMap.put(trt, magMap);
//			}
//			return trtMap;
//		}
//	}

//	/**
//	 * Fixed distance filter for {@link Source}s.
//	 * @param loc of interest
//	 * @param r filter distance
//	 * @return {@code true} if source is within distance, {@code false}
//	 *         otherwise
//	 */
//	public static Function<Rupture, Boolean> ruptureDistanceFilter(
//			final Location loc, final double r) {
//		return new Function<Rupture, Boolean>() {
//			@Override
//			public Boolean apply(Rupture rup) {
//				return rup.getRuptureSurface().getDistanceJB(loc) < r;
//			}
//		};
//	}

	/**
	 * Fixed distance filter for {@link Source}s.
	 * @param site of interest
	 * @param r filter distance
	 * @return {@code true} if source is within distance, {@code false}
	 *         otherwise
	 */
//	public static Function<Source, Boolean> sourceDistanceFilter(
//			final org.opensha.geo.Location loc, final double r) {
//		return new Function<Source, Boolean>() {
//			@Override
//			public Boolean apply(Source src) {
//				return src.getMinDistance(site) < r;
//			}
//		};
//	}

	// TODO the code below is for future mag dependent distance filtering
	// - mag filtereing is processed at rupture level.
	// - need cutoff maps for each trt
	// - given the above it's sort of silly to have this be user editable
	// mag distance cutoff settings; values she be prescribed based
	// on analysis of GMPE's and source types
	// - should have way to have hybrid cutoffs; say we know a source
	// is subduction events only, then it could just have a single
	// cutoff, whereas mag -dependent cutoffs would be appropriate
	// for gridded and complex fault sources

	/* Cutoff map for low attenuation TRTs and GMPEs */
	private static final EnumMap<MagCutoff, Double> LOW_ATTEN_CUTOFF = createCutoffMap(new double[] { 500, 750, 1000, 1250, 1500 });

	/* Cutoff map for high attenuation TRTs and GMPEs */
	private static final EnumMap<MagCutoff, Double> HIGH_ATTEN_CUTOFF = createCutoffMap(new double[] { 50, 100, 150, 200, 250 });

	/*
	 * Utility method to create a cutoff map. Cutoff distances should correspond
	 * to the ascending values of {@code MagCutoff} (i.e. {@code cutoffs[0]}
	 * should be the cutoff for M&lt;5 [{@code M5}] events).
	 * 
	 * @param cutoffs
	 * 
	 * @return map of magnitude dependent cutoff values
	 * 
	 * @throws IllegalArgumentException if cutoffs does not have the same number
	 * of elements as {@code MagCutoff} has values
	 */
	private static EnumMap<MagCutoff, Double> createCutoffMap(double[] cutoffs) {
		checkNotNull(cutoffs);
		checkArgument(cutoffs.length == MagCutoff.values().length);
		EnumMap<MagCutoff, Double> cutoffMap = Maps.newEnumMap(MagCutoff.class);
		int idx = 0;
		for (MagCutoff mc : MagCutoff.values()) {
			cutoffMap.put(mc, cutoffs[idx++]);
		}
		return cutoffMap;
	}

	/*
	 * Identifiers for different magnitudes.
	 */
	private enum MagCutoff {
		M5,
		M6,
		M7,
		M8,
		MAX;
		public static MagCutoff forMag(double m) {
			return m < 5 ? M5 : m < 6 ? M6 : m < 7 ? M7 : m < 8 ? M8 : MAX;
		}
	}

}
