package org.opensha2.eq;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

@SuppressWarnings("javadoc")
public final class EarthquakesTests {

  @Test
  public final void testCheckDepths() {
    assertEquals(5.0, Earthquakes.checkDepth(5.0), 0.0);
    assertEquals(5.0, Earthquakes.checkCrustalDepth(5.0), 0.0);
    assertEquals(15.0, Earthquakes.checkCrustalWidth(15.0), 0.0);
    assertEquals(10.0, Earthquakes.checkInterfaceDepth(10.0), 0.0);
    assertEquals(40.0, Earthquakes.checkInterfaceWidth(40.0), 0.0);
    assertEquals(30.0, Earthquakes.checkSlabDepth(30.0), 0.0);
  }

  @Test(expected = IllegalArgumentException.class)
  public final void testCheckDepthLo_IAE() {
    Earthquakes.checkDepth(-5.1);
  }

  @Test(expected = IllegalArgumentException.class)
  public final void testCheckDepthHi_IAE() {
    Earthquakes.checkDepth(700.1);
  }

  @Test(expected = IllegalArgumentException.class)
  public final void testCheckCrustalDepthLo_IAE() {
    Earthquakes.checkCrustalDepth(-0.1);
  }

  @Test(expected = IllegalArgumentException.class)
  public final void testCheckCrustalDepthHi_IAE() {
    Earthquakes.checkCrustalDepth(40.1);
  }

  @Test(expected = IllegalArgumentException.class)
  public final void testCheckCrustalWidthLo_IAE() {
    Earthquakes.checkCrustalWidth(-0.1);
  }

  @Test(expected = IllegalArgumentException.class)
  public final void testCheckCrustalWipthHi_IAE() {
    Earthquakes.checkCrustalWidth(60.1);
  }

  @Test(expected = IllegalArgumentException.class)
  public final void testCheckInterfaceDepthLo_IAE() {
    Earthquakes.checkInterfaceDepth(-0.1);
  }

  @Test(expected = IllegalArgumentException.class)
  public final void testCheckInterfaceDepthHi_IAE() {
    Earthquakes.checkInterfaceDepth(60.1);
  }

  @Test(expected = IllegalArgumentException.class)
  public final void testCheckInterfaceWidthLo_IAE() {
    Earthquakes.checkInterfaceWidth(-0.1);
  }

  @Test(expected = IllegalArgumentException.class)
  public final void testCheckInterfaceWipthHi_IAE() {
    Earthquakes.checkInterfaceWidth(200.1);
  }

  @Test(expected = IllegalArgumentException.class)
  public final void testCheckSlabDepthLo_IAE() {
    Earthquakes.checkSlabDepth(19.9);
  }

  @Test(expected = IllegalArgumentException.class)
  public final void testCheckSlabDepthHi_IAE() {
    Earthquakes.checkSlabDepth(700.1);
  }

  @Test
  public final void testCheckMagnitude() {
    assertEquals(6.5, Earthquakes.checkMagnitude(6.5), 0.0);
  }

  @Test(expected = IllegalArgumentException.class)
  public final void testCheckMagnitudeLo_IAE() {
    Earthquakes.checkMagnitude(-2.1);
  }

  @Test(expected = IllegalArgumentException.class)
  public final void testCheckMagnitudeHi_IAE() {
    Earthquakes.checkMagnitude(9.8);
  }

  @Test
  public final void testMagToMoment() {
    double moExpect = 6.3095734e18;
    double magInput = 6.5;
    double tolerance = 1e11; // 8 sig figs
    assertEquals(moExpect, Earthquakes.magToMoment(magInput), tolerance);
  }

  @Test
  public final void testMomentToMag() {
    double magExpect = 6.5;
    double moInput = 6.3095734e18;
    double tolerance = 1e-8; // 8 sig figs
    assertEquals(magExpect, Earthquakes.momentToMag(moInput), tolerance);
  }

  @Test
  public final void testMoment() {
    double fLength = 20 * 1000.0; // 20 km
    double fWidth = 10 * 1000.0; // 10 km
    double slip = 0.02; // 20 mm
    double moExpect = 1.2e17;
    assertEquals(moExpect, Earthquakes.moment(fLength * fWidth, slip), 0.0);
  }

  @Test
  public final void testSlip() {
    double fLength = 20 * 1000.0; // 20 km
    double fWidth = 10 * 1000.0; // 10 km
    double moment = 1.2e17; 
    double slipExpect = 0.02; // 20 mm
    assertEquals(slipExpect, Earthquakes.slip(fLength * fWidth, moment), 0.0);
  }

}
