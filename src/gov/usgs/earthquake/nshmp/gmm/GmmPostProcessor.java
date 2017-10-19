package gov.usgs.earthquake.nshmp.gmm;

import gov.usgs.earthquake.nshmp.calc.CalcConfig;

/**
 * Interface implemented by any model designed to modify scalar ground motions
 * computed from any {@link GroundMotionModel} (GMM).
 *
 * @author Peter Powers
 */
public interface GmmPostProcessor {

  /**
   * Update the supplied {@code ScalarGroundMotion}.
   * 
   * @param sgm the {@code ScalarGroundMotion} to modify
   * @param in the source-site parameterization
   * @param imt the intensity measure type being considered
   * @param gmm the ground motion model in use
   */
  ScalarGroundMotion apply(ScalarGroundMotion sgm, GmmInput in, Imt imt, Gmm gmm);

  /**
   * Ground motion post processors identifiers
   */
  public enum Model {

    /**
     * The Rezaeian et al. (2014) viscous damping model.
     * 
     * @see RezaeianDamping_2014
     */
    REZAEIAN_DAMPING_2014 {
      @Override
      public GmmPostProcessor instance(CalcConfig config) {
        return new RezaeianDamping_2014(config);
      }
    };

    /**
     * Create an instance of the post processor.
     * @param config for instance intialization
     * @return a concrete {@code GmmPostProcessor} instance
     */
    public abstract GmmPostProcessor instance(CalcConfig config);

  }
}
