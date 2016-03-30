package org.opensha2.calc;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.opensha2.calc.CalcFactory.clustersToCurves;
import static org.opensha2.calc.CalcFactory.sourcesToCurves;
import static org.opensha2.calc.CalcFactory.systemToCurves;
import static org.opensha2.calc.CalcFactory.toHazardResult;
import static com.google.common.util.concurrent.Futures.allAsList;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static com.google.common.util.concurrent.Futures.transform;
import static org.opensha2.eq.model.PointSourceType.FIXED_STRIKE;
import static org.opensha2.data.Data.checkInRange;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.opensha2.calc.Transforms.SourceToInputs;
import org.opensha2.eq.model.ClusterSourceSet;
import org.opensha2.eq.model.GridSourceSet;
import org.opensha2.eq.model.HazardModel;
import org.opensha2.eq.model.Source;
import org.opensha2.eq.model.SourceSet;
import org.opensha2.eq.model.SystemSourceSet;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Range;

/**
 * Static probabilistic seismic hazard analysis calculators.
 * 
 * @author Peter Powers
 */
public class Calcs {

	/*
	 * Developer notes:
	 * 
	 * -------------------------------------------------------------------------
	 * Method argument order in this class, CalcFactory, and Transforms follow
	 * the general rule of model (or model derived objects), calc config, site,
	 * and then any others.
	 * -------------------------------------------------------------------------
	 * All calculations are done in log space, only on export are x-values
	 * returned to linear space.
	 * -------------------------------------------------------------------------
	 * The ability to run a full calculation on the current thread facilitates
	 * debugging.
	 * -------------------------------------------------------------------------
	 * Single threaded calcs monitor and log calculation duration of each
	 * SourceSet.
	 * -------------------------------------------------------------------------
	 * Although NPEs will be thrown once any method argument in this class is
	 * used, because multiple arguments may be passed on the same line thereby
	 * complicating identification of a null value, preemtive checks are
	 * performed.
	 * -------------------------------------------------------------------------
	 * Notes on empty objects: In the default calculation pipeline, calculation
	 * tasks are initiated when SourceSet.iterableForLocation() yields relevant
	 * sources for a site. Similarly, optimized (table-based) grid sources, once
	 * created for a site, will not yield sources if the mfd table is empty.
	 * System source sets, on the other hand, create a list of inputs on a
	 * separate thread using means other than iterableForLocation. They are
	 * therefore short-circuited returning an empty HazardCurveSet if no sources
	 * are within range of a site. Cluster source sets have similar issue.
	 * 
	 * TODO follow HazardCurveSet.Builder.empty() calls because it seems like
	 * clusters shouldn't have to use it as the iterator in clustersToInputs
	 * should just not be submitting any sources in any event if they are all
	 * too far away.
	 * -------------------------------------------------------------------------
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

	private static Range<Double> rpRange = Range.closed(1.0, 4000.0);

	/**
	 * Perform a deaggregation of probabilisitic seismic hazard.
	 * 
	 * @param hazard to deaggregate
	 * @param returnPeriod at which to deaggregate
	 */
	public static Deaggregation deaggregation(
			Hazard hazard,
			double returnPeriod) {

		checkNotNull(hazard);
		checkInRange(rpRange, "Return period", returnPeriod);

		return Deaggregation.atReturnPeriod(hazard, returnPeriod);
	}

	/**
	 * Compute probabilistic seismic hazard, possibly using an {@link Optional}
	 * {@link Executor}. If no executor is supplied, the calculation
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
	public static Hazard hazard(
			HazardModel model,
			CalcConfig config,
			Site site,
			Optional<Executor> ex)
			throws InterruptedException, ExecutionException {

		checkNotNull(model);
		checkNotNull(config);
		checkNotNull(site);
		checkNotNull(ex);

		if (ex.isPresent()) {
			return asyncHazardCurve(model, config, site, ex.get());
		}
		Logger log = Logger.getLogger(Calcs.class.getName());
		return hazardCurve(model, config, site, log);
	}

	/*
	 * Run a hazard curve calculation in parallel.
	 */
	private static Hazard asyncHazardCurve(
			HazardModel model,
			CalcConfig config,
			Site site,
			Executor ex) throws InterruptedException, ExecutionException {

		AsyncList<HazardCurveSet> curveSets = AsyncList.createWithCapacity(model.size());
		AsyncList<SourceSet<? extends Source>> gridTables = AsyncList.create();

		for (SourceSet<? extends Source> sourceSet : model) {

			switch (sourceSet.type()) {

				case GRID:
					GridSourceSet gss = (GridSourceSet) sourceSet;
					if (config.performance.optimizeGrids 
							&& gss.sourceType() != FIXED_STRIKE
							&& gss.optimizable()) {
						gridTables.add(transform(immediateFuture(gss),
							GridSourceSet.optimizer(site.location), ex));
						break;
					}
					curveSets.add(sourcesToCurves(sourceSet, config, site, ex));
					break;

				case CLUSTER:
					curveSets.add(clustersToCurves((ClusterSourceSet) sourceSet, config, site, ex));
					break;

				case SYSTEM:
					curveSets.add(systemToCurves((SystemSourceSet) sourceSet, config, site, ex));
					break;

				default:
					curveSets.add(sourcesToCurves(sourceSet, config, site, ex));
					break;
			}
		}

		/*
		 * If grid optimization is enabled, grid calculations were deferred
		 * (above) while table based source sets were initialized. Submit once
		 * all other source types have been submitted.
		 */
		for (SourceSet<? extends Source> sourceSet : allAsList(gridTables).get()) {
			curveSets.add(sourcesToCurves(sourceSet, config, site, ex));
		}

		return toHazardResult(model, config, site, curveSets, ex);
	}

	/*
	 * Run a hazard curve calculation on the current thread.
	 */
	private static Hazard hazardCurve(
			HazardModel model,
			CalcConfig config,
			Site site,
			Logger log) {

		List<HazardCurveSet> curveSets = new ArrayList<>(model.size());

		log.info("HazardCurve: (single-threaded)");
		Stopwatch swTotal = Stopwatch.createStarted();
		Stopwatch swSource = Stopwatch.createStarted();

		for (SourceSet<? extends Source> sourceSet : model) {

			switch (sourceSet.type()) {
				case GRID:
					GridSourceSet gss = (GridSourceSet) sourceSet;
					if (config.performance.optimizeGrids && gss.sourceType() != FIXED_STRIKE) {
						sourceSet = GridSourceSet.optimizer(site.location).apply(gss);
						log(log, MSSG_GRID_INIT, sourceSet.name(), duration(swSource));
					}
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
					break;
			}
		}

		log.log(Level.INFO, String.format(" %s: %s", MSSG_DURATION, duration(swTotal)));
		swTotal.stop();
		swSource.stop();
		return toHazardResult(model, config, site, curveSets);
	}

	/*
	 * Support methods and fields for single-threaded calculations with more
	 * verbose output.
	 */

	private static String duration(Stopwatch sw) {
		String duration = sw.toString();
		sw.reset().start();
		return duration;
	}

	private static final String MSSG_GRID_INIT = "Init grid table";
	private static final String MSSG_COMPLETED = "      Completed";
	private static final String MSSG_DURATION = "     Total time";

	private static void log(Logger log, String leader, String source, String duration) {
		log.log(Level.INFO, String.format(" %s: %s %s", leader, duration, source));
	}

}
