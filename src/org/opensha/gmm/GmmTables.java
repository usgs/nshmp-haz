package org.opensha.gmm;

import static com.google.common.base.StandardSystemProperty.LINE_SEPARATOR;
import static org.opensha.gmm.Imt.PGA;
import static org.opensha.gmm.Imt.PGV;
import static org.opensha.gmm.Imt.SA0P03;
import static org.opensha.gmm.Imt.SA0P3;
import static org.opensha.gmm.Imt.SA3P0;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.logging.Logger;

import org.opensha.data.DataUtils;
import org.opensha.util.MathUtils;
import org.opensha.util.Parsing;
import org.opensha.util.Parsing.Delimiter;

import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Range;
import com.google.common.io.LineProcessor;
import com.google.common.io.Resources;
import com.google.common.primitives.Doubles;

/**
 * Utility class to load and fetch ground motion model lookup tables.
 * @author Peter Powers
 */
final class GmmTables {
	// @formatter:off
	
	private static final Logger log = Logger.getLogger(GmmTable.class.getName());
	private static final String LF = LINE_SEPARATOR.value();

	// Implementation notes:
	//
	//		Tables record value in log10(PSA); note conversion to base e when
	//		static get(double, double, Map) returns.
	//
	//		R and M based lookup is standardized, but some models require post
	//		processing of the returned result; e.g. atkinson tables scale gm
	//		like 1/r above largest distance; Frankel clamps to largest distance

	private static final String T_DIR = "tables/";
	
	private static final String[] frankelSrcSR = { 
		"pgak01l.tbl", "t0p2k01l.tbl", "t1p0k01l.tbl", "t0p1k01l.tbl",
		"t0p3k01l.tbl", "t0p5k01l.tbl", "t2p0k01l.tbl" };
	
	private static final String[] frankelSrcHR = {
		"pgak006.tbl", "t0p2k006.tbl", "t1p0k006.tbl", "t0p1k006.tbl",
		"t0p3k006.tbl", "t0p5k006.tbl", "t2p0k006.tbl" };
	
	private static final String atkinson06src = "AB06revA_Rcd.dat";
	private static final String atkinson08src = "A08revA_Rjb.dat";
	private static final String pezeshk11src = "P11A_Rcd.dat";
	
	private static final Set<Double> threesLo = ImmutableSet.of(0.32, 0.33);
	private static final Set<Double> threesMid = ImmutableSet.of(3.2, 3.33);
	private static final Set<Double> threesHi = ImmutableSet.of(32.0, 33.0, 33.33);

	private static final Range<Double> frankRangeM;
	private static final Range<Double> frankRangeR;
	private static final Range<Double> atkinRangeM;
	private static final Range<Double> atkinRangeR;
	private static final Range<Double> pezRangeM;
	private static final Range<Double> pezRangeR;
	
	private static final double[] frankelMagKeys;
	
	private static final Map<Imt, GmmTable> frankelHR;
	private static final Map<Imt, GmmTable> frankelSR;
	private static final Map<Imt, GmmTable> atkinson06;
	private static final Map<Imt, GmmTable> atkinson08;
	private static final Map<Imt, GmmTable> pezeshk11;
	
	static {
		frankRangeM = Range.closed(4.4, 8.2);
		frankRangeR = Range.closed(1.0, 3.0);
		atkinRangeM = Range.closed(4.0, 8.0);
		atkinRangeR = Range.closed(-1.0, 2.699);
		pezRangeM = Range.closed(4.5, 8.0);
		pezRangeR = Range.closed(1.0, 3.0);
		
		frankelMagKeys = DataUtils.buildCleanSequence(
			frankRangeM.lowerEndpoint(),
			frankRangeM.upperEndpoint(),
			0.2, true, 1);
		
		frankelHR = loadFrankel(frankelSrcHR);
		frankelSR = loadFrankel(frankelSrcSR);
		atkinson06 = loadAtkinson(atkinson06src);
		atkinson08 = loadAtkinson(atkinson08src);
		pezeshk11 = loadAtkinson(pezeshk11src);
	}
	
