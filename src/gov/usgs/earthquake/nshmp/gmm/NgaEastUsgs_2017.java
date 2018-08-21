package gov.usgs.earthquake.nshmp.gmm;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.io.Resources.getResource;
import static com.google.common.io.Resources.readLines;
import static gov.usgs.earthquake.nshmp.data.Data.checkWeights;
import static gov.usgs.earthquake.nshmp.gmm.GmmInput.Field.MW;
import static gov.usgs.earthquake.nshmp.gmm.GmmInput.Field.RJB;
import static gov.usgs.earthquake.nshmp.gmm.GmmInput.Field.VS30;
import static gov.usgs.earthquake.nshmp.gmm.GroundMotionTables.TABLE_DIR;
import static java.lang.Math.exp;
import static java.lang.Math.log;
import static java.lang.Math.min;
import static java.lang.Math.sqrt;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Range;
import com.google.common.primitives.Doubles;

import gov.usgs.earthquake.nshmp.calc.ExceedanceModel;
import gov.usgs.earthquake.nshmp.data.Interpolator;
import gov.usgs.earthquake.nshmp.gmm.GmmInput.Constraints;
import gov.usgs.earthquake.nshmp.gmm.GroundMotionTables.GroundMotionTable;
import gov.usgs.earthquake.nshmp.gmm.GroundMotionTables.GroundMotionTable.Position;
import gov.usgs.earthquake.nshmp.internal.Parsing;
import gov.usgs.earthquake.nshmp.internal.Parsing.Delimiter;
import gov.usgs.earthquake.nshmp.util.Maths;

/**
 * Implementation of the PEER NGA-East for USGS ground motion model. This is a
 * custom version of the model developed specifically for USGS applications. It
 * is a composite model that consists of 17 median ground motion models with
 * period dependent weights.
 * 
 * <p>Calculation of hazard using this preliminary implementation deviates
 * somewhat from the current nshmp-haz PSHA pipeline and required implementation
 * of a {@code MultiScalarGroundMotion}. A {@code MultiScalarGroundMotion}
 * stores arrays of means and sigmas with associated weights and can only be
 * properly processed by {@link ExceedanceModel#NSHM_CEUS_MAX_INTENSITY} at this
 * time.
 * 
 * <p>This class also manages implementations of 22 'seed' models, 19 of which
 * were used to generate (via Sammons mapping) the 17 NGA-East for USGS models
 * and associated weights, and 3 of which are updates. This class also handles
 * USGS logic tree of 14 of those seed models. Ground motions for most models
 * are computed via table lookups (SP16 is the exception).
 * 
 * <p>On it's own, NGA-East is a hard rock model returning results for a site
 * class where Vs30 = 3000 m/s. To accomodate other site classes, the Stewart et
 * al. (2017) CEUS site amplification model is used. This model is applicable to
 * 200 ≤ vs30 ≤ 2000 m/s. In the current implementation, for vs30 < 200, vs30 =
 * 200 m/s; for vs30 > 2000, vs30 = 3000 m/s.
 *
 * <p><b>Note:</b> Direct instantiation of {@code GroundMotionModel}s is
 * prohibited. Use {@link Gmm#instance(Imt)} to retrieve an instance for a
 * desired {@link Imt}.
 *
 * <p><b>Implementation note:</b> Mean values are NOT clamped per
 * {@link GmmUtils#ceusMeanClip(Imt, double)}.
 *
 * <p><b>Reference:</b> Goulet, C., Bozorgnia, Y., Kuehn, N., Al Atik L.,
 * Youngs, R., Graves, R., Atkinson, G., 2017, NGA-East ground-motion models for
 * the U.S. Geological Survey national seismic hazard maps: PEER Report No.
 * 2017/03, 180 p.
 * 
 * <p><b>Reference:</b> Stewart, J., Parker, G., Harmon, J., Atkinson, G.,
 * Boore, D., Darragh, R., Silva, W., and Hashash, Y., 2017, Expert panel
 * recommendations for ergodic site amplification in central and eastern North
 * America: PEER Report No. 2017/04, 66 p.
 *
 * <p><b>Reference:</b> Hashash, Y., Harmon, J., Ilhan, O., Parker, G., and
 * Stewart, 2017, Recommendation for ergodic nonlinear site amplification in
 * central and eastern North America: PEER Report No. 2017/05, 62 p.
 * 
 * <p><b>Component:</b> average horizontal (RotD50)
 *
 * @author Peter Powers
 */
@Beta
public abstract class NgaEastUsgs_2017 implements GroundMotionModel {

  /*
   * TODO
   * 
   * Update javadoc (above).
   * 
   * Cluster analysis is incorrect as currently implemented; analysis performed
   * after combining models
   * 
   * Deagg will currently use weight-averaged means; this is incorrect, or at
   * least requires more study to determine if it generates an acceptable
   * approximation
   * 
   * Note: supported periods are derived from sigma coefficient files. Several
   * supported periods have been commented out because they are not represented
   * in teh site amplification model.
   * 
   * When supplied with tables for the 13 usgs models, 0.01s was added with
   * values distinct from PGA, however the seed models are missing this period.
   * For now we've duplicated PGA for 0.01s ground motion values in the seed
   * model tables. Because a coefficient table (sigma coeffs in this case) is
   * referenced for supported IMTs, the ground motion tables across seed and
   * sammons models must be consistent.
   * 
   * Missing PGV tables: Grazier, PEER_EX, PEER_GP, PZCT15_M1SS, PZCT15_M2ES
   * 
   * Notes on sigma:
   * 
   * When supplied with the NGA-East for USGS (2017), PEER recommended use of an
   * updated EPRI (2013) model (Table 5.5). The NSHMP asked PEER to develop an
   * improved ϕ_s2s model, which necessitated reverting to the functional forms
   * for τ and ϕ_ss. The tau and ϕ_s2s models have a single branch each; the
   * ϕ_ss model has three branches. The ϕ_ss and τ models both have coefficients
   * for low, central, and high statistical uncertainty branches; no statistical
   * uncertianty model was developed for ϕ_s2s
   * 
   * Sigma tables are broken into 'lo', 'mid', and 'hi' files representing the
   * 'Low', 'Central', and 'Hi' branches of the sigma logi tree. In many cases
   * coefficents are constant across all periods, but because of the statistical
   * uncertainty branching, it is easier to repeat the values in the coefficient
   * tables/files than to have to encode lo-mid-hi branching logic in sigma
   * calculation methods. Similarly, the ϕ_s2s coefficents, for which there is
   * no statistical uncertainty model/branching, are the same across all files.
   */
  static final String NAME = "NGA-East USGS (2017)";

