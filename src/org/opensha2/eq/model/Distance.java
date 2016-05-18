package org.opensha2.eq.model;

import static com.google.common.base.Preconditions.checkState;

import static org.opensha2.geo.Locations.distanceToLineFast;
import static org.opensha2.geo.Locations.distanceToSegmentFast;
import static org.opensha2.geo.Locations.horzDistanceFast;

import org.opensha2.eq.fault.Faults;
import org.opensha2.eq.fault.surface.GriddedSurface;
import org.opensha2.geo.BorderType;
import org.opensha2.geo.Location;
import org.opensha2.geo.LocationList;
import org.opensha2.geo.LocationVector;
import org.opensha2.geo.Locations;
import org.opensha2.geo.Region;
import org.opensha2.geo.Regions;

import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.util.Iterator;

/**
 * Distance value wrapper.
 *
 * @author Peter Powers
 */
public final class Distance {

  /**
   * Maximum supported distance for PSHA calculations. Currently set to 1000 km,
   * the maximum known to be supported by across all implementated ground motion
   * models.
   *
   * TODO: ground motion models should be polled for this number
   */
  @Deprecated
  public static final double MAX = 1000.0;

  @SuppressWarnings("javadoc")
  public enum Type {
    R_JB,
    R_RUP,
    R_X;
  }

  public final double rJB, rRup, rX;

  private Distance(double rJB, double rRup, double rX) {
    this.rJB = rJB;
    this.rRup = rRup;
    this.rX = rX;
  }

  static Distance create(double rJB, double rRup, double rX) {
    return new Distance(rJB, rRup, rX);
  }

  /**
   * Compute distance metrics: rJB, rRup, and rX.
   * @param surface
   * @param loc
   */
  public static Distance compute(GriddedSurface surface, Location loc) {

    Location loc1 = loc;
    Location loc2;
    double distJB = Double.MAX_VALUE;
    // double distSeis = Double.MAX_VALUE;
    double distRup = Double.MAX_VALUE;

    double horzDist, vertDist, rupDist;

    // flag to project to seisDepth if only one row and depth is below
    // seisDepth
    // boolean projectToDepth = false;
    // if (surface.getNumRows() == 1 && surface.getLocation(0,0).depth() <
    // SEIS_DEPTH)
    // projectToDepth = true;

    // get locations to iterate over depending on dip
    Iterator<Location> it;
    if (surface.dip() > 89) {
      it = surface.getColumnIterator(0);
      // if (surface.getLocation(0,0).depth() < SEIS_DEPTH)
      // projectToDepth = true;
    } else {
      it = surface.iterator();
    }

    while (it.hasNext()) {

      loc2 = it.next();

      // get the vertical distance
      vertDist = Locations.vertDistance(loc1, loc2);

      // get the horizontal dist depending on desired accuracy
      horzDist = Locations.horzDistanceFast(loc1, loc2);

      if (horzDist < distJB) {
        distJB = horzDist;
      }

      rupDist = horzDist * horzDist + vertDist * vertDist;
      if (rupDist < distRup) {
        distRup = rupDist;
      }

      // if (loc2.depth() >= SEIS_DEPTH) {
      // if (rupDist < distSeis)
      // distSeis = rupDist;
      // }
      // // take care of shallow line or point source case
      // else if(projectToDepth) {
      // rupDist = horzDist * horzDist + SEIS_DEPTH * SEIS_DEPTH;
      // if (rupDist < distSeis)
      // distSeis = rupDist;
      // }
    }

    distRup = Math.pow(distRup, 0.5);
    // distSeis = Math.pow(distSeis,0.5);

    // if(D) {
    // System.out.println(C+": distRup = " + distRup);
    // System.out.println(C+": distSeis = " + distSeis);
    // System.out.println(C+": distJB = " + distJB);
    // }

    // Check whether small values of distJB should really be zero
    if (distJB < surface.getAveGridSpacing()) { // check this first since
      // the next steps could take
      // time

      // first identify whether it's a frankel type surface
      // boolean frankelTypeSurface=false;
      // if(surface instanceof FrankelGriddedSurface) {
      // frankelTypeSurface = true;
      // }
      // else if(surface instanceof GriddedSubsetSurface) {
      // if(((GriddedSubsetSurface)surface).getParentSurface() instanceof
      // FrankelGriddedSurface) {
      // frankelTypeSurface = true;
      // }
      // }

      // if (frankelTypeSurface) {
      // if (isDjbZeroFrankel(surface, distJB)) distJB = 0;
      // } else {
      if (isDjbZero(surface.getPerimeter(), loc))
      {
        distJB = 0;
        // }
      }
    }

    if (distJB < surface.getAveGridSpacing() && isDjbZero(surface.getPerimeter(), loc)) {
      distJB = 0;
    }

    // double[] results = {distRup, distJB, distSeis};

    // return results;

    double rX = getDistanceX(surface.getEvenlyDiscritizedUpperEdge(), loc);

    return Distance.create(distJB, distRup, rX);

  }

