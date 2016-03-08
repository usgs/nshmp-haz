package org.opensha2.util;

import static org.opensha2.util.MathUtils.round;
import static com.google.common.base.Strings.*;

import static org.opensha2.util.NshmpPolygon.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.opensha2.calc.NamedLocation;
import org.opensha2.geo.Location;
import org.opensha2.geo.LocationList;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Strings;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;

/**
 * Methods for exporting data as TXT, JSON, KML, CSV, etc.
 *
 * @author Peter Powers
 */
final class Export {

	private static final Gson GSON = new GsonBuilder()
		.setPrettyPrinting()
		.disableHtmlEscaping()
		.create();

	private static final Path EXPORT_DIR = Paths.get("etc", "nshm");

	public static void main(String[] args) throws IOException {
		 writeNshmpSites();
		writeNshmpPolys();
		writeNshmpSummaryPoly();
	}

	/*
	 * Currently, we're exporting map regions as polygons. Although the GeoJSON
	 * spec supports polygons with holes (and hence 3-dimensional arrays, we
	 * only support singular polygons. Polygons render better than PointStrings
	 * in any event.
	 */
	static void writeNshmpPolys() throws IOException {
		LocationList usCoords = CONTERMINOUS_US.coordinates();

		Path ceusOut = EXPORT_DIR.resolve("map-ceus.geojson");
		LocationList ceusBounds = CEUS_CLIP.coordinates();
		writePolyJson(ceusOut, "NSHMP Central & Eastern US", usCoords, ceusBounds);

		Path wusOut = EXPORT_DIR.resolve("map-wus.geojson");
		LocationList wusBounds = WUS_CLIP.coordinates();
		writePolyJson(wusOut, "NSHMP Western US", usCoords, wusBounds);

		writePolyJson(
			EXPORT_DIR.resolve("map-la-basin.geojson"),
			LA_BASIN.toString(),
			LA_BASIN.coordinates(),
			null);

		writePolyJson(
			EXPORT_DIR.resolve("map-sf-bay.geojson"),
			SF_BAY.toString(),
			SF_BAY.coordinates(),
			null);

		writePolyJson(
			EXPORT_DIR.resolve("map-puget.geojson"),
			PUGET.toString(),
			PUGET.coordinates(),
			null);

		writePolyJson(
			EXPORT_DIR.resolve("map-wasatch.geojson"),
			WASATCH.toString(),
			WASATCH.coordinates(),
			null);

		writePolyJson(
			EXPORT_DIR.resolve("map-new-madrid.geojson"),
			NEW_MADRID.toString(),
			NEW_MADRID.coordinates(),
			null);

		writePolyJson(
			EXPORT_DIR.resolve("map-ucerf3-nshm.geojson"),
			UCERF3_NSHM14.toString(),
			UCERF3_NSHM14.coordinates(),
			null);

		writePolyJson(
			EXPORT_DIR.resolve("map-ucerf3-relm.geojson"),
			UCERF3_RELM.toString(),
			UCERF3_RELM.coordinates(),
			null);

		writePolyJson(
			EXPORT_DIR.resolve("map-cybershake.geojson"),
			CYBERSHAKE.toString(),
			CYBERSHAKE.coordinates(),
			null);

	}

	static void writeNshmpSummaryPoly() throws IOException {
		Set<NshmpPolygon> polys = EnumSet.range(LA_BASIN, UCERF3_NSHM14);
		writePolysJson(
			EXPORT_DIR.resolve("map-nshmp-all.geojson"),
			FluentIterable.from(polys)
				.transform(Functions.toStringFunction())
				.toList(),
			FluentIterable.from(polys)
				.transform(new Function<NshmpPolygon, LocationList>() {
					@Override
					public LocationList apply(NshmpPolygon poly) {
						return poly.coordinates();
					}
				})
				.toList());
	}

	static void writePolyJson(Path out, String name, LocationList coords, LocationList bounds)
			throws IOException {
		List<Feature> features = new ArrayList<>();
		if (bounds != null) {
			features.add(createPolygon(name, "CLIP", bounds));
		}
		features.add(createPolygon(name, null, coords));
		FeatureCollection fc = new FeatureCollection();
		fc.features = features;
		String json = cleanPoly(GSON.toJson(fc));
		Files.write(out, json.getBytes(StandardCharsets.UTF_8));
	}

	static void writePolysJson(Path out, List<String> nameList, List<LocationList> coordList)
			throws IOException {
		List<Feature> features = new ArrayList<>();
		int i = 0;
		for (LocationList coords : coordList) {
			features.add(createPolygon(nameList.get(i++), null, coords));
		}
		FeatureCollection fc = new FeatureCollection();
		fc.features = features;
		String json = cleanPoly(GSON.toJson(fc));
		Files.write(out, json.getBytes(StandardCharsets.UTF_8));
	}

