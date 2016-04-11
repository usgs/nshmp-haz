package org.opensha2.gmm;

import static com.google.common.io.Resources.getResource;
import static com.google.common.io.Resources.readLines;
import static java.lang.Math.log10;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.opensha2.gmm.Imt.PGA;
import static org.opensha2.gmm.Imt.PGV;
import static org.opensha2.gmm.Imt.SA0P03;
import static org.opensha2.gmm.Imt.SA0P3;
import static org.opensha2.gmm.Imt.SA3P0;
import static org.opensha2.util.Parsing.splitToDoubleList;
import static org.opensha2.util.TextUtils.NEWLINE;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Logger;

import org.opensha2.util.Parsing;
import org.opensha2.util.Parsing.Delimiter;

import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.LineProcessor;
import com.google.common.primitives.Doubles;

/**
 * Utility class to load and fetch {@code GroundMotionModel} lookup tables.
 * 
 * The currently implemented tables store ground motion in log10 values;
 * additionaly, Atkinson flavored tables store ground motion in cm/s^2.
 * 
 * @author Peter Powers
 */
final class GroundMotionTables {

	// TODO NGA-East csv files may be linear R.
	
	static GroundMotionTable getFrankel96(Imt imt, SiteClass siteClass) {
		return siteClass == SiteClass.SOFT_ROCK ?
			frankelSoftRock.get(imt) :
			frankelHardRock.get(imt);
	}

	static GroundMotionTable getAtkinson06(Imt imt) {
		return atkinson06.get(imt);
	}

	static GroundMotionTable getAtkinson08(Imt imt) {
		return atkinson08.get(imt);
	}

	static GroundMotionTable getPezeshk11(Imt imt) {
		return pezeshk11.get(imt);
	}

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

	private static final double[] ATKINSON_R = {
		-1.000, 0.000, 0.301, 0.699, 1.000, 1.176, 1.301, 1.398, 1.477, 1.602,
		1.699, 1.778, 1.845, 1.903, 1.954, 2.000, 2.041, 2.079, 2.176, 2.301,
		2.398, 2.477, 2.544, 2.602, 2.699 };

	private static final double[] PEZESHK_R = {
		0.000, 0.301, 0.699, 1.000, 1.176, 1.301, 1.477, 1.602, 1.699, 1.778,
		1.845, 1.903, 2.000, 2.079, 2.146, 2.255, 2.301, 2.398, 2.477, 2.602,
		2.699, 2.778, 2.845, 2.903, 3.000 };

	private static final double[] FRANKEL_R = {
		1.0, 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8, 1.9, 2.0, 2.1,
		2.2, 2.3, 2.4, 2.5, 2.6, 2.7, 2.8, 2.9, 3.0 };

	private static final double[] ATKINSON_M = {
		4.00, 4.25, 4.50, 4.75, 5.00, 5.25, 5.50, 5.75, 6.00,
		6.25, 6.50, 6.75, 7.00, 7.25, 7.50, 7.75, 8.00 };

	private static final double[] PEZESHK_M = {
		4.50, 4.75, 5.00, 5.25, 5.50, 5.75, 6.00, 6.25,
		6.50, 6.75, 7.00, 7.25, 7.50, 7.75, 8.00 };

	private static final double[] FRANKEL_M = {
		4.4, 4.6, 4.8, 5.0, 5.2, 5.4, 5.6, 5.8, 6.0, 6.2, 6.4,
		6.6, 6.8, 7.0, 7.2, 7.4, 7.6, 7.8, 8.0, 8.2 };

	// different numeric representations of 0.33 3.3 and 33.0 Hz
	private static final Set<Double> FREQ3_LO = ImmutableSet.of(0.32, 0.33);
	private static final Set<Double> FREQ3_MID = ImmutableSet.of(3.2, 3.33);
	private static final Set<Double> FREQ3_HI = ImmutableSet.of(32.0, 33.0, 33.33);

	private static final Map<Imt, GroundMotionTable> frankelHardRock;
	private static final Map<Imt, GroundMotionTable> frankelSoftRock;
	private static final Map<Imt, GroundMotionTable> atkinson06;
	private static final Map<Imt, GroundMotionTable> atkinson08;
	private static final Map<Imt, GroundMotionTable> pezeshk11;

	static {
		frankelHardRock = initFrankel(frankelSrcHR, FRANKEL_R, FRANKEL_M);
		frankelSoftRock = initFrankel(frankelSrcSR, FRANKEL_R, FRANKEL_M);
		atkinson06 = initAtkinson(atkinson06src, ATKINSON_R, ATKINSON_M);
		atkinson08 = initAtkinson(atkinson08src, ATKINSON_R, ATKINSON_M);
		pezeshk11 = initAtkinson(pezeshk11src, PEZESHK_R, PEZESHK_M);
	}

