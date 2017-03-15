package org.opensha2.geo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;

import static org.opensha2.internal.TextUtils.NEWLINE;

import org.opensha2.geo.LocationList.RegularLocationList;
import org.opensha2.util.Maths;

import org.hamcrest.CoreMatchers;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import java.util.List;

@SuppressWarnings("javadoc")
public class LocationListTest {

  private static LocationList locs1, locs2;
  private static Location p1, p2, p3, p4, p5, p6, p7;
  private static Location pp1, pp2;

  private static Location g00, g01, g02, g03, g10, g11, g12, g13;
  private static LocationGrid grid;

  @BeforeClass
  public static void setUp() {
    p1 = Location.create(-5, 0);
    p2 = Location.create(-3, -2);
    p3 = Location.create(-2, -2);
    p4 = Location.create(0, 0);
    p5 = Location.create(2, 2);
    p6 = Location.create(3, 2);
    p7 = Location.create(5, 0);

    pp1 = Location.create(0, 0);
    pp2 = Location.create(1, 1);

    locs1 = LocationList.create(p1, p2, p3, p4, p5, p6, p7);
    locs2 = LocationList.create(p1, p3, p2, p4, p6, p5, p7);

    g00 = Location.create(0, 0);
    g01 = Location.create(1, 0);
    g02 = Location.create(2, 0);
    g03 = Location.create(3, 0);
    g10 = Location.create(0, 1);
    g11 = Location.create(1, 1);
    g12 = Location.create(2, 1);
    g13 = Location.create(3, 1);
    grid = LocationGrid.builder(2, 4)
        .fillRow(0, LocationList.builder().add(g00, g01, g02, g03).build())
        .fillRow(1, LocationList.builder().add(g10, g11, g12, g13).build())
        .build();
  }

  /* Factory */

  @Test
  public final void create() {
    LocationList locs = LocationList.create(p1, p2, p3, p4, p5, p6, p7);
    assertEquals(locs, locs1);
    List<Location> il = ImmutableList.of(p1, p2, p3, p4, p5, p6, p7);
    locs = LocationList.create(il);
    assertEquals(locs, locs1);
    assertSame(il, ((RegularLocationList) locs).locs);
    locs = LocationList.create(locs1);
    assertSame(locs, locs1);
  }

  @Test(expected = NullPointerException.class)
  public final void create_NPE1() {
    Location[] locs = null;
    LocationList.create(locs);
  }

  @Test(expected = IllegalArgumentException.class)
  public final void create_IAE1() {
    Location[] locs = new Location[] {};
    LocationList.create(locs);
  }

  @Test(expected = NullPointerException.class)
  public final void create_NPE2() {
    List<Location> locs = null;
    LocationList.create(locs);
  }

  @Test(expected = IllegalArgumentException.class)
  public final void create_IAE2() {
    List<Location> locs = ImmutableList.of();
    LocationList.create(locs);
  }

  @Test
  public final void resample() {
    LocationList locs = LocationList.create(pp1, pp2);
    LocationList resampled = locs.resample(12);
    assertEquals(resampled.size(), 15);
    assertSame(resampled.first(), pp1);
    assertSame(resampled.last(), pp2);
    Location mid = resampled.get(7);
    assertEquals(mid.lon(), 0.49996509384838933, 0.0);
    assertEquals(mid.lat(), 0.5000222122727477, 0.0);

    // singleton
    locs = LocationList.create(pp1);
    resampled = locs.resample(1.0);
    assertSame(locs, resampled);
  }

  @Test(expected = IllegalArgumentException.class)
  public final void resample_IAE1() {
    LocationList locs = LocationList.create(pp1, pp2);
    locs.resample(Double.POSITIVE_INFINITY);
  }

  @Test(expected = IllegalArgumentException.class)
  public final void resample_IAE2() {
    LocationList locs = LocationList.create(pp1, pp2);
    locs.resample(-1.0);
  }