  /**
   * This computes distanceX
   *
   * TODO I cannot believe there is not a cleaner implementation
   * @param surface
   * @param siteLoc
   */
  private static double getDistanceX(LocationList trace, Location siteLoc) {

    // Point sources used to get processed through this method.
    // Theoretically we shouldn't get here now because any trace
    // will have already been validated to have 2 or more points.
    // Conceivably the points could be closer together than the
    // spacing of a gridded surface and I'm not sure what the
    // consequence of that would be, but we should make sure that
    // dosn't happen.
    // TODO check gridded surface building
    checkState(trace.size() > 1, "Trace is too short");

    // TODO clean
    // double distanceX;

    // set to zero if it's a point source
    // if(trace.size() == 1) {
    // //distanceX = 0;
    //
    // } else {

    // @formatter:off

    /*
     *    P4                        P1
     *    |                          |
     *    |          dip dir         |
     *    |             |            |
     *    P3------************------P2
     *            <- strike --
     */

    // @formatter:on
    double strike = Faults.strikeRad(trace);
    double dipDir = Faults.dipDirectionRad(strike);

    LocationVector toP3 = LocationVector.create(strike, 1000.0, 0.0);
    LocationVector toP2 = LocationVector.reverseOf(toP3);
    LocationVector toP14 = LocationVector.create(dipDir, 1000.0, 0.0);

    Location p3 = Locations.location(trace.last(), toP3);
    Location p4 = Locations.location(p3, toP14);
    Location p2 = Locations.location(trace.first(), toP2);
    Location p1 = Locations.location(p2, toP14);

    LocationList region = LocationList.builder()
        .add(p1)
        .add(p2)
        .addAll(trace)
        .add(p3)
        .add(p4)
        .build();

    LocationList extendedTrace = LocationList.builder()
        .add(p2)
        .addAll(trace)
        .add(p3)
        .build();

    // We should probably set something here here too if it's vertical
    // strike-slip
    // (to avoid unnecessary calculations)

    // // get points projected off the ends
    // Location firstTraceLoc = trace.first(); // first trace point
    // Location lastTraceLoc = trace.last(); // last trace point
    //
    // // get point projected from first trace point in opposite direction
    // of the ave trace
    // LocationVector dirBase = LocationVector.create(lastTraceLoc,
    // firstTraceLoc);
    //// dir.setHorzDistance(1000); // project to 1000 km
    //// dir.setVertDistance(0d);
    // LocationVector dirUtil = LocationVector.create(dirBase.azimuth(),
    // 1000.0, 0.0);
    // Location projectedLoc1 = Locations.location(firstTraceLoc, dirUtil);
    //
    //
    // // get point projected from last trace point in ave trace direction
    // dirUtil = LocationVector.reverseOf(dirUtil);
    //// dir.setAzimuth(dir.getAzimuth()+180); // flip to ave trace dir
    // Location projectedLoc2 = Locations.location(lastTraceLoc, dirUtil);
    // // point down dip by adding 90 degrees to the azimuth
    // double rot90 = (dirUtil.azimuthDegrees() + 90.0) * GeoTools.TO_RAD;
    // dirUtil = LocationVector.create(rot90, dirUtil.horizontal(), 0.0);
    //// dir.setAzimuth(dir.getAzimuth()+90); // now point down dip
    //
    // // get points projected in the down dip directions at the ends of the
    // new trace
    // Location projectedLoc3 = Locations.location(projectedLoc1, dirUtil);
    //
    // Location projectedLoc4 = Locations.location(projectedLoc2, dirUtil);
    //
    //// LocationList locsForExtendedTrace = new LocationList();
    // List<Location> locsForExtendedTrace = Lists.newArrayList();
    //// LocationList locsForRegion = new LocationList();
    // List<Location> locsForRegion = Lists.newArrayList();
    //
    // locsForExtendedTrace.add(projectedLoc1);
    // locsForRegion.add(projectedLoc1);
    // for(int c=0; c<trace.size(); c++) {
    // locsForExtendedTrace.add(trace.get(c));
    // locsForRegion.add(trace.get(c));
    // }
    // locsForExtendedTrace.add(projectedLoc2);
    // locsForRegion.add(projectedLoc2);
    //
    // // finish the region
    // locsForRegion.add(projectedLoc4);
    // locsForRegion.add(projectedLoc3);

    // // write these out if in debug mode
    // if(D) {
    // System.out.println("Projected Trace:");
    // for(int l=0; l<locsForExtendedTrace.size(); l++) {
    // Location loc = locsForExtendedTrace.get(l);
    // System.out.println(loc.lat()+"\t"+ loc.lon()+"\t"+ loc.depth());
    // }
    // System.out.println("Region:");
    // for(int l=0; l<locsForRegion.size(); l++) {
    // Location loc = locsForRegion.get(l);
    // System.out.println(loc.lat()+"\t"+ loc.lon()+"\t"+ loc.depth());
    // }
    // }

    // try {
    // System.out.println("==== trace ====");
    // System.out.println(trace);
    // RegionUtils.locListToKML(extendedTrace, "distX_trace", Color.ORANGE);
    // System.out.println("==== region ====");
    // System.out.println(region);
    // RegionUtils.locListToKML(LocationList.create(region), "distX_region",
    // Color.RED);
    // System.exit(0);
    // } catch (Exception e2) {
    // e2.printStackTrace();
    // }

    Region polygon = null;
    try {
      polygon = Regions.create("",
          LocationList.create(region),
          BorderType.MERCATOR_LINEAR);
    } catch (Exception e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
      System.out.println("==== trace  ====");
      System.out.println(trace);
      // RegionUtils.locListToKML(extendedTrace, "distX_trace",
      // Color.ORANGE);
      System.out.println("==== region ====");
      System.out.println(region);
      // RegionUtils.locListToKML(LocationList.create(region),
      // "distX_region", Color.RED);
      System.exit(0);
    }
    boolean isInside = polygon.contains(siteLoc);

    double distToExtendedTrace = Locations.minDistanceToLine(
        siteLoc,
        LocationList.create(extendedTrace));

    if (isInside || distToExtendedTrace == 0.0) { // zero values are always
      // on the hanging wall
      return distToExtendedTrace;
    }
    // else
    return -distToExtendedTrace;
    // }

    // return distanceX;
  }

