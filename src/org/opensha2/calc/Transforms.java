package org.opensha2.calc;

import static org.opensha2.gmm.Gmm.instances;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.opensha2.calc.ClusterCurves.Builder;
import org.opensha2.data.ArrayXY_Sequence;
import org.opensha2.eq.fault.Faults;
import org.opensha2.eq.fault.surface.RuptureSurface;
import org.opensha2.eq.model.ClusterSource;
import org.opensha2.eq.model.ClusterSourceSet;
import org.opensha2.eq.model.Distance;
import org.opensha2.eq.model.FaultSource;
import org.opensha2.eq.model.HazardModel;
import org.opensha2.eq.model.Rupture;
import org.opensha2.eq.model.Source;
import org.opensha2.eq.model.SourceSet;
import org.opensha2.eq.model.SystemSourceSet;
import org.opensha2.gmm.Gmm;
import org.opensha2.gmm.GmmInput;
import org.opensha2.gmm.GroundMotionModel;
import org.opensha2.gmm.Imt;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;

/**
 * Data transform {@code Function}s.
 * 
 * @author Peter Powers
 */
final class Transforms {

	/*
	 * Implemenation notes:
	 * ---------------------------------------------------------------------
	 * ClusterSourceSets contain ClusterSources, each of which references a
	 * FaultSourceSet containing one or more fault representations for the
	 * ClusterSource.
	 * 
	 * e.g. for New Madrid, each ClusterSourceSet has 5 ClusterSources, one for
	 * each position variant of the model. For each position variant there is
	 * one FaultSourceSet containing the FaultSources in the cluster, each of
	 * which may have one, or more, magnitude or other variants represented by
	 * its internal List of IncrementalMfds.
	 * ---------------------------------------------------------------------
	 * SystemSourceSets contain many single sources and the functions here
	 * handle them collectively. Rather than creating lists of input lists for
	 * each source, one large input list is created. This may change if it is
	 * decided to apply a magnitude distribution on each system source. This
	 * motivated reordering of the SourceType enum such that SystemSourceSets
	 * are processed first and granted a thread early in the calculation
	 * process.
	 * ---------------------------------------------------------------------
	 */

	/*
	 * Create a list of ground motion inputs from a source.
	 */
	static final class SourceToInputs implements Function<Source, InputList> {

		private final Site site;

		SourceToInputs(Site site) {
			this.site = site;
		}

		@Override public SourceInputList apply(Source source) {
			SourceInputList hazardInputs = new SourceInputList(source);

			for (Rupture rup : source) {

				RuptureSurface surface = rup.surface();

				Distance distances = surface.distanceTo(site.location);
				double dip = surface.dip();
				double width = surface.width();
				double zTop = surface.depth();
				double zHyp = Faults.hypocentralDepth(dip, width, zTop);

				HazardInput input = new HazardInput(
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
					site.z1p0,
					site.z2p5);
				hazardInputs.add(input);
			}

			return hazardInputs;
		}
	}

	/*
	 * Calculate ground motions for a list of ground motion inputs.
	 */
	static final class InputsToGroundMotions implements Function<InputList, GroundMotions> {

		private final Map<Imt, Map<Gmm, GroundMotionModel>> gmmTable;

		InputsToGroundMotions(Map<Imt, Map<Gmm, GroundMotionModel>> gmmTable) {
			this.gmmTable = gmmTable;
		}

		@Override public GroundMotions apply(InputList inputs) {

			Set<Imt> imtKeys = gmmTable.keySet();
			Set<Gmm> gmmKeys = gmmTable.get(imtKeys.iterator().next()).keySet();
			GroundMotions.Builder builder = GroundMotions.builder(
				inputs,
				imtKeys,
				gmmKeys);

			for (Imt imt : imtKeys) {
				for (Gmm gmm : gmmKeys) {
					GroundMotionModel model = gmmTable.get(imt).get(gmm);
					int inputIndex = 0;
					for (GmmInput gmmInput : inputs) {
						builder.add(imt, gmm, model.calc(gmmInput), inputIndex++);
					}
				}
			}
			return builder.build();
		}
	}

