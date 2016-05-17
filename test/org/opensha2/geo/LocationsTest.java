package org.opensha2.geo;

import static org.junit.Assert.*;

import static org.opensha2.geo.GeoTools.EARTH_RADIUS_MEAN;
import static org.opensha2.geo.GeoTools.TO_RAD;
import static org.opensha2.geo.Locations.angle;
import static org.opensha2.geo.Locations.areSimilar;
import static org.opensha2.geo.Locations.azimuth;
import static org.opensha2.geo.Locations.azimuthRad;
import static org.opensha2.geo.Locations.bounds;
import static org.opensha2.geo.Locations.distanceToLine;
import static org.opensha2.geo.Locations.distanceToLineFast;
import static org.opensha2.geo.Locations.distanceToSegment;
import static org.opensha2.geo.Locations.distanceToSegmentFast;
import static org.opensha2.geo.Locations.horzDistance;
import static org.opensha2.geo.Locations.horzDistanceFast;
import static org.opensha2.geo.Locations.isPole;
import static org.opensha2.geo.Locations.linearDistance;
import static org.opensha2.geo.Locations.linearDistanceFast;
import static org.opensha2.geo.Locations.location;
import static org.opensha2.geo.Locations.vertDistance;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.BeforeClass;
import org.junit.Test;
import org.opensha2.geo.Location;
import org.opensha2.geo.LocationList;
import org.opensha2.geo.Locations;

import com.google.common.collect.Lists;

import org.opensha2.geo.LocationVector;

@SuppressWarnings("javadoc")
public class LocationsTest {

  // private static LocationList locs1, locs2;
  // private static Location p1,p2,p3,p4,p5,p6,p7,p8,p9;
  //
  // private double result_p3p4_p8 = 78.62078721818267;
  // private double result_p4_p8 = 111.19505230826488;
  // private double result_p6p7_p9 = 78.55334545519592;
  // private double result_p6_p9 = 111.04266335361307;
  //
  // @BeforeClass
  // public static void setUp() {
  // p1 = Location.create(-5,0);
  // p2 = Location.create(-3,-2);
  // p3 = Location.create(-2,-2);
  // p4 = Location.create(0,0);
  // p5 = Location.create(2,2);
  // p6 = Location.create(3,2);
  // p7 = Location.create(5,0);
  //
  // p8 = Location.create(-1,0);
  // p9 = Location.create(3,1);
  //
  // locs1 = LocationList.create(p1,p2,p3,p4,p5,p6,p7);
  // locs2 = LocationList.create(p1,p3,p2,p4,p6,p5,p7);
  // }
  //
  // /**
  // * @throws java.lang.Exception
  // */
  // @BeforeClass public static void setUpBeforeClass() throws Exception {}
  //
  //
  // @Test
  // public final void testMinDistToLocation() {
  // assertEquals(result_p4_p8, ll1.minDistToLocation(p8), 0);
  // assertEquals(result_p6_p9, ll1.minDistToLocation(p9), 0);
  // assertEquals(
  // Locations.horzDistanceFast(p6, p9),
  // ll1.minDistToLocation(p9), 0);
  // assertEquals(
  // Locations.horzDistanceFast(p4, p8),
  // ll1.minDistToLocation(p8), 0);
  // }
  //
  // @Test
  // public final void testMinDistToLine() {
  // assertEquals(result_p6p7_p9, ll1.minDistToLine(p9), 0);
  // assertEquals(result_p3p4_p8, ll1.minDistToLine(p8), 0);
  // // test against underlying distToSegment
  // assertEquals(
  // Locations.distanceToLineSegmentFast(p6, p7, p9),
  // ll1.minDistToLine(p9), 0);
  // assertEquals(
  // Locations.distanceToLineSegmentFast(p3, p4, p8),
  // ll1.minDistToLine(p8), 0);
  // }

  // short-range, small-angle test points
  private static Location L1 = Location.create(32.6, 20.4);
  private static Location L2 = Location.create(32.4, 20.0);
  private static Location L3 = Location.create(32.2, 20.6);
  private static Location L4 = Location.create(32.0, 20.2, 10.0);

  // polar and long-distance, large-angle test points
  private static Location L5 = Location.create(90, 0);
  private static Location L6 = Location.create(-90, 0);

  // Expected results from methods in this class were computed using the
  // class methods and compared to the results provided by one or more
  // reputable online calculators.

  // p2p: L1 to L2 eg
  // vd: Vincenty distance (most accurate, provided for comparison)
  // sd: expected values of surfaceDistance()
  // fsd: expected values of fastSurfaceDistance()
  // angle: in radians between points
  // az-rad: azimuth in radians from L1 to L2
  // az-deg: azimuth in degrees from L1 to L2

  // p2p vd (km) sd fsd angle az-rad az-deg
  // --- --------- --------- --------- ----------- ----------- ---------
  // d51 6393.578 6382.596 6474.888 1.001818991 3.141592654 180.0
  // d25 6415.757 6404.835 6493.824 1.005309649 0.0 0.0
  // d46 13543.818 13565.796 13707.303 2.129301687 3.141592654 180.0
  // d63 13565.996 13588.035 13735.216 2.132792346 0.0 0.0

  // d12 43.645957 43.6090311 43.6090864 0.006844919 4.179125015 239.44623
  // d13 48.183337 48.2790582 48.2790921 0.007577932 2.741190313 157.05864
  // d14 69.150258 69.3145862 69.3146382 0.010879690 3.417161139 195.78891
  // d23 60.706703 60.6198752 60.6200022 0.009514959 1.943625801 111.36156
  // d42 48.198212 48.2952067 48.2952403 0.007580467 5.883856933 337.12017
  // d43 43.787840 43.7518411 43.7518956 0.006867335 1.035735858 59.34329

  // fdtl dtl
  // d321 34.472999888 34.425229936
  // d231 34.472999888 -34.425229936
  // d432 47.859144611 -47.851004687
  // d413 30.170948729 30.205855981

  // deltas - based on what decimal place known values above were clipped
  private static double ldD = 0.001; // long-distance
  private static double sdD = 0.0000001; // short-distance
  private static double angleD = 0.000000001; // angle
  private static double azrD = 0.000000001; // azimuth-rad
  private static double azdD = 0.00001; // azimuth-deg
  private static double dtlD = 0.000000001; // dist to line

  // @Test
  // public final void testLocations() {
  // // silly test of no arg private constructor
  // try {
  // Object obj = TestUtils.callPrivateNoArgConstructor(
  // Locations.class);
  // } catch (Exception e) {
  // fail("Private no-arg constructor failed to initialize: " +
  // e.getMessage());
  // }
  // }

  @Test
  public final void testAngle() {
    assertEquals(1.001818991, angle(L5, L1), angleD);
    assertEquals(1.005309649, angle(L2, L5), angleD);
    assertEquals(2.129301687, angle(L4, L6), angleD);
    assertEquals(2.132792346, angle(L6, L3), angleD);
    assertEquals(0.006844919, angle(L1, L2), angleD);
    assertEquals(0.007577932, angle(L1, L3), angleD);
    assertEquals(0.010879690, angle(L1, L4), angleD);
    assertEquals(0.009514959, angle(L2, L3), angleD);
    assertEquals(0.007580467, angle(L4, L2), angleD);
    assertEquals(0.006867335, angle(L4, L3), angleD);
  }

