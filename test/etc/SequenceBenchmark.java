package etc;

import com.google.common.base.Stopwatch;

import gov.usgs.earthquake.nshmp.data.XySequence;
import gov.usgs.earthquake.nshmp.function.ArbitrarilyDiscretizedFunc;
import gov.usgs.earthquake.nshmp.function.DiscretizedFunc;
import gov.usgs.earthquake.nshmp.function.EvenlyDiscretizedFunc;

/**
 * Quick, possibly naive, benchmark of ArbitrarilyDiscretizedFunction,
 * EvenlyDiscretizedFunction, and XySequence. XySequence is generally faster and
 * more concise.
 *
 * @author Peter Powers
 */
class SequenceBenchmark {

  private static final double[] XS =
      new double[] { 0.0010, 0.0013, 0.0016, 0.0019, 0.0022, 0.0025, 0.0028, 0.0031 };
  private static final double[] YS = new double[] { 1.0, 0.95, 0.85, 0.65, 0.35, 0.15, 0.05, 0.0 };
  private static final int its = 100000000;

  public static void main(String[] args) {
    sequenceTest();
  }

  private static void sequenceTest() {

    System.out.println("Starting ArbitrarilyDiscretizedFunc...");
    ArbitrarilyDiscretizedFunc adf = new ArbitrarilyDiscretizedFunc();
    for (int i = 0; i < XS.length; i++) {
      adf.set(XS[i], YS[i]);
    }
    int numPoints = adf.getNum();
    ArbitrarilyDiscretizedFunc adfReceiver = new ArbitrarilyDiscretizedFunc();
    for (int i = 0; i < XS.length; i++) {
      adfReceiver.set(XS[i], 0);
    }

    Stopwatch sw = Stopwatch.createStarted();
    for (int i = 0; i < its; i++) {
      ArbitrarilyDiscretizedFunc copy = adf.deepClone();

      for (int k = 0; k < numPoints; k++) {
        copy.set(k, copy.getY(k) * copy.getY(k));
      }

      for (int k = 0; k < numPoints; k++) {
        double y = adfReceiver.getY(k);
        adfReceiver.set(k, copy.getY(k) + y);
      }
    }
    System.out.println("Time: " + sw.stop());
    System.out.println(adfReceiver);
    System.out.println();

    System.out.println("Starting EvenlyDiscretizedFunction...");
    EvenlyDiscretizedFunc edf = new EvenlyDiscretizedFunc(0.0010, 8, 0.0003);
    for (int i = 0; i < XS.length; i++) {
      edf.set(i, YS[i]);
    }
    numPoints = edf.getNum();
    EvenlyDiscretizedFunc edfReceiver = new EvenlyDiscretizedFunc(0.0010, 8, 0.0003);
    for (int i = 0; i < XS.length; i++) {
      edfReceiver.set(i, 0);
    }

    sw.reset().start();
    for (int i = 0; i < its; i++) {
      // why on earth does this return DF when
      // ADF.deepCLone returns an ADF?
      DiscretizedFunc copy = edf.deepClone();

      for (int k = 0; k < numPoints; k++) {
        copy.set(k, copy.getY(k) * copy.getY(k));
      }

      for (int k = 0; k < numPoints; k++) {
        double y = edfReceiver.getY(k);
        edfReceiver.set(k, copy.getY(k) + y);
      }
    }
    System.out.println("Time: " + sw.stop());
    System.out.println(edfReceiver);
    System.out.println();

    System.out.println("Starting XySequence...");
    XySequence xy = XySequence.createImmutable(XS, YS);
    XySequence xyReceiver = XySequence.emptyCopyOf(xy);

    sw.reset().start();
    for (int i = 0; i < its; i++) {
      XySequence copy = XySequence.copyOf(xy);
      xyReceiver.add(copy.multiply(xy));
    }
    System.out.println("Time: " + sw.stop());
    System.out.println(xyReceiver);
  }

}
