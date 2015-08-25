package org.opensha2.calc;

import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_UNDERSCORE;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.opensha2.util.TextUtils.format;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.opensha2.data.ArrayXY_Sequence;
import org.opensha2.data.DataUtils;
import org.opensha2.data.XY_Sequence;
import org.opensha2.gmm.Imt;
import org.opensha2.util.Parsing;

import com.google.common.collect.Iterables;
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

	
	// TODO revisit privatization, comments, and immutability

	static final String FILE_NAME = "config.json";

	final Path resource;

	final ExceedanceModel exceedanceModel;
	final double truncationLevel;
	final Set<Imt> imts;
	private final double[] defaultImls;
	private final Map<Imt, double[]> customImls;
	final DeaggData deagg;
	private final SiteSet sites;
	
	final Map<Imt, ArrayXY_Sequence> modelCurves;
	final Map<Imt, ArrayXY_Sequence> logModelCurves;

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
			DeaggData deagg,
			SiteSet sites,
			Map<Imt, ArrayXY_Sequence> modelCurves,
			Map<Imt, ArrayXY_Sequence> logModelCurves) {

		this.resource = resource;
		this.exceedanceModel = exceedanceModel;
		this.truncationLevel = truncationLevel;
		this.imts = imts;
		this.defaultImls = defaultImls;
		this.customImls = customImls;
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
	 * Return an unmodifiable iterator over the {@code Site}s specified by this
	 * configuration.
	 */
	public Iterable<Site> sites() {
		return sites;
	}
	
	/**
	 * Return an empty linear (i.e. not log) curve for the requested {@code Imt}.
	 * @param imt to get curve for
	 */
	public XY_Sequence modelCurve(Imt imt) {
		return modelCurves.get(imt);
	}
	
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

		Path resource;
		ExceedanceModel exceedanceModel;
		Double truncationLevel;
		Set<Imt> imts;
		double[] defaultImls;
		Map<Imt, double[]> customImls;
		DeaggData deagg;
		SiteSet sites;

		public Builder copy(CalcConfig config) {
			checkNotNull(config);
			this.resource = config.resource;
			this.exceedanceModel = config.exceedanceModel;
			this.truncationLevel = config.truncationLevel;
			this.imts = config.imts;
			this.defaultImls = config.defaultImls;
			this.customImls = config.customImls;
			this.deagg = config.deagg;
			this.sites = config.sites;
			return this;
		}

		public Builder withDefaults() {
			this.exceedanceModel = ExceedanceModel.TRUNCATION_UPPER_ONLY;
			this.truncationLevel = 3.0;
			this.imts = EnumSet.of(Imt.PGA, Imt.SA0P2, Imt.SA1P0);
			// Slightly modified version of NSHM 5Hz curve, size = 20
			this.defaultImls = new double[] { 0.0025, 0.0045, 0.0075, 0.0113, 0.0169, 0.0253,
				0.0380, 0.0570, 0.0854, 0.128, 0.192, 0.288, 0.432, 0.649, 0.973, 1.46,
				2.19, 3.28, 4.92, 7.38 };
			this.customImls = Maps.newHashMap();
			this.deagg = new DeaggData();
			this.sites = new SiteSet(Lists.newArrayList(Site.builder().build()));
			return this;
		}

		public Builder extend(final Builder that) {
			checkNotNull(that);
			if (that.resource != null) this.resource = that.resource;
			if (that.exceedanceModel != null) this.exceedanceModel = that.exceedanceModel;
			if (that.truncationLevel != null) this.truncationLevel = that.truncationLevel;
			if (that.imts != null) this.imts = that.imts;
			if (that.defaultImls != null) this.defaultImls = that.defaultImls;
			if (that.customImls != null) this.customImls = that.customImls;
			if (that.deagg != null) this.deagg = that.deagg;
			if (that.sites != null) this.sites = that.sites;
			return this;
		}

		public Builder imts(Set<Imt> imts) {
			this.imts = checkNotNull(imts);
			return this;
		}

		private Map<Imt, ArrayXY_Sequence> createLogCurveMap() {
			Map<Imt, ArrayXY_Sequence> curveMap = Maps.newEnumMap(Imt.class);
			for (Imt imt : imts) {
				double[] imls = imlsForImt(imt);
				imls = Arrays.copyOf(imls, imls.length);
				DataUtils.ln(imls);
				curveMap.put(imt, ArrayXY_Sequence.create(imls, null));
			}
			return Maps.immutableEnumMap(curveMap);
		}

		private  Map<Imt, ArrayXY_Sequence> createCurveMap() {
			Map<Imt, ArrayXY_Sequence> curveMap = Maps.newEnumMap(Imt.class);
			for (Imt imt : imts) {
				double[] imls = imlsForImt(imt);
				imls = Arrays.copyOf(imls, imls.length);
				curveMap.put(imt, ArrayXY_Sequence.create(imls, null));
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
			checkNotNull(deagg, MSSG, buildId, Key.DEAGG);
			checkNotNull(sites, MSSG, buildId, Key.SITES);
			built = true;
		}

		public CalcConfig build() {
			validateState(ID);
			Set<Imt> finalImts = Sets.immutableEnumSet(imts);
			Map<Imt, ArrayXY_Sequence> curves = createCurveMap();
			Map<Imt, ArrayXY_Sequence> logCurves = createLogCurveMap();
			return new CalcConfig(
				resource, exceedanceModel, truncationLevel, finalImts,
				defaultImls, customImls, deagg, sites, curves, logCurves);
		}
		
	}

}
