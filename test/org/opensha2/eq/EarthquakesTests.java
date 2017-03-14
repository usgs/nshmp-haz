package org.opensha2.eq;

import org.opensha2.geo.Location;

import org.junit.Test;

@SuppressWarnings("javadoc")
public final class EarthquakesTests {

  @Test(expected = IllegalArgumentException.class)
  public final void create_IAE5() {
    Location.create(0, 0, Earthquakes.MAX_DEPTH + 0.1);
  }

  @Test(expected = IllegalArgumentException.class)
  public final void create_IAE6() {
    Location.create(0, 0, Earthquakes.MIN_DEPTH - 0.1);
  }

}