  @Test
  public final void testHorzDistance() {
    assertEquals(6382.596, horzDistance(L5, L1), ldD);
    assertEquals(6404.835, horzDistance(L2, L5), ldD);
    assertEquals(13565.796, horzDistance(L4, L6), ldD);
    assertEquals(13588.035, horzDistance(L6, L3), ldD);
    assertEquals(43.6090311, horzDistance(L1, L2), sdD);
    assertEquals(48.2790582, horzDistance(L1, L3), sdD);
    assertEquals(69.3145862, horzDistance(L1, L4), sdD);
    assertEquals(60.6198752, horzDistance(L2, L3), sdD);
    assertEquals(48.2952067, horzDistance(L4, L2), sdD);
    assertEquals(43.7518411, horzDistance(L4, L3), sdD);
  }

  // same locations, different reference frame
  // distance between 1 and 2 should be the same as between 4 and 5
  // P3 and P6 are used to test distanceToLineFast
  private static Location P1 = Location.create(32.0, -160.0, 2.0);
  private static Location P2 = Location.create(31.0, -163.0, 0.0);
  private static Location P3 = Location.create(33.0, -164.0, 0.0);
  private static Location P4 = Location.create(32.0, 200.0, 2.0);
  private static Location P5 = Location.create(31.0, 197.0, 0.0);
  private static Location P6 = Location.create(33.0, 196.0, 0.0);

  @Test
  public final void testHorzDistanceFast() {
    assertEquals(6474.888, horzDistanceFast(L5, L1), ldD);
    assertEquals(6493.824, horzDistanceFast(L2, L5), ldD);
    assertEquals(13707.303, horzDistanceFast(L4, L6), ldD);
    assertEquals(13735.216, horzDistanceFast(L6, L3), ldD);
    assertEquals(43.6090864, horzDistanceFast(L1, L2), sdD);
    assertEquals(48.2790921, horzDistanceFast(L1, L3), sdD);
    assertEquals(69.3146382, horzDistanceFast(L1, L4), sdD);
    assertEquals(60.6200022, horzDistanceFast(L2, L3), sdD);
    assertEquals(48.2952403, horzDistanceFast(L4, L2), sdD);
    assertEquals(43.7518956, horzDistanceFast(L4, L3), sdD);

    // test change to allow lon values up to 360
    double original = horzDistanceFast(P1, P2);
    double updated = horzDistanceFast(P4, P5);
    assertEquals(original, updated, sdD);
  }

  @Test
  public final void testVertDistance() {
    Location L1 = Location.create(23, -32, 2);
    Location L2 = Location.create(-12, -112, -2);
    Location L3 = Location.create(-34, 86, 10);
    assertEquals(-4, vertDistance(L1, L2), 0);
    assertEquals(8, vertDistance(L1, L3), 0);
    assertEquals(12, vertDistance(L2, L3), 0);
  }

  @Test
  public final void testLinearDistance() {
    double delta = 0.000000001;

    // small angles
    Location L1 = Location.create(20.0, 20.0, 2);
    Location L2 = Location.create(20.1, 20.1, 2);
    Location L3 = Location.create(20.1, 20.1, 17);
    double sd12 = horzDistance(L1, L2); // 15.256270609
    double ld12 = linearDistance(L1, L2); // 15.251477684
    double ld13 = linearDistance(L1, L3); // 21.378955649

    assertTrue("Linear distance should be shorter", ld12 < sd12);
    assertTrue("ld12 should be shorter than ld13", ld13 > ld12);
    assertEquals(15.251477684, ld12, delta);
    assertEquals(21.378955649, ld13, delta);

    // large angles
    Location L4 = Location.create(45.0, -20.0, 2);
    Location L5 = Location.create(-40.0, 20.0, 17);
    Location L6 = Location.create(-50.0, 20.0, 17);
    double ld45 = linearDistance(L4, L5); // 9172.814801278
    double ld46 = linearDistance(L4, L6); // 9828.453361410

    assertEquals(9172.814801278, ld45, delta);
    assertEquals(9828.453361410, ld46, delta);
  }

  @Test
  public final void testLinearDistanceFast() {
    double delta = 0.000000001;

    // small angles
    Location L1 = Location.create(20.0, 20.0, 2);
    Location L2 = Location.create(20.1, 20.1, 2);
    Location L3 = Location.create(20.1, 20.1, 17);
    double ld12 = linearDistanceFast(L1, L2); // 15.256271986
    double ld13 = linearDistanceFast(L1, L3); // 21.395182516
    assertEquals(15.256271986, ld12, delta);
    assertEquals(21.395182516, ld13, delta);

    // test change to allow lon values up to 360
    double original = linearDistanceFast(P1, P2);
    double updated = linearDistanceFast(P4, P5);
    assertEquals(original, updated, sdD);
  }

  // additional locoations for line and segment tests
  private static Location l1 = Location.create(2, 0);
  private static Location l2 = Location.create(4, 0);

  private static Location p1 = Location.create(1, 0);
  private static Location p2 = Location.create(1, -1);
  private static Location p3 = Location.create(3, -1);
  private static Location p4 = Location.create(5, -1);
  private static Location p5 = Location.create(5, 0);
  private static Location p6 = Location.create(5, 1);
  private static Location p7 = Location.create(3, 1);
  private static Location p8 = Location.create(1, 1);

  @Test
  public final void testDistanceToLine() {
    // set 1
    assertEquals(34.425229936, distanceToLine(L3, L2, L1), dtlD);
    assertEquals(-34.425229936, distanceToLine(L2, L3, L1), dtlD);
    assertEquals(-47.851004687, distanceToLine(L4, L3, L2), dtlD);
    assertEquals(30.205855981, distanceToLine(L4, L1, L3), dtlD);
    // set 2
    assertEquals(0.0, distanceToLine(l1, l2, p1), 0);
    assertEquals(-111.17811504377568, distanceToLine(l1, l2, p2), 0);
    assertEquals(-111.04264791008788, distanceToLine(l1, l2, p3), 0);
    assertEquals(-110.77187883896175, distanceToLine(l1, l2, p4), 0);
    assertEquals(0.0, distanceToLine(l1, l2, p5), 0);
    assertEquals(110.7718788389617, distanceToLine(l1, l2, p6), 0);
    assertEquals(111.04264791008788, distanceToLine(l1, l2, p7), 0);
    assertEquals(111.17811504377575, distanceToLine(l1, l2, p8), 0);
  }

  @Test
  public final void testDistanceToLineFast() {
    // set 1
    assertEquals(34.472999888, distanceToLineFast(L3, L2, L1), dtlD);
    assertEquals(-34.472999888, distanceToLineFast(L2, L3, L1), dtlD);
    assertEquals(-47.859144611, distanceToLineFast(L4, L3, L2), dtlD);
    assertEquals(30.170948729, distanceToLineFast(L4, L1, L3), dtlD);
    // set 2
    assertEquals(0.0, distanceToLineFast(l1, l2, p1), 0);
    assertEquals(-111.12731528678844, distanceToLineFast(l1, l2, p2), 0);
    assertEquals(-111.04266335361307, distanceToLineFast(l1, l2, p3), 0);
    assertEquals(-110.92418674948573, distanceToLineFast(l1, l2, p4), 0);
    assertEquals(0.0, distanceToLineFast(l1, l2, p5), 0);
    assertEquals(110.92418674948573, distanceToLineFast(l1, l2, p6), 0);
    assertEquals(111.04266335361307, distanceToLineFast(l1, l2, p7), 0);
    assertEquals(111.12731528678844, distanceToLineFast(l1, l2, p8), 0);

    // test change to allow lon values up to 360
    double original = distanceToLineFast(P2, P3, P1);
    double updated = distanceToLineFast(P5, P6, P4);
    assertEquals(original, updated, sdD);
  }

