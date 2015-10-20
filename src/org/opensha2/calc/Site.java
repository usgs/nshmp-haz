package org.opensha2.calc;

import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_UNDERSCORE;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.opensha2.data.Data.checkInRange;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.Map.Entry;

import org.opensha2.geo.Location;
import org.opensha2.gmm.GroundMotionModel;
import org.opensha2.util.Named;

import com.google.common.collect.Range;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

/**
 * Site characteristics container. Take note of default values. Not all
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

	/** Minimum allowed depth to a shear wave velocity of 2.5 km/sec (0.0 km). */
	public static final double MIN_Z2P5 = 0.0;
	/** Minimum allowed depth to a shear wave velocity of 2.5 km/sec (5.0 km). */
	public static final double MAX_Z2P5 = 5.0;

	/** Minimum allowed depth to a shear wave velocity of 1.0 km/sec (0.0 km). */
	public static final double MIN_Z1P0 = 0.0;
	/** Minimum allowed depth to a shear wave velocity of 1.0 km/sec (2.0 km). */
	public static final double MAX_Z1P0 = 2.0;

	/** The name used for {@code Site}s with no supplied name. */
	public static final String NO_NAME = "Unnamed Site";

	private static final Range<Double> VS30_RANGE = Range.closed(MIN_VS_30, MAX_VS_30);
	private static final Range<Double> Z2P5_RANGE = Range.closed(MIN_Z2P5, MAX_Z2P5);
	private static final Range<Double> Z1P0_RANGE = Range.closed(MIN_Z1P0, MAX_Z1P0);

	enum Key {
		NAME,
		LOCATION,
		LAT,
		LON,
		VS30,
		VS_INF,
		Z1P0,
		Z2P5;

		@Override public String toString() {
			return UPPER_UNDERSCORE.to(LOWER_CAMEL, name());
		}

		static Key fromString(String s) {
			return valueOf(LOWER_CAMEL.to(UPPER_UNDERSCORE, s));
		}
	}

	/** The site name. */
	public final String name;

	/**
	 * The location.
	 * 
	 * <p>Default: lat=34.05, lon=-118.25 (Los Angeles, CA)</p>
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

	@Override public String toString() {
		return String.format("Site: {name=%s, loc=%s, vs30=%s, vsInf=%s, z1p0=%s, z2p5=%s}", name,
			location, vs30, vsInferred, z1p0, z2p5);
	}

	@Override public String name() {
		return name;
	}

	/**
	 * Creates an {@code Iterable<Site>} from the comma-delimted site file
	 * designated by {@code path}.
	 * 
	 * @param path to comma-delimited site data file
	 * @throws IOException if a problem is encountered
	 */
	public static Iterable<Site> fromCsv(Path path) throws IOException {
		return SiteSet.fromCsv(path);
	}

	/**
	 * Return a fresh {@link Builder}.
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * A reusable {@code Site} builder. In the absence of specifying any site
	 * characteristics, a default {@code Site} is returned by {@code build()}.
	 *
	 * @see Site for default values
	 */
	public static class Builder {

		private String name = NO_NAME;
		private Location location = NehrpTestCity.LOS_ANGELES.location();
		private double vs30 = DEFAULT_VS_30;
		private boolean vsInferred = true;
		private double z1p0 = Double.NaN;
		private double z2p5 = Double.NaN;

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

		/** The location of the {@code Site}; also sets the {@code Site} name. */
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
			this.vs30 = checkInRange(VS30_RANGE, Key.VS30.toString(), vs30);
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
			this.z1p0 = checkInRange(Z1P0_RANGE, Key.Z1P0.toString(), z1p0);
			return this;
		}

		/** Depth to the shear-wave velocity horizon of 2.5 km/sec, in km. */
		public Builder z2p5(double z2p5) {
			this.z2p5 = checkInRange(Z2P5_RANGE, Key.Z2P5.toString(), z2p5);
			return this;
		}

		/* Available for other package parsers */
		Builder set(Key key, JsonElement json) {
			switch (key) {

				case NAME:
					return name(json.getAsString());
				case LOCATION:
					JsonArray coords = json.getAsJsonArray();
					double lon = coords.get(0).getAsDouble();
					double lat = coords.get(1).getAsDouble();
					return location(lat, lon);
				case VS30:
					return vs30(json.getAsDouble());
				case VS_INF:
					return vsInferred(json.getAsBoolean());
				case Z1P0:
					return z1p0(json.getAsDouble());
				case Z2P5:
					return z2p5(json.getAsDouble());
				default:
					throw new IllegalStateException("Unsupported site property key: " + key);
			}
		}

		/**
		 * Build the {@code Site}.
		 */
		public Site build() {
			return new Site(name, location, vs30, vsInferred, z1p0, z2p5);
		}

	}

	/*
	 * Custom (de)serializers that take care of several issues with sites.
	 * Specifically, JSON prohibits the use of NaN, which is the default value
	 * for z1p0 and z2p5, and so these two fields may not be set. Users have
	 * been notified that as long as no z1p0 or z2p5 value has been supplied in
	 * any JSON, the default will be used. Also, a Location stores data
	 * internally in radians and so we ensure that user-friendly decimal degree
	 * values are used udring (de)serialization.
	 */

	static class Deserializer implements JsonDeserializer<Site> {

		@Override public Site deserialize(JsonElement json, Type typeOfT,
				JsonDeserializationContext context) throws JsonParseException {

			JsonObject jObj = json.getAsJsonObject();
			Builder builder = Site.builder();

			for (Entry<String, JsonElement> entry : jObj.entrySet()) {
				Key key = Key.fromString(entry.getKey());
				builder.set(key, entry.getValue());
			}

			return builder.build();
		}
	}

	static class Serializer implements JsonSerializer<Site> {

		@Override public JsonElement serialize(Site site, Type typeOfSrc,
				JsonSerializationContext context) {

			JsonObject jObj = new JsonObject();
			jObj.addProperty(Key.NAME.toString(), site.name);
			double[] coords = new double[] { site.location.lon(), site.location.lat() };
			jObj.add(Key.LOCATION.toString(), context.serialize(coords));
			jObj.addProperty(Key.VS30.toString(), site.vs30);
			jObj.addProperty(Key.VS_INF.toString(), site.vsInferred);
			if (!Double.isNaN(site.z1p0)) jObj.addProperty(Key.Z1P0.toString(), site.z1p0);
			if (!Double.isNaN(site.z2p5)) jObj.addProperty(Key.Z2P5.toString(), site.z2p5);

			return jObj;
		}
	}

}