  // this was an experiment gone wrong; it doesn't work because there
  // are wedges of areas where points are equidistant to two segments
  // and it is difficult to determine if one is off the endpoints of
  // a segment. See Kevins distance X tests in Quad Surface and possible
  // azimuth based solution in my notes. p powers
  private static double calcDistanceX(LocationList trace, Location loc) {
    if (trace.size() == 1) {
      return 0.0;
    }

    // Compare the distance to the closest segment to the distances to the
    // endpoints. If the closest segment distance is less than both endpoint
    // distances, use that segment as a line to compute rX, otherwise use
    // endpoints of the trace as a line to compute rX
    int minIndex = Locations.minDistanceIndex(loc, trace);
    double rSeg = distanceToSegmentFast(trace.get(minIndex),
        trace.get(minIndex + 1), loc);
    double rFirst = horzDistanceFast(trace.first(), loc);
    double rLast = horzDistanceFast(trace.last(), loc);

    return (rSeg < Math.min(rFirst, rLast)) ? distanceToLineFast(
        trace.get(minIndex), trace.get(minIndex + 1), loc)
        : distanceToLineFast(trace.first(), trace.last(), loc);
  }

  /*
   * This method is used to check small distJB values for continuous, smooth
   * surfaces; e.g. non-Frankel type surfaces. This was implemented to replace
   * using a Region.contains() which can fail when dipping faults have jagged
   * traces. This method borrows from Region using a java.awt.geom.Area to
   * perform a contains test, however no checking is done of the area's
   * singularity.
   *
   * The Elsinore fault was the culprit leading to this implementation. For a
   * near-vertical (85??) strike-slip fault, it is has an unrealistic ???90 jog
   * in it. Even this method does not technically give a 100% correct answer.
   * Drawing out a steeply dipping fault with a jog will show that the resultant
   * perimeter polygon has eliminated small areas for which distJB should be
   * zero. The areas are so small though that the hazard is not likely affected.
   */
  private static boolean isDjbZero(LocationList border, Location pt) {
    Path2D path = new Path2D.Double(Path2D.WIND_EVEN_ODD, border.size());
    boolean starting = true;
    for (Location loc : border) {
      double lat = loc.lat();
      double lon = loc.lon();
      // if just starting, then moveTo
      if (starting) {
        path.moveTo(lon, lat);
        starting = false;
        continue;
      }
      path.lineTo(lon, lat);
    }
    path.closePath();
    Area area = new Area(path);
    return area.contains(pt.lon(), pt.lat());
  }

}