	private static Map<Imt, GroundMotionTable> initFrankel(String[] files, double[] rKeys,
			double[] mKeys) {
		Map<Imt, GroundMotionTable> map = Maps.newEnumMap(Imt.class);
		for (String file : files) {
			try {
				Imt imt = frankelFilenameToIMT(file);
				URL url = getResource(GroundMotionTables.class, T_DIR + file);
				double[][] data = readLines(url, UTF_8, new FrankelParser());
				map.put(imt, new LogDistanceTable(data, rKeys, mKeys));
			} catch (IOException ioe) {
				handleIOex(ioe, file);
			}
		}
		return map;
	}

	private static Map<Imt, GroundMotionTable> initAtkinson(String file, double[] rKeys,
			double[] mKeys) {
		Map<Imt, GroundMotionTable> map = Maps.newEnumMap(Imt.class);
		URL url = getResource(GroundMotionTables.class, T_DIR + file);
		try {
			AtkinsonParser parser = new AtkinsonParser(rKeys.length);
			Map<Imt, double[][]> dataMap = readLines(url, UTF_8, parser);
			for (Entry<Imt, double[][]> entry : dataMap.entrySet()) {
				double[][] data = entry.getValue();
				map.put(entry.getKey(), new LogDistanceScalingTable(data, rKeys, mKeys));
			}
		} catch (IOException ioe) {
			handleIOex(ioe, file);
		}
		return map;
	}

	private static Imt frankelFilenameToIMT(String s) {
		if (s.startsWith("pga")) return PGA;
		StringBuilder sb = new StringBuilder();
		sb.append(s.charAt(1)).append('.').append(s.charAt(3));
		return Imt.fromPeriod(Double.valueOf(sb.toString()));
	}

	/* IO error handler */
	static void handleIOex(IOException ioe, String file) {
		StringBuilder sb = new StringBuilder(NEWLINE);
		sb.append("** IO error: ").append("GroundMotionTable; ");
		sb.append(ioe.getMessage()).append(NEWLINE);
		sb.append("**   File: ").append(file).append(NEWLINE);
		sb.append("** Exiting **").append(NEWLINE);
		Logger.getLogger(GroundMotionTables.class.getName()).severe(sb.toString());
		System.exit(1);
	}

	/*
	 * Interface implemented by handlers of table-based ground motion data.
	 * 
	 * Single method returns a interpolated ground motion value from the table.
	 * Values outside the range supported by the table are generally constrained
	 * to min or max values, although implementations may behave differently.
	 * Some implementations store data in log space and therefore perform log
	 * interpolation.
	 * 
	 * Whether r is rRup or rJB is implementation specific. Whether
	 */
	interface GroundMotionTable {

		/**
		 * Return a linearly interpolated ground motion value from the table.
		 * Values outside the range supported by the table are generally
		 * constrained to min or max values, although implementations may behave
		 * differently. Some implementations store data in log space and
		 * 
		 * @param m magnitude to consider
		 * @param r distance to consider, whether this is rRup or rJB is
		 *        implementation specific
		 * @return the natural log of the ground motion for the supplied
		 *         {@code r} and {@code m}
		 */
		double get(double r, double m);

	}

	/*
	 * NOTE No data validation is performed in this package private class. It's
	 * conceivable someone would supply an inapproprate distance. Negative
	 * distances yield an NaN result, r=0 will give the lowest value in a table;
	 * log10(0) = -Infinity (for whatever reason) which clamps to the low end of
	 * a table.
	 */

	/* Base table implementation */
	private static class ClampingTable implements GroundMotionTable {

		final double[][] data;
		final double[] rKeys;
		final double[] mKeys;

		ClampingTable(double[][] data, double[] rKeys, double[] mKeys) {
			this.data = data;
			this.rKeys = rKeys;
			this.mKeys = mKeys;
		}

		@Override public double get(final double r, final double m) {
			int ir = dataIndex(rKeys, r);
			int im = dataIndex(mKeys, m);
			double rFrac = fraction(rKeys[ir], rKeys[ir + 1], r);
			double mFrac = fraction(mKeys[im], mKeys[im + 1], m);
			return interpolate(
				data[ir][im],
				data[ir][im + 1],
				data[ir + 1][im],
				data[ir + 1][im + 1],
				mFrac,
				rFrac);
		}
	}

	/*
	 * For tables where r keys are log10
	 */
	private static class LogDistanceTable extends ClampingTable {

		LogDistanceTable(double[][] data, double[] rKeys, double[] mKeys) {
			super(data, rKeys, mKeys);
		}

		@Override public double get(final double r, final double m) {
			return super.get(log10(r), m);
		}
	}

	/*
	 * For tables where r keys are log10 and ground motion scales like 1/r
	 * beyond the table maximum.
	 */
	private static class LogDistanceScalingTable extends ClampingTable {

		final double rMax;

		LogDistanceScalingTable(double[][] data, double[] rKeys, double[] mKeys) {
			super(data, rKeys, mKeys);
			this.rMax = rKeys[rKeys.length - 1];
		}