  @Test
  public final void testDistanceToSegment() {
    // set 1
    assertEquals(34.425229936, distanceToSegment(L3, L2, L1), dtlD);
    assertEquals(34.425229936, distanceToSegment(L2, L3, L1), dtlD);
    assertEquals(47.851004687, distanceToSegment(L4, L3, L2), dtlD);
    assertEquals(30.205855981, distanceToSegment(L4, L1, L3), dtlD);
    // set 2
    assertEquals(111.19505230826488, distanceToSegment(l1, l2, p1), 0);
    assertEquals(157.22560972181338, distanceToSegment(l1, l2, p2), 0);
    assertEquals(111.04264791008788, distanceToSegment(l1, l2, p3), 0);
    assertEquals(157.0103400810619, distanceToSegment(l1, l2, p4), 0);
    assertEquals(111.19505230826486, distanceToSegment(l1, l2, p5), 0);
    assertEquals(157.0103400810619, distanceToSegment(l1, l2, p6), 0);
    assertEquals(111.04264791008788, distanceToSegment(l1, l2, p7), 0);
    assertEquals(157.22560972181338, distanceToSegment(l1, l2, p8), 0);
  }

  @Test
  public final void testDistanceToSegmentFast() {
    // set 1
    assertEquals(34.472999888, distanceToSegmentFast(L3, L2, L1), dtlD);
    assertEquals(34.472999888, distanceToSegmentFast(L2, L3, L1), dtlD);
    assertEquals(47.859144611, distanceToSegmentFast(L4, L3, L2), dtlD);
    assertEquals(30.170948729, distanceToSegmentFast(L4, L1, L3), dtlD);
    // set 2
    assertEquals(111.19505230826488, distanceToSegmentFast(l1, l2, p1), 0);
    assertEquals(157.2056610325692, distanceToSegmentFast(l1, l2, p2), 0);
    assertEquals(111.04266335361307, distanceToSegmentFast(l1, l2, p3), 0);
    assertEquals(157.0621369518209, distanceToSegmentFast(l1, l2, p4), 0);
    assertEquals(111.19505230826486, distanceToSegmentFast(l1, l2, p5), 0);
    assertEquals(157.0621369518209, distanceToSegmentFast(l1, l2, p6), 0);
    assertEquals(111.04266335361307, distanceToSegmentFast(l1, l2, p7), 0);
    assertEquals(157.2056610325692, distanceToSegmentFast(l1, l2, p8), 0);

    // test change to allow lon values up to 360
    double original = distanceToSegmentFast(P2, P3, P1);
    double updated = distanceToSegmentFast(P5, P6, P4);
    assertEquals(original, updated, sdD);
  }

  @Test
  public final void testAzimuth() {
    assertEquals(180.0, azimuth(L5, L1), azdD);
    assertEquals(0.0, azimuth(L2, L5), azdD);
    assertEquals(180.0, azimuth(L4, L6), azdD);
    assertEquals(0.0, azimuth(L6, L3), azdD);
    assertEquals(239.44623, azimuth(L1, L2), azdD);
    assertEquals(157.05864, azimuth(L1, L3), azdD);
    assertEquals(195.78891, azimuth(L1, L4), azdD);
    assertEquals(111.36156, azimuth(L2, L3), azdD);
    assertEquals(337.12017, azimuth(L4, L2), azdD);
    assertEquals(59.34329, azimuth(L4, L3), azdD);
  }

  @Test
  public final void testAzimuthRad() {
    assertEquals(3.141592654, azimuthRad(L5, L1), azrD);
    assertEquals(0.0, azimuthRad(L2, L5), azrD);
    assertEquals(3.141592654, azimuthRad(L4, L6), azrD);
    assertEquals(0.0, azimuthRad(L6, L3), azrD);
    assertEquals(4.179125015, azimuthRad(L1, L2), azrD);
    assertEquals(2.741190313, azimuthRad(L1, L3), azrD);
    assertEquals(3.417161139, azimuthRad(L1, L4), azrD);
    assertEquals(1.943625801, azimuthRad(L2, L3), azrD);
    assertEquals(5.883856933, azimuthRad(L4, L2), azrD);
    assertEquals(1.035735858, azimuthRad(L4, L3), azrD);
  }

  @Test
  public final void testLocationLocationDoubleDouble() {
    LocationVector d = LocationVector.create(L1, L2);
    // TODO need to switch to getAzimuthRad
    assertTrue(areSimilar(L2, location(
      L1, d.azimuth(), d.horizontal())));
    d = LocationVector.create(L1, L3);
    assertTrue(areSimilar(L3, location(
      L1, d.azimuth(), d.horizontal())));
    d = LocationVector.create(L2, L3);
    assertTrue(areSimilar(L3, location(
      L2, d.azimuth(), d.horizontal())));
  }

  @Test
  public final void testLocationLocationDirection() {
    assertTrue(areSimilar(L2, location(L1, LocationVector.create(L1, L2))));
    assertTrue(areSimilar(L3, location(L1, LocationVector.create(L1, L3))));
    assertTrue(areSimilar(L4, location(L1, LocationVector.create(L1, L4))));
    assertTrue(areSimilar(L3, location(L2, LocationVector.create(L2, L3))));
    assertTrue(areSimilar(L2, location(L4, LocationVector.create(L4, L2))));
    assertTrue(areSimilar(L3, location(L4, LocationVector.create(L4, L3))));
  }

  @Test
  public final void testLocationVector() {
    assertEquals(43.6090311, LocationVector.create(L1, L2).horizontal(), sdD);
    assertEquals(48.2790582, LocationVector.create(L1, L3).horizontal(), sdD);
    assertEquals(69.3145862, LocationVector.create(L1, L4).horizontal(), sdD);
    assertEquals(60.6198752, LocationVector.create(L2, L3).horizontal(), sdD);
    assertEquals(48.2952067, LocationVector.create(L4, L2).horizontal(), sdD);
    assertEquals(43.7518411, LocationVector.create(L4, L3).horizontal(), sdD);

    // TODO these will need to be replaced with azimuthRad()
    assertEquals(239.44623, LocationVector.create(L1, L2).azimuthDegrees(), azdD);
    assertEquals(157.05864, LocationVector.create(L1, L3).azimuthDegrees(), azdD);
    assertEquals(195.78891, LocationVector.create(L1, L4).azimuthDegrees(), azdD);
    assertEquals(111.36156, LocationVector.create(L2, L3).azimuthDegrees(), azdD);
    assertEquals(337.12017, LocationVector.create(L4, L2).azimuthDegrees(), azdD);
    assertEquals(59.34329, LocationVector.create(L4, L3).azimuthDegrees(), azdD);

    assertEquals(0, LocationVector.create(L1, L2).vertical(), 0);
    assertEquals(0, LocationVector.create(L1, L3).vertical(), 0);
    assertEquals(10, LocationVector.create(L1, L4).vertical(), 0);
    assertEquals(0, LocationVector.create(L2, L3).vertical(), 0);
    assertEquals(-10, LocationVector.create(L4, L2).vertical(), 0);
    assertEquals(-10, LocationVector.create(L4, L3).vertical(), 0);

    // reverse tests
    Location L1 = Location.create(20.0, 20.0, 0);
    Location L2 = Location.create(20.1, 20.1, 2);
    LocationVector v = LocationVector.create(L1, L2);
    double az = v.azimuthDegrees();
    double dv = v.vertical();
    LocationVector vr = LocationVector.reverseOf(v);
    assertEquals((az + 180) % 360, vr.azimuthDegrees(), 0);
    assertEquals(-dv, vr.vertical(), 0);

    // test plunge
    LocationVector vPlunge = LocationVector.create(0.0, 2, 2);
    assertEquals(45.0, vPlunge.plungeDegrees(), 0.0);
    vPlunge = LocationVector.create(0.0, 2, -2);
    assertEquals(-45.0, vPlunge.plungeDegrees(), 0.0);

  }

