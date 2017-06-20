package org.opensha2.gmm;

import static org.opensha2.gmm.GmmInput.Field.MW;
import static org.opensha2.gmm.GmmInput.Field.RJB;
import static org.opensha2.gmm.GmmInput.Field.VS30;

import org.opensha2.calc.ExceedanceModel;
import org.opensha2.data.Data;
import org.opensha2.data.Indexing;
import org.opensha2.data.Interpolator;
import org.opensha2.gmm.GmmInput.Constraints;
import org.opensha2.gmm.GroundMotionTables.GroundMotionTable;
import org.opensha2.gmm.GroundMotionTables.GroundMotionTable.Position;
import org.opensha2.util.Maths;

import com.google.common.collect.Range;
import com.google.common.primitives.Ints;

import java.util.Map;

/**
 * Implementation of the PEER NGA-East ground motion model. This is a custom
 * version of the model developed specifically for USGS applications. It is a
 * composite model that consists of a reduced set of 13 median ground motion
 * models (down from 29 in the full NGA-East model) with period dependent
 * weights. PEER recommends the use of a total, magnitude dependent ergodic
 * sigma model. This implementation includes the 3-branch logic tree on sigma as
 * well.
 * 
 * <p>Calculation of hazard using this preliminary implementation deviates
 * somewhat from the current nshmp-haz PSHA pipeline and required implementation
 * of a {@code MultiScalarGroundMotion}. A {@code MultiScalarGroundMotion}
 * stores arrays of means and sigmas with associated weights and can only be
 * properly processed by {@link ExceedanceModel#NSHM_CEUS_MAX_INTENSITY} at this
 * time.
 * 
 * <p> This class also manages implementations of the 19 'seed' models used to
 * generate (via Sammons mapping) the 13 NGA-East models and associated weights.
 * Ground motions for the 19 seed and 13 component models are computed via table
 * lookups.
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
 * <p><b>Component:</b> average horizontal (RotD50)
 *
 * @author Peter Powers
 */
public abstract class NgaEastUsgs_2017 implements GroundMotionModel {

  /*
   * TODO
   * 
   * Cluster analysis is incorrect as currently implemented; analysis performed
   * after combining models
   * 
   * Deagg will currently use weight-averaged means; this is incorrect, or at
   * least requires more study to determine if it generates an acceptable
   * approximation
   * 
   * Note: the following inconsistency exists: NgaEast GroundMotionTables
   * include SA0P025 but sigma table do not. Sigma tables are used to initialize
   * supported Imts so there is never a problem.
   * 
   * When supplied with tables for the 13 usgs models, 0.01s was added with
   * values distinct from PGA, however the seed models are missing this period.
   * For now we've duplicated PGA for 0.01s ground motion values in the seed
   * model tables. Because a coefficient table (sigma coeffs in this case) is
   * referenced for supported IMTs, the ground motion tables across seed and
   * sammons models must be consistent.
   * 
   * Missing PGV tables: Grazier, PEER_EX, PEER_GP, PZCT15_M1SS, PZCT15_M2ES
   */
  static final String NAME = "NGA-East USGS (2017)";

  static final Constraints CONSTRAINTS = Constraints.builder()
      .set(MW, Range.closed(4.0, 8.2))
      .set(RJB, Range.closed(0.0, 1500.0))
      .set(VS30, Range.singleton(3000.0))
      .build();

  /*
   * Sigma coefficients for global model from tables 11-3 (tau) and 11-9 (phi).
   */
  static CoefficientContainer COEFFS_SIGMA_LO;
  static CoefficientContainer COEFFS_SIGMA_MID;
  static CoefficientContainer COEFFS_SIGMA_HI;
  static CoefficientContainer COEFFS_SIGMA_TOTAL;

  static {
    COEFFS_SIGMA_LO = new CoefficientContainer("nga-east-usgs-sigma-lo.csv");
    COEFFS_SIGMA_MID = new CoefficientContainer("nga-east-usgs-sigma-mid.csv");
    COEFFS_SIGMA_HI = new CoefficientContainer("nga-east-usgs-sigma-hi.csv");
    COEFFS_SIGMA_TOTAL = new CoefficientContainer("nga-east-usgs-sigma-total.csv");
  }

  private static final double[] SIGMA_WTS = { 0.185, 0.63, 0.185 };

  /* ModelID's of NGA-East concentric Sammon's map rings (29). */
  private static final int[] NGAE_R0 = { 1 };
  private static final int[] NGAE_R1 = Indexing.indices(2, 5);
  private static final int[] NGAE_R2 = Indexing.indices(6, 13);

  private static final class Coefficients {

