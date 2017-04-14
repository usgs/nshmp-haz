package org.opensha2.calc;

import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_UNDERSCORE;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Strings.padEnd;
import static com.google.common.base.Strings.repeat;
import static java.nio.charset.StandardCharsets.UTF_8;

import static org.opensha2.data.XySequence.create;
import static org.opensha2.data.XySequence.immutableCopyOf;
import static org.opensha2.internal.Parsing.enumsToString;
import static org.opensha2.internal.TextUtils.LOG_INDENT;
import static org.opensha2.internal.TextUtils.LOG_VALUE_COLUMN;
import static org.opensha2.internal.TextUtils.NEWLINE;

import org.opensha2.data.Data;
import org.opensha2.data.XySequence;
import org.opensha2.eq.model.SourceType;
import org.opensha2.gmm.Gmm;
import org.opensha2.gmm.GroundMotionModel;
import org.opensha2.gmm.Imt;

import com.google.common.base.Optional;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Calculation configuration.
 *
 * @author Peter Powers
 */
public final class CalcConfig {

  static final String FILE_NAME = "config.json";
  private static final String ID = CalcConfig.class.getSimpleName();
  private static final String STATE_ERROR = "%s %s not set";
  static final String DEFAULT_OUT = "curves";

  /**
   * The resource from which {@code this} was derived. This field may be empty.
   */
  public final transient Optional<Path> resource;

  /** Hazard calculation configuration. */
  public final Hazard hazard;

  /** Default site settings. */
  public final SiteDefaults site;

  /** Performance and optimization configuration. */
  public final Performance performance;

  /** Output configuration. */
  public final Output output;

  /** Deaggregation configuration. */
  public final Deagg deagg;

  /** Earthquake rate configuration. */
  public final Rate rate;

  private CalcConfig(
      Optional<Path> resource,
      Hazard hazard,
      SiteDefaults site,
      Performance performance,
      Output output,
      Deagg deagg,
      Rate rate) {

    this.resource = resource;
    this.hazard = hazard;
    this.site = site;
    this.performance = performance;
    this.output = output;
    this.deagg = deagg;
    this.rate = rate;
  }

  /**
   * Hazard calculation configuration.
   */
  public final static class Hazard {

    static final String ID = CalcConfig.ID + "." + Hazard.class.getSimpleName();

    /**
     * The probability distribution model to use when computing hazard curves.
     *
     * <p><b>Default:</b> {@link ExceedanceModel#TRUNCATION_UPPER_ONLY}
     */
    public final ExceedanceModel exceedanceModel;

    /**
     * The number of standard deviations (σ) at which to truncate a
     * distribution. This field is ignored if an {@link ExceedanceModel} does
     * not implement truncation.
     *
     * <p><b>Default:</b> {@code 3.0}
     */
    public final double truncationLevel;

    /**
     * The {@code Set} of IMTs for which calculations should be performed.
     *
     * <p><b>Default:</b> [{@link Imt#PGA}, {@link Imt#SA0P2}, {@link Imt#SA1P0}
     * ]
     */
    public final Set<Imt> imts;

    /**
     * Whether to consider additional ground motion model uncertainty, or not.
     * Currently this is only applicable when using the PEER NGA-West or
     * NGA-West2 {@link Gmm}s with USGS hazard models.
     *
     * <p><b>Default:</b> {@code false}
     */
    public final boolean gmmUncertainty;

    /**
     * The value format for hazard curves.
     *
     * <p><b>Default:</b> {@link ValueFormat#ANNUAL_RATE}
     */
    public final ValueFormat valueFormat;

    private final double[] defaultImls;
    private final Map<Imt, double[]> customImls;

    /* Do not serialize to JSON */
    private final transient Map<Imt, XySequence> modelCurves;
    private final transient Map<Imt, XySequence> logModelCurves;

    private Hazard(
        ExceedanceModel exceedanceModel,
        double truncationLevel,
        Set<Imt> imts,
        boolean gmmUncertainty,
        ValueFormat valueFormat,
        double[] defaultImls,
        Map<Imt, double[]> customImls,
        Map<Imt, XySequence> modelCurves,
        Map<Imt, XySequence> logModelCurves) {

      this.exceedanceModel = exceedanceModel;
      this.truncationLevel = truncationLevel;
      this.imts = imts;
      this.gmmUncertainty = gmmUncertainty;
      this.valueFormat = valueFormat;

      this.defaultImls = defaultImls;
      this.customImls = customImls;
      this.modelCurves = modelCurves;
      this.logModelCurves = logModelCurves;
    }

    /**
     * An empty linear curve for the requested {@code Imt}.
     * @param imt to get curve for
     * @see #modelCurves() defaults
     */
    public XySequence modelCurve(Imt imt) {
      return modelCurves.get(imt);
    }

