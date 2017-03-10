package org.opensha2.gmm;

import static org.opensha2.gmm.GmmInput.Field.MW;
import static org.opensha2.gmm.GmmInput.Field.RJB;
import static org.opensha2.gmm.GmmInput.Field.VS30;

import org.opensha2.calc.ExceedanceModel;
import org.opensha2.data.Data;
import org.opensha2.gmm.GmmInput.Constraints;
import org.opensha2.gmm.GroundMotionTables.GroundMotionTable;
import org.opensha2.gmm.GroundMotionTables.GroundMotionTable.Position;
import org.opensha2.util.Maths;

import com.google.common.collect.Range;
import com.google.common.primitives.Doubles;
import com.google.common.primitives.Ints;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Experimental implementation of the PEER NGA-East ground motion model by
 * Goulet et al. (2016). This is a composite model that consists of 29 median
 * ground motion models with period dependent weights and a common standard
 * deviation model that itself consists of a 3-branch logic tree. A complete
 * NGA-East hazard curve is the wieghted sum of 87 individual curves.
 * 
 * <p>Calculation of hazard using this preliminary implementation deviates
 * somewhat from the current nshmp-haz PSHA pipeline and required implementation
 * of a {@code MultiScalarGroundMotion}. A {@code MultiScalarGroundMotion}
 * stores arrays of means and sigmas with associated weights and can only be
 * properly processed by {@link ExceedanceModel#NSHM_CEUS_MAX_INTENSITY} at this
 * time. NGA-East uses table lookups for the 29 component models.
 *
 * <p><b>Note:</b> Direct instantiation of {@code GroundMotionModel}s is
 * prohibited. Use {@link Gmm#instance(Imt)} to retrieve an instance for a
 * desired {@link Imt}.
 *
 * <p><b>Implementation note:</b> Mean values are NOT clamped per
 * {@link GmmUtils#ceusMeanClip(Imt, double)}.
 *
 * <p><b>doi:</b> TODO
 *
 * <p><b>Reference:</b> Goulet, C., Bozorgnia, Y., Abrahamson, N.A., Kuehn, N.,
 * Al Atik L., Youngs, R., Graves, R., Atkinson, G., in review, Central and
 * Eastern North America Ground-Motion Characterization: NGA-East Final Report,
 * 665 p.
 *
 * <p><b>Component:</b> average horizontal (RotD50)
 *
 * @author Peter Powers
 */
public abstract class NgaEast_2016 implements GroundMotionModel {

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
   * Note: the following inconsistency exists: NgaEast GroundMotionTables include
   * SA0P025 but sigma table do not. Sigma tables are used to initialize
   * supported Imts so there is never a problem.
   */
  static final String NAME = "NGA-East (2016)";

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
  static CoefficientContainer COEFFS_SIGMA_NGAW2;

  static {
    /*
     * TODO nga-east data are not public and therefore may not exist when
     * initializing Gmm's; we therefore init with a dummy file. Once data are
     * public, make fields (above) final, remove try-catch, and delete summy
     * sigma file.
     */
    try {
      COEFFS_SIGMA_LO = new CoefficientContainer("nga-east-sigma-lo.csv");
    } catch (Exception e) {
      COEFFS_SIGMA_LO = new CoefficientContainer("dummy-nga-east-sigma.csv");
    }
    try {
      COEFFS_SIGMA_MID = new CoefficientContainer("nga-east-sigma-mid.csv");
    } catch (Exception e) {
      COEFFS_SIGMA_MID = new CoefficientContainer("dummy-nga-east-sigma.csv");
    }
    try {
      COEFFS_SIGMA_HI = new CoefficientContainer("nga-east-sigma-hi.csv");
    } catch (Exception e) {
      COEFFS_SIGMA_HI = new CoefficientContainer("dummy-nga-east-sigma.csv");
    }
    try {
      COEFFS_SIGMA_NGAW2 = new CoefficientContainer("nga-east-sigma-ngaw2.csv");
    } catch (Exception e) {
      COEFFS_SIGMA_NGAW2 = new CoefficientContainer("dummy-nga-east-sigma.csv");
    }
  }

  private static final double[] SIGMA_WTS = { 0.185, 0.63, 0.185 };

  /* ModelID's of concentric Sammon's map rings. */
  private static final int[] R0 = { 1 };
  private static final int[] R1 = Data.indices(2, 5);
  private static final int[] R2 = Data.indices(6, 13);
  private static final int[] R3 = Data.indices(14, 29);

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

  private static final class CoefficientsNgaw2 {

    final double m5, m6, m7;

    CoefficientsNgaw2(Imt imt, CoefficientContainer cc) {
      Map<String, Double> coeffs = cc.get(imt);
      m5 = coeffs.get("m5");
      m6 = coeffs.get("m6");
      m7 = coeffs.get("m7");
    }
  }

  private final Coefficients σCoeffsLo;
  private final Coefficients σCoeffsMid;
  private final Coefficients σCoeffsHi;
  private final CoefficientsNgaw2 σCoeffsNgaw2;

  private final GroundMotionTable[] tables;
  private final double[] weights;

  NgaEast_2016(final Imt imt) {
    σCoeffsLo = new Coefficients(imt, COEFFS_SIGMA_LO);
    σCoeffsMid = new Coefficients(imt, COEFFS_SIGMA_MID);
    σCoeffsHi = new Coefficients(imt, COEFFS_SIGMA_HI);
    σCoeffsNgaw2 = new CoefficientsNgaw2(imt, COEFFS_SIGMA_NGAW2);
    tables = GroundMotionTables.getNgaEast(imt);
    weights = GroundMotionTables.getNgaEastWeights(imt);
  }

  /* Total (3-branch) sigma model. */
  double[] calcSigmas(double Mw) {
    return new double[] {
        calcSigma(σCoeffsLo, Mw),
        calcSigma(σCoeffsMid, Mw),
        calcSigma(σCoeffsHi, Mw),
    };
  }

  @Deprecated
  double[] calcSigmasMid(double Mw) {
    return new double[] {
        calcSigma(σCoeffsMid, Mw)
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

  double[] calcSigmasNgaw2(double Mw) {
    return new double[] {
        Mw < 5.5 ? σCoeffsNgaw2.m5 : Mw < 6.5 ? σCoeffsNgaw2.m6 : σCoeffsNgaw2.m7
    };
  }

  /* Return the subset of weights specified by the supplied modelIDs. */
  private static double[] selectWeights(double[] weights, int[] modelIDs) {
    double[] subsetWeights = new double[modelIDs.length];
    for (int i = 0; i < modelIDs.length; i++) {
      subsetWeights[i] = weights[modelIDs[i] - 1];
    }
    return subsetWeights;
  }

  /*
   * Sort weights descending. Pick out those models that make up the pth
   * percentile in terms of weight. If the cutoff is low enough that no models
   * are iuncluded, include just the one with the highest weight.
   */
  private static int[] percentileModels(double p, double[] weights) {
    List<Integer> sortedWtIndices = Data.sortedIndices(Doubles.asList(weights), false);
    List<Integer> modelIndices = new ArrayList<>();
    double pSum = 0.0;
    for (int wtIndex : sortedWtIndices) {
      pSum += weights[wtIndex];
      if (pSum < p) {
        modelIndices.add(wtIndex);
        continue;
      }
      break;
    }
    if (modelIndices.isEmpty()) {
      modelIndices.add(sortedWtIndices.get(0));
    }
    for (int i = 0; i < modelIndices.size(); i++) {
      modelIndices.set(i, modelIndices.get(i) + 1);
    }
    return Ints.toArray(modelIndices);
  }

  static abstract class ModelGroup extends NgaEast_2016 {

    final int[] models;
    final double[] weights;

    /* Specifiy an array of models ids. */
    ModelGroup(Imt imt, int[] models) {
      super(imt);
      this.models = models;
      this.weights = Data.round(8, Data.normalize(selectWeights(super.weights, models)));
    }

    /* Specify a weight cutoff; weights are IMT dependent. */
    ModelGroup(Imt imt, double p) {
      super(imt);
      this.models = percentileModels(p, super.weights);
      this.weights = Data.round(8, Data.normalize(selectWeights(super.weights, this.models)));
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

    // /* provided for overrides */ TODO clean
    // double[] calcSigmas(double Mw) {
    // return super.calcSigmasTotal(Mw);
    // }
  }

  /*
   * With the exception of SA0P2, the center model (1) captures the 16th
   * percentile; SA0P2 uses 1 & 2,
   */
  static class Center extends ModelGroup {
    static final String NAME = NgaEast_2016.NAME + ": Center";

    Center(Imt imt) {
      super(imt, R0);
    }
  }

  static class CenterNga extends Center {
    static final String NAME = Center.NAME + " (σ = NGAW2)";

    CenterNga(Imt imt) {
      super(imt);
    }

    @Override
    double[] calcSigmas(double Mw) {
      return super.calcSigmasNgaw2(Mw);
    }
  }

  @Deprecated
  static final class Group1 extends ModelGroup {
    static final String NAME = NgaEast_2016.NAME + ": Group1";

    Group1(Imt imt) {
      super(imt, Ints.concat(R0, R1));
    }
  }

  @Deprecated
  static final class Group2 extends ModelGroup {
    static final String NAME = NgaEast_2016.NAME + ": Group2";

    Group2(Imt imt) {
      super(imt, Ints.concat(R0, R1, R2));
    }
  }

  static class Percentile50th extends ModelGroup {
    static final String NAME = NgaEast_2016.NAME + ": 50th";

    Percentile50th(Imt imt) {
      super(imt, 0.5);
    }
  }

  static final class Percentile50thNga extends Percentile50th {
    static final String NAME = Percentile50th.NAME + " (σ = NGAW2)";

    Percentile50thNga(Imt imt) {
      super(imt);
    }

    @Override
    double[] calcSigmas(double Mw) {
      return super.calcSigmasNgaw2(Mw);
    }
  }

  static class Percentile84th extends ModelGroup {
    static final String NAME = NgaEast_2016.NAME + ": 84th";

    Percentile84th(Imt imt) {
      super(imt, 0.84);
    }
  }

  static final class Percentile84thNga extends Percentile84th {
    static final String NAME = Percentile84th.NAME + " (σ = NGAW2)";

    Percentile84thNga(Imt imt) {
      super(imt);
    }

    @Override
    double[] calcSigmas(double Mw) {
      return super.calcSigmasNgaw2(Mw);
    }
  }

  static class Percentile98th extends ModelGroup {
    static final String NAME = NgaEast_2016.NAME + ": 98th";

    Percentile98th(Imt imt) {
      super(imt, 0.98);
    }
  }

  static final class Percentile98thNga extends Percentile98th {
    static final String NAME = Percentile98th.NAME + " (σ = NGAW2)";

    Percentile98thNga(Imt imt) {
      super(imt);
    }

    @Override
    double[] calcSigmas(double Mw) {
      return super.calcSigmasNgaw2(Mw);
    }
  }

  static class Total extends ModelGroup {
    static final String NAME = NgaEast_2016.NAME + ": Total";

    Total(Imt imt) {
      super(imt, Ints.concat(R0, R1, R2, R3));
    }
  }

  static final class TotalNga extends Total {
    static final String NAME = Total.NAME + " (σ = NGAW2)";

    TotalNga(Imt imt) {
      super(imt);
    }

    @Override
    double[] calcSigmas(double Mw) {
      return super.calcSigmasNgaw2(Mw);
    }
  }

  @Deprecated
  static final class TotalSigmaCenter extends Total {
    static final String NAME = NgaEast_2016.NAME + ": Total (Central sigma)";

    TotalSigmaCenter(Imt imt) {
      super(imt);
    }

    @Override
    double[] calcSigmas(double Mw) {
      return super.calcSigmasMid(Mw);
    }
  }

  // TODO clean
  public static void main(String[] args) {
//    GroundMotionModel gmm = Gmm.NGA_EAST_50TH.instance(Imt.SA0P025);
    
    for (Imt imt : Gmm.NGA_EAST_CENTER.supportedIMTs()) {
      System.out.println(Percentile50th.NAME + ": " + imt.name());
      Percentile50th pp = new Percentile50th(imt);
      
    }
    for (Imt imt : Gmm.NGA_EAST_CENTER.supportedIMTs()) {
      System.out.println(SeedNga_1CCSP.NAME + ": " + imt.name());
      SeedNga_1CCSP pp = new SeedNga_1CCSP(imt);
    }
    // Total ngaEast1 = new Total(Imt.SA0P2);
    // Total ngaEast2 = new TotalSigmaCenter(Imt.SA0P2);
    // Total ngaEast3 = new TotalSigmaNgaw2(Imt.SA0P2);
    //
    // GmmInput.Builder builder = GmmInput.builder().withDefaults();
    // builder.rRup(10);
    // GmmInput in = builder.build();
    //
    // System.out.println(in);
    // System.out.println(ngaEast1.calc(in));
    // System.out.println(ngaEast2.calc(in));
    // System.out.println(ngaEast3.calc(in));
    //
    // System.out.println(Arrays.toString(ngaEast1.weights));
    // System.out.println(Data.sum(ngaEast1.weights));

    // System.out.println(Arrays.toString(ngaEast.models));
    // System.out.println(Arrays.toString(ngaEast.weights));
    // System.out.println(Arrays.toString(ngaEast.R0_weights));
  }

  static class Seed extends NgaEast_2016 {
    static final String NAME = NgaEast_2016.NAME + ": Seed : ";
    
    final String id;
    final GroundMotionTable table;
    private static final double[] UNIT_WT = { 1.0 };

    Seed(String id, Imt imt) {
      super(imt);
      this.id = id;
      this.table = GroundMotionTables.getNgaEastSeed(id, imt);
    }

    @Override
    public ScalarGroundMotion calc(GmmInput in) {
      Position p = table.position(in.rRup, in.Mw);
      double[] μs = { table.get(p) };
      double[] σs = calcSigmas(in.Mw);
      return new MultiScalarGroundMotion(μs, UNIT_WT, σs, SIGMA_WTS);
    }
  }

  static class SeedNga extends Seed {

    SeedNga(String id, Imt imt) {
      super(id, imt);
    }

    @Override
    double[] calcSigmas(double Mw) {
      return super.calcSigmasNgaw2(Mw);
    }
  }

  static final class Seed_1CCSP extends Seed {
    static final String ID = "1CCSP";
    static final String NAME = Seed.NAME + ID;

    Seed_1CCSP(Imt imt) {
      super(ID, imt);
    }
  }

  static final class SeedNga_1CCSP extends SeedNga {
    static final String NAME = Seed_1CCSP.NAME + " (σ = NGAW2)";

    SeedNga_1CCSP(Imt imt) {
      super(Seed_1CCSP.ID, imt);
    }
  }
  
  static final class Seed_1CVSP extends Seed {
    static final String ID = "1CVSP";
    static final String NAME = Seed.NAME + ID;

    Seed_1CVSP(Imt imt) {
      super(ID, imt);
    }
  }

  static final class SeedNga_1CVSP extends SeedNga {
    static final String NAME = Seed_1CVSP.NAME + " (σ = NGAW2)";

    SeedNga_1CVSP(Imt imt) {
      super(Seed_1CVSP.ID, imt);
    }
  }
  
  static final class Seed_2CCSP extends Seed {
    static final String ID = "2CCSP";
    static final String NAME = Seed.NAME + ID;

    Seed_2CCSP(Imt imt) {
      super(ID, imt);
    }
  }

  static final class SeedNga_2CCSP extends SeedNga {
    static final String NAME = Seed_2CCSP.NAME + " (σ = NGAW2)";

    SeedNga_2CCSP(Imt imt) {
      super(Seed_2CCSP.ID, imt);
    }
  }
  
  static final class Seed_2CVSP extends Seed {
    static final String ID = "2CVSP";
    static final String NAME = Seed.NAME + ID;

    Seed_2CVSP(Imt imt) {
      super(ID, imt);
    }
  }

  static final class SeedNga_2CVSP extends SeedNga {
    static final String NAME = Seed_2CVSP.NAME + " (σ = NGAW2)";

    SeedNga_2CVSP(Imt imt) {
      super(Seed_2CVSP.ID, imt);
    }
  }
  
  static final class Seed_ANC15 extends Seed {
    static final String ID = "ANC15";
    static final String NAME = Seed.NAME + ID;

    Seed_ANC15(Imt imt) {
      super(ID, imt);
    }
  }

  static final class SeedNga_ANC15 extends SeedNga {
    static final String NAME = Seed_ANC15.NAME + " (σ = NGAW2)";

    SeedNga_ANC15(Imt imt) {
      super(Seed_ANC15.ID, imt);
    }
  }
  
  static final class Seed_B_a04 extends Seed {
    static final String ID = "B_a04";
    static final String NAME = Seed.NAME + ID;

    Seed_B_a04(Imt imt) {
      super(ID, imt);
    }
  }

  static final class SeedNga_B_a04 extends SeedNga {
    static final String NAME = Seed_B_a04.NAME + " (σ = NGAW2)";

    SeedNga_B_a04(Imt imt) {
      super(Seed_B_a04.ID, imt);
    }
  }
  
  static final class Seed_B_ab14 extends Seed {
    static final String ID = "B_ab14";
    static final String NAME = Seed.NAME + ID;

    Seed_B_ab14(Imt imt) {
      super(ID, imt);
    }
  }

  static final class SeedNga_B_ab14 extends SeedNga {
    static final String NAME = Seed_B_ab14.NAME + " (σ = NGAW2)";

    SeedNga_B_ab14(Imt imt) {
      super(Seed_B_ab14.ID, imt);
    }
  }
  
  static final class Seed_B_ab95 extends Seed {
    static final String ID = "B_ab95";
    static final String NAME = Seed.NAME + ID;

    Seed_B_ab95(Imt imt) {
      super(ID, imt);
    }
  }

  static final class SeedNga_B_ab95 extends SeedNga {
    static final String NAME = Seed_B_ab95.NAME + " (σ = NGAW2)";

    SeedNga_B_ab95(Imt imt) {
      super(Seed_B_ab95.ID, imt);
    }
  }
  
  static final class Seed_B_bca10d extends Seed {
    static final String ID = "B_bca10d";
    static final String NAME = Seed.NAME + ID;

    Seed_B_bca10d(Imt imt) {
      super(ID, imt);
    }
  }

  static final class SeedNga_B_bca10d extends SeedNga {
    static final String NAME = Seed_B_bca10d.NAME + " (σ = NGAW2)";

    SeedNga_B_bca10d(Imt imt) {
      super(Seed_B_bca10d.ID, imt);
    }
  }
  
  static final class Seed_B_bs11 extends Seed {
    static final String ID = "B_bs11";
    static final String NAME = Seed.NAME + ID;

    Seed_B_bs11(Imt imt) {
      super(ID, imt);
    }
  }

  static final class SeedNga_B_bs11 extends SeedNga {
    static final String NAME = Seed_B_bs11.NAME + " (σ = NGAW2)";

    SeedNga_B_bs11(Imt imt) {
      super(Seed_B_bs11.ID, imt);
    }
  }
  
  static final class Seed_B_sgd02 extends Seed {
    static final String ID = "B_sgd02";
    static final String NAME = Seed.NAME + ID;

    Seed_B_sgd02(Imt imt) {
      super(ID, imt);
    }
  }

  static final class SeedNga_B_sgd02 extends SeedNga {
    static final String NAME = Seed_B_sgd02.NAME + " (σ = NGAW2)";

    SeedNga_B_sgd02(Imt imt) {
      super(Seed_B_sgd02.ID, imt);
    }
  }
  
  static final class Seed_Frankel extends Seed {
    static final String ID = "Frankel";
    static final String NAME = Seed.NAME + ID;

    Seed_Frankel(Imt imt) {
      super(ID, imt);
    }
  }

  static final class SeedNga_Frankel extends SeedNga {
    static final String NAME = Seed_Frankel.NAME + " (σ = NGAW2)";

    SeedNga_Frankel(Imt imt) {
      super(Seed_Frankel.ID, imt);
    }
  }
  
  static final class Seed_Graizer extends Seed {
    static final String ID = "Graizer";
    static final String NAME = Seed.NAME + ID;

    Seed_Graizer(Imt imt) {
      super(ID, imt);
    }
  }

  static final class SeedNga_Graizer extends SeedNga {
    static final String NAME = Seed_Graizer.NAME + " (σ = NGAW2)";

    SeedNga_Graizer(Imt imt) {
      super(Seed_Graizer.ID, imt);
    }
  }
  
  static final class Seed_HA15 extends Seed {
    static final String ID = "HA15";
    static final String NAME = Seed.NAME + ID;

    Seed_HA15(Imt imt) {
      super(ID, imt);
    }
  }

  static final class SeedNga_HA15 extends SeedNga {
    static final String NAME = Seed_HA15.NAME + " (σ = NGAW2)";

    SeedNga_HA15(Imt imt) {
      super(Seed_HA15.ID, imt);
    }
  }
  
  static final class Seed_PEER_EX extends Seed {
    static final String ID = "PEER_EX";
    static final String NAME = Seed.NAME + ID;

    Seed_PEER_EX(Imt imt) {
      super(ID, imt);
    }
  }

  static final class SeedNga_PEER_EX extends SeedNga {
    static final String NAME = Seed_PEER_EX.NAME + " (σ = NGAW2)";

    SeedNga_PEER_EX(Imt imt) {
      super(Seed_PEER_EX.ID, imt);
    }
  }
  
  static final class Seed_PEER_GP extends Seed {
    static final String ID = "PEER_GP";
    static final String NAME = Seed.NAME + ID;

    Seed_PEER_GP(Imt imt) {
      super(ID, imt);
    }
  }

  static final class SeedNga_PEER_GP extends SeedNga {
    static final String NAME = Seed_PEER_GP.NAME + " (σ = NGAW2)";

    SeedNga_PEER_GP(Imt imt) {
      super(Seed_PEER_GP.ID, imt);
    }
  }
  
  static final class Seed_PZCT15_M1SS extends Seed {
    static final String ID = "PZCT15_M1SS";
    static final String NAME = Seed.NAME + ID;

    Seed_PZCT15_M1SS(Imt imt) {
      super(ID, imt);
    }
  }

  static final class SeedNga_PZCT15_M1SS extends SeedNga {
    static final String NAME = Seed_PZCT15_M1SS.NAME + " (σ = NGAW2)";

    SeedNga_PZCT15_M1SS(Imt imt) {
      super(Seed_PZCT15_M1SS.ID, imt);
    }
  }
  
  static final class Seed_PZCT15_M2ES extends Seed {
    static final String ID = "PZCT15_M2ES";
    static final String NAME = Seed.NAME + ID;

    Seed_PZCT15_M2ES(Imt imt) {
      super(ID, imt);
    }
  }

  static final class SeedNga_PZCT15_M2ES extends SeedNga {
    static final String NAME = Seed_PZCT15_M2ES.NAME + " (σ = NGAW2)";

    SeedNga_PZCT15_M2ES(Imt imt) {
      super(Seed_PZCT15_M2ES.ID, imt);
    }
  }
  
  static final class Seed_SP15 extends Seed {
    static final String ID = "SP15";
    static final String NAME = Seed.NAME + ID;

    Seed_SP15(Imt imt) {
      super(ID, imt);
    }
  }

  static final class SeedNga_SP15 extends SeedNga {
    static final String NAME = Seed_SP15.NAME + " (σ = NGAW2)";

    SeedNga_SP15(Imt imt) {
      super(Seed_SP15.ID, imt);
    }
  }
  
  static final class Seed_YA15 extends Seed {
    static final String ID = "YA15";
    static final String NAME = Seed.NAME + ID;

    Seed_YA15(Imt imt) {
      super(ID, imt);
    }
  }

  static final class SeedNga_YA15 extends SeedNga {
    static final String NAME = Seed_YA15.NAME + " (σ = NGAW2)";

    SeedNga_YA15(Imt imt) {
      super(Seed_YA15.ID, imt);
    }
  }


}