  public static void main(String[] args) {
    // Location pp1 = Location.create(0, 0);
    // Location pp2 = Location.create(1, 1);
    //
    // LocationList ll1 = null;
    // LocationList ll2 = LocationList.create(pp1, pp2);
    //
    // assertEquals(ll1, ll2);
    //
    // LocationList locs = LocationList.create(pp1, pp2);
    // LocationVector v = LocationVector.create(135 * GeoTools.TO_RAD, 5.0,
    // 5.0);
    // LocationList translated = locs.translate(v);
    // System.out.println(translated);
    //
    // System.out.println("---");
    // System.out.println(translated.get(0).lat());
    // System.out.println(translated.get(0).lon());
    // System.out.println(translated.get(0).depth());
    // System.out.println("---");
    // System.out.println(translated.get(1).lat());
    // System.out.println(translated.get(1).lon());
    // System.out.println(translated.get(1).depth());
    // System.out.println("---");

    // LocationList locs = LocationList.create(pp1, pp2);
    // LocationList resampled = locs.resample(12);
    // System.out.println(resampled);

    // List<Location> pp1 = ImmutableList.of(p1, p2, p3);
    // Iterable<Location> pp2 = LocationList.create(p1, p2, p3);
    // Iterable<Location> pp3 = LocationList.create(p1, p2, p3);
    // System.out.println(pp3.equals(pp2));

    // String locStr = new StringBuilder(NEWLINE)
    // .append("0.00000,0.00000,0.00000").append(NEWLINE)
    // .append("1.00000,1.00000,0.00000").append(NEWLINE)
    // .toString();
    // LocationList locs = LocationList.create(pp1, pp2);
    //
    // System.out.println("----");
    // System.out.println(locStr);
    // System.out.println("----");
    // System.out.println(locs);
    // System.out.println("----");

    List<Integer> pp1 = Lists.newArrayList(2, 4, 7, 31);
    List<Integer> pp2 = ImmutableList.of(2, 4, 7, 31);
    System.out.println(pp1.hashCode());
    System.out.println(pp2.hashCode());

  }

  @Test
  public final void reverse() {

    // this tests the default RegularLocationList
    LocationList reversed = locs1.reverse();
    assertSame(locs1.get(0), reversed.get(6));
    assertSame(locs1.get(1), reversed.get(5));
    assertSame(locs1.get(2), reversed.get(4));
    assertSame(locs1.get(4), reversed.get(2));
    assertSame(locs1.get(5), reversed.get(1));
    assertSame(locs1.get(6), reversed.get(0));

    // use LocationGrid to test fall through to
    // other LocList implementations
    LocationList reversedRow = grid.row(1).reverse();
    assertSame(g10, reversedRow.get(3));
    assertSame(g11, reversedRow.get(2));
    assertSame(g12, reversedRow.get(1));
    assertSame(g13, reversedRow.get(0));
  }

  @Test
  public final void translate() {
    LocationList locs = LocationList.create(pp1, pp2);
    LocationVector v = LocationVector.create(135 * Maths.TO_RAD, 5.0, 5.0);
    LocationList transLoc = locs.translate(v);
    Location pp1trans = Location.create(-0.03179578273558637, 0.031795787631496104, 5.0);
    Location pp2trans = Location.create(0.9682040632704144, 1.031800322985746, 5.0);
    assertEquals(pp1trans, transLoc.get(0));
    assertEquals(pp2trans, transLoc.get(1));
  }

  @Test
  public final void fromString() {
    String locStr = "-117.0,34.0,0.1 -117.0,34.1,0.2 -117.1,34.0,0.3";
    LocationList locsFromString = LocationList.fromString(locStr);
    LocationList locsActual = LocationList.create(
        Location.create(34.0, -117.0, 0.1),
        Location.create(34.1, -117.0, 0.2),
        Location.create(34.0, -117.1, 0.3));
    assertEquals(locsFromString, locsActual);
  }

  /* Object */

