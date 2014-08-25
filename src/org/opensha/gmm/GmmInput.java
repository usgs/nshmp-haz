package org.opensha.gmm;

import static com.google.common.base.Preconditions.checkState;
import static java.lang.Double.NaN;
import static org.opensha.gmm.GmmInput.GmmField.DIP;
import static org.opensha.gmm.GmmInput.GmmField.MAG;
import static org.opensha.gmm.GmmInput.GmmField.RAKE;
import static org.opensha.gmm.GmmInput.GmmField.RJB;
import static org.opensha.gmm.GmmInput.GmmField.RRUP;
import static org.opensha.gmm.GmmInput.GmmField.RX;
import static org.opensha.gmm.GmmInput.GmmField.VS30;
import static org.opensha.gmm.GmmInput.GmmField.VSINF;
import static org.opensha.gmm.GmmInput.GmmField.WIDTH;
import static org.opensha.gmm.GmmInput.GmmField.Z1P0;
import static org.opensha.gmm.GmmInput.GmmField.Z2P5;
import static org.opensha.gmm.GmmInput.GmmField.ZHYP;
import static org.opensha.gmm.GmmInput.GmmField.ZTOP;

import java.util.BitSet;
import java.util.Map;

import org.opensha.calc.Site;
import org.opensha.eq.forecast.Distances;
import org.opensha.eq.forecast.Rupture;

import com.google.common.collect.Maps;

/**
 * Earthquake {@link Rupture} and receiver {@link Site} property container used
 * as input to {@link GroundMotionModel}s (GMMs). Not all GMMs use all
 * properties.
 * 
 * @author Peter Powers
 * @see GroundMotionModel#calc(GmmInput)
 */
public class GmmInput {

	// source
	final double Mw;

	final double rJB;
	final double rRup;
	final double rX;

	final double dip;
	final double width;
	final double zTop;
	final double zHyp;

	final double rake;

	// site
	final double vs30;
	final boolean vsInf;
	final double z2p5; // always in km
	final double z1p0; // always in meters TODO NO NO NO - this is now always
						// km, CY08 needs updating

	/**
	 * Create a deterministic rupture and site property container with all
	 * properties common to the supported set of ground motion models (GMMs).
	 * 
	 * <p>It is generally preferred to use a {@link Builder} to assemble the
	 * components of a {@code GmmInput}.</p>
	 * 
	 * @param Mw moment magnitude of rupture
	 * @param rJB Joyner-Boore distance to rupture (in km)
	 * @param rRup 3D distance to rupture plane (in km)
	 * @param rX distance X (in km)
	 * @param dip of rupture (in degrees)
	 * @param width down-dip rupture width (in km)
	 * @param zTop depth to the top of the rupture (in km)
	 * @param zHyp hypocentral depth (in km)
	 * @param rake of rupture
	 * @param vs30 average shear wave velocity in top 30 m (in m/sec)
	 * @param vsInf whether vs30 is an inferred or measured value
	 * @param z2p5 depth to V<sub>s</sub>=2.5 km/sec (in km)
	 * @param z1p0 depth to V<sub>s</sub>=1.0 km/sec (in km)
	 */
	protected GmmInput(double Mw, double rJB, double rRup, double rX, double dip, double width,
		double zTop, double zHyp, double rake, double vs30, boolean vsInf, double z2p5, double z1p0) {

		this.Mw = Mw;

		this.rJB = rJB;
		this.rRup = rRup;
		this.rX = rX;

		this.dip = dip;
		this.width = width;
		this.zTop = zTop;
		this.zHyp = zHyp;
		this.rake = rake;

		this.vs30 = vs30;
		this.vsInf = vsInf;
		this.z2p5 = z2p5;
		this.z1p0 = z1p0;
	}
	
	// for tests
	static GmmInput create(double Mw, double rJB, double rRup, double rX, double dip,
			double width, double zTop, double zHyp, double rake, double vs30, boolean vsInf,
			double z2p5, double z1p0) {

		return new GmmInput(Mw, rJB, rRup, rX, dip, width, zTop, zHyp, rake, vs30, vsInf,
			z2p5, z1p0);
	}

	/**
	 * Return a {@code GmmInput} builder that requires all fields to be
	 * explicitely set. This builder is stateful and may be reused (by a single
	 * thread) preserving previously set fields. However, repeat calls of
	 * builder methods are not permitted until {@code build()} has been called.
	 * 
	 * @see GmmInput.Builder
	 */
	public static Builder builder() {
		return new Builder();
	}

	@SuppressWarnings("javadoc")
	public static class Builder {

		static final int SIZE = GmmField.values().length;
		boolean built = false;
		BitSet flags = new BitSet(SIZE); // monitors set fields
		BitSet reset = new BitSet(SIZE); // monitors sets between build() calls

		private double Mw;

		private double rJB;
		private double rRup;
		private double rX;

		private double dip;
		private double width;
		private double zTop;
		private double zHyp;

		private double rake;

		// site
		private double vs30;
		private boolean vsInf;
		private double z2p5;
		private double z1p0;

		private Builder() {}