	private static Map<Imt, GmmTable> loadFrankel(String[] files) {
		Map<Imt, GmmTable> map = Maps.newEnumMap(Imt.class);
		for (String file : files) {
			
			try {
				Imt imt = frankelFilenameToIMT(file);
				URL url = Resources.getResource(GmmTables.class, T_DIR + file);
				NavigableMap<Double, NavigableMap<Double, Double>> rMap =
						Resources.readLines(url, StandardCharsets.UTF_8, new FrankelParser());
				map.put(imt, new FrankelTable(rMap));
			} catch (IOException  ioe) {
				handleIOex(ioe, file);
			}
		}
		return map;
	}
	
	private static Map<Imt, GmmTable> loadAtkinson(String file) {
		Map<Imt, GmmTable> map = Maps.newEnumMap(Imt.class);
		URL url = Resources.getResource(GmmTables.class, T_DIR + file);
		try {
			Map<Imt, NavigableMap<Double, NavigableMap<Double, Double>>> imtMap =
				Resources.readLines(url, StandardCharsets.UTF_8, new AtkinsonParser());
			for (Imt imt : imtMap.keySet()) {
				map.put(imt, new AtkinsonTable(imtMap.get(imt)));
			}
		} catch (IOException ioe) {
			handleIOex(ioe, file);
		}
		return map;
	}
	
	
	/**
	 * Return the ground motion table for the supplied {@code Imt}.
	 * @param imt to fetch table for
	 */
	static GmmTable getFrankel96(Imt imt, SiteClass siteClass) {
		return siteClass == SiteClass.SOFT_ROCK ?
			frankelSR.get(imt) : frankelHR.get(imt);
	}
	
	/**
	 * Return the ground motion table for the supplied {@code Imt}.
	 * @param imt to fetch table for
	 */
	static GmmTable getAtkinson06(Imt imt) {
		return atkinson06.get(imt);
	}
	
	/**
	 * Return the ground motion table for the supplied {@code Imt}.
	 * @param imt to fetch table for
	 */
	static GmmTable getAtkinson08(Imt imt) {
		return atkinson08.get(imt);
	}

	/**
	 * Return the ground motion table for the supplied {@code Imt}.
	 * @param imt to fetch table for
	 */
	static GmmTable getPezeshk11(Imt imt) {
		return pezeshk11.get(imt);
	}
	
	/* Base implementation */
	static abstract class AbstractGMM_Table implements GmmTable {
		NavigableMap<Double, NavigableMap<Double, Double>> rmMap;
		AbstractGMM_Table(NavigableMap<Double, NavigableMap<Double, Double>> rmMap) {
			this.rmMap = rmMap;
		}
	}
		
	// TODO I think we should move the table base implementations to their
	// respective classes that way all implementation details will be restricted
	// to one class
	
	/* Frankel flavor lookup map wrapper */
	static class FrankelTable extends AbstractGMM_Table {
		FrankelTable(NavigableMap<Double, NavigableMap<Double, Double>> rmMap) {
			super(rmMap);
		}
		
		@Override
		public double get(double r, double m) {
			r = clamp(frankRangeR, Math.log10(r));
			m = clamp(frankRangeM, m);
			return GmmTables.get(r, m, rmMap);
		}

	}

	/* Atkinson flavor lookup map wrapper */
	static class AtkinsonTable extends AbstractGMM_Table {
		AtkinsonTable(NavigableMap<Double, NavigableMap<Double, Double>> rmMap){
			super(rmMap);
		}
		@Override
		public double get(double r, double m) {
			r = clamp(atkinRangeR, Math.log10(r));
			m = clamp(atkinRangeM, m);
			return GmmTables.get(r, m, rmMap);
		}
	}
	
