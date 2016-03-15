package org.opensha2.calc;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.opensha2.data.Data.checkInRange;
import static org.opensha2.util.GeoJson.validateProperty;

import java.lang.reflect.Type;
import java.util.Set;

import org.opensha2.geo.Location;
import org.opensha2.gmm.GroundMotionModel;
import org.opensha2.util.GeoJson;
import org.opensha2.util.Named;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Range;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Site characteristics container. Take note of default values; this minimum
 * information required to create a {@code Site} is its location. Not all
 * {@link GroundMotionModel}s will use all fields and additional fields may be
 * added at any time in the future.
 * 
 * @author Peter Powers
 */
public class Site implements Named {

	/** Minimum allowed Vs30 value (150 m/sec). */
	public static final double MIN_VS_30 = 150.0;

	/** Maximum allowed Vs30 value (2000 m/sec). */
	public static final double MAX_VS_30 = 2000.0;

	/** Default Vs30 value (760 m/sec). */
	public static final double DEFAULT_VS_30 = 760.0;

	/**
	 * Minimum allowed depth to a shear wave velocity of 2.5 km/sec (0.0 km).
	 */
	public static final double MIN_Z2P5 = 0.0;

	/**
	 * Minimum allowed depth to a shear wave velocity of 2.5 km/sec (5.0 km).
	 */
	public static final double MAX_Z2P5 = 5.0;

	/**
	 * Minimum allowed depth to a shear wave velocity of 1.0 km/sec (0.0 km).
	 */
	public static final double MIN_Z1P0 = 0.0;

	/**
	 * Minimum allowed depth to a shear wave velocity of 1.0 km/sec (2.0 km).
	 */
	public static final double MAX_Z1P0 = 2.0;

	/** The name used for {@code Site}s with no supplied name. */
	public static final String NO_NAME = "Unnamed";

	/** {@link #MIN_VS_30} and {@link #MAX_VS_30} as a closed {@code Range}. */
	public static final Range<Double> VS30_RANGE = Range.closed(MIN_VS_30, MAX_VS_30);

	/** {@link #MIN_Z2P5} and {@link #MAX_Z2P5} as a closed {@code Range}. */
	public static final Range<Double> Z2P5_RANGE = Range.closed(MIN_Z2P5, MAX_Z2P5);

	/** {@link #MIN_Z1P0} and {@link #MAX_Z1P0} as a closed {@code Range}. */
	public static final Range<Double> Z1P0_RANGE = Range.closed(MIN_Z1P0, MAX_Z1P0);

	/** The site name. */
	public final String name;

	/**
	 * The location of this site. This is the only field that must be
	 * explicitely set in a {@link Builder}.
	 */
	public final Location location;

	/**
	 * The average shear-wave velocity down to 30 meters depth.
	 * 
	 * <p>Default: 760.0 m/sec</p>
	 */
	public final double vs30;

	/**
	 * Whether Vs30 was inferred, {@code true}, or measured, {@code false}.
	 * 
	 * <p>Default: true (inferred)</p>
	 */
	public final boolean vsInferred;

	/**
	 * Depth to the shear-wave velocity horizon of 1.0 km/sec, in km.
	 * 
	 * <p>Default: {@code NaN} ({@link GroundMotionModel}s will use a default
	 * value or model)</p>
	 */
	public final double z1p0;

	/**
	 * Depth to the shear-wave velocity horizon of 2.5 km/sec, in km;
	 * 
	 * <p>Default: {@code NaN} ({@link GroundMotionModel}s will use a default
	 * value or model)</p>
	 */
	public final double z2p5;

	private Site(String name, Location location, double vs30, boolean vsInferred, double z1p0,
			double z2p5) {

		this.name = name;
		this.location = location;
		this.vs30 = vs30;
		this.vsInferred = vsInferred;
		this.z1p0 = z1p0;
		this.z2p5 = z2p5;
	}

	@Override
	public String toString() {
		return new StringBuilder(Strings.padEnd(name, 16, ' '))
			.append(String.format("%.3f %.3f Vs30=%s ", location.lon(), location.lat(), vs30))
			.append(vsInferred ? "inferred " : "measured ")
			.append(String.format("Z1.0=%s Z2.5=%s", z1p0, z2p5))
			.toString();
	}

	@Override
	public String name() {
		return name;
	}

	/**
	 * Return a fresh {@link Builder}.
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * A reusable {@code Site} builder. In the absence of specifying any site
	 * characteristics other than location (required), a default {@code Site} is
	 * returned by {@code build()}. Builder instances may be obtained via
	 * {@link Site#builder()}.
	 *
	 * @see Site for default values
	 */
	public static class Builder {

