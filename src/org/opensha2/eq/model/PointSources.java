package org.opensha2.eq.model;

import org.opensha2.calc.Calcs;
import org.opensha2.calc.InputList;
import org.opensha2.calc.Site;
import org.opensha2.data.DataTables;
import org.opensha2.data.XySequence;
import org.opensha2.eq.fault.FocalMech;
import org.opensha2.eq.fault.surface.RuptureScaling;
import org.opensha2.eq.model.PointSource.DepthModel;
import org.opensha2.geo.Location;
import org.opensha2.geo.Locations;
import org.opensha2.util.Parsing;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;

/**
 * Factory class for generating lists of {@code GmmInput}s for point sources.
 *
 * @author Peter Powers
 */
public class PointSources {

  /**
   * Using the supplied {@code Site} and the standard data that is used to build
   * point sources, create and return an {@code InputList}.
   * 
   * @param site of interest
   * @param loc
   * @param mfd
   * @param mechWtMap
   * @param rupScaling
   * @param magDepthMap
   * @param maxDepth
   */
  public static InputList finiteInputs(
      Site site,
      Location loc,
      XySequence mfd,
      Map<FocalMech, Double> mechWtMap,
      RuptureScaling rupScaling,
      NavigableMap<Double, Map<Double, Double>> magDepthMap,
      double maxDepth) {

    Source source = finiteSource(loc, mfd, mechWtMap, rupScaling, magDepthMap, maxDepth);
    return Calcs.sourceToInputs(site).apply(source);
  }

  /**
   * Using the supplied {@code Site}s and the standard data that is used to
   * build point sources, create and return a {@code List<InputList>}.
   * 
   * @param sites
   * @param loc
   * @param mfd
   * @param mechWtMap
   * @param rupScaling
   * @param magDepthMap
   * @param maxDepth
   */
  public static List<InputList> finiteInputs(
      List<Site> sites,
      Location loc,
      XySequence mfd,
      Map<FocalMech, Double> mechWtMap,
      RuptureScaling rupScaling,
      NavigableMap<Double, Map<Double, Double>> magDepthMap,
      double maxDepth) {

    Source source = finiteSource(loc, mfd, mechWtMap, rupScaling, magDepthMap, maxDepth);
    List<InputList> inputsList = new ArrayList<>();
    for (Site site : sites) {
      InputList inputs = Calcs.sourceToInputs(site).apply(source);
      inputsList.add(inputs);
    }
    return inputsList;
  }

  /**
   * Using the supplied {@code Site}s and a GridSourceSet, from which the
   * standard data that is used to build point sources is derived, create and
   * return a {@code List<InputList>}.
   * 
   */
  public static List<InputList> finiteInputs(
      List<Site> sites,
      Location loc,
      XySequence mfd,
      Map<FocalMech, Double> mechWtMap,
      GridSourceSet grid) {

    Source source = pointSource(PointSourceType.FINITE, loc, mfd, mechWtMap, grid.rupScaling,
      grid.depthModel);
    return finiteInputs(sites, source);
  }

  private static List<InputList> finiteInputs(List<Site> sites, Source source) {

    List<InputList> inputsList = new ArrayList<>();
    for (Site site : sites) {
      InputList inputs = Calcs.sourceToInputs(site).apply(source);
      inputsList.add(inputs);
    }
    return inputsList;
  }

  private static PointSource finiteSource(
      Location loc,
      XySequence mfd,
      Map<FocalMech, Double> mechWtMap,
      RuptureScaling rupScaling,
      NavigableMap<Double, Map<Double, Double>> magDepthMap,
      double maxDepth) {

    DepthModel depthModel = DepthModel.create(magDepthMap, mfd.xValues(), maxDepth);
    return new PointSourceFinite(loc, mfd, mechWtMap, rupScaling, depthModel);
  }

  public static PointSource pointSource(
      PointSourceType type,
      Location loc,
      XySequence mfd,
      Map<FocalMech, Double> mechWtMap,
      RuptureScaling rupScaling,
      DepthModel depthModel) {

    switch (type) {
      case POINT:
        return new PointSource(loc, mfd, mechWtMap, rupScaling, depthModel);
      case FINITE:
        return new PointSourceFinite(loc, mfd, mechWtMap, rupScaling, depthModel);
      default:
        throw new UnsupportedOperationException("FIXED_STRIKE point sources not supported");
    }

  }

  public static void main(String[] args) {

    double M_MIN = 4.7;
    double M_MAX = 8.0;
    double M_Δ = 0.1;

    double rMin = 0.0;
    double rMax = 1000.0;
    double rΔ = 5.0;

    double[] distances = DataTables.keys(rMin, rMax, rΔ);
    double[] mags = DataTables.keys(M_MIN, M_MAX, M_Δ);
    double[] rates = new double[mags.length];

    // IncrementalMfd mfd = Mfds.newIncrementalMFD(mags, rates);
    XySequence mfd = XySequence.create(mags, rates);

    Map<FocalMech, Double> ssMap = Maps.immutableEnumMap(
      ImmutableMap.<FocalMech, Double> builder()
        .put(FocalMech.STRIKE_SLIP, 1.0)
        .put(FocalMech.REVERSE, 0.0)
        .put(FocalMech.NORMAL, 0.0)
        .build());

    Map<FocalMech, Double> multiMechMap = Maps.immutableEnumMap(
      ImmutableMap.<FocalMech, Double> builder()
        .put(FocalMech.STRIKE_SLIP, 0.3334)
        .put(FocalMech.REVERSE, 0.3333)
        .put(FocalMech.NORMAL, 0.3333)
        .build());

    RuptureScaling rupScaling = RuptureScaling.NSHM_POINT_WC94_LENGTH;

    String ceusMagDepthStr = "[10.0::[5.0:1.0]]";
    String wusMagDepthStr = "[6.5::[5.0:1.0]; 10.0::[1.0:1.0]]";

    NavigableMap<Double, Map<Double, Double>> ceusMagDepthMap =
      Parsing.stringToValueValueWeightMap(ceusMagDepthStr);
    NavigableMap<Double, Map<Double, Double>> wusMagDepthMap =
      Parsing.stringToValueValueWeightMap(wusMagDepthStr);

    double ceusMaxDepth = 22.0;
    double wusMaxDepth = 14.0;

    PointSource source = finiteSource(
      Location.create(0.0, 0.0),
      mfd,
      multiMechMap,
      rupScaling,
      wusMagDepthMap,
      wusMaxDepth);
    System.out.println(source.name());

    Location srcLoc = Location.create(0.0, 0.0);

    /*
     * Using longitudinal azimuth (0.0) best recovers distance using 'fast'
     * distance algorithm.
     */
    double az = 0.0; // radians;
    Location siteLoc = Locations.location(srcLoc, az, distances[1]);
    Site site = Site.builder()
      .location(siteLoc)
      .vs30(760.0)
      .build();

    InputList inputs = finiteInputs(
      site,
      srcLoc,
      mfd,
      multiMechMap,
      rupScaling,
      wusMagDepthMap,
      wusMaxDepth);

    System.out.println(inputs);

    // List<Site> siteList = new ArrayList<>();
    // Site.Builder siteBuilder = Site.builder().vs30(760.0);
    // for (double r : distances) {
    // Location loc = Locations.location(srcLoc, az, r);
    // siteBuilder.location(loc);
    // siteList.add(siteBuilder.build());
    // }
    //
    // List<InputList> inputsList = finiteInputs(
    // siteList,
    // srcLoc,
    // mfd,
    // multiMechMap,
    // rupScaling,
    // wusMagDepthMap,
    // wusMaxDepth);
    //
    // System.out.println(inputsList.size());
    // System.out.println(inputsList.get(inputsList.size() - 1));

    // System.out.println(inputsList);

    // double distance = 700.0;
    // Location loc1 = Location.create(0, 0);
    // Location loc1a = Locations.location(loc1, az, distance);
    // System.out.println(loc1a);
    // double d1 = Locations.horzDistanceFast(loc1, loc1a);
    // System.out.println(d1);
    //
    // Location loc2 = Location.create(37, -90);
    // Location loc2a = Locations.location(loc2, az, distance);
    // System.out.println(loc2a);
    // double d2 = Locations.horzDistanceFast(loc2, loc2a);
    // System.out.println(d2);

  }

}