		/**
		 * Return a {@code Builder} with the following presets: <ul><li>Mw:
		 * 6.5</li><li>rJB: 10.0 (km)</li><li>rRup: 10.3 (km)</li><li>rX: 10.0
		 * (km)</li> <li>dip: 90˚</li><li>width: 14.0 (km)</li><li>zTop: 0.5
		 * (km)</li><li>zHyp: 7.5 (km)</li> <li>rake: 0˚</li><li>vs30: 760
		 * (m/s)</li><li>vsInf: true</li><li>z2p5: NaN</li><li>z1p0:
		 * NaN</li></ul>
		 */
		public Builder withDefaults() {
			Mw = 6.5;
			rJB = 10.0;
			rRup = 10.3;
			rX = 10.0;
			dip = 90.0;
			width = 14.0;
			zTop = 0.5;
			zHyp = 7.5;
			rake = 0.0;
			vs30 = 760.0;
			vsInf = true;
			z2p5 = NaN;
			z1p0 = NaN;
			flags.set(0, SIZE);
			return this;
		}

		/* returns the double value of interest for inlining */
		private final double validateAndFlag(GmmField field, double value) {
			int index = field.ordinal();
			checkState(!built && !reset.get(index));
			flags.set(index);
			reset.set(index);
			return value;
		}

		/* returns the boolean value of interest for inlining */
		private final boolean validateAndFlag(GmmField field, boolean value) {
			int index = field.ordinal();
			checkState(!built && !reset.get(index));
			flags.set(index);
			reset.set(index);
			return value;
		}

		public Builder mag(double Mw) {
			this.Mw = validateAndFlag(MAG, Mw);
			return this;
		}

		public Builder rJB(double rJB) {
			this.rJB = validateAndFlag(RJB, rJB);
			return this;
		}

		public Builder rRup(double rRup) {
			this.rRup = validateAndFlag(RRUP, rRup);
			return this;
		}

		public Builder rX(double rX) {
			this.rX = validateAndFlag(RX, rX);
			return this;
		}

		public Builder distances(double rJB, double rRup, double rX) {
			this.rJB = validateAndFlag(RJB, rJB);
			this.rRup = validateAndFlag(RRUP, rRup);
			this.rX = validateAndFlag(RX, rX);
			return this;
		}

		public Builder distances(Distances distances) {
			this.rJB = validateAndFlag(RJB, distances.rJB);
			this.rRup = validateAndFlag(RRUP, distances.rRup);
			this.rX = validateAndFlag(RX, distances.rX);
			return this;
		}

		public Builder dip(double dip) {
			this.dip = validateAndFlag(DIP, dip);
			return this;
		}

		public Builder width(double width) {
			this.width = validateAndFlag(WIDTH, width);
			return this;
		}

		public Builder zTop(double zTop) {
			this.zTop = validateAndFlag(ZTOP, zTop);
			return this;
		}

		public Builder zHyp(double zHyp) {
			this.zHyp = validateAndFlag(ZHYP, zHyp);
			return this;
		}

		public Builder rake(double rake) {
			this.rake = validateAndFlag(RAKE, rake);
			return this;
		}

		public Builder vs30(double vs30) {
			this.vs30 = validateAndFlag(VS30, vs30);
			return this;
		}

		public Builder vsInf(boolean vsInf) {
			this.vsInf = validateAndFlag(VSINF, vsInf);
			return this;
		}

		public Builder vs30(double vs30, boolean vsInf) {
			this.vs30 = validateAndFlag(VS30, vs30);
			this.vsInf = validateAndFlag(VSINF, vsInf);
			return this;
		}

		public Builder z2p5(double z2p5) {
			this.z2p5 = validateAndFlag(Z2P5, z2p5);
			return this;
		}

		public Builder z1p0(double z1p0) {
			this.z1p0 = validateAndFlag(Z1P0, z1p0);
			return this;
		}

		public GmmInput build() {
			checkState(flags.cardinality() == SIZE, "Not all fields set");
			reset.clear();
			return new GmmInput(Mw, rJB, rRup, rX, dip, width, zTop, zHyp, rake, vs30, vsInf, z2p5,
				z1p0);
		}
	}

	/**
	 * {@code GmmInput} field identifiers. This is used internally to manage
	 * builder flags and provides access to the case-sensitive keys (via
	 * toString()) used when building http queries
	 * 
	 */
	@SuppressWarnings("javadoc")
	public enum GmmField {
		MAG,
		RJB,
		RRUP,
		RX,
		DIP,
		WIDTH,
		ZTOP,
		ZHYP,
		RAKE,
		VS30,
		VSINF,
		Z2P5,
		Z1P0;

		@Override public String toString() {
			return this.name().toLowerCase();
		}

		public static GmmField fromString(String s) {
			return valueOf(s.toUpperCase());
		}
	}

	/**
	 * Some values [mag, rJB, rRup, rX, zHyp] may be truncated to 2 or 3 decimal
	 * places for output.
	 */
	@Override public String toString() {
		return createKeyValueMap().toString();
	}

	private Map<GmmField, Object> createKeyValueMap() {
		Map<GmmField, Object> keyValueMap = Maps.newEnumMap(GmmField.class);
		keyValueMap.put(MAG, String.format("%.2f", Mw));
		keyValueMap.put(RJB, String.format("%.3f", rJB));
		keyValueMap.put(RRUP, String.format("%.3f", rRup));
		keyValueMap.put(RX, String.format("%.3f", rX));
		keyValueMap.put(DIP, dip);
		keyValueMap.put(WIDTH, String.format("%.3f", width));
		keyValueMap.put(ZTOP, zTop);
		keyValueMap.put(ZHYP, String.format("%.3f", zHyp));
		keyValueMap.put(RAKE, rake);
		keyValueMap.put(VS30, vs30);
		keyValueMap.put(VSINF, vsInf);
		keyValueMap.put(Z2P5, z2p5);
		keyValueMap.put(Z1P0, z1p0);
		return keyValueMap;
	}

}