  @Test
  public final void hashCodeTest() {
    // copy create should return the same list
    LocationList copy = LocationList.create(locs1);
    assertEquals(copy.hashCode(), locs1.hashCode());
    // using builder should copy locations
    copy = LocationList.builder().addAll(locs1).build();
    assertEquals(copy.hashCode(), locs1.hashCode());
    assertNotEquals(locs1.hashCode(), locs2.hashCode());
    // because hash code is based on backing list
    // implementation, check that too
    List<Location> locs = ImmutableList.copyOf(locs1);
    assertEquals(locs.hashCode(), locs1.hashCode());
  }

  @Test
  public final void equalsTest() {
    assertEquals(locs1, locs1);
    LocationList equal = null;
    assertNotEquals(locs1, equal);
    assertNotEquals(locs1, "test");
    equal = LocationList.create(p1, p2, p3, p4, p5, p6, p7);
    assertEquals(locs1, equal);
    assertNotEquals(locs1, locs2);

    // size check
    equal = LocationList.create(p1, p2, p3, p4, p5);
    assertNotEquals(locs1, equal);
  }

  @Test
  public final void toStringTest() {
    String locStr = new StringBuilder(NEWLINE)
        .append("0.00000,0.00000,0.00000").append(NEWLINE)
        .append("1.00000,1.00000,0.00000").append(NEWLINE)
        .toString();
    LocationList locs = LocationList.create(pp1, pp2);
    assertEquals(locStr, locs.toString());
  }

  /* Methods */

  @Test
  public final void size() {
    assertEquals(locs1.get(0), locs1.first());
  }

  @Test
  public final void get() {
    assertSame(locs1.get(0), p1);
    assertSame(locs1.get(2), p3);
    assertSame(locs1.get(4), p5);
    assertSame(locs1.get(6), p7);
  }

  @Test
  public final void first() {
    assertSame(locs1.get(0), locs1.first());
  }

  @Test
  public final void last() {
    assertSame(locs1.get(locs1.size() - 1), locs1.last());
  }

  @Test
  public final void length() {
    assertEquals(1479.6049574653778, locs1.length(), 0.0);
    assertEquals(157.25055720494782, LocationList.create(pp1, pp2).length(), 0.0);
    assertEquals(0.0, LocationList.create(p1).length(), 0.0);
  }

  @Test
  public final void depth() {
    LocationList locs = LocationList.create(
        Location.create(0, 0, 0),
        Location.create(1, 1, 0),
        Location.create(2, 2, 1));
    assertEquals(0.3333333333333333, locs.depth(), 0.0);
  }

  /* Builder */

  @Test
  public final void builder() {
    LocationList.Builder b = LocationList.builder();
    assertNotNull(b.builder);
    assertThat(b.builder, CoreMatchers.instanceOf(ImmutableList.Builder.class));

    LocationList locs = b.add(p1).build();
    assertEquals(1, locs.size());
    assertSame(locs.get(0), p1);

    double lat = 34.001;
    double lon = -117.044;
    locs = b.add(lat, lon).build();
    assertEquals(2, locs.size());
    assertEquals(locs.get(1), Location.create(lat, lon));

    double depth = 3.2;
    locs = b.add(lat, lon, depth).build();
    assertEquals(3, locs.size());
    assertEquals(locs.get(2), Location.create(lat, lon, depth));

    locs = b.add(p1, p2, p3).build();
    assertEquals(6, locs.size());
    assertSame(locs.get(5), p3);

    List<Location> locList = Lists.newArrayList(p4, p5, p6);
    locs = b.addAll(locList).build();
    assertEquals(9, locs.size());
    assertSame(locs.get(8), p6);
  }

  @Test(expected = NullPointerException.class)
  public final void builderAdd_NPE1() {
    Location loc = null;
    LocationList.builder().add(loc);
  }

  @Test(expected = NullPointerException.class)
  public final void builderAdd_NPE2() {
    Location loc = null;
    LocationList.builder().add(Location.create(0, 0), loc);
  }

  @Test(expected = NullPointerException.class)
  public final void builderAdd_NPE3() {
    List<Location> locs = Lists.newArrayList(
        Location.create(0, 0), null);
    LocationList.builder().addAll(locs);
  }

  @Test(expected = IllegalStateException.class)
  public final void builderBuild_ISE() {
    LocationList.builder().build();
  }

}
