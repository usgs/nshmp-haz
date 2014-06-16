package org.opensha.gmm;

import java.util.Set;
import java.util.concurrent.ExecutionException;

import org.opensha.calc.ScalarGroundMotion;

/**
 * Static utiltiy methods targeted at Matlab users wishing to compute scalar
 * ground motions.
 * 
 * @author Peter Powers
 */
public class MatUtil {

	/**
	 * Compute the median groiund motion and its standard deviation for a
	 * specified model, intensity measure type (IMT), and source
	 * 
	 * <p>{@code enum} types are identified in matlab as e.g. {@code Gmm$ASK_14}
	 * .</p>
	 * 
	 * @param model to use
	 * @param imt intensity measure type (e.g. {@code PGA}, {@code SA1P00})
	 * @param source parameterization
	 * @return a two-element double[] containing the natural log of the median
	 *         ground motion and its standard deviation
	 * @throws ExecutionException if a problem occurs while initializing the model
	 */
	public static double[] calc(Gmm model, IMT imt, GMM_Input source) throws ExecutionException {
		ScalarGroundMotion sgm = model.instance(imt).calc(source);
		return new double[] { sgm.mean(), sgm.stdDev() };
	}

	/**
	 * Compute a spectrum of ground motions and their standard deviations for a
	 * specified model and source. All spectral periods supported by the model
	 * are returned.
	 * 
	 * <p>{@code enum} types are identified in matlab as e.g. {@code Gmm$ASK_14}
	 * .</p>
	 * 
	 * @param model to use
	 * @param source parameterization
	 * @return a {@link MatSpectrum} data container
	 * @throws ExecutionException if a problem occurs while initializing the model
	 */
	public static MatSpectrum spectrum(Gmm model, GMM_Input source) throws ExecutionException {
		Set<IMT> imts = model.responseSpectrumIMTs();
		MatSpectrum spectrum = new MatSpectrum(imts.size());
		int i = 0;
		for (IMT imt : imts) {
			ScalarGroundMotion sgm = model.instance(imt).calc(source);
			spectrum.periods[i] = imt.period();
			spectrum.means[i] = sgm.mean();
			spectrum.sigmas[i] = sgm.stdDev();
			i++;
		}
		return spectrum;
	}
	
}