    /**
     * An immutable map of model curves where x-values are in linear space. In
     * the absence of specific values being provided in a config file, the
     * following defaults are used:
     * 
     * <p><b>{@code PGA}</b> and <b>{@code SA}</b>: {@code [0.0025,
     * 0.0045, 0.0075, 0.0113, 0.0169, 0.0253, 0.0380, 0.0570, 0.0854, 0.128,
     * 0.192, 0.288, 0.432, 0.649, 0.973, 1.46, 2.19, 3.28, 4.92, 7.38]}
     * 
     * <p><b>{@code PGV}</b>: {@code [0.0100, 0.0177, 0.0312, 0.0552, 
     * 0.0976, 0.173, 0.305, 0.539, 0.953, 1.68, 2.98, 5.26, 9.30, 16.4, 
     * 29.1, 51.3, 90.8, 160, 284, 501]}
     */
    public Map<Imt, XySequence> modelCurves() {
      return modelCurves;
    }

    /**
     * An immutable map of model curves where x-values are in natural-log space.
     * @see #modelCurves() linear space defaults
     */
    public Map<Imt, XySequence> logModelCurves() {
      return logModelCurves;
    }

    private StringBuilder asString() {
      StringBuilder imlSb = new StringBuilder();
      if (!customImls.isEmpty()) {
        for (Entry<Imt, double[]> entry : customImls.entrySet()) {
          String imtStr = "imls (" + entry.getKey().name() + ")";
          imlSb.append(formatEntry(imtStr))
              .append(wrap(Arrays.toString(entry.getValue()), false));
        }
      }
      return new StringBuilder()
          .append(LOG_INDENT).append("Hazard")
          .append(formatEntry(Key.EXCEEDANCE_MODEL, exceedanceModel.name()))
          .append(formatEntry(Key.TRUNCATION_LEVEL, truncationLevel))
          .append(formatEntry(Key.IMTS, enumsToString(imts, Imt.class)))
          .append(formatEntry(Key.GMM_UNCERTAINTY, gmmUncertainty))
          .append(formatEntry(Key.VALUE_FORMAT, valueFormat.name()))
          .append(formatEntry(Key.DEFAULT_IMLS, wrap(Arrays.toString(defaultImls), false)))
          .append(imlSb);
    }

    private static final class Builder {

      ExceedanceModel exceedanceModel;
      Double truncationLevel;
      Set<Imt> imts;
      Boolean gmmUncertainty;
      ValueFormat valueFormat;
      double[] defaultImls;
      Map<Imt, double[]> customImls;

      Hazard build() {
        return new Hazard(
            exceedanceModel,
            truncationLevel,
            Sets.immutableEnumSet(imts),
            gmmUncertainty,
            valueFormat,
            defaultImls,
            customImls,
            createCurveMap(),
            createLogCurveMap());
      }

      void copy(Hazard that) {
        this.exceedanceModel = that.exceedanceModel;
        this.truncationLevel = that.truncationLevel;
        this.imts = that.imts;
        this.gmmUncertainty = that.gmmUncertainty;
        this.valueFormat = that.valueFormat;
        this.defaultImls = that.defaultImls;
        this.customImls = that.customImls;
      }

      void extend(Builder that) {
        if (that.exceedanceModel != null) {
          this.exceedanceModel = that.exceedanceModel;
        }
        if (that.truncationLevel != null) {
          this.truncationLevel = that.truncationLevel;
        }
        if (that.imts != null) {
          this.imts = that.imts;
        }
        if (that.gmmUncertainty != null) {
          this.gmmUncertainty = that.gmmUncertainty;
        }
        if (that.valueFormat != null) {
          this.valueFormat = that.valueFormat;
        }
        if (that.defaultImls != null) {
          this.defaultImls = that.defaultImls;
        }
        if (that.customImls != null) {
          this.customImls.putAll(that.customImls);
        }
      }

      /* Slightly modified version of NSHM 5Hz curve, size = 20 */
      private static final double[] IMLS_PGA_SA = new double[] { 0.0025, 0.0045, 0.0075, 0.0113,
          0.0169, 0.0253, 0.0380, 0.0570, 0.0854, 0.128, 0.192, 0.288, 0.432, 0.649, 0.973, 1.46,
          2.19, 3.28, 4.92, 7.38 };

      private static final double[] IMLS_PGV = new double[] { 0.0100, 0.0177, 0.0312, 0.0552,
          0.0976, 0.173, 0.305, 0.539, 0.953, 1.68, 2.98, 5.26, 9.30, 16.4, 29.1, 51.3, 90.8,
          160.0, 284.0, 501.0 };

      static Builder defaults() {
        Builder b = new Builder();
        b.exceedanceModel = ExceedanceModel.TRUNCATION_UPPER_ONLY;
        b.truncationLevel = 3.0;
        b.imts = EnumSet.of(Imt.PGA, Imt.SA0P2, Imt.SA1P0);
        b.gmmUncertainty = false;
        b.valueFormat = ValueFormat.ANNUAL_RATE;
        b.defaultImls = IMLS_PGA_SA;
        b.customImls = Maps.newHashMap();
        b.customImls.put(Imt.PGV, IMLS_PGV);
        return b;
      }

