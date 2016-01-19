package org.opensha2.calc;

import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_UNDERSCORE;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.opensha2.util.TextUtils.format;

import static org.opensha2.data.XySequence.*;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.opensha2.data.Data;
import org.opensha2.data.XySequence;
import org.opensha2.gmm.Imt;
import org.opensha2.util.Parsing;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Calculation configuration.
 * 
 * All config fields are immutable and all methods return immutable objects.
 * 
 * @author Peter Powers
 */
public final class CalcConfig {

	/**
	 * Returns models of the intensity measure levels for each {@code Imt}
	 * adressed by this calculation. The {@code Map} returned by this method is
	 * an immutable {@code EnumMap}.
	 * 
	 * @see Maps#immutableEnumMap(Map)
	 */

	/**
	 * Returns models of the intensity measure levels for each {@code Imt}
	 * adressed by this calculation. The x-values in each sequence are in
	 * natural log space. The {@code Map} returned by this method is an
	 * immutable {@code EnumMap}.
	 * 
	 * @see Maps#immutableEnumMap(Map)
	 */

	static final String FILE_NAME = "config.json";

	private final Path resource;

	private final ExceedanceModel exceedanceModel;
	private final double truncationLevel;
	private final Set<Imt> imts;
	private final double[] defaultImls;
	private final Map<Imt, double[]> customImls;
	private final boolean optimizeGrids;
	private final boolean gmmUncertainty;

	private final DeaggData deagg;

	private final SiteSet sites;

	private final Map<Imt, XySequence> modelCurves;
	private final Map<Imt, XySequence> logModelCurves;

	private static final Gson GSON = new GsonBuilder()
		.registerTypeAdapter(Site.class, new Site.Deserializer())
		.registerTypeAdapter(SiteSet.class, new SiteSet.Deserializer())
		.create();

	private CalcConfig(
			Path resource,
			ExceedanceModel exceedanceModel,
			double truncationLevel,
			Set<Imt> imts,
			double[] defaultImls,
			Map<Imt, double[]> customImls,
			boolean optimizeGrids,
			boolean gmmUncertainty,
			DeaggData deagg,
			SiteSet sites,
			Map<Imt, XySequence> modelCurves,
			Map<Imt, XySequence> logModelCurves) {

		this.resource = resource;
		this.exceedanceModel = exceedanceModel;
		this.truncationLevel = truncationLevel;
		this.imts = imts;
		this.defaultImls = defaultImls;
		this.customImls = customImls;
		this.optimizeGrids = optimizeGrids;
		this.gmmUncertainty = gmmUncertainty;
		this.deagg = deagg;
		this.sites = sites;
		this.modelCurves = modelCurves;
		this.logModelCurves = logModelCurves;
	}

	private enum Key {
		RESOURCE,
		EXCEEDANCE_MODEL,
		TRUNCATION_LEVEL,
		IMTS,
		DEFAULT_IMLS,
		CUSTOM_IMLS,
		GMM_UNCERTAINTY,
		OPTIMIZE_GRIDS,
		DEAGG,
		SITES;

		private String label;

		private Key() {
			this.label = UPPER_UNDERSCORE.to(LOWER_CAMEL, name());
		}

		@Override public String toString() {
			return label;
		}
	}

	@Override public String toString() {
		String customImlStr = "";
		if (!customImls.isEmpty()) {
			StringBuilder sb = new StringBuilder();
			for (Entry<Imt, double[]> entry : customImls.entrySet()) {
				String imtStr = "(override) " + entry.getKey().name();
				sb.append(format(imtStr)).append(Arrays.toString(entry.getValue()));
			}
			customImlStr = sb.toString();
		}

		return new StringBuilder("Calc config:")
			.append(format(Key.RESOURCE)).append(resource)
			.append(format(Key.EXCEEDANCE_MODEL)).append(exceedanceModel)
			.append(format(Key.TRUNCATION_LEVEL)).append(truncationLevel)
			.append(format(Key.IMTS)).append(Parsing.enumsToString(imts, Imt.class))
			.append(format(Key.DEFAULT_IMLS)).append(Arrays.toString(defaultImls))
			.append(customImlStr)
			.append(format(Key.OPTIMIZE_GRIDS)).append(optimizeGrids)
			.append(format(Key.GMM_UNCERTAINTY)).append(gmmUncertainty)
			.append(format("Deaggregation R"))
			.append("min=").append(deagg.rMin).append(", ")
			.append("max=").append(deagg.rMax).append(", ")
			.append("Δ=").append(deagg.Δr)
			.append(format("Deaggregation M"))
			.append("min=").append(deagg.mMin).append(", ")
			.append("max=").append(deagg.mMax).append(", ")
			.append("Δ=").append(deagg.Δm)
			.append(format("Deaggregation ε"))
			.append("min=").append(deagg.εMin).append(", ")
			.append("max=").append(deagg.εMax).append(", ")
			.append("Δ=").append(deagg.Δε)
			.append(format(Key.SITES)).append(sites)
			.toString();
	}

