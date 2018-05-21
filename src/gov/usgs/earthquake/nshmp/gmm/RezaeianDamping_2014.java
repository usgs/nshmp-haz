package gov.usgs.earthquake.nshmp.gmm;

import static com.google.common.base.Preconditions.checkArgument;
import static gov.usgs.earthquake.nshmp.gmm.Gmm.ASK_14;
import static gov.usgs.earthquake.nshmp.gmm.Gmm.BA_08;
import static gov.usgs.earthquake.nshmp.gmm.Gmm.BSSA_14;
import static gov.usgs.earthquake.nshmp.gmm.Gmm.CB_08;
import static gov.usgs.earthquake.nshmp.gmm.Gmm.CB_14;
import static gov.usgs.earthquake.nshmp.gmm.Gmm.CY_08;
import static gov.usgs.earthquake.nshmp.gmm.Gmm.CY_14;
import static gov.usgs.earthquake.nshmp.gmm.Gmm.IDRISS_14;
import static java.lang.Math.abs;
import static java.lang.Math.log;
import static java.lang.Math.sqrt;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.annotations.Beta;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Lists;
import com.google.common.collect.Range;
import com.google.common.primitives.Doubles;

import gov.usgs.earthquake.nshmp.calc.CalcConfig;
import gov.usgs.earthquake.nshmp.data.Interpolator;
import gov.usgs.earthquake.nshmp.data.XySequence;

/**
 * Implementation of Rezaeian et al. (2014) damping scaling factor model.
 * 
 * <p><b>Reference:</b> Rezaeian, S., Bozorgnia, Y., Idriss, I.M., Abrahamson,
 * N., Campbell, K., and Silva, W., 2014, Damping scaling factors for elastic
 * response spectra for shallow crustal earthquakes in active tectonic regions:
 * "Average" horizontal component: Earthquake SPectra, v. 30, n. 2, p. 939-963.
 * 
 * <p><b>doi:</b> <a href="http://dx.doi.org/10.1193/100512EQS298M">
 * 10.1193/100512EQS298M</a>
 *
 * @author Peter Powers
 */
@Beta
public class RezaeianDamping_2014 implements GmmPostProcessor {

  /*
   * Developer notes:
   * 
   * The apply method doesn't do anything if the supplied damping ratio is set
   * to 5% (lnDSF = 0.0). In point of fact, the computeDsf() model returns a
   * value much closer to zero for a damping ratio of 5.027590868... The
   * assumption is that only coarser damping ratios will be used (e.g. 3.5, 4.0)
   * such that this inconsistency will not make a difference.
   */

  private static final String NAME = "Rezaeian et al. (2014)";

  private static final CoefficientContainer COEFFS;

  private static final Map<Imt, Coefficients> COEFF_MAP = new EnumMap<>(Imt.class);
  private static final Map<Imt, XySequence> CORR_COEFF_MAP = new EnumMap<>(Imt.class);

  private static final String[] corrCoeffKeys =
      { "0.5", "1", "2", "3", "5", "7", "10", "15", "20", "25", "30" };

  private static final List<Double> CORR_COEFF_DSFS =
      FluentIterable.from(corrCoeffKeys)
          .transform(Doubles.stringConverter())
          .toList();

  private static final Interpolator CC_INTERP = Interpolator.builder().build();

  static {
    COEFFS = new CoefficientContainer("Rezaeian14.csv");
    for (Imt imt : COEFFS.imts()) {
      COEFF_MAP.put(imt, new Coefficients(imt, COEFFS));
      CORR_COEFF_MAP.put(imt, initCorrCoeff(imt, COEFFS));
    }
  }

  /**
   * The range of damping ratios supported by this model: {@code [0.5..30.0]}.
   */
  public static final Range<Double> DAMPING_RATIO_RANGE = Range.closed(
      CORR_COEFF_DSFS.get(0),
      CORR_COEFF_DSFS.get(CORR_COEFF_DSFS.size() - 1));

  /**
   * The GMMs supported by this model. Currently the Rezaien et al. (2014) model
   * only supports GMMs for active continental crust (i.e. the NGA-West1 and
   * NGA-West2 models as implemented for the 2008 and 2014 NSHMs for the
   * Conterminous U.S. This set is for external use and filtering prior to
   * calling public methods of this class.
   */
  public static final Set<Gmm> SUPPORTED_GMMS = EnumSet.of(
      BA_08, CB_08, CY_08,
      ASK_14, BSSA_14, CB_14, CY_14, IDRISS_14);