	/* Pezeshk flavor lookup map wrapper */
	static class PezeshkTable extends AbstractGMM_Table {
		PezeshkTable(NavigableMap<Double, NavigableMap<Double, Double>> rmMap){
			super(rmMap);
		}
		@Override
		public double get(double r, double m) {
			r = clamp(pezRangeR, Math.log10(r));
			m = clamp(pezRangeM, m);
			return GmmTables.get(r, m, rmMap);
		}
	}



	/* Parser for Frankel tables */
	static class FrankelParser implements
			LineProcessor<NavigableMap<Double, NavigableMap<Double, Double>>> {

		boolean firstLine = true;

		ImmutableSortedMap.Builder<Double, NavigableMap<Double, Double>> rMap =
			ImmutableSortedMap.naturalOrder();

		@Override
		public NavigableMap<Double, NavigableMap<Double, Double>> getResult() {
			return rMap.build();
		}

		@Override
		public boolean processLine(String line) throws IOException {
			if (firstLine) {
				firstLine = false;
				return true;
			}
			List<Double> values = Parsing.splitToDoubleList(line, Delimiter.SPACE);
			ImmutableSortedMap.Builder<Double, Double> mMap = ImmutableSortedMap.naturalOrder();
			for (int i = 0; i < frankelMagKeys.length; i++) {
				mMap.put(frankelMagKeys[i], values.get(i + 1));
			}
			rMap.put(values.get(0), mMap.build());
			return true;
		}
	}
	
	/* Parser for Atkinson style tables */
	static class AtkinsonParser implements
			LineProcessor<Map<Imt, NavigableMap<Double, NavigableMap<Double, Double>>>> {

		int lineIdx = -1;
		List<Imt> imts = null;
		double m;
		
		Map<Imt, Map<Double, Map<Double, Double>>> imtMap =
				Maps.newEnumMap(Imt.class);

		@Override
		public Map<Imt, NavigableMap<Double, NavigableMap<Double, Double>>> getResult() {
			// convert all to navigable
			Map<Imt, NavigableMap<Double, NavigableMap<Double, Double>>> mapOut = 
					Maps.newEnumMap(Imt.class);
			
			for (Map.Entry<Imt, Map<Double, Map<Double, Double>>> imtEntry : imtMap.entrySet()) {
				
				ImmutableSortedMap.Builder<Double, NavigableMap<Double, Double>> rMap = 
						ImmutableSortedMap.naturalOrder();
				
				for (Map.Entry<Double, Map<Double,Double>> rEntry : imtEntry.getValue().entrySet()) {
					rMap.put(rEntry.getKey(), ImmutableSortedMap.copyOf(rEntry.getValue()));
				}
				
				mapOut.put(imtEntry.getKey(), rMap.build());
			}
			return mapOut;
		}

		@Override
		public boolean processLine(String line) throws IOException {
			lineIdx++;
			if (lineIdx < 2) return true;
			
			if (lineIdx == 2) {
				List<Imt> imtList = FluentIterable
					.from(Parsing.split(line, Delimiter.SPACE))
					.transform(Doubles.stringConverter())
					.transform(new FrequencyToIMT())
					.toList();
				// remove dupes -- (e.g., 2s PGA columns in P11)
				imts = Lists.newArrayList(new LinkedHashSet<Imt>(imtList));
				for (Imt imt : imts) {
					imtMap.put(imt, Maps.<Double, Map<Double, Double>>newHashMap());
				}
				return true;
			}
			
			List<Double> values = Parsing.splitToDoubleList(line, Delimiter.SPACE);
			if (values.size() == 1) {
				m = values.get(0); // set magnitude
				return true;
			}
			
			if (values.isEmpty()) return true; // skip empty lines
			
			// process table
			double r = values.get(0);
			for (int i=0; i<imts.size(); i++) {
				Imt imt = imts.get(i);
				Map<Double, Map<Double, Double>> rMap = imtMap.get(imt);
				if (rMap == null) {
					imtMap.put(imt, rMap = Maps.newHashMap());
				}
				Map<Double, Double> mMap = rMap.get(r);
				if (mMap == null) {
					rMap.put(r, mMap = Maps.newHashMap());
				}
				mMap.put(m, values.get(i + 1));
			}
				
			return true;
		}
	}
	
