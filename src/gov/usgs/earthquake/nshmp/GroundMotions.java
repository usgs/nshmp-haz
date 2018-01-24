package gov.usgs.earthquake.nshmp;

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

  public static DistanceResult distanceGroundMotions(
      Set<Gmm> gmms,
      GmmInput inputModel,
      Imt imt,
      double rMax) {

    int round = 5;
    double rJB;
    double rX;
    double rRup;
    double rMin = 0.001;
    double rPoints = 100.0;
    double rStep = (Math.log10(rMax / rMin)) / (rPoints - 1);
    double[] distance = Data.round(round, Data.pow10(
        Data.buildSequence(Math.log10(rMin), Math.log10(rMax), rStep, true)));

    List<GmmInput> gmmInputs = new ArrayList<>();
    GmmInput.Builder gmmBuilder = GmmInput.builder().fromCopy(inputModel);

    for (double r : distance) {
      rJB = r;
      rX = r;
      rRup = Maths.hypot(r, inputModel.zTop);
      gmmBuilder.distances(rJB, rRup, rX);
      gmmInputs.add(gmmBuilder.build());
    }

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

}
