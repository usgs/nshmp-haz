package org.opensha2.internal;

import static com.google.common.base.Strings.padEnd;
import static com.google.common.base.Strings.padStart;

import static org.opensha2.internal.NshmpPolygon.AK_CLIP;
import static org.opensha2.internal.NshmpPolygon.CEUS_CLIP;
import static org.opensha2.internal.NshmpPolygon.CONTERMINOUS_US;
import static org.opensha2.internal.NshmpPolygon.CYBERSHAKE;
import static org.opensha2.internal.NshmpPolygon.LA_BASIN;
import static org.opensha2.internal.NshmpPolygon.NEW_MADRID;
import static org.opensha2.internal.NshmpPolygon.PUGET;
import static org.opensha2.internal.NshmpPolygon.SF_BAY;
import static org.opensha2.internal.NshmpPolygon.UCERF3_NSHM14;
import static org.opensha2.internal.NshmpPolygon.UCERF3_RELM;
import static org.opensha2.internal.NshmpPolygon.WASATCH;
import static org.opensha2.internal.NshmpPolygon.WUS_CLIP;

import org.opensha2.geo.Location;
import org.opensha2.geo.LocationList;
import org.opensha2.internal.GeoJson.Feature;
import org.opensha2.internal.GeoJson.FeatureCollection;
import org.opensha2.internal.GeoJson.PointProperties;
import org.opensha2.util.NamedLocation;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Methods for exporting nshmp site data as CSV and GeoJSON.
 *
 * @author Peter Powers
 */
final class NshmpSiteFiles {

  private static final Gson GSON = new GsonBuilder()
      .setPrettyPrinting()
      .disableHtmlEscaping()
      .create();

  private static final Path EXPORT_DIR = Paths.get("etc", "nshm");

  /**
   * Updates all site list and map files in etc/nshm.
   */
  public static void main(String[] args) throws IOException {
    writeNshmpSites();
    writeSites("nureg", EnumSet.allOf(NuregSite.class), DEC3_FMT);
    writeCybershakeSites("cybershake", EnumSet.allOf(CybershakeSite.class));

     writeNshmpPolys();
    // writeNshmpSummaryPoly();
    // writeNshmpSites_0p1();
  }

