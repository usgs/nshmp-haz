package org.opensha.calc;

import static com.google.common.util.concurrent.Futures.allAsList;
import static java.lang.Math.sin;
import static org.opensha.calc.Utils.setExceedProbabilities;
import static org.opensha.geo.GeoTools.TO_RAD;
import static java.lang.Double.NaN;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.opensha.calc.HazardCurves.Builder;
import org.opensha.data.ArrayXY_Sequence;
import org.opensha.eq.fault.surface.RuptureSurface;
import org.opensha.eq.forecast.ClusterSource;
import org.opensha.eq.forecast.ClusterSourceSet;
import org.opensha.eq.forecast.Distances;
import org.opensha.eq.forecast.Rupture;
import org.opensha.eq.forecast.Source;
import org.opensha.eq.forecast.SourceSet;
import org.opensha.gmm.Gmm;
import org.opensha.gmm.GmmInput;
import org.opensha.gmm.GroundMotionModel;
import org.opensha.gmm.Imt;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;

/**
 * Factory class for creating data transforms.
 * 
 * @author Peter Powers
 */
final class Transforms {

	/**
	 * Return a site-specific {@link Function} to transform sources to ground
	 * motion model inputs.
	 * 
	 * @param site of interest
	 */
	public static Function<Source, HazardInputs> sourceToInputs(Site site) {
		return new SourceToInputs(site);
	}

	/**
	 * Return a {@link Function} to transform ground motion model inputs to
	 * ground motions.
	 * 
	 * @param models ground motion model instances to use
	 */
	public static Function<HazardInputs, HazardGroundMotions> inputsToGroundMotions(
			Map<Gmm, GroundMotionModel> models) {
		return new InputsToGroundMotions(models);
	}

	/**
	 * Return a {@link Function} to transform a SourceSet to ground motions. The
	 * returned {@code Function} internally composes
	 * {@link #sourceToInputs(Site)} and {@link #inputsToGroundMotions(Map)}.
	 * 
	 * @param site of interest
	 * @param imt intensity measure type to use
	 */
	public static Function<SourceSet<? extends Source>, List<HazardGroundMotions>> sourcesToGroundMotions(
			Site site, Imt imt) {
		return new SourceSetToGroundMotions(site, imt);
	}

	/**
	 * Return a {@link Function} to transform {@code Source} ground motions to
	 * hazard curves.
	 * 
	 * @param model curve
	 */
	public static Function<HazardGroundMotions, HazardCurves> groundMotionsToCurves(
			ArrayXY_Sequence model) {
		return new GroundMotionsToCurves(model);
	}

	public static Function<List<HazardCurves>, HazardCurveSet> curveConsolidator(
			SourceSet<? extends Source> sourceSet, ArrayXY_Sequence model) {
		return new CurveConsolidator(sourceSet, model);
	}

	public static Function<List<HazardCurveSet>, HazardResult> curveSetConsolidator() {
		return new CurveSetConsolidator();
	}

	/**
	 * Return a {@link Function} to transform a {@code ClusterSourceSet} to
	 * ground motions.
	 * 
	 * @param site of interest
	 * @param imt intensity measure type to use
	 */
	public static Function<ClusterSourceSet, List<List<HazardGroundMotions>>> clustersToGroundMotions(
			Site site, Imt imt) {
		return new ClustersToGroundMotions(site, imt);

	}

	/**
	 * Return a {@link Function} to transform {@code ClusterSource} ground
	 * motions to hazard curves.
	 * 
	 * @param model curve
	 */
	public static Function<List<HazardGroundMotions>, Map<Gmm, ArrayXY_Sequence>> clusterGroundMotionsToCurves(
			ArrayXY_Sequence model) {
		return new ClusterGroundMotionsToCurves(model);
	}

	// public static Function<>

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

	private static class SourceToInputs implements Function<Source, HazardInputs> {

		// TODO this needs additional rJB distance filtering
		// Is it possible to return an empty list??

		private final Site site;

		SourceToInputs(Site site) {
			this.site = site;
		}

