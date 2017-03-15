package org.opensha2.eq.fault;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import org.opensha2.geo.Location;
import org.opensha2.geo.LocationList;

import org.junit.Test;

@SuppressWarnings("javadoc")
public final class FaultsTests {

  @Test
  public final void testCheckFaultDipRakeStrike() {
    assertEquals(45.0, Faults.checkDip(45.0), 0.0);
    assertEquals(45.0, Faults.checkRake(45.0), 0.0);
    assertEquals(45.0, Faults.checkStrike(45.0), 0.0);
  }

  @Test(expected = IllegalArgumentException.class)
  public final void testCheckDipLo_IAE() {
    Faults.checkDip(-0.1);
  }

  @Test(expected = IllegalArgumentException.class)
  public final void testCheckDipHi_IAE() {
    Faults.checkDip(90.1);
  }
  
  @Test(expected = IllegalArgumentException.class)
  public final void testCheckRakeLo_IAE() {
    Faults.checkRake(-180.1);
  }

  @Test(expected = IllegalArgumentException.class)
  public final void testCheckRakeHi_IAE() {
    Faults.checkRake(180.1);
  }
  
  @Test(expected = IllegalArgumentException.class)
  public final void testCheckStrikeLo_IAE() {
    Faults.checkStrike(-0.1);
  }

  @Test(expected = IllegalArgumentException.class)
  public final void testCheckStrikeHi_IAE() {
    Faults.checkStrike(360.0);
  }
  
  @Test
  public final void testCheckTrace() {
    LocationList expect = LocationList.create(
        Location.create(0.0, 0.0),
        Location.create(1.0, 1.0));
    assertSame(expect, Faults.checkTrace(expect));
  }

  @Test(expected = IllegalArgumentException.class)
  public final void testCheckTrace_IAE() {
    Faults.checkTrace(LocationList.create(Location.create(0.0, 0.0)));
  }


}
