package org.opensha2.gmm;

import static java.lang.Math.log;

import java.util.Map;

/**
 * Boore & Atkinson 2008 site amplification model.
 *
 * @author Peter Powers
 */
class BooreAtkinsonSiteAmp {

  /*
   * NOTE: Currently only 2 Gmms use this model. One object is created for every
   * period instance. Should the model be more broadly used, consider adding a
   * loading cache.
   * 
   * Boore & Atkinson 2008 also have this model nested in implementation.
   * Consider removing and pointing here.
   */

  private static final CoefficientContainer COEFFS = new CoefficientContainer("ABsiteAmp.csv");

  private static final double V1 = 180.0;
  private static final double V2 = 300.0;
  private static final double A1 = 0.030;
  private static final double A2 = 0.090;
  private static final double A2FAC = 0.405465108;
  private static final double VREF = 760.0;
  private static final double DX = 1.098612289; // ln(a2/a1)
  private static final double DXSQ = 1.206948961;
  private static final double DXCUBE = 1.325968960;
  private static final double PLFAC = -0.510825624; // ln(0.06/0.1)

  private final SiteAmpCoefficients c;

  private static final class SiteAmpCoefficients {

    final double blin, b1, b2;

    SiteAmpCoefficients(Imt imt, CoefficientContainer cc) {
      Map<String, Double> coeffs = cc.get(imt);
      blin = coeffs.get("blin");
      b1 = coeffs.get("b1");
      b2 = coeffs.get("b2");
    }
  }

  BooreAtkinsonSiteAmp(Imt imt) {
    c = new SiteAmpCoefficients(imt, COEFFS);
  }

  /**
   * Utility method returns a site response value that is a continuous function
   * of <code>vs30</code>: log(AMP at vs30)-log(AMP at vs30r). Value at
   * <code>vs30 == vs30r</code> is unity. This function was adapted from
   * hazSUBXngatest.f and is valid for 23 periods.
   * 
   * @param lnPga reference natural log pga
   * @param vs30 at a site of interest
   * @param vs30r reference vs30, usually one value for soil and another for
   *        rock
   * @return the site response correction
   */
  double calc(final double lnPga, final double vs30, final double vs30r) {

    double dy, dyr, site, siter = 0.0;

    double bnl, bnlr;
    // some site term precalcs that are not M or d dependent
    if (V1 < vs30 && vs30 <= V2) {
      bnl = (c.b1 - c.b2) * log(vs30 / V2) / log(V1 / V2) + c.b2;
    } else if (V2 < vs30 && vs30 <= VREF) {
      bnl = c.b2 * log(vs30 / VREF) / log(V2 / VREF);
    } else if (vs30 <= V1) {
      bnl = c.b1;
    } else {
      bnl = 0.0;
    }

    if (V1 < vs30r && vs30r <= V2) {
      // repeat site term precalcs that are not M or d dependent
      // @ reference vs
      bnlr = (c.b1 - c.b2) * log(vs30r / V2) / log(V1 / V2) + c.b2;
    } else if (V2 < vs30r && vs30r <= VREF) {
      bnlr = c.b2 * log(vs30r / VREF) / log(V2 / VREF);
    } else if (vs30r <= V1) {
      bnlr = c.b1;
    } else {
      bnlr = 0.0;
    }

    dy = bnl * A2FAC; // ADF added line
    dyr = bnlr * A2FAC;
    site = c.blin * log(vs30 / VREF);
    siter = c.blin * log(vs30r / VREF);

    // Second part, nonlinear siteamp reductions below.
    if (lnPga <= A1) {
      site = site + bnl * PLFAC;
      siter = siter + bnlr * PLFAC;
    } else if (lnPga <= A2) {
      // extra lines smooth a kink in siteamp, pp 9-11 of boore sept
      // report. c and d from p 10 of boore sept report. Smoothing
      // introduces extra calcs in the range a1 < pganl < a2. Otherwise
      // nonlin term same as in june-july. Many of these terms are fixed
      // and are defined in data or parameter statements. Of course, if a1
      // and a2 change from their sept 06 values the parameters will also
      // have to be redefined. (a1,a2) represents a siteamp smoothing
      // range (units g)
      double cc = (3. * dy - bnl * DX) / DXSQ;
      double dd = (bnl * DX - 2. * dy) / DXCUBE;
      double pgafac = log(lnPga / A1);
      double psq = pgafac * pgafac;
      site = site + bnl * PLFAC + (cc + dd * pgafac) * psq;
      cc = (3. * dyr - bnlr * DX) / DXSQ;
      dd = (bnlr * DX - 2. * dyr) / DXCUBE;
      siter = siter + bnlr * PLFAC + (cc + dd * pgafac) * psq;
    } else {
      double pgafac = log(lnPga / 0.1);
      site = site + bnl * pgafac;
      siter = siter + bnlr * pgafac;
    }
    return site - siter;
  }

}
