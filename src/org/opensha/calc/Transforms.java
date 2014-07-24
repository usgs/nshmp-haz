package org.opensha.calc;

import static java.lang.Math.sin;
import static org.opensha.geo.GeoTools.TO_RAD;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;

import org.opensha.data.ArrayXY_Sequence;
import org.opensha.eq.fault.surface.IndexedFaultSurface;
import org.opensha.eq.fault.surface.RuptureSurface;
import org.opensha.eq.forecast.Distances;
import org.opensha.eq.forecast.Rupture;
import org.opensha.eq.forecast.Source;
import org.opensha.geo.Location;
import org.opensha.gmm.Gmm;
import org.opensha.gmm.GmmInput;
import org.opensha.gmm.GroundMotionModel;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

/**
 * Factory class for creating asynchronous data transforms.
 * 
 * @author Peter Powers
 */
public final class Transforms {

	/**
	 * Create a site-specific asynchronous function to transform sources to
	 * ground motion model inputs.
	 * 
	 * @param site of interest
	 */
	public static AsyncFunction<Source, GmmInputList> sourceToInputs(Site site) {
		return new SourceToInputs(site);
	}

	/**
	 * Create an asynchronous function to transform ground motion model inputs
	 * to fround motions.
	 * 
	 * @param models ground motion model instances to use
	 */
	public static AsyncFunction<GmmInputList, GroundMotionSet> inputsToGroundMotions(
			Map<Gmm, GroundMotionModel> models) {
		return new InputsToGroundMotions(models);
	}

	/**
	 * Create an asynchronous function to convert ground motions to hazard
	 * curves.
	 * 
	 * @param model curve
	 */
	public static AsyncFunction<GroundMotionSet, Map<Gmm, ArrayXY_Sequence>> groundMotionsToCurves(
			ArrayXY_Sequence model) {
		return new GroundMotionsToHazardCurves(model);
	}


	/**
	 * Creates a {@code Callable} from a {@code FaultSource} and {@code Site}
	 * that returns a {@code List<GmmInput>} of inputs for a ground motion model
	 * (Gmm) calculation.
	 * @param source
	 * @param site
	 * @return a {@code List<GmmInput>} of Gmm inputs
	 * @see Gmm
	 */
	// public static Callable<GmmInput> newIndexedFaultCalcInitializer(
	// final IndexedFaultSource source, final Site site,
	// final Table<DistanceType, Integer, Double> rTable, final List<Integer>
	// sectionIDs) {
	// return new IndexedFaultCalcInitializer(source, site, rTable, sectionIDs);
	// }

	/**
	 * Creates a {@code Callable} that returns the distances between the
	 * supplied {@code IndexedFaultSurface} and {@code Location}.
	 * 
	 * @param surface for distance
	 * @param loc for distance calculation
	 * @return a {@code Distances} wrapper object
	 */
	public static Callable<Distances> newDistanceCalc(final IndexedFaultSurface surface,
			final Location loc) {
		return new DistanceCalc(surface, loc);
	}

	/**
	 * Creates a {@code Callable} that returns the supplied {@code FaultSource}
	 * if it is within {@code distance} of a {@code Location}.
	 * 
	 * @param source to filter
	 * @param loc for distance calculation
	 * @param distance limit
	 * @return the supplied {@code source} or {@code null} if source is farther
	 *         than {@code distance} from {@code loc}
	 */
	// public static Callable<FaultSource> newQuickDistanceFilter(final
	// FaultSource source,
	// final Location loc, final double distance) {
	// return new QuickDistanceFilter(source, loc, distance);
	// } TODO clean

	/**
	 * Creates a {@code Callable} that processes {@code GmmInput}s against one
	 * or more {@code GroundMotionModel}s and returns the results in a
	 * {@code Map}.
	 * @param gmmInstanceMap ground motion models to use
	 * @param input to the models
	 * @return a {@code Map} of {@code ScalarGroundMotion}s
	 */
	// public static Callable<GroundMotionCalcResult> newGroundMotionCalc(
	// final Map<Gmm, GroundMotionModel> gmmInstanceMap, final GmmInput input) {
	// return new GroundMotionCalc(gmmInstanceMap, input);
	// }