  static final Constraints CONSTRAINTS = Constraints.builder()
      .set(MW, Range.closed(4.0, 8.2))
      .set(RJB, Range.closed(0.0, 1500.0))
      .set(VS30, Range.closed(200.0, 3000.0))
      .build();

  /*
   * Sigma coefficients for global model from tables 11-3 (tau) and 11-9 (phi).
   */
  static CoefficientContainer COEFFS_SIGMA_LO;
  static CoefficientContainer COEFFS_SIGMA_MID;
  static CoefficientContainer COEFFS_SIGMA_HI;
  static CoefficientContainer COEFFS_SIGMA_EPRI;

  /* Immutable, ordered map */
  static Map<String, Double> USGS_SEED_WEIGHTS;

  static {
    COEFFS_SIGMA_LO = new CoefficientContainer("nga-east-usgs-sigma-lo.csv");
    COEFFS_SIGMA_MID = new CoefficientContainer("nga-east-usgs-sigma-mid.csv");
    COEFFS_SIGMA_HI = new CoefficientContainer("nga-east-usgs-sigma-hi.csv");
    COEFFS_SIGMA_EPRI = new CoefficientContainer("nga-east-usgs-sigma-epri.csv");
    USGS_SEED_WEIGHTS = initSeedWeights();
  }

  private static Map<String, Double> initSeedWeights() {
    try {
      Map<String, Double> wtMap = readLines(
          getResource(
              NgaEastUsgs_2017.class,
              TABLE_DIR + "nga-east-seed-weights.dat"),
          StandardCharsets.UTF_8)
              .stream()
              .skip(1)
              .map(line -> Parsing.splitToList(line, Delimiter.COMMA))
              .collect(Collectors.toMap(
                  entry -> entry.get(0),
                  entry -> Double.valueOf(entry.get(1))));

      checkWeights(wtMap.values());
      return ImmutableMap.copyOf(wtMap);

    } catch (IOException ioe) {
      throw new RuntimeException(ioe);
    }
  }

  private static final double[] SIGMA_WTS = { 0.185, 0.63, 0.185 };
  private static final double[] SITE_AMP_WTS = SIGMA_WTS;

  /* ϕ_s2s constants */
  private static final double VΦ1 = 1200.0;
  private static final double VΦ2 = 1500.0;

  private static final class CoefficientsSigma {

    /* τ coefficients */
    final double τ1, τ2, τ3, τ4;

    /* ϕ_ss coefficients; g=global, m=mag-dep, c=constant */
    final double ga, gb, ma, mb, c;

    /* ϕ_s2s coefficients */
    final double ϕs2s1, ϕs2s2;

    CoefficientsSigma(Imt imt, CoefficientContainer cc) {
      Map<String, Double> coeffs = cc.get(imt);
      τ1 = coeffs.get("t1");
      τ2 = coeffs.get("t2");
      τ3 = coeffs.get("t3");
      τ4 = coeffs.get("t4");
      ga = coeffs.get("ss_ga");
      gb = coeffs.get("ss_gb");
      ma = coeffs.get("ss_ma");
      mb = coeffs.get("ss_mb");
      c = coeffs.get("ss_c");
      ϕs2s1 = coeffs.get("s2s1");
      ϕs2s2 = coeffs.get("s2s2");
    }
  }

  /* Updated EPRI (2013); initial NGAE-East team recommendation. */
  private static final class CoefficientsSigmaEpri {

    final double τ_m5, φ_m5, τ_m6, φ_m6, τ_m7, φ_m7;

    CoefficientsSigmaEpri(Imt imt, CoefficientContainer cc) {
      Map<String, Double> coeffs = cc.get(imt);
      τ_m5 = coeffs.get("tau_M5");
      φ_m5 = coeffs.get("phi_M5");
      τ_m6 = coeffs.get("tau_M6");
      φ_m6 = coeffs.get("phi_M6");
      τ_m7 = coeffs.get("tau_M7");
      φ_m7 = coeffs.get("phi_M7");
    }
  }

  private final CoefficientsSigma σCoeffsLo;
  private final CoefficientsSigma σCoeffsMid;
  private final CoefficientsSigma σCoeffsHi;
  private final CoefficientsSigmaEpri σCoeffsEpri;

  NgaEastUsgs_2017(final Imt imt) {
    σCoeffsLo = new CoefficientsSigma(imt, COEFFS_SIGMA_LO);
    σCoeffsMid = new CoefficientsSigma(imt, COEFFS_SIGMA_MID);
    σCoeffsHi = new CoefficientsSigma(imt, COEFFS_SIGMA_HI);
    σCoeffsEpri = new CoefficientsSigmaEpri(imt, COEFFS_SIGMA_EPRI);
  }

  /* Central branch of sigma model. */
  double sigmaCentral(double Mw, double vs30) {
    return sigma(σCoeffsMid, Mw, vs30);
  }

  /* 3-branch sigma model. */
  SigmaSet sigmaSet(double Mw, double vs30) {
    double[] sigmas = {
        sigma(σCoeffsLo, Mw, vs30),
        sigma(σCoeffsMid, Mw, vs30),
        sigma(σCoeffsHi, Mw, vs30),
    };
    SigmaSet σSet = new SigmaSet();
    σSet.sigmas = sigmas;
    σSet.weights = SIGMA_WTS;
    return σSet;
  }

