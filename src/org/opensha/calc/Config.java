package org.opensha.calc;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.opensha.util.TextUtils.NEWLINE;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.opensha.gmm.Imt;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Calculation configuration.
 * @author Peter Powers
 */
final class Config {

	public final SigmaModel sigmaModel;
	public final double truncationLevel;

	public final double[] defaultImls;
	public final Map<Imt, double[]> customImls;

	public final Deagg deagg;

	public final List<Site> sites;

	// @formatter:off
	private static final Gson GSON = new GsonBuilder()
			.setPrettyPrinting()
			.registerTypeAdapter(Site.class, new Site.Deserializer())
			.registerTypeAdapter(Site.class, new Site.Serializer())
			.create();
	// @formatter:on

	private Config() {

		/*
		 * Default values. These are initialized here because gson will not
		 * deserialize field initialized final primitives and Strings.
		 */

		sigmaModel = SigmaModel.TRUNCATION_UPPER_ONLY;
		truncationLevel = 3.0;

		/* Slightly modified version of NSHM 5Hz curve, size = 20 */
		defaultImls = new double[] { 0.0025, 0.0045, 0.0075, 0.0113, 0.0169, 0.0253, 0.0380, 0.0570, 0.0854, 0.128, 0.192, 0.288, 0.432, 0.649, 0.973, 1.46, 2.19, 3.28, 4.92, 7.38 };
		customImls = Maps.newHashMap();

		deagg = new Deagg();
		sites = Lists.newArrayList(Site.builder().build());

	}

	@Override public String toString() {
		// @formatter:off
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
			.append("       Sigma model: ")
			.append("type=").append(sigmaModel).append(", ")
			.append("level=").append(truncationLevel)
			.append(NEWLINE)
			.append("      Default IMLs: ")
			.append(Arrays.toString(defaultImls))
			.append(NEWLINE)
			.append(customImlStr)
			.append("   Deaggregation R: ")
			.append("min=").append(deagg.rMin).append(", ")
			.append("max=").append(deagg.rMax).append(", ")
			.append("Δ=").append(deagg.Δr)
			.append(NEWLINE)
			.append("   Deaggregation M: ")
			.append("min=").append(deagg.mMin).append(", ")
			.append("max=").append(deagg.mMax).append(", ")
			.append("Δ=").append(deagg.Δm)
			.append(NEWLINE)
			.append("   Deaggregation ε: ")
			.append("min=").append(deagg.εMin).append(", ")
			.append("max=").append(deagg.εMax).append(", ")
			.append("Δ=").append(deagg.Δε)
			.append(NEWLINE);
		// @formatter:on
		
		for (Site site : sites) {
			sb.append("              ").append(site.toString()).append(NEWLINE);
		}

		return sb.toString();
	}

	double[] imlsForImt(Imt imt) {
		return customImls.containsKey(imt) ? customImls.get(imt) : defaultImls;
	}

	static Config load(Path path) throws IOException {
		Reader reader = Files.newBufferedReader(path, UTF_8);
		Config config = GSON.fromJson(reader, Config.class);
		reader.close();
		return config;
	}

	static void exportDefaults(Path path) throws IOException {
		Writer writer = Files.newBufferedWriter(path, UTF_8);
		GSON.toJson(new Config(), writer);
		writer.close();
	}

	// TODO clean
	public static void main(String[] args) throws IOException {
//		Path path = Paths.get("tmp", "config", "config_calc2.json");
//		exportDefaults(path);

//		 Path path = Paths.get("tmp", "config", "config_nshm_imls.json");
		 Path path = Paths.get("..","nshmp-model-dev", "models", "PEER", "Set2-Case2a1", "config.json");
		 Config c = load(path);
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