		private String name = NO_NAME;
		private Location location;
		private double vs30 = DEFAULT_VS_30;
		private boolean vsInferred = true;
		private double z1p0 = Double.NaN;
		private double z2p5 = Double.NaN;

		private Builder() {}

		/** The name of the {@code Site}. */
		public Builder name(String name) {
			this.name = checkNotNull(name);
			return this;
		}

		/** The location of the {@code Site}. */
		public Builder location(Location loc) {
			this.location = checkNotNull(loc);
			return this;
		}

		/**
		 * The location of the {@code Site}; also sets the {@code Site} name.
		 */
		public Builder location(NamedLocation namedLoc) {
			this.location = checkNotNull(namedLoc).location();
			this.name = namedLoc.toString();
			return this;
		}

		/** The location of the {@code Site}. */
		public Builder location(double lat, double lon) {
			this.location = Location.create(lat, lon);
			return this;
		}

		/** The average shear-wave velocity down to 30 meters depth. */
		public Builder vs30(double vs30) {
			this.vs30 = checkInRange(VS30_RANGE, Site.Key.VS30, vs30);
			return this;
		}

		/**
		 * Whether Vs30 was inferred, {@code true}, or measured {@code false}.
		 */
		public Builder vsInferred(boolean vsInferred) {
			this.vsInferred = vsInferred;
			return this;
		}

		/** Depth to the shear-wave velocity horizon of 1.0 km/sec, in km. */
		public Builder z1p0(double z1p0) {
			this.z1p0 = checkInRange(Z1P0_RANGE, Site.Key.Z1P0, z1p0);
			return this;
		}

		/** Depth to the shear-wave velocity horizon of 2.5 km/sec, in km. */
		public Builder z2p5(double z2p5) {
			this.z2p5 = checkInRange(Z2P5_RANGE, Site.Key.Z2P5, z2p5);
			return this;
		}

		/**
		 * Build the {@code Site}.
		 */
		public Site build() {
			checkState(location != null, "Site location not set");
			return new Site(name, location, vs30, vsInferred, z1p0, z2p5);
		}

	}

	/* Json and csv serialization keys */
	static final class Key {
		static final String NAME = "name";
		static final String LAT = "lat";
		static final String LON = "lon";
		static final String VS30 = "vs30";
		static final String VS_INF = "vsInf";
		static final String Z1P0 = "z1p0";
		static final String Z2P5 = "z2p5";
	}

	/* Json and csv serialization key set */
	static final Set<String> KEYS = ImmutableSet.of(
		Key.NAME,
		Key.LAT,
		Key.LON,
		Key.VS30,
		Key.VS_INF,
		Key.Z1P0,
		Key.Z2P5);

	/*
	 * Custom deserializer that takes care of several issues with sites.
	 * Specifically, JSON prohibits the use of NaN, which is the default value
	 * for z1p0 and z2p5, and so these two fields may not be set. Users have
	 * been notified that as long as no z1p0 or z2p5 value has been supplied in
	 * any JSON, the default will be used.
	 */

	static final class Deserializer implements JsonDeserializer<Site> {

		@Override
		public Site deserialize(
				JsonElement json,
				Type type,
				JsonDeserializationContext context) {

			JsonObject feature = json.getAsJsonObject();
			validateProperty(feature, GeoJson.Key.TYPE, GeoJson.Value.FEATURE);

			JsonObject geometry = feature.getAsJsonObject(GeoJson.Key.GEOMETRY);
			validateProperty(geometry, GeoJson.Key.TYPE, GeoJson.Value.POINT);

			JsonArray coordinates = feature
				.getAsJsonObject(GeoJson.Key.GEOMETRY)
				.getAsJsonArray(GeoJson.Key.COORDINATES);

			Builder builder = Site.builder().location(
				coordinates.get(1).getAsDouble(),
				coordinates.get(0).getAsDouble());

			JsonObject properties = feature.getAsJsonObject(GeoJson.Key.PROPERTIES);

			JsonElement name = properties.get(GeoJson.Properties.Key.TITLE);
			if (name != null) builder.name(name.getAsString());

			JsonElement vs30 = properties.get(Site.Key.VS30);
			if (vs30 != null) builder.vs30(vs30.getAsDouble());

			JsonElement vsInf = properties.get(Site.Key.VS_INF);
			if (vsInf != null) builder.vsInferred(vs30.getAsBoolean());

			JsonElement z1p0 = properties.get(Site.Key.Z1P0);
			if (z1p0 != null) builder.z1p0(z1p0.getAsDouble());

			JsonElement z2p5 = properties.get(Site.Key.Z2P5);
			if (z2p5 != null) builder.z2p5(z2p5.getAsDouble());

			return builder.build();
		}
	}

}
