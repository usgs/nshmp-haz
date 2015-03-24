package org.opensha.calc;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.opensha.calc.Site.Key.LAT;
import static org.opensha.calc.Site.Key.LON;
import static org.opensha.calc.Site.Key.NAME;
import static org.opensha.calc.Site.Key.VS30;
import static org.opensha.calc.Site.Key.VS_INF;
import static org.opensha.calc.Site.Key.Z1P0;
import static org.opensha.calc.Site.Key.Z2P5;
import static org.opensha.geo.BorderType.MERCATOR_LINEAR;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.opensha.calc.Site.Builder;
import org.opensha.calc.Site.Key;
import org.opensha.geo.GriddedRegion;
import org.opensha.geo.Location;
import org.opensha.geo.LocationList;
import org.opensha.geo.Regions;
import org.opensha.util.Parsing;
import org.opensha.util.Parsing.Delimiter;

import com.google.common.reflect.TypeToken;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.Expose;

/**
 * A {@code SiteSet} is an Iterable over a group of {@code Site}s. The supplied
 * {@code Site}s may be defined internally by a region with common properties or
 * a list of individual sites.
 *
 * @author Peter Powers
 */
public final class SiteSet implements Iterable<Site> {

	@Expose(deserialize = false)
	final private GriddedRegion region;
	final private Builder builder;
	final private List<Site> sites;

	SiteSet(List<Site> sites) {
		this.sites = checkNotNull(sites);
		this.region = null;
		this.builder = null;
	}

	SiteSet(GriddedRegion region, Builder builder) {
		this.region = checkNotNull(region);
		this.builder = checkNotNull(builder);
		this.sites = null;
	}

	@Override public Iterator<Site> iterator() {
		return (region == null) ? sites.iterator() : new RegionIterator();
	}

	private final class RegionIterator implements Iterator<Site> {

		private final Iterator<Location> locations;

		private RegionIterator() {
			locations = region.iterator();
		}

		@Override public boolean hasNext() {
			return locations.hasNext();
		}

		@Override public Site next() {
			builder.location(locations.next());
			return builder.build();
		}

		@Override public void remove() {
			throw new UnsupportedOperationException();
		}
	}

	static SiteSet loadCsvSites(Path csvPath) throws IOException {
		checkNotNull(csvPath);

		List<Site> siteList = new ArrayList<>();
		Builder builder = Site.builder();

		List<String> lines = Files.readAllLines(csvPath, StandardCharsets.UTF_8);
		boolean firstline = true;
		List<Key> keyList = new ArrayList<>();
		for (String line : lines) {

			// skip comments
			if (line.startsWith("#")) continue;

			List<String> values = Parsing.splitToList(line, Delimiter.COMMA);

			// set up key/column ordering
			if (firstline) {
				Set<Key> keys = EnumSet.of(NAME, LAT, LON, VS30, VS_INF, Z1P0, Z2P5);
				for (String keyStr : values) {
					Key key = Key.fromString(keyStr);
					checkState(keys.contains(key), "Illegal site property key [%s]", keyStr);
					keyList.add(key);
				}
				checkState(keyList.contains(LAT), "Site latitudes must be defined");
				checkState(keyList.contains(LON), "Site longitudes must be defined");
				continue;
			}

			int index = 0;
			double lat = 0.0;
			double lon = 0.0;
			for (Key key : keyList) {
				String value = values.get(index);
				switch (key) {
					case LAT:
						lat = Double.parseDouble(value);
						break;
					case LON:
						lon = Double.parseDouble(value);
						break;
					case NAME:
						builder.name(value);
						break;
					case VS30:
						builder.vs30(Double.parseDouble(value));
						break;
					case VS_INF:
						builder.vsInferred(Boolean.parseBoolean(value));
						break;
					case Z1P0:
						builder.z1p0(Double.parseDouble(value));
						break;
					case Z2P5:
						builder.z2p5(Double.parseDouble(value));
						break;
					default:
						throw new IllegalStateException("Unsupported site key: " + key);
				}
				index++;
			}
			builder.location(lat, lon);
			siteList.add(builder.build());
		}
		return new SiteSet(siteList);
	}

	static final String SITES = "sites";
	static final String REGION = "region";
	static final String BORDER = "border";
	static final String SPACING = "spacing";

	static class Deserializer implements JsonDeserializer<SiteSet> {

		@Override public SiteSet deserialize(JsonElement json, Type typeOfT,
				JsonDeserializationContext context) throws JsonParseException {

			Builder builder = Site.builder();

			// check if we have a site list
			if (json.isJsonArray()) {
				Type type = new TypeToken<List<Site>>(){}.getType();
				List<Site> sites = context.deserialize(json, type);
				return new SiteSet(sites);
			}

			// or a region
			JsonObject jRegion = json.getAsJsonObject().getAsJsonObject(REGION);

			if (jRegion.has(VS30.toString())) {
				double vs30 = jRegion.get(VS30.toString()).getAsDouble();
				builder.vs30(vs30);
			}

			if (jRegion.has(VS_INF.toString())) {
				boolean vsInf = jRegion.get(VS_INF.toString()).getAsBoolean();
				builder.vsInferred(vsInf);
			}

			if (jRegion.has(Z1P0.toString())) {
				double z1p0 = jRegion.get(Z1P0.toString()).getAsDouble();
				builder.z1p0(z1p0);
			}

			if (jRegion.has(Z2P5.toString())) {
				double z2p5 = jRegion.get(Z2P5.toString()).getAsDouble();
				builder.z2p5(z2p5);
			}

			checkState(jRegion.has(BORDER), "Site region must define a border");
			checkState(jRegion.has(SPACING), "Site region must define a spacing");

			String name = "Unnamed region";
			if (jRegion.has(NAME.toString())) {
				name = jRegion.get(NAME.toString()).getAsString();
			}

			double spacing = jRegion.get(SPACING).getAsDouble();

			JsonArray coords = jRegion.getAsJsonArray(BORDER);
			LocationList.Builder borderBuilder = LocationList.builder();
			for (JsonElement jElem : coords) {
				JsonArray coord = jElem.getAsJsonArray();
				borderBuilder.add(
					coord.get(1).getAsDouble(),
					coord.get(0).getAsDouble());
			}
			LocationList border = borderBuilder.build();

			checkState(border.size() >= 2, "Site region border must define at "
				+ "least 2 coordinates");

			GriddedRegion region = (border.size() == 2) ?
				Regions.createRectangularGridded(name, border.get(0),
					border.get(1), spacing, spacing, GriddedRegion.ANCHOR_0_0) :
				Regions.createGridded(name, border, MERCATOR_LINEAR,
					spacing, spacing, GriddedRegion.ANCHOR_0_0);

			return new SiteSet(region, builder);
		}
	}
}