	static void writeNshmpSites() throws IOException {
		writeSites("nshmp", EnumSet.allOf(NshmpSite.class));
		writeSites("ceus", NshmpSite.ceus());
		writeSites("wus", NshmpSite.wus());
	}

	static void writeSites(String name, Collection<? extends NamedLocation> sites)
			throws IOException {
		Path jsonOut = EXPORT_DIR.resolve("sites-" + name + ".geojson");
		writeJsonSites(jsonOut, sites);
		Path csvOut = EXPORT_DIR.resolve("sites-" + name + ".csv");
		writeCsvSites(csvOut, sites);
	}

	private static final DecimalFormat FMT = new DecimalFormat("0.00");
	private static final int NAME_BUFF = 28;
	private static final int LON_BUFF = 8;
	private static final int LAT_BUFF = 7;

	private static void writeCsvSites(Path out, Collection<? extends NamedLocation> locs)
			throws IOException {
		Iterable<String> lines = Iterables.transform(locs, new Function<NamedLocation, String>() {
			@Override
			public String apply(NamedLocation loc) {
				StringBuilder sb = new StringBuilder();
				sb.append(padEnd(loc.toString() + ",", NAME_BUFF, ' '));
				sb.append(padStart(FMT.format(loc.location().lon()), LON_BUFF, ' '));
				sb.append(',');
				sb.append(padStart(FMT.format(loc.location().lat()), LAT_BUFF, ' '));
				return sb.toString();
			}
		});
		String header = new StringBuilder(padEnd("name,", NAME_BUFF, ' '))
			.append(padStart("lon", LON_BUFF, ' '))
			.append(',')
			.append(padStart("lat", LAT_BUFF, ' '))
			.toString();
		Files.write(
			out,
			Iterables.concat(ImmutableList.of(header), lines),
			StandardCharsets.UTF_8);
	}

	private static void writeJsonSites(Path out, Collection<? extends NamedLocation> sites)
			throws IOException {

		List<Feature> features = new ArrayList<>(sites.size());
		for (NamedLocation loc : sites) {
			features.add(createPoint(loc));
		}
		FeatureCollection fc = new FeatureCollection();
		fc.features = features;
		String json = cleanPoints(GSON.toJson(fc));
		Files.write(out, json.getBytes(StandardCharsets.UTF_8));
	}

	/* GeoJSON objects */

	static class FeatureCollection {
		String type = "FeatureCollection";
		List<Feature> features;
	}

	static class Feature {
		String type = "Feature";
		String id;
		Geometry geometry = new Geometry();
		Properties properties;
	}

	static Feature createPoint(NamedLocation loc) {
		Feature f = new Feature();
		f.geometry.type = "Point";
		f.geometry.coordinates = toCoordinates(loc.location());
		f.properties = new PointProperties();
		f.properties.title = loc.toString();
		return f;
	}

	static Feature createPolygon(String name, String id, LocationList coords) {
		Feature f = new Feature();
		if (id != null) {
			f.id = id;
			coords = coords.bounds().toList();
		}
		f.geometry.type = "Polygon";
		f.geometry.coordinates = ImmutableList.of(toCoordinates(coords));
		f.properties = new PolyProperties();
		f.properties.title = name;
		return f;
	}

	static class Geometry {
		String type;
		Object coordinates;
	}

	static class Properties {
		String title;
	}

	static class PointProperties extends Properties {
		@SerializedName("marker-size")
		String markerSize = "small";
	}

	static class PolyProperties extends Properties {}

	static double[] toCoordinates(Location loc) {
		return new double[] { round(loc.lon(), 5), round(loc.lat(), 5) };
	}

	static double[][] toCoordinates(LocationList locs) {
		double[][] coords = new double[locs.size()][2];
		for (int i = 0; i < locs.size(); i++) {
			coords[i] = toCoordinates(locs.get(i));
		}
		return coords;
	}

	/* brute force compaction of coordinate array onto single line */
	static String cleanPoints(String s) {
		return s.replace(": [\n          ", ": [")
			.replace(",\n          ", ", ")
			.replace("\n        ]", "]") + "\n";
	}

	/* brute force compaction of coordinate array onto single line */
	static String cleanPoly(String s) {
		return s
			.replace("\n          [", "[")
			.replace("[\n              ", "[ ")
			.replace(",\n              ", ", ")
			.replace("\n            ]", " ]")
			.replace("\n        ]", "]") + "\n";
	}

}
