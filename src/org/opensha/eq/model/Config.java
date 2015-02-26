package org.opensha.eq.model;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.opensha.util.TextUtils.NEWLINE;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

import org.opensha.calc.GaussTruncation;
import org.opensha.eq.fault.surface.RuptureFloating;
import org.opensha.eq.model.AreaSource.GridScaling;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Model and calculation configuration class.
 *
 * @author Peter Powers
 */
final class Config {
	
	public final double surface_gridUnit;
	public final RuptureFloating surface_floatingModel;

	public final AreaSource.GridScaling source_areaScaling;

	private Config() {

		/*
		 * Default values. These are initialized here because gson will not
		 * deserialize field initialized final primitives and Strings.
		 */
		
		surface_gridUnit = 1.0;
		surface_floatingModel = RuptureFloating.STRIKE_ONLY;
		
		source_areaScaling = GridScaling.SCALED_SMALL;
	}

	@Override public String toString() {
		// @formatter:off
		return new StringBuilder("Model configuration:").append(NEWLINE)
			.append("       surface_gridUnit: ").append(surface_gridUnit).append(NEWLINE)
			.append("  surface_floatingModel: ").append(surface_floatingModel).append(NEWLINE)
			.append("     source_areaScaling: ").append(source_areaScaling).append(NEWLINE)
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

