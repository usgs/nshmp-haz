package org.opensha2.gmm;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static java.lang.Double.NaN;
import static org.opensha2.gmm.GmmInput.Field.DIP;
import static org.opensha2.gmm.GmmInput.Field.MAG;
import static org.opensha2.gmm.GmmInput.Field.RAKE;
import static org.opensha2.gmm.GmmInput.Field.RJB;
import static org.opensha2.gmm.GmmInput.Field.RRUP;
import static org.opensha2.gmm.GmmInput.Field.RX;
import static org.opensha2.gmm.GmmInput.Field.VS30;
import static org.opensha2.gmm.GmmInput.Field.VSINF;
import static org.opensha2.gmm.GmmInput.Field.WIDTH;
import static org.opensha2.gmm.GmmInput.Field.Z1P0;
import static org.opensha2.gmm.GmmInput.Field.Z2P5;
import static org.opensha2.gmm.GmmInput.Field.ZHYP;
import static org.opensha2.gmm.GmmInput.Field.ZTOP;
import static org.opensha2.util.TextUtils.NEWLINE;

import java.util.BitSet;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.opensha2.calc.Site;
import org.opensha2.eq.model.Distance;
import org.opensha2.eq.model.Rupture;

import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import com.google.common.collect.Range;

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
	/** Depth to 1.0 km/s (in km). */
	public final double z1p0;
	/** Depth to 2.5 km/s (in km). */
	public final double z2p5;

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
	 * @param z1p0 depth to V<sub>s</sub>=1.0 km/sec (in km)
	 * @param z2p5 depth to V<sub>s</sub>=2.5 km/sec (in km)
	 */
	protected GmmInput(
			double Mw, double rJB, double rRup, double rX,
			double dip, double width, double zTop, double zHyp, double rake,
			double vs30, boolean vsInf, double z1p0, double z2p5) {

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
		this.z1p0 = z1p0;
		this.z2p5 = z2p5;
	}

	// for testing only
	static GmmInput create(
			double Mw, double rJB, double rRup, double rX,
			double dip, double width, double zTop, double zHyp, double rake,
			double vs30, boolean vsInf, double z2p5, double z1p0) {

		return new GmmInput(
			Mw, rJB, rRup, rX,
			dip, width, zTop, zHyp, rake,
			vs30, vsInf, z1p0, z2p5);
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
		private double z1p0;
		private double z2p5;

		private Builder() {}

		/**
		 * Return a {@code Builder} prepopulated with default values. Builder
		 * has the following presets: <ul><li>Mw: 6.5</li><li>rJB: 10.0
		 * (km)</li><li>rRup: 10.3 (km)</li><li>rX: 10.0 (km)</li> <li>dip:
		 * 90˚</li><li>width: 14.0 (km)</li><li>zTop: 0.5 (km)</li><li>zHyp: 7.5
		 * (km)</li> <li>rake: 0˚</li><li>vs30: 760 (m/s)</li><li>vsInf:
		 * true</li><li>z2p5: NaN</li><li>z1p0: NaN</li></ul>
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
			z1p0 = Z1P0.defaultValue;
			z2p5 = Z2P5.defaultValue;
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

		public Builder z1p0(double z1p0) {
			this.z1p0 = validateAndFlag(Z1P0, z1p0);
			return this;
		}

		public Builder z2p5(double z2p5) {
			this.z2p5 = validateAndFlag(Z2P5, z2p5);
			return this;
		}

		public GmmInput build() {
			checkState(flags.cardinality() == SIZE, "Not all fields set");
			reset.clear();
			return new GmmInput(
				Mw, rJB, rRup, rX,
				dip, width, zTop, zHyp, rake,
				vs30, vsInf, z1p0, z2p5);
		}
	}

	private static final String DISTANCE_UNIT = "km";
	private static final String ANGLE_UNIT = "°";

	/**
	 * {@code GmmInput} field identifiers. These are used internally to manage
	 * builder flags and provides access to the case-sensitive keys (via
	 * toString()) used when building http queries
	 * 
	 * Lets use negative positive for vsInf for now
	 */
	@SuppressWarnings("javadoc")
	public enum Field {

		MAG(
				"Magnitude",
				"The moment magnitude of an earthquake",
				null,
				6.5),

		RJB(
				"Joyner-Boore Distance",
				"The shortest distance from a site to the surface projection of a rupture, in kilometers",
				DISTANCE_UNIT,
				10.0),

		RRUP(
				"Rupture Distance",
				"The shortest distance from a site to a rupture, in kilometers",
				DISTANCE_UNIT,
				10.3),

		RX(
				"Distance X",
				"The shortest distance from a site to the extended trace a fault, in kilometers",
				DISTANCE_UNIT,
				10.0),

		DIP(
				"Dip",
				"The dip of a rupture surface, in degrees",
				ANGLE_UNIT,
				90.0),

		WIDTH(
				"Width",
				"The width of a rupture surface, in kilometers",
				DISTANCE_UNIT,
				14.0),

		ZTOP(
				"Depth",
				"The depth to the top of a rupture surface, in kilometers and positive-down",
				DISTANCE_UNIT,
				0.5),

		ZHYP(
				"Hypocentral Depth",
				"The depth to the hypocenter on a rupture surface, in kilometers and positive-down",
				DISTANCE_UNIT,
				7.5),

		RAKE(
				"Rake",
				"The rake (or sense of slip) of a rupture surface, in degrees",
				ANGLE_UNIT,
				0.0),

		VS30(
				"Vs30",
				"The average shear-wave velocity down to 30 meters, in kilometers per second",
				"km/s",
				760.0),

		VSINF(
				"Vs30 Inferred",
				"Whether Vs30 was measured or inferred",
				null,
				1.0),

		Z1P0(
				"Depth to Vs=1.0 km/s",
				"Depth to a shear-wave velocity of 1.0 kilometers per second, in kilometers",
				DISTANCE_UNIT,
				NaN),

		Z2P5(
				"Depth to Vs=2.5 km/s",
				"Depth to a shear-wave velocity of 2.5 kilometers per second, in kilometers",
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
		keyValueMap.put(Z1P0, z1p0);
		keyValueMap.put(Z2P5, z2p5);
		return keyValueMap;
	}

	/**
	 * Return a builder of {@code GmmInput} constraints. This builder sets all
	 * fields to {@code Optional.absent()} by default.
	 */
	static Constraints.Builder constraintsBuilder() {
		return new Constraints.Builder();
	}

	/**
	 * The constraints associated with each {@code GmmInput} field. All methods
	 * return an {@link Optional} whose {@link Optional#isPresent()} method will
	 * indicate whether a field is used by a {@code GroundMotionModel}, or not.
	 */
	@SuppressWarnings("javadoc")
	public static class Constraints {

		// TODO would moving to RangeSet be a satisfactory way
		// to handle discrete value sets (using Range.singleton)

		public final Optional<Range<Double>> mag;
		public final Optional<Range<Double>> rJB;
		public final Optional<Range<Double>> rRup;
		public final Optional<Range<Double>> rX;
		public final Optional<Range<Double>> dip;
		public final Optional<Range<Double>> width;
		public final Optional<Range<Double>> zTop;
		public final Optional<Range<Double>> zHyp;
		public final Optional<Range<Double>> rake;
		public final Optional<Range<Double>> vs30;
		public final Optional<Range<Boolean>> vsInf;
		public final Optional<Range<Double>> z1p0;
		public final Optional<Range<Double>> z2p5;

		// for internal use only
		private Map<Field, Optional<?>> constraintMap;

		private Constraints(
				Optional<Range<Double>> mag,
				Optional<Range<Double>> rJB,
				Optional<Range<Double>> rRup,
				Optional<Range<Double>> rX,
				Optional<Range<Double>> dip,
				Optional<Range<Double>> width,
				Optional<Range<Double>> zTop,
				Optional<Range<Double>> zHyp,
				Optional<Range<Double>> rake,
				Optional<Range<Double>> vs30,
				Optional<Range<Boolean>> vsInf,
				Optional<Range<Double>> z1p0,
				Optional<Range<Double>> z2p5) {

			constraintMap = new EnumMap<>(Field.class);

			this.mag = mag;
			constraintMap.put(MAG, mag);

			this.rJB = rJB;
			constraintMap.put(RJB, rJB);

			this.rRup = rRup;
			constraintMap.put(RRUP, rRup);

			this.rX = rX;
			constraintMap.put(RX, rX);

			this.dip = dip;
			constraintMap.put(DIP, dip);

			this.width = width;
			constraintMap.put(WIDTH, width);

			this.zTop = zTop;
			constraintMap.put(ZTOP, zTop);

			this.zHyp = zHyp;
			constraintMap.put(ZHYP, zHyp);

			this.rake = rake;
			constraintMap.put(RAKE, rake);

			this.vs30 = vs30;
			constraintMap.put(VS30, vs30);

			this.vsInf = vsInf;
			constraintMap.put(VSINF, vsInf);

			this.z1p0 = z1p0;
			constraintMap.put(Z1P0, z1p0);

			this.z2p5 = z2p5;
			constraintMap.put(Z2P5, z2p5);

		}

		@Override public String toString() {
			StringBuilder sb = new StringBuilder("Constraints: ").append(NEWLINE);
			sb.append(HEADER).append(NEWLINE);
			sb.append(string());
			return sb.toString();
		}

		/**
		 * Creates a structured {@code String} representation of the
		 * {@code Constraints} associated with the supplied set of {@code Gmm}s.
		 */
		static String toString(Set<Gmm> gmms) {
			StringBuilder sb = new StringBuilder("Constraints table:");
			sb.append(NEWLINE);
			sb.append(Strings.padEnd("GMM", CONSTRAINT_TABLE_COL1_WIDTH, ' '));
			sb.append(HEADER).append(NEWLINE);
			for (Gmm gmm : gmms) {
				sb.append(Strings.padEnd(gmm.name(), CONSTRAINT_TABLE_COL1_WIDTH, ' '));
				sb.append(gmm.constraints().string());
				sb.append(NEWLINE);
			}
			return sb.toString();
		}

		private static final int CONSTRAINT_STR_COL_WIDTH = 16;
		private static final int CONSTRAINT_TABLE_COL1_WIDTH = 28;
		private static final String HEADER;

		static {
			StringBuilder sb = new StringBuilder();
			for (Field field : Field.values()) {
				sb.append(Strings.padEnd(field.name(), CONSTRAINT_STR_COL_WIDTH, ' '));
			}
			HEADER = sb.toString();
		}

		private StringBuffer string() {
			StringBuffer sb = new StringBuffer();
			for (Entry<Field, Optional<?>> entry : constraintMap.entrySet()) {
				Optional<?> opt = entry.getValue();
				String optStr = Strings.padEnd(
					opt.isPresent() ? opt.get().toString() : "", CONSTRAINT_STR_COL_WIDTH, ' ');
				sb.append(optStr);
			}
			return sb;
		}

		static class Builder {

			private Optional<Range<Double>> mag = Optional.absent();
			private Optional<Range<Double>> rJB = Optional.absent();
			private Optional<Range<Double>> rRup = Optional.absent();
			private Optional<Range<Double>> rX = Optional.absent();
			private Optional<Range<Double>> dip = Optional.absent();
			private Optional<Range<Double>> width = Optional.absent();
			private Optional<Range<Double>> zTop = Optional.absent();
			private Optional<Range<Double>> zHyp = Optional.absent();
			private Optional<Range<Double>> rake = Optional.absent();
			private Optional<Range<Double>> vs30 = Optional.absent();
			private Optional<Range<Boolean>> vsInf = Optional.absent();
			private Optional<Range<Double>> z1p0 = Optional.absent();
			private Optional<Range<Double>> z2p5 = Optional.absent();

			/**
			 * Set {@code Range<Double>} constraint.
			 */
			Builder set(Field id, Range<Double> constraint) {
				checkArgument(EnumSet.complementOf(EnumSet.of(Field.VSINF)).contains(id));
				switch (id) {
					case MAG:
						mag = Optional.of(constraint);
						break;
					case RJB:
						rJB = Optional.of(constraint);
						break;
					case RRUP:
						rRup = Optional.of(constraint);
						break;
					case RX:
						rX = Optional.of(constraint);
						break;
					case ZTOP:
						zTop = Optional.of(constraint);
						break;
					case ZHYP:
						zHyp = Optional.of(constraint);
						break;
					case DIP:
						dip = Optional.of(constraint);
						break;
					case WIDTH:
						width = Optional.of(constraint);
						break;
					case RAKE:
						rake = Optional.of(constraint);
						break;
					case VS30:
						vs30 = Optional.of(constraint);
						break;
					case Z1P0:
						z1p0 = Optional.of(constraint);
						break;
					case Z2P5:
						z2p5 = Optional.of(constraint);
						break;
					default:
						throw new IllegalArgumentException(
							"GmmInput.Constraints.Builder " +
								"Unsupported field: " + id.name());
				}
				return this;
			}

			/**
			 * Sets a {@code Boolean} constraint.
			 */
			Builder set(Field id) {
				checkArgument(EnumSet.of(Field.VSINF).contains(id));
				vsInf = Optional.of(Range.closed(false, true));
				return this;
			}

			/**
			 * Set all distance metrics [rJB, rRup, rX] to the range [0, r].
			 */
			Builder setDistances(double r) {
				rJB = Optional.of(Range.closed(0.0, r));
				rRup = Optional.of(Range.closed(0.0, r));
				rX = Optional.of(Range.closed(0.0, r));
				return this;
			}

			/**
			 * Create the {@code Constraints}.
			 */
			Constraints build() {
				return new Constraints(
					mag,
					rJB, rRup, rX,
					dip, width, zTop, zHyp, rake,
					vs30, vsInf,
					z1p0, z2p5);
			}
		}

	}

}
