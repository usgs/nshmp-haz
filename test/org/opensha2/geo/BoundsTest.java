package org.opensha2.geo;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;

import org.junit.Test;

@SuppressWarnings("javadoc")
public class BoundsTest {

  /*
   * Bounds creation is not public so the burden of checking that min and max
   * are actually min and max lies with classes that create bounds objects.
   */

  private static final double MIN_LAT = -10.0;
  private static final double MIN_LON = -10.0;
  private static final double MAX_LAT = 10.0;
  private static final double MAX_LON = 10.0;

  private static final Location MIN = Location.create(MIN_LAT, MIN_LON);
  private static final Location MAX = Location.create(MAX_LAT, MAX_LON);
  private static final Location BAD = Location.create(0.0, 0.0);

  @Test
  public final void minMax() {
    Bounds b = new Bounds(MIN, MAX);
    assertSame(MIN, b.min());
    assertSame(MAX, b.max());
    b = new Bounds(MIN_LAT, MIN_LON, MAX_LAT, MAX_LON);
    assertEquals(MIN, b.min());
    assertEquals(MAX, b.max());
  }

  @Test
  public void toList() {
    Bounds b = new Bounds(MIN, MAX);
    LocationList bList = b.toList();
    LocationList oList = LocationList.create(
      MIN,
      Location.create(MIN_LAT, MAX_LON),
      MAX,
      Location.create(MAX_LAT, MIN_LON),
      MIN);
    assertEquals(oList, bList);
  }

  @Test
  public void toArray() {
    Bounds b = new Bounds(MIN, MAX);
    double[] coords = new double[] { MIN_LON, MIN_LAT, MAX_LON, MAX_LAT };
    assertArrayEquals(coords, b.toArray(), 0.0);
  }

  @Test
  public void equalsTest() {
    Bounds b1 = new Bounds(MIN, MAX);
    assertEquals(b1, b1);
    assertNotEquals(b1, null);
    assertNotEquals(b1, "test");

    Bounds b2 = new Bounds(MIN, MAX);
    assertEquals(b1, b2);
    b2 = new Bounds(MIN, BAD);
    assertNotEquals(b1, b2);
    b2 = new Bounds(BAD, MAX);
    assertNotEquals(b1, b2);

    b2 = new Bounds(MIN_LAT, MIN_LON, MAX_LAT, MAX_LON);
    assertEquals(b1, b2);
  }

  @Test
  public void hashCodeTest() {
    Bounds b1 = new Bounds(MIN, MAX);
    Bounds b2 = new Bounds(MIN_LAT, MIN_LON, MAX_LAT, MAX_LON);
    assertEquals(b1.hashCode(), b2.hashCode());
    b2 = new Bounds(0, 0, 2, 2);
    assertNotEquals(b1.hashCode(), b2.hashCode());
  }

  @Test
  public void toStringTest() {
    Bounds b = new Bounds(MIN, MAX);
    assertEquals("[-10.0, -10.0, 10.0, 10.0]", b.toString());
  }

}
