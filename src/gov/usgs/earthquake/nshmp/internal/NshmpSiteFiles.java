package gov.usgs.earthquake.nshmp.internal;

import static com.google.common.base.Strings.padEnd;
import static com.google.common.base.Strings.padStart;
import static gov.usgs.earthquake.nshmp.internal.NshmpPolygon.ALASKA;
import static gov.usgs.earthquake.nshmp.internal.NshmpPolygon.ALASKA_CLIP;
import static gov.usgs.earthquake.nshmp.internal.NshmpPolygon.CEUS_CLIP;
import static gov.usgs.earthquake.nshmp.internal.NshmpPolygon.CONTERMINOUS_US;
import static gov.usgs.earthquake.nshmp.internal.NshmpPolygon.CONUS_CLIP;
import static gov.usgs.earthquake.nshmp.internal.NshmpPolygon.CYBERSHAKE;
import static gov.usgs.earthquake.nshmp.internal.NshmpPolygon.HAWAII;
import static gov.usgs.earthquake.nshmp.internal.NshmpPolygon.HAWAII_CLIP;
import static gov.usgs.earthquake.nshmp.internal.NshmpPolygon.LA_BASIN;
import static gov.usgs.earthquake.nshmp.internal.NshmpPolygon.NEW_MADRID;
import static gov.usgs.earthquake.nshmp.internal.NshmpPolygon.PUGET;
import static gov.usgs.earthquake.nshmp.internal.NshmpPolygon.SF_BAY;
import static gov.usgs.earthquake.nshmp.internal.NshmpPolygon.UCERF3_NSHM14;
import static gov.usgs.earthquake.nshmp.internal.NshmpPolygon.UCERF3_NSHM_CLIP;
import static gov.usgs.earthquake.nshmp.internal.NshmpPolygon.UCERF3_RELM;
import static gov.usgs.earthquake.nshmp.internal.NshmpPolygon.WASATCH;
import static gov.usgs.earthquake.nshmp.internal.NshmpPolygon.WUS_CLIP;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import com.google.common.base.Functions;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import gov.usgs.earthquake.nshmp.geo.Location;
import gov.usgs.earthquake.nshmp.geo.LocationList;
import gov.usgs.earthquake.nshmp.geo.json.Feature;
import gov.usgs.earthquake.nshmp.geo.json.GeoJson;
import gov.usgs.earthquake.nshmp.geo.json.Properties;
import gov.usgs.earthquake.nshmp.geo.json.Properties.Style;
import gov.usgs.earthquake.nshmp.util.Maths;
import gov.usgs.earthquake.nshmp.util.NamedLocation;

/**
 * Methods for exporting nshmp site data as CSV and GeoJSON.
 *
 * @author U.S. Geological Survey
 */
final class NshmpSiteFiles {

  private static final Path EXPORT_DIR = Paths.get("etc", "nshm");
  private static final String EXTENTS_COLOR = "#AA0078";

