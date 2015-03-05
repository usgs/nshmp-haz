package org.opensha.eq.model;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.opensha.util.TextUtils.NEWLINE;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

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
	
	static final String FILE_NAME = "config.json";

	public final double surfaceSpacing;
	public final RuptureFloating ruptureFloating;

	public final PointSourceType pointSourceType;
	public final AreaSource.GridScaling areaGridScaling;

	private Config() {

		/*
		 * Default values. These are initialized here because gson will not
		 * deserialize field initialized final primitives and Strings.
		 */
		
		surfaceSpacing = 1.0;
		ruptureFloating = RuptureFloating.STRIKE_ONLY;
		
		pointSourceType = PointSourceType.FINITE;
		areaGridScaling = GridScaling.SCALED_SMALL;
	}

	@Override public String toString() {
		// @formatter:off
		return new StringBuilder("Source config:").append(NEWLINE)
			.append("       surfaceSpacing: ").append(surfaceSpacing).append(NEWLINE)
			.append("      ruptureFloating: ").append(ruptureFloating).append(NEWLINE)
			.append("      pointSourceType: ").append(pointSourceType).append(NEWLINE)
			.append("      areaGridScaling: ").append(areaGridScaling).toString();
		// @formatter:on
	}

	static Config load(Path path) throws IOException {
		Path configFile = path.resolve(FILE_NAME);
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		Reader reader = Files.newBufferedReader(configFile, UTF_8);
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

