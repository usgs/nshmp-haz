package org.opensha2.calc;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Strings.repeat;
import static org.opensha2.geo.BorderType.MERCATOR_LINEAR;
import static org.opensha2.util.GeoJson.validateProperty;
import static org.opensha2.util.TextUtils.ALIGN_COL;
import static org.opensha2.util.TextUtils.format;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.opensha2.calc.Site.Builder;
import org.opensha2.geo.Bounds;
import org.opensha2.geo.GriddedRegion;
import org.opensha2.geo.Location;
import org.opensha2.geo.LocationList;
import org.opensha2.geo.Region;
import org.opensha2.geo.Regions;
import org.opensha2.util.GeoJson;
import org.opensha2.util.Parsing;
import org.opensha2.util.Parsing.Delimiter;
import org.opensha2.util.TextUtils;

import com.google.common.base.Optional;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import com.google.common.reflect.TypeToken;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * A {@code SiteSet} is an Iterable over a group of {@code Site}s. The supplied
 * {@code Site}s may be defined internally by a region with common properties or
 * a list of individual sites. Any {@code iterator} returned by this class is
 * unmodifiable.
 *
 * @author Peter Powers
 */
public final class SiteSet implements Iterable<Site> {

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

	int size() {
		return (region == null) ? sites.size() : region.size();
	}

	@Override
	public Iterator<Site> iterator() {
		return (region == null) ? Iterators.unmodifiableIterator(sites.iterator())
			: new RegionIterator();
	}

	private final class RegionIterator implements Iterator<Site> {

		private final Iterator<Location> locations;

		private RegionIterator() {
			locations = region.iterator();
		}

		@Override
		public boolean hasNext() {
			return locations.hasNext();
		}

		@Override
		public Site next() {
			builder.location(locations.next());
			return builder.build();
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException();
		}
	}

