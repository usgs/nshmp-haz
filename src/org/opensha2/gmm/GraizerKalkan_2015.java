package org.opensha2.gmm;

import static java.lang.Math.abs;
import static java.lang.Math.atan;
import static java.lang.Math.cos;
import static java.lang.Math.exp;
import static java.lang.Math.log;
import static java.lang.Math.max;
import static java.lang.Math.pow;
import static java.lang.Math.sqrt;

import static org.opensha2.gmm.FaultStyle.REVERSE;
import static org.opensha2.gmm.GmmInput.Field.MW;
import static org.opensha2.gmm.GmmInput.Field.RAKE;
import static org.opensha2.gmm.GmmInput.Field.RJB;
import static org.opensha2.gmm.GmmInput.Field.VS30;

import org.opensha2.data.Interpolator;
import org.opensha2.eq.fault.Faults;
import org.opensha2.gmm.GmmInput.Constraints;

import com.google.common.collect.Range;

/**
 * Implementation of the updated Graizer & Kalkan (2015, 2016) ground motion
 * model. The model computes spectral accelerations as a continuous function of
 * spectral period. For consistency with the NGA-West2, this implementation
 * supports the common set of (22) spectral periods supported by the NGA-West2
 * models; additional periods can be added at any time.
 * 
 * <p><b>References:</b><ul>
 * 
 * <li>Graizer, V., and E. Kalkan, 2015, Update of the Graizer–Kalkan
 * ground-motion prediction equations for shallow crustal continental
 * earthquakes: U.S. Geol. Surv. Open-File Report 2015-1009. <b>doi:</b> <a
 * href="http://dx.doi.org/10.3133/ofr20151009">10.3133/ofr20151009</a></li>
 * 
 * <li>Graizer, V., and E. Kalkan, 2016, Summary of the GK15 ground-motion
 * prediction equation for horizontal PGA and 5% damped PSA from shallow crustal
 * continental earthquakes: Bulletin of the Seismological Society of America, v.
 * 106, n. 2, p. 687-707. <b>doi:</b> <a
 * href="http://dx.doi.org/10.1785/0120150194"> 10.1785/0120150194</a></li>
 * 
 * </ul>
 * 
 * <p><b>Component:</b> geometric mean of two randomly oriented horizontal
 * components
 * 
 * @author Peter Powers
 * @see Gmm#GK_15
 */
final class GraizerKalkan_2015 implements GroundMotionModel {

  /*
   * Developer notes:
   * 
   * This model does not support specific periods as most other GMMs do; it can
   * handle any frequency. Currently the referenced coefficient file contains
   * only those periods that match the NGAW2 set, but more can be added at any
   * time.
   * 
   * Basin term is a function of z1p5. Current implementation linearly
   * interpolates between z1p0 and z2p5 if they are not NaN, otherwise uses a
   * default value of 0.15 km. This value is
   */

  static final String NAME = "Graizer & Kalkan (2015)";

  static final CoefficientContainer COEFFS = new CoefficientContainer("GK15.csv");

  static final Constraints CONSTRAINTS = Constraints.builder()
      .set(MW, Range.closed(5.0, 8.5))
      .set(RJB, Range.closed(0.0, 250.0))
      .set(RAKE, Faults.RAKE_RANGE)
      .set(VS30, Range.closed(200.0, 1300.0))
      .build();

  private final Imt imt;
  private final double period;

  GraizerKalkan_2015(Imt imt) {
    this.imt = imt;
    this.period = imt.equals(Imt.PGA) ? 0.01 : imt.period();
  }

  @Override
  public ScalarGroundMotion calc(final GmmInput in) {
    double z1p5 = z1p5(in.z1p0, in.z2p5);
    double μ = lnPga(in.Mw, in.rRup, in.rake, in.vs30, z1p5);
    double σ = stdDev(period);
    if (imt.isSA()) {
      double sa = spectralShape(period, in.Mw, in.rRup, in.vs30, z1p5);
      μ += log(sa);
    }
    return DefaultScalarGroundMotion.create(μ, σ);
  }

  /* Default basin depth per pers. comm w/ Vladimir circa 2012 */
  private static final double Z1P5_DEFAULT = 0.15;

  private static double z1p5(double z1p0, double z2p5) {
    if (Double.isNaN(z1p0) || Double.isNaN(z2p5)) {
      return Z1P5_DEFAULT;
    }
    return Interpolator.findY(1.0, z1p0, 2.5, z2p5, 1.5);
  }