  @Test
  public final void testBisect() {
    double tol = 0.000000000001;
    Location p1, p2, p3;
    LocationVector p2p1, p2p3, vTest;

    // general case
    p2 = Location.create(20, 20);
    p2p1 = LocationVector.create(220 * TO_RAD, 100, 0);
    p1 = Locations.location(p2, p2p1);
    p2p3 = LocationVector.create(90 * TO_RAD, 100, 0);
    p3 = Locations.location(p2, p2p3);
    vTest = Locations.bisect(p1, p2, p3);
    assertEquals(155, vTest.azimuthDegrees(), tol);

    // 4th quadrant 270-360
    p2p1 = LocationVector.create(320 * TO_RAD, 100, 0);
    p1 = Locations.location(p2, p2p1);
    p2p3 = LocationVector.create(20 * TO_RAD, 100, 0);
    p3 = Locations.location(p2, p2p3);
    vTest = Locations.bisect(p1, p2, p3);
    assertEquals(170, vTest.azimuthDegrees(), tol);

    // p1 & p3 coincident
    p2p1 = LocationVector.create(90 * TO_RAD, 100, 0);
    p1 = Locations.location(p2, p2p1);
    p2p3 = LocationVector.create(90 * TO_RAD, 100, 0);
    p3 = Locations.location(p2, p2p3);
    vTest = Locations.bisect(p1, p2, p3);
    assertEquals(90, vTest.azimuthDegrees(), tol);

    // p1, p2, & p3 coincident
    vTest = Locations.bisect(p2, p2, p2);
    assertEquals(0, vTest.azimuthDegrees(), tol);
  }

  @Test
  public final void testIsPole() {
    Location sp = Location.create(-89.999999999999, 0);
    Location np = Location.create(89.999999999999, 0);
    Location ll = Location.create(22, 150);
    assertTrue(isPole(sp));
    assertTrue(isPole(np));
    assertTrue(!isPole(ll));
  }

  @Test
  public final void testAreSimilar() {
    // NOTE the generic tolerance of 0.000000000001 in Locations imposes
    // different magnitude constraints on depth vs. lat lon
    Location p1, p2;
    // compare lats
    p1 = Location.create(30, 0, 0);
    p2 = Location.create(30.00000000001, 0, 0);
    assertTrue(areSimilar(p1, p2));
    p2 = Location.create(30.0000000001, 0, 0);
    assertTrue(!areSimilar(p1, p2));
    // compare lons
    p1 = Location.create(0, -30.0, 0);
    p2 = Location.create(0, -30.00000000001, 0);
    assertTrue(areSimilar(p1, p2));
    p2 = Location.create(0, -30.0000000001, 0);
    assertTrue(!areSimilar(p1, p2));
    // compare depths
    p1 = Location.create(0, 0, 5.0);
    p2 = Location.create(0, 0, 5.0000000000001);
    assertTrue(areSimilar(p1, p2));
    p2 = Location.create(0, 0, 5.000000000001);
    assertTrue(!areSimilar(p1, p2));
  }

  @Test
  public void testBounds() {
    Location p1 = Location.create(-10.0, -10.0);
    Location p2 = Location.create(-10.0, 10.0);
    Location p3 = Location.create(10.0, 10.0);
    Location p4 = Location.create(10.0, -10.0);
    LocationList locs = LocationList.create(p1, p2, p3, p4);
    Bounds b = bounds(locs);
    Location min = Location.create(-10.0, -10.0);
    Location max = Location.create(10.0, 10.0);
    assertEquals(min, b.min());
    assertEquals(max, b.max());
  }

  @Test(expected = IllegalArgumentException.class)
  public void testBoundsIAE() {
    List<Location> locs = Lists.newArrayList();
    bounds(locs);
  }