  /* 3-branch sigma model. */
  SigmaSet sigmaSetHybrid(double Mw, double vs30) {
    double[] sigmas = {
        sigmaHybrid(σCoeffsLo, σCoeffsEpri, Mw, vs30),
        sigmaHybrid(σCoeffsMid, σCoeffsEpri, Mw, vs30),
        sigmaHybrid(σCoeffsHi, σCoeffsEpri, Mw, vs30),
    };
    SigmaSet σSet = new SigmaSet();
    σSet.sigmas = sigmas;
    σSet.weights = SIGMA_WTS;
    return σSet;
  }

  private static double sigma(CoefficientsSigma c, double Mw, double vs30) {

    /* τ model; global branch only; Equation 5-1. */
    double τ = tau(Mw, c.τ1, c.τ2, c.τ3, c.τ4);

    /* φ_ss model; global, constant, and mag-dep. branches Equation 5.2. */
    double φ_ss = 0.8 * phi_ss(Mw, c.ga, c.gb) +
        0.1 * c.c +
        0.1 * phi_ss(Mw, c.ma, c.mb);

    /* φ_s2s model; single branch; Stewart et al. */
    double φ_s2s = phi_s2s(vs30, c.ϕs2s1, c.ϕs2s2);

    return Maths.hypot(τ, φ_ss, φ_s2s);
  }

  /* Updated EPRI (2013); initial NGA-East team recomendation. */
  double sigmaEpri(double Mw) {
    double τ;
    double φ;
    if (Mw <= 5.0) {
      τ = σCoeffsEpri.τ_m5;
      φ = σCoeffsEpri.φ_m5;
    } else if (Mw <= 6.0) {
      τ = Interpolator.findY(5.0, σCoeffsEpri.τ_m5, 6.0, σCoeffsEpri.τ_m6, Mw);
      φ = Interpolator.findY(5.0, σCoeffsEpri.φ_m5, 6.0, σCoeffsEpri.φ_m6, Mw);
    } else if (Mw <= 7.0) {
      τ = Interpolator.findY(6.0, σCoeffsEpri.τ_m6, 7.0, σCoeffsEpri.τ_m7, Mw);
      φ = Interpolator.findY(6.0, σCoeffsEpri.φ_m6, 7.0, σCoeffsEpri.φ_m7, Mw);
    } else {
      τ = σCoeffsEpri.τ_m7;
      φ = σCoeffsEpri.φ_m7;
    }
    return Maths.hypot(τ, φ);
  }

  /* Updated EPRI phi only. */
  private static double phiEpri(CoefficientsSigmaEpri c, double Mw) {
    double φ;
    if (Mw <= 5.0) {
      φ = c.φ_m5;
    } else if (Mw <= 6.0) {
      φ = Interpolator.findY(5.0, c.φ_m5, 6.0, c.φ_m6, Mw);
    } else if (Mw <= 7.0) {
      φ = Interpolator.findY(6.0, c.φ_m6, 7.0, c.φ_m7, Mw);
    } else {
      φ = c.φ_m7;
    }
    return φ;
  }

  /*
   * Updated guidance from panel 8-16-18. TODO currently a lot of redundancy
   * because both the panel phiS2S and EPRI phi models do not have lo- mid- and
   * hi-branch implementations and are being called repeatedly.
   * 
   * At the (long) periods of interest, the panel recommends using the NGAW2 phi
   * models, which are effectively the same as the EPRI phi values at long
   * periods.
   */
  private static double sigmaHybrid(
      CoefficientsSigma c,
      CoefficientsSigmaEpri ec,
      double Mw,
      double vs30) {

    /* τ model; global branch only; Equation 5-1. */
    double τ = tau(Mw, c.τ1, c.τ2, c.τ3, c.τ4);

    /* φ_ss model; global, constant, and mag-dep. branches Equation 5.2. */
    double φ_ss = 0.8 * phi_ss(Mw, c.ga, c.gb) +
        0.1 * c.c +
        0.1 * phi_ss(Mw, c.ma, c.mb);

    /* φ_s2s model; single branch; Stewart et al. */
    double φ_s2s = phi_s2s(vs30, c.ϕs2s1, c.ϕs2s2);

    /* Model 1 phi, original */
    double φ1 = Maths.hypot(φ_ss, φ_s2s);

    /* Model 2 phi, EPRI. */
    double φ2 = phiEpri(ec, Mw);

    return Maths.hypot(τ, Math.max(φ1, φ2));
  }

  /* τ: Equation 5.1 */
  private static double tau(
      double Mw,
      double τ1,
      double τ2,
      double τ3,
      double τ4) {

    if (Mw <= 4.5) {
      return τ1;
    } else if (Mw <= 5.0) {
      return τ1 + (τ2 - τ1) * (Mw - 4.5) / 0.5;
    } else if (Mw <= 5.5) {
      return τ2 + (τ3 - τ2) * (Mw - 5.0) / 0.5;
    } else if (Mw <= 6.5) {
      return τ3 + (τ4 - τ3) * (Mw - 5.5);
    }
    return τ4;
  }

  /* φ_ss: Equation 5.2 */
  private static double phi_ss(
      double Mw,
      double a,
      double b) {

    if (Mw <= 5.0) {
      return a;
    } else if (Mw <= 6.5) {
      return a + (Mw - 5.0) * (b - a) / 1.5;
    }
    return b;
  }

  /* φ_s2s: Stewart et al. */
  private static double phi_s2s(
      double vs30,
      double ϕs2s1,
      double ϕs2s2) {

    return (vs30 < VΦ1)
        ? ϕs2s1
        : (vs30 < VΦ2)
            ? ϕs2s1 - ((ϕs2s1 - ϕs2s2) / (VΦ2 - VΦ1)) * (vs30 - VΦ1)
            : ϕs2s2;
  }

  static class SigmaSet {
    double[] sigmas;
    double[] weights;
  }