  /*
   * Currently, we're exporting map regions as polygons. Although the GeoJSON
   * spec supports polygons with holes (and hence 3-dimensional arrays, we only
   * support singular polygons. Polygons render better than PointStrings in any
   * event.
   */
  static void writeNshmpPolys() throws IOException {
    LocationList usCoords = CONTERMINOUS_US.coordinates();

    Path ceusOut = EXPORT_DIR.resolve("map-ceus.geojson");
    LocationList ceusBounds = CEUS_CLIP.coordinates();
    writePolyJson(ceusOut, "NSHMP Central & Eastern US", usCoords, 0.1, ceusBounds);

    Path wusOut = EXPORT_DIR.resolve("map-wus.geojson");
    LocationList wusBounds = WUS_CLIP.coordinates();
    writePolyJson(wusOut, "NSHMP Western US", usCoords, 0.1, wusBounds);
    
    // TODO AK needs to be updated with proper clipping region as above
    // currently just mercator rectangle
    writePolyJson(
        EXPORT_DIR.resolve("map-alaska.geojson"),
        "Alaska",
        AK_CLIP.coordinates(),
        0.1,
        null);
    
    writePolyJson(
        EXPORT_DIR.resolve("map-la-basin.geojson"),
        LA_BASIN.toString(),
        LA_BASIN.coordinates(),
        0.05,
        null);

    writePolyJson(
        EXPORT_DIR.resolve("map-sf-bay.geojson"),
        SF_BAY.toString(),
        SF_BAY.coordinates(),
        0.05,
        null);

    writePolyJson(
        EXPORT_DIR.resolve("map-puget.geojson"),
        PUGET.toString(),
        PUGET.coordinates(),
        0.05,
        null);

    writePolyJson(
        EXPORT_DIR.resolve("map-wasatch.geojson"),
        WASATCH.toString(),
        WASATCH.coordinates(),
        0.05,
        null);

    writePolyJson(
        EXPORT_DIR.resolve("map-new-madrid.geojson"),
        NEW_MADRID.toString(),
        NEW_MADRID.coordinates(),
        0.05,
        null);

    writePolyJson(
        EXPORT_DIR.resolve("map-ucerf3-nshm.geojson"),
        UCERF3_NSHM14.toString(),
        UCERF3_NSHM14.coordinates(),
        0.1,
        null);

    writePolyJson(
        EXPORT_DIR.resolve("map-ucerf3-relm.geojson"),
        UCERF3_RELM.toString(),
        UCERF3_RELM.coordinates(),
        0.1,
        null);

    writePolyJson(
        EXPORT_DIR.resolve("map-cybershake.geojson"),
        CYBERSHAKE.toString(),
        CYBERSHAKE.coordinates(),
        0.1,
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

  static void writePolysJson(Path out, List<String> nameList, List<LocationList> coordList)
      throws IOException {
    List<Feature> features = new ArrayList<>();
    int i = 0;
    for (LocationList coords : coordList) {
      features.add(GeoJson.createPolygon(
          nameList.get(i++),
          coords,
          Optional.<String> absent(),
          Optional.of(0.1)));
    }
    FeatureCollection fc = new FeatureCollection();
    fc.features = features;
    String json = GeoJson.cleanPoly(GSON.toJson(fc));
    Files.write(out, json.getBytes(StandardCharsets.UTF_8));
  }

  static void writePolyJson(
      Path out,
      String name,
      LocationList coords,
      double spacing,
      LocationList bounds) throws IOException {

    List<Feature> features = new ArrayList<>();
    if (bounds != null) {
      features.add(GeoJson.createPolygon(
          name + " Map Extents",
          bounds,
          Optional.of(GeoJson.Value.EXTENTS),
          Optional.<Double> absent()));
    }
    features.add(GeoJson.createPolygon(
        name,
        coords,
        Optional.<String> absent(),
        Optional.of(spacing)));
    FeatureCollection fc = new FeatureCollection();
    fc.features = features;
    String json = GeoJson.cleanPoly(GSON.toJson(fc));
    Files.write(out, json.getBytes(StandardCharsets.UTF_8));
  }

  static void writeNshmpSites_0p1() throws IOException {
    writeSites(
        "ceus-0p1",
        FluentIterable.from(NshmpSite.ceus())
            .transform(adjustLocation_0p1())
            .toList(),
        DEC2_FMT);
    writeSites(
        "wus-0p1",
        FluentIterable.from(NshmpSite.wus())
            .transform(adjustLocation_0p1())
            .toList(),
        DEC2_FMT);
  }

  static void writeNshmpSites() throws IOException {
    writeNshmpSites("nshmp", EnumSet.allOf(NshmpSite.class));
    writeNshmpSites("ceus", NshmpSite.ceus());
    writeNshmpSites("wus", NshmpSite.wus());
    writeNshmpSites("nrc", NshmpSite.nrc());
    writeNshmpSites("alaska", NshmpSite.alaska());
    writeSites("nehrp", NshmpSite.nehrp(), DEC2_FMT);
  }

  static void writeNshmpSites(String name, Collection<NshmpSite> sites)
      throws IOException {
    List<NshmpSite> sortedSites = Lists.newArrayList(sites);
    Collections.sort(sortedSites, new NshmpSite.StateComparator());
    writeSites(name, sortedSites, DEC2_FMT);
  }

  static void writeCybershakeSites(
      String name,
      Collection<CybershakeSite> sites) throws IOException {

    Path jsonOut = EXPORT_DIR.resolve("sites-" + name + ".geojson");
    writeCybershakeJsonSites(jsonOut, sites, CybershakeVs30.NONE);
    jsonOut = EXPORT_DIR.resolve("sites-" + name + "-vs30-wills.geojson");
    writeCybershakeJsonSites(jsonOut, sites, CybershakeVs30.WILLS);
    jsonOut = EXPORT_DIR.resolve("sites-" + name + "-vs30-cvm.geojson");
    writeCybershakeJsonSites(jsonOut, sites, CybershakeVs30.CVM);

    Path csvOut = EXPORT_DIR.resolve("sites-" + name + ".csv");
    writeCybershakeCsvSites(csvOut, sites, CybershakeVs30.NONE);
    csvOut = EXPORT_DIR.resolve("sites-" + name + "-vs30-wills.csv");
    writeCybershakeCsvSites(csvOut, sites, CybershakeVs30.WILLS);
    csvOut = EXPORT_DIR.resolve("sites-" + name + "-vs30-cvm.csv");
    writeCybershakeCsvSites(csvOut, sites, CybershakeVs30.CVM);
  }

  static void writeSites(
      String name,
      Collection<? extends NamedLocation> sites,
      DecimalFormat latLonFormat) throws IOException {

    Path jsonOut = EXPORT_DIR.resolve("sites-" + name + ".geojson");
    writeJsonSites(jsonOut, sites);
    Path csvOut = EXPORT_DIR.resolve("sites-" + name + ".csv");
    writeCsvSites(csvOut, sites, latLonFormat);
  }

  private static final DecimalFormat DEC2_FMT = new DecimalFormat("0.00");
  private static final DecimalFormat DEC3_FMT = new DecimalFormat("0.000");
  private static final DecimalFormat DEC5_FMT = new DecimalFormat("0.00000");
  private static final int NAME_BUFF = 28;
  private static final int LON_BUFF = 8;
  private static final int LAT_BUFF = 7;
  private static final int LON_BUFF_CYBER = 10;
  private static final int LAT_BUFF_CYBER = 9;

  private enum CybershakeVs30 {
    NONE,
    WILLS,
    CVM;
  }

  private static void writeCsvSites(
      Path out,
      Collection<? extends NamedLocation> locs,
      final DecimalFormat latLonFormat) throws IOException {

    Iterable<String> lines = Iterables.transform(locs, new Function<NamedLocation, String>() {
      @Override
      public String apply(NamedLocation loc) {
        StringBuilder sb = new StringBuilder();
        sb.append(padEnd(loc.toString() + ",", NAME_BUFF, ' '));
        sb.append(padStart(latLonFormat.format(loc.location().lon()), LON_BUFF, ' '));
        sb.append(',');
        sb.append(padStart(latLonFormat.format(loc.location().lat()), LAT_BUFF, ' '));
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

  private static void writeCybershakeCsvSites(
      Path out,
      Collection<CybershakeSite> locs,
      final CybershakeVs30 vs30) throws IOException {

    Iterable<String> lines = Iterables.transform(locs, new Function<CybershakeSite, String>() {
      @Override
      public String apply(CybershakeSite loc) {
        StringBuilder sb = new StringBuilder();
        sb.append(padEnd(loc.toString() + ",", NAME_BUFF, ' '));
        sb.append(padStart(DEC5_FMT.format(loc.location().lon()), LON_BUFF_CYBER, ' '));
        sb.append(',');
        sb.append(padStart(DEC5_FMT.format(loc.location().lat()), LAT_BUFF_CYBER, ' '));
        if (vs30 != CybershakeVs30.NONE) {
          double vs30value = (vs30 == CybershakeVs30.WILLS) ? loc.willsVs30() : loc.cvmVs30();
          sb.append(", ").append(padStart(Double.toString(vs30value), 7, ' '));
          sb.append(", ").append(DEC2_FMT.format(loc.z1p0()));
          sb.append(", ").append(DEC2_FMT.format(loc.z2p5()));
        }
        return sb.toString();
      }
    });
    StringBuilder header = new StringBuilder(padEnd("name,", NAME_BUFF, ' '))
        .append(padStart("lon", LON_BUFF_CYBER, ' '))
        .append(',')
        .append(padStart("lat", LAT_BUFF_CYBER, ' '));
    if (vs30 != CybershakeVs30.NONE) {
      header.append(",    vs30")
          .append(", z1p0")
          .append(", z2p5");
    }
    Files.write(
        out,
        Iterables.concat(ImmutableList.of(header.toString()), lines),
        StandardCharsets.UTF_8);
  }

  private static void writeJsonSites(Path out, Collection<? extends NamedLocation> sites)
      throws IOException {

    List<Feature> features = new ArrayList<>(sites.size());
    for (NamedLocation loc : sites) {
      features.add(GeoJson.createPoint(loc));
    }
    FeatureCollection fc = new FeatureCollection();
    fc.features = features;
    String json = GeoJson.cleanPoints(GSON.toJson(fc));
    Files.write(out, json.getBytes(StandardCharsets.UTF_8));
  }

  private static void writeCybershakeJsonSites(
      Path out,
      Collection<CybershakeSite> sites,
      CybershakeVs30 vs30) throws IOException {

    List<Feature> features = new ArrayList<>(sites.size());
    for (CybershakeSite loc : sites) {
      Feature feature = GeoJson.createPoint(loc);
      CybershakeSiteProperties props = new CybershakeSiteProperties();
      props.title = loc.name();
      // props.runId = loc.runId();
      if (vs30 != CybershakeVs30.NONE) {
        props.vs30 = (vs30 == CybershakeVs30.WILLS) ? loc.willsVs30() : loc.cvmVs30();
        props.z1p0 = loc.z1p0();
        props.z2p5 = loc.z2p5();
      }
      feature.properties = props;
      features.add(feature);
    }
    FeatureCollection fc = new FeatureCollection();
    fc.features = features;
    String json = GeoJson.cleanPoints(GSON.toJson(fc));
    Files.write(out, json.getBytes(StandardCharsets.UTF_8));
  }

  static class CybershakeSiteProperties extends PointProperties {
    // Integer runId;
    Double vs30;
    Double z1p0;
    Double z2p5;
  }

  private static final Function<NamedLocation, NamedLocation> adjustLocation_0p1() {
    return new Function<NamedLocation, NamedLocation>() {

      @Override
      public NamedLocation apply(final NamedLocation input) {
        return new NamedLocation() {

          @Override
          public Location location() {
            return Location.create(
                MathUtils.round(input.location().lat(), 1),
                MathUtils.round(input.location().lon(), 1));
          }

          @Override
          public String id() {
            return input.id();
          }

          @Override
          public String toString() {
            return input.toString();
          }
        };
      }
    };
  }

}