	/*
	 * Derive hazard curves for a set of ground motions.
	 */
	static final class GroundMotionsToCurves implements Function<GroundMotions, HazardCurves> {

		private final Map<Imt, ArrayXY_Sequence> modelCurves;
		private final ExceedanceModel exceedanceModel;
		private final double truncationLevel;

		GroundMotionsToCurves(CalcConfig config) {
			this.modelCurves = config.logModelCurves;
			this.exceedanceModel = config.exceedanceModel;
			this.truncationLevel = config.truncationLevel;
		}

		@Override public HazardCurves apply(GroundMotions groundMotions) {

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
						exceedanceModel.exceedance(
							means.get(i),
							sigmas.get(i),
							truncationLevel,
							imt,
							utilCurve);
						utilCurve.multiply(groundMotions.inputs.get(i).rate);
						gmmCurve.add(utilCurve);
					}
					curveBuilder.addCurve(imt, gmm, gmmCurve);
				}
			}
			return curveBuilder.build();
		}
	}

	/*
	 * Compute hazard curves for a source. This function coalesces the three
	 * preceeding functions into one.
	 */
	static final class SourceToCurves implements Function<Source, HazardCurves> {

		private final Function<Source, InputList> sourceToInputs;
		private final Function<InputList, GroundMotions> inputsToGroundMotions;
		private final Function<GroundMotions, HazardCurves> groundMotionsToCurves;

		SourceToCurves(
				SourceSet<? extends Source> sources,
				CalcConfig config,
				Site site) {

			Set<Gmm> gmms = sources.groundMotionModels().gmms();
			Map<Imt, Map<Gmm, GroundMotionModel>> gmmTable = instances(config.imts, gmms);

			this.sourceToInputs = new SourceToInputs(site);
			this.inputsToGroundMotions = new InputsToGroundMotions(gmmTable);
			this.groundMotionsToCurves = new GroundMotionsToCurves(config);
		}

		@Override public HazardCurves apply(Source source) {
			return groundMotionsToCurves.apply(
				inputsToGroundMotions.apply(
					sourceToInputs.apply(source)));
		}
	}

	/* Reduce multiple source curves. */
	static final class CurveConsolidator implements Function<List<HazardCurves>, HazardCurveSet> {

		private final SourceSet<? extends Source> sources;
		private final Map<Imt, ArrayXY_Sequence> modelCurves;

		CurveConsolidator(
				SourceSet<? extends Source> sources,
				CalcConfig config) {

			this.sources = sources;
			this.modelCurves = config.logModelCurves;
		}

		@Override public HazardCurveSet apply(List<HazardCurves> curvesList) {

			if (curvesList.isEmpty()) return HazardCurveSet.empty(sources);

			HazardCurveSet.Builder curveSetBuilder = HazardCurveSet.builder(
				sources,
				modelCurves);

			for (HazardCurves curves : curvesList) {
				curveSetBuilder.addCurves(curves);
			}
			return curveSetBuilder.build();
		}
	}

	/*
	 * SYSTEM: Compute hazard curves for system sources. This function derives
	 * all inputs for an entire SystemSourceSet before being composed with
	 * standard ground motion and hazard curve functions.
	 */
	static final class SystemToCurves implements Function<SystemSourceSet, HazardCurveSet> {

		private final Function<SystemSourceSet, InputList> sourcesToInputs;
		private final Function<InputList, GroundMotions> inputsToGroundMotions;
		private final Function<GroundMotions, HazardCurves> groundMotionsToCurves;
		private final Function<List<HazardCurves>, HazardCurveSet> curveConsolidator;

		SystemToCurves(
				SystemSourceSet sources,
				CalcConfig config,
				Site site) {

			Set<Gmm> gmms = sources.groundMotionModels().gmms();
			Map<Imt, Map<Gmm, GroundMotionModel>> gmmTable = instances(config.imts, gmms);

			this.sourcesToInputs = new SystemSourceSet.ToInputs(site);
			this.inputsToGroundMotions = new InputsToGroundMotions(gmmTable);
			this.groundMotionsToCurves = new GroundMotionsToCurves(config);
			this.curveConsolidator = new CurveConsolidator(sources, config);
		}

		@Override public HazardCurveSet apply(SystemSourceSet sources) {
			return curveConsolidator.apply(
				ImmutableList.of(
					groundMotionsToCurves.apply(
						inputsToGroundMotions.apply(
							sourcesToInputs.apply(sources)))));

		}
	}

	/*
	 * CLUSTER: Create a list of ground motion inputs from a cluster source.
	 */
	static final class ClusterSourceToInputs implements Function<ClusterSource, ClusterInputs> {

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

	/*
	 * CLUSTER: Calculate ground motions for a list of cluster ground motion
	 * inputs.
	 */
	static final class ClusterInputsToGroundMotions implements
			Function<ClusterInputs, ClusterGroundMotions> {

		private final InputsToGroundMotions transform;

		ClusterInputsToGroundMotions(Map<Imt, Map<Gmm, GroundMotionModel>> gmmTable) {
			transform = new InputsToGroundMotions(gmmTable);
		}

		@Override public ClusterGroundMotions apply(ClusterInputs clusterInputs) {
			ClusterGroundMotions clusterGroundMotions = new ClusterGroundMotions(
				clusterInputs.parent);
			for (SourceInputList hazardInputs : clusterInputs) {
				clusterGroundMotions.add(transform.apply(hazardInputs));
			}
			return clusterGroundMotions;
		}
	}

	/*
	 * CLUSTER: Collapse magnitude variants and compute the joint probability of
	 * exceedence for sources in a cluster. Note that this is only to be used
	 * with cluster sources as the weight of each magnitude variant is stored in
	 * the TmeporalGmmInput.rate field, which is kinda KLUDGY, but works.
	 */
	static final class ClusterGroundMotionsToCurves implements
			Function<ClusterGroundMotions, ClusterCurves> {

		private final Map<Imt, ArrayXY_Sequence> logModelCurves;
		private final ExceedanceModel exceedanceModel;
		private final double truncationLevel;

		ClusterGroundMotionsToCurves(CalcConfig config) {
			this.logModelCurves = config.logModelCurves;
			this.exceedanceModel = config.exceedanceModel;
			this.truncationLevel = config.truncationLevel;
		}

		@Override public ClusterCurves apply(ClusterGroundMotions clusterGroundMotions) {

			Builder builder = ClusterCurves.builder(clusterGroundMotions);

			for (Entry<Imt, ArrayXY_Sequence> entry : logModelCurves.entrySet()) {

				ArrayXY_Sequence modelCurve = entry.getValue();
				Imt imt = entry.getKey();

				// aggregator of curves for each fault in a cluster
				ListMultimap<Gmm, ArrayXY_Sequence> faultCurves = MultimapBuilder
					.enumKeys(Gmm.class)
					.arrayListValues(clusterGroundMotions.size())
					.build();
				ArrayXY_Sequence utilCurve = ArrayXY_Sequence.copyOf(modelCurve);

				for (GroundMotions hazardGroundMotions : clusterGroundMotions) {

					Map<Gmm, List<Double>> gmmMeans = hazardGroundMotions.means.get(imt);
					Map<Gmm, List<Double>> gmmSigmas = hazardGroundMotions.sigmas.get(imt);

					for (Gmm gmm : gmmMeans.keySet()) {
						ArrayXY_Sequence magVarCurve = ArrayXY_Sequence.copyOf(modelCurve);
						List<Double> means = gmmMeans.get(gmm);
						List<Double> sigmas = gmmSigmas.get(gmm);
						for (int i = 0; i < hazardGroundMotions.inputs.size(); i++) {
							exceedanceModel.exceedance(
								means.get(i),
								sigmas.get(i),
								truncationLevel,
								imt,
								utilCurve);
							utilCurve.multiply(hazardGroundMotions.inputs.get(i).rate);
							magVarCurve.add(utilCurve);
						}
						faultCurves.put(gmm, magVarCurve);
					}
				}

				double rate = clusterGroundMotions.parent.rate();
				for (Gmm gmm : faultCurves.keySet()) {
					// TODO where should this be pointing
					ArrayXY_Sequence clusterCurve = Utils.calcClusterExceedProb(faultCurves
						.get(gmm));
					builder.addCurve(imt, gmm, clusterCurve.multiply(rate));
				}
			}

			return builder.build();
		}
	}

	/*
	 * CLUSTER: Compute hazard curves for a cluster source. This function
	 * coalesces the three preceeding functions into one.
	 */
	static final class ClusterToCurves implements Function<ClusterSource, ClusterCurves> {

		private final Function<ClusterSource, ClusterInputs> sourceToInputs;
		private final Function<ClusterInputs, ClusterGroundMotions> inputsToGroundMotions;
		private final Function<ClusterGroundMotions, ClusterCurves> groundMotionsToCurves;

		ClusterToCurves(
				ClusterSourceSet sources,
				CalcConfig config,
				Site site) {

			Set<Gmm> gmms = sources.groundMotionModels().gmms();
			Map<Imt, Map<Gmm, GroundMotionModel>> gmmTable = instances(config.imts, gmms);

			this.sourceToInputs = new ClusterSourceToInputs(site);
			this.inputsToGroundMotions = new ClusterInputsToGroundMotions(gmmTable);
			this.groundMotionsToCurves = new ClusterGroundMotionsToCurves(config);
		}

		@Override public ClusterCurves apply(ClusterSource source) {
			return groundMotionsToCurves.apply(
				inputsToGroundMotions.apply(
					sourceToInputs.apply(source)));
		}
	}

	/*
	 * CLUSTER: Reduce multiple cluster source curves.
	 */
	static final class ClusterCurveConsolidator implements
			Function<List<ClusterCurves>, HazardCurveSet> {

		private final ClusterSourceSet sources;
		private final Map<Imt, ArrayXY_Sequence> modelCurves;

		ClusterCurveConsolidator(
				ClusterSourceSet sources,
				CalcConfig config) {

			this.sources = sources;
			this.modelCurves = config.logModelCurves;
		}

		@Override public HazardCurveSet apply(List<ClusterCurves> curvesList) {

			if (curvesList.isEmpty()) return HazardCurveSet.empty(sources);

			HazardCurveSet.Builder curveSetBuilder = HazardCurveSet.builder(
				sources,
				modelCurves);

			for (ClusterCurves curves : curvesList) {
				curveSetBuilder.addCurves(curves);
			}
			return curveSetBuilder.build();
		}
	}

	/*
	 * ALL: Final 'fan-in' consolidator function used for all source types.
	 */
	static final class CurveSetConsolidator implements Function<List<HazardCurveSet>, HazardResult> {

		private final HazardModel model;
		private final CalcConfig config;
		private final Site site;

		CurveSetConsolidator(
				HazardModel model,
				CalcConfig config,
				Site site) {

			this.model = model;
			this.config = config;
			this.site = site;
		}

		@Override public HazardResult apply(List<HazardCurveSet> curveSetList) {

			HazardResult.Builder resultBuilder = HazardResult
				.builder(config)
				.model(model)
				.site(site);

			for (HazardCurveSet curveSet : curveSetList) {
				if (curveSet.isEmpty()) continue;
				resultBuilder.addCurveSet(curveSet);
			}
			return resultBuilder.build();
		}
	}

}
