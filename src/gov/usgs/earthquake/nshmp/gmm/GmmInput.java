package gov.usgs.earthquake.nshmp.gmm;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static gov.usgs.earthquake.nshmp.gmm.GmmInput.Field.DIP;
import static gov.usgs.earthquake.nshmp.gmm.GmmInput.Field.MW;
import static gov.usgs.earthquake.nshmp.gmm.GmmInput.Field.RAKE;
import static gov.usgs.earthquake.nshmp.gmm.GmmInput.Field.RJB;
import static gov.usgs.earthquake.nshmp.gmm.GmmInput.Field.RRUP;
import static gov.usgs.earthquake.nshmp.gmm.GmmInput.Field.RX;
import static gov.usgs.earthquake.nshmp.gmm.GmmInput.Field.VS30;
import static gov.usgs.earthquake.nshmp.gmm.GmmInput.Field.VSINF;
import static gov.usgs.earthquake.nshmp.gmm.GmmInput.Field.WIDTH;
import static gov.usgs.earthquake.nshmp.gmm.GmmInput.Field.Z1P0;
import static gov.usgs.earthquake.nshmp.gmm.GmmInput.Field.Z2P5;
import static gov.usgs.earthquake.nshmp.gmm.GmmInput.Field.ZHYP;
import static gov.usgs.earthquake.nshmp.gmm.GmmInput.Field.ZTOP;
import static gov.usgs.earthquake.nshmp.internal.TextUtils.NEWLINE;
import static java.lang.Double.NaN;

import java.util.BitSet;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.common.collect.Range;

