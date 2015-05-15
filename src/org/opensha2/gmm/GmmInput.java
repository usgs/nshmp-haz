package org.opensha.gmm;

import static com.google.common.base.Preconditions.checkState;
import static java.lang.Double.NaN;
import static org.opensha.gmm.GmmInput.Field.DIP;
import static org.opensha.gmm.GmmInput.Field.MAG;
import static org.opensha.gmm.GmmInput.Field.RAKE;
import static org.opensha.gmm.GmmInput.Field.RJB;
import static org.opensha.gmm.GmmInput.Field.RRUP;
import static org.opensha.gmm.GmmInput.Field.RX;
import static org.opensha.gmm.GmmInput.Field.VS30;
import static org.opensha.gmm.GmmInput.Field.VSINF;
import static org.opensha.gmm.GmmInput.Field.WIDTH;
import static org.opensha.gmm.GmmInput.Field.Z1P0;
import static org.opensha.gmm.GmmInput.Field.Z2P5;
import static org.opensha.gmm.GmmInput.Field.ZHYP;
import static org.opensha.gmm.GmmInput.Field.ZTOP;

import java.util.BitSet;
import java.util.Map;

import org.opensha.calc.Site;
import org.opensha.eq.model.Distance;
import org.opensha.eq.model.Rupture;

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

	/** Moment magnitude. */
	public final double Mw;

	/** Joyner-Boore distance (distance to surface projection of rupture). */
	public final double rJB;
	/** Rupture distance (distance to rupture plane). */
	public final double rRup;
	/** Distance X (shortest distance to extended strike of rupture). */
	public final double rX;

	/** Rupture dip. */
	public final double dip;
	/** Rupture width. */
	public final double width;
	/** Depth to top of rupture. */
	public final double zTop;
	/** Depth to rupture hypocenter. */
	public final double zHyp;
	/** Rupture rake. */
	public final double rake;

	/** Vs30 at site. */
	public final double vs30;
	/** Whether vs30 is inferred or measured. */
	public final boolean vsInf;
	/** Depth to 2.5 km/s (in km). */
	public final double z2p5;
	/** Depth to 1.0 km/s (in km). */
	public final double z1p0; // km, TODO CY08 needs updating

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
	static GmmInput create(double Mw, double rJB, double rRup, double rX, double dip, double width,
			double zTop, double zHyp, double rake, double vs30, boolean vsInf, double z2p5,
			double z1p0) {

		return new GmmInput(Mw, rJB, rRup, rX, dip, width, zTop, zHyp, rake, vs30, vsInf, z2p5,
			z1p0);
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

		static final int SIZE = Field.values().length;
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
		 * Return a preloaded {@code Builder}. Builder has the following
		 * presets: <ul><li>Mw: 6.5</li><li>rJB: 10.0 (km)</li><li>rRup: 10.3
		 * (km)</li><li>rX: 10.0 (km)</li> <li>dip: 90˚</li><li>width: 14.0
		 * (km)</li><li>zTop: 0.5 (km)</li><li>zHyp: 7.5 (km)</li> <li>rake:
		 * 0˚</li><li>vs30: 760 (m/s)</li><li>vsInf: true</li><li>z2p5:
		 * NaN</li><li>z1p0: NaN</li></ul>
		 * 
		 * TODO just point to dcouemnted static final fields
		 */
		public Builder withDefaults() {
			Mw = MAG.defaultValue;
			rJB = RJB.defaultValue;
			rRup = RRUP.defaultValue;
			rX = RX.defaultValue;
			dip = DIP.defaultValue;
			width = WIDTH.defaultValue;
			zTop = ZTOP.defaultValue;
			zHyp = ZHYP.defaultValue;
			rake = RAKE.defaultValue;
			vs30 = VS30.defaultValue;
			vsInf = VSINF.defaultValue > 0.0;
			z2p5 = Z2P5.defaultValue;
			z1p0 = Z1P0.defaultValue;
			flags.set(0, SIZE);
			return this;
		}

		/* returns the double value of interest for inlining */
		private final double validateAndFlag(Field field, double value) {
			int index = field.ordinal();
			checkState(!built && !reset.get(index));
			flags.set(index);
			reset.set(index);
			return value;
		}

		/* returns the boolean value of interest for inlining */
		private final boolean validateAndFlag(Field field, boolean value) {
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

		public Builder distances(Distance distances) {
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

	private static final String DISTANCE_UNIT = "km";
	private static final String ANGLE_UNIT = "°";

	/**
	 * {@code GmmInput} field identifiers. This is used internally to manage
	 * builder flags and provides access to the case-sensitive keys (via
	 * toString()) used when building http queries
	 * 
	 * Lets use negative positive for vsInf for now
	 */
	@SuppressWarnings("javadoc")
	public enum Field {
		
		// @formatter:off
		
		MAG("Magnitude",
			"The moment magnitude of an earthquake",
			null,
			6.5),
		
		RJB("Joyner-Boore Distance",
			"The shortest distance from a site to the surface projection of a rupture, in kilometers",
			DISTANCE_UNIT, 
			10.0),
			
		RRUP("Rupture Distance",
			"The shortest distance from a site to a rupture, in kilometers",
			DISTANCE_UNIT, 
			10.3),
			
		RX("Distance X",
			"The shortest distance from a site to the extended trace a fault, in kilometers",
			DISTANCE_UNIT,
			10.0),
			
		DIP("Dip",
			"The dip of a rupture surface, in degrees",
			ANGLE_UNIT,
			90.0),
			
		WIDTH("Width",
			"The width of a rupture surface, in kilometers",
			DISTANCE_UNIT,
			14.0),
			
		ZTOP("Depth",
			"The depth to the top of a rupture surface, in kilometers and positive-down",
			DISTANCE_UNIT,
			0.5),
			
		ZHYP("Hypocentral Depth",
			"The depth to the hypocenter on a rupture surface, in kilometers and positive-down",
			DISTANCE_UNIT,
			7.5),
			
		RAKE("Rake",
			"The rake (or sense of slip) of a rupture surface, in degrees",
			ANGLE_UNIT,
			0.0),
			
		VS30("Vs30",
			"The average shear-wave velocity down to 30 meters, in kilometers per second",
			"km/s",
			760.0),
			
		VSINF("Vs30 Inferred",
			"Whether Vs30 was measured or inferred",
			null,
			1.0),
		Z2P5("Depth to Vs=2.5 km/s",
			"Depth to a shear-wave velocity of 2.5 kilometers per second, in kilometers",
			DISTANCE_UNIT,
			NaN),
			
		Z1P0("Depth to Vs=1.0 km/s",
			"Depth to a shear-wave velocity of 1.0 kilometers per second, in kilometers",
			DISTANCE_UNIT,
			NaN);
		
		public final String label;
		public final String info;
		public final String unit;
		public final double defaultValue;
		
		private Field(String label, String info, String unit, double defaultValue) {
			this.label = label;
			this.info = info;
			this.unit = unit;
			this.defaultValue = defaultValue;
		}


		@Override public String toString() {
			return this.name().toLowerCase();
		}

		public static Field fromString(String s) {
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

	private Map<Field, Object> createKeyValueMap() {
		Map<Field, Object> keyValueMap = Maps.newEnumMap(Field.class);
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
