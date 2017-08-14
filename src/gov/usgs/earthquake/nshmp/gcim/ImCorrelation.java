package gov.usgs.earthquake.nshmp.gcim;

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.Math.PI;
import static java.lang.Math.cos;
import static java.lang.Math.exp;
import static java.lang.Math.log;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.sqrt;

import gov.usgs.earthquake.nshmp.gmm.Imt;

/**
 * Add comments here
 *
 * @author Peter Powers
 */
public enum ImCorrelation {

  BAKER_07("Baker (2007)") {
    @Override
    double get(Imt im1, Imt im2) {
      return 0;
      // TODO do nothing

    }
  },

  BAKER_JARAYAM_08("") {
    @Override
    double get(Imt im1, Imt im2) {
      return computeBakerJaraym08(im1, im2);
    }
  },

  BRADLEY_11("") {
    @Override
    double get(Imt im1, Imt im2) {
      return 0;
      // TODO do nothing

    }
  },

  GODA_ATKINSON_09("") {
    @Override
    double get(Imt im1, Imt im2) {
      return 0;
      // TODO do nothing

    }
  };

  private String label;

  private ImCorrelation(String label) {
    this.label = label;
  }

  /**
   * Compute the correlation between two intensity measures.
   */
  abstract double get(Imt im1, Imt im2);

  private static double computeBakerJaraym08(Imt imi, Imt imj) {
    checkArgument(imi.isSA() && imj.isSA(), "Supplied IMTs are not SA intensity measures");

    double ti = imi.period();
    double tj = imj.period();

    double t_min = min(ti, tj);
    double t_max = max(ti, tj);
    double c2 = Double.NaN;
    double c3;
    double c4;

    double c1 = (1.0 - cos(PI / 2.0 - log(t_max / max(t_min, 0.109)) * 0.366));

    if (t_max < 0.2) {
      c2 = 1.0 - 0.105 *
          (1.0 - 1.0 / (1.0 + exp(100.0 * t_max - 5.))) *
          (t_max - t_min) / (t_max - 0.0099);
    }

    // if (t_max < 0.109) {
    // c3 = c2;
    // } else {
    // c3 = c1;
    // }
    c3 = (t_max < 0.109) ? c2 : c1;

    c4 = c1 + 0.5 * (sqrt(c3) - c3) * (1. + cos(PI * t_min / 0.109));

    if (t_max <= 0.109) {
      return c2;
    } else if (t_min > 0.109) {
      return c1;
    } else if (t_max < 0.2) {
      return min(c2, c4);
    } else {
      return c4;
    }

    // return (t_max <= 0.109) ? c2 : (t_min > 0.109) ? c1 : (t_max < 0.2) ?
    // min(c2, c4) : c4;

  }
}