	/**
	 * The probability distribution model to use when computing hazard curves.
	 */
	public ExceedanceModel exceedanceModel() {
		return exceedanceModel; // TODO probabilitModel
	}

	/**
	 * The number of standard deviations at which to truncate a distribution.
	 * This field is ignored if a model does not implement truncation.
	 */
	public double truncationLevel() {
		return truncationLevel;
	}

	/**
	 * The unmodifiable {@code Set} of IMTs for which calculations should be
	 * performed.
	 */
	public Set<Imt> imts() {
		return imts;
	}

	/**
	 * Whether to optimize grid source sets, or not.
	 */
	public boolean optimizeGrids() {
		return optimizeGrids;
	}

	/**
	 * Whether to consider additional ground motion model uncertainty, or not.
	 */
	public boolean gmmUncertainty() {
		return gmmUncertainty;
	}

	/**
	 * Deaggregation configuration data.
	 */
	public DeaggData deagg() {
		return deagg;
	}

	/**
	 * An unmodifiable iterator over the {@code Site}s at which hazard should be
	 * calculated.
	 */
	public Iterable<Site> sites() {
		return sites;
	}

	/**
	 * An empty linear curve for the requested {@code Imt}.
	 * @param imt to get curve for
	 */
	public XySequence modelCurve(Imt imt) {
		return modelCurves.get(imt);
	}

	/**
	 * An immutable map of model curves where x-values are in linear space.
	 */
	public Map<Imt, XySequence> modelCurves() {
		return modelCurves;
	}

	/**
	 * An immutable map of model curves where x-values are in natural-log space.
	 */
	public Map<Imt, XySequence> logModelCurves() {
		return logModelCurves;
	}

	/**
	 * Deaggregation configuration data container.
	 */
	@SuppressWarnings("javadoc")
	public static final class DeaggData {

		public final double rMin;
		public final double rMax;
		public final double Δr;

		public final double mMin;
		public final double mMax;
		public final double Δm;

		public final double εMin;
		public final double εMax;
		public final double Δε;

		DeaggData() {
			rMin = 0.0;
			rMax = 100.0;
			Δr = 10.0;

			mMin = 5.0;
			mMax = 7.0;
			Δm = 0.1;

			εMin = -3;
			εMax = 3.0;
			Δε = 0.5;
		}
	}

	/**
	 * Create a new calculation configuration builder from the resource at the
	 * specified {@code path}.
	 * 
	 * @param path to configuration file or resource
	 * @throws IOException
	 */
	public static Builder builder(Path path) throws IOException {
		checkNotNull(path);
		// TODO test with zip files
		Path configPath = Files.isDirectory(path) ? path.resolve(FILE_NAME) : path;
		Reader reader = Files.newBufferedReader(configPath, UTF_8);
		Builder configBuilder = GSON.fromJson(reader, Builder.class);
		configBuilder.resource = configPath;
		reader.close();
		return configBuilder;
	}

