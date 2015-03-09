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
import java.util.Map;
import java.util.Map.Entry;

import org.opensha.gmm.Imt;

import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Calculation configuration.
 * @author Peter Powers
 */
final class Config {

	public final GaussTruncation truncationType;
	public final double truncationLevel;
	public final IntensityMeasureLimit maxIntensityModel;

	public final double[] defaultImls;
	public final Map<Imt, double[]> customImls;

	public final double deagg_rMin;
	public final double deagg_rMax;
	public final double deagg_Δr;

	public final double deagg_mMin;
	public final double deagg_mMax;
	public final double deagg_Δm;

	public final double deagg_εMin;
	public final double deagg_εMax;
	public final double deagg_Δε;

	private Config() {

		/*
		 * Default values. These are initialized here because gson will not
		 * deserialize field initialized final primitives and Strings.
		 */

		truncationType = GaussTruncation.ONE_SIDED;
		truncationLevel = 3.0;
		maxIntensityModel = IntensityMeasureLimit.OFF;

		/* Slightly modified version of NSHM 5Hz curve, size = 20 */
		defaultImls = new double[] { 0.0025, 0.0045, 0.0075, 0.0113, 0.0169, 0.0253, 0.0380, 0.0570, 0.0854, 0.128, 0.192, 0.288, 0.432, 0.649, 0.973, 1.46, 2.19, 3.28, 4.92, 7.38 };
		customImls = Maps.newHashMap();

		deagg_rMin = 0.0;
		deagg_rMax = 100.0;
		deagg_Δr = 10.0;

		deagg_mMin = 5.0;
		deagg_mMax = 7.0;
		deagg_Δm = 0.1;

		deagg_εMin = -3;
		deagg_εMax = 3.0;
		deagg_Δε = 0.5;

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
				
		return new StringBuilder("Calculation config:").append(NEWLINE)
			.append("      Curve truncation: ")
			.append("type=").append(truncationType).append(", ")
			.append("level=").append(truncationLevel)
			.append(NEWLINE)
			.append("     Maximum IML model: ")
			.append(maxIntensityModel)
			.append(NEWLINE)
			.append("          Default IMLs: ")
			.append(Arrays.toString(defaultImls))
			.append(NEWLINE)
			.append(customImlStr)
			.append("       Deaggregation R: ")
			.append("min=").append(deagg_rMin).append(", ")
			.append("max=").append(deagg_rMax).append(", ")
			.append("Δ=").append(deagg_Δr)
			.append(NEWLINE)
			.append("       Deaggregation M: ")
			.append("min=").append(deagg_mMin).append(", ")
			.append("max=").append(deagg_mMax).append(", ")
			.append("Δ=").append(deagg_Δm)
			.append(NEWLINE)
			.append("       Deaggregation ε: ")
			.append("min=").append(deagg_εMin).append(", ")
			.append("max=").append(deagg_εMax).append(", ")
			.append("Δ=").append(deagg_Δε)
			.append(NEWLINE)
			.toString();
		// @formatter:on
	}

	double[] imlsForImt(Imt imt) {
		return customImls.containsKey(imt) ? customImls.get(imt) : defaultImls;
	}

	static Config load(Path path) throws IOException {
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		Reader reader = Files.newBufferedReader(path, UTF_8);
		Config config = gson.fromJson(reader, Config.class);
		reader.close();
		return config;
	}

	static void exportDefaults(Path path) throws IOException {
		Writer writer = Files.newBufferedWriter(path, UTF_8);
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		gson.toJson(new Config(), writer);
		writer.close();
	}

	// TODO clean
	public static void main(String[] args) throws IOException {
		// Path path = Paths.get("tmp", "config", "config_calc.json");
		// exportDefaults(path);

		Path path = Paths.get("tmp", "config", "config_nshm_imls.json");
		Config c = load(path);
		System.out.println(c);
	}

}
