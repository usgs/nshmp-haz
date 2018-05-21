package gov.usgs.earthquake.nshmp.calc;

import java.util.List;
import java.util.stream.Collectors;

import com.google.common.annotations.Beta;

import gov.usgs.earthquake.nshmp.gmm.Gmm;
import gov.usgs.earthquake.nshmp.gmm.GmmInput;
import gov.usgs.earthquake.nshmp.gmm.GmmPostProcessor;
import gov.usgs.earthquake.nshmp.gmm.GroundMotionModel;
import gov.usgs.earthquake.nshmp.gmm.Imt;
import gov.usgs.earthquake.nshmp.gmm.ScalarGroundMotion;

/**
 * Ground motion calculation manager. More often than not, the default instance
 * of this class is used to simply compute scalar ground motions. However, if
 * any post processors are specified in the calculation configuration, they are
 * applied in the order listed to the computed scalar ground motions.
 *
 * @author Peter Powers
 */
@Beta
abstract class GmmProcessor {

  abstract ScalarGroundMotion apply(GroundMotionModel model, GmmInput in, Imt imt, Gmm gmm);

  static GmmProcessor instance(CalcConfig config) {
    boolean defaultOnly = config.hazard.gmmPostProcessors.isEmpty();
    return defaultOnly ? new DefaultInstance() : new Instance(config);
  }

  private static final class Instance extends GmmProcessor {

    final List<GmmPostProcessor> postProcessors;

    Instance(CalcConfig config) {
      this.postProcessors = config.hazard.gmmPostProcessors.stream()
          .map(model -> model.instance(config))
          .collect(Collectors.toList());
    }

    @Override
    public ScalarGroundMotion apply(GroundMotionModel model, GmmInput in, Imt imt, Gmm gmm) {
      ScalarGroundMotion sgm = model.calc(in);
      for (GmmPostProcessor processor : postProcessors) {
        sgm = processor.apply(sgm, in, imt, gmm);
      }
      return sgm;
    }

  }

  private static final class DefaultInstance extends GmmProcessor {
    @Override
    public ScalarGroundMotion apply(GroundMotionModel model, GmmInput in, Imt imt, Gmm gmm) {
      return model.calc(in);
    }
  }

}
