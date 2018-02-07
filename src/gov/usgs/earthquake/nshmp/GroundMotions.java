package gov.usgs.earthquake.nshmp;

import static java.lang.Math.cos;
import static java.lang.Math.hypot;
import static java.lang.Math.sin;
import static java.lang.Math.tan;
import static java.lang.Math.toRadians;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.primitives.Doubles;

import gov.usgs.earthquake.nshmp.data.Data;
import gov.usgs.earthquake.nshmp.gmm.Gmm;
import gov.usgs.earthquake.nshmp.gmm.GmmInput;
import gov.usgs.earthquake.nshmp.gmm.GroundMotionModel;
import gov.usgs.earthquake.nshmp.gmm.Imt;
import gov.usgs.earthquake.nshmp.gmm.ScalarGroundMotion;
import gov.usgs.earthquake.nshmp.util.Maths;


public class GroundMotions {
	
	private final static int ROUND = 5;
	private final static int R_POINTS = 100;
	
  public static DistanceResult distanceGroundMotions(
      Set<Gmm> gmms,
      GmmInput inputModel,
      Imt imt,
      double rMin,
      double rMax,
      boolean isLogSpace) {

    double[] distance = isLogSpace ? distanceLog(rMin, rMax) : 
    			distanceLinear(rMin, rMax);
    List<GmmInput> gmmInputs = hangingWallDistances(distance, inputModel);

    Map<Gmm, List<Double>> distanceMap = Maps.newEnumMap(Gmm.class);
    Map<Gmm, List<Double>> meanMap = Maps.newEnumMap(Gmm.class);
    Map<Gmm, List<Double>> sigmaMap = Maps.newEnumMap(Gmm.class);

    for (Gmm gmm : gmms) {
      ImmutableList.Builder<Double> means = ImmutableList.builder();
      ImmutableList.Builder<Double> sigmas = ImmutableList.builder();

      GroundMotionModel model = gmm.instance(imt);
      for (GmmInput gmmInput : gmmInputs) {
        ScalarGroundMotion gm = model.calc(gmmInput);
        means.add(gm.mean());
        sigmas.add(gm.sigma());
      }

      meanMap.put(gmm, means.build());
      sigmaMap.put(gmm, sigmas.build());
      distanceMap.put(gmm, Doubles.asList(distance));
    }

    return new DistanceResult(
        Maps.immutableEnumMap(distanceMap),
        Maps.immutableEnumMap(meanMap),
        Maps.immutableEnumMap(sigmaMap));
  }

  public static class DistanceResult {

    public final Map<Gmm, List<Double>> means;
    public final Map<Gmm, List<Double>> distance;
    public final Map<Gmm, List<Double>> sigmas;

    DistanceResult(
        Map<Gmm, List<Double>> distance,
        Map<Gmm, List<Double>> means,
        Map<Gmm, List<Double>> sigmas) {
      this.distance = distance;
      this.means = means;
      this.sigmas = sigmas;
    }
  }
  
  static double[] distanceLog(double rMin, double rMax) {
    double rStep = (Math.log10(rMax / rMin)) / ( R_POINTS - 1);
    double[] distance = Data.round(ROUND, Data.pow10(
        Data.buildSequence(Math.log10(rMin), Math.log10(rMax), rStep, true)));
    
  		return distance;
  }
  
  static double[] distanceLinear(double rMin, double rMax) {
    double rStep = 1.0;
    double[] distance = Data.buildCleanSequence(
    			rMin, rMax, rStep, true, ROUND);
    
  		return distance;
  }
  
  /*
   * Compute distance metrics for a fault.
   */
  static List<GmmInput> hangingWallDistances(
      double[] rValues,
      GmmInput inputModel) {

    /* Dip in radians */
    double δ = toRadians(inputModel.dip);

    /* Horizontal and vertical widths of fault */
    double h = cos(δ) * inputModel.width;
    double v = sin(δ) * inputModel.width;

    /* Depth to bottom of rupture */
    double zBot = inputModel.zTop + v;

    /* Distance range over which site is normal to fault plane */
    double rCutLo = tan(δ) * inputModel.zTop;
    double rCutHi = tan(δ) * zBot + h;

    /* rRup values corresponding to cutoffs above */
    double rRupLo = Maths.hypot(inputModel.zTop, rCutLo);
    double rRupHi = Maths.hypot(zBot, rCutHi - h);

    List<GmmInput> gmmInputs = new ArrayList<>(rValues.length);
    GmmInput.Builder gmmBuilder = GmmInput.builder().fromCopy(inputModel);
    
    for (double r : rValues) {
      double rJB = (r < 0) ? -r : (r < h) ? 0.0 : r - h;
      double rRup = (r < rCutLo)
          ? hypot(r, inputModel.zTop)
          : (r > rCutHi)
              ? hypot(r - h, zBot)
              : rRupScaled(r, rCutLo, rCutHi, rRupLo, rRupHi);
      gmmBuilder.distances(rJB, rRup, r);
      gmmInputs.add(gmmBuilder.build());
    }

    return gmmInputs;
  }
  
  /*
   * Computes rRup for a surface distance r. The range [rCutLo, rCutHi] must
   * contain r; rRupLo and rRupHi are rRup at rCutLo and rCutHi, respectively.
   */
  private static double rRupScaled(
      double r,
      double rCutLo,
      double rCutHi,
      double rRupLo,
      double rRupHi) {

    double rRupΔ = rRupHi - rRupLo;
    double rCutΔ = rCutHi - rCutLo;
    return rRupLo + (r - rCutLo) / rCutΔ * rRupΔ;
  }
  

}