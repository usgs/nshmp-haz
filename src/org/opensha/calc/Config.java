package org.opensha.calc;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.opensha.util.TextUtils.NEWLINE;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

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
		return new StringBuilder("Calculation config:").append(NEWLINE)
			.append("  Hazard curve truncation: [ ")
			.append("type=").append(truncationType).append(", ")
			.append("level=").append(truncationLevel).append(" ]")
			.append(NEWLINE)
			.append("          Deaggregation R: [ ")
			.append("min=").append(deagg_rMin).append(", ")
			.append("max=").append(deagg_rMax).append(", ")
			.append("Δ=").append(deagg_Δr).append(" ]")
			.append(NEWLINE)
			.append("          Deaggregation M: [ ")
			.append("min=").append(deagg_mMin).append(", ")
			.append("max=").append(deagg_mMax).append(", ")
			.append("Δ=").append(deagg_Δm).append(" ]")
			.append(NEWLINE)
			.append("          Deaggregation ε: [ ")
			.append("min=").append(deagg_εMin).append(", ")
			.append("max=").append(deagg_εMax).append(", ")
			.append("Δ=").append(deagg_Δε).append(" ]")
			.append(NEWLINE)
			.toString();
		// @formatter:on
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

}
