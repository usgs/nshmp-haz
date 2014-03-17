package org.opensha.gmm;

import org.opensha.calc.ScalarGroundMotion;

/**
 * Interface implemented by all ground motion models (GMMs); these are comonly
 * referred to as ground motion prediction equations (GMPEs) or attenuation
 * relationships. Instances of GMMs should be obtained through the {@link GMM}
 * {@code enum} class.
 *
 * @author Peter Powers
 * @see GMM
 */
public interface GroundMotionModel {
	
	/*
	 * NOTE: There is an unenforceable contract that GroundMotionModel
	 * implementations provide a public static String 'NAME' field and
	 * CoefficientContainer 'CC' field. This field is used to supply
	 * the name of the GroundMotionModel to the GMM enum via reflection. GMM
	 * will throw an error if the field does not exist. The two fields are
	 * declared public to simplify reflection.
	 */

	/**
	 * Compute the scalar ground motion and its standard deviation for the
	 * supplied arguments.
	 * @param args a ground motion model input argument container
	 * @return a scalar ground motion wrapper
	 */
	public ScalarGroundMotion calc(GMM_Input args);
	
	/*
	 * Compute the scalar ground motion for the supplied arguments at all
	 * spectral periods.
	 */
//	Map<IMT, ScalarGroundMotion> calcAll(GMM_Input args);
	
	
	
}