    final double a, b, τ1, τ2, τ3, τ4;

    Coefficients(Imt imt, CoefficientContainer cc) {
      Map<String, Double> coeffs = cc.get(imt);
      a = coeffs.get("a");
      b = coeffs.get("b");
      τ1 = coeffs.get("tau1");
      τ2 = coeffs.get("tau2");
      τ3 = coeffs.get("tau3");
      τ4 = coeffs.get("tau4");
    }
  }

  private static final class CoefficientsTotal {

    final double τ_m5, φ_m5, τ_m6, φ_m6, τ_m7, φ_m7;

    CoefficientsTotal(Imt imt, CoefficientContainer cc) {
      Map<String, Double> coeffs = cc.get(imt);
      τ_m5 = coeffs.get("tau_M5");
      φ_m5 = coeffs.get("phi_M5");
      τ_m6 = coeffs.get("tau_M6");
      φ_m6 = coeffs.get("phi_M6");
      τ_m7 = coeffs.get("tau_M7");
      φ_m7 = coeffs.get("phi_M7");
    }
  }

  private final Coefficients σCoeffsLo;
  private final Coefficients σCoeffsMid;
  private final Coefficients σCoeffsHi;
  private final CoefficientsTotal σCoeffsTotal;

  private final GroundMotionTable[] tables;
  private final double[] weights;

  NgaEastUsgs_2017(final Imt imt) {
    σCoeffsLo = new Coefficients(imt, COEFFS_SIGMA_LO);
    σCoeffsMid = new Coefficients(imt, COEFFS_SIGMA_MID);
    σCoeffsHi = new Coefficients(imt, COEFFS_SIGMA_HI);
    σCoeffsTotal = new CoefficientsTotal(imt, COEFFS_SIGMA_TOTAL);
    tables = GroundMotionTables.getNgaEast(imt);
    weights = GroundMotionTables.getNgaEastWeights(imt);
  }

  /* Total ergodic sigma model */
  double calcSigmaTotal(double Mw) {
    double τ;
    double φ;
    if (Mw <= 5.0) {
      τ = σCoeffsTotal.τ_m5;
      φ = σCoeffsTotal.φ_m5;
    } else if (Mw <= 6.0) {
      τ = Interpolator.findY(5.0, σCoeffsTotal.τ_m5, 6.0, σCoeffsTotal.τ_m6, Mw);
      φ = Interpolator.findY(5.0, σCoeffsTotal.φ_m5, 6.0, σCoeffsTotal.φ_m6, Mw);
    } else if (Mw <= 7.0) {
      τ = Interpolator.findY(6.0, σCoeffsTotal.τ_m6, 7.0, σCoeffsTotal.τ_m7, Mw);
      φ = Interpolator.findY(6.0, σCoeffsTotal.φ_m6, 7.0, σCoeffsTotal.φ_m7, Mw);
    } else {
      τ = σCoeffsTotal.τ_m7;
      φ = σCoeffsTotal.φ_m7;
    }
    return Maths.hypot(τ, φ);
  }

  /* 3-branch sigma model. */
  double[] calcSigmas(double Mw) {
    return new double[] {
        calcSigma(σCoeffsLo, Mw),
        calcSigma(σCoeffsMid, Mw),
        calcSigma(σCoeffsHi, Mw),
    };
  }

  private static double calcSigma(Coefficients c, double Mw) {

    /* Global τ model. Equation 10-6. */
    double τ = c.τ4;
    if (Mw <= 4.5) {
      τ = c.τ1;
    } else if (Mw <= 5.0) {
      τ = c.τ1 + (c.τ2 - c.τ1) * (Mw - 4.5) / 0.5;
    } else if (Mw <= 5.5) {
      τ = c.τ2 + (c.τ3 - c.τ2) * (Mw - 5.5) / 0.5;
    } else if (Mw <= 6.5) {
      τ = c.τ3 + (c.τ4 - c.τ3) * (Mw - 5.5);
    }

    /* Global φ model. Equation 11-9. */
    double φ = c.b;
    if (Mw <= 5.0) {
      φ = c.a;
    } else if (Mw <= 6.5) {
      φ = c.a + (Mw - 5.0) * (c.b - c.a) / 1.5;
    }

    return Maths.hypot(τ, φ);
  }

  /* Return the subset of weights specified by the supplied modelIDs. */
  private static double[] selectWeights(double[] weights, int[] modelIDs) {
    double[] subsetWeights = new double[modelIDs.length];
    for (int i = 0; i < modelIDs.length; i++) {
      subsetWeights[i] = weights[modelIDs[i] - 1];
    }
    return subsetWeights;
  }

