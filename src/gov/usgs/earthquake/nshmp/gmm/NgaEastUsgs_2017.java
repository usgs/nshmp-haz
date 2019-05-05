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

import gov.usgs.earthquake.nshmp.data.Data;
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
 * <p>Calculation of hazard using this implementation deviates somewhat from the
 * current nshmp-haz PSHA pipeline and required implementation of a
 * {@code MultiScalarGroundMotion}. A {@code MultiScalarGroundMotion} stores
 * arrays of means and sigmas with associated weights.
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
 * <p><b>Reference:</b> Stewart, J., Parker, G., Atkinson, G., Boore, D., and
 * Hashash, Y., and Silva, W., 2019, Ergodic site amplification model for
 * central and eastern North America: Earthquake Spectra (in review)
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
   * Developer notes:
   * 
   * Note: supported periods are derived from sigma coefficient files. Several
   * supported periods have been commented out because they are not represented
   * in the site amplification model.
   * 
   * When supplied with tables for the 17 usgs models, 0.01s is present with
   * values distinct from PGA, however the seed models are missing this period.
   * We therefore duplicate PGA for 0.01s ground motion values in the seed model
   * tables. Because a coefficient table (sigma coeffs in this case) is
   * referenced for supported IMTs, the ground motion tables across seed and
   * sammons models must be consistent.
   * 
   * Missing PGV tables: Grazier, PEER_EX, PEER_GP, PZCT15_M1SS, PZCT15_M2ES
   * 
   * Notes on sigma:
   * 
   * NGA-East for USGS (2017) recommended use of an updated EPRI (2013) model
   * (Table 5.5), which is currently considered as one independent branch of an
   * aleatory variability logic tree.
   * 
   * The NSHMP asked PEER panel to develop an improved φ_s2s model. This
   * necessitated including τ and φ_ss as independent terms. Only the 'global' τ
   * and φ_ss models are considered, per Linda Al Atik's recommendation. Because
   * no statistical uncertianty model was developed for φ_s2s, we only consider
   * the 'central' branch for τ and φ_ss terms. Sensitivity studies show that
   * consideration of only the 'global' and 'central' branches of τ and φ_ss has
   * little effect on the final σ. This 'panel' model is the second branch in
   * the aleatory variability logic tree.
   */

  static final String NAME = "NGA-East USGS (2017)";

  static final Constraints CONSTRAINTS = Constraints.builder()
      .set(MW, Range.closed(4.0, 8.2))
      .set(RJB, Range.closed(0.0, 1500.0))
      .set(VS30, Range.closed(200.0, 3000.0))
      .build();

  /*
   * Sigma coefficients for global model from tables 5-1 (tau) and 5-2 (phi) and
   * EPRI model from table 5-5.
   */
  static CoefficientContainer COEFFS_SIGMA_MID;
  static CoefficientContainer COEFFS_SIGMA_EPRI;

  /* Immutable, ordered map of seed model weights. */
  static Map<String, Double> USGS_SEED_WEIGHTS;

  static {
    COEFFS_SIGMA_MID = new CoefficientContainer("nga-east-usgs-sigma-mid.csv");
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

  /* Logic tree between panel model and EPRI */
  private static final double[] SIGMA_LTC_WTS = { 0.2, 0.8 };

  /* φ_s2s constants */
  private static final double VΦ1 = 1200.0;
  private static final double VΦ2 = 1500.0;

  private static final class CoefficientsSigma {

    /* τ coefficients */
    final double τ1, τ2, τ3, τ4;

    /* φ_ss coefficients; g=global, m=mag-dep, c=constant */
    final double ga, gb, ma, mb, c;

    /* φ_s2s coefficients */
    final double φs2s1, φs2s2;

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
      φs2s1 = coeffs.get("s2s1");
      φs2s2 = coeffs.get("s2s2");
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

  private final CoefficientsSigma σCoeffsMid;
  private final CoefficientsSigmaEpri σCoeffsEpri;

  NgaEastUsgs_2017(final Imt imt) {
    σCoeffsMid = new CoefficientsSigma(imt, COEFFS_SIGMA_MID);
    σCoeffsEpri = new CoefficientsSigmaEpri(imt, COEFFS_SIGMA_EPRI);
  }

  /* Recommendation 1: Updated EPRI model. */
  SigmaSet sigmaSetEpri(double Mw) {
    SigmaSet σSet = new SigmaSet();
    σSet.sigmas = new double[] { sigmaEpri(σCoeffsEpri, Mw) };
    σSet.weights = new double[] { 1.0 };
    return σSet;
  }

  /* Recommendation 2: Panel model (φ_S2S), no branches. */
  SigmaSet sigmaSetPanel(double Mw, double vs30) {
    SigmaSet σSet = new SigmaSet();
    σSet.sigmas = new double[] { sigmaPanel(σCoeffsMid, Mw, vs30) };
    σSet.weights = new double[] { 1.0 };
    return σSet;
  }

  /* Final USGS logic-tree model: Panel=0.2, EPRIu=0.8 */
  SigmaSet sigmaSetLogicTree(double Mw, double vs30) {
    SigmaSet σSet = new SigmaSet();
    σSet.sigmas = new double[] {
        sigmaPanel(σCoeffsMid, Mw, vs30),
        sigmaEpri(σCoeffsEpri, Mw)
    };
    σSet.weights = SIGMA_LTC_WTS;
    return σSet;
  }

  /* USGS model: Final, no nested branching, collapsed. */
  double sigmaLogicTree(double Mw, double vs30) {
    SigmaSet ss = sigmaSetLogicTree(Mw, vs30);
    return Data.sum(Data.multiply(ss.sigmas, ss.weights));
  }

  /*
   * Recommendation 1: Updated EPRI (2013); initial NGA-East team recomendation.
   */
  private static double sigmaEpri(CoefficientsSigmaEpri c, double Mw) {
    double[] phiTau = sigmaEpriTerms(c, Mw);
    return Maths.hypot(phiTau[0], phiTau[1]);
  }

  /* Updated EPRI (2013) φ and τ. Method returns a double[] of {φ,τ} */
  private static double[] sigmaEpriTerms(CoefficientsSigmaEpri c, double Mw) {
    double τ;
    double φ;
    if (Mw <= 5.0) {
      τ = c.τ_m5;
      φ = c.φ_m5;
    } else if (Mw <= 6.0) {
      τ = Interpolator.findY(5.0, c.τ_m5, 6.0, c.τ_m6, Mw);
      φ = Interpolator.findY(5.0, c.φ_m5, 6.0, c.φ_m6, Mw);
    } else if (Mw <= 7.0) {
      τ = Interpolator.findY(6.0, c.τ_m6, 7.0, c.τ_m7, Mw);
      φ = Interpolator.findY(6.0, c.φ_m6, 7.0, c.φ_m7, Mw);
    } else {
      τ = c.τ_m7;
      φ = c.φ_m7;
    }
    return new double[] { φ, τ };
  }

  /* Recommendation 2: Same as above, no c branching. */
  private static double sigmaPanel(
      CoefficientsSigma c,
      double Mw,
      double vs30) {

    /* τ model; global branch only; Equation 5-1 */
    double τ = tau(Mw, c.τ1, c.τ2, c.τ3, c.τ4);

    /* φ_ss model; global branch only; Equation 5-2 */
    double φ_ss = phi_ss(Mw, c.ga, c.gb);

    /* φ_s2s model; single branch; Stewart et al. */
    double φ_s2s = phi_s2s(vs30, c.φs2s1, c.φs2s2);

    return Maths.hypot(τ, φ_ss, φ_s2s);
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
      double φs2s1,
      double φs2s2) {

    if (vs30 < VΦ1) {
      return φs2s1;
    } else if (vs30 < VΦ2) {
      return φs2s1 - ((φs2s1 - φs2s2) / (VΦ2 - VΦ1)) * (vs30 - VΦ1);
    }
    return φs2s2;
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
        GroundMotionTable[] pgaTables,
        SiteAmp.Model model) {

      super(imt);
      checkArgument(
          weights.length == tables.length && weights.length == pgaTables.length,
          "Weights and table arrays are different sizes");
      this.weights = weights;
      this.tables = tables;
      this.pgaTables = pgaTables;
      this.siteAmp = new SiteAmp(imt, model);
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
      return sigmaSetLogicTree(in.Mw, in.vs30);
    }
  }

  static class Usgs17 extends ModelGroup {
    static final String BASE_NAME = NgaEastUsgs_2017.NAME;
    static final String NAME = BASE_NAME + " : σ-LogicTree";

    Usgs17(Imt imt) {
      this(imt, SiteAmp.Model.LOGIC_TREE);
    }

    Usgs17(Imt imt, SiteAmp.Model model) {
      super(
          imt,
          GroundMotionTables.getNgaEastWeights(imt),
          GroundMotionTables.getNgaEast(imt),
          GroundMotionTables.getNgaEast(Imt.PGA),
          model);
    }
  }

  static class Usgs17_SigmaEpri extends Usgs17 {
    static final String NAME = Usgs17.BASE_NAME + " : σ-EPRI";

    Usgs17_SigmaEpri(Imt imt) {
      super(imt);
    }

    @Override
    SigmaSet calcSigma(GmmInput in) {
      return sigmaSetEpri(in.Mw);
    }
  }

  static class Usgs17_SigmaPanel extends Usgs17 {
    static final String NAME = Usgs17.BASE_NAME + " : σ-Panel";

    Usgs17_SigmaPanel(Imt imt) {
      super(imt);
    }

    @Override
    SigmaSet calcSigma(GmmInput in) {
      return sigmaSetPanel(in.Mw, in.vs30);
    }
  }

  static class Usgs17_SiteImpedance extends Usgs17 {
    static final String NAME = Usgs17.BASE_NAME + " : site-impedance";

    Usgs17_SiteImpedance(Imt imt) {
      super(imt, SiteAmp.Model.IMPEDANCE_ONLY);
    }
  }

  static class Usgs17_SiteGradient extends Usgs17 {
    static final String NAME = Usgs17.BASE_NAME + " : site-gradient";

    Usgs17_SiteGradient(Imt imt) {
      super(imt, SiteAmp.Model.GRADIENT_ONLY);
    }
  }

  /*
   * Implementation of USGS Seed model logic tree. All models but SP16 are table
   * based; SP16 is added to the median ground motion array last. NOTE that the
   * model ignores the SP16 aleatory variability model for consistency with the
   * other seed models.
   */
  static class UsgsSeeds extends NgaEastUsgs_2017 {
    static final String BASE_NAME = NgaEastUsgs_2017.NAME + " : Seed Tree";
    static final String NAME = BASE_NAME + " : σ-LogicTree";
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
      this(imt, SiteAmp.Model.LOGIC_TREE);
    }

    UsgsSeeds(Imt imt, SiteAmp.Model model) {
      super(imt);
      this.tables = GroundMotionTables.getNgaEastSeeds(ids, imt);
      this.pgaTables = GroundMotionTables.getNgaEastSeeds(ids, Imt.PGA);
      this.sp16 = new ShahjoueiPezeshk_2016(imt);
      this.siteAmp = new SiteAmp(imt, model);
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
      return sigmaSetLogicTree(in.Mw, in.vs30);
    }
  }

  static class UsgsSeedsEpri extends UsgsSeeds {

    static final String NAME = UsgsSeeds.BASE_NAME + " : σ-EPRI";

    UsgsSeedsEpri(Imt imt) {
      super(imt);
    }

    @Override
    SigmaSet calcSigma(GmmInput in) {
      return sigmaSetEpri(in.Mw);
    }
  }

  static class UsgsSeedsPanel extends UsgsSeeds {

    static final String NAME = UsgsSeeds.BASE_NAME + " : σ-Panel";

    UsgsSeedsPanel(Imt imt) {
      super(imt);
    }

    @Override
    SigmaSet calcSigma(GmmInput in) {
      return sigmaSetPanel(in.Mw, in.vs30);
    }
  }

  static class UsgsSeedsSiteImpedance extends UsgsSeeds {

    static final String NAME = UsgsSeeds.BASE_NAME + " : site-impedance";

    UsgsSeedsSiteImpedance(Imt imt) {
      super(imt, SiteAmp.Model.IMPEDANCE_ONLY);
    }
  }

  static class UsgsSeedsSiteGradient extends UsgsSeeds {

    static final String NAME = UsgsSeeds.BASE_NAME + " : site-gradient";

    UsgsSeedsSiteGradient(Imt imt) {
      super(imt, SiteAmp.Model.GRADIENT_ONLY);
    }
  }

  static abstract class Sammons extends NgaEastUsgs_2017 {
    static final String NAME = NgaEastUsgs_2017.NAME + " : Sammons : ";
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
      double σ = sigmaLogicTree(in.Mw, in.vs30);
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

  static class Sammons_14 extends Sammons {
    static final int ID = 14;
    static final String NAME = Sammons.NAME + ID;

    Sammons_14(Imt imt) {
      super(ID, imt);
    }
  }

  static class Sammons_15 extends Sammons {
    static final int ID = 15;
    static final String NAME = Sammons.NAME + ID;

    Sammons_15(Imt imt) {
      super(ID, imt);
    }
  }

  static class Sammons_16 extends Sammons {
    static final int ID = 16;
    static final String NAME = Sammons.NAME + ID;

    Sammons_16(Imt imt) {
      super(ID, imt);
    }
  }

  static class Sammons_17 extends Sammons {
    static final int ID = 17;
    static final String NAME = Sammons.NAME + ID;

    Sammons_17(Imt imt) {
      super(ID, imt);
    }
  }

  static abstract class Seed extends NgaEastUsgs_2017 {
    static final String NAME = NgaEastUsgs_2017.NAME + " : Seed : ";

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

      double σ = sigmaLogicTree(in.Mw, in.vs30);
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

    static final String NAME = NgaEastUsgs_2017.NAME + " : Site Amplification";

    private static final CoefficientContainer COEFFS = new CoefficientContainer(
        "nga-east-usgs-siteamp.csv");

    private static final double V_MIN = 150.0;
    private static final double V_MAX = 3000.0;
    private static final double V_LIN_REF = 760.0;
    private static final double VL = 200.0;
    private static final double VU = 2000.0;

    private static final double VW1 = 600.0;
    private static final double VW2 = 400.0;
    private static final double WT1 = 0.767; // impedance model @ VW1
    private static final double WT2 = 0.1; // impedance model @ VW2
    private static final double WT_SCALE = (WT1 - WT2) / (log(VW1) - log(VW2)); // ≈1.65

    private final Coefficients c;

    private final Model model;

    private static final class Coefficients {

      final Imt imt;
      final double c, v1, v2, vf, σvc, σl, σu;
      final double f760i, f760g, f760iσ, f760gσ;
      final double f3, f4, f5, vc, σc;

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
      this(imt, Model.LOGIC_TREE);
    }

    SiteAmp(Imt imt, Model model) {
      c = new Coefficients(imt, COEFFS);
      this.model = model;
    }

    static enum Model {
      GRADIENT_ONLY,
      IMPEDANCE_ONLY,
      LOGIC_TREE;
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

      /*
       * Vs30 dependent f760 model: impedance vs. gradient
       * 
       * The final model gives 2/3 weight to a weight scaling model of
       * [0.9i,0.1g] to [0.1i,0.9g] and 1/3 weight to a weight scaling model of
       * [0.5i,0.5g] to [0.1i,0.9g]. This results in a final weight scaling
       * model of [0.767i,0.233g] at 600 m/s and [0.1i,0.9g] at 400 m/s.
       */
      double wti = WT1; // impedance = 0.767 @ Vs30 > 600
      if (vs30 < VW2) {
        wti = WT2; // impedance = 0.1 @ Vs30 < 400
      } else if (vs30 < VW1) {
        wti = WT_SCALE * log(vs30 / VW2) + WT2;
      }
      double wtg = 1.0 - wti;

      // TODO clean - sensitivity testing
      if (this.model == Model.GRADIENT_ONLY) {
        wti = 0.0;
        wtg = 1.0;
      } else if (this.model == Model.IMPEDANCE_ONLY) {
        wti = 1.0;
        wtg = 0.0;
      }

      double f760 = c.f760i * wti + c.f760g * wtg;
      double f760σ = c.f760iσ * wti + c.f760gσ * wtg;

      /* Vs30 dependent f760 model: impedance vs.gradient. */
      // TODO clean
      //
      // renamed class fields
      // private static final double WT_I = 0.9;
      // private static final double WT_G = 1.0 - WT_I;
      // private static final double WT_SCALE = (WT_I - WT_G) / (log(VW1) -
      // log(VW2)); // ≈1.97
      //
      // implementation
      // double wti = WT_I;
      // if (vs30 < VW2) {
      // wti = WT_G;
      // } else if (vs30 < VW1) {
      // wti = WT_SCALE * log(vs30 / VW2) + WT_G;
      // }
      // double wtg = 1.0 - wti;
      // double f760 = c.f760i * wti + c.f760g * wtg;
      // double f760σ = c.f760iσ * wti + c.f760gσ * wtg;

      double fv = 0.0;
      if (vs30 <= c.v1) {
        fv = c.c * log(c.v1 / V_LIN_REF);
      } else if (vs30 <= c.v2) {
        fv = c.c * log(vs30 / V_LIN_REF);
      } else if (vs30 <= VU) {
        fv = c.c * log(c.v2 / V_LIN_REF);
      } else {
        /* Equivalent to equation 3 for 2000 < vs30 < 3000 */
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
      } else if (vs30 <= VU) {
        double vT = (vs30 - c.v2) / (VU - c.v2);
        fvσ = c.σvc + (c.σu - c.σvc) * vT * vT;
      } else {
        fvσ = c.σu * (1.0 - log(vs30 / VU) / log(V_MAX / VU));
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
      // "%12s\t%5.3f\t%.6g\t%.7g\t%.7g\t%.7g\t%.7g\t%.7g\t%.7g\t%.7g\t%.7g\t%.7g",
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

      // // TODO clean
      // String values = String.format(
      // "%5.3f\t%d\t%.7g\t%.7g\t%.7g",
      // c.imt.isSA() ? c.imt.period() : 0.0,
      // (int) vs30,
      // exp(fLin),
      // exp(fNonlin),
      // exp(fT));
      //
      // System.out.println(values);

      return new Value(fT, σT);
    }

    /**
     * Wrapper class for site amplification and associated epistemic
     * uncertainty.
     */
    static final class Value {

      // TODO test short circuit for vs3000 matches pass thru apply

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
