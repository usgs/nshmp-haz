package gov.usgs.earthquake.nshmp.calc;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.util.concurrent.Futures.allAsList;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static com.google.common.util.concurrent.Futures.transform;
import static gov.usgs.earthquake.nshmp.calc.CalcFactory.clustersToCurves;
import static gov.usgs.earthquake.nshmp.calc.CalcFactory.sourcesToCurves;
import static gov.usgs.earthquake.nshmp.calc.CalcFactory.systemToCurves;
import static gov.usgs.earthquake.nshmp.calc.CalcFactory.toHazardResult;
import static gov.usgs.earthquake.nshmp.data.Data.checkInRange;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Range;

import gov.usgs.earthquake.nshmp.calc.Transforms.SourceToInputs;
import gov.usgs.earthquake.nshmp.eq.model.ClusterSourceSet;
import gov.usgs.earthquake.nshmp.eq.model.GridSourceSet;
import gov.usgs.earthquake.nshmp.eq.model.HazardModel;
import gov.usgs.earthquake.nshmp.eq.model.Source;
import gov.usgs.earthquake.nshmp.eq.model.SourceSet;
import gov.usgs.earthquake.nshmp.eq.model.SystemSourceSet;

/**
 * Static probabilistic seismic hazard analysis calculators.
 *
 * @author Peter Powers
 */
public class HazardCalcs {

  /*
   * Developer notes:
   *
   * -------------------------------------------------------------------------
   * Method argument order in this class, CalcFactory, and Transforms follow the
   * general rule of model (or model derived objects), calc config, site, and
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
   * should just not be submitting any sources in any event if they are all too
   * far away.
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
    return new SourceToInputs(site)::apply;
  }

  private static Range<Double> rpRange = Range.closed(1.0, 20000.0);
  private static Range<Double> imlRange = Range.closed(0.0001, 8.0);

  /**
   * Deaggregate probabilistic seismic hazard at the supplied return period (in
   * years). Deaggregation will performed for all IMTs specified for hazard.
   * 
   * <p>Call this method with the {@link Hazard} result of
   * {@link #hazard(HazardModel, CalcConfig, Site, Executor)} to which you
   * supply the calculation settings and sites of interest that will also be
   * used for deaggregation.
   *
   * <p><b>Note:</b> any model initialization settings in {@code config} will be
   * ignored as the supplied model will already have been initialized.
   *
   * @param hazard to deaggregate
   * @param returnPeriod at which to deaggregate (in years)
   * @return a {@code Deaggregation} object
   */
  public static Deaggregation deaggReturnPeriod(
      Hazard hazard,
      double returnPeriod,
      Executor exec) {

    checkNotNull(hazard);
    checkInRange(rpRange, "Return period", returnPeriod);

    return Deaggregation.atReturnPeriod(hazard, returnPeriod, exec);
  }

  /**
   * Deaggregate probabilistic seismic hazard at the supplied intensity measure
   * level (in units of g). Deaggregation will performed for all IMTs specified
   * for hazard.
   * 
   * <p>Call this method with the {@link Hazard} result of
   * {@link #hazard(HazardModel, CalcConfig, Site, Executor)} to which you
   * supply the calculation settings and sites of interest that will also be
   * used for deaggregation.
   *
   * <p><b>Note:</b> any model initialization settings in {@code config} will be
   * ignored as the supplied model will already have been initialized.
   *
   * @param hazard to deaggregate
   * @param iml intensity measure level at which to deaggregate (in g)
   * @param imt an optional (single) IMT at which to deaggregate
   * @return a {@code Deaggregation} object
   */
  public static Deaggregation deaggIml(
      Hazard hazard,
      double iml,
      Executor exec) {

    checkNotNull(hazard);
    checkInRange(imlRange, "Intensity measure level", iml);

    return Deaggregation.atIml(hazard, iml, exec);
  }

  /**
   * Compute probabilistic seismic hazard curves at a {@code site} using the
   * supplied {@code model} and {@code config}. If an {@code executor} is
   * supplied,the calculation will be distributed; otherwise, it will run on the
   * current thread. Be sure to shutdown any supplied executor after a
   * calculation completes.
   * 
   * <p><b>Note:</b> any model initialization settings in {@code config} will be
   * ignored as the supplied model will already have been initialized.
   * 
   * @param model to use
   * @param config calculation configuration
   * @param site of interest
   * @param exec optional {@code Executor} to distribute calculation
   */
  public static Hazard hazard(
      HazardModel model,
      CalcConfig config,
      Site site,
      Executor exec) {

    checkNotNull(model);
    checkNotNull(config);
    checkNotNull(site);
    checkNotNull(exec);

    try {
      if (config.performance.threadCount == ThreadCount.ONE) {
        /*
         * TODO if threads = 1, we will have passed in a direct executor that
         * will be ignored here to make use of timing/logging method. Refactor
         * so that a log-level for this class can be used instead for a single
         * method.
         */
        Logger log = Logger.getLogger(HazardCalcs.class.getName());
        return hazardCurve(model, config, site, log);
      }
      return asyncHazardCurve(model, config, site, exec);
    } catch (ExecutionException | InterruptedException e) {
      throw new RuntimeException(e);
    }
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
          if (config.performance.optimizeGrids && gss.optimizable()) {
            gridTables.add(transform(
                immediateFuture(gss),
                GridSourceSet.optimizer(
                    site.location,
                    config.performance.smoothGrids)::apply,
                ex));
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
     * If grid optimization is enabled, grid calculations were deferred (above)
     * while table based source sets were initialized. Submit once all other
     * source types have been submitted.
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
          if (config.performance.optimizeGrids && gss.optimizable()) {
            sourceSet = GridSourceSet.optimizer(
                site.location,
                config.performance.smoothGrids).apply(gss);
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