	private static final int TO_STRING_LIMIT = 5;

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder()
			.append((region == null) ? "List" : "Region")
			.append(" [size=").append(size()).append("]");
		if (region == null) {
			for (Site site : Iterables.limit(sites, TO_STRING_LIMIT)) {
				sb.append(format("Site")).append(site);
			}
			if (sites.size() > TO_STRING_LIMIT) {
				int delta = sites.size() - TO_STRING_LIMIT;
				sb.append(TextUtils.NEWLINE)
					.append(repeat(" ", ALIGN_COL + 2))
					.append("... and ").append(delta).append(" more ...");
			}
		}
		return sb.toString();
	}

	/**
	 * Creates an {@code Iterable<Site>} from the comma-delimted site file
	 * designated by {@code path}.
	 * 
	 * @param path to comma-delimited site data file
	 * @throws IOException if a problem is encountered
	 */
	public static Iterable<Site> fromCsv(Path path) throws IOException {
		return processCsv(path);
	}

	private static SiteSet processCsv(Path path) throws IOException {
		checkNotNull(path);

		List<Site> siteList = new ArrayList<>();
		Builder builder = Site.builder();

		List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
		boolean firstline = true;
		List<String> keyList = new ArrayList<>();
		for (String line : lines) {

			// skip comments
			if (line.startsWith("#")) continue;
			if (line.trim().isEmpty()) continue;

			List<String> values = Parsing.splitToList(line, Delimiter.COMMA);

			// set up key/column ordering
			if (firstline) {
				for (String key : values) {
					checkState(Site.KEYS.contains(key), "Illegal site property key [%s]", key);
					keyList.add(key);
				}
				checkState(keyList.contains(Site.Key.LAT), "Site latitudes must be defined");
				checkState(keyList.contains(Site.Key.LON), "Site longitudes must be defined");
				firstline = false;
				continue;
			}

			int index = 0;
			double lat = 0.0;
			double lon = 0.0;
			for (String key : keyList) {
				String value = values.get(index);
				switch (key) {
					case Site.Key.LAT:
						lat = Double.parseDouble(value);
						break;
					case Site.Key.LON:
						lon = Double.parseDouble(value);
						break;
					case Site.Key.NAME:
						builder.name(value);
						break;
					case Site.Key.VS30:
						builder.vs30(Double.parseDouble(value));
						break;
					case Site.Key.VS_INF:
						builder.vsInferred(Boolean.parseBoolean(value));
						break;
					case Site.Key.Z1P0:
						builder.z1p0(Double.parseDouble(value));
						break;
					case Site.Key.Z2P5:
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

	/*
	 * Site GeoJSON is currently deserialized with the expectation that the
	 * feature array will be strictly of type:Point or type:Polygon. Polygon
	 * feature arrays are assumed to have only 1 or 2 elements. In the event
	 * that there are 2, the first one is expected to have id:CLIP, define four
	 * corners, and be rectangular (in a mercator project) with edges parallel
	 * to lines of latitude and longitude. Polygon holes, if present are not
	 * processed.
	 */
	static class Deserializer implements JsonDeserializer<SiteSet> {

		@Override
		public SiteSet deserialize(
				JsonElement json,
				Type type,
				JsonDeserializationContext context) {

			// should always have a features array
			JsonArray features = json.getAsJsonObject()
				.get(GeoJson.Key.FEATURES)
				.getAsJsonArray();
			checkState(features.size() > 0, "Feature array is empty");

			// check if we have a site list
			String featureType = features.get(0).getAsJsonObject()
				.get(GeoJson.Key.GEOMETRY).getAsJsonObject()
				.get(GeoJson.Key.TYPE).getAsString();
			if (featureType.equals(GeoJson.Value.POINT)) {
				Type siteType = new TypeToken<List<Site>>() {}.getType();
				List<Site> sites = context.deserialize(json, siteType);
				return new SiteSet(sites);
			}

			// or a region
			checkState(features.size() <= 2, "Only 2 polygon features may be defined");

			Optional<Region> extents = Optional.absent();
			if (features.size() == 2) {

				JsonObject extentsFeature = features.get(0).getAsJsonObject();
				validateProperty(extentsFeature, GeoJson.Key.ID, GeoJson.Value.EXTENTS);

				Bounds bounds = validateExtents(readPolygon(extentsFeature)).bounds();

				JsonObject properties = extentsFeature.getAsJsonObject(GeoJson.Key.PROPERTIES);
				String extentsName = readName(properties, "Map Extents");

				Region r = Regions.createRectangular(extentsName, bounds.min(), bounds.max());
				extents = Optional.of(r);
			}

			JsonObject sitesFeature = features.get(1).getAsJsonObject();
			LocationList border = readPolygon(sitesFeature);

			JsonObject properties = sitesFeature.getAsJsonObject(GeoJson.Key.PROPERTIES);
			String mapName = readName(properties, "Unnamed Map");

			Region calcRegion = Regions.create(mapName, border, MERCATOR_LINEAR);
			double spacing = properties.get(GeoJson.Properties.Key.SPACING).getAsDouble();

			// builder used to create all sites when iterating over region
			Builder builder = Site.builder();

			if (properties.has(Site.Key.VS30)) {
				double vs30 = properties.get(Site.Key.VS30).getAsDouble();
				builder.vs30(vs30);
			}

			if (properties.has(Site.Key.VS_INF)) {
				boolean vsInf = properties.get(Site.Key.VS_INF).getAsBoolean();
				builder.vsInferred(vsInf);
			}

			if (properties.has(Site.Key.Z1P0)) {
				double z1p0 = properties.get(Site.Key.Z1P0).getAsDouble();
				builder.z1p0(z1p0);
			}

			if (properties.has(Site.Key.Z2P5)) {
				double z2p5 = properties.get(Site.Key.Z2P5).getAsDouble();
				builder.z2p5(z2p5);
			}

			Region mapRegion = extents.isPresent()
				? Regions.intersectionOf(mapName, extents.get(), calcRegion)
				: calcRegion;

			GriddedRegion region = Regions.toGridded(
				mapRegion,
				spacing, spacing,
				GriddedRegion.ANCHOR_0_0);

			return new SiteSet(region, builder);
		}
	}

	private static LocationList readPolygon(JsonObject feature) {
		JsonObject geometry = feature.getAsJsonObject(GeoJson.Key.GEOMETRY);
		validateProperty(geometry, GeoJson.Key.TYPE, GeoJson.Value.POLYGON);
		JsonArray coords = geometry.getAsJsonArray(GeoJson.Key.COORDINATES);
		LocationList border = GeoJson.fromCoordinates(coords);

		checkState(
			border.size() > 2,
			"A GeoJSON polygon must have at least 3 coordinates:%s",
			border);

		checkState(
			border.first().equals(border.last()),
			"The first and last points in a GeoJSON polygon must be the same:%s",
			border);

		return border;
	}

	private static String readName(JsonObject properties, String defaultName) {
		return properties.has(GeoJson.Properties.Key.TITLE)
			? properties.get(GeoJson.Properties.Key.TITLE).getAsString() : defaultName;
	}

	private static LocationList validateExtents(LocationList locs) {
		checkState(locs.size() == 5,
			"Extents polygon must contain 5 coordinates:%s", locs);
		Location p1 = locs.get(0);
		Location p2 = locs.get(1);
		Location p3 = locs.get(2);
		Location p4 = locs.get(3);
		boolean rectangular = (p1.latRad() == p2.latRad())
			? (p3.latRad() == p4.latRad() &&
				p1.lonRad() == p4.lonRad() &&
				p2.lonRad() == p3.lonRad())
			: (p1.latRad() == p4.latRad() &&
				p2.latRad() == p3.latRad() &&
				p1.lonRad() == p2.lonRad() &&
				p3.lonRad() == p4.lonRad());
		checkState(rectangular,
			"Extents polygon does not define a lat-lon Mercator rectangle:%s", locs);
		return locs;
	}
}
