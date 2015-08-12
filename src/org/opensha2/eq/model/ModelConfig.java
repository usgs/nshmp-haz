package org.opensha2.eq.model;

import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_UNDERSCORE;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Strings.padStart;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.opensha2.util.TextUtils.ALIGN_COL;
import static org.opensha2.util.TextUtils.NEWLINE;
import static org.opensha2.util.TextUtils.format;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;

import org.opensha2.eq.fault.surface.RuptureFloating;
import org.opensha2.eq.model.AreaSource.GridScaling;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Model and calculation configuration class. No defaults; 'config.json' must be
 * supplied with model.
 *
 * @author Peter Powers
 */
final class ModelConfig {

	static final String FILE_NAME = "config.json";

	private static final Gson GSON = new GsonBuilder().create();

	private final Path resource;

	public final String name;
	public final double surfaceSpacing;
	public final RuptureFloating ruptureFloating;
	public final boolean ruptureVariability;
	public final PointSourceType pointSourceType;
	public final GridScaling areaGridScaling;

	private ModelConfig(
			String name,
			Path resource,
			double surfaceSpacing,
			RuptureFloating ruptureFloating,
			boolean ruptureVariability,
			PointSourceType pointSourceType,
			GridScaling areaGridScaling) {

		this.name = name;
		this.resource = resource;
		this.surfaceSpacing = surfaceSpacing;
		this.ruptureFloating = ruptureFloating;
		this.ruptureVariability = ruptureVariability;
		this.pointSourceType = pointSourceType;
		this.areaGridScaling = areaGridScaling;
	}

	private enum Key {
		NAME,
		RESOURCE,
		SURFACE_SPACING,
		RUPTURE_FLOATING,
		RUPTURE_VARIABILITY,
		POINT_SOURCE_TYPE,
		AREA_GRID_SCALING;

		private String label;

		private Key() {
			this.label = UPPER_UNDERSCORE.to(LOWER_CAMEL, name());
		}

		@Override public String toString() {
			return label;
		}
	}

	@Override public String toString() {
		return new StringBuilder("Model config:")
			.append(format(Key.NAME)).append(name)
			.append(format(Key.RESOURCE)).append(resource)
			.append(format(Key.SURFACE_SPACING)).append(surfaceSpacing)
			.append(format(Key.RUPTURE_FLOATING)).append(ruptureFloating)
			.append(format(Key.RUPTURE_VARIABILITY)).append(ruptureVariability)
			.append(format(Key.POINT_SOURCE_TYPE)).append(pointSourceType)
			.append(format(Key.AREA_GRID_SCALING)).append(areaGridScaling)
			.toString();
	}

	/**
	 * Create a new model configuration builder from the resource at the
	 * specified {@code path}.
	 * 
	 * @param path to configuration file or resource
	 * @throws IOException
	 */
	static Builder builder(Path path) throws IOException {
		// TODO test with zip files
		checkNotNull(path);
		Path configPath = Files.isDirectory(path) ? path.resolve(FILE_NAME) : path;
		Reader reader = Files.newBufferedReader(configPath, UTF_8);
		Builder configBuilder = GSON.fromJson(reader, Builder.class);
		configBuilder.resource = configPath;
		reader.close();
		return configBuilder;
	}

	/**
	 * Create a new empty model configuration builder.
	 */
	static Builder builder() {
		return new Builder();
	}

	final static class Builder {

		private static final String ID = "ModelConfig.Builder";
		private boolean built = false;

		private String name;
		private Path resource;
		private Double surfaceSpacing;
		private RuptureFloating ruptureFloating;
		private Boolean ruptureVariability;
		private PointSourceType pointSourceType;
		private GridScaling areaGridScaling;

		Builder copy(ModelConfig config) {
			checkNotNull(config);
			this.name = config.name;
			this.resource = config.resource;
			this.surfaceSpacing = config.surfaceSpacing;
			this.ruptureFloating = config.ruptureFloating;
			this.ruptureVariability = config.ruptureVariability;
			this.pointSourceType = config.pointSourceType;
			this.areaGridScaling = config.areaGridScaling;
			return this;
		}

		Builder extend(Builder that) {
			checkNotNull(that);
			// name can't be overridden
			if (that.resource != null) this.resource = that.resource;
			if (that.surfaceSpacing != null) this.surfaceSpacing = that.surfaceSpacing;
			if (that.ruptureFloating != null) this.ruptureFloating = that.ruptureFloating;
			if (that.ruptureVariability != null) this.ruptureVariability = that.ruptureVariability;
			if (that.pointSourceType != null) this.pointSourceType = that.pointSourceType;
			if (that.areaGridScaling != null) this.areaGridScaling = that.areaGridScaling;
			return this;
		}

		private void validateState(String buildId) {
			checkState(!built, "This %s instance as already been used", buildId);
			checkNotNull(name, "%s %s not set", buildId, Key.NAME);
			checkNotNull(resource, "%s %s not set", buildId, Key.RESOURCE);
			checkNotNull(surfaceSpacing, "%s %s not set", buildId, Key.SURFACE_SPACING);
			checkNotNull(ruptureFloating, "%s %s not set", buildId, Key.RUPTURE_FLOATING);
			checkNotNull(ruptureVariability, "%s %s not set", buildId, Key.RUPTURE_VARIABILITY);
			checkNotNull(pointSourceType, "%s %s not set", buildId, Key.POINT_SOURCE_TYPE);
			checkNotNull(areaGridScaling, "%s %s not set", buildId, Key.AREA_GRID_SCALING);
			built = true;
		}

		ModelConfig build() {
			validateState(ID);
			return new ModelConfig(
				name, resource, surfaceSpacing, ruptureFloating,
				ruptureVariability, pointSourceType, areaGridScaling);
		}
	}

}
