package gov.usgs.earthquake.nshmp.www.meta;

import static gov.usgs.earthquake.nshmp.calc.Vs30.VS_1150;
import static gov.usgs.earthquake.nshmp.calc.Vs30.VS_180;
import static gov.usgs.earthquake.nshmp.calc.Vs30.VS_2000;
import static gov.usgs.earthquake.nshmp.calc.Vs30.VS_259;
import static gov.usgs.earthquake.nshmp.calc.Vs30.VS_360;
import static gov.usgs.earthquake.nshmp.calc.Vs30.VS_537;
import static gov.usgs.earthquake.nshmp.calc.Vs30.VS_760;
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

import java.util.EnumSet;
import java.util.Set;

import gov.usgs.earthquake.nshmp.calc.Vs30;
import gov.usgs.earthquake.nshmp.gmm.Imt;

@SuppressWarnings("javadoc")
public enum Region implements Constrained {

  AK(
      "Alaska",
      new double[] { 48.0, 72.0 },
      new double[] { -200.0, -125.0 },
      new double[] { 48.0, 72.0 },
      new double[] { -200.0, -125.0 },
      EnumSet.of(PGA, SA0P1, SA0P2, SA0P3, SA0P5, SA1P0, SA2P0),
      EnumSet.of(VS_760)),

  COUS(
      "Conterminous US",
      new double[] { 24.6, 50.0 },
      new double[] { -125.0, -65.0 },
      new double[] { 24.6, 50.0 },
      new double[] { -125.0, -65.0 },
      EnumSet.of(PGA, SA0P1, SA0P2, SA0P3, SA0P5, SA1P0, SA2P0),
      EnumSet.of(VS_760)),

  CEUS(
      "Central & Eastern US",
      new double[] { 24.6, 50.0 },
      new double[] { -115.0, -65.0 },
      new double[] { 24.6, 50.0 },
      new double[] { -100.0, -65.0 },
      EnumSet.of(PGA, SA0P1, SA0P2, SA0P3, SA0P5, SA1P0, SA2P0),
      EnumSet.of(VS_2000, VS_760)),
  
  HI(
      "Hawaii",
      new double[] { 18.0, 23.0 },
      new double[] { -161.0, -154.0 },
      new double[] { 18.0, 23.0 },
      new double[] { -161.0, -154.0 },
      EnumSet.of(PGA, SA0P1, SA0P2, SA0P3, SA0P5, SA0P75, SA1P0, SA2P0, SA3P0, SA5P0),
      EnumSet.of(VS_1150, VS_760, VS_537, VS_360, VS_259, VS_180)),

  WUS(
      "Western US",
      new double[] { 24.6, 50.0 },
      new double[] { -125.0, -100.0 },
      new double[] { 24.6, 50.0 },
      new double[] { -125.0, -115.0 },
      EnumSet.of(PGA, SA0P1, SA0P2, SA0P3, SA0P5, SA0P75, SA1P0, SA2P0, SA3P0, SA4P0, SA5P0),
      EnumSet.of(VS_1150, VS_760, VS_537, VS_360, VS_259, VS_180));

  public final String label;

  public final double minlatitude;
  public final double maxlatitude;
  public final double minlongitude;
  public final double maxlongitude;

  public final double uiminlatitude;
  public final double uimaxlatitude;
  public final double uiminlongitude;
  public final double uimaxlongitude;

  /* not serialized */
  final transient Set<Imt> imts;
  final transient Set<Vs30> vs30s;

  private final Constraints constraints;

  private Region(
      String label,
      double[] latRange,
      double[] lonRange,
      double[] uiLatRange,
      double[] uiLonRange,
      Set<Imt> imts,
      Set<Vs30> vs30s) {

    this.label = label;

    this.minlatitude = latRange[0];
    this.maxlatitude = latRange[1];
    this.minlongitude = lonRange[0];
    this.maxlongitude = lonRange[1];

    this.uiminlatitude = uiLatRange[0];
    this.uimaxlatitude = uiLatRange[1];
    this.uiminlongitude = uiLonRange[0];
    this.uimaxlongitude = uiLonRange[1];

    this.imts = imts;
    this.vs30s = vs30s;

    this.constraints = new RegionConstraints(imts, vs30s);
  }

  @Override
  public String toString() {
    return label;
  }

  @Override
  public Constraints constraints() {
    return constraints;
  }
}
