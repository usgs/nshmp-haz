package org.opensha2.calc;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.opensha2.calc.CalcFactory.clustersToCurves;
import static org.opensha2.calc.CalcFactory.sourcesToCurves;
import static org.opensha2.calc.CalcFactory.systemToCurves;
import static org.opensha2.calc.CalcFactory.toHazardResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.opensha2.calc.Transforms.SourceToInputs;
import org.opensha2.data.DataTable;
import org.opensha2.eq.model.ClusterSourceSet;
import org.opensha2.eq.model.GridSourceSet;
import org.opensha2.eq.model.GridSourceSetTable;
import org.opensha2.eq.model.HazardModel;
import org.opensha2.eq.model.Source;
import org.opensha2.eq.model.SourceSet;
import org.opensha2.eq.model.SystemSourceSet;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Stopwatch;

/**
 * Static probabilistic seismic hazard analysis calculators.
 * 
 * @author Peter Powers
 */
public class Calcs {

	/*
	 * Implementation notes:
	 * -------------------------------------------------------------------------
	 * Method argument order in this, CalcFactory , and Transforms follow the
	 * gerneral rule of model (or model derived obejcts), calc config, site, and
	 * then any others.
	 * -------------------------------------------------------------------------
	 * All calculations are done in log space, only on export are x-values
	 * returned to linear space.
	 * -------------------------------------------------------------------------
	 * The ability to run a full calculation on the current thread facilitates
	 * debugging.
	 * -------------------------------------------------------------------------
	 * Single threaded calcs monitor and log calculation duration of each
	 * SourceSet.
	 */

	/**
	 * Return a {@code Function} that converts a {@code Source} along with an
	 * initially supplied {@code Site} to a list of ground motion model inputs.
	 * 
	 * @param site to initialize function with
	 * @see InputList
	 */
	public static Function<Source, InputList> sourceToInputs(Site site) {
		return new SourceToInputs(site);
	}

	/**
	 * Compute a hazard curve, possibly using an {@link Optional}
	 * {@link Executor}. If no {@code Executor} is supplied, the calculation
	 * will run on the current thread.
	 * 
	 * @param model to use
	 * @param config calculation properties
	 * @param site of interest
	 * @param ex optional {@code Executor} to use in calculation
	 * @throws InterruptedException if an {@code Executor} was supplied and the
	 *         calculation is interrupted
	 * @throws ExecutionException if an {@code Executor} was supplied and a
	 *         problem arises during the calculation
	 */
	public static HazardResult hazardCurve(
			HazardModel model,
			CalcConfig config,
			Site site,
			Optional<Executor> ex)
			throws InterruptedException, ExecutionException {

		checkNotNull(model);
		checkNotNull(config);
		checkNotNull(site);
		checkNotNull(ex);

		Logger log = Logger.getLogger(Calcs.class.getName());

		if (ex.isPresent()) {
			return asyncHazardCurve(model, config, site, ex.get());
		}
		return hazardCurve(model, config, site, log);
	}

	/* Run a hazard curve calculation using supplied executor. */
	private static HazardResult asyncHazardCurve(
			HazardModel model,
			CalcConfig config,
			Site site,
			Executor ex) throws InterruptedException, ExecutionException {

		AsyncList<HazardCurveSet> curveSets = AsyncList.createWithCapacity(model.size());

		for (SourceSet<? extends Source> sourceSet : model) {
			switch (sourceSet.type()) {
				case CLUSTER:
					curveSets.add(clustersToCurves((ClusterSourceSet) sourceSet, config, site, ex));
					break;
				case SYSTEM:
					curveSets.add(systemToCurves((SystemSourceSet) sourceSet, config, site, ex));
					break;
				default:
					curveSets.add(sourcesToCurves(sourceSet, config, site, ex));
			}
		}

		return toHazardResult(model, config, site, curveSets, ex);
	}

	/* Run a hazard curve calculation on the current thread. */
	private static HazardResult hazardCurve(
			HazardModel model,
			CalcConfig config,
			Site site,
			Logger log) {

		List<HazardCurveSet> curveSets = new ArrayList<>(model.size());

		log.info("Single threaded HazardCurve calculation:");
		Stopwatch swTotal = Stopwatch.createStarted();
		Stopwatch swSource = Stopwatch.createStarted();

		for (SourceSet<? extends Source> sourceSet : model) {
			switch (sourceSet.type()) {
				case GRID:
					DataTable d = GridSourceSetTable.toSourceTable((GridSourceSet) sourceSet, site.location);
//					System.out.println(d);
					curveSets.add(sourcesToCurves(sourceSet, config, site));
					log(log, MSSG_COMPLETED, sourceSet.name(), duration(swSource));
					break;
					
				case CLUSTER:
					curveSets.add(clustersToCurves((ClusterSourceSet) sourceSet, config, site));
					log(log, MSSG_COMPLETED, sourceSet.name(), duration(swSource));
					break;
				case SYSTEM:
					curveSets.add(systemToCurves((SystemSourceSet) sourceSet, config, site));
					log(log, MSSG_COMPLETED, sourceSet.name(), duration(swSource));
					break;
				default:
					curveSets.add(sourcesToCurves(sourceSet, config, site));
					log(log, MSSG_COMPLETED, sourceSet.name(), duration(swSource));
			}
		}

		log.log(Level.INFO, String.format(" %s: %s", MSSG_DURATION, duration(swTotal)));
		swTotal.stop();
		swSource.stop();

		return toHazardResult(model, config, site, curveSets);
	}

	/* support methods and fields for single-threaded calculations. */

	private static String duration(Stopwatch sw) {
		String duration = sw.toString();
		sw.reset().start();
		return duration;
	}

	private static final String MSSG_COMPLETED = " Completed";
	private static final String MSSG_DURATION = "Total time";

	private static void log(Logger log, String leader, String source, String duration) {
		log.log(Level.INFO, String.format(" %s: %s %s", leader, duration, source));
	}

}