	/*
	 * Converts frequencies from Gail Atkinson style Gmm tables to IMTs.
	 * Frequencies corresponding to 0.03s, 0.3s, and 3s are variably identified
	 * and handled independently. AB06 uses 0.32, 3.2, and 32 which do not
	 * strictly correspond to 3s, 0.3s, and 0.03s, but we use them anyway.
	 */
	static class FrequencyToIMT implements Function<Double, Imt> {
		@Override
		public Imt apply(Double f) {
			if (threesLo.contains(f)) return SA3P0;
			if (threesMid.contains(f)) return SA0P3;
			if (threesHi.contains(f)) return SA0P03;
			if (f == 99.0) return PGA;
			if (f == 89.0) return PGV;
			return Imt.fromPeriod(1.0 / f);
		}
	}

	/* 
	 * Base implementation of navigable r-m map lookups. This assumes m and r
	 * values have already been clamped to the limits of the map/table.
	 */
	private static double get(double r, double m,
			NavigableMap<Double, NavigableMap<Double, Double>> rMap) {
		
		Map.Entry<Double, NavigableMap<Double,Double>> rHi = rMap.ceilingEntry(r);
		Map.Entry<Double, NavigableMap<Double,Double>> rLo = rMap.floorEntry(r);
		double rFrac = fraction(rLo.getKey(), rHi.getKey(), r);
		
		Map.Entry<Double, Double> rHi_mHi = rHi.getValue().ceilingEntry(m);
		Map.Entry<Double, Double> rHi_mLo = rHi.getValue().floorEntry(m);
		double mFrac = fraction(rHi_mLo.getKey(), rHi_mHi.getKey(), m);

		double gmRloMlo = rLo.getValue().floorEntry(m).getValue();
		double gmRloMhi = rLo.getValue().ceilingEntry(m).getValue();
		double gmRhiMlo = rHi_mLo.getValue();
		double gmRhiMhi = rHi_mHi.getValue();

		double gm = fractionalValue(
			fractionalValue(gmRloMlo, gmRloMhi, mFrac),
			fractionalValue(gmRhiMlo, gmRhiMhi, mFrac),
			rFrac);

		return gm * MathUtils.LOG_BASE_10_TO_E;
	}
	
	/* IO error handler */
	static void handleIOex(IOException ioe, String file) {
		StringBuilder sb = new StringBuilder(LF);
		sb.append("** IO error: ").append("Gmm table; ");
		sb.append(ioe.getMessage()).append(LF);
		sb.append("**   File: ").append(file).append(LF);
		sb.append("** Exiting **").append(LF);
		log.severe(sb.toString());
		System.exit(1);
	}

	/*
	 * Returns fractional position of value between lo and hi. Does not do any
	 * range checking. For use with the navigable maps of this class where hi
	 * will alwyas be greater than or equal to lo (same keys coming out of
	 * NavigableMaps) and value will never be greater than hi or less than lo.
	 * 
	 */
	private static double fraction(double lo, double hi, double value) {
		return (hi == lo) ? 0.0 : (value - lo) / (hi - lo);
	}
	
	/*
	 * Returns the value corresponding to the frac(tional) offset between lo
	 * and hi. 
	 */
	private static double fractionalValue(double lo, double hi, double frac) {
		return lo + frac * (hi - lo);
	}

	/*
	 * Returns the upper or lower bound of a closed Range<Double> if the
	 * supplied value is out of range, otherwise returns the value.
	 */
	private static double clamp(Range<Double> range, double value) {
		if (range.contains(value)) return value;
		return value < range.lowerEndpoint() ? range.lowerEndpoint() : range
			.upperEndpoint();
	}
	
