package org.opensha2.gmm;

import static java.lang.Math.exp;
import static java.lang.Math.log;
import static java.lang.Math.sqrt;

import static org.opensha2.gmm.GmmInput.Field.MW;
import static org.opensha2.gmm.GmmInput.Field.RJB;
import static org.opensha2.gmm.GmmInput.Field.VS30;
import static org.opensha2.gmm.MagConverter.MB_TO_MW_ATKIN_BOORE;
import static org.opensha2.gmm.MagConverter.MB_TO_MW_JOHNSTON;
import static org.opensha2.gmm.SiteClass.HARD_ROCK;

import org.opensha2.gmm.GmmInput.Constraints;

import com.google.common.collect.Range;

import java.util.Map;

/**
 * Implementation of the Toro et al. (1997) ground motion model for stable
 * continental regions with 2002 updates. This implementation matches that used
 * in the 2008 USGS NSHMP and comes in two flavors (mb and Mw) to support the
 * 2008 central and eastern US model.
 *
 * <p><b>Note:</b> Direct instantiation of {@code GroundMotionModel}s is
 * prohibited. Use {@link Gmm#instance(Imt)} to retrieve an instance for a
 * desired {@link Imt}.
 *
 * <p><b>Implementation note:</b> Mean values are clamped per
 * {@link GmmUtils#ceusMeanClip(Imt, double)}.
 *
 * <p><b>Reference:</b> Toro, G.R., 2002, Modification of the Toro et al. (1997)
 * attenuation relations for large magnitudes and short distances: Risk
 * Engineering, Inc. <a href=
 * "http://www.ce.memphis.edu/7137/PDFs/attenuations/Toro_2001_(modification_1997).pdf"
 * >Report</a>
 *
 * <p><b>Reference:</b> Toro, G.R., Abrahamson, N.A., and Schneider, J.F., 1997,
 * A model of strong ground motions from earthquakes in central and eastern
 * North America: Best estimates and uncertainties: Seismological Research
 * Letters, v. 68, p. 41–57.
 *
 * <p><b>doi:</b> <a href="http://dx.doi.org/10.1785/gssrl.68.1.41">
 * 10.1785/gssrl.68.1.41</a>
 *
 * <p><b>Component:</b> not specified
 *
 * @author Peter Powers
 * @see Gmm#TORO_97_MB
 * @see Gmm#TORO_97_MW
 */
public abstract class ToroEtAl_1997 implements GroundMotionModel {

  // TODO fix clamp values (not implemented here yet) to match other CEUS gmms

  // notes from fortran source:
  //
  // added 0.04 and 0.4 s coeffs july 16 2008 (NRC files)
  // (TODO may want to remove these periods - coeff interpolation)
  // I would rather do these on the fly
  //
  // MbLg coeffs. BC/A 2-hz Siteamp = 1.58, with BC-A coef. diff. of 0.4574.
  //
  // Mw coeffs for BC rock. 3hz BC-A is 0.5423 (BC/A siteamp is then 1.72)
  // Mw coeffs. 3.33 hz is log-log from the 2.5 and 5 hz values.
  //
  // Sigma in nat log units. Saves a divide
  // Toro : slightly larger sigma for 1 and 2 s. Toro Lg based mag has
  // larger sigma for larger M (table 3, p 50 ,srl 1997. This isn't
  // in our rendering)

  static final String NAME = "Toro et al. (1997)";

  static final Constraints CONSTRAINTS = Constraints.builder()
      .set(MW, Range.closed(4.0, 8.0))
      .set(RJB, Range.closed(0.0, 1000.0))
      .set(VS30, Range.closed(760.0, 2000.0))
      .build();

  static final CoefficientContainer COEFFS_MW = new CoefficientContainer("Toro97Mw.csv");
  static final CoefficientContainer COEFFS_MB = new CoefficientContainer("Toro97Mb.csv");

  private static final class Coefficients {

    final Imt imt;
    final double t1, t1h, t2, t3, t4, t5, t6, th, tσ;

    Coefficients(Imt imt, CoefficientContainer cc) {
      this.imt = imt;
      Map<String, Double> coeffs = cc.get(imt);
      t1 = coeffs.get("t1");
      t1h = coeffs.get("t1h");
      t2 = coeffs.get("t2");
      t3 = coeffs.get("t3");
      t4 = coeffs.get("t4");
      t5 = coeffs.get("t5");
      t6 = coeffs.get("t6");
      th = coeffs.get("th");
      tσ = coeffs.get("tsigma");
    }
  }

  private final Coefficients coeffs;

  ToroEtAl_1997(final Imt imt) {
    coeffs = new Coefficients(imt, isMw() ? COEFFS_MW : COEFFS_MB);
  }

  @Override
  public final ScalarGroundMotion calc(final GmmInput in) {
    double μ = calcMean(coeffs, isMw(), in);
    return DefaultScalarGroundMotion.create(μ, coeffs.tσ);
  }

  abstract boolean isMw();

  private static final double calcMean(final Coefficients c, final boolean isMw,
      final GmmInput in) {

    double mag = in.Mw;
    double rJB = in.rJB;

    SiteClass siteClass = GmmUtils.ceusSiteClass(in.vs30);

    double thsq = c.th * c.th;

    // magnitude correction: with Toro model, you change the coefficients
    // appropriate to the magnitude.
    //
    // New, Nov 2006: the finite-fault correction, affects the fictitious
    // depth or bending point; from Toro Paducah paper. Mod. Dec 2007, mblg
    // to Mw for the correction.

    double mCorr;

    if (isMw) {
      mCorr = Math.exp(-1.25 + 0.227 * mag);
    } else {
      double magJ = MB_TO_MW_JOHNSTON.convert(mag);
      double cor1 = exp(-1.25 + 0.227 * magJ);
      double magAB = MB_TO_MW_ATKIN_BOORE.convert(mag);
      double cor2 = exp(-1.25 + 0.227 * magAB);
      mCorr = sqrt(cor1 * cor2); // geo mean
    }

    double dist = Math.sqrt(rJB * rJB + thsq * mCorr * mCorr);

    // default to SOFT_ROCK values
    double gnd = (siteClass == HARD_ROCK) ? c.t1h : c.t1;
    gnd += c.t2 * (mag - 6.0) + c.t3 * ((mag - 6.0) * (mag - 6.0));
    gnd += -c.t4 * log(dist) - c.t6 * dist;

    double factor = log(dist / 100.0);
    if (factor > 0) {
      gnd = gnd - (c.t5 - c.t4) * factor;
    }

    return GmmUtils.ceusMeanClip(c.imt, gnd);
  }

  static final class Mb extends ToroEtAl_1997 {
    static final String NAME = ToroEtAl_1997.NAME + ": mb";

    Mb(Imt imt) {
      super(imt);
    }

    @Override
    boolean isMw() {
      return false;
    }
  }

  static final class Mw extends ToroEtAl_1997 {
    static final String NAME = ToroEtAl_1997.NAME + ": Mw";

    Mw(Imt imt) {
      super(imt);
    }

    @Override
    boolean isMw() {
      return true;
    }
  }

}