  private static final double m1 = -0.0012;
  private static final double m2 = -0.38;
  private static final double m3 = 0.0006;
  private static final double m4 = 3.9;

  private static final double a1 = 0.01686;
  private static final double a2 = 1.2695;
  private static final double a3 = 0.0001;

  private static final double Dsp = 0.75;

  private static final double t1 = 0.001;
  private static final double t2 = 0.59;
  private static final double t3 = -0.0005;
  private static final double t4 = -2.3;

  private static final double s1 = 0.001;
  private static final double s2 = 0.077;
  private static final double s3 = 0.3251;

  private static final double spectralShape(
      double T,
      double Mw,
      double rRup,
      double vs30,
      double z1p5) {

    /* Equations 9b-f */
    double mu = m1 * rRup + m2 * Mw + m3 * vs30 + m4;
    double I = (a1 * Mw + a2) * exp(a3 * rRup);
    double S = s1 * rRup - (s2 * Mw + s3);
    double Tsp0 = max(0.3, abs(t1 * rRup + t2 * Mw + t3 * vs30 + t4));
    double ζ = 1.763 - 0.25 * atan(1.4 * (z1p5 - 1.0));

    /* Equation 8 */
    double F1A = (log(T) + mu) / S;
    double F1 = I * exp(-0.5 * F1A * F1A);
    double F2A = pow(T / Tsp0, ζ);
    double F2 = 1.0 / sqrt((1.0 - F2A) * (1.0 - F2A) + 4.0 * Dsp * Dsp * F2A);

    return F1 + F2;
  }

  /*
   * Models and coefficients for inter- and intra-event terms, τ and φ, are
   * provided in the USGS Open-File reference; here we only compute total σ.
   */
  private static final double stdDev(double T) {
    double σ1 = 0.668 + 0.0047 * log(T);
    double σ2 = 0.8 + 0.13 * log(T);
    return max(σ1, σ2);
  }

  private static final double c1 = 0.14;
  private static final double c2 = -6.25;
  private static final double c3 = 0.37;
  private static final double c4 = 2.237;
  private static final double c5 = -7.542;
  private static final double c6 = -0.125;
  private static final double c7 = 1.19;
  private static final double c8 = -6.15;
  private static final double c9 = 0.6;
  private static final double c10 = 0.345;
  private static final double c11 = 1.077;
  private static final double c12 = 1.5;
  private static final double c13 = 0.7;
  private static final double c14 = 40.0;

  private static final double bv = -0.24;
  private static final double VA = 484.5;

  /*
   * Regional quality factor; California value is 150; see Graizer & Kalkan
   * (2016) for other regionalizations.
   */
  private static final double Qo = 150.0;

  private static final double lnPga(
      double Mw,
      double rRup,
      double rake,
      double vs30,
      double z1p5) {

    /* Magnitude and style-of-faulting; equation 3 */
    double F = (GmmUtils.rakeToFaultStyle_NSHMP(rake) == REVERSE) ? 1.28 : 1.0;
    double G1 = log((c1 * atan(Mw + c2) + c3) * F);

    /* Distance attenuation; equation 4 */
    double Ro = c4 * Mw + c5;
    double RRo = rRup / Ro;
    double Do = c6 * cos(c7 * (Mw + c8)) + c9;
    double G2 = -0.5 * log((1.0 - RRo) * (1.0 - RRo) + 4 * Do * Do * RRo);

    /* Anelastic attenuation; equation 5 */
    double G3 = -c10 * rRup / Qo;

    /* Site correction; equation 6 */
    double G4 = bv * log(vs30 / VA);

    /* Basin depth; equation 7b */
    double ABz1 = c12 / (z1p5 + 0.1);
    double ABz1sq = ABz1 * ABz1;
    double ABz2 = (1.0 - ABz1sq) * (1.0 - ABz1sq);
    double ABz = c11 / sqrt(ABz2 + 4.0 * c13 * c13 * ABz1sq);

    /* Basin distance; equation 7c */
    double ABr1 = c14 / (rRup + 0.1);
    double ABr1sq = ABr1 * ABr1;
    double ABr2 = (1.0 - ABr1sq) * (1.0 - ABr1sq);
    double ABr = 1.0 / sqrt(ABr2 + 4.0 * c13 * c13 * ABr1sq);

    /* Basin effect; equation 7a */
    double G5 = log(1 + ABr * ABz);

    return G1 + G2 + G3 + G4 + G5;
  }

}