      void validate() {
        checkNotNull(exceedanceModel, STATE_ERROR, Hazard.ID, Key.EXCEEDANCE_MODEL);
        checkNotNull(truncationLevel, STATE_ERROR, Hazard.ID, Key.TRUNCATION_LEVEL);
        checkNotNull(imts, STATE_ERROR, Hazard.ID, Key.IMTS);
        checkNotNull(defaultImls, STATE_ERROR, Hazard.ID, Key.DEFAULT_IMLS);
        checkNotNull(customImls, STATE_ERROR, Hazard.ID, Key.CUSTOM_IMLS);
      }

      Map<Imt, XySequence> createLogCurveMap() {
        Map<Imt, XySequence> curveMap = Maps.newEnumMap(Imt.class);
        for (Imt imt : imts) {
          double[] imls = imlsForImt(imt);
          imls = Arrays.copyOf(imls, imls.length);
          Data.ln(imls);
          curveMap.put(imt, immutableCopyOf(create(imls, null)));
        }
        return Maps.immutableEnumMap(curveMap);
      }

      Map<Imt, XySequence> createCurveMap() {
        Map<Imt, XySequence> curveMap = Maps.newEnumMap(Imt.class);
        for (Imt imt : imts) {
          double[] imls = imlsForImt(imt);
          imls = Arrays.copyOf(imls, imls.length);
          curveMap.put(imt, immutableCopyOf(create(imls, null)));
        }
        return Maps.immutableEnumMap(curveMap);
      }

      double[] imlsForImt(Imt imt) {
        return customImls.containsKey(imt) ? customImls.get(imt) : defaultImls;
      }
    }
  }

  /**
   * Default site settings.
   */
  public static final class SiteDefaults {

    static final String ID = CalcConfig.ID + "." + SiteDefaults.class.getSimpleName();

    /**
     * The default average shear-wave velocity down to 30 meters depth.
     *
     * <p><b>Default:</b> {@code 760.0} m/sec
     */
    public final double vs30;

    /**
     * Whether Vs30 was inferred, {@code true}, or measured, {@code false}.
     *
     * <p><b>Default:</b> {@code true} (inferred)
     */
    public final boolean vsInferred;

    /**
     * Depth to the shear-wave velocity horizon of 1.0 km/sec, in km.
     *
     * <p><b>Default:</b> {@code NaN} ({@link GroundMotionModel}s will use a
     * default value or model)
     */
    public final double z1p0;

    /**
     * Depth to the shear-wave velocity horizon of 2.5 km/sec, in km;
     *
     * <p><b>Default:</b> {@code NaN} ({@link GroundMotionModel}s will use a
     * default value or model)
     */
    public final double z2p5;

    private SiteDefaults(
        double vs30,
        boolean vsInferred,
        double z1p0,
        double z2p5) {

      this.vs30 = vs30;
      this.vsInferred = vsInferred;
      this.z1p0 = z1p0;
      this.z2p5 = z2p5;
    }

    private StringBuilder asString() {
      return new StringBuilder()
          .append(LOG_INDENT).append("Site")
          .append(formatEntry(Key.VS30, vs30))
          .append(formatEntry(Key.VS_INF, vsInferred))
          .append(formatEntry(Key.Z1P0, z1p0))
          .append(formatEntry(Key.Z2P5, z2p5));
    }

    private static final class Builder {

      Double vs30;
      Boolean vsInferred;
      Double z1p0;
      Double z2p5;

      SiteDefaults build() {
        return new SiteDefaults(
            vs30,
            vsInferred,
            z1p0,
            z2p5);
      }

      void copy(SiteDefaults that) {
        this.vs30 = that.vs30;
        this.vsInferred = that.vsInferred;
        this.z1p0 = that.z1p0;
        this.z2p5 = that.z2p5;
      }

      void extend(Builder that) {
        if (that.vs30 != null) {
          this.vs30 = that.vs30;
        }
        if (that.vsInferred != null) {
          this.vsInferred = that.vsInferred;
        }
        if (that.z1p0 != null) {
          this.z1p0 = that.z1p0;
        }
        if (that.z2p5 != null) {
          this.z2p5 = that.z2p5;
        }
      }

      static Builder defaults() {
        Builder b = new Builder();
        b.vs30 = Site.VS_30_DEFAULT;
        b.vsInferred = Site.VS_INF_DEFAULT;
        b.z1p0 = Site.Z1P0_DEFAULT;
        b.z2p5 = Site.Z2P5_DEFAULT;
        return b;
      }

