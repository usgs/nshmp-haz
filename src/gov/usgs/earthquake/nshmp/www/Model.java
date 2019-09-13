package gov.usgs.earthquake.nshmp.www;

import static gov.usgs.earthquake.nshmp.calc.Vs30.*;
import static gov.usgs.earthquake.nshmp.gmm.Imt.*;
import static gov.usgs.earthquake.nshmp.www.meta.Region.*;

import java.nio.file.Paths;
import java.util.EnumSet;
import java.util.Set;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;

import gov.usgs.earthquake.nshmp.calc.Vs30;
import gov.usgs.earthquake.nshmp.gmm.Imt;
import gov.usgs.earthquake.nshmp.internal.Parsing;
import gov.usgs.earthquake.nshmp.internal.Parsing.Delimiter;
import gov.usgs.earthquake.nshmp.www.meta.Region;

enum Model {

  AK_2007(
      EnumSet.of(PGA, SA0P1, SA0P2, SA0P3, SA0P5, SA1P0, SA2P0),
      EnumSet.of(VS_760)),

  CEUS_2008(
      EnumSet.of(PGA, SA0P1, SA0P2, SA0P3, SA0P5, SA1P0, SA2P0),
      EnumSet.of(VS_760, VS_2000)),

  WUS_2008(
      EnumSet.of(PGA, SA0P1, SA0P2, SA0P3, SA0P5, SA0P75, SA1P0, SA2P0, SA3P0),
      EnumSet.of(VS_1150, VS_760, VS_537, VS_360, VS_259, VS_180)),

  CEUS_2014(
      EnumSet.of(PGA, SA0P1, SA0P2, SA0P3, SA0P5, SA1P0, SA2P0),
      EnumSet.of(VS_760, VS_2000)),

  WUS_2014(
      EnumSet.of(PGA, SA0P1, SA0P2, SA0P3, SA0P5, SA0P75, SA1P0, SA2P0, SA3P0),
      EnumSet.of(VS_1150, VS_760, VS_537, VS_360, VS_259, VS_180)),

  WUS_2014B(
      EnumSet.of(PGA, SA0P1, SA0P2, SA0P3, SA0P5, SA0P75, SA1P0, SA2P0, SA3P0, SA4P0, SA5P0),
      EnumSet.of(VS_1150, VS_760, VS_537, VS_360, VS_259, VS_180)),

  CEUS_2018(
      EnumSet.of(PGA, SA0P1, SA0P2, SA0P3, SA0P5, SA0P75, SA1P0, SA2P0, SA3P0, SA4P0, SA5P0),
      EnumSet.of(VS_1150, VS_760, VS_537, VS_360, VS_259, VS_180)),

  WUS_2018(
      EnumSet.of(PGA, SA0P1, SA0P2, SA0P3, SA0P5, SA0P75, SA1P0, SA2P0, SA3P0, SA4P0, SA5P0),
      EnumSet.of(VS_1150, VS_760, VS_537, VS_360, VS_259, VS_180)),
  
  HI_2020(
      EnumSet.of(PGA, SA0P1, SA0P2, SA0P3, SA0P5, SA0P75, SA1P0, SA2P0, SA3P0, SA5P0),
      EnumSet.of(VS_1150, VS_760, VS_537, VS_360, VS_259, VS_180));

  private static final String MODEL_DIR = "models";

  final Set<Imt> imts;
  final Set<Vs30> vs30s;

  final String path;
  final String name;
  final Region region;
  final String year;

  private Model(Set<Imt> imts, Set<Vs30> vs30s) {
    this.imts = Sets.immutableEnumSet(imts);
    this.vs30s = Sets.immutableEnumSet(vs30s);
    region = deriveRegion(name());
    year = name().substring(name().lastIndexOf('_') + 1);
    path = Paths.get("/", MODEL_DIR)
        .resolve(region.name().toLowerCase())
        .resolve(year.toLowerCase())
        .toString();
    name = Parsing.join(
        ImmutableList.of(year, region.label, "Hazard Model"),
        Delimiter.SPACE);
  }

  private static Region deriveRegion(String s) {
    return s.startsWith("AK") ? AK : s.startsWith("WUS") ? WUS : s.startsWith("HI") ? HI : CEUS;
  }
}