  /**
   * DEVELOPER NOTE
   * 
   * Test value generation along with various speed comparisons provided below.
   * Speed tests can be run with fixed or randomized values; randomization
   * generally only adds a set amount of time to each test.
   * 
   * Internal methods marked with *OLD were removed intact from Locations to
   * preserve history and preserve ability to document performance enhancements.
   */
  public static void main(String[] args) {

    // shared convenience fields
    Location L1, L2, L3, L4, L5, L6;
    int numIter = 1000000;

    // flag for using fixed (vs random) values in speed tests
    boolean fixedVals = true;

    // ==========================================================
    // VALUE GENERATION
    // ==========================================================

    L1 = Location.create(32.6, 20.4);
    L2 = Location.create(32.4, 20);
    L3 = Location.create(32.2, 20.6);
    L4 = Location.create(32, 20.2, 10);

    L5 = Location.create(90, 0);
    L6 = Location.create(-90, 0);

    // vd sd fsd angle az-rad az-deg
    // d51 6393.578 km 6382.596 6474.888 1.001818991 3.141592654 180.0
    // d25 6415.757 km 6404.835 6493.824 1.005309649 0.0 0.0
    // d46 13543.818 km 13565.796 13707.303 2.129301687 3.141592654 180.0
    // d63 13565.996 km 13588.035 13735.216 2.132792346 0.0 0.0

    // d12 43.645957 km 43.6090311 43.6090864 0.006844919 4.179125015 239.44623
    // d13 48.183337 km 48.2790582 48.2790921 0.007577932 2.741190313 157.05864
    // d14 69.150258 km 69.3145862 69.3146382 0.010879690 3.417161139 195.78891
    // d23 60.706703 km 60.6198752 60.6200022 0.009514959 1.943625801 111.36156
    // d42 48.198212 km 48.2952067 48.2952403 0.007580467 5.883856933 337.12017
    // d43 43.787840 km 43.7518411 43.7518956 0.006867335 1.035735858 59.34329

    // fdtl dtl
    // d321 34.472999888 34.425229936
    // d231 34.472999888 -34.425229936
    // d432 47.859144611 -47.851004687
    // d413 30.170948729 30.205855981

    Location p1 = L1;
    Location p2 = L2;
    Location p3 = L3;
    System.out.println(horzDistance(p1, p2));
    System.out.println(horzDistanceFast(p1, p2));
    System.out.println(angle(p1, p2));
    System.out.println(azimuthRad(p1, p2));
    System.out.println(azimuth(p1, p2));
    System.out.println(distanceToLineFast(p1, p2, p3));
    System.out.println(distanceToLine(p1, p2, p3));

    // ==========================================================
    // Distance to Line Methods
    //
    // Summary: the highly accurate Haversine based formula is
    // much slower (up to 20x), but does not work
    // accross dateline and does not indicate
    // sidedness.
    // 1M repeat runs showed the following comp
    // times for fixed location pairs:
    //
    // DTL distanceToLine() 1600 ms
    // DTLFo distanceToLineFastOLD() 120 ms
    // DTLF distanceToLineFast() 1 ms
    // DTS distanceToSegment() 1 ms
    // DTSF distanceToSegmentFast() 1 ms
    //
    //
    // ==========================================================

    L2 = Location.create(32, -116);
    L1 = Location.create(37, -115);
    L3 = Location.create(34, -114);

    System.out.println("\nSPEED TEST -- Distance to Line\n");
    System.out.println("distanceToLine(): " +
      distanceToLine(L1, L2, L3));
    for (int i = 0; i < 5; i++) {
      long T = System.currentTimeMillis();
      double d;
      for (int j = 0; j < numIter; j++) {
        d = (fixedVals) ? distanceToLine(L1, L2, L3)
          : distanceToLine(randomLoc(), randomLoc(), randomLoc());
      }
      T = (System.currentTimeMillis() - T);
      System.out.println("    DTL: " + T);
    }

    System.out.println("distToLineFastOLD(): " +
      distanceToLineFastOLD(L1, L2, L3));
    for (int i = 0; i < 5; i++) {
      long T = System.currentTimeMillis();
      double d;
      for (int j = 0; j < numIter; j++) {
        d = (fixedVals) ? distanceToLineFastOLD(L1, L2, L3) : distanceToLineFastOLD(
          randomLoc(), randomLoc(), randomLoc());
      }
      T = (System.currentTimeMillis() - T);
      System.out.println("  DTLFo: " + T);
    }

    System.out.println("distanceToLineFast(): " +
      distanceToLineFast(L1, L2, L3));
    for (int i = 0; i < 5; i++) {
      long T = System.currentTimeMillis();
      double d;
      for (int j = 0; j < numIter; j++) {
        d = (fixedVals) ? distanceToLineFast(L1, L2, L3)
          : distanceToLineFast(randomLoc(), randomLoc(), randomLoc());
      }
      T = (System.currentTimeMillis() - T);
      System.out.println("   DTLF: " + T);
    }

    System.out.println("distanceToSegment(): " +
      distanceToSegment(L1, L2, L3));
    for (int i = 0; i < 5; i++) {
      long T = System.currentTimeMillis();
      double d;
      for (int j = 0; j < numIter; j++) {
        d = (fixedVals) ? distanceToSegment(L1, L2, L3)
          : distanceToSegment(randomLoc(), randomLoc(), randomLoc());
      }
      T = (System.currentTimeMillis() - T);
      System.out.println("    DTS: " + T);
    }

    System.out.println("distanceToSegmentFast(): " +
      distanceToSegmentFast(L1, L2, L3));
    for (int i = 0; i < 5; i++) {
      long T = System.currentTimeMillis();
      double d;
      for (int j = 0; j < numIter; j++) {
        d = (fixedVals) ? distanceToSegmentFast(L1, L2, L3)
          : distanceToSegmentFast(randomLoc(), randomLoc(), randomLoc());
      }
      T = (System.currentTimeMillis() - T);
      System.out.println("   DTSF: " + T);
    }

    // // ==========================================================
    // // Horizontal (Surface) Distance Methods
    // //
    // // Summary: Accurate, Haversine based methods of distance
    // // calculation have beeen shown to be much faster
    // // than existing methods (e.g. getHorzDistance).
    // // 1M repeat runs showed the following comp
    // // times for fixed location pairs:
    // //
    // // HDo getHorizDistanceOLD() 1285 ms
    // // AHDo getApproxHorzDistanceOLD() 955 ms
    // // HD horzDistance() 230 ms
    // // HDF horzDistanceFast() 1 ms
    // // ==========================================================
    //
    // // long pair ~9K km : discrepancies > 100km
    // // L1 = new Location(20,-10);
    // // L2 = new Location(-20,60);
    //
    // // mid pair ~250 km : discrepancies in 10s of meters
    // // L1 = new Location(32.1,-117.2);
    // // L2 = new Location(33.8, -115.4);
    //
    // // short pair : negligible discrepancy in values
    // L1 = new Location(32.132,-117.21);
    // L2 = new Location(32.306, -117.105);
    //
    // System.out.println("\nSPEED TEST -- Horizontal Distance\n");
    // System.out.println("getHorzDistanceOLD(): " +
    // getHorzDistanceOLD(L1, L2));
    // for (int i=0; i < 5; i++) {
    // long T = System.currentTimeMillis();
    // double d;
    // for (int j=0; j<numIter; j++) {
    // d = (fixedVals) ?
    // getHorzDistanceOLD(L1, L2) :
    // getHorzDistanceOLD(randomLoc(),randomLoc());
    // }
    // T = (System.currentTimeMillis() - T);
    // System.out.println(" HDo: " + T);
    // }
    //
    // System.out.println("getApproxHorzDistanceOLD(): " +
    // getApproxHorzDistanceOLD(L1, L2));
    // for (int i=0; i < 5; i++) {
    // long T = System.currentTimeMillis();
    // double d;
    // for (int j=0; j<numIter; j++) {
    // d = (fixedVals) ?
    // getApproxHorzDistanceOLD(L1, L2) :
    // getApproxHorzDistanceOLD(randomLoc(),randomLoc());
    // }
    // T = (System.currentTimeMillis() - T);
    // System.out.println(" ADo: " + T);
    // }
    //
    // System.out.println("horzDistance(): " +
    // horzDistance(L1, L2));
    // for (int i=0; i < 5; i++) {
    // long T = System.currentTimeMillis();
    // double d;
    // for (int j=0; j<numIter; j++) {
    // d = (fixedVals) ?
    // horzDistance(L1, L2) :
    // horzDistance(randomLoc(),randomLoc());
    // }
    // T = (System.currentTimeMillis() - T);
    // System.out.println(" HD: " + T);
    // }
    //
    // System.out.println("horzDistanceFast(): " +
    // horzDistanceFast(L1, L2));
    // for (int i=0; i < 5; i++) {
    // long T = System.currentTimeMillis();
    // double d;
    // for (int j=0; j<numIter; j++) {
    // d = (fixedVals) ?
    // horzDistanceFast(L1, L2) :
    // horzDistanceFast(randomLoc(),randomLoc());
    // }
    // T = (System.currentTimeMillis() - T);
    // System.out.println(" HDF: " + T);
    // }
    //
    //
    //
    //
    // // ==========================================================
    // // Linear Distance Methods
    // //
    // // Summary: Accurate, Haversine based methods of distance
    // // calculation have beeen shown to be much faster
    // // than existing methods (e.g. getHorzDistance).
    // // 1M repeat runs showed the following comp
    // // times for fixed location pairs:
    // //
    // // TDo getTotalDistanceOLD() 1300 ms
    // // LD linearDistance() 240 ms
    // // LDF linearDistanceFast() 1 ms
    // // ==========================================================
    //
    // // mid pair ~250 km : discrepancies in 10s of meters
    // L1 = new Location(32.1,-117.2);
    // L2 = new Location(33.8, -115.4);
    //
    // // short pair : negligible discrepancy in values
    // // L1 = new Location(32.132,-117.21);
    // // L2 = new Location(32.306, -117.105);
    //
    // System.out.println("\nSPEED TEST -- Linear Distance\n");
    // System.out.println("getTotalDistanceOLD(): " +
    // getTotalDistanceOLD(L1, L2));
    // for (int i=0; i < 5; i++) {
    // long T = System.currentTimeMillis();
    // double d;
    // for (int j=0; j<numIter; j++) {
    // d = (fixedVals) ?
    // getTotalDistanceOLD(L1, L2) :
    // getTotalDistanceOLD(randomLoc(),randomLoc());
    // }
    // T = (System.currentTimeMillis() - T);
    // System.out.println(" TDo: " + T);
    // }
    //
    // System.out.println("linearDistance(): " +
    // linearDistance(L1, L2));
    // for (int i=0; i < 5; i++) {
    // long T = System.currentTimeMillis();
    // double d;
    // for (int j=0; j<numIter; j++) {
    // d = (fixedVals) ?
    // linearDistance(L1, L2) :
    // linearDistance(randomLoc(),randomLoc());
    // }
    // T = (System.currentTimeMillis() - T);
    // System.out.println(" LD: " + T);
    // }
    //
    // System.out.println("linearDistanceFast(): " +
    // linearDistanceFast(L1, L2));
    // for (int i=0; i < 5; i++) {
    // long T = System.currentTimeMillis();
    // double d;
    // for (int j=0; j<numIter; j++) {
    // d = (fixedVals) ?
    // linearDistanceFast(L1, L2) :
    // linearDistanceFast(randomLoc(),randomLoc());
    // }
    // T = (System.currentTimeMillis() - T);
    // System.out.println(" LDF: " + T);
    // }
    //
    //
    //
    // // ==========================================================
    // // Azimuth Methods
    // //
    // // Summary: New, spherical geometry azimuth methods are
    // // faster than existing methods.
    // // 1M repeat runs showed the following comp
    // // times for fixed location pairs:
    // //
    // // gAo getAzimuthOLD() 1240 ms
    // // A azimuth() 348 ms
    // // ==========================================================
    //
    // L1 = new Location(32, -117);
    // L2 = new Location(33, -115);
    //
    // System.out.println("\nSPEED TEST -- Azimuth\n");
    // System.out.println("getAzimuthOLD(): " +
    // getAzimuthOLD(L1, L2));
    // for (int i=0; i < 5; i++) {
    // long T = System.currentTimeMillis();
    // double d;
    // for (int j=0; j<numIter; j++) {
    // d = (fixedVals) ?
    // getAzimuthOLD(L1, L2) :
    // getAzimuthOLD(randomLoc(),randomLoc());
    // }
    // T = (System.currentTimeMillis() - T);
    // System.out.println(" gAo: " + T);
    // }
    //
    // System.out.println("azimuth(): " +
    // azimuth(L1, L2));
    // for (int i=0; i < 5; i++) {
    // long T = System.currentTimeMillis();
    // double d;
    // for (int j=0; j<numIter; j++) {
    // d = (fixedVals) ?
    // azimuth(L1, L2) :
    // azimuth(randomLoc(),randomLoc());
    // }
    // T = (System.currentTimeMillis() - T);
    // System.out.println(" A: " + T);
    // }
    //
    //
    //
    //
    // // ==========================================================
    // // Vector Methods
    // //
    // // Summary: New, spherical geometry direction methods are
    // // faster than existing methods. A test using
    // // horzDistanceFast instead of horzDistance
    // // realized no speed gain.
    // // 1M repeat runs showed the following comp
    // // times for fixed location pairs:
    // //
    // // gDo getDirectionOLD() 3700 ms
    // // V vector() 610 ms
    // // ==========================================================
    //
    // L1 = new Location(32, -117);
    // L2 = new Location(33, -115);
    //
    // System.out.println("\nSPEED TEST -- LocationVector\n");
    // System.out.println("getDirectionOLD(): " + getDirectionOLD(L1, L2));
    // for (int i=0; i < 5; i++) {
    // long T = System.currentTimeMillis();
    // LocationVector d;
    // for (int j=0; j<numIter; j++) {
    // d = (fixedVals) ?
    // getDirectionOLD(L1, L2) :
    // getDirectionOLD(randomLoc(),randomLoc());
    // }
    // T = (System.currentTimeMillis() - T);
    // System.out.println(" gDo: " + T);
    // }
    //
    // System.out.println("vector(): " + vector(L1, L2));
    // for (int i=0; i < 5; i++) {
    // long T = System.currentTimeMillis();
    // LocationVector d;
    // for (int j=0; j<numIter; j++) {
    // d = (fixedVals) ?
    // vector(L1, L2) :
    // vector(randomLoc(),randomLoc());
    // }
    // T = (System.currentTimeMillis() - T);
    // System.out.println(" V: " + T);
    // }
    //
    //
    //
    // // ==========================================================
    // // Location Methods
    // //
    // // Summary: New, spherical geometry direction methods are
    // // slightly faster than existing methods.
    // // 1M repeat runs showed the following comp
    // // times for fixed location pairs:
    // //
    // // gLo getLocationOLD() 915 ms
    // // L location() 670 ms
    // // ==========================================================
    //
    // L1 = new Location(32, -117);
    // L2 = new Location(33, -115);
    // LocationVector dir = new LocationVector(20,111,10);
    // System.out.println("\nSPEED TEST -- Location\n");
    // System.out.println("getLocationOLD(): " + getLocationOLD(L1, dir));
    // for (int i=0; i < 5; i++) {
    // long T = System.currentTimeMillis();
    // Location loc;
    // for (int j=0; j<numIter; j++) {
    // loc = (fixedVals) ?
    // getLocationOLD(L1, dir) :
    // getLocationOLD(randomLoc(),dir);
    // }
    // T = (System.currentTimeMillis() - T);
    // System.out.println(" gL: " + T);
    // }
    //
    // System.out.println("location(): " + location(L1, dir));
    // for (int i=0; i < 5; i++) {
    // long T = System.currentTimeMillis();
    // Location loc;
    // for (int j=0; j<numIter; j++) {
    // loc = (fixedVals) ?
    // location(L1, dir) :
    // location(randomLoc(),dir);
    // }
    // T = (System.currentTimeMillis() - T);
    // System.out.println(" L: " + T);
    // }
    //
    //
    //
    // // ==========================================================
    // // The following code may be used to explore how old and
    // // new distance caclulation methods compare and how
    // // results cary with distance
    // // ==========================================================
    //
    // // commented values are accurate distances computed
    // // using the Vincenty formula
    //
    // Location L1a = new Location(20,-10); // 8818.496 km
    // Location L1b = new Location(-20,60);
    //
    // Location L2a = new Location(90,10); // 4461.118 km
    // Location L2b = new Location(50,80);
    //
    // Location L3a = new Location(-80,-30); // 3824.063 km
    // Location L3b = new Location(-50,20);
    //
    // Location L4a = new Location(-42,178); // 560.148 km
    // Location L4b = new Location(-38,-178);
    //
    // Location L5a = new Location(5,-90); // 784.028 km
    // Location L5b = new Location(0,-85);
    //
    // Location L6a = new Location(70,-40); // 1148.942 km
    // Location L6b = new Location(80,-50);
    //
    // Location L7a = new Location(-30,80); // 1497.148 km
    // Location L7b = new Location(-20,90);
    //
    // Location L8a = new Location(70,70); // 234.662 km
    // Location L8b = new Location(72,72);
    //
    // Location L9a = new Location(-20,120); // 305.532 km
    // Location L9b = new Location(-18,122);
    //
    // // LocationList llL1 = createLocList(L1a,L1b,0.2);
    // // LocationList llL2 = createLocList(L2a,L2b,0.2);
    // // LocationList llL3 = createLocList(L3a,L3b,0.2);
    // // LocationList llL4 = createLocList(L4a,L4b,356); // spans prime
    // meridian
    // LocationList llL5 = createLocList(L5a,L5b,0.05);
    // // LocationList llL6 = createLocList(L6a,L6b,0.05);
    // // LocationList llL7 = createLocList(L7a,L7b,0.05);
    // // LocationList llL8 = createLocList(L8a,L8b,0.001);
    // // LocationList llL9 = createLocList(L9a,L9b,0.001);
    //
    // LocationList LLtoUse = llL5;
    // Location startPt = LLtoUse.get(0);
    // for (int i = 1; i < LLtoUse.size(); i++) {
    // Location endPt = LLtoUse.get(i);
    // double surfDist = horzDistance(startPt, endPt);
    // double fastSurfDist = horzDistanceFast(startPt, endPt);
    // double delta1 = fastSurfDist - surfDist;
    // double horizDist = getHorzDistanceOLD(startPt, endPt);
    // double approxDist = getApproxHorzDistanceOLD(startPt, endPt);
    // double delta2 = approxDist - horizDist;
    // double delta3 = fastSurfDist - approxDist;
    // String s = String.format(
    // "sd: %03.4f sdf: %03.4f d: %03.4f " +
    // "hdO: %03.4f adO: %03.4f d: %03.4f Df: %03.4f",
    // surfDist, fastSurfDist, delta1,
    // horizDist, approxDist, delta2, delta3);
    // System.out.println(s);
    // }
    //
  }