	private static class SourceToInputs implements AsyncFunction<Source, GmmInputList> {

		// TODO this needs additional rJB distance filtering
		// Is it possible to return an empty list??

		private final Site site;

		SourceToInputs(Site site) {
			this.site = site;
		}

		@Override public ListenableFuture<GmmInputList> apply(Source source) throws Exception {
			GmmInputList inputs = new GmmInputList(source);
			for (Rupture rup : source) {

				RuptureSurface surface = rup.surface();

				Distances distances = surface.distanceTo(site.loc);
				double dip = surface.dip();
				double width = surface.width();
				double zTop = surface.depth();
				double zHyp = zTop + sin(dip * TO_RAD) * width / 2.0;

				// @formatter:off
				GmmInput input = GmmInput.create(
					rup.mag(),
					distances.rJB,
					distances.rRup,
					distances.rX,
					dip,
					width,
					zTop,
					zHyp,
					rup.rake(),
					site.vs30,
					site.vsInferred,
					site.z2p5,
					site.z1p0);
				inputs.add(input);
				// @formatter:on
			}
			return Futures.immediateFuture(inputs);
		}
	}

	private static class InputsToGroundMotions implements
			AsyncFunction<GmmInputList, GroundMotionSet> {

		private final Map<Gmm, GroundMotionModel> gmmInstances;

		InputsToGroundMotions(Map<Gmm, GroundMotionModel> gmmInstances) {
			this.gmmInstances = gmmInstances;
		}

		@Override public ListenableFuture<GroundMotionSet> apply(GmmInputList gmmInputs)
				throws Exception {

			GroundMotionSet.Builder gmBuilder = GroundMotionSet.builder(gmmInputs,
				gmmInstances.keySet());

			for (Entry<Gmm, GroundMotionModel> entry : gmmInstances.entrySet()) {
				int inputIndex = 0;
				for (GmmInput gmmInput : gmmInputs) {
					gmBuilder.add(entry.getKey(), entry.getValue().calc(gmmInput), inputIndex++);
				}
			}
			GroundMotionSet results = gmBuilder.build();
			return Futures.immediateFuture(results);
		}
	}

	/*
	 * Convert a GroundMotionSet to a map of hazard curves, one per gmm.
	 */
	private static class GroundMotionsToHazardCurves implements
			AsyncFunction<GroundMotionSet, Map<Gmm, ArrayXY_Sequence>> {

		private final ArrayXY_Sequence model;

		GroundMotionsToHazardCurves(ArrayXY_Sequence model) {
			this.model = model;
		}

		@Override public ListenableFuture<Map<Gmm, ArrayXY_Sequence>> apply(GroundMotionSet gmSet)
				throws Exception {

			Map<Gmm, ArrayXY_Sequence> curveMap = Maps.newEnumMap(Gmm.class);

			for (Gmm gmm : gmSet.means.keySet()) {

				ArrayXY_Sequence gmmCurve = ArrayXY_Sequence.copyOf(model);
				curveMap.put(gmm, gmmCurve);

				ArrayXY_Sequence utilCurve = ArrayXY_Sequence.copyOf(model);

				List<Double> means = gmSet.means.get(gmm);
				List<Double> sigmas = gmSet.sigmas.get(gmm);

				for (int i = 0; i < means.size(); i++) {
					Utils.setExceedProbabilities(utilCurve, means.get(i), sigmas.get(i), false,
						Double.NaN);
					utilCurve.multiply(gmSet.inputs.get(i).rate());
					gmmCurve.add(utilCurve);
				}
			}
			return Futures.immediateFuture(curveMap);
		}
	}
	

}