      void validate() {
        checkNotNull(vs30, STATE_ERROR, SiteDefaults.ID, Key.VS30);
        checkNotNull(vsInferred, STATE_ERROR, SiteDefaults.ID, Key.VS_INF);
        checkNotNull(z1p0, STATE_ERROR, SiteDefaults.ID, Key.Z1P0);
        checkNotNull(z2p5, STATE_ERROR, SiteDefaults.ID, Key.Z2P5);
      }
    }
  }

  /**
   * Performance and optimization settings.
   */
  public static final class Performance {

    static final String ID = CalcConfig.ID + "." + Performance.class.getSimpleName();

    /**
     * Whether to optimize grid source sets, or not.
     *
     * <p><b>Default:</b> {@code true}
     */
    public final boolean optimizeGrids;

    /**
     * Whether to collapse/combine magnitude-frequency distributions, or not.
     * Doing so prevents uncertainty analysis as logic-tree branches are
     * obscured.
     *
     * <p><b>Default:</b> {@code true}
     */
    public final boolean collapseMfds;

    /**
     * The partition or batch size to use when distributing
     * {@link SourceType#SYSTEM} calculations.
     *
     * <p><b>Default:</b> {@code 1000}
     */
    public final int systemPartition;

    /**
     * The number of threads to use when distributing calculations.
     *
     * <p><b>Default:</b> {@link ThreadCount#ALL}
     */
    public final ThreadCount threadCount;

    private Performance(
        boolean optimizeGrids,
        boolean collapseMfds,
        int systemPartition,
        ThreadCount threadCount) {

      this.optimizeGrids = optimizeGrids;
      this.collapseMfds = collapseMfds;
      this.systemPartition = systemPartition;
      this.threadCount = threadCount;
    }

    private StringBuilder asString() {
      return new StringBuilder()
          .append(LOG_INDENT).append("Performance")
          .append(formatEntry(Key.OPTIMIZE_GRIDS, optimizeGrids))
          .append(formatEntry(Key.COLLAPSE_MFDS, collapseMfds))
          .append(formatEntry(Key.SYSTEM_PARTITION, systemPartition))
          .append(formatEntry(Key.THREAD_COUNT, threadCount.name()));
    }

    private static final class Builder {

      Boolean optimizeGrids;
      Boolean collapseMfds;
      Integer systemPartition;
      ThreadCount threadCount;

      Performance build() {
        return new Performance(
            optimizeGrids,
            collapseMfds,
            systemPartition,
            threadCount);
      }

      void copy(Performance that) {
        this.optimizeGrids = that.optimizeGrids;
        this.collapseMfds = that.collapseMfds;
        this.systemPartition = that.systemPartition;
        this.threadCount = that.threadCount;
      }

      void extend(Builder that) {
        if (that.optimizeGrids != null) {
          this.optimizeGrids = that.optimizeGrids;
        }
        if (that.collapseMfds != null) {
          this.collapseMfds = that.collapseMfds;
        }
        if (that.systemPartition != null) {
          this.systemPartition = that.systemPartition;
        }
        if (that.threadCount != null) {
          this.threadCount = that.threadCount;
        }
      }

      static Builder defaults() {
        Builder b = new Builder();
        b.optimizeGrids = true;
        b.collapseMfds = true;
        b.systemPartition = 1000;
        b.threadCount = ThreadCount.ALL;
        return b;
      }

      void validate() {
        checkNotNull(optimizeGrids, STATE_ERROR, Performance.ID, Key.OPTIMIZE_GRIDS);
        checkNotNull(collapseMfds, STATE_ERROR, Performance.ID, Key.COLLAPSE_MFDS);
        checkNotNull(systemPartition, STATE_ERROR, Performance.ID, Key.SYSTEM_PARTITION);
        checkNotNull(threadCount, STATE_ERROR, Performance.ID, Key.THREAD_COUNT);
      }
    }
  }

  /**
   * Data and file output settings.
   */
  public static final class Output {

    static final String ID = CalcConfig.ID + "." + Output.class.getSimpleName();

    /**
     * The directory to write any results to.
     *
     * <p><b>Default:</b> {@code "curves"} for hazard and deaggregation calculations;
     * {@code "eq-rate"} or {@code "eq-prob"} for rate calculations.
     */
    public final Path directory;

    /**
     * The different {@linkplain DataType types} of data to save. Note that
     * {@link DataType#TOTAL} will <i>always</i> be included in this set,
     * regardless of any user settings.
     *
     * <p><b>Default:</b> [{@link DataType#TOTAL}]
     */
    public final Set<DataType> dataTypes;

    /**
     * The number of results (one per {@code Site}) to store before writing to
     * file(s). A larger number requires more memory.
     *
     * <p><b>Default:</b> {@code 20}
     */
    public final int flushLimit;

    private Output(
        Path directory,
        Set<DataType> dataTypes,
        int flushLimit) {

      this.directory = directory;
      this.dataTypes = Sets.immutableEnumSet(
          DataType.TOTAL,
          dataTypes.toArray(new DataType[dataTypes.size()]));
      this.flushLimit = flushLimit;
    }