  // utility method to create a locationlist between two points; points
  // are discretized in longitude using 'lonInterval'; latitude intervals
  // are whatever they need to be to get to L2
  //
  // this is used in 'main' when exploring variations between distance
  // calculators
  private static LocationList createLocList(
      Location L1, Location L2, double lonInterval) {
    int numPoints = (int) Math.floor(Math.abs(
      L2.lon() - L1.lon()) / lonInterval);
    double dLat = (L2.lat() - L1.lat()) / numPoints;
    double dLon = (L1.lon() - L2.lon() < 0) ? lonInterval : -lonInterval;
    LocationList.Builder llb = LocationList.builder();
    double lat = L1.lat();
    double lon = L1.lon();
    for (int i = 0; i <= numPoints; i++) {
      // System.out.println(lat + " " + lon);
      llb.add(Location.create(lat, lon));
      lat += dLat;
      lon += dLon;
    }
    return llb.build();
  }

  // utility method to generate random locations within +/- 40deg lat and
  // +/- 40 deg lon
  private static Random rand = new Random();

  private static Location randomLoc() {
    return Location.create(randLatLon(), randLatLon());
  }

  private static double randLatLon() {
    return (rand.nextDouble() * 80) - 40;
  }

  // ==========================================================
  // ARCHIVED METHODS
  // ==========================================================

