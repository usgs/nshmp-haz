package gov.usgs.earthquake.nshmp.gmm;

import static gov.usgs.earthquake.nshmp.gmm.GmmInput.Field.MW;
import static gov.usgs.earthquake.nshmp.gmm.GmmInput.Field.RRUP;
import static gov.usgs.earthquake.nshmp.gmm.GmmInput.Field.VS30;
import static gov.usgs.earthquake.nshmp.gmm.GmmInput.Field.ZTOP;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA0P05;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA0P075;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA0P1;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA10P0;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA3P0;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA4P0;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA5P0;
import static gov.usgs.earthquake.nshmp.gmm.Imt.SA7P5;
import static java.lang.Math.exp;
import static java.lang.Math.log;

import java.util.Map;

import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Range;

import gov.usgs.earthquake.nshmp.gmm.GmmInput.Constraints;

/**
 * Implementation of the ground motion model by Wong et al. (2015) for deep
 * events (>20 km) on the Big Island, Hawaii.
 *
 * <p><b>Implementation notes:</b> This model, as published, only produces
 * results for a site class with Vs30 = 428 m/s. This implementation computes a
 * conversion to other Vs30 values using {@link BooreEtAl_2014}. To support the
 * full MPRS response spectrum and compatibility with {@link BooreEtAl_2014},
 * coefficients, which are provided in Hz, are mapped as closely as possible to
 * their corrresponding periods. This results in coefficients for 1.58 s being
 * used for 1.5 s. Coefficients for 0.07 and 0.08 seconds are provided, but this
 * implementation interpolates between 0.1 s and 0.05 s rather than interpolate
 * coefficients for 0.075 s. Spectral periods 4 s and 7.5 s are missing
 * coefficients and are also interpolated using adjacent periods.
 *
 * <p><b>Implementation update:</b> (October, 2020) Wong et al. provided the
 * USGS with new coefficients for VS30=760 m/s. These have been implemented as
 * separate model; the original model is now referenced as
 * {@link Gmm#WONG_15_428} and the updated version {@link Gmm#WONG_15_760}. In
 * addition, following discussion with the model devlopers, {@code WONG_15_760}
 * uses a fixed sigma of 0.8. The {@code WONG_15_760} model continues to use
 * {@link BooreEtAl_2014} site effect scaling for Vs30 values other than 760.
 *
 * <p><b>Note:</b> Direct instantiation of {@code GroundMotionModel}s is
 * prohibited. Use {@link Gmm#instance(Imt)} to retrieve an instance for a
 * desired {@link Imt}.
 *
 * <p><b>Reference:</b> Wong, I.G., Silva, W.J., Darragh, R., Gregor, N., and
 * Dober, M., 2015, A ground motion prediction model for deep earthquakes
 * beneath the island of Hawaii: Earthquake Spectra, v. 31, p. 1763–1788.
 *
 * <p><b>doi:</b><a href="http://doi.org/10.1193/012012EQS015M" target="_top">
 * 10.1193/012012EQS015M</a>
 *
 * <p><b>Component:</b> average horizontal
 *
 * @author Peter Powers
 * @see Gmm#WONG_15_428
 * @see Gmm#WONG_15_760
 */
@Beta
public abstract class WongEtAl_2015 implements GroundMotionModel {

  static final String NAME = "Wong et al. (2015)";

  static final Constraints CONSTRAINTS = Constraints.builder()
      .set(MW, Range.closed(5.0, 8.0))
      .set(RRUP, Range.closed(0.0, 300.0))
      .set(ZTOP, Range.closed(20.0, 60.0))
      .set(VS30, Range.closed(180.0, 1300.0))
      .build();

  private static final Map<Imt, Range<Imt>> INTERPOLATED_IMTS = Maps.immutableEnumMap(
      ImmutableMap.of(
          SA0P075, Range.closed(SA0P05, SA0P1),
          SA4P0, Range.closed(SA3P0, SA5P0),
          SA7P5, Range.closed(SA5P0, SA10P0)));

  /*
   * Developer note: c6 @ 1.995Hz (0.5s) missing negative sign per email from
   * Ivan on 07/23/2019.
   */
  static final CoefficientContainer COEFFS_428 = new CoefficientContainer("Wong15-428.csv");
  static final CoefficientContainer COEFFS_760 = new CoefficientContainer("Wong15-760.csv");

  private static final class Coefficients {

    final Imt imt;
    final double vRef;
    final double c1, c2, c3, c4, c5, c6, σ;

    Coefficients(Imt imt, double vRef, CoefficientContainer cc) {
      this.imt = imt;
      this.vRef = vRef;
      Map<String, Double> coeffs = cc.get(imt);
      c1 = coeffs.get("C1");
      c2 = coeffs.get("C2");
      c3 = coeffs.get("C3");
      c4 = coeffs.get("C4");
      c5 = coeffs.get("C5");
      c6 = coeffs.get("C6");
      σ = coeffs.get("sigma");
    }
  }

  private final Coefficients coeffs;
  private final BooreEtAl_2014 siteDelegate;

  private final boolean interpolated;
  private final GroundMotionModel interpolatedGmm;

  WongEtAl_2015(Coefficients coeffs, Gmm subtype) {
    this.coeffs = coeffs;
    Imt imt = coeffs.imt;
    siteDelegate = (BooreEtAl_2014) Gmm.BSSA_14.instance(imt);
    interpolated = INTERPOLATED_IMTS.containsKey(imt);
    interpolatedGmm = interpolated
        ? new InterpolatedGmm(subtype, imt, INTERPOLATED_IMTS.get(imt))
        : null;
  }

  @Override
  public final ScalarGroundMotion calc(final GmmInput in) {

    if (interpolated) {
      return interpolatedGmm.calc(in);
    }

    double μ = calcMean(coeffs, in);
    double site = 0.0;
    if (in.vs30 != coeffs.vRef) {
      GmmInput inVsRef = GmmInput.builder().fromCopy(in)
          .vs30(coeffs.vRef)
          .build();
      site = siteDelegate.calc(in).mean() - siteDelegate.calc(inVsRef).mean();
    }

    return DefaultScalarGroundMotion.create(
        μ + site,
        coeffs.σ);
  }

  private static final double calcMean(final Coefficients c, final GmmInput in) {
    double Mw = in.Mw;
    return c.c1 + c.c2 * Mw +
        (c.c4 + c.c5 * Mw) * log(in.rJB + exp(c.c3)) +
        c.c6 * (Mw - 6.0) * (Mw - 6.0);
  }

  static final class Site428 extends WongEtAl_2015 {
    static final String NAME = WongEtAl_2015.NAME + " : 428 m/s";

    Site428(Imt imt) {
      super(new Coefficients(imt, 428.0, COEFFS_428), Gmm.WONG_15_428);
    }
  }

  static final class Site760 extends WongEtAl_2015 {
    static final String NAME = WongEtAl_2015.NAME + " : 760 m/s";

    Site760(Imt imt) {
      super(new Coefficients(imt, 760.0, COEFFS_760), Gmm.WONG_15_760);
    }
  }
}