import gov.usgs.earthquake.nshmp.calc.Site;
import gov.usgs.earthquake.nshmp.eq.Earthquakes;
import gov.usgs.earthquake.nshmp.eq.fault.Faults;
import gov.usgs.earthquake.nshmp.eq.model.Distance;
import gov.usgs.earthquake.nshmp.eq.model.Rupture;

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
  /** Whether {@code vs30} is inferred or measured. */
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
   * components of a {@code GmmInput}.
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

  /**
   * Return a {@code GmmInput} builder that requires all fields to be
   * explicitely set. This builder is stateful and may be reused (by a single
   * thread) preserving previously set fields. However, repeat calls of builder
   * methods are not permitted until {@code build()} has been called.
   *
   * @see GmmInput.Builder
   */
  public static Builder builder() {
    return new Builder();
  }

  @SuppressWarnings("javadoc")
  public static class Builder {

    static final int SIZE = Field.values().length;
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
     * Return a {@code Builder} prepopulated with values copied from the
     * supplied model.
     * 
     * @param model to copy
     * @throws IllegalStateException if any other builder method has already
     *         been called without first calling {@link #build()}
     */
    public Builder fromCopy(GmmInput model) {
      return copy(model);
    }

    /**
     * Return a {@code Builder} prepopulated with default values. Builder has
     * the following presets:
     *
     * <ul><li>Mw: 6.5</li>
     *
     * <li>rJB: 10.0 (km)</li>
     *
     * <li>rRup: 10.3 (km)</li>
     *
     * <li>rX: 10.0 (km)</li>
     *
     * <li>dip: 90˚</li>
     *
     * <li>width: 14.0 (km)</li>
     *
     * <li>zTop: 0.5 (km)</li>
     *
     * <li>zHyp: 7.5 (km)</li>
     *
     * <li>rake: 0˚</li>
     *
     * <li>vs30: 760 (m/s)</li>
     *
     * <li>vsInf: true</li>
     *
     * <li>z2p5: NaN</li>
     *
     * <li>z1p0: NaN</li></ul>
     * 
     * @throws IllegalStateException if any other builder method has already
     *         been called without first calling {@link #build()}
     */
    public Builder withDefaults() {
      return copy(DEFAULT);
    }

    private Builder copy(GmmInput model) {
      checkState(reset.isEmpty(), "Some fields are already set");
      Mw = model.Mw;
      rJB = model.rJB;
      rRup = model.rRup;
      rX = model.rX;
      dip = model.dip;
      width = model.width;
      zTop = model.zTop;
      zHyp = model.zHyp;
      rake = model.rake;
      vs30 = model.vs30;
      vsInf = model.vsInf;
      z1p0 = model.z1p0;
      z2p5 = model.z2p5;
      flags.set(0, SIZE);
      return this;
    }

    /**
     * Set a field in this builder. String values are converted to the
     * appropriate class required by the field.
     * 
     * @param id of the field to set
     * @param s string value that will be converted to appropriate class
     */
    @SuppressWarnings("incomplete-switch")
    public Builder set(Field id, String s) {
      try {
        double v = Double.valueOf(s);
        switch (id) {
          case MW:
            return mag(v);
          case RJB:
            return rJB(v);
          case RRUP:
            return rRup(v);
          case RX:
            return rX(v);
          case DIP:
            return dip(v);
          case WIDTH:
            return width(v);
          case ZTOP:
            return zTop(v);
          case ZHYP:
            return zHyp(v);
          case RAKE:
            return rake(v);
          case VS30:
            return vs30(v);
          case Z2P5:
            return z2p5(v);
          case Z1P0:
            return z1p0(v);
        }
      } catch (NumberFormatException nfe) {
        // move along
      }
      if (id == VSINF) {
        return vsInf(Boolean.valueOf(s));
      }
      throw new IllegalStateException("Unhandled field: " + id);
    }

    /** Set the moment magnitude. */
    public Builder mag(double Mw) {
      this.Mw = validateAndFlag(MW, Mw);
      return this;
    }

    /**
     * Set the Joyner-Boore distance (distance to surface projection of
     * rupture).
     */
    public Builder rJB(double rJB) {
      this.rJB = validateAndFlag(RJB, rJB);
      return this;
    }

    /** Set the rupture distance (distance to rupture plane). */
    public Builder rRup(double rRup) {
      this.rRup = validateAndFlag(RRUP, rRup);
      return this;
    }

    /** Set the distance X (shortest distance to extended strike of rupture). */
    public Builder rX(double rX) {
      this.rX = validateAndFlag(RX, rX);
      return this;
    }

    /** Set the Joyner-Boore distance, rupture distance, and distance X. */
    public Builder distances(double rJB, double rRup, double rX) {
      return rJB(rJB).rRup(rRup).rX(rX);
    }

    /**
     * Set the Joyner-Boore distance, rupture distance, and distance X with a
     * {@link Distance} object.
     */
    public Builder distances(Distance distances) {
      return distances(distances.rJB, distances.rRup, distances.rX);
    }

    /** Set the rupture dip. */
    public Builder dip(double dip) {
      this.dip = validateAndFlag(DIP, dip);
      return this;
    }

    /** Set the rupture width. */
    public Builder width(double width) {
      this.width = validateAndFlag(WIDTH, width);
      return this;
    }

    /** Set the depth to top of rupture. */
    public Builder zTop(double zTop) {
      this.zTop = validateAndFlag(ZTOP, zTop);
      return this;
    }

    /** Set the depth to rupture hypocenter. */
    public Builder zHyp(double zHyp) {
      this.zHyp = validateAndFlag(ZHYP, zHyp);
      return this;
    }

    /** Set the rupture rake. */
    public Builder rake(double rake) {
      this.rake = validateAndFlag(RAKE, rake);
      return this;
    }

    /** Set the vs30 at site. */
    public Builder vs30(double vs30) {
      this.vs30 = validateAndFlag(VS30, vs30);
      return this;
    }

    /** Set whether {@code vs30} is inferred or measured. */
    public Builder vsInf(boolean vsInf) {
      this.vsInf = validateAndFlag(VSINF, vsInf);
      return this;
    }

    /**
     * Set both the vs30 at site and whether {@code vs30} is inferred or
     * measured.
     */
    public Builder vs30(double vs30, boolean vsInf) {
      return vs30(vs30).vsInf(vsInf);
    }

    /** Set the depth to 1.0 km/s (in km). */
    public Builder z1p0(double z1p0) {
      this.z1p0 = validateAndFlag(Z1P0, z1p0);
      return this;
    }

    /** Set the depth to 2.5 km/s (in km). */
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

    /* returns the double value of interest for inlining */
    private final double validateAndFlag(Field field, double value) {
      int index = field.ordinal();
      checkState(!reset.get(index), "Field %s already set", field);
      flags.set(index);
      reset.set(index);
      return value;
    }

    /* returns the boolean value of interest for inlining */
    private final boolean validateAndFlag(Field field, boolean value) {
      int index = field.ordinal();
      checkState(!reset.get(index), "Field %s already set", field);
      flags.set(index);
      reset.set(index);
      return value;
    }
  }

  private static final String DISTANCE_UNIT = "km";
  private static final String VELOCITY_UNIT = "m/s";
  private static final String ANGLE_UNIT = "°";

  private static final GmmInput DEFAULT = new GmmInput(
      MW.defaultValue,
      RJB.defaultValue,
      RRUP.defaultValue,
      RX.defaultValue,
      DIP.defaultValue,
      WIDTH.defaultValue,
      ZTOP.defaultValue,
      ZHYP.defaultValue,
      RAKE.defaultValue,
      VS30.defaultValue,
      VSINF.defaultValue > 0.0,
      Z1P0.defaultValue,
      Z2P5.defaultValue);

  /**
   * {@code GmmInput} field identifiers. These are used internally to manage
   * builder flags and provides access to the case-sensitive keys (via
   * toString()) used when building http queries
   *
   * Lets use negative positive for vsInf for now
   */
  @SuppressWarnings("javadoc")
  public enum Field {

    MW(
        "Mw",
        "Magnitude",
        "The moment magnitude of an earthquake",
        Optional.<String> empty(),
        6.5),

    RJB(
        "rJB",
        "Joyner-Boore Distance",
        "The shortest distance from a site to the surface projection of a rupture, in kilometers",
        Optional.of(DISTANCE_UNIT),
        10.0),

    RRUP(
        "rRup",
        "Rupture Distance",
        "The shortest distance from a site to a rupture, in kilometers",
        Optional.of(DISTANCE_UNIT),
        10.3),

    RX(
        "rX",
        "Distance X",
        "The shortest distance from a site to the extended trace a fault, in kilometers",
        Optional.of(DISTANCE_UNIT),
        10.0),

    DIP(
        "dip",
        "Dip",
        "The dip of a rupture surface, in degrees",
        Optional.of(ANGLE_UNIT),
        90.0),

    WIDTH(
        "width",
        "Width",
        "The width of a rupture surface, in kilometers",
        Optional.of(DISTANCE_UNIT),
        14.0),

    ZTOP(
        "zTop",
        "Depth",
        "The depth to the top of a rupture surface, in kilometers and positive-down",
        Optional.of(DISTANCE_UNIT),
        0.5),

    ZHYP(
        "zHyp",
        "Hypocentral Depth",
        "The depth to the hypocenter on a rupture surface, in kilometers and positive-down",
        Optional.of(DISTANCE_UNIT),
        7.5),

    RAKE(
        "rake",
        "Rake",
        "The rake (or sense of slip) of a rupture surface, in degrees",
        Optional.of(ANGLE_UNIT),
        0.0),

    VS30(
        "vs30",
        "Vs30",
        "The average shear-wave velocity down to 30 meters, in kilometers per second",
        Optional.of(VELOCITY_UNIT),
        760.0),

    VSINF(
        "vsInf",
        "Vs30 Inferred",
        "Whether Vs30 was measured or inferred",
        Optional.<String> empty(),
        1.0),

    Z1P0(
        "z1p0",
        "Depth to Vs=1.0 km/s",
        "Depth to a shear-wave velocity of 1.0 kilometers per second, in kilometers",
        Optional.of(DISTANCE_UNIT),
        NaN),

    Z2P5(
        "z2p5",
        "Depth to Vs=2.5 km/s",
        "Depth to a shear-wave velocity of 2.5 kilometers per second, in kilometers",
        Optional.of(DISTANCE_UNIT),
        NaN);

    public final String id;
    public final String label;
    public final String info;
    public final Optional<String> units;
    public final double defaultValue;

    private Field(
        String id,
        String label,
        String info,
        Optional<String> units,
        double defaultValue) {

      this.id = id;
      this.label = label;
      this.info = info;
      this.units = units;
      this.defaultValue = defaultValue;
    }

    @Override
    public String toString() {
      return id;
    }

    public static Field fromString(String s) {
      return valueOf(s.toUpperCase());
    }
  }

  /**
   * Some values [mag, rJB, rRup, rX, zHyp] may be truncated to 2 or 3 decimal
   * places for output.
   */
  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add(MW.toString(), String.format("%.2f", Mw))
        .add(RJB.toString(), String.format("%.3f", rJB))
        .add(RRUP.toString(), String.format("%.3f", rRup))
        .add(RX.toString(), String.format("%.3f", rX))
        .add(DIP.toString(), dip)
        .add(WIDTH.toString(), String.format("%.3f", width))
        .add(ZTOP.toString(), zTop)
        .add(ZHYP.toString(), String.format("%.3f", zHyp))
        .add(RAKE.toString(), rake)
        .add(VS30.toString(), vs30)
        .add(VSINF.toString(), vsInf)
        .add(Z1P0.toString(), z1p0)
        .add(Z2P5.toString(), z2p5)
        .toString();
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (!(obj instanceof GmmInput)) {
      return false;
    }
    GmmInput gmm = (GmmInput) obj;
    Boolean z1p0Check = Double.isNaN(gmm.z1p0) ? Double.isNaN(this.z1p0) : this.z1p0 == gmm.z1p0;
    Boolean z2p5Check = Double.isNaN(gmm.z2p5) ? Double.isNaN(this.z2p5) : this.z2p5 == gmm.z2p5;
    return this.Mw == gmm.Mw &&
        this.rJB == gmm.rJB &&
        this.rRup == gmm.rRup &&
        this.rX == gmm.rX &&
        this.dip == gmm.dip &&
        this.width == gmm.width &&
        this.zTop == gmm.zTop &&
        this.zHyp == gmm.zHyp &&
        this.rake == gmm.rake &&
        this.vs30 == gmm.vs30 &&
        this.vsInf == gmm.vsInf &&
        z1p0Check &&
        z2p5Check;
  }

  @Override
  public int hashCode() {
    return Objects.hash(Mw, rJB, rRup, rX, dip, width, zTop, zHyp,
        rake, vs30, vsInf, z1p0, z2p5);
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

    // for internal use only
    private Map<Field, Optional<?>> constraintMap;

    private Optional<Range<Double>> mag;

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
      constraintMap.put(MW, mag);
      constraintMap.put(RJB, rJB);
      constraintMap.put(RRUP, rRup);
      constraintMap.put(RX, rX);
      constraintMap.put(DIP, dip);
      constraintMap.put(WIDTH, width);
      constraintMap.put(ZTOP, zTop);
      constraintMap.put(ZHYP, zHyp);
      constraintMap.put(RAKE, rake);
      constraintMap.put(VS30, vs30);
      constraintMap.put(VSINF, vsInf);
      constraintMap.put(Z1P0, z1p0);
      constraintMap.put(Z2P5, z2p5);
    }

    public Optional<?> get(Field field) {
      return constraintMap.get(field);
    }

    /**
     * Return a builder of {@code GmmInput} constraints. This builder sets all
     * fields to {@code Optional.absent()}.
     */
    static Builder builder() {
      return new Builder();
    }

    /**
     * The default allowed values for each input field.
     */
    public static Constraints defaults() {
      return builder().withDefaults().build();
    }

    @Override
    public String toString() {
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

      private Optional<Range<Double>> mag = Optional.empty();
      private Optional<Range<Double>> rJB = Optional.empty();
      private Optional<Range<Double>> rRup = Optional.empty();
      private Optional<Range<Double>> rX = Optional.empty();
      private Optional<Range<Double>> dip = Optional.empty();
      private Optional<Range<Double>> width = Optional.empty();
      private Optional<Range<Double>> zTop = Optional.empty();
      private Optional<Range<Double>> zHyp = Optional.empty();
      private Optional<Range<Double>> rake = Optional.empty();
      private Optional<Range<Double>> vs30 = Optional.empty();
      private Optional<Range<Boolean>> vsInf = Optional.empty();
      private Optional<Range<Double>> z1p0 = Optional.empty();
      private Optional<Range<Double>> z2p5 = Optional.empty();

      /**
       * Set {@code Range<Double>} constraint.
       */
      Builder set(Field id, Range<Double> constraint) {
        checkArgument(EnumSet.complementOf(EnumSet.of(Field.VSINF)).contains(id));
        switch (id) {
          case MW:
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
                "GmmInput.Constraints.Builder Unsupported field: " + id.name());
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
        set(RJB, Range.closed(0.0, r));
        set(RRUP, Range.closed(0.0, r));
        set(RX, Range.closed(0.0, r));
        return this;
      }

      Builder withDefaults() {

        /*
         * TODO this should really be executed by polling all implemented GMMs
         * and unioning results; are also going to need an intersection method
         * to support returning the constraints supported by subsets of gmms
         */

        set(MW, Earthquakes.MAG_RANGE);
        set(RJB, Range.closed(0.0, Distance.MAX));
        set(RRUP, Range.closed(0.0, Distance.MAX));
        set(RX, Range.closed(0.0, Distance.MAX));

        Range<Double> depthRange = Earthquakes.INTERFACE_DEPTH_RANGE
            .span(Earthquakes.SLAB_DEPTH_RANGE);
        set(ZTOP, depthRange);
        set(ZHYP, depthRange);

        set(DIP, Faults.DIP_RANGE);

        Range<Double> widthRange = Earthquakes.CRUSTAL_WIDTH_RANGE
            .intersection(Earthquakes.INTERFACE_WIDTH_RANGE);
        set(WIDTH, widthRange);

        set(RAKE, Faults.RAKE_RANGE);

        set(VS30, Site.VS30_RANGE);
        set(VSINF);
        set(Z1P0, Site.Z1P0_RANGE);
        set(Z2P5, Site.Z2P5_RANGE);

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