  /**
   * Updates all site list and map files in etc/nshm.
   */
  public static void main(String[] args) throws IOException {
    writeNshmpSites();
    writeSites("nureg", EnumSet.allOf(NuregSite.class), DEC3_FMT);
    writeCybershakeSites("cybershake", EnumSet.allOf(CybershakeSite.class));

    writeNshmpPolys();
    writeNshmpSummaryPoly();
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
    LocationList ceusBounds = CEUS_CLIP.coordinates().bounds().toList();
    writePolyJson(ceusOut, "NSHMP Central & Eastern US", usCoords, 0.05, ceusBounds);

    Path wusOut = EXPORT_DIR.resolve("map-wus.geojson");
    LocationList wusBounds = WUS_CLIP.coordinates().bounds().toList();
    writePolyJson(wusOut, "NSHMP Western US", usCoords, 0.05, wusBounds);

    writePolyJson(
        EXPORT_DIR.resolve("map-cous.geojson"),
        CONTERMINOUS_US.toString(),
        CONTERMINOUS_US.coordinates(),
        0.05,
        CONUS_CLIP.coordinates().bounds().toList());

    writePolyJson(
        EXPORT_DIR.resolve("map-alaska.geojson"),
        ALASKA.toString(),
        ALASKA.coordinates(),
        0.1,
        ALASKA_CLIP.coordinates().bounds().toList());

    writePolyJson(
        EXPORT_DIR.resolve("map-hawaii.geojson"),
        HAWAII.toString(),
        HAWAII.coordinates(),
        0.02,
        HAWAII_CLIP.coordinates().bounds().toList());

    writePolyJson(
        EXPORT_DIR.resolve("map-la-basin.geojson"),
        LA_BASIN.toString(),
        LA_BASIN.coordinates(),
        0.01,
        null);

    writePolyJson(
        EXPORT_DIR.resolve("map-sf-bay.geojson"),
        SF_BAY.toString(),
        SF_BAY.coordinates(),
        0.01,
        null);

    writePolyJson(
        EXPORT_DIR.resolve("map-puget.geojson"),
        PUGET.toString(),
        PUGET.coordinates(),
        0.01,
        null);

    writePolyJson(
        EXPORT_DIR.resolve("map-wasatch.geojson"),
        WASATCH.toString(),
        WASATCH.coordinates(),
        0.01,
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
        EXPORT_DIR.resolve("map-ucerf3-nshm-clip.geojson"),
        UCERF3_NSHM_CLIP.toString(),
        UCERF3_NSHM_CLIP.coordinates(),
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
            }::apply)
            .toList());
  }

  static void writePolysJson(
      Path out,
      List<String> nameList,
      List<LocationList> coordList) throws IOException {

    GeoJson.Builder b = GeoJson.builder();
    Properties.Builder props = Properties.builder()
        .put("spacing", 0.1);

    int i = 0;
    // TODO this incrementer is messed up
    // can't name and coords come as a map?
    for (LocationList border : coordList) {
      props.put(Style.TITLE, nameList.get(i++));
      b.add(Feature.polygon(border)
          .properties(props.build())
          .build());
    }
    b.write(out);
  }

  static void writePolyJson(
      Path out,
      String name,
      LocationList coords,
      Double spacing,
      LocationList bounds) throws IOException {

    GeoJson.Builder b = GeoJson.builder();

    if (bounds != null) {
      Map<String, Object> boundsProps = Properties.builder()
          .put(Style.FILL, EXTENTS_COLOR)
          .put(Style.STROKE, EXTENTS_COLOR)
          .put(Style.TITLE, name + " Map Extents")
          .build();
      b.add(Feature.polygon(bounds)
          .properties(boundsProps)
          .id("Extents")
          .build());
    }

    Map<String, Object> polyProps = Properties.builder()
        .put("spacing", spacing)
        .put(Style.TITLE, name)
        .build();
    b.add(Feature.polygon(coords)
        .properties(polyProps)
        .build())
        .write(out);
  }

  static void writeNshmpSites_0p1() throws IOException {
    writeSites(
        "ceus-0p1",
        FluentIterable.from(NshmpSite.ceus())
            .transform(adjustLocation_0p1()::apply)
            .toList(),
        DEC2_FMT);
    writeSites(
        "wus-0p1",
        FluentIterable.from(NshmpSite.wus())
            .transform(adjustLocation_0p1()::apply)
            .toList(),
        DEC2_FMT);
  }

  static void writeNshmpSites() throws IOException {
    writeNshmpSites("nshmp", EnumSet.allOf(NshmpSite.class));
    writeNshmpSites("ceus", NshmpSite.ceus());
    writeNshmpSites("wus", NshmpSite.wus());
    writeNshmpSites("nrc", NshmpSite.nrc());
    writeNshmpSites("alaska", NshmpSite.alaska());
    writeNshmpSites("hawaii", NshmpSite.hawaii());
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
    }::apply);
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
    }::apply);
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

    GeoJson.Builder b = GeoJson.builder();
    Properties.Builder props = Properties.builder()
        .put(Style.MARKER_SIZE, "small");

    for (NamedLocation loc : sites) {
      // TODO test loc vs loc.toString()
      b.add(Feature.point(loc.location())
          .properties(props
              .put(Style.TITLE, loc.toString())
              .build())
          .build());
    }
    b.write(out);
  }

  private static void writeCybershakeJsonSites(
      Path out,
      Collection<CybershakeSite> sites,
      CybershakeVs30 vs30) throws IOException {

    GeoJson.Builder b = GeoJson.builder();
    Properties.Builder props = Properties.builder()
        .put(Style.MARKER_SIZE, "small");

    for (CybershakeSite loc : sites) {
      props.put(Style.TITLE, loc.name());
      if (vs30 != CybershakeVs30.NONE) {
        props.put(
            "vs30",
            (vs30 == CybershakeVs30.WILLS) ? loc.willsVs30() : loc.cvmVs30())
            .put("z1p0", loc.z1p0())
            .put("z2p5", loc.z2p5());
      }
      b.add(Feature.point(loc.location())
          .properties(props.build())
          .build());
    }
    b.write(out);
  }

  private static final Function<NamedLocation, NamedLocation> adjustLocation_0p1() {
    return new Function<NamedLocation, NamedLocation>() {

      @Override
      public NamedLocation apply(final NamedLocation input) {
        return new NamedLocation() {

          @Override
          public Location location() {
            return Location.create(
                Maths.round(input.location().lat(), 1),
                Maths.round(input.location().lon(), 1));
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