  /** Earth radius constant */
  private final static int R = 6367;

  /** Radians to degrees conversion constant */
  private final static double RADIANS_CONVERSION = Math.PI / 180;

  /** Degree to Km conversion at equator */
  private final static double D_COEFF = 111.11;

  /**
   * OLD METHOD
   */
  private static double getHorzDistanceOLD(Location loc1, Location loc2) {
    return getHorzDistanceOLD(
      loc1.lat(), loc1.lon(),
      loc2.lat(), loc2.lon());
  }

  /**
   * OLD METHOD
   * 
   * Second way to calculate the distance between two points. Obtained off the
   * internet, but forgot where now. When used in comparision with the
   * latLonDistance function you see they give practically the same values at
   * the equator, and only start to diverge near the poles, but still reasonable
   * close to each other. Good for point of comparision.
   */
  private static double getHorzDistanceOLD(
      double lat1, double lon1, double lat2, double lon2) {

    double xlat = lat1 * RADIANS_CONVERSION;
    double xlon = lon1 * RADIANS_CONVERSION;

    double st0 = Math.cos(xlat);
    double ct0 = Math.sin(xlat);

    double phi0 = xlon;

    xlat = lat2 * RADIANS_CONVERSION;
    xlon = lon2 * RADIANS_CONVERSION;

    double st1 = Math.cos(xlat);
    double ct1 = Math.sin(xlat);

    double sdlon = Math.sin(xlon - phi0);
    double cdlon = Math.cos(xlon - phi0);

    double cdelt = (st0 * st1 * cdlon) + (ct0 * ct1);

    double x = (st0 * ct1) - (st1 * ct0 * cdlon);
    double y = st1 * sdlon;

    double sdelt = Math.pow(((x * x) + (y * y)), .5);
    double delta = Math.atan2(sdelt, cdelt) / RADIANS_CONVERSION;

    delta = delta * D_COEFF;

    return delta;
  }

  /**
   * OLD METHOD
   */
  private static double getApproxHorzDistanceOLD(
      Location loc1, Location loc2) {
    return getApproxHorzDistanceOLD(
      loc1.lat(), loc1.lon(),
      loc2.lat(), loc2.lon());
  }

  /**
   * OLD METHOD
   * 
   * This computes the approximate horizontal distance (in km) using the
   * standard cartesian coordinate transformation. Not implemented correctly is
   * lons straddle 360 or 0 degrees!
   */
  private static double getApproxHorzDistanceOLD(
      double lat1, double lon1, double lat2, double lon2) {
    double d1 = (lat1 - lat2) * 111.111;
    double d2 = (lon1 - lon2) * 111.111 * Math.cos(((lat1 + lat2) / (2)) * Math.PI / 180);
    return Math.sqrt(d1 * d1 + d2 * d2);
  }

  /**
   * OLD METHOD
   * 
   * Helper method that calculates the angle between two locations on the
   * earth.<p>
   *
   * @param loc1 location of first point
   * @param loc2 location of second point
   * @return angle between the two locations
   */
  private static double getAzimuthOLD(Location loc1, Location loc2) {
    return getAzimuthOLD(loc1.lat(), loc1.lon(),
      loc2.lat(), loc2.lon());
  }

  /**
   * OLD METHOD
   * 
   * Helper method that calculates the angle between two locations (value
   * returned is between -180 and 180 degrees) on the earth.<p>
   *
   * @param lat1 latitude of first point
   * @param lon1 longitude of first point
   * @param lat2 latitude of second point
   * @param lon2 longitude of second point
   * @return angle between the two lat/lon locations
   */
  private static double getAzimuthOLD(
      double lat1, double lon1, double lat2, double lon2) {

    double xlat = lat1 * RADIANS_CONVERSION;
    double xlon = lon1 * RADIANS_CONVERSION;

    double st0 = Math.cos(xlat);
    double ct0 = Math.sin(xlat);

    double phi0 = xlon;

    xlat = lat2 * RADIANS_CONVERSION;
    xlon = lon2 * RADIANS_CONVERSION;

    double st1 = Math.cos(xlat);
    double ct1 = Math.sin(xlat);

    double sdlon = Math.sin(xlon - phi0);
    double cdlon = Math.cos(xlon - phi0);

    double x = (st0 * ct1) - (st1 * ct0 * cdlon);
    double y = st1 * sdlon;

    double az = Math.atan2(y, x) / RADIANS_CONVERSION;

    return az;
  }

  /**
   * OLD METHOD
   * 
   * Helper method that calculates the angle between two locations on the
   * earth.<p>
   *
   * Note: SWR: I'm not quite sure of the difference between azimuth and back
   * azimuth. Ned, you will have to fill in the details.
   *
   * @param lat1 latitude of first point
   * @param lon1 longitude of first point
   * @param lat2 latitude of second point
   * @param lon2 longitude of second point
   * @return angle between the two lat/lon locations
   */
  private static double getBackAzimuthOLD(
      double lat1, double lon1, double lat2, double lon2) {

    double xlat = lat1 * RADIANS_CONVERSION;
    double xlon = lon1 * RADIANS_CONVERSION;

    double st0 = Math.cos(xlat);
    double ct0 = Math.sin(xlat);

    double phi0 = xlon;

    xlat = lat2 * RADIANS_CONVERSION;
    xlon = lon2 * RADIANS_CONVERSION;

    double st1 = Math.cos(xlat);
    double ct1 = Math.sin(xlat);

    double sdlon = Math.sin(xlon - phi0);
    double cdlon = Math.cos(xlon - phi0);

    double x = (st1 * ct0) - (st0 * ct1 * cdlon);
    double y = -sdlon * st0;

    double baz = Math.atan2(y, x) / RADIANS_CONVERSION;

    return baz;
  }

  /**
   * OLD METHOD
   * 
   * This computes the total distance in km.
   */
  private static double getTotalDistanceOLD(Location loc1, Location loc2) {
    double hDist = getHorzDistanceOLD(loc1, loc2);
    double vDist = vertDistance(loc1, loc2);
    return Math.sqrt(hDist * hDist + vDist * vDist);
  }