    private StringBuilder asString() {
      return new StringBuilder()
          .append(LOG_INDENT).append("Output")
          .append(formatEntry(Key.DIRECTORY, directory.toAbsolutePath().normalize()))
          .append(formatEntry(Key.DATA_TYPES, enumsToString(dataTypes, DataType.class)))
          .append(formatEntry(Key.FLUSH_LIMIT, flushLimit));
    }

    private static final class Builder {

      Path directory;
      Set<DataType> dataTypes;
      Integer flushLimit;

      Output build() {
        return new Output(
            directory,
            dataTypes,
            flushLimit);
      }

      void copy(Output that) {
        this.directory = that.directory;
        this.dataTypes = that.dataTypes;
        this.flushLimit = that.flushLimit;
      }

      void extend(Builder that) {
        if (that.directory != null) {
          this.directory = that.directory;
        }
        if (that.dataTypes != null) {
          this.dataTypes = that.dataTypes;
        }
        if (that.flushLimit != null) {
          this.flushLimit = that.flushLimit;
        }
      }

      static Builder defaults() {
        Builder b = new Builder();
        b.directory = Paths.get(DEFAULT_OUT);
        b.dataTypes = EnumSet.of(DataType.TOTAL);
        b.flushLimit = 5;
        return b;
      }

      void validate() {
        checkNotNull(directory, STATE_ERROR, Output.ID, Key.DIRECTORY);
        checkNotNull(dataTypes, STATE_ERROR, Output.ID, Key.DATA_TYPES);
        checkNotNull(flushLimit, STATE_ERROR, Output.ID, Key.FLUSH_LIMIT);
      }
    }
  }

  /**
   * Magnitude-frequency distribution configuration.
   */
  public static final class Rate {

    static final String ID = CalcConfig.ID + "." + Rate.class.getSimpleName();

    /**
     * The magnitude discretization.
     */
    public final Bins bins;

    /**
     * The distance from a site within which all sources should be included.
     *
     * <p><b>Default:</b> {@code 20}
     */
    public final Double distance;

    /**
     * The rate data distribution type.
     *
     * <p><b>Default:</b> {@link DistributionFormat#INCREMENTAL}
     */
    public final DistributionFormat distributionFormat;

    /**
     * The value format for rate data.
     *
     * <p><b>Default:</b> {@link ValueFormat#ANNUAL_RATE}
     */
    public final ValueFormat valueFormat;

    /**
     * The timespan of interest when computing Poisson probabilities.
     *
     * <p><b>Default:</b> {@code 30}
     */
    public final Double timespan;

    private Rate(
        Bins bins,
        double distance,
        DistributionFormat distributionFormat,
        ValueFormat valueFormat,
        double timespan) {

      this.bins = bins;
      this.distance = distance;
      this.distributionFormat = distributionFormat;
      this.valueFormat = valueFormat;
      this.timespan = timespan;
    }

    private StringBuilder asString() {
      return new StringBuilder()
          .append(LOG_INDENT).append("Rate")
          .append(formatEntry("mBins"))
          .append("min=").append(bins.mMin).append(", ")
          .append("max=").append(bins.mMax).append(", ")
          .append("Δ=").append(bins.Δm)
          .append(formatEntry(Key.DISTANCE, distance))
          .append(formatEntry(Key.DISTRIBUTION, distributionFormat.name()))
          .append(formatEntry(Key.VALUE_FORMAT, valueFormat.name()))
          .append(formatEntry(Key.TIMESPAN, timespan));
    }

    private static final class Builder {

      Bins bins;
      Double distance;
      DistributionFormat distributionFormat;
      ValueFormat valueFormat;
      Double timespan;

      Rate build() {
        return new Rate(
            bins,
            distance,
            distributionFormat,
            valueFormat,
            timespan);
      }

      void copy(Rate that) {
        this.bins = that.bins;
        this.distance = that.distance;
        this.distributionFormat = that.distributionFormat;
        this.valueFormat = that.valueFormat;
        this.timespan = that.timespan;
      }

      void extend(Builder that) {
        if (that.bins != null) {
          this.bins = that.bins;
        }
        if (that.distance != null) {
          this.distance = that.distance;
        }
        if (that.distributionFormat != null) {
          this.distributionFormat = that.distributionFormat;
        }
        if (that.valueFormat != null) {
          this.valueFormat = that.valueFormat;
        }
        if (that.timespan != null) {
          this.timespan = that.timespan;
        }
      }

      static Builder defaults() {
        Builder b = new Builder();
        b.bins = Bins.defaults();
        b.distance = 20.0;
        b.distributionFormat = DistributionFormat.INCREMENTAL;
        b.valueFormat = ValueFormat.ANNUAL_RATE;
        b.timespan = 30.0;
        return b;
      }