	/**
	 * Create a new empty calculation configuration builder.
	 */
	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {

		private static final String ID = "CalcConfig.Builder";
		private boolean built = false;

		// TODO should resource be Optional; if created with defaults
		// there will be no path
		private Path resource;
		private ExceedanceModel exceedanceModel;
		private Double truncationLevel;
		private Set<Imt> imts;
		private double[] defaultImls;
		private Map<Imt, double[]> customImls;
		private Boolean optimizeGrids;
		private Boolean gmmUncertainty;
		private DeaggData deagg;
		private SiteSet sites;

		/**
		 * Initialize a new builder with a copy of that supplied.
		 */
		public Builder copy(CalcConfig config) {
			checkNotNull(config);
			this.resource = config.resource;
			this.exceedanceModel = config.exceedanceModel;
			this.truncationLevel = config.truncationLevel;
			this.imts = config.imts;
			this.defaultImls = config.defaultImls;
			this.customImls = config.customImls;
			this.optimizeGrids = config.optimizeGrids;
			this.gmmUncertainty = config.gmmUncertainty;
			this.deagg = config.deagg;
			this.sites = config.sites;
			return this;
		}

		/**
		 * Initialize a new builder with defaults.
		 */
		public Builder withDefaults() {
			this.exceedanceModel = ExceedanceModel.TRUNCATION_UPPER_ONLY;
			this.truncationLevel = 3.0;
			this.imts = EnumSet.of(Imt.PGA, Imt.SA0P2, Imt.SA1P0);
			// Slightly modified version of NSHM 5Hz curve, size = 20
			this.defaultImls = new double[] { 0.0025, 0.0045, 0.0075, 0.0113, 0.0169, 0.0253,
				0.0380, 0.0570, 0.0854, 0.128, 0.192, 0.288, 0.432, 0.649, 0.973, 1.46,
				2.19, 3.28, 4.92, 7.38 };
			this.customImls = Maps.newHashMap();
			this.optimizeGrids = true;
			this.gmmUncertainty = false;
			this.deagg = new DeaggData();
			this.sites = new SiteSet(Lists.newArrayList(Site.builder().build()));
			return this;
		}

		/**
		 * Extend {@code this} builder to match {@code that} builder. Fields in
		 * that builder take precedence unless they are not set.
		 */
		public Builder extend(final Builder that) {
			checkNotNull(that);
			if (that.resource != null) this.resource = that.resource;
			if (that.exceedanceModel != null) this.exceedanceModel = that.exceedanceModel;
			if (that.truncationLevel != null) this.truncationLevel = that.truncationLevel;
			if (that.imts != null) this.imts = that.imts;
			if (that.defaultImls != null) this.defaultImls = that.defaultImls;
			if (that.customImls != null) this.customImls = that.customImls;
			if (that.optimizeGrids != null) this.optimizeGrids = that.optimizeGrids;
			if (that.gmmUncertainty != null) this.gmmUncertainty = that.gmmUncertainty;
			if (that.deagg != null) this.deagg = that.deagg;
			if (that.sites != null) this.sites = that.sites;
			return this;
		}

		/**
		 * Set the IMTs for which results should be calculated.
		 */
		public Builder imts(Set<Imt> imts) {
			this.imts = checkNotNull(imts);
			return this;
		}

		private Map<Imt, XySequence> createLogCurveMap() {
			Map<Imt, XySequence> curveMap = Maps.newEnumMap(Imt.class);
			for (Imt imt : imts) {
				double[] imls = imlsForImt(imt);
				imls = Arrays.copyOf(imls, imls.length);
				Data.ln(imls);
				curveMap.put(imt, immutableCopyOf(create(imls, null)));
			}
			return Maps.immutableEnumMap(curveMap);
		}

		private Map<Imt, XySequence> createCurveMap() {
			Map<Imt, XySequence> curveMap = Maps.newEnumMap(Imt.class);
			for (Imt imt : imts) {
				double[] imls = imlsForImt(imt);
				imls = Arrays.copyOf(imls, imls.length);
				curveMap.put(imt, immutableCopyOf(create(imls, null)));
			}
			return Maps.immutableEnumMap(curveMap);
		}

		private double[] imlsForImt(Imt imt) {
			return customImls.containsKey(imt) ? customImls.get(imt) : defaultImls;
		}

		private static final String MSSG = "%s %s not set";

		private void validateState(String buildId) {
			checkState(!built, "This %s instance as already been used", buildId);
			checkNotNull(exceedanceModel, MSSG, buildId, Key.EXCEEDANCE_MODEL);
			checkNotNull(truncationLevel, MSSG, buildId, Key.TRUNCATION_LEVEL);
			checkNotNull(imts, MSSG, buildId, Key.IMTS);
			checkNotNull(defaultImls, MSSG, buildId, Key.DEFAULT_IMLS);
			checkNotNull(customImls, MSSG, buildId, Key.CUSTOM_IMLS);
			checkNotNull(optimizeGrids, MSSG, buildId, Key.OPTIMIZE_GRIDS);
			checkNotNull(gmmUncertainty, MSSG, buildId, Key.GMM_UNCERTAINTY);
			checkNotNull(deagg, MSSG, buildId, Key.DEAGG);
			checkNotNull(sites, MSSG, buildId, Key.SITES);
			built = true;
		}

		/**
		 * Build a new calculation configuration.
		 */
		public CalcConfig build() {
			validateState(ID);
			Set<Imt> finalImts = Sets.immutableEnumSet(imts);
			Map<Imt, XySequence> curves = createCurveMap();
			Map<Imt, XySequence> logCurves = createLogCurveMap();
			return new CalcConfig(
				resource,
				exceedanceModel,
				truncationLevel,
				finalImts,
				defaultImls,
				customImls,
				optimizeGrids,
				gmmUncertainty,
				deagg,
				sites,
				curves, logCurves);
		}

	}

}
