package org.opensha2.calc;

import static com.google.common.util.concurrent.Futures.allAsList;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static com.google.common.util.concurrent.Futures.transform;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

import org.opensha2.calc.Transforms.ClusterCurveConsolidator;
import org.opensha2.calc.Transforms.ClusterToCurves;
import org.opensha2.calc.Transforms.CurveConsolidator;
import org.opensha2.calc.Transforms.CurveSetConsolidator;
import org.opensha2.calc.Transforms.ParallelSystemToCurves;
import org.opensha2.calc.Transforms.SourceToCurves;
import org.opensha2.calc.Transforms.SystemToCurves;
import org.opensha2.eq.model.ClusterSource;
import org.opensha2.eq.model.ClusterSourceSet;
import org.opensha2.eq.model.HazardModel;
import org.opensha2.eq.model.Source;
import org.opensha2.eq.model.SourceSet;
import org.opensha2.eq.model.SystemSourceSet;

import com.google.common.util.concurrent.ListenableFuture;

/**
 * Static utility methods for performing single-threaded and asynchronous hazard
 * calculations via functions in {@link Transforms}.
 * 
 * @author Peter Powers
 * @see Transforms
 * @see AsyncList
 */
final class CalcFactory {

	private CalcFactory() {}

	/* Compute hazard curves for a SourceSet. */
	static HazardCurveSet sourcesToCurves(
			SourceSet<? extends Source> sources,
			CalcConfig config,
			Site site) {

		SourceToCurves sourceToCurves = new SourceToCurves(sources, config, site);
		List<HazardCurves> curvesList = new ArrayList<>();
		for (Source source : sources.iterableForLocation(site.location)) {
			curvesList.add(sourceToCurves.apply(source));
		}
		CurveConsolidator consolidateFn = new CurveConsolidator(sources, config);
		return consolidateFn.apply(curvesList);
	}

	/* Asynchronously compute hazard curves for a SourceSet. */
	static ListenableFuture<HazardCurveSet> sourcesToCurves(
			SourceSet<? extends Source> sources,
			CalcConfig config,
			Site site,
			Executor ex) {

		SourceToCurves sourceToCurves = new SourceToCurves(sources, config, site);
		AsyncList<HazardCurves> curvesList = AsyncList.create();
		for (Source source : sources.iterableForLocation(site.location)) {
			ListenableFuture<HazardCurves> curves = transform(
				immediateFuture(source),
				sourceToCurves,
				ex);
			curvesList.add(curves);
		}
		return transform(
			allAsList(curvesList),
			new CurveConsolidator(sources, config),
			ex);
	}

	/* Compute hazard curves for a SystemSourceSet. */
	static HazardCurveSet systemToCurves(
			SystemSourceSet sources,
			CalcConfig config,
			Site site) {

		return new SystemToCurves(config, site).apply(sources);
	}

	/* Asynchronously compute hazard curves for a SystemSourceSet. */
	static ListenableFuture<HazardCurveSet> systemToCurves(
			SystemSourceSet sources,
			CalcConfig config,
			Site site,
			final Executor ex) {

		return transform(
			immediateFuture(sources),
			new ParallelSystemToCurves(site, config, ex),
			ex);
	}

	/* Compute hazard curves for a ClusterSourceSet. */
	static HazardCurveSet clustersToCurves(
			ClusterSourceSet sources,
			CalcConfig config,
			Site site) {

		ClusterToCurves clusterToCurves = new ClusterToCurves(sources, config, site);
		List<ClusterCurves> curvesList = new ArrayList<>();
		for (ClusterSource source : sources.iterableForLocation(site.location)) {
			curvesList.add(clusterToCurves.apply(source));
		}
		ClusterCurveConsolidator consolidateFn = new ClusterCurveConsolidator(sources, config);
		return consolidateFn.apply(curvesList);
	}

	/* Asynchronously compute hazard curves for a ClusterSourceSet. */
	static ListenableFuture<HazardCurveSet> clustersToCurves(
			ClusterSourceSet sources,
			CalcConfig config,
			Site site,
			Executor ex) {

		ClusterToCurves clusterToCurves = new ClusterToCurves(sources, config, site);
		AsyncList<ClusterCurves> curvesList = AsyncList.create();
		for (ClusterSource source : sources.iterableForLocation(site.location)) {
			ListenableFuture<ClusterCurves> curves = transform(
				immediateFuture(source),
				clusterToCurves,
				ex);
			curvesList.add(curves);
		}
		return transform(
			allAsList(curvesList),
			new ClusterCurveConsolidator(sources, config),
			ex);
	}

	/* Reduce hazard curves to a result. */
	static Hazard toHazardResult(
			HazardModel model,
			CalcConfig config,
			Site site,
			List<HazardCurveSet> curveSets) {

		return new CurveSetConsolidator(model, config, site).apply(curveSets);
	}

	/* Asynchronously reduce hazard curves to a result. */
	static Hazard toHazardResult(
			HazardModel model,
			CalcConfig config,
			Site site,
			AsyncList<HazardCurveSet> curveSets,
			Executor ex) throws InterruptedException, ExecutionException {

		return transform(
			allAsList(curveSets),
			new CurveSetConsolidator(model, config, site), ex).get();
	}

}