      void validate() {
        checkNotNull(bins, STATE_ERROR, Rate.ID, Key.BINS);
        checkNotNull(bins.mMin, STATE_ERROR, Rate.ID, Key.BINS + ".mMin");
        checkNotNull(bins.mMax, STATE_ERROR, Rate.ID, Key.BINS + ".mMax");
        checkNotNull(bins.Δm, STATE_ERROR, Rate.ID, Key.BINS + ".Δm");
        checkNotNull(distance, STATE_ERROR, Rate.ID, Key.DISTANCE);
        checkNotNull(distributionFormat, STATE_ERROR, Rate.ID, Key.DISTRIBUTION);
        checkNotNull(valueFormat, STATE_ERROR, Rate.ID, Key.VALUE_FORMAT);
        checkNotNull(timespan, STATE_ERROR, Rate.ID, Key.TIMESPAN);
      }

    }

    /**
     * The mfd magnitude discretization.
     */
    public static final class Bins {

      static final String ID = CalcConfig.ID + "." + Rate.ID + "." + Bins.class.getSimpleName();

      /** Minimum magnitude. Lower edge of smallest magnitude bin. */
      public final Double mMin;

      /** Maximum magnitude. Upper edge of largest magnitude bin. */
      public final Double mMax;

      /** Magnitude bin width. */
      public final Double Δm;

      Bins(double mMin, double mMax, double Δm) {
        this.mMin = mMin;
        this.mMax = mMax;
        this.Δm = Δm;
      }

      static Bins defaults() {
        return new Bins(4.2, 9.4, 0.1);
      }
    }

  }

  /**
   * Deaggregation configuration.
   */
  public static final class Deagg {

    static final String ID = CalcConfig.ID + "." + Deagg.class.getSimpleName();

    /**
     * The distance, magnitude, and epsilon bins into which contributing sources
     * to hazard are sorted.
     */
    public final Bins bins;

    /**
     * The minimum contribution (in %) that a source must make to hazard to be
     * included on the contributor source list in deaggregation result.
     */
    public final Double contributorLimit;

    private Deagg(
        Bins bins,
        double contributorLimit) {

      this.bins = bins;
      this.contributorLimit = contributorLimit;
    }

    private StringBuilder asString() {
      return new StringBuilder()
          .append(LOG_INDENT).append("Deaggregation")
          .append(formatEntry("rBins"))
          .append("min=").append(bins.rMin).append(", ")
          .append("max=").append(bins.rMax).append(", ")
          .append("Δ=").append(bins.Δr)
          .append(formatEntry("mBins"))
          .append("min=").append(bins.mMin).append(", ")
          .append("max=").append(bins.mMax).append(", ")
          .append("Δ=").append(bins.Δm)
          .append(formatEntry("εBins"))
          .append("min=").append(bins.εMin).append(", ")
          .append("max=").append(bins.εMax).append(", ")
          .append("Δ=").append(bins.Δε)
          .append(formatEntry(Key.CONTRIBUTOR_LIMIT, contributorLimit));
    }

    /**
     * The distance, magnitude, and epsilon bins into which contributing sources
     * to hazard will be sorted.
     */
    public static final class Bins {

      static final String ID = CalcConfig.ID + "." + Deagg.ID + "." + Bins.class.getSimpleName();

      /** Minimum distance. Lower edge of smallest distance bin. */
      public final Double rMin;

      /** Maximum distance. Upper edge of largest distance bin. */
      public final Double rMax;

      /** Distance bin width. */
      public final Double Δr;

      /** Minimum magnitude. Lower edge of smallest magnitude bin. */
      public final Double mMin;

      /** Maximum magnitude. Upper edge of largest magnitude bin. */
      public final Double mMax;

      /** Magnitude bin width. */
      public final Double Δm;

      /** Minimum epsilon. Lower edge of smallest epsilon bin. */
      public final Double εMin;

      /** Maximum epsilon. Upper edge of largest epsilon bin. */
      public final Double εMax;

      /** Epsilon bin width. */
      public final Double Δε;

      Bins(
          double rMin, double rMax, double Δr,
          double mMin, double mMax, double Δm,
          double εMin, double εMax, double Δε) {

        this.rMin = rMin;
        this.rMax = rMax;
        this.Δr = Δr;
        this.mMin = mMin;
        this.mMax = mMax;
        this.Δm = Δm;
        this.εMin = εMin;
        this.εMax = εMax;
        this.Δε = Δε;
      }

      static Bins defaults() {
        return new Bins(
            0.0, 1000.0, 20.0,
            4.4, 9.4, 0.2,
            -3.0, 3.0, 0.5);
      }
    }

    private static final class Builder {

      Bins bins;
      Double contributorLimit;

      Deagg build() {
        return new Deagg(
            bins,
            contributorLimit);
      }

      void copy(Deagg that) {
        this.bins = that.bins;
        this.contributorLimit = that.contributorLimit;
      }

