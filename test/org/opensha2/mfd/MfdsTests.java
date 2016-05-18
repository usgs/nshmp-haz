package org.opensha2.mfd;

import static org.junit.Assert.assertArrayEquals;

import java.awt.geom.Point2D;

import org.junit.Test;

import com.google.common.primitives.Doubles;

public class MfdsTests {

  private static final double MFD_TOL = 1e-10;

  @Test
  public final void testTaperedGR() {
    IncrementalMfd tGR = Mfds.newTaperedGutenbergRichterMFD(5.05, 0.1, 30, 4.0, 0.8, 7.5, 1.0);
    assertArrayEquals(TAPERED_GR_MAGS, Doubles.toArray(tGR.xValues()), MFD_TOL);
    assertArrayEquals(TAPERED_GR_RATES, Doubles.toArray(tGR.yValues()), MFD_TOL);
  }

  // private static

  public static void main(String[] args) {
    IncrementalMfd tGR = Mfds.newTaperedGutenbergRichterMFD(5.05, 0.1, 30, 4.0, 0.8, 7.5, 1.0);
    System.out.println(tGR.xValues());
    System.out.println(tGR.yValues());
    for (Point2D p : tGR) {
      System.out.println(p.getX() + " " + p.getY());
    }
  }

  private static final double[] TAPERED_GR_MAGS = { 5.05, 5.15, 5.25, 5.35, 5.45, 5.55, 5.65,
      5.75, 5.85, 5.95, 6.05, 6.15, 6.25, 6.35, 6.45, 6.55, 6.65, 6.75, 6.85, 6.95, 7.05, 7.15,
      7.25, 7.35, 7.45, 7.55, 7.65, 7.75, 7.85, 7.95 };

  private static final double[] TAPERED_GR_RATES = {
      3.6487347712647455E-4,
      3.0351155247723856E-4,
      2.524769424833213E-4,
      2.1003291504182055E-4,
      1.747350371537821E-4,
      1.4538201763727163E-4,
      1.2097482132130435E-4,
      1.0068266229609008E-4,
      8.38147171800033E-5,
      6.979659287661515E-5,
      5.815074326255645E-5,
      4.848016071724235E-5,
      4.0454775273297684E-5,
      3.380007928256618E-5,
      2.828756084416522E-5,
      2.37265764202333E-5,
      1.995732452895587E-5,
      1.6844604598702625E-5,
      1.4272075425536466E-5,
      1.2136808492386587E-5,
      1.0344152445466779E-5,
      8.803445832444012E-6,
      7.426137715686029E-6,
      6.129417141745154E-6,
      4.849632254896957E-6,
      3.5675342487878273E-6,
      2.3362943546550987E-6,
      1.2825840184413836E-6,
      5.438860517858615E-7,
      1.5952698054966954E-7 };
}
