package org.opensha.calc;

import static java.lang.Double.NaN;
import static java.lang.Math.sin;
import static org.opensha.calc.Utils.setExceedProbabilities;
import static org.opensha.geo.GeoTools.TO_RAD;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.opensha.calc.ClusterCurves.Builder;
import org.opensha.data.ArrayXY_Sequence;
import org.opensha.eq.fault.surface.RuptureSurface;
import org.opensha.eq.model.ClusterSource;
import org.opensha.eq.model.ClusterSourceSet;
import org.opensha.eq.model.Distances;
import org.opensha.eq.model.FaultSource;
import org.opensha.eq.model.Rupture;
import org.opensha.eq.model.Source;
import org.opensha.eq.model.SourceSet;
import org.opensha.gmm.Gmm;
import org.opensha.gmm.GmmInput;
import org.opensha.gmm.GroundMotionModel;

import com.google.common.base.Function;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;

/**
 * Factory class for creating data transforms.
 * 
 * @author Peter Powers
 */
final class Transforms {

	private Transforms() {}

	/**
	 * Return a site-specific Function that transforms a Source to HazardInputs.
	 */
	static Function<Source, HazardInputs> sourceToInputs(Site site) {
		return new SourceToInputs(site);
	}

	/**
	 * Return a Function that transforms HazardInputs to HazardGroundMotions.
	 */
	static Function<HazardInputs, HazardGroundMotions> inputsToGroundMotions(
			Map<Gmm, GroundMotionModel> models) {
		return new InputsToGroundMotions(models);
	}

	/**
	 * Return a Function that transforms HazardGroundMotions to HazardCurves.
	 */
	static Function<HazardGroundMotions, HazardCurves> groundMotionsToCurves(ArrayXY_Sequence modelCurve) {
		return new GroundMotionsToCurves(modelCurve);
	}

	/**
	 * Return a Function that reduces a List of HazardCurves to a
	 * HazardCurveSet.
	 */
	static Function<List<HazardCurves>, HazardCurveSet> curveConsolidator(
			SourceSet<? extends Source> sourceSet, ArrayXY_Sequence modelCurve) {
		return new CurveConsolidator(sourceSet, modelCurve);
	}

	/**
	 * Return a Function that reduces a List of HazardCurveSets to a
	 * HazardResult.
	 */
	static Function<List<HazardCurveSet>, HazardResult> curveSetConsolidator(ArrayXY_Sequence modelCurve) {
		return new CurveSetConsolidator(modelCurve);
	}

	/**
	 * Return a site-specific Function that transforms a ClusterSource to a List
	 * of HazardInputs, one for each Source in the cluster.
	 */
	static Function<ClusterSource, ClusterInputs> clusterSourceToInputs(Site site) {
		return new ClusterSourceToInputs(site);
	}

	/**
	 * Return a Function that transforms a List of HazardInputs for the Sources
	 * in a ClusterSource to a List of HazardGroundMotions.
	 */
	static Function<ClusterInputs, ClusterGroundMotions> clusterInputsToGroundMotions(
			Map<Gmm, GroundMotionModel> models) {
		return new ClusterInputsToGroundMotions(models);
	}

	/**
	 * Return a Function that transforms a List of HazardGroundMotions to a
	 * ClusterCurves.
	 */
	static Function<ClusterGroundMotions, ClusterCurves> clusterGroundMotionsToCurves(
			ArrayXY_Sequence modelCurve) {
		return new ClusterGroundMotionsToCurves(modelCurve);
	}

	/**
	 * Return a Function that reduces a List of ClusterCurves to a
	 * HazardCurveSet.
	 */
	static Function<List<ClusterCurves>, HazardCurveSet> clusterCurveConsolidator(
			ClusterSourceSet clusterSourceSet, ArrayXY_Sequence modelCurve) {
		return new ClusterCurveConsolidator(clusterSourceSet, modelCurve);
	}

	private static class SourceToInputs implements Function<Source, HazardInputs> {

		private final Site site;

