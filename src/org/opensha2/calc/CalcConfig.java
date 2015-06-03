package org.opensha2.calc;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.opensha2.util.TextUtils.NEWLINE;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.opensha2.data.ArrayXY_Sequence;
import org.opensha2.data.DataUtils;
import org.opensha2.gmm.Imt;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Calculation configuration.
 * @author Peter Powers
 */
public final class CalcConfig {

	static final String FILE_NAME = "config.json";

	final ExceedanceModel exceedanceModel;
	final double truncationLevel;
	final Set<Imt> imts;
	final double[] defaultImls;
	final Map<Imt, double[]> customImls;
	final Deagg deagg;
	final SiteSet sites;

	private static final Gson GSON = new GsonBuilder()
		.setPrettyPrinting()
		.registerTypeAdapter(Site.class, new Site.Deserializer())
		.registerTypeAdapter(Site.class, new Site.Serializer())
		.registerTypeAdapter(SiteSet.class, new SiteSet.Deserializer())
		.create();

	// defaults
	private CalcConfig() {

		/*
		 * Default values. These are initialized here because gson will not
		 * deserialize field initialized final primitives and Strings.
		 */

		/*
		 * TODO consider adding TypeAdapter for enums that will throw an
		 * exception if invalid enum value is supplied in config.json
		 * 
		 * TODO consider strengthening immutability, however, methods provide
		 * immutable views of those fields required outside package
		 */

		exceedanceModel = ExceedanceModel.TRUNCATION_UPPER_ONLY;
		truncationLevel = 3.0;
		imts = Sets.immutableEnumSet(Imt.PGA, Imt.SA0P2, Imt.SA1P0);
		// Slightly modified version of NSHM 5Hz curve, size = 20
		defaultImls = new double[] { 0.0025, 0.0045, 0.0075, 0.0113, 0.0169, 0.0253, 0.0380, 0.0570, 0.0854, 0.128, 0.192, 0.288, 0.432, 0.649, 0.973, 1.46, 2.19, 3.28, 4.92, 7.38 };
		customImls = Maps.newHashMap();
		deagg = new Deagg();
		sites = new SiteSet(Lists.newArrayList(Site.builder().build()));
	}

	// copy with imt override
	private CalcConfig(CalcConfig config, Set<Imt> imts) {
		this.exceedanceModel = config.exceedanceModel;
		this.truncationLevel = config.truncationLevel;
		this.imts = ImmutableSet.copyOf(imts);
		this.defaultImls = config.defaultImls;
		this.customImls = config.customImls;
		this.deagg = config.deagg;
		this.sites = config.sites;
	}

	/**
	 * Load a calculation configuration from the resource at the specified
	 * {@code path}.
	 *
	 * @param path to configuration file or resource
	 * @throws IOException if problem encountered loading config
	 */
	public static CalcConfig load(Path path) throws IOException {
		Path configPath = path.resolve(FILE_NAME);
		Reader reader = Files.newBufferedReader(configPath, UTF_8);
		CalcConfig config = GSON.fromJson(reader, CalcConfig.class);
		reader.close();
		return config;
	}

	/**
	 * Create a copy of an existing calculation coinfiguration.
	 * 
	 * @param config to copy
	 */
	public static CalcConfig copyOf(CalcConfig config) {
		return copyWithImts(config, config.imts);
	}

	/**
	 * Create a copy of an existing calculation coinfiguration but with updated
	 * {@link Imt}s.
	 * 
	 * @param config to copy
	 * @param imts to use in calculations
	 */
	public static CalcConfig copyWithImts(CalcConfig config, Set<Imt> imts) {
		return new CalcConfig(config, imts);
	}

	@Override public String toString() {
		String customImlStr = "";
		if (!customImls.isEmpty()) {
			StringBuilder sb = new StringBuilder();
			for (Entry<Imt, double[]> entry : customImls.entrySet()) {
				String imtStr = "(IMT override) " + entry.getKey() + ": ";
				sb.append(Strings.padStart(imtStr, 24, ' '))
					.append(Arrays.toString(entry.getValue()))
					.append(NEWLINE);
			}
			customImlStr = sb.toString();
		}

		StringBuilder sb = new StringBuilder("Calculation config:").append(NEWLINE)
			.append("     Exceedance model: ")
			.append("type=").append(exceedanceModel).append(", ")
			.append("truncLevel=").append(truncationLevel)
			.append(NEWLINE)
			.append("                 IMTs: ").append(imts)
			.append(NEWLINE)
			.append("         Default IMLs: ")
			.append(Arrays.toString(defaultImls))
			.append(NEWLINE)
			.append(customImlStr)
			.append("      Deaggregation R: ")
			.append("min=").append(deagg.rMin).append(", ")
			.append("max=").append(deagg.rMax).append(", ")
			.append("Δ=").append(deagg.Δr)
			.append(NEWLINE)
			.append("      Deaggregation M: ")
			.append("min=").append(deagg.mMin).append(", ")
			.append("max=").append(deagg.mMax).append(", ")
			.append("Δ=").append(deagg.Δm)
			.append(NEWLINE)
			.append("      Deaggregation ε: ")
			.append("min=").append(deagg.εMin).append(", ")
			.append("max=").append(deagg.εMax).append(", ")
			.append("Δ=").append(deagg.Δε)
			.append(NEWLINE);

		for (Site site : sites) {
			sb.append("                 ");
			sb.append(site.toString());
			sb.append(NEWLINE);
		}

		return sb.toString();
	}

	/**
	 * Returns models of the intensity measure levels for each {@code Imt}
	 * adressed by this calculation. The x-values in each sequence are in
	 * natural log space. The {@code Map} returned by this method is an
	 * immutable {@code EnumMap}.
	 * 
	 * @see Maps#immutableEnumMap(Map)
	 */
	public Map<Imt, ArrayXY_Sequence> logModelCurves() {
		Map<Imt, ArrayXY_Sequence> curveMap = Maps.newEnumMap(Imt.class);
		for (Imt imt : imts) {
			double[] imls = imlsForImt(imt);
			imls = Arrays.copyOf(imls, imls.length);
			DataUtils.ln(imls);
			curveMap.put(imt, ArrayXY_Sequence.create(imls, null));
		}
		return Maps.immutableEnumMap(curveMap);
	}

	/**
	 * Returns models of the intensity measure levels for each {@code Imt}
	 * adressed by this calculation. The {@code Map} returned by this method is
	 * an immutable {@code EnumMap}.
	 * 
	 * @see Maps#immutableEnumMap(Map)
	 */
	public Map<Imt, ArrayXY_Sequence> modelCurves() {
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

	/**
	 * Returns an unmodifiable iterator over the {@code Site}s specified by this
	 * configuration.
	 * 
	 * @see Iterables#unmodifiableIterable(Iterable)
	 */
	public Iterable<Site> sites() {
		return Iterables.unmodifiableIterable(sites);
	}

	static void exportDefaults(Path path) throws IOException {
		Writer writer = Files.newBufferedWriter(path, UTF_8);
		GSON.toJson(new CalcConfig(), writer);
		writer.close();
	}

	// TODO comment and finalize

	public static final class Deagg {

		public final double rMin;
		public final double rMax;
		public final double Δr;

		public final double mMin;
		public final double mMax;
		public final double Δm;

		public final double εMin;
		public final double εMax;
		public final double Δε;

		Deagg() {
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

}