  static abstract class ModelGroup extends NgaEastUsgs_2017 {

    final int[] models;
    final double[] weights;

    /* Specifiy an array of models ids. */
    ModelGroup(Imt imt, int[] models) {
      super(imt);
      this.models = models;
      this.weights = Data.round(8, Data.normalize(selectWeights(super.weights, models)));
    }

    @Override
    public MultiScalarGroundMotion calc(GmmInput in) {
      Position p = super.tables[0].position(in.rRup, in.Mw);
      double[] μs = new double[models.length];
      for (int i = 0; i < models.length; i++) {
        μs[i] = super.tables[models[i] - 1].get(p);
      }
      double[] σs = calcSigmas(in.Mw);
      double[] σWts = σs.length > 1 ? SIGMA_WTS : new double[] { 1.0 };
      return new MultiScalarGroundMotion(μs, weights, σs, σWts);
    }
  }

  static class TotalSigmaModel extends ModelGroup {
    static final String NAME = NgaEastUsgs_2017.NAME + ": Total";

    TotalSigmaModel(Imt imt) {
      super(imt, Ints.concat(NGAE_R0, NGAE_R1, NGAE_R2));
    }

    @Override
    double[] calcSigmas(double Mw) {
      return new double[] { calcSigmaTotal(Mw) };
    }
  }

  static class BranchSigmaModel extends ModelGroup {
    static final String NAME = TotalSigmaModel.NAME + " (branching σ)";

    BranchSigmaModel(Imt imt) {
      super(imt, Ints.concat(NGAE_R0, NGAE_R1, NGAE_R2));
    }
  }

  static abstract class Sammons extends NgaEastUsgs_2017 {
    static final String NAME = NgaEastUsgs_2017.NAME + ": Sammons : ";

    final int id;
    final GroundMotionTable table;

    Sammons(int id, Imt imt) {
      super(imt);
      this.id = id;
      this.table = super.tables[id - 1];
    }

    @Override
    public ScalarGroundMotion calc(GmmInput in) {
      Position p = table.position(in.rRup, in.Mw);
      return new DefaultScalarGroundMotion(
          table.get(p),
          calcSigmaTotal(in.Mw));
    }
  }

  static class Sammons_1 extends Sammons {
    static final int ID = 1;
    static final String NAME = Sammons.NAME + ID;

    Sammons_1(Imt imt) {
      super(ID, imt);
    }
  }

  static class Sammons_2 extends Sammons {
    static final int ID = 2;
    static final String NAME = Sammons.NAME + ID;

    Sammons_2(Imt imt) {
      super(ID, imt);
    }
  }

  static class Sammons_3 extends Sammons {
    static final int ID = 3;
    static final String NAME = Sammons.NAME + ID;

    Sammons_3(Imt imt) {
      super(ID, imt);
    }
  }

  static class Sammons_4 extends Sammons {
    static final int ID = 4;
    static final String NAME = Sammons.NAME + ID;

    Sammons_4(Imt imt) {
      super(ID, imt);
    }
  }

  static class Sammons_5 extends Sammons {
    static final int ID = 5;
    static final String NAME = Sammons.NAME + ID;

    Sammons_5(Imt imt) {
      super(ID, imt);
    }
  }

  static class Sammons_6 extends Sammons {
    static final int ID = 6;
    static final String NAME = Sammons.NAME + ID;

    Sammons_6(Imt imt) {
      super(ID, imt);
    }
  }

  static class Sammons_7 extends Sammons {
    static final int ID = 7;
    static final String NAME = Sammons.NAME + ID;

    Sammons_7(Imt imt) {
      super(ID, imt);
    }
  }

  static class Sammons_8 extends Sammons {
    static final int ID = 8;
    static final String NAME = Sammons.NAME + ID;

    Sammons_8(Imt imt) {
      super(ID, imt);
    }
  }

  static class Sammons_9 extends Sammons {
    static final int ID = 9;
    static final String NAME = Sammons.NAME + ID;

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

  static abstract class Seed extends NgaEastUsgs_2017 {
    static final String NAME = NgaEastUsgs_2017.NAME + ": Seed : ";

    final String id;
    final GroundMotionTable table;

    Seed(String id, Imt imt) {
      super(imt);
      this.id = id;
      this.table = GroundMotionTables.getNgaEastSeed(id, imt);
    }

    @Override
    public ScalarGroundMotion calc(GmmInput in) {
      Position p = table.position(in.rRup, in.Mw);
      return new DefaultScalarGroundMotion(
          table.get(p),
          calcSigmaTotal(in.Mw));
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

}