  /*
   * Base model used for sammons and seed model groups that share common site
   * class and sigma models.
   */
  static abstract class ModelGroup extends NgaEastUsgs_2017 {

    final double[] weights;
    final GroundMotionTable[] tables;
    final GroundMotionTable[] pgaTables;
    final SiteAmp siteAmp;

    /* Specifiy an array of models ids. */
    ModelGroup(
        Imt imt,
        double[] weights,
        GroundMotionTable[] tables,
        GroundMotionTable[] pgaTables) {

      super(imt);
      checkArgument(
          weights.length == tables.length && weights.length == pgaTables.length,
          "Weights and table arrays are different sizes");
      this.weights = weights;
      this.tables = tables;
      this.pgaTables = pgaTables;
      this.siteAmp = new SiteAmp(imt);
    }

    @Override
    public MultiScalarGroundMotion calc(GmmInput in) {
      Position p = tables[0].position(in.rRup, in.Mw);
      double[] μs = new double[weights.length];
      for (int i = 0; i < weights.length; i++) {
        double μ = tables[i].get(p);
        double μPga = exp(pgaTables[i].get(p));
        SiteAmp.Value fSite = siteAmp.calc(μPga, in.vs30);
        μs[i] = fSite.apply(μ);
      }
      SigmaSet σs = calcSigma(in);
      return new MultiScalarGroundMotion(μs, weights, σs.sigmas, σs.weights);
    }

    /* Default sigma */
    SigmaSet calcSigma(GmmInput in) {
      return sigmaSet(in.Mw, in.vs30);
    }
  }

  static class Usgs17 extends ModelGroup {
    static final String BASE_NAME = NgaEastUsgs_2017.NAME + ": 17 Branch";
    static final String NAME = BASE_NAME + ": σ-Panel";

    Usgs17(Imt imt) {
      super(
          imt,
          GroundMotionTables.getNgaEastV2Weights(imt),
          GroundMotionTables.getNgaEastV2(imt),
          GroundMotionTables.getNgaEastV2(Imt.PGA));
    }
  }

  static class Usgs17_Epri extends Usgs17 {
    static final String NAME = Usgs17.BASE_NAME + ": σ-EPRI";

    Usgs17_Epri(Imt imt) {
      super(imt);
    }

    @Override
    SigmaSet calcSigma(GmmInput in) {
      SigmaSet σSet = new SigmaSet();
      σSet.sigmas = new double[] { sigmaEpri(in.Mw) };
      σSet.weights = new double[] { 1.0 };
      return σSet;
    }
  }

  static class Usgs17_Hybrid extends Usgs17 {
    static final String NAME = Usgs17.BASE_NAME + ": σ-Hybrid ";

    Usgs17_Hybrid(Imt imt) {
      super(imt);
    }

    @Override
    SigmaSet calcSigma(GmmInput in) {
      return sigmaSetHybrid(in.Mw, in.vs30);
    }
  }

  @Deprecated
  static class Usgs13 extends ModelGroup {
    static final String NAME = NgaEastUsgs_2017.NAME + ": 13 Branch";

    Usgs13(Imt imt) {
      super(
          imt,
          GroundMotionTables.getNgaEastWeights(imt),
          GroundMotionTables.getNgaEast(imt),
          GroundMotionTables.getNgaEast(Imt.PGA));
    }
  }

  @Deprecated
  static class Usgs13_Epri extends Usgs13 {
    static final String NAME = Usgs13.NAME + ": EPRI";

    Usgs13_Epri(Imt imt) {
      super(imt);
    }

    @Override
    SigmaSet calcSigma(GmmInput in) {
      SigmaSet σSet = new SigmaSet();
      σSet.sigmas = new double[] { sigmaEpri(in.Mw) };
      σSet.weights = new double[] { 1.0 };
      return σSet;
    }
  }

  @Deprecated
  static class Usgs13_Envelope extends Usgs13 {
    static final String NAME = Usgs13.NAME + ": Envelope";

    Usgs13_Envelope(Imt imt) {
      super(imt);
    }

    @Override
    SigmaSet calcSigma(GmmInput in) {
      SigmaSet σSet = new SigmaSet();
      σSet.sigmas = new double[] {
          Math.max(
              sigmaCentral(in.Mw, in.vs30),
              sigmaEpri(in.Mw)) };
      σSet.weights = new double[] { 1.0 };
      return σSet;
    }
  }

  /*
   * Implementation of USGS Seed model logic tree. All models but SP16 are table
   * based; SP16 is added to the median ground motion array last. NOTE that the
   * model ignores the SP16 aleatory variability model for consistency with the
   * other seed models.
   */
  static class UsgsSeeds extends NgaEastUsgs_2017 {
    static final String NAME = NgaEastUsgs_2017.NAME + ": USGS Seed Tree";
    static final String SP16_ID = "SP16";

    /* ids for table based models only; skips SP16 */
    static final List<String> ids;
    /* includes SP16 as last entry */
    static final double[] weights;

    final GroundMotionTable[] tables;
    final GroundMotionTable[] pgaTables;
    final ShahjoueiPezeshk_2016 sp16;
    final SiteAmp siteAmp;

    static {
      ids = new ArrayList<>();
      List<Double> wtList = USGS_SEED_WEIGHTS.entrySet().stream()
          .filter(entry -> !entry.getKey().equals(SP16_ID))
          .peek(entry -> ids.add(entry.getKey()))
          .map(entry -> entry.getValue())
          .collect(Collectors.toList());
      weights = Doubles.toArray(ImmutableList.<Double> builder()
          .addAll(wtList)
          .add(USGS_SEED_WEIGHTS.get(SP16_ID))
          .build());
    }

    UsgsSeeds(Imt imt) {
      super(imt);
      this.tables = GroundMotionTables.getNgaEastSeeds(ids, imt);
      this.pgaTables = GroundMotionTables.getNgaEastSeeds(ids, Imt.PGA);
      this.sp16 = new ShahjoueiPezeshk_2016(imt);
      this.siteAmp = new SiteAmp(imt);
    }

