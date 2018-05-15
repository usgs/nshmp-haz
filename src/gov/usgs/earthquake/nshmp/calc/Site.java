package gov.usgs.earthquake.nshmp.calc;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static gov.usgs.earthquake.nshmp.data.Data.checkInRange;
import static gov.usgs.earthquake.nshmp.internal.GeoJson.validateProperty;

import java.lang.reflect.Type;
import java.util.Set;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Range;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import gov.usgs.earthquake.nshmp.geo.Location;
import gov.usgs.earthquake.nshmp.gmm.GroundMotionModel;
import gov.usgs.earthquake.nshmp.internal.GeoJson;
import gov.usgs.earthquake.nshmp.util.Named;
import gov.usgs.earthquake.nshmp.util.NamedLocation;

/**
 * Site characteristics container. Take note of default values; the minimum
 * information required to create a {@code Site} is its location. Not all
 * {@link GroundMotionModel}s will use all fields and additional fields may be
 * added at any time in the future.
 * 
 * <p>Terminology:<ul>
 * 
 * <li><b>{@code Vs30}:</b> Average shear wave velocity down to a depth
 * of 30 m, in units of m/s. This value may be <i>inferred</i> or
 * <i>measured</i> (see {@link #vsInferred}).</li>
 * 
 * <li><b>{@code z1.0}:</b> Depth to a shear wave velocity of 1.0 km/sec, in
 * units of km.</li>
 * 
 * <li><b>{@code z2.5}:</b> Depth to a shear wave velocity of 2.5 km/sec, in
 * units of km.</li>
 * 
 * </ul>
 * 
 * <p>Both {@code z1.0} and {@code z2.5}, collectively referred to as
 * <i>basin-terms</i>, have default values of {@code Double.NaN}. When supplied
 * with the default, those {@link GroundMotionModel}s that support basin terms
 * will use an author defined model, typically based on {@code Vs30}, to compute
 * basin-amplifications.
 * 
 * <p><b>Note:</b> If a {@link CalcConfig.SiteData#basinDataProvider} has been set,
 * any non-{@code null} or non-{@code NaN} {@code z1p0} or {@code z2p5} values 
 * supplied by the provider take precedence over defaults or recent calls to the builder.
 * 
 *
 * @author Peter Powers
 */
public class Site implements Named {

  /** The name used for a {@code Site} with no supplied name. */
  public static final String NO_NAME = "Unnamed";

  /** Default {@link #vs30} value: {@code 760 m/s}. */
  public static final double VS_30_DEFAULT = 760.0;

  /** Supported {@link #vs30} values: {@code [150..2000] m/s}. */
  public static final Range<Double> VS30_RANGE = Range.closed(150.0, 3000.0);

  /** Default {@link #vsInferred} inferred value: {@code true}. */
  public static final boolean VS_INF_DEFAULT = true;

  /**
   * Default {@link #z1p0} value: {@code NaN} <br>({@link GroundMotionModel}s
   * will use a default value or model)
   */
  public static final double Z1P0_DEFAULT = Double.NaN;

  /** Supported {@link #z1p0} values: {@code [0..5] km}. */
  public static final Range<Double> Z1P0_RANGE = Range.closed(0.0, 5.0);

  /**
   * Default {@link #z2p5} value: {@code NaN} <br>({@link GroundMotionModel}s
   * will use a default value or model)
   */
  public static final double Z2P5_DEFAULT = Double.NaN;

  /** Supported {@link #z2p5} values: {@code [0..10] km}. */
  public static final Range<Double> Z2P5_RANGE = Range.closed(0.0, 10.0);

  /** The site name. */
  public final String name;

  /**
   * The location of this site. This is the only field that must be explicitely
   * set in a {@link Builder}.
   */
  public final Location location;

  /**
   * The average shear-wave velocity down to 30 meters depth.
   *
   * <p>Default: {@code 760.0 m/s}
   */
  public final double vs30;

  /**
   * Whether Vs30 was inferred, {@code true}, or measured, {@code false}.
   *
   * <p>Default: {@code true} (inferred)
   */
  public final boolean vsInferred;

  /**
   * Depth to the shear-wave velocity horizon of 1.0 km/s, in km.
   *
   * <p>Default: {@code NaN} <br>({@link GroundMotionModel}s will use a default
   * value or model)
   */
  public final double z1p0;

