package org.opensha2.programs;

import static java.lang.Runtime.getRuntime;
import static java.util.concurrent.Executors.newFixedThreadPool;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

import org.opensha2.calc.CalcConfig;
import org.opensha2.calc.Calcs;
import org.opensha2.calc.Deaggregation;
import org.opensha2.calc.HazardResult;
import org.opensha2.calc.Site;
import org.opensha2.eq.model.HazardModel;
import org.opensha2.gmm.Imt;

import com.google.common.base.Optional;
import com.google.common.base.Throwables;

/**
 * Entry point for deaggregating probabilisitic seismic hazard.
 * 
 * computing a hazard curve at a {@link Site} from a {@link HazardModel}. The
 * {@code main()} method of this class returns mean hazard curves for the model
 * and {@link Imt} specified. For more detailed results at a {@code Site},
 * consider programmatically using the {@code calc()} methods of this class.
 * 
 * @author Peter Powers
 */
public class DeaggCalc {

	public static void main(String[] args) {

		/* Delegate to run which has a return value for testing. */

		String status = run(args);
		if (status != null) {
			System.err.print(status);
			System.exit(1);
		}
		System.exit(0);
	}

	static String run(String[] args) {
		throw new UnsupportedOperationException(
			"Not yet implemented: how to specify return period");
	}

	// TODO when doing deagg as a program, what is output?
	// Do we skip curve output? probably not
	// - csv files of total and each gmm, deagg table
	// - metadatafile about mean, mode (json??)
	// - file of contributions?
	// - one folder per site

	/**
	 * Perform a hazard deaggregation at a {@code site} for a {@code model},
	 * {@code config}, and return period. If an {@code executor} is supplied, it
	 * will be used to distribute hazard calculation tasks; otherwise, one will
	 * be created.
	 * 
	 * <p><b>Note:</b> any model initialization settings in {@code config} will
	 * be ignored as the supplied model will already have been initialized.</p>
	 * 
	 * @param model to use
	 * @param config calculation configuration
	 * @param site of interest
	 * @param returnPeriod at which to deaggregate
	 * @param executor to use ({@link Optional})
	 * @return a HazardResult
	 */
	public static Deaggregation calc(
			HazardModel model,
			CalcConfig config,
			Site site,
			double returnPeriod,
			Optional<Executor> executor) {

		Optional<Executor> execLocal = executor.or(Optional.of(createExecutor()));

		try {
			HazardResult result = Calcs.hazardCurve(model, config, site, execLocal);
			if (!executor.isPresent()) ((ExecutorService) executor).shutdown();
			return Calcs.deaggregation(result, returnPeriod);
		} catch (ExecutionException | InterruptedException e) {
			Throwables.propagate(e);
			return null;
		}
	}

	private static ExecutorService createExecutor() {
		return newFixedThreadPool(getRuntime().availableProcessors());
	}

}
