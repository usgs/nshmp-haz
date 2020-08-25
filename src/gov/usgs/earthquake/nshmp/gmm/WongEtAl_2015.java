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
import static java.lang.Math.max;

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
 * @see Gmm#WONG_15
 */
@Beta
public final class WongEtAl_2015 implements GroundMotionModel {

  static final String NAME = "Wong et al. (2015)";

  // TODO
  static final Constraints CONSTRAINTS = Constraints.builder()
      .set(MW, Range.closed(5.0, 8.0))
      .set(RRUP, Range.closed(0.0, 300.0))
      .set(ZTOP, Range.closed(20.0, 60.0))
      .set(VS30, Range.singleton(760.0))
      .build();

  private static final double R_MIN = 20.0;
  private static final double V_REF = 428.0;

  private static final Map<Imt, Range<Imt>> INTERPOLATED_IMTS = Maps.immutableEnumMap(
      ImmutableMap.of(
          SA0P075, Range.closed(SA0P05, SA0P1),
          SA4P0, Range.closed(SA3P0, SA5P0),
          SA7P5, Range.closed(SA5P0, SA10P0)));

  /*
   * Developer note: c6 @ 1.995Hz (0.5s) missing negative sign per email from
   * Ivan on 07/23/2019.
   */
  static final CoefficientContainer COEFFS = new CoefficientContainer("Wong15.csv");

  private static final class Coefficients {

    final double c1, c2, c3, c4, c5, c6, σ;

    Coefficients(Imt imt, CoefficientContainer cc) {
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
  private final BooreEtAl_2014 delegate;

  private final boolean interpolated;
  private final GroundMotionModel interpolatedGmm;

  WongEtAl_2015(final Imt imt) {
    coeffs = new Coefficients(imt, COEFFS);
    delegate = (BooreEtAl_2014) Gmm.BSSA_14.instance(imt);
    interpolated = INTERPOLATED_IMTS.containsKey(imt);
    interpolatedGmm = interpolated
        ? new InterpolatedGmm(Gmm.WONG_15, imt, INTERPOLATED_IMTS.get(imt))
        : null;
  }

  @Override
  public final ScalarGroundMotion calc(final GmmInput in) {

    if (interpolated) {
      return interpolatedGmm.calc(in);
    }

    GmmInput in428 = GmmInput.builder().fromCopy(in).vs30(V_REF).build();
    double μ = calcMean(coeffs, in);
    double site = delegate.calc(in).mean() - delegate.calc(in428).mean();
    return DefaultScalarGroundMotion.create(
        μ + site,
        coeffs.σ);
  }

  private static final double calcMean(final Coefficients c, final GmmInput in) {
    double Mw = in.Mw;
    return c.c1 + c.c2 * Mw +
        (c.c4 + c.c5 * Mw) * log(max(R_MIN, in.rJB) + exp(c.c3)) +
        c.c6 * (Mw - 6.0) * (Mw - 6.0);
  }
}