		@Override public HazardInputs apply(Source source) {
			HazardInputs inputs = new HazardInputs(source);
			for (Rupture rup : source) {

				RuptureSurface surface = rup.surface();

				Distances distances = surface.distanceTo(site.loc);
				double dip = surface.dip();
				double width = surface.width();
				double zTop = surface.depth();
				double zHyp = zTop + sin(dip * TO_RAD) * width / 2.0;

				// @formatter:off
				TemporalGmmInput input = new TemporalGmmInput(
					rup.rate(),
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
			return inputs;
		}
	}

	private static class InputsToGroundMotions implements
			Function<HazardInputs, HazardGroundMotions> {

		private final Map<Gmm, GroundMotionModel> gmmInstances;

		InputsToGroundMotions(Map<Gmm, GroundMotionModel> gmmInstances) {
			this.gmmInstances = gmmInstances;
		}

		@Override public HazardGroundMotions apply(HazardInputs gmmInputs) {

			HazardGroundMotions.Builder gmBuilder = HazardGroundMotions.builder(gmmInputs,
				gmmInstances.keySet());

			for (Entry<Gmm, GroundMotionModel> entry : gmmInstances.entrySet()) {
				int inputIndex = 0;
				for (GmmInput gmmInput : gmmInputs) {
					gmBuilder.add(entry.getKey(), entry.getValue().calc(gmmInput), inputIndex++);
				}
			}
			HazardGroundMotions results = gmBuilder.build();
			return results;
		}
	}

	/*
	 * Transforms SourceSets to a List of GroundMotionSets. For use when
	 * processing whole SourceSets per thread.
	 */
	private static class SourceSetToGroundMotions implements
			Function<SourceSet<? extends Source>, List<HazardGroundMotions>> {

		private final Site site;
		private final Imt imt;

		SourceSetToGroundMotions(Site site, Imt imt) {
			this.site = site;
			this.imt = imt;
		}

		@Override public List<HazardGroundMotions> apply(SourceSet<? extends Source> sources) {

			Map<Gmm, GroundMotionModel> gmmInstances = Gmm.instances(sources.groundMotionModels()
				.gmms(), imt);

			Function<Source, HazardGroundMotions> transform = Functions.compose(
				Transforms.inputsToGroundMotions(gmmInstances), Transforms.sourceToInputs(site));

			List<HazardGroundMotions> gmSetList = new ArrayList<>();
			for (Source source : sources.locationIterable(site.loc)) {
				gmSetList.add(transform.apply(source));
			}
			return gmSetList;
		}
	}

	/*
	 * Transforms HazardGroundMotions to HazardCurves that contains one curve
	 * per gmm.
	 */
	private static class GroundMotionsToCurves implements
			Function<HazardGroundMotions, HazardCurves> {

		private final ArrayXY_Sequence model;

		GroundMotionsToCurves(ArrayXY_Sequence model) {
			this.model = model;
		}

		@Override public HazardCurves apply(HazardGroundMotions groundMotions) {

			HazardCurves.Builder curveBuilder = HazardCurves.builder(groundMotions);
			ArrayXY_Sequence utilCurve = ArrayXY_Sequence.copyOf(model);

			for (Gmm gmm : groundMotions.means.keySet()) {

				ArrayXY_Sequence gmmCurve = ArrayXY_Sequence.copyOf(model);

				List<Double> means = groundMotions.means.get(gmm);
				List<Double> sigmas = groundMotions.sigmas.get(gmm);

				for (int i = 0; i < means.size(); i++) {
					setExceedProbabilities(utilCurve, means.get(i), sigmas.get(i), false, NaN);
					utilCurve.multiply(groundMotions.inputs.get(i).rate);
					gmmCurve.add(utilCurve);
				}
				curveBuilder.addCurve(gmm, gmmCurve);
			}
			return curveBuilder.build();
		}
	}

	/*
	 * Transforms a List of HazardCurves to a single HazardCurveSet (the final
	 * results for an individual SourceSet.
	 */
	private static class CurveConsolidator implements Function<List<HazardCurves>, HazardCurveSet> {

		private final ArrayXY_Sequence model;
		private final SourceSet<? extends Source> sourceSet;

		CurveConsolidator(SourceSet<? extends Source> sourceSet, ArrayXY_Sequence model) {
			this.sourceSet = sourceSet;
			this.model = model;
		}

		@Override public HazardCurveSet apply(List<HazardCurves> curvesList) {

			HazardCurveSet.Builder curveSetBuilder = HazardCurveSet.builder(sourceSet, model);

			for (HazardCurves curves : curvesList) {
				curveSetBuilder.addCurves(curves);
			}
			return curveSetBuilder.build();
		}
	}

	private static class CurveSetConsolidator implements
			Function<List<HazardCurveSet>, HazardResult> {

		CurveSetConsolidator() {}

		@Override public HazardResult apply(List<HazardCurveSet> curveSetList) {

			HazardResult.Builder resultBuilder = HazardResult.builder();

			for (HazardCurveSet curves : curveSetList) {
				resultBuilder.addCurveSet(curves);
			}
			return resultBuilder.build();
		}
	}

	// /*
	// * Transforms a HazardGroundMotions to a map of hazard curves, one per
	// gmm.
	// */
	// private static class GroundMotionsToCurves implements
	// Function<HazardGroundMotions, Map<Gmm, ArrayXY_Sequence>> {
	//
	// private final ArrayXY_Sequence model;
	//
	// GroundMotionsToCurves(ArrayXY_Sequence model) {
	// this.model = model;
	// }
	//
	// @Override public Map<Gmm, ArrayXY_Sequence> apply(HazardGroundMotions
	// gmSet)
	// {
	//
	// Map<Gmm, ArrayXY_Sequence> curveMap = Maps.newEnumMap(Gmm.class);
	// ArrayXY_Sequence utilCurve = ArrayXY_Sequence.copyOf(model);
	//
	// for (Gmm gmm : gmSet.means.keySet()) {
	//
	// ArrayXY_Sequence gmmCurve = ArrayXY_Sequence.copyOf(model);
	// curveMap.put(gmm, gmmCurve);
	//
	// List<Double> means = gmSet.means.get(gmm);
	// List<Double> sigmas = gmSet.sigmas.get(gmm);
	//
	// for (int i = 0; i < means.size(); i++) {
	// setExceedProbabilities(utilCurve, means.get(i), sigmas.get(i), false,
	// NaN);
	// utilCurve.multiply(gmSet.inputs.get(i).rate);
	// gmmCurve.add(utilCurve);
	// }
	// }
	// return curveMap;
	// }
	// }

	/*
	 * Transforms ClusterSourceSet to nested Lists of GroundMotionSets, one for
	 * each cluster.
	 */
	private static class ClustersToGroundMotions implements
			Function<ClusterSourceSet, List<List<HazardGroundMotions>>> {

		SourceSetToGroundMotions transform;

		ClustersToGroundMotions(Site site, Imt imt) {
			transform = new SourceSetToGroundMotions(site, imt);
		}

		@Override public List<List<HazardGroundMotions>> apply(ClusterSourceSet clusterSourceSet) {
			List<List<HazardGroundMotions>> gmSetList = new ArrayList<>();
			for (ClusterSource clusterSource : clusterSourceSet) {
				gmSetList.add(transform.apply(clusterSource.faults()));
			}
			return gmSetList;
		}
	}

//	/*
//	 * Transforms a ClusterSource to a List of GroundMotion Sets, one for
//	 * each fault in the cluster.
//	 */
//	private static class ClusterToGroundMotions implements
//			Function<ClusterSourceSet, List<List<HazardGroundMotions>>> {
//
//		SourceSetToGroundMotions transform;
//
//		ClustersToGroundMotions(Site site, Imt imt) {
//			transform = new SourceSetToGroundMotions(site, imt);
//		}
//
//		@Override public List<List<HazardGroundMotions>> apply(ClusterSourceSet clusterSourceSet) {
//			List<List<HazardGroundMotions>> gmSetList = new ArrayList<>();
//			for (ClusterSource clusterSource : clusterSourceSet) {
//				gmSetList.add(transform.apply(clusterSource.faults()));
//			}
//			return gmSetList;
//		}
//	}

	/*
	 * Collapse magnitude variants and compute the joint probability of
	 * exceedence for sources in a cluster. Note that this is only to be used
	 * with cluster sources as the weight of each magnitude variant is stored in
	 * the TmeporalGmmInput.rate field, which is kinda KLUDGY, but works for
	 * now.
	 */
	private static class ClusterGroundMotionsToCurves implements
			Function<List<HazardGroundMotions>, Map<Gmm, ArrayXY_Sequence>> {

		private final ArrayXY_Sequence model;

		ClusterGroundMotionsToCurves(ArrayXY_Sequence model) {
			this.model = model;
		}

		// TODO we're not doing any checking to see if Gmm keys are identical;
		// internally, we know they should be, so perhaps it's not necessary

		@Override public Map<Gmm, ArrayXY_Sequence> apply(List<HazardGroundMotions> gmSets) {

			// aggregator of curves for each fault in a cluster
			ListMultimap<Gmm, ArrayXY_Sequence> faultCurves = MultimapBuilder.enumKeys(Gmm.class)
				.arrayListValues(gmSets.size()).build();
			ArrayXY_Sequence utilCurve = ArrayXY_Sequence.copyOf(model);

			for (HazardGroundMotions gmSet : gmSets) {
				for (Gmm gmm : gmSet.means.keySet()) {
					ArrayXY_Sequence magVarCurve = ArrayXY_Sequence.copyOf(model);
					List<Double> means = gmSet.means.get(gmm);
					List<Double> sigmas = gmSet.sigmas.get(gmm);
					for (int i = 0; i < gmSet.inputs.size(); i++) {
						setExceedProbabilities(utilCurve, means.get(i), sigmas.get(i), false, 0.0);
						utilCurve.multiply(gmSet.inputs.get(i).rate);
						magVarCurve.add(utilCurve);
					}
					faultCurves.put(gmm, magVarCurve);
				}
			}

			Map<Gmm, ArrayXY_Sequence> clusterCurves = Maps.newEnumMap(Gmm.class);
			for (Gmm gmm : faultCurves.keySet()) {
				clusterCurves.put(gmm, Utils.calcClusterExceedProb(faultCurves.get(gmm)));
			}

			return clusterCurves;
		}

	}

}