    @Override
    public MultiScalarGroundMotion calc(GmmInput in) {
      Position p = tables[0].position(in.rRup, in.Mw);
      int seedCount = ids.size();
      double[] μs = new double[seedCount + 1];
      for (int i = 0; i < ids.size(); i++) {
        double μ = tables[i].get(p);
        double μPga = exp(pgaTables[i].get(p));
        SiteAmp.Value fSite = siteAmp.calc(μPga, in.vs30);
        μs[i] = fSite.apply(μ);
      }
      /* add SP16; already includes NGA-East site amp */
      μs[seedCount] = sp16.calc(in).mean();
      SigmaSet σs = calcSigma(in);
      return new MultiScalarGroundMotion(μs, weights, σs.sigmas, σs.weights);
    }

    /* Default sigma */
    SigmaSet calcSigma(GmmInput in) {
      return sigmaSet(in.Mw, in.vs30);
    }
  }

  static abstract class Sammons extends NgaEastUsgs_2017 {
    static final String NAME = NgaEastUsgs_2017.NAME + ": Sammons : ";
    static final String NAME0 = NAME + "0";

    final int id;
    final GroundMotionTable table;
    final GroundMotionTable pgaTable;
    final SiteAmp siteAmp;

    Sammons(int id, Imt imt) {
      super(imt);
      this.id = id;
      this.table = GroundMotionTables.getNgaEast(imt)[id - 1];
      this.pgaTable = GroundMotionTables.getNgaEast(Imt.PGA)[id - 1];
      this.siteAmp = new SiteAmp(imt);
    }

    @Override
    public ScalarGroundMotion calc(GmmInput in) {
      Position p = table.position(in.rRup, in.Mw);
      double μPga = exp(pgaTable.get(p));
      SiteAmp.Value fSite = siteAmp.calc(μPga, in.vs30);
      double μ = fSite.apply(table.get(p));
      double σ = sigmaCentral(in.Mw, in.vs30);
      return new DefaultScalarGroundMotion(μ, σ);
    }
  }

  static class Sammons_1 extends Sammons {
    static final int ID = 1;
    static final String NAME = Sammons.NAME0 + ID;

    Sammons_1(Imt imt) {
      super(ID, imt);
    }
  }

  static class Sammons_2 extends Sammons {
    static final int ID = 2;
    static final String NAME = Sammons.NAME0 + ID;

    Sammons_2(Imt imt) {
      super(ID, imt);
    }
  }

  static class Sammons_3 extends Sammons {
    static final int ID = 3;
    static final String NAME = Sammons.NAME0 + ID;

    Sammons_3(Imt imt) {
      super(ID, imt);
    }
  }

  static class Sammons_4 extends Sammons {
    static final int ID = 4;
    static final String NAME = Sammons.NAME0 + ID;

    Sammons_4(Imt imt) {
      super(ID, imt);
    }
  }

  static class Sammons_5 extends Sammons {
    static final int ID = 5;
    static final String NAME = Sammons.NAME0 + ID;

    Sammons_5(Imt imt) {
      super(ID, imt);
    }
  }

  static class Sammons_6 extends Sammons {
    static final int ID = 6;
    static final String NAME = Sammons.NAME0 + ID;

    Sammons_6(Imt imt) {
      super(ID, imt);
    }
  }

  static class Sammons_7 extends Sammons {
    static final int ID = 7;
    static final String NAME = Sammons.NAME0 + ID;

    Sammons_7(Imt imt) {
      super(ID, imt);
    }
  }

  static class Sammons_8 extends Sammons {
    static final int ID = 8;
    static final String NAME = Sammons.NAME0 + ID;

    Sammons_8(Imt imt) {
      super(ID, imt);
    }
  }

  static class Sammons_9 extends Sammons {
    static final int ID = 9;
    static final String NAME = Sammons.NAME0 + ID;

    Sammons_9(Imt imt) {
      super(ID, imt);
    }
  }

  static class Sammons_10 extends Sammons {
    static final int ID = 10;
    static final String NAME = Sammons.NAME + ID;

    Sammons_10(Imt imt) {
      super(ID, imt);
    }
  }

  static class Sammons_11 extends Sammons {
    static final int ID = 11;
    static final String NAME = Sammons.NAME + ID;

    Sammons_11(Imt imt) {
      super(ID, imt);
    }
  }

  static class Sammons_12 extends Sammons {
    static final int ID = 12;
    static final String NAME = Sammons.NAME + ID;

    Sammons_12(Imt imt) {
      super(ID, imt);
    }
  }

  static class Sammons_13 extends Sammons {
    static final int ID = 13;
    static final String NAME = Sammons.NAME + ID;

    Sammons_13(Imt imt) {
      super(ID, imt);
    }
  }

  static abstract class Sammons2 extends NgaEastUsgs_2017 {
    static final String NAME = NgaEastUsgs_2017.NAME + ": Sammons2 : ";
    static final String NAME0 = NAME + "0";

    final int id;
    final GroundMotionTable table;
    final GroundMotionTable pgaTable;
    final SiteAmp siteAmp;

    Sammons2(int id, Imt imt) {
      super(imt);
      this.id = id;
      this.table = GroundMotionTables.getNgaEastV2(imt)[id - 1];
      this.pgaTable = GroundMotionTables.getNgaEastV2(Imt.PGA)[id - 1];
      this.siteAmp = new SiteAmp(imt);
    }

    @Override
    public ScalarGroundMotion calc(GmmInput in) {
      Position p = table.position(in.rRup, in.Mw);
      double μPga = exp(pgaTable.get(p));
      SiteAmp.Value fSite = siteAmp.calc(μPga, in.vs30);
      double μ = fSite.apply(table.get(p));
      double σ = sigmaCentral(in.Mw, in.vs30);
      return new DefaultScalarGroundMotion(μ, σ);
    }
  }