  private static final class Coefficients {

    final Imt imt;
    final double a0, a1, b0, b1, b2, b3, b4, b5, b6, b7, b8;

    Coefficients(Imt imt, CoefficientContainer cc) {
      this.imt = imt;
      Map<String, Double> coeffs = cc.get(imt);
      a0 = coeffs.get("a0");
      a1 = coeffs.get("a1");
      b0 = coeffs.get("b0");
      b1 = coeffs.get("b1");
      b2 = coeffs.get("b2");
      b3 = coeffs.get("b3");
      b4 = coeffs.get("b4");
      b5 = coeffs.get("b5");
      b6 = coeffs.get("b6");
      b7 = coeffs.get("b7");
      b8 = coeffs.get("b8");
    }
  }

  /*
   * Correlation coeffs at fixed damping ratios as a sequence for interpolation
   * over the range [0.5..30.0]
   */
  private static XySequence initCorrCoeff(Imt imt, CoefficientContainer cc) {
    Map<String, Double> coeffs = cc.get(imt);
    List<Double> corrCoeffs = Lists.newArrayList();
    for (String key : corrCoeffKeys) {
      corrCoeffs.add(coeffs.get(key));
    }
    return XySequence.createImmutable(CORR_COEFF_DSFS, corrCoeffs);
  }

  /* The correlation coefficient (rho) for the supplied IMT and DSF. */
  private static double calcCorrCoeff(Imt imt, double dsf) {
    XySequence cc = CORR_COEFF_MAP.get(imt);
    return CC_INTERP.findY(cc, dsf);
  }

  private final double dampingRatio;
  private final boolean updateSigma;

  RezaeianDamping_2014(CalcConfig config) {
    dampingRatio = config.hazard.gmmDampingRatio;
    updateSigma = config.hazard.gmmDampingSigma;
  }

  /**
   * If the configured damping ratio equals 5.0% or {@link #SUPPORTED_GMMS} does
   * not contain the supplied {@code gmm}, this method returns the supplied
   * {@code ScalarGroundMotion}, {@code sgm}.
   */
  @Override
  public ScalarGroundMotion apply(
      ScalarGroundMotion sgm,
      GmmInput in,
      Imt imt,
      Gmm gmm) {

    if (dampingRatio == 5.0 || !SUPPORTED_GMMS.contains(gmm)) {
      return sgm;
    }

    checkArgument(
        COEFF_MAP.containsKey(imt),
        "%s damping model does not support IMT: %s", NAME, imt.name());

    Coefficients c = COEFF_MAP.get(imt);
    double dsf = computeDsf(c, dampingRatio, in.Mw, in.rRup);
    double σ = updateSigma ? updateSigma(c, dampingRatio, sgm.sigma()) : sgm.sigma();
    return DefaultScalarGroundMotion.create(sgm.mean() + dsf, σ);
  }

  /*
   * Compute the damping scaling factor (DSF) in natural log units for the
   * damping ratio of interest. Equation (3).
   */
  private static double computeDsf(
      Coefficients c,
      double dampingRatio,
      double Mw,
      double rRup) {

    if (dampingRatio == 5.0) {
      return 0.0;
    }

    double lnβ = log(dampingRatio);
    double lnβsq = lnβ * lnβ;
    return c.b0 + c.b1 * lnβ + c.b2 * lnβsq +
        (c.b3 + c.b4 * lnβ + c.b5 * lnβsq) * Mw +
        (c.b6 + c.b7 * lnβ + c.b8 * lnβsq) * log(rRup + 1.0);
  }

  /*
   * Adjust the aleatory variability of ground motion for the damping ratio of
   * interest. Equation (5).
   */
  private static double updateSigma(
      Coefficients c,
      double dampingRatio,
      double σ) {

    double βTerm = log(dampingRatio / 5.0);
    double σDsf = abs(c.a0 * βTerm + c.a1 * βTerm * βTerm);
    double ρ = calcCorrCoeff(c.imt, dampingRatio);
    return sqrt(σ * σ + σDsf * σDsf + 2.0 * σ * σDsf * ρ);
  }
}