	/*
	 * Derives the correct Imt froma filename.
	 */
	private static Imt frankelFilenameToIMT(String s) {
		if (s.startsWith("pga")) return PGA;
		StringBuilder sb = new StringBuilder();
		sb.append(s.charAt(1)).append('.').append(s.charAt(3));
		return Imt.fromPeriod(Double.valueOf(sb.toString()));
	}

	
	// TODO clean
//	static class FrankelTable implements GmmTable {
//
//		static final int M_IDX = 19;
//		static final int D_IDX = 20;
//
//		List<List<Double>> gmTable;
//
//		FrankelTable(String file) throws IOException {
//			URL url = Resources.getResource(GmmTables.class, T_DIR + file);
//			gmTable = Resources.readLines(url, Charsets.US_ASCII, new TableParser());
//		}
//		
//		FrankelTable(URL data) throws IOException {
//			gmTable = Resources.readLines(data, Charsets.US_ASCII,
//				new TableParser());
//		}
//
//		/*
//		 * Returns the interpolated value from the table that is constrainted to
//		 * the table bounds; i.e. arguments are clamped to table min max.
//		 */
//		public double get(double mag, double dist) {
//			// clamp values to min max
//			mag = clamp(frankRangeM, mag);
//			dist = clamp(frankRangeR, Math.log10(dist));
//
//			int mIdx, mIdx1, dIdx1, dIdx;
//			double mFrac, dFrac;
//			double gm1, gm2, gm3, gm4;
//
//			mIdx = idxForMag(mag);
//			dIdx = idxForDist(dist);
//			mIdx1 = Math.min(mIdx + 1, M_IDX);
//			dIdx1 = Math.min(dIdx + 1, D_IDX);
//
//			mFrac = (mag - magForIdx(mIdx)) / 0.2;
//			dFrac = (dist - distForIdx(dIdx)) / 0.1;
//
//			gm1 = gmTable.get(dIdx).get(mIdx);
//			gm2 = gmTable.get(dIdx).get(mIdx1);
//			gm3 = gmTable.get(dIdx1).get(mIdx);
//			gm4 = gmTable.get(dIdx1).get(mIdx1);
////			System.out.println(gm1 + " " + gm2 + " " + gm3 + " " + gm4);
//
//			double gmDlo = gm1 + mFrac * (gm2 - gm1);
//			double gmDhi = gm3 + mFrac * (gm4 - gm3);
//			double gm = gmDlo + dFrac * (gmDhi - gmDlo);
//
//			return gm * Utils.LOG_BASE_10_TO_E;
//
//		}
//
//		private static double magForIdx(int idx) {
//			return idx * 0.2 + frankRangeM.lowerEndpoint();
//		}
//
//		private static int idxForMag(double mag) {
//			// upscaling by *10 eliminates double rounding errors
//			return (int) (mag * 10 - frankRangeM.lowerEndpoint() * 10) / 2;
//		}
//
//		private static double distForIdx(int idx) {
//			return idx * 0.1 + frankRangeR.lowerEndpoint();
//		}
//
//		private static int idxForDist(double dist) {
//			// upscaling by *10 eliminates double rounding errors
//			return (int) (dist * 10 - frankRangeR.lowerEndpoint() * 10);
//		}
//	}
//
//	//@formatter:off
//	static class TableParser implements LineProcessor<List<List<Double>>> {
//		boolean firstLine = true;
//		List<List<Double>> data;
//		TableParser() { data = Lists.newArrayList(); }
//		@Override
//		public List<List<Double>> getResult() { return data; }
//		@Override
//		public boolean processLine(String line) throws IOException {
//			if (firstLine) {
//				firstLine = false;
//				return true;
//			}
//			List<Double> values = FluentIterable
//					.from(Parsing.splitOnSpaces(line))
//					.transform(Parsing.doubleValueFunction())
//					.skip(1)
//					.toList();
//			data.add(values);
//			return true;
//		}
//	}
//	//@formatter:on

	
}