  static class Sammons2_1 extends Sammons2 {
    static final int ID = 1;
    static final String NAME = Sammons2.NAME0 + ID;

    Sammons2_1(Imt imt) {
      super(ID, imt);
    }
  }

  static class Sammons2_2 extends Sammons2 {
    static final int ID = 2;
    static final String NAME = Sammons2.NAME0 + ID;

    Sammons2_2(Imt imt) {
      super(ID, imt);
    }
  }

  static class Sammons2_3 extends Sammons2 {
    static final int ID = 3;
    static final String NAME = Sammons2.NAME0 + ID;

    Sammons2_3(Imt imt) {
      super(ID, imt);
    }
  }

  static class Sammons2_4 extends Sammons2 {
    static final int ID = 4;
    static final String NAME = Sammons2.NAME0 + ID;

    Sammons2_4(Imt imt) {
      super(ID, imt);
    }
  }

  static class Sammons2_5 extends Sammons2 {
    static final int ID = 5;
    static final String NAME = Sammons2.NAME0 + ID;

    Sammons2_5(Imt imt) {
      super(ID, imt);
    }
  }

  static class Sammons2_6 extends Sammons2 {
    static final int ID = 6;
    static final String NAME = Sammons2.NAME0 + ID;

    Sammons2_6(Imt imt) {
      super(ID, imt);
    }
  }

  static class Sammons2_7 extends Sammons2 {
    static final int ID = 7;
    static final String NAME = Sammons2.NAME0 + ID;

    Sammons2_7(Imt imt) {
      super(ID, imt);
    }
  }

  static class Sammons2_8 extends Sammons2 {
    static final int ID = 8;
    static final String NAME = Sammons2.NAME0 + ID;

    Sammons2_8(Imt imt) {
      super(ID, imt);
    }
  }

  static class Sammons2_9 extends Sammons2 {
    static final int ID = 9;
    static final String NAME = Sammons2.NAME0 + ID;

    Sammons2_9(Imt imt) {
      super(ID, imt);
    }
  }

  static class Sammons2_10 extends Sammons2 {
    static final int ID = 10;
    static final String NAME = Sammons2.NAME + ID;

    Sammons2_10(Imt imt) {
      super(ID, imt);
    }
  }

  static class Sammons2_11 extends Sammons2 {
    static final int ID = 11;
    static final String NAME = Sammons2.NAME + ID;

    Sammons2_11(Imt imt) {
      super(ID, imt);
    }
  }

  static class Sammons2_12 extends Sammons2 {
    static final int ID = 12;
    static final String NAME = Sammons2.NAME + ID;

    Sammons2_12(Imt imt) {
      super(ID, imt);
    }
  }

  static class Sammons2_13 extends Sammons2 {
    static final int ID = 13;
    static final String NAME = Sammons2.NAME + ID;

    Sammons2_13(Imt imt) {
      super(ID, imt);
    }
  }

  static class Sammons2_14 extends Sammons2 {
    static final int ID = 14;
    static final String NAME = Sammons2.NAME + ID;

    Sammons2_14(Imt imt) {
      super(ID, imt);
    }
  }

  static class Sammons2_15 extends Sammons2 {
    static final int ID = 15;
    static final String NAME = Sammons2.NAME + ID;

    Sammons2_15(Imt imt) {
      super(ID, imt);
    }
  }

  static class Sammons2_16 extends Sammons2 {
    static final int ID = 16;
    static final String NAME = Sammons2.NAME + ID;

    Sammons2_16(Imt imt) {
      super(ID, imt);
    }
  }

  static class Sammons2_17 extends Sammons2 {
    static final int ID = 17;
    static final String NAME = Sammons2.NAME + ID;

    Sammons2_17(Imt imt) {
      super(ID, imt);
    }
  }

  static abstract class Seed extends NgaEastUsgs_2017 {
    static final String NAME = NgaEastUsgs_2017.NAME + ": Seed : ";

    final String id;
    final GroundMotionTable table;
    final GroundMotionTable pgaTable;
    final SiteAmp siteAmp;

    Seed(String id, Imt imt) {
      super(imt);
      this.id = id;
      this.table = GroundMotionTables.getNgaEastSeed(id, imt);
      this.pgaTable = GroundMotionTables.getNgaEastSeed(id, Imt.PGA);
      this.siteAmp = new SiteAmp(imt);
    }

    @Override
    public ScalarGroundMotion calc(GmmInput in) {
      Position p = table.position(in.rRup, in.Mw);
      double μPga = exp(pgaTable.get(p));
      SiteAmp.Value fSite = siteAmp.calc(μPga, in.vs30);
      double μ = fSite.apply(table.get(p));

      // TODO clean
      // double muTmp = exp(table.get(p));
      // double μLin = exp(μ);
      // double ampScale = μLin / muTmp;
      // System.out.println(String.format(
      // "%10s, %.3f, %.3f, %.3f",
      // siteAmp.c.imt.name(), muTmp, μLin, ampScale));

      double σ = sigmaCentral(in.Mw, in.vs30);
      return new DefaultScalarGroundMotion(μ, σ);
    }
  }

  static final class Seed_1CCSP extends Seed {
    static final String ID = "1CCSP";
    static final String NAME = Seed.NAME + ID;

    Seed_1CCSP(Imt imt) {
      super(ID, imt);
    }
  }

  static final class Seed_1CVSP extends Seed {
    static final String ID = "1CVSP";
    static final String NAME = Seed.NAME + ID;

    Seed_1CVSP(Imt imt) {
      super(ID, imt);
    }
  }

  static final class Seed_2CCSP extends Seed {
    static final String ID = "2CCSP";
    static final String NAME = Seed.NAME + ID;

    Seed_2CCSP(Imt imt) {
      super(ID, imt);
    }
  }

  static final class Seed_2CVSP extends Seed {
    static final String ID = "2CVSP";
    static final String NAME = Seed.NAME + ID;

    Seed_2CVSP(Imt imt) {
      super(ID, imt);
    }
  }