  /**
   * OLD METHOD
   * 
   * Given a Location and a LocationVector object, this function calculates a
   * second Location the LocationVector points to (only the azimuth is used;
   * backAzimuth is ignored). The fields calculated for the second Location are:
   *
   * <uL> <li>Lat <li>Lon <li>Depth </ul>
   *
   * @param location1 First geographic location
   * @param direction LocationVector object pointing to second Location
   * @return location2 The second location
   * @exception UnsupportedOperationException Thrown if the Location or
   *            LocationVector contain bad data such as invalid latitudes
   * @see Location to see the field definitions
   */
  private static Location getLocationOLD(
      Location location, LocationVector direction)
      throws UnsupportedOperationException {

    double lat1 = location.lat();
    double lon1 = location.lon();
    double depth = location.depth();

    double azimuth = direction.azimuth();
    double horzDistance = direction.horizontal();
    double vertDistance = direction.vertical();

    double newLat = getLatitudeOLD(horzDistance, azimuth, lat1, lon1);
    double newLon = getLongitudeOLD(horzDistance, azimuth, lat1, lon1);
    // double newDepth = depth + -1*vertDistance;
    double newDepth = depth + vertDistance;

    Location newLoc = Location.create(newLat, newLon, newDepth);
    return newLoc;
  }

  /**
   * OLD METHOD
   * 
   * By passing in two Locations this calculator will determine the Distance
   * object between them. The four fields calculated are:
   *
   * <uL> <li>horzDistance <li>azimuth <li>backAzimuth <li>vertDistance </ul>
   *
   * @param location1 First geographic location
   * @param location2 Second geographic location
   * @return The direction, decomposition of the vector between two locations
   * @exception UnsupportedOperationException Thrown if the Locations contain
   *            bad data such as invalid latitudes
   * @see Distance to see the field definitions
   */
  private static LocationVector getDirectionOLD(
      Location location1, Location location2)
      throws UnsupportedOperationException {

    double lat1 = location1.lat();
    double lon1 = location1.lon();
    double lat2 = location2.lat();
    double lon2 = location2.lon();

    double horzDistance = getHorzDistanceOLD(location1, location2);
    double azimuth = getAzimuthOLD(location1, location2);
    double vertDistance = location2.depth() - location1.depth();

    return LocationVector.create(azimuth, horzDistance, vertDistance);
  }

  /**
   * OLD METHOD
   * 
   * Internal helper method that calculates the latitude of a second location
   * given the input location and direction components
   *
   * @param delta Horizontal distance
   * @param azimuth angle towards new point
   * @param lat latitude of original point
   * @param lon longitude of original point
   * @return latitude of new point
   */
  private static double getLatitudeOLD(
      double delta, double azimuth, double lat, double lon) {

    delta = (delta / D_COEFF) * RADIANS_CONVERSION;

    double sdelt = Math.sin(delta);
    double cdelt = Math.cos(delta);

    double xlat = lat * RADIANS_CONVERSION;
    // double xlon = lon * RADIANS_CONVERSION;

    double az2 = azimuth * RADIANS_CONVERSION;

    double st0 = Math.cos(xlat);
    double ct0 = Math.sin(xlat);

    // double phi0 = xlon;

    double cz0 = Math.cos(az2);
    double ct1 = (st0 * sdelt * cz0) + (ct0 * cdelt);

    double x = (st0 * cdelt) - (ct0 * sdelt * cz0);
    double y = sdelt * Math.sin(az2);

    double st1 = Math.pow(((x * x) + (y * y)), .5);
    // double dlon = Math.atan2( y, x );

    double newLat = Math.atan2(ct1, st1) / RADIANS_CONVERSION;
    return newLat;
  }

  /**
   * OLD METHOD
   * 
   * Internal helper method that calculates the longitude of a second location
   * given the input location and direction components
   *
   * @param delta Horizontal distance
   * @param azimuth angle towards new point
   * @param lat latitude of original point
   * @param lon longitude of original point
   * @return longitude of new point
   */
  private static double getLongitudeOLD(
      double delta, double azimuth, double lat, double lon) {

    delta = (delta / D_COEFF) * RADIANS_CONVERSION;

    double sdelt = Math.sin(delta);
    double cdelt = Math.cos(delta);

    double xlat = lat * RADIANS_CONVERSION;
    double xlon = lon * RADIANS_CONVERSION;

    double az2 = azimuth * RADIANS_CONVERSION;

    double st0 = Math.cos(xlat);
    double ct0 = Math.sin(xlat);

    double phi0 = xlon;

    double cz0 = Math.cos(az2);
    // double ct1 = ( st0 * sdelt * cz0 ) + ( ct0 * cdelt );

    double x = (st0 * cdelt) - (ct0 * sdelt * cz0);
    double y = sdelt * Math.sin(az2);

    // double st1 = Math.pow( ( ( x * x ) + ( y * y ) ), .5 );
    double dlon = Math.atan2(y, x);

    double newLon = (phi0 + dlon) / RADIANS_CONVERSION;
    return newLon;
  }

  /**
   * 
   * OLD METHOD - although fast, curent implementation is faster and not nearly
   * as complicated
   * 
   * Computes the shortest distance between a point and a line. Both the line
   * and point are assumed to be at the earth's surface; the depth component of
   * each <code>Location</code> is ignored. This is a fast, geometric, cartesion
   * (flat-earth approximation) solution in which longitude is scaled by the
   * cosine of latitude; it is only appropriate for use over short distances
   * (e.g. &lt;200 km).<br/> <br/> <b>Note:</b> This method does <i>NOT</i>
   * support values spanning &#177;180&#176; and results for such input values
   * are not guaranteed.
   * 
   * @param p1 the first <code>Location</code> point on the line
   * @param p2 the second <code>Location</code> point on the line
   * @param p3 the <code>Location</code> point for which distance will be
   *        calculated
   * @return the shortest distance in km between the supplied point and line
   * @see #distanceToLine(Location, Location, Location)
   */
  private static double distanceToLineFastOLD(
      Location p1,
      Location p2,
      Location p3) {

    double lat1 = p1.latRad();
    double lat2 = p2.latRad();
    double lat3 = p3.latRad();
    double lon1 = p1.lonRad();
    double lon2 = p2.lonRad();
    double lon3 = p3.lonRad();

    // use average latitude to scale longitude
    double lonScale = Math.cos(0.5 * lat3 + 0.25 * lat1 + 0.25 * lat2);

    // line-point corrdinates w/ loc transformed to the origin
    double x1 = (lon1 - lon3) * lonScale;
    double x2 = (lon2 - lon3) * lonScale;
    double y1 = lat1 - lat3;
    double y2 = lat2 - lat3;

    double dist;

    // check for values very close to zero
    if (Math.abs(x1 - x2) > 1e-8) {
      double m = (y2 - y1) / (x2 - x1); // slope
      double b = y2 - m * x2; // intercept
      double xT = -m * b / (1 + m * m); // x target
      double yT = m * xT + b; // y target

      // make sure the target point is in between the two endpoints
      boolean betweenPts = false;
      if (x2 > x1) {
        if (xT <= x2 && xT >= x1) betweenPts = true;
      } else {
        if (xT <= x1 && xT >= x2) betweenPts = true;
      }

      if (betweenPts)
        dist = Math.sqrt(xT * xT + yT * yT);
      // return Math.sqrt(xT*xT + yT*yT) * EARTH_RADIUS_MEAN;
      else {
        double d1 = Math.sqrt(x1 * x1 + y1 * y1);
        double d2 = Math.sqrt(x2 * x2 + y2 * y2);
        dist = Math.min(d1, d2);
      }
    } else {
      // the x1 = x2 case
      if (y2 > y1) {
        if (y2 <= 0.0) {
          dist = Math.sqrt(x2 * x2 + y2 * y2);
        } else if (y1 >= 0) {
          dist = Math.sqrt(x1 * x1 + y1 * y1);
        } else {
          dist = Math.abs(x1);
        }
      } else {
        // (y1 > y2)
        if (y1 <= 0.0) {
          dist = Math.sqrt(x1 * x1 + y1 * y1);
        } else if (y2 >= 0) {
          dist = Math.sqrt(x2 * x2 + y2 * y2);
        } else {
          dist = Math.abs(x1);
        }
      }
    }
    return dist * EARTH_RADIUS_MEAN;
  }

}
