package org.opensha.calc;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.opensha.data.DataUtils.validate;

import java.lang.reflect.Type;

import org.opensha.geo.Location;
import org.opensha.gmm.GroundMotionModel;
import org.opensha.util.Named;

import com.google.common.collect.Range;
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
	public static final double MIN_VS30 = 150.0;
	/** Maximum allowed Vs30 value (2000 m/sec). */
	public static final double MAX_VS30 = 2000.0;

	/** Minimum allowed depth to a shear wave velocity of 2.5 km/sec (0.0 km). */
	public static final double MIN_Z2P5 = 0.0;
	/** Minimum allowed depth to a shear wave velocity of 2.5 km/sec (5.0 km). */
	public static final double MAX_Z2P5 = 5.0;

	/** Minimum allowed depth to a shear wave velocity of 1.0 km/sec (0.0 km). */
	public static final double MIN_Z1P0 = 0.0;
	/** Minimum allowed depth to a shear wave velocity of 1.0 km/sec (2.0 km). */
	public static final double MAX_Z1P0 = 2.0;

	private static final Range<Double> VS30_RANGE = Range.closed(MIN_VS30, MAX_VS30);
	private static final Range<Double> Z2P5_RANGE = Range.closed(MIN_Z2P5, MAX_Z2P5);
	private static final Range<Double> Z1P0_RANGE = Range.closed(MIN_Z1P0, MAX_Z1P0);

	// field names - mostly used from JSON serialization
	private static final String NAME = "name";
	private static final String LOC = "location";
	private static final String VS_30 = "vs30";
	private static final String VS_INF = "vsInferred";
	private static final String Z1P0 = "z1p0";
	private static final String Z2P5 = "z2p5";
	private static final String LAT = "lat";
	private static final String LON = "lon";

	/** The site name. */
	public final String name;

	/**
	 * The location.
	 * 
	 * <p>Default: lat=40.75, lon=-111.90 (Salt Lake City, UT)</p>
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

		private String name = "Unnamed Site";
		private Location location = NehrpTestCity.SALT_LAKE_CITY.location();
		private double vs30 = 760.0;
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
			this.vs30 = validate(VS30_RANGE, VS_30, vs30);
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
			this.z1p0 = validate(Z1P0_RANGE, Z1P0, z1p0);
			return this;
		}

		/** Depth to the shear-wave velocity horizon of 2.5 km/sec, in km. */
		public Builder z2p5(double z2p5) {
			this.z2p5 = validate(Z2P5_RANGE, Z2P5, z2p5);
			return this;
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
			Builder siteBuilder = Site.builder();

			if (jObj.has(NAME)) siteBuilder.name(jObj.get(NAME).getAsString());
			if (jObj.has(LOC)) {
				JsonObject locObj = jObj.getAsJsonObject(LOC);
				siteBuilder.location(Location.create(locObj.get(LAT).getAsDouble(), locObj.get(LON)
					.getAsDouble()));
			}
			if (jObj.has(VS_30)) siteBuilder.vs30(jObj.get(VS_30).getAsDouble());
			if (jObj.has(VS_INF)) siteBuilder.vsInferred(jObj.get(VS_INF).getAsBoolean());
			if (jObj.has(Z1P0)) siteBuilder.z1p0(jObj.get(Z1P0).getAsDouble());
			if (jObj.has(Z2P5)) siteBuilder.z2p5(jObj.get(Z2P5).getAsDouble());

			return siteBuilder.build();
		}

	}

	static class Serializer implements JsonSerializer<Site> {

		@Override public JsonElement serialize(Site site, Type typeOfSrc,
				JsonSerializationContext context) {

			JsonObject jObj = new JsonObject();
			jObj.addProperty(NAME, site.name);

			JsonObject locObj = new JsonObject();
			locObj.addProperty(LAT, site.location.lat());
			locObj.addProperty(LON, site.location.lon());
			jObj.add(LOC, locObj);

			jObj.addProperty(VS_30, site.vs30);
			jObj.addProperty(VS_INF, site.vsInferred);
			if (!Double.isNaN(site.z1p0)) jObj.addProperty(Z1P0, site.z1p0);
			if (!Double.isNaN(site.z2p5)) jObj.addProperty(Z2P5, site.z2p5);

			return jObj;
		}

	}

}
