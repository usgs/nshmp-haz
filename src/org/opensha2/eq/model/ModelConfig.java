package org.opensha2.eq.model;

import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_UNDERSCORE;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Strings.padEnd;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.opensha2.util.TextUtils.LOG_INDENT;
import static org.opensha2.util.TextUtils.LOG_VALUE_COLUMN;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;

import org.opensha2.eq.fault.surface.RuptureFloating;
import org.opensha2.eq.model.AreaSource.GridScaling;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

/**
 * Model and calculation configuration class. No defaults; 'config.json' must be
 * supplied with model.
 *
 * @author Peter Powers
 */
final class ModelConfig {

  static final String FILE_NAME = "config.json";
  private static final String ID = ModelConfig.class.getSimpleName();
  private static final String STATE_ERROR = "%s %s not set";
  private static final String ELEMENT_NAME = "model";

  private static final Gson GSON = new GsonBuilder().create();

  private final Path resource;

  final String name;
  final double surfaceSpacing;
  final RuptureFloating ruptureFloating;
  final boolean ruptureVariability;
  final PointSourceType pointSourceType;
  final GridScaling areaGridScaling;

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

    @Override
    public String toString() {
      return label;
    }
  }

  @Override
  public String toString() {
    return new StringBuilder("Model Config: ")
      .append(resource.toAbsolutePath().normalize())
      .append(LOG_INDENT).append("Model")
      .append(formatEntry(Key.NAME, name))
      .append(formatEntry(Key.SURFACE_SPACING, surfaceSpacing))
      .append(formatEntry(Key.RUPTURE_FLOATING, ruptureFloating))
      .append(formatEntry(Key.RUPTURE_VARIABILITY, ruptureVariability))
      .append(formatEntry(Key.POINT_SOURCE_TYPE, pointSourceType))
      .append(formatEntry(Key.AREA_GRID_SCALING, areaGridScaling))
      .toString();
  }

  private static final String KEY_INDENT = LOG_INDENT + "  ";

  private static <E extends Enum<E>> String formatEntry(E key, Object value) {
    return padEnd(KEY_INDENT + '.' + key + ':', LOG_VALUE_COLUMN, ' ') + value;
  }

  final static class Builder {

    private boolean built = false;

    private String name;
    private Path resource;
    private Double surfaceSpacing;
    private RuptureFloating ruptureFloating;
    private Boolean ruptureVariability;
    private PointSourceType pointSourceType;
    private GridScaling areaGridScaling;

    static Builder copyOf(ModelConfig that) {
      checkNotNull(that);
      Builder b = new Builder();
      b.name = that.name;
      b.resource = that.resource;
      b.surfaceSpacing = that.surfaceSpacing;
      b.ruptureFloating = that.ruptureFloating;
      b.ruptureVariability = that.ruptureVariability;
      b.pointSourceType = that.pointSourceType;
      b.areaGridScaling = that.areaGridScaling;
      return b;
    }

    static Builder fromFile(Path path) throws IOException {
      // TODO test with zip files
      checkNotNull(path);
      Path configPath = Files.isDirectory(path) ? path.resolve(FILE_NAME) : path;
      Reader reader = Files.newBufferedReader(configPath, UTF_8);
      JsonElement modelRoot = new JsonParser()
        .parse(reader)
        .getAsJsonObject()
        .get(ELEMENT_NAME);
      Builder configBuilder = GSON.fromJson(
        checkNotNull(
          modelRoot,
          "'%s' element is missing from root of config: %s",
          ELEMENT_NAME,
          configPath),
        Builder.class);
      configBuilder.resource = configPath;
      reader.close();
      return configBuilder;
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

    private void validateState() {
      checkState(!built, "This %s instance has already been used", ID + ".Builder");
      checkNotNull(name, STATE_ERROR, ID, Key.NAME);
      checkNotNull(resource, STATE_ERROR, ID, Key.RESOURCE);
      checkNotNull(surfaceSpacing, STATE_ERROR, ID, Key.SURFACE_SPACING);
      checkNotNull(ruptureFloating, STATE_ERROR, ID, Key.RUPTURE_FLOATING);
      checkNotNull(ruptureVariability, STATE_ERROR, ID, Key.RUPTURE_VARIABILITY);
      checkNotNull(pointSourceType, STATE_ERROR, ID, Key.POINT_SOURCE_TYPE);
      checkNotNull(areaGridScaling, STATE_ERROR, ID, Key.AREA_GRID_SCALING);
      built = true;
    }

    ModelConfig build() {
      validateState();
      return new ModelConfig(
        name, resource, surfaceSpacing, ruptureFloating,
        ruptureVariability, pointSourceType, areaGridScaling);
    }
  }

}