  /**
   * Depth to the shear-wave velocity horizon of 2.5 km/s, in km.
   *
   * <p>Default: {@code NaN} <br>({@link GroundMotionModel}s will use a default
   * value or model)
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
    return new StringBuilder(Strings.padEnd(name, 28, ' '))
        .append(String.format("%.3f %.3f ", location.lon(), location.lat()))
        .append(paramString(vs30, vsInferred, z1p0, z2p5))
        .toString();
  }

  private static String paramString(double vs30, boolean vsInf, double z1p0, double z2p5) {
    return String.format(
        "Vs30=%s %s Z1.0=%s Z2.5=%s",
        vs30, vsInf ? "inferred " : "measured ", z1p0, z2p5);
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
   * Return a fresh {@link Builder} configured with the supplied defaults.
   */
  static Builder builder(CalcConfig defaults) {
    return new Builder(defaults);
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
    private double vs30 = VS_30_DEFAULT;
    private boolean vsInferred = VS_INF_DEFAULT;
    private double z1p0 = Z1P0_DEFAULT;
    private double z2p5 = Z2P5_DEFAULT;

    private Builder() {}

    private Builder(CalcConfig config) {
      vs30(config.site.vs30);
      vsInferred(config.site.vsInferred);
      z1p0(config.site.z1p0);
      z2p5(config.site.z2p5);
    }

    /**
     * The name of the {@code Site}. Prior to setting, the supplied name is
     * stripped of commas and truncated at 72 characters.
     */
    public Builder name(String name) {
      this.name = cleanName(checkNotNull(name));
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

    /** Depth to the shear-wave velocity horizon of 1.0 km/s, in km. */
    public Builder z1p0(double z1p0) {
      if (Double.isNaN(z1p0)) {
        this.z1p0 = z1p0;
      } else {
        this.z1p0 = checkInRange(Z1P0_RANGE, Site.Key.Z1P0, z1p0);
      }
      return this;
    }

    /** Depth to the shear-wave velocity horizon of 2.5 km/s, in km. */
    public Builder z2p5(double z2p5) {
      if (Double.isNaN(z2p5)) {
        this.z2p5 = z2p5;
      } else {
        this.z2p5 = checkInRange(Z2P5_RANGE, Site.Key.Z2P5, z2p5);
      }
      return this;
    }

    /**
     * Return a string reflecting the current site parameter state of this
     * builder.
     */
    String state() {
      return paramString(vs30, vsInferred, z1p0, z2p5);
    }

    /**
     * Build the {@code Site}.
     */
    public Site build() {
      checkState(location != null, "Site location not set");
      return new Site(name, location, vs30, vsInferred, z1p0, z2p5);
    }

  }

  private static final int MAX_NAME_LENGTH = 72;

  private static String cleanName(String name) {
    name = name.replaceAll(",", "");
    return name.length() > MAX_NAME_LENGTH ? name.substring(0, MAX_NAME_LENGTH) : name;
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
   * Specifically, JSON prohibits the use of NaN, which is the default value for
   * z1p0 and z2p5, and so these two fields may not be set. Users have been
   * notified that as long as no z1p0 or z2p5 value has been supplied in any
   * JSON, the default will be used.
   */

  static final class Deserializer implements JsonDeserializer<Site> {

    final CalcConfig defaults;

    Deserializer(CalcConfig defaults) {
      this.defaults = defaults;
    }

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

      Builder builder = Site.builder(defaults).location(
          coordinates.get(1).getAsDouble(),
          coordinates.get(0).getAsDouble());

      JsonObject properties = feature.getAsJsonObject(GeoJson.Key.PROPERTIES);

      JsonElement name = properties.get(GeoJson.Properties.Key.TITLE);
      if (name != null) {
        builder.name(name.getAsString());
      }

      JsonElement vs30 = properties.get(Site.Key.VS30);
      if (vs30 != null) {
        builder.vs30(vs30.getAsDouble());
      }

      JsonElement vsInf = properties.get(Site.Key.VS_INF);
      if (vsInf != null) {
        builder.vsInferred(vs30.getAsBoolean());
      }

      JsonElement z1p0 = properties.get(Site.Key.Z1P0);
      if (z1p0 != null) {
        builder.z1p0(z1p0.getAsDouble());
      }

      JsonElement z2p5 = properties.get(Site.Key.Z2P5);
      if (z2p5 != null) {
        builder.z2p5(z2p5.getAsDouble());
      }

      return builder.build();
    }
  }

}