      void extend(Builder that) {
        if (that.bins != null) {
          this.bins = that.bins;
        }
        if (that.contributorLimit != null) {
          this.contributorLimit = that.contributorLimit;
        }
      }

      static Builder defaults() {
        Builder b = new Builder();
        b.bins = Bins.defaults();
        b.contributorLimit = 1.0;
        return b;
      }

      void validate() {
        checkNotNull(bins, STATE_ERROR, Deagg.ID, Key.BINS);
        checkNotNull(bins.rMin, STATE_ERROR, Deagg.ID, Key.BINS + ".rMin");
        checkNotNull(bins.rMax, STATE_ERROR, Deagg.ID, Key.BINS + ".rMax");
        checkNotNull(bins.Δr, STATE_ERROR, Deagg.ID, Key.BINS + ".Δr");
        checkNotNull(bins.mMin, STATE_ERROR, Deagg.ID, Key.BINS + ".mMin");
        checkNotNull(bins.mMax, STATE_ERROR, Deagg.ID, Key.BINS + ".mMax");
        checkNotNull(bins.Δm, STATE_ERROR, Deagg.ID, Key.BINS + ".Δm");
        checkNotNull(bins.εMin, STATE_ERROR, Deagg.ID, Key.BINS + ".εMin");
        checkNotNull(bins.εMax, STATE_ERROR, Deagg.ID, Key.BINS + ".εMax");
        checkNotNull(bins.Δε, STATE_ERROR, Deagg.ID, Key.BINS + ".Δε");
        checkNotNull(contributorLimit, STATE_ERROR, Deagg.ID, Key.CONTRIBUTOR_LIMIT);
      }
    }
  }

  private enum Key {
    RESOURCE,
    /* hazard */
    EXCEEDANCE_MODEL,
    TRUNCATION_LEVEL,
    IMTS,
    GMM_UNCERTAINTY,
    VALUE_FORMAT, 
    DEFAULT_IMLS,
    CUSTOM_IMLS,
    /* site defaults */
    VS30,
    VS_INF,
    Z1P0,
    Z2P5,
    /* performance */
    OPTIMIZE_GRIDS,
    COLLAPSE_MFDS,
    SYSTEM_PARTITION,
    THREAD_COUNT,
    /* output */
    DIRECTORY,
    DATA_TYPES,
    FLUSH_LIMIT,
    /* deagg */
    BINS,
    CONTRIBUTOR_LIMIT,
    /* rate */
    DISTANCE,
    DISTRIBUTION,
    TIMESPAN;

    private String label;

    private Key() {
      this.label = UPPER_UNDERSCORE.to(LOWER_CAMEL, name());
    }

    @Override
    public String toString() {
      return label;
    }
  }

  @Override
  public String toString() {
    return new StringBuilder("Calc Config: ")
        .append(resource.isPresent()
            ? resource.get().toAbsolutePath().normalize()
            : "(from defaults)")
        .append(hazard.asString())
        .append(site.asString())
        .append(performance.asString())
        .append(output.asString())
        .append(deagg.asString())
        .append(rate.asString())
        .toString();
  }

  private static final int MAX_COL = 100;
  private static final int VALUE_WIDTH = MAX_COL - LOG_VALUE_COLUMN;
  private static final String KEY_INDENT = LOG_INDENT + "  ";
  private static final String VALUE_INDENT = NEWLINE + repeat(" ", LOG_VALUE_COLUMN);

  private static String formatEntry(String key) {
    return padEnd(KEY_INDENT + '.' + key + ':', LOG_VALUE_COLUMN, ' ');
  }

  private static <E extends Enum<E>> String formatEntry(E key, Object value) {
    return padEnd(KEY_INDENT + '.' + key + ':', LOG_VALUE_COLUMN, ' ') + value;
  }

  /* wrap a commma-delimited string */
  private static String wrap(String s, boolean pad) {
    if (s.length() <= VALUE_WIDTH) {
      return pad ? VALUE_INDENT + s : s;
    }
    StringBuilder sb = new StringBuilder();
    int lastCommaIndex = s.substring(0, VALUE_WIDTH).lastIndexOf(',') + 1;
    if (pad) {
      sb.append(VALUE_INDENT);
    }
    sb.append(s.substring(0, lastCommaIndex));
    sb.append(wrap(s.substring(lastCommaIndex).trim(), true));
    return sb.toString();
  }

  private static final Gson GSON = new GsonBuilder()
      .setPrettyPrinting()
      .enableComplexMapKeySerialization()
      .serializeNulls()
      .registerTypeAdapter(Double.class, new DoubleSerializer())
      .registerTypeHierarchyAdapter(Path.class, new PathConverter())
      .create();

  private static class DoubleSerializer implements JsonSerializer<Double> {

    @Override
    public JsonElement serialize(
        Double value,
        Type type,
        JsonSerializationContext context) {
      return Double.isNaN(value) ? null : new JsonPrimitive(value);
    }
  }

