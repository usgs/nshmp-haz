package org.opensha.calc;

import static java.lang.Math.sin;
import static org.opensha.calc.Utils.setExceedProbabilities;
import static org.opensha.geo.GeoTools.TO_RAD;
import static java.lang.Double.NaN;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

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
 * Factory class for creating asynchronous data transforms.
 * 
 * @author Peter Powers
 */
public final class Transforms {

	/**
	 * Return a site-specific {@link Function} to transform sources to ground
	 * motion model inputs.
	 * 
	 * @param site of interest
	 */
	public static Function<Source, GmmInputList> sourceToInputs(Site site) {
		return new SourceToInputs(site);
	}

	/**
	 * Return a {@link Function} to transform ground motion model inputs to
	 * ground motions.
	 * 
	 * @param models ground motion model instances to use
	 */
	public static Function<GmmInputList, GroundMotionSet> inputsToGroundMotions(
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
	public static Function<SourceSet<? extends Source>, List<GroundMotionSet>> sourcesToGroundMotions(
			Site site, Imt imt) {
		return new SourceSetToGroundMotions(site, imt);
	}

	/**
	 * Return a {@link Function} to transform {@code Source} ground motions to
	 * hazard curves.
	 * 
	 * @param model curve
	 */
	public static Function<GroundMotionSet, Map<Gmm, ArrayXY_Sequence>> groundMotionsToCurves(
			ArrayXY_Sequence model) {
		return new GroundMotionsToCurves(model);
	}

	/**
	 * Return a {@link Function} to transform a {@code ClusterSourceSet} to
	 * ground motions.
	 * 
	 * @param site of interest
	 * @param imt intensity measure type to use
	 */
	public static Function<ClusterSourceSet, List<List<GroundMotionSet>>> clustersToGroundMotions(
			Site site, Imt imt) {
		return new ClustersToGroundMotions(site, imt);

	}

	/**
	 * Return a {@link Function} to transform {@code ClusterSource} ground
	 * motions to hazard curves.
	 * 
	 * @param model curve
	 */
	public static Function<List<GroundMotionSet>, Map<Gmm, ArrayXY_Sequence>> clusterGroundMotionsToCurves(
			ArrayXY_Sequence model) {
		return new ClusterGroundMotionsToCurves(model);
	}
	
//	public static Function<>

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

	private static class SourceToInputs implements Function<Source, GmmInputList> {

		// TODO this needs additional rJB distance filtering
		// Is it possible to return an empty list??

		private final Site site;

		SourceToInputs(Site site) {
			this.site = site;
		}

		@Override public GmmInputList apply(Source source) {
			GmmInputList inputs = new GmmInputList(source);
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

	private static class InputsToGroundMotions implements Function<GmmInputList, GroundMotionSet> {

		private final Map<Gmm, GroundMotionModel> gmmInstances;

		InputsToGroundMotions(Map<Gmm, GroundMotionModel> gmmInstances) {
			this.gmmInstances = gmmInstances;
		}

		@Override public GroundMotionSet apply(GmmInputList gmmInputs) {

			GroundMotionSet.Builder gmBuilder = GroundMotionSet.builder(gmmInputs,
				gmmInstances.keySet());

			for (Entry<Gmm, GroundMotionModel> entry : gmmInstances.entrySet()) {
				int inputIndex = 0;
				for (GmmInput gmmInput : gmmInputs) {
					gmBuilder.add(entry.getKey(), entry.getValue().calc(gmmInput), inputIndex++);
				}
			}
			GroundMotionSet results = gmBuilder.build();
			return results;
		}
	}

	/*
	 * Transforms SourceSets to a List of GroundMotionSets. For use when
	 * processing whole SourceSets per thread.
	 */
	private static class SourceSetToGroundMotions implements
			Function<SourceSet<? extends Source>, List<GroundMotionSet>> {

		private final Site site;
		private final Imt imt;

		SourceSetToGroundMotions(Site site, Imt imt) {
			this.site = site;
			this.imt = imt;
		}

		@Override public List<GroundMotionSet> apply(SourceSet<? extends Source> sources) {

			Map<Gmm, GroundMotionModel> gmmInstances = Gmm.instances(sources.groundMotionModels()
				.gmms(), imt);

			Function<Source, GroundMotionSet> transform = Functions.compose(
				Transforms.inputsToGroundMotions(gmmInstances), Transforms.sourceToInputs(site));

			List<GroundMotionSet> gmSetList = new ArrayList<>();
			for (Source source : sources.locationIterable(site.loc)) {
				gmSetList.add(transform.apply(source));
			}
			return gmSetList;
		}
	}

	/*
	 * Transforms a GroundMotionSet to a map of hazard curves, one per gmm.
	 */
	private static class GroundMotionsToCurves implements
			Function<GroundMotionSet, Map<Gmm, ArrayXY_Sequence>> {

		private final ArrayXY_Sequence model;

		GroundMotionsToCurves(ArrayXY_Sequence model) {
			this.model = model;
		}

		@Override public Map<Gmm, ArrayXY_Sequence> apply(GroundMotionSet gmSet) {

			Map<Gmm, ArrayXY_Sequence> curveMap = Maps.newEnumMap(Gmm.class);
			ArrayXY_Sequence utilCurve = ArrayXY_Sequence.copyOf(model);

			for (Gmm gmm : gmSet.means.keySet()) {

				ArrayXY_Sequence gmmCurve = ArrayXY_Sequence.copyOf(model);
				curveMap.put(gmm, gmmCurve);

				List<Double> means = gmSet.means.get(gmm);
				List<Double> sigmas = gmSet.sigmas.get(gmm);

				for (int i = 0; i < means.size(); i++) {
					setExceedProbabilities(utilCurve, means.get(i), sigmas.get(i), false, NaN);
					utilCurve.multiply(gmSet.inputs.get(i).rate);
					gmmCurve.add(utilCurve);
				}
			}
			return curveMap;
		}
	}

	/*
	 * Transforms ClusterSourceSet to nested Lists of GroundMotionSets, one for
	 * each cluster.
	 */
	private static class ClustersToGroundMotions implements
			Function<ClusterSourceSet, List<List<GroundMotionSet>>> {

		SourceSetToGroundMotions transform;

		ClustersToGroundMotions(Site site, Imt imt) {
			transform = new SourceSetToGroundMotions(site, imt);
		}

		@Override public List<List<GroundMotionSet>> apply(ClusterSourceSet clusters) {
			List<List<GroundMotionSet>> gmSetList = new ArrayList<>();
			for (ClusterSource cluster : clusters) {
				gmSetList.add(transform.apply(cluster.faults()));
			}
			return gmSetList;
		}
	}

	/*
	 * Collapse magnitude variants and compute the joint probability of
	 * exceedence for sources in a cluster. Note that this is only to be used
	 * with cluster sources as the weight of each magnitude variant is stored in
	 * the TmeporalGmmInput.rate field, which is kinda KLUDGY, but works for
	 * now.
	 */
	private static class ClusterGroundMotionsToCurves implements
			Function<List<GroundMotionSet>, Map<Gmm, ArrayXY_Sequence>> {

		private final ArrayXY_Sequence model;

		ClusterGroundMotionsToCurves(ArrayXY_Sequence model) {
			this.model = model;
		}

		// TODO we're not doing any checking to see if Gmm keys are identical;
		// internally, we know they should be, so perhaps it's not necessary

		@Override public Map<Gmm, ArrayXY_Sequence> apply(List<GroundMotionSet> gmSets) {

			// aggregator of curves for each fault in a cluster
			ListMultimap<Gmm, ArrayXY_Sequence> faultCurves = MultimapBuilder.enumKeys(Gmm.class)
				.arrayListValues(gmSets.size()).build();
			ArrayXY_Sequence utilCurve = ArrayXY_Sequence.copyOf(model);

			for (GroundMotionSet gmSet : gmSets) {
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
