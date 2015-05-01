package org.opensha.calc;

import static java.lang.Double.NaN;
import static java.lang.Math.sin;
import static org.opensha.calc.Utils.setProbExceed;
import static org.opensha.geo.GeoTools.TO_RAD;
import static org.opensha.calc.ExceedanceModel.TRUNCATION_UPPER_ONLY;

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
import org.opensha.gmm.Imt;

import com.google.common.base.Function;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Table;
import com.google.common.collect.Table.Cell;

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
			Table<Gmm, Imt, GroundMotionModel> models) {
		return new InputsToGroundMotions(models);
	}

	/**
	 * Return a Function that transforms HazardGroundMotions to HazardCurves.
	 */
	static Function<HazardGroundMotions, HazardCurves> groundMotionsToCurves(
			Map<Imt, ArrayXY_Sequence> modelCurves, ExceedanceModel sigmaModel, double truncLevel) {
		return new GroundMotionsToCurves(modelCurves, sigmaModel, truncLevel);
	}

	/**
	 * Return a Function that reduces a List of HazardCurves to a
	 * HazardCurveSet.
	 */
	static Function<List<HazardCurves>, HazardCurveSet> curveConsolidator(
			SourceSet<? extends Source> sourceSet, Map<Imt, ArrayXY_Sequence> modelCurves) {
		return new CurveConsolidator(sourceSet, modelCurves);
	}

	/**
	 * Return a Function that reduces a List of HazardCurveSets to a
	 * HazardResult.
	 */
	static Function<List<HazardCurveSet>, HazardResult> curveSetConsolidator(
			Map<Imt, ArrayXY_Sequence> modelCurves) {
		return new CurveSetConsolidator(modelCurves);
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
			Table<Gmm, Imt, GroundMotionModel> models) {
		return new ClusterInputsToGroundMotions(models);
	}

	/**
	 * Return a Function that transforms a List of HazardGroundMotions to a
	 * ClusterCurves.
	 */
	static Function<ClusterGroundMotions, ClusterCurves> clusterGroundMotionsToCurves(
			Map<Imt, ArrayXY_Sequence> modelCurves, ExceedanceModel sigmaModel, double truncLevel) {
		return new ClusterGroundMotionsToCurves(modelCurves, sigmaModel, truncLevel);
	}

	/**
	 * Return a Function that reduces a List of ClusterCurves to a
	 * HazardCurveSet.
	 */
	static Function<List<ClusterCurves>, HazardCurveSet> clusterCurveConsolidator(
			ClusterSourceSet clusterSourceSet, Map<Imt, ArrayXY_Sequence> modelCurves) {
		return new ClusterCurveConsolidator(clusterSourceSet, modelCurves);
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

				Distances distances = surface.distanceTo(site.location);
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

		private final Table<Gmm, Imt, GroundMotionModel> gmmInstances;

		InputsToGroundMotions(Table<Gmm, Imt, GroundMotionModel> gmmInstances) {
			this.gmmInstances = gmmInstances;
		}

		@Override public HazardGroundMotions apply(HazardInputs hazardInputs) {

			HazardGroundMotions.Builder builder = HazardGroundMotions.builder(hazardInputs,
				gmmInstances.rowKeySet(), gmmInstances.columnKeySet());

			for (Cell<Gmm, Imt, GroundMotionModel> cell : gmmInstances.cellSet()) {
				Gmm rowKey = cell.getRowKey();
				Imt colKey = cell.getColumnKey();
				GroundMotionModel gmm = cell.getValue();
				int inputIndex = 0;
				for (GmmInput gmmInput : hazardInputs) {
					builder.add(rowKey, colKey, gmm.calc(gmmInput), inputIndex++);
				}
			}
			return builder.build();
		}
	}

	private static class GroundMotionsToCurves implements
			Function<HazardGroundMotions, HazardCurves> {

		private final Map<Imt, ArrayXY_Sequence> modelCurves;
		private final ExceedanceModel sigmaModel;
		private final double truncLevel;

		GroundMotionsToCurves(Map<Imt, ArrayXY_Sequence> modelCurves, ExceedanceModel sigmaModel,
			double truncLevel) {
			this.modelCurves = modelCurves;
			this.sigmaModel = sigmaModel;
			this.truncLevel = truncLevel;
		}

		@Override public HazardCurves apply(HazardGroundMotions groundMotions) {

			HazardCurves.Builder curveBuilder = HazardCurves.builder(groundMotions);

			for (Entry<Imt, ArrayXY_Sequence> entry : modelCurves.entrySet()) {

				ArrayXY_Sequence modelCurve = entry.getValue();
				Imt imt = entry.getKey();

				ArrayXY_Sequence utilCurve = ArrayXY_Sequence.copyOf(modelCurve);

				Map<Gmm, List<Double>> gmmMeans = groundMotions.means.get(imt);
				Map<Gmm, List<Double>> gmmSigmas = groundMotions.sigmas.get(imt);

				for (Gmm gmm : gmmMeans.keySet()) {

					ArrayXY_Sequence gmmCurve = ArrayXY_Sequence.copyOf(modelCurve);

					List<Double> means = gmmMeans.get(gmm);
					List<Double> sigmas = gmmSigmas.get(gmm);

					for (int i = 0; i < means.size(); i++) {
						// TODO the model curve is passed in in linear space but
						// for
						// lognormal we need x-values to be ln(x)
						sigmaModel.exceedance(means.get(i), sigmas.get(i), truncLevel, imt,
							utilCurve);

						// TODO clean
						// setProbExceed(means.get(i), sigmas.get(i), utilCurve,
						// TRUNCATION_UPPER_ONLY, 3.0);
						utilCurve.multiply(groundMotions.inputs.get(i).rate);
						gmmCurve.add(utilCurve);
					}
					curveBuilder.addCurve(imt, gmm, gmmCurve);
				}
			}
			return curveBuilder.build();
		}
	}

	private static class CurveConsolidator implements Function<List<HazardCurves>, HazardCurveSet> {

		private final Map<Imt, ArrayXY_Sequence> modelCurves;
		private final SourceSet<? extends Source> sourceSet;

		CurveConsolidator(SourceSet<? extends Source> sourceSet,
			Map<Imt, ArrayXY_Sequence> modelCurves) {
			this.sourceSet = sourceSet;
			this.modelCurves = modelCurves;
		}

		@Override public HazardCurveSet apply(List<HazardCurves> curvesList) {

			HazardCurveSet.Builder curveSetBuilder = HazardCurveSet.builder(sourceSet, modelCurves);

			for (HazardCurves curves : curvesList) {
				curveSetBuilder.addCurves(curves);
			}
			return curveSetBuilder.build();
		}
	}

	private static class CurveSetConsolidator implements
			Function<List<HazardCurveSet>, HazardResult> {

		private final Map<Imt, ArrayXY_Sequence> modelCurves;

		CurveSetConsolidator(Map<Imt, ArrayXY_Sequence> modelCurves) {
			this.modelCurves = modelCurves;
		}

		@Override public HazardResult apply(List<HazardCurveSet> curveSetList) {

			HazardResult.Builder resultBuilder = HazardResult.builder(modelCurves);

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

		ClusterInputsToGroundMotions(Table<Gmm, Imt, GroundMotionModel> gmmInstances) {
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

		private final Map<Imt, ArrayXY_Sequence> modelCurves;
		private final ExceedanceModel sigmaModel;
		private final double truncLevel;

		ClusterGroundMotionsToCurves(Map<Imt, ArrayXY_Sequence> modelCurves, ExceedanceModel sigmaModel,
			double truncLevel) {
			
			this.modelCurves = modelCurves;
			this.sigmaModel = sigmaModel;
			this.truncLevel = truncLevel;
		}

		@Override public ClusterCurves apply(ClusterGroundMotions clusterGroundMotions) {

			Builder builder = ClusterCurves.builder(clusterGroundMotions);

			for (Entry<Imt, ArrayXY_Sequence> entry : modelCurves.entrySet()) {

				ArrayXY_Sequence modelCurve = entry.getValue();
				Imt imt = entry.getKey();

				// aggregator of curves for each fault in a cluster
				ListMultimap<Gmm, ArrayXY_Sequence> faultCurves = MultimapBuilder
					.enumKeys(Gmm.class)
					.arrayListValues(clusterGroundMotions.size())
					.build();
				ArrayXY_Sequence utilCurve = ArrayXY_Sequence.copyOf(modelCurve);

				for (HazardGroundMotions hazardGroundMotions : clusterGroundMotions) {

					Map<Gmm, List<Double>> gmmMeans = hazardGroundMotions.means.get(imt);
					Map<Gmm, List<Double>> gmmSigmas = hazardGroundMotions.sigmas.get(imt);

					for (Gmm gmm : gmmMeans.keySet()) {
						ArrayXY_Sequence magVarCurve = ArrayXY_Sequence.copyOf(modelCurve);
						List<Double> means = gmmMeans.get(gmm);
						List<Double> sigmas = gmmSigmas.get(gmm);
						for (int i = 0; i < hazardGroundMotions.inputs.size(); i++) {
							sigmaModel.exceedance(means.get(i), sigmas.get(i), truncLevel, imt,
								utilCurve);

							// TODO needs ln(x-values)
//							setProbExceed(means.get(i), sigmas.get(i), utilCurve,
//								TRUNCATION_UPPER_ONLY, 3.0);
							utilCurve.multiply(hazardGroundMotions.inputs.get(i).rate);
							magVarCurve.add(utilCurve);
						}
						faultCurves.put(gmm, magVarCurve);
					}
				}

				double rate = clusterGroundMotions.parent.rate();
				for (Gmm gmm : faultCurves.keySet()) {
					ArrayXY_Sequence clusterCurve = Utils.calcClusterExceedProb(faultCurves
						.get(gmm));
					builder.addCurve(imt, gmm, clusterCurve.multiply(rate));
				}
			}

			return builder.build();
		}
	}

	private static class ClusterCurveConsolidator implements
			Function<List<ClusterCurves>, HazardCurveSet> {

		private final Map<Imt, ArrayXY_Sequence> modelCurves;
		private final ClusterSourceSet clusterSourceSet;

		ClusterCurveConsolidator(ClusterSourceSet clusterSourceSet,
			Map<Imt, ArrayXY_Sequence> modelCurves) {
			this.clusterSourceSet = clusterSourceSet;
			this.modelCurves = modelCurves;
		}

		@Override public HazardCurveSet apply(List<ClusterCurves> curvesList) {

			HazardCurveSet.Builder curveSetBuilder = HazardCurveSet.builder(clusterSourceSet,
				modelCurves);

			for (ClusterCurves curves : curvesList) {
				curveSetBuilder.addCurves(curves);
			}
			return curveSetBuilder.build();
		}
	}

}
