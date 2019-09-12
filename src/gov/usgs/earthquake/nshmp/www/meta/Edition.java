package gov.usgs.earthquake.nshmp.www.meta;

import static gov.usgs.earthquake.nshmp.gmm.Imt.PGA;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA0P1;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA0P2;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA0P3;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA0P5;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA0P75;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA1P0;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA2P0;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA3P0;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA4P0;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA5P0;
import static gov.usgs.earthquake.nshmp.www.meta.Region.AK;
import static gov.usgs.earthquake.nshmp.www.meta.Region.CEUS;
import static gov.usgs.earthquake.nshmp.www.meta.Region.COUS;
import static gov.usgs.earthquake.nshmp.www.meta.Region.WUS;

import java.util.EnumSet;
import java.util.Set;

import gov.usgs.earthquake.nshmp.gmm.Imt;

@SuppressWarnings({ "javadoc", "unused" })
public enum Edition implements Constrained {

  E2008(
      "Dynamic: Conterminous U.S. 2008",
      100,
      EnumSet.of(COUS, CEUS, WUS),
      EnumSet.of(PGA, SA0P1, SA0P2, SA0P3, SA0P5, SA0P75, SA1P0, SA2P0, SA3P0)),

  E2014(
      "Dynamic: Conterminous U.S. 2014",
      0,
      EnumSet.of(COUS, CEUS, WUS),
      EnumSet.of(PGA, SA0P1, SA0P2, SA0P3, SA0P5, SA1P0, SA2P0)),

  E2014B(
      "Dynamic: Conterminous U.S. 2014 (update)",
      -10,
      EnumSet.of(COUS, CEUS, WUS),
      EnumSet.of(PGA, SA0P1, SA0P2, SA0P3, SA0P5, SA0P75, SA1P0, SA2P0, SA3P0, SA4P0, SA5P0)),

  E2007(
      "Dynamic: Alaska 2007",
      -100,
      EnumSet.of(AK),
      EnumSet.of(PGA, SA0P1, SA0P2, SA0P3, SA0P5, SA1P0, SA2P0));

  private final String label;

  /* not serialized */
  private final transient String version;
  private final transient Set<Region> regions;
  final transient Set<Imt> imts;

  private final Constraints constraints;

  final int displayOrder;

  private Edition(
      String label,
      int displayOrder,
      Set<Region> regions,
      Set<Imt> imts) {

    this.version = Versions.modelVersion(name());
    this.label = label + " (" + version + ")";
    this.displayOrder = displayOrder;
    this.regions = regions;
    this.imts = imts;
    this.constraints = new EditionConstraints(regions, imts);
  }

  @Override
  public String toString() {
    return label;
  }

  public String version() {
    return version;
  }

  @Override
  public Constraints constraints() {
    return constraints;
  }

}
