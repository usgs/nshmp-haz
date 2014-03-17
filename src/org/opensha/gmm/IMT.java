package org.opensha.gmm;

import static com.google.common.math.DoubleMath.fuzzyEquals;

import java.util.EnumSet;
import java.util.Set;

/**
 * Intesity measure type (IMT) identifiers.
 * @author Peter Powers
 */
@SuppressWarnings("javadoc")
public enum IMT {

	PGA,
	PGV,
	PGD,
	SA0P01,
	SA0P02,
	SA0P025,
	SA0P03,
	SA0P04,
	SA0P05,
	SA0P075,
	SA0P08,
	SA0P1,
	SA0P12,
	SA0P15,
	SA0P17,
	SA0P2,
	SA0P25,
	SA0P3,
	SA0P4,
	SA0P5,
	SA0P6,
	SA0P7,
	SA0P75,
	SA0P8,
	SA0P9,
	SA1P0,
	SA1P25,
	SA1P5,
	SA2P0,
	SA2P5,
	SA3P0,
	SA4P0,
	SA5P0,
	SA6P0,
	SA7P5,
	SA10P0;

	/**
	 * Returns the corresponding period or frequency for this {@code IMT} if it
	 * represents a spectral acceleration.
	 * @return the period for this {@code IMT} if it represents a spectral
	 *         acceleration, {@code null} otherwise
	 */
	public Double period() {
		// TODO should this throw an IAE instead or return null?
		if (ordinal() < 3) return null;
		String valStr = name().substring(2).replace("P", ".");
		return Double.parseDouble(valStr);
	}

	/**
	 * Returns the spectral acceleration {@code IMT} associated with the
	 * supplied period. Due to potential floating point precision problems, this
	 * method internally checks values to within a small tolerance.
	 * @param period for {@code IMT}
	 * @return an {@code IMT}, or {@code null} if no IMT exsists for the
	 *         supplied period
	 */
	public static IMT fromPeriod(double period) {
		// TODO should this throw an IAE instead or return null?
		for (IMT imt : IMT.values()) {
			if (imt.name().startsWith("SA")) {
				double saPeriod = imt.period();
				if (fuzzyEquals(saPeriod, period, 0.000001)) return imt;
			}
		}
		return null;
	}
	
	/**
	 * Returns the frequency (in Hz) for this {@code IMT}. {@code PGA} returns
	 * 100 Hz, spectral periods return their expected value (1 / period), and
	 * {@code PGV} and {@code PGD} throw exceptions.
	 * @return thre frequency associated with this {@code IMT}
	 * @throws UnsupportedOperationException if called on {@code PGV} or
	 *         {@code PGD}
	 */
	public double frequency() {
		if (this == PGA) return 100;
		if (this.isSA()) return 1.0 / period();
		throw new UnsupportedOperationException(
			"frequncy() not supported for PGD and PGV");
	}
	
	/**
	 * Returns true if this IMT is some flavor of spectral acceleration.
	 * @return {@code true} if this is a spectral period, {@code false}
	 *         otherwise
	 */
	public boolean isSA() {
		return ordinal() > 2;
	}
	
	/**
	 * Returns the {@code Set} of spectal acceleration IMTs.
	 * @return the IMTs that represent spectral accelerations
	 */
	public static Set<IMT> saIMTs() {
		return EnumSet.complementOf(EnumSet.of(PGA, PGV, PGD));
	}

	/**
	 * Parses the supplied {@code String} into an {@code IMT}; method expects
	 * labels for specifically named intensity measure types ({@code "PGA"}) and
	 * double value {@code String}s for spectral periods ({@code "0.2"}). This
	 * method is <i>not</i> the same as {@link #valueOf(String)}.
	 * @param s {@code String} to parse
	 * @return an {@code IMT}, or {@code null} if supplied {@code String} is
	 *         invalid
	 */
	public static IMT parseIMT(String s) {
		s = s.trim().toUpperCase();
		if (s.equals("PGA") || s.equals("PGV") || s.equals("PGD")) {
			return IMT.valueOf(s);
		}
		try {
			double period = Double.parseDouble(s);
			return fromPeriod(period);
		} catch (NumberFormatException nfe) {
			return null;
		}
	}
	
	public static void main(String[] args) {
		IMT imt = PGA;
		System.out.println(imt);
		IMT currentIMT = imt;
		System.out.println(currentIMT);
		imt = SA1P0;
		System.out.println(currentIMT);
	}

}