  static final class Seed_ANC15 extends Seed {
    static final String ID = "ANC15";
    static final String NAME = Seed.NAME + ID;

    Seed_ANC15(Imt imt) {
      super(ID, imt);
    }
  }

  static final class Seed_B_a04 extends Seed {
    static final String ID = "B_a04";
    static final String NAME = Seed.NAME + ID;

    Seed_B_a04(Imt imt) {
      super(ID, imt);
    }
  }

  static final class Seed_B_ab14 extends Seed {
    static final String ID = "B_ab14";
    static final String NAME = Seed.NAME + ID;

    Seed_B_ab14(Imt imt) {
      super(ID, imt);
    }
  }

  static final class Seed_B_ab95 extends Seed {
    static final String ID = "B_ab95";
    static final String NAME = Seed.NAME + ID;

    Seed_B_ab95(Imt imt) {
      super(ID, imt);
    }
  }

  static final class Seed_B_bca10d extends Seed {
    static final String ID = "B_bca10d";
    static final String NAME = Seed.NAME + ID;

    Seed_B_bca10d(Imt imt) {
      super(ID, imt);
    }
  }

  static final class Seed_B_bs11 extends Seed {
    static final String ID = "B_bs11";
    static final String NAME = Seed.NAME + ID;

    Seed_B_bs11(Imt imt) {
      super(ID, imt);
    }
  }

  static final class Seed_B_sgd02 extends Seed {
    static final String ID = "B_sgd02";
    static final String NAME = Seed.NAME + ID;

    Seed_B_sgd02(Imt imt) {
      super(ID, imt);
    }
  }

  static final class Seed_Frankel extends Seed {
    static final String ID = "Frankel";
    static final String NAME = Seed.NAME + ID;

    Seed_Frankel(Imt imt) {
      super(ID, imt);
    }
  }

  static final class Seed_Graizer extends Seed {
    static final String ID = "Graizer";
    static final String NAME = Seed.NAME + ID;

    Seed_Graizer(Imt imt) {
      super(ID, imt);
    }
  }

  static final class Seed_HA15 extends Seed {
    static final String ID = "HA15";
    static final String NAME = Seed.NAME + ID;

    Seed_HA15(Imt imt) {
      super(ID, imt);
    }
  }

  static final class Seed_PEER_EX extends Seed {
    static final String ID = "PEER_EX";
    static final String NAME = Seed.NAME + ID;

    Seed_PEER_EX(Imt imt) {
      super(ID, imt);
    }
  }

  static final class Seed_PEER_GP extends Seed {
    static final String ID = "PEER_GP";
    static final String NAME = Seed.NAME + ID;

    Seed_PEER_GP(Imt imt) {
      super(ID, imt);
    }
  }

  static final class Seed_PZCT15_M1SS extends Seed {
    static final String ID = "PZCT15_M1SS";
    static final String NAME = Seed.NAME + ID;

    Seed_PZCT15_M1SS(Imt imt) {
      super(ID, imt);
    }
  }

  static final class Seed_PZCT15_M2ES extends Seed {
    static final String ID = "PZCT15_M2ES";
    static final String NAME = Seed.NAME + ID;

    Seed_PZCT15_M2ES(Imt imt) {
      super(ID, imt);
    }
  }

  static final class Seed_SP15 extends Seed {
    static final String ID = "SP15";
    static final String NAME = Seed.NAME + ID;

    Seed_SP15(Imt imt) {
      super(ID, imt);
    }
  }

  static final class Seed_YA15 extends Seed {
    static final String ID = "YA15";
    static final String NAME = Seed.NAME + ID;

    Seed_YA15(Imt imt) {
      super(ID, imt);
    }
  }

  /*
   * Updated Graizer models.
   */

  static final class SeedUpdate_Graizer16 extends Seed {
    static final String ID = "Graizer16";
    static final String NAME = Seed.NAME + ID + " (updated)";

    SeedUpdate_Graizer16(Imt imt) {
      super(ID, imt);
    }
  }

  static final class SeedUpdate_Graizer17 extends Seed {
    static final String ID = "Graizer17";
    static final String NAME = Seed.NAME + ID + " (updated)";

    SeedUpdate_Graizer17(Imt imt) {
      super(ID, imt);
    }
  }

  /**
   * Stewart et al. site amplification model.
   * 
   * The model is applicable to 200 ≤ vs30 ≤ 2000 m/s. In the current
   * implementation, for vs30 < 200, vs30 = 200 m/s; for vs30 > 2000, vs30 =
   * 3000 m/s.
   */
  static final class SiteAmp {

    static final String NAME = NgaEastUsgs_2017.NAME + ": Site Amplification";

    private static final CoefficientContainer COEFFS = new CoefficientContainer(
        "nga-east-usgs-siteamp.csv");

    private static final double V_MIN = 200.0;
    private static final double V_MAX = 3000.0;
    private static final double V_LIN_REF = 760.0;
    private static final double VL = 200.0;
    private static final double VU = 2000.0;

    private static final double VW1 = 600.0;
    private static final double VW2 = 400.0;
    private static final double WT_I = 0.9;
    private static final double WT_G = 1.0 - WT_I;
    private static final double WT_SCALE = (WT_I - WT_G) / (log(VW1) - log(VW2)); // ≈1.97

    private final Coefficients c;

    private static final class Coefficients {

      final Imt imt;
      final double c, v1, v2, vf, σvc, σl, σu, f760i, f760g, f760iσ, f760gσ, f3, f4, f5, vc, σc;

      Coefficients(Imt imt, CoefficientContainer cc) {
        this.imt = imt;
        Map<String, Double> coeffs = cc.get(imt);
        c = coeffs.get("c");
        v1 = coeffs.get("V1");
        v2 = coeffs.get("V2");
        vf = coeffs.get("Vf");
        σvc = coeffs.get("sig_vc");
        σl = coeffs.get("sig_l");
        σu = coeffs.get("sig_u");
        f760i = coeffs.get("f760i");
        f760iσ = coeffs.get("f760is");
        f760g = coeffs.get("f760g");
        f760gσ = coeffs.get("f760gs");
        f3 = coeffs.get("f3");
        f4 = coeffs.get("f4");
        f5 = coeffs.get("f5");
        vc = coeffs.get("Vc");
        σc = coeffs.get("sig_c");
      }
    }

