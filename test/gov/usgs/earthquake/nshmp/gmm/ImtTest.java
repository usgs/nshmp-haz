package gov.usgs.earthquake.nshmp.gmm;

import static gov.usgs.earthquake.nshmp.gmm.Imt.*;
import static org.junit.Assert.*;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.Test;

@SuppressWarnings("javadoc")
public class ImtTest {

  /*
   * IMT fields, particularly PGA and spectral accelerations are hit by a
   * variety of tests. The tests here cover edge case exceptions, less
   * frequently used IMTs, and other helper methods.
   */

  @Test
  public void testToString() {
    assertEquals(PGA.toString(), "Peak Ground Acceleration");
    assertEquals(PGV.toString(), "Peak Ground Velocity");
    assertEquals(PGD.toString(), "Peak Ground Displacement");
    assertEquals(AI.toString(), "Arias Intensity");
    assertEquals(ASI.toString(), "Acceleration Spectrum Intensity");
    assertEquals(DSI.toString(), "Displacement Spectrum Intensity");
    assertEquals(SI.toString(), "Spectrum intensity");
    assertEquals(CAV.toString(), "Cumulative Absolute Velocity");
    assertEquals(DS575.toString(), "Significant Duration 5-75%");
    assertEquals(DS595.toString(), "Significant Duration 5-95%");
    assertEquals(SA1P0.toString(), "1.00 Second Spectral Acceleration");
  }

  @Test
  public void testUnits() {
    assertEquals(PGA.units(), "g");
    assertEquals(PGV.units(), "cm/s");
    assertEquals(PGD.units(), "cm");
    assertEquals(AI.units(), "m/s");
    assertEquals(ASI.units(), "g⋅s");
    assertEquals(DSI.units(), "cm⋅s");
    assertEquals(SI.units(), "cm⋅s/s");
    assertEquals(CAV.units(), "g⋅s");
    assertEquals(DS575.units(), "s");
    assertEquals(DS595.units(), "s");
    assertEquals(SA1P0.units(), "g");
  }

  @Test(expected = IllegalArgumentException.class)
  public void testFromPeriodException() {
    Imt.fromPeriod(0.1234);
  }

  @Test
  public void testPeriods() {
    Set<Imt> imts = EnumSet.range(SA0P01, SA10P0);
    List<Double> expected = imts.stream()
        .map(Imt::period)
        .collect(Collectors.toList());
    assertEquals(expected, Imt.periods(imts));
  }

  @Test(expected = UnsupportedOperationException.class)
  public void testFrequencyException() {
    PGV.frequency();
  }

  @Test
  public void testSaImts() {
    // underlying method is complement of non-SA Imts
    // which are listed first in the enum
    assertEquals(Imt.saImts(), EnumSet.range(SA0P01, SA10P0));
  }

}