		@Override public double get(final double r, final double m) {
			double rLog = log10(r);
			double μLog = super.get(rLog, m);
			return (rLog <= rMax) ? μLog : μLog - (rLog - rMax);
		}
	}

	// @formatter:off
	
	/*
	 * Basic bilinear interpolation
	 * 
	 *    c11---i1----c12
	 *     |     |     |
	 *     |-----o-----| < f2
	 *     |     |     |
	 *    c21---i2----c22
	 *           ^
	 *          f1
	 * 
	 */
	private static final double interpolate(
			double c11,
			double c12,
			double c21,
			double c22,
			double f1,
			double f2) {

		double i1 = c11 + f1 * (c12 - c11);
		double i2 = c21 + f1 * (c22 - c21);
		return i1 + f2 * (i2 - i1);
	}

	private static final double fraction(double lo, double hi, double value) {
		return value < lo ? 0.0 : value > hi ? 1.0 : (value - lo) / (hi - lo);
	}

	/*
	 * NOTE this was lifted from the interpolate class and could parhaps benefit
	 * from checking the size of 'data' and then doing linear instead of binary
	 * search.
	 * 
	 * This is a clamping index search algorithm; it will always return an index
	 * in the range [0, data.length - 2]; it is always used to get some value at
	 * index and index+1
	 */
	private static final int dataIndex(final double[] data, final double value) {
		int i = Arrays.binarySearch(data, value);
		// adjust index for low value (-1) and in-sequence insertion pt
		i = (i == -1) ? 0 : (i < 0) ? -i - 2 : i;
		// adjust hi index to next to last index
		return (i >= data.length - 1) ? --i : i;
	}
	
	private static class FrankelParser implements LineProcessor<double[][]> {

		boolean firstLine = true;
		List<List<Double>> data = Lists.newArrayList();

		@Override public double[][] getResult() {
			return toArray(data);
		}

		@Override public boolean processLine(String line) throws IOException {
			if (firstLine) {
				firstLine = false;
				return true;
			}
			List<Double> values = splitToDoubleList(line, Delimiter.SPACE);
			data.add(values.subList(1, values.size()));
			return true;
		}
	}

	/* Parser for Atkinson style tables */
	private static class AtkinsonParser implements LineProcessor<Map<Imt, double[][]>> {

		int lineIndex = -1;
		int rIndex = -1;
		final int rSize;
		List<Imt> imts = null;
		Map<Imt, List<List<Double>>> dataMap = Maps.newEnumMap(Imt.class);

		AtkinsonParser(int rSize) {
			this.rSize = rSize;
		}

		@Override public Map<Imt, double[][]> getResult() {

			Map<Imt, double[][]> out = Maps.newEnumMap(Imt.class);

			for (Entry<Imt, List<List<Double>>> entry : dataMap.entrySet()) {
				Imt imt = entry.getKey();
				out.put(imt, toArray(entry.getValue()));
			}
			return out;
		}

		@Override public boolean processLine(String line) throws IOException {
			lineIndex++;
			if (lineIndex < 2) return true;

			if (lineIndex == 2) {
				List<Imt> imtList = FluentIterable
					.from(Parsing.split(line, Delimiter.SPACE))
					.transform(Doubles.stringConverter())
					.transform(new FrequencyToIMT())
					.toList();
				// remove dupes -- (e.g., 2s PGA columns in P11)
				imts = Lists.newArrayList(new LinkedHashSet<Imt>(imtList));
				for (Imt imt : imts) {
					List<List<Double>> outerList = new ArrayList<List<Double>>(); // r
					dataMap.put(imt, outerList);
					for (int i = 0; i < rSize; i++) {
						List<Double> innerList = new ArrayList<Double>(); // m
						outerList.add(innerList);
					}
				}
				return true;
			}

			List<Double> values = Parsing.splitToDoubleList(line, Delimiter.SPACE);

			if (values.size() == 1) {
				// reset rIndex for every single mag line encountered
				rIndex = -1;
				return true;
			}

			if (values.isEmpty()) return true;

			rIndex++;
			for (int i = 0; i < imts.size(); i++) {
				Imt imt = imts.get(i);
				List<List<Double>> data = dataMap.get(imt);
				data.get(rIndex).add(values.get(i + 1));
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
		@Override public Imt apply(Double f) {
			if (FREQ3_LO.contains(f)) return SA3P0;
			if (FREQ3_MID.contains(f)) return SA0P3;
			if (FREQ3_HI.contains(f)) return SA0P03;
			if (f == 99.0) return PGA;
			if (f == 89.0) return PGV;
			return Imt.fromPeriod(1.0 / f);
		}
	}
	
	private static double[][] toArray(List<List<Double>> data) {
		int s1 = data.size();
		int s2 = data.get(0).size();
		double[][] out = new double[s1][s2];
		for (int i = 0; i < s1; i++) {
			for (int j = 0; j < s2; j++) {
				out[i][j] = data.get(i).get(j);
			}
		}
		return out;
	}

}