		SourceToInputs(Site site) {
			this.site = site;
		}

		@Override public HazardInputs apply(Source source) {
			HazardInputs hazardInputs = new HazardInputs(source);
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
				hazardInputs.add(input);
				// @formatter:on
			}
			return hazardInputs;
		}
	}

	private static class InputsToGroundMotions implements
			Function<HazardInputs, HazardGroundMotions> {

		private final Map<Gmm, GroundMotionModel> gmmInstances;

		InputsToGroundMotions(Map<Gmm, GroundMotionModel> gmmInstances) {
			this.gmmInstances = gmmInstances;
		}

		@Override public HazardGroundMotions apply(HazardInputs hazardInputs) {

			HazardGroundMotions.Builder gmBuilder = HazardGroundMotions.builder(hazardInputs,
				gmmInstances.keySet());

			for (Entry<Gmm, GroundMotionModel> entry : gmmInstances.entrySet()) {
				int inputIndex = 0;
				for (GmmInput gmmInput : hazardInputs) {
					gmBuilder.add(entry.getKey(), entry.getValue().calc(gmmInput), inputIndex++);
				}
			}
			HazardGroundMotions results = gmBuilder.build();
			return results;
		}
	}

	/*
	 * Transforms HazardGroundMotions to HazardCurves that contains one curve
	 * per gmm.
	 */
	private static class GroundMotionsToCurves implements
			Function<HazardGroundMotions, HazardCurves> {

		private final ArrayXY_Sequence modelCurve;

		GroundMotionsToCurves(ArrayXY_Sequence modelCurve) {
			this.modelCurve = modelCurve;
		}

		@Override public HazardCurves apply(HazardGroundMotions groundMotions) {

			HazardCurves.Builder curveBuilder = HazardCurves.builder(groundMotions);
			ArrayXY_Sequence utilCurve = ArrayXY_Sequence.copyOf(modelCurve);

			for (Gmm gmm : groundMotions.means.keySet()) {

				ArrayXY_Sequence gmmCurve = ArrayXY_Sequence.copyOf(modelCurve);

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

	private static class CurveConsolidator implements Function<List<HazardCurves>, HazardCurveSet> {

		private final ArrayXY_Sequence modelCurve;
		private final SourceSet<? extends Source> sourceSet;

		CurveConsolidator(SourceSet<? extends Source> sourceSet, ArrayXY_Sequence modelCurve) {
			this.sourceSet = sourceSet;
			this.modelCurve = modelCurve;
		}

		@Override public HazardCurveSet apply(List<HazardCurves> curvesList) {

			HazardCurveSet.Builder curveSetBuilder = HazardCurveSet.builder(sourceSet, modelCurve);

			for (HazardCurves curves : curvesList) {
				curveSetBuilder.addCurves(curves);
			}
			return curveSetBuilder.build();
		}
	}

	private static class CurveSetConsolidator implements
			Function<List<HazardCurveSet>, HazardResult> {

		private final ArrayXY_Sequence modelCurve;
		
		CurveSetConsolidator(ArrayXY_Sequence modelCurve) {
			this.modelCurve = modelCurve;
		}

		@Override public HazardResult apply(List<HazardCurveSet> curveSetList) {

			HazardResult.Builder resultBuilder = HazardResult.builder(modelCurve);

			for (HazardCurveSet curves : curveSetList) {
				resultBuilder.addCurveSet(curves);
			}
			return resultBuilder.build();
		}
	}

	private static class ClusterSourceToInputs implements Function<ClusterSource, ClusterInputs> {

		private final SourceToInputs transform;

		ClusterSourceToInputs(Site site) {
			transform = new SourceToInputs(site);
		}

		@Override public ClusterInputs apply(ClusterSource clusterSource) {
			ClusterInputs clusterInputs = new ClusterInputs(clusterSource);
			for (FaultSource faultSource : clusterSource.faults()) {
				clusterInputs.add(transform.apply(faultSource));
			}
			return clusterInputs;
		}
	}

	private static class ClusterInputsToGroundMotions implements
			Function<ClusterInputs, ClusterGroundMotions> {

		private final InputsToGroundMotions transform;

		ClusterInputsToGroundMotions(Map<Gmm, GroundMotionModel> gmmInstances) {
			transform = new InputsToGroundMotions(gmmInstances);
		}

		@Override public ClusterGroundMotions apply(ClusterInputs clusterInputs) {
			ClusterGroundMotions clusterGroundMotions = new ClusterGroundMotions(
				clusterInputs.parent);
			for (HazardInputs hazardInputs : clusterInputs) {
				clusterGroundMotions.add(transform.apply(hazardInputs));
			}
			return clusterGroundMotions;
		}
	}

	/*
	 * Collapse magnitude variants and compute the joint probability of
	 * exceedence for sources in a cluster. Note that this is only to be used
	 * with cluster sources as the weight of each magnitude variant is stored in
	 * the TmeporalGmmInput.rate field, which is kinda KLUDGY, but works.
	 */
	private static class ClusterGroundMotionsToCurves implements
			Function<ClusterGroundMotions, ClusterCurves> {

		private final ArrayXY_Sequence modelCurve;

		ClusterGroundMotionsToCurves(ArrayXY_Sequence modelCurve) {
			this.modelCurve = modelCurve;
		}

		// TODO we're not doing any checking to see if Gmm keys are identical;
		// internally, we know they should be, so perhaps it's not necessary
		// verify this; is this referring to the builders used baing able to
		// accept multiple, overriding calls to addCurve ??

		@Override public ClusterCurves apply(ClusterGroundMotions clusterGroundMotions) {

			// aggregator of curves for each fault in a cluster
			ListMultimap<Gmm, ArrayXY_Sequence> faultCurves = MultimapBuilder.enumKeys(Gmm.class)
				.arrayListValues(clusterGroundMotions.size()).build();
			ArrayXY_Sequence utilCurve = ArrayXY_Sequence.copyOf(modelCurve);

			for (HazardGroundMotions hazardGroundMotions : clusterGroundMotions) {
				for (Gmm gmm : hazardGroundMotions.means.keySet()) {
					ArrayXY_Sequence magVarCurve = ArrayXY_Sequence.copyOf(modelCurve);
					List<Double> means = hazardGroundMotions.means.get(gmm);
					List<Double> sigmas = hazardGroundMotions.sigmas.get(gmm);
					for (int i = 0; i < hazardGroundMotions.inputs.size(); i++) {
						setExceedProbabilities(utilCurve, means.get(i), sigmas.get(i), false, 0.0);
						utilCurve.multiply(hazardGroundMotions.inputs.get(i).rate);
						magVarCurve.add(utilCurve);
					}
					faultCurves.put(gmm, magVarCurve);
				}
			}

			Builder builder = ClusterCurves.builder(clusterGroundMotions);
			double rate = clusterGroundMotions.parent.rate();
			for (Gmm gmm : faultCurves.keySet()) {
				ArrayXY_Sequence clusterCurve = Utils.calcClusterExceedProb(faultCurves.get(gmm));
				builder.addCurve(gmm, clusterCurve.multiply(rate));
			}
			return builder.build();
		}
	}

	private static class ClusterCurveConsolidator implements
			Function<List<ClusterCurves>, HazardCurveSet> {

		private final ArrayXY_Sequence modelCurve;
		private final ClusterSourceSet clusterSourceSet;

		ClusterCurveConsolidator(ClusterSourceSet clusterSourceSet, ArrayXY_Sequence modelCurve) {
			this.clusterSourceSet = clusterSourceSet;
			this.modelCurve = modelCurve;
		}

		@Override public HazardCurveSet apply(List<ClusterCurves> curvesList) {

			HazardCurveSet.Builder curveSetBuilder = HazardCurveSet
				.builder(clusterSourceSet, modelCurve);

			for (ClusterCurves curves : curvesList) {
				curveSetBuilder.addCurves(curves);
			}
			return curveSetBuilder.build();
		}
	}

}