    SiteAmp(Imt imt) {
      c = new Coefficients(imt, COEFFS);
    }

    SiteAmp.Value calc(double pgaRock, double vs30) {

      /*
       * Developer notes:
       * 
       * Vs30 values outside the range 200 < Vs30 < 3000 m/s are clamped to the
       * supported range.
       * 
       * ---------------------------------------------------------------------
       * 
       * Comments from R implementation:
       * 
       * This function is used to compute site amplification in ln units for
       * inputs Vs30 (in m/s) and PGAr (peak acceleration for 3000 m/s reference
       * rock in g). Outputs:
       * 
       * 1) Linear and nonlinear site amplification (FLin, FNonlin)
       * 
       * 2) Standard deviation (σLin, σNonlin)
       * 
       * 3) Total site amplification (fT) and total standard deviation (σT)
       * 
       * Notes:
       * 
       * 1) We assume fv and f760 are independent, so variance of linear site
       * amplication is the sum of variance f760 and f760;
       *
       * 2) The third value of σc coefficient, which is used to compute
       * nonlinear site amplication. 0.12 in report, and 0.1 in excel. Here we
       * use 0.12.
       * 
       * 3) Standard deviation of fv calculation is updated by adding a new
       * coefficient vf instead of using v1.
       */

      /* Vs30 filtering */ // TODO update comments to 3000

      if (vs30 >= V_MAX) {
        return new Value(0.0, 0.0);
      } else if (vs30 < V_MIN) {
        vs30 = V_MIN;
      }

      /* Linear response */

      /* Vs30 dependent f760 model: impedance vs.gradient. */
      double wti = WT_I;
      if (vs30 < VW2) {
        wti = WT_G;
      } else if (vs30 < VW1) {
        wti = WT_SCALE * log(vs30 / VW2) + WT_G;
      }
      double wtg = 1.0 - wti;
      double f760 = c.f760i * wti + c.f760g * wtg;
      double f760σ = c.f760iσ * wti + c.f760gσ * wtg;

      double fv = 0.0;
      if (vs30 <= c.v1) {
        fv = c.c * log(c.v1 / V_LIN_REF);
      } else if (vs30 <= c.v2) {
        fv = c.c * log(vs30 / V_LIN_REF);
      } else if (vs30 <= VU) {
        fv = c.c * log(c.v2 / V_LIN_REF);
      } else {
        double f2000 = c.c * log(c.v2 / V_LIN_REF);
        fv = Interpolator.findY(log(VU), f2000, log(V_MAX), -f760, log(vs30));
      }

      double fvσ = 0.0;
      if (vs30 < c.vf) {
        double σT = c.σl - c.σvc;
        double vT = (vs30 - VL) / (c.vf - VL);
        fvσ = c.σl - 2.0 * σT * vT + σT * vT * vT;
      } else if (vs30 <= c.v2) {
        fvσ = c.σvc;
      } else {
        double vT = (vs30 - c.v2) / (VU - c.v2);
        fvσ = c.σvc + (c.σu - c.σvc) * vT * vT;
      }

      double fLin = fv + f760;
      double σLin = sqrt(fvσ * fvσ + f760σ * f760σ);

      /* Nonlinear response */

      double vRefNl = (c.imt.ordinal() >= Imt.SA0P4.ordinal()) ? V_MAX : V_LIN_REF;
      double rkRefTerm = log((pgaRock + c.f3) / c.f3);

      double fNonlin = 0.0;
      if (vs30 < c.vc) {
        double f2 = c.f4 * (exp(c.f5 * (min(vs30, vRefNl) - 360.0)) -
            exp(c.f5 * (vRefNl - 360.0)));
        fNonlin = f2 * rkRefTerm;
      }

      double σf2 = 0.0;
      if (vs30 < 300.0) {
        σf2 = c.σc;
      } else if (vs30 < 1000.0) {
        σf2 = c.σc - c.σc / log(1000.0 / 300.0) * log(vs30 / 300.0);
      }
      double σNonlin = σf2 * rkRefTerm;

      double fT = fLin + fNonlin;
      double σT = sqrt(σLin * σLin + σNonlin * σNonlin);

      // // TODO clean
      // String values = String.format(
      // "%12s %5.3f %.6g %.7g %.7g %.7g %.7g %.7g %.7g %.7g %.7g %.7g",
      // c.imt.name(),
      // c.imt.isSA() ? c.imt.period() : 0.0,
      // pgaRock,
      // fv,
      // f760,
      // fvσ,
      // fLin,
      // σLin,
      // fNonlin,
      // σNonlin,
      // fT,
      // σT);

      // TODO clean
      // String values = String.format(
      // "%5.3f %.7g %.7g %.7g %.7g %.7g %.7g %.7g %.7g",
      // c.imt.isSA() ? c.imt.period() : 0.0,
      // fv,
      // c.f760g,
      // c.f760i,
      // f760,
      // fLin,
      // fNonlin,
      // fT,
      // σT);

      // System.out.println(values);

      return new Value(fT, σT);
    }

    /**
     * Wrapper class for site amplification and associated epistemic
     * uncertainty.
     */
    static final class Value {

      final double siteAmp;
      final double σ;

      Value(double siteAmp, double σ) {
        this.siteAmp = siteAmp;
        this.σ = σ;
      }

      double apply(double μ) {
        double μAmp = μ + siteAmp;
        return log(
            SITE_AMP_WTS[0] * exp(μAmp + σ) +
                SITE_AMP_WTS[1] * exp(μAmp) +
                SITE_AMP_WTS[2] * exp(μAmp - σ));
      }
    }
  }

}