  private static class PathConverter implements JsonSerializer<Path>, JsonDeserializer<Path> {

    @Override
    public Path deserialize(
        JsonElement json,
        Type type,
        JsonDeserializationContext context) throws JsonParseException {
      return Paths.get(json.getAsString());
    }

    @Override
    public JsonElement serialize(
        Path path,
        Type type,
        JsonSerializationContext context) {
      return new JsonPrimitive(path.toAbsolutePath().normalize().toString());
    }
  }

  /**
   * Save this config in JSON format to the speciifed directory.
   *
   * @param dir the directory to write to
   * @throws IOException if there is a problem writing the file
   */
  public void write(Path dir) throws IOException {
    Path file = dir.resolve(FILE_NAME);
    Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8);
    GSON.toJson(this, writer);
    writer.close();
  }

  /**
   * A builder of configuration instances.
   */
  public static final class Builder {

    private boolean built = false;

    private Path resource;
    private Hazard.Builder hazard;
    private SiteDefaults.Builder site;
    private Performance.Builder performance;
    private Output.Builder output;
    private Deagg.Builder deagg;
    private Rate.Builder rate;

    private Builder() {
      hazard = new Hazard.Builder();
      site = new SiteDefaults.Builder();
      performance = new Performance.Builder();
      output = new Output.Builder();
      deagg = new Deagg.Builder();
      rate = new Rate.Builder();
    }

    /**
     * Initialize a new builder with values copied from the supplied config.
     */
    public static Builder copyOf(CalcConfig config) {
      Builder b = new Builder();
      if (config.resource.isPresent()) {
        b.resource = config.resource.get();
      }
      b.hazard.copy(config.hazard);
      b.site.copy(config.site);
      b.performance.copy(config.performance);
      b.output.copy(config.output);
      b.deagg.copy(config.deagg);
      b.rate.copy(config.rate);
      return b;
    }

    /**
     * Create a new builder from the resource at the specified path. This will
     * only set those fields that are explicitely defined.
     *
     * @param path to configuration file or resource
     * @throws IOException
     */
    public static Builder fromFile(Path path) throws IOException {
      checkNotNull(path);
      // TODO test with zip files
      Path configPath = Files.isDirectory(path) ? path.resolve(FILE_NAME) : path;
      Reader reader = Files.newBufferedReader(configPath, UTF_8);
      Builder b = GSON.fromJson(reader, Builder.class);
      reader.close();
      b.resource = configPath;
      return b;
    }

    /**
     * Initialize a new builder with all fields initialized to default values.
     */
    public static Builder withDefaults() {
      Builder b = new Builder();
      b.hazard = Hazard.Builder.defaults();
      b.site = SiteDefaults.Builder.defaults();
      b.performance = Performance.Builder.defaults();
      b.output = Output.Builder.defaults();
      b.deagg = Deagg.Builder.defaults();
      b.rate = Rate.Builder.defaults();
      return b;
    }

    /**
     * Extend {@code this} builder to match {@code that} builder. Fields in that
     * builder take precedence unless they are not set.
     */
    public Builder extend(final Builder that) {
      checkNotNull(that);
      this.resource = that.resource;
      this.hazard.extend(that.hazard);
      this.site.extend(that.site);
      this.performance.extend(that.performance);
      this.output.extend(that.output);
      this.deagg.extend(that.deagg);
      this.rate.extend(that.rate);
      return this;
    }

    /*
     * Those values for which web services require custom configurations
     * (overrides) are exposed below.
     */

    /**
     * Set the IMTs for which results should be calculated.
     * 
     * @see Hazard#imts
     */
    public Builder imts(Set<Imt> imts) {
      this.hazard.imts = checkNotNull(imts);
      return this;
    }

    /**
     * Set the timespan for earthquake probabilities. Calling this method also
     * sets {@link Rate#valueFormat} to {@link ValueFormat#POISSON_PROBABILITY} to
     * ensure consistency.
     * 
     * @see Rate#timespan
     */
    public Builder timespan(double timespan) {
      this.rate.timespan = timespan;
      this.rate.valueFormat = ValueFormat.POISSON_PROBABILITY;
      return this;
    }

    /**
     * Set the cutoff distance within which to include all sources when
     * computing earthquake rates or probabilities.
     * 
     * @see Rate#distance
     */
    public Builder distance(double distance) {
      this.rate.distance = distance;
      return this;
    }

    private void validateState() {
      checkState(!built, "This %s instance as already been used", ID + ".Builder");
      hazard.validate();
      site.validate();
      performance.validate();
      output.validate();
      deagg.validate();
      rate.validate();
      built = true;
    }

    /**
     * Build a new calculation configuration.
     */
    public CalcConfig build() {
      validateState();
      return new CalcConfig(
          Optional.fromNullable(resource),
          hazard.build(),
          site.build(),
          performance.build(),
          output.build(),
          deagg.build(),
          rate.build());
    }
  }

}
