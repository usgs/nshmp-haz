package org.opensha2.calc;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.opensha2.util.TextUtils.NEWLINE;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.opensha2.data.ArrayXY_Sequence;
import org.opensha2.data.DataUtils;
import org.opensha2.gmm.GroundMotionModel;
import org.opensha2.gmm.Imt;

import com.google.common.base.Strings;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Calculation configuration.
 * @author Peter Powers
 */
public final class CalcConfig {

	static final String FILE_NAME = "config.json";

	public final ExceedanceModel exceedanceModel;
	public final double truncationLevel;

	public final Set<Imt> imts;
	
	public final double[] defaultImls;
	public final Map<Imt, double[]> customImls;
	
	public final Deagg deagg;

	public final SiteSet sites;

	private static final Gson GSON = new GsonBuilder()
		.setPrettyPrinting()
		.registerTypeAdapter(Site.class, new Site.Deserializer())
		.registerTypeAdapter(Site.class, new Site.Serializer())
		.registerTypeAdapter(SiteSet.class, new SiteSet.Deserializer())
		.create();

	private CalcConfig() {

		/*
		 * Default values. These are initialized here because gson will not
		 * deserialize field initialized final primitives and Strings.
		 */
		
		// TODO consider adding TypeAdapter for enums that will throw an
		// exception if invalid enum value is supplied in config.json

		exceedanceModel = ExceedanceModel.TRUNCATION_UPPER_ONLY;
		truncationLevel = 3.0;

		imts = EnumSet.of(Imt.PGA, Imt.SA0P2, Imt.SA1P0);
		
		/* Slightly modified version of NSHM 5Hz curve, size = 20 */
		defaultImls = new double[] { 0.0025, 0.0045, 0.0075, 0.0113, 0.0169, 0.0253, 0.0380, 0.0570, 0.0854, 0.128, 0.192, 0.288, 0.432, 0.649, 0.973, 1.46, 2.19, 3.28, 4.92, 7.38 };
		customImls = Maps.newHashMap();

		deagg = new Deagg();
		sites = new SiteSet(Lists.newArrayList(Site.builder().build()));
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
			sb.append("                 ").append(site.toString()).append(NEWLINE);
		}

		return sb.toString();
	}

	public double[] imlsForImt(Imt imt) {
		return customImls.containsKey(imt) ? customImls.get(imt) : defaultImls;
	}
	
	/**
	 * Returns models of the intensity measure levels for each {@code Imt} adressed
	 * by this calculation. Note that the x-values in each sequence are in natural
	 * log space.
	 */
	public Map<Imt, ArrayXY_Sequence> logModelCurves() {
		Map<Imt, ArrayXY_Sequence> curveMap = Maps.newEnumMap(Imt.class);
		for (Imt imt : imts) {
			double[] imls = imlsForImt(imt);
			imls = Arrays.copyOf(imls, imls.length);
			DataUtils.ln(imls);
			curveMap.put(imt, ArrayXY_Sequence.create(imls, null));
		}
		return curveMap;
	}
	
	public Map<Imt, ArrayXY_Sequence> modelCurves() {
		Map<Imt, ArrayXY_Sequence> curveMap = Maps.newEnumMap(Imt.class);
		for (Imt imt : imts) {
			double[] imls = imlsForImt(imt);
			imls = Arrays.copyOf(imls, imls.length);
			curveMap.put(imt, ArrayXY_Sequence.create(imls, null));
		}
		return curveMap;
	}


	public static CalcConfig load(Path path) throws IOException {
		Path configPath = path.resolve(FILE_NAME);
		Reader reader = Files.newBufferedReader(configPath, UTF_8);
		CalcConfig config = GSON.fromJson(reader, CalcConfig.class);
		reader.close();
		return config;
	}

	static void exportDefaults(Path path) throws IOException {
		Writer writer = Files.newBufferedWriter(path, UTF_8);
		GSON.toJson(new CalcConfig(), writer);
		writer.close();
	}

	// TODO clean
	public static void main(String[] args) throws IOException {
//		 Path path = Paths.get("..","nshmp-model-dev", "tmp", "config_calc3.json");
		// exportDefaults(path);

		// Path path = Paths.get("tmp", "config", "config_nshm_imls.json");
		// Path path = Paths.get("..","nshmp-model-dev", "models", "PEER",
		// "Set2-Case2a1", "config.json");
		Path path = Paths.get("..", "nshmp-model-dev", "models", "PEER", "Set1-Case1",
			"config.json");
		CalcConfig c = load(path);
		System.out.println(c);
	}

	public static class Deagg {

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
