package gov.usgs.earthquake.nshmp;

//....................................... Import ..........................................
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;

import gov.usgs.earthquake.nshmp.data.Data;
import gov.usgs.earthquake.nshmp.eq.model.Distance;
import gov.usgs.earthquake.nshmp.gmm.Gmm;
import gov.usgs.earthquake.nshmp.gmm.GmmInput;
import gov.usgs.earthquake.nshmp.gmm.Imt;
import gov.usgs.earthquake.nshmp.gmm.ScalarGroundMotion;
import gov.usgs.earthquake.nshmp.util.Maths;
//--------------------------------------- End Import --------------------------------------



public class GroundMotions {
  
  
  //................................ Method: distanceGroundMotion .........................
  public static DistanceResult distanceGroundMotions(
      Set<Gmm> gmms,
      GmmInput inputModel,
      Imt imt,
      double rMax) {
    
    
    //.............................. Set Distance and Gmm Inputs ..........................
    double rJB;
    double rX;
    double rRup;
    double rMin = 0.001;
    double rPoints = 20.0;
    double rStep = (Math.log10(rMax/rMin))/(rPoints-1);
    double[] distanceLog = Data.buildSequence(
        Math.log10(rMin), Math.log10(rMax), rStep, true);
    double[] distance = Data.pow10(distanceLog.clone());
    
    List<Double> rJBs = new ArrayList<>();
    List<Double> rRups = new ArrayList<>();
    List<Double> rXs = new ArrayList<>();
    List<GmmInput> gmmInputs = new ArrayList<>(); 
    
    for(double r : distance) {
      rJB = r;
      rX = r;
      rRup = Maths.hypot(r, inputModel.zTop);
      GmmInput.Builder gmmBuilder = GmmInput.builder().fromModel(inputModel);
      gmmInputs.add(gmmBuilder.distances(rJB, rRup, rX).build());
      rJBs.add(rJB);
      rRups.add(rRup);
      rXs.add(rX);
    }
    //-------------------------------------------------------------------------------------
    
    
    //........................... Calculate Ground Motion .................................
    Map<Gmm, List<Double>> distanceMap = Maps.newEnumMap(Gmm.class);
    Map<Gmm, List<Double>> meanMap = Maps.newEnumMap(Gmm.class);
    Map<Gmm, List<Double>> sigmaMap = Maps.newEnumMap(Gmm.class);
    
    for(Gmm gmm : gmms) {
      ImmutableList.Builder<Double> means = ImmutableList.builder();
      ImmutableList.Builder<Double> sigmas = ImmutableList.builder();
     
      for(GmmInput gmmInput : gmmInputs) {
        ScalarGroundMotion gm = gmm.instance(imt).calc(gmmInput);
        means.add(gm.mean());
        sigmas.add(gm.sigma());      
      }
      
      meanMap.put(gmm, means.build());
      sigmaMap.put(gmm, sigmas.build());
      distanceMap.put(gmm, rXs);
    }
    //-------------------------------------------------------------------------------------
    
    return new DistanceResult(
        rJBs,
        rRups,
        rXs,
        Maps.immutableEnumMap(distanceMap),
        Maps.immutableEnumMap(meanMap),
        Maps.immutableEnumMap(sigmaMap)
    );
  }
  //------------------------------ End Method: distanceGroundMotion -----------------------
  
  
  
  //............................. Class: DistanceResult ...................................
  public static class DistanceResult {
    
    public final List<Double> rJBs;
    public final List<Double> rRups;  
    public final List<Double> rXs;
    public final Map<Gmm, List<Double>> means;
    public final Map<Gmm, List<Double>> distance;
    public final Map<Gmm, List<Double>> sigmas;
    
    DistanceResult(
        List<Double> rJBs,
        List<Double> rRups,
        List<Double> rXs,
        Map<Gmm, List<Double>> distance,
        Map<Gmm, List<Double>> means,
        Map<Gmm, List<Double>> sigmas){
      this.rJBs = rJBs;
      this.rRups = rRups;
      this.rXs = rXs;
      this.distance = distance;    
      this.means = means;
      this.sigmas = sigmas;
    }
  }
  //------------------------------ End Class: DistanceResult ------------------------------

  
  
}
//-------------------------------- End Class: GroundMotions -------------------------------
