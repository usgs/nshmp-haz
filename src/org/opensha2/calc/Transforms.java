package org.opensha2.calc;

import static java.lang.Math.min;
import static org.opensha2.eq.model.Distance.Type.R_JB;
import static org.opensha2.eq.model.Distance.Type.R_RUP;
import static org.opensha2.eq.model.Distance.Type.R_X;

import java.util.BitSet;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;

import org.opensha2.calc.ClusterCurves.Builder;
import org.opensha2.data.ArrayXY_Sequence;
import org.opensha2.data.DataUtils;
import org.opensha2.eq.fault.Faults;
import org.opensha2.eq.fault.surface.GriddedSurface;
import org.opensha2.eq.fault.surface.RuptureSurface;
import org.opensha2.eq.model.ClusterSource;
import org.opensha2.eq.model.ClusterSourceSet;
import org.opensha2.eq.model.Distance;
import org.opensha2.eq.model.Distance.Type;
import org.opensha2.eq.model.FaultSource;
import org.opensha2.eq.model.Rupture;
import org.opensha2.eq.model.Source;
import org.opensha2.eq.model.SourceSet;
import org.opensha2.eq.model.SystemSourceSet;
import org.opensha2.eq.model.SystemSourceSet.SystemSource;
import org.opensha2.geo.Location;
import org.opensha2.gmm.Gmm;
import org.opensha2.gmm.GmmInput;
import org.opensha2.gmm.GroundMotionModel;
import org.opensha2.gmm.Imt;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.ArrayTable;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Table;
import com.google.common.collect.Table.Cell;

/**
 * Data transform {@code Function}s. This class includes {@code Function}s for
 * all {@code SourceType}s except {@code SystemSource}s. See
 * {@link SystemSourceSet} for more information.
 * 
 * @author Peter Powers
 */
final class Transforms {

	static final class SourceToInputs implements Function<Source, HazardInputs> {

		private final Site site;

		SourceToInputs(final Site site) {
			this.site = site;
		}

		@Override public HazardInputs apply(final Source source) {

			HazardInputs hazardInputs = new HazardInputs(source);
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
					site.z2p5,
					site.z1p0);
				hazardInputs.add(input);
			}
			return hazardInputs;
		}
	}

	static final class InputsToGroundMotions implements Function<HazardInputs, HazardGroundMotions> {

		private final Table<Gmm, Imt, GroundMotionModel> gmmInstances;

		InputsToGroundMotions(final Table<Gmm, Imt, GroundMotionModel> gmmInstances) {
			this.gmmInstances = gmmInstances;
		}

		@Override public HazardGroundMotions apply(final HazardInputs hazardInputs) {

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

	static final class GroundMotionsToCurves implements Function<HazardGroundMotions, HazardCurves> {

		private final Map<Imt, ArrayXY_Sequence> modelCurves;
		private final ExceedanceModel sigmaModel;
		private final double truncLevel;

		GroundMotionsToCurves(
			final Map<Imt, ArrayXY_Sequence> modelCurves,
			final ExceedanceModel sigmaModel,
			final double truncLevel) {

			this.modelCurves = modelCurves;
			this.sigmaModel = sigmaModel;
			this.truncLevel = truncLevel;
		}

		@Override public HazardCurves apply(final HazardGroundMotions groundMotions) {

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

	static final class CurveConsolidator implements Function<List<HazardCurves>, HazardCurveSet> {

		private final Map<Imt, ArrayXY_Sequence> modelCurves;
		private final SourceSet<? extends Source> sourceSet;

		CurveConsolidator(
			final SourceSet<? extends Source> sourceSet,
			final Map<Imt, ArrayXY_Sequence> modelCurves) {

			this.sourceSet = sourceSet;
			this.modelCurves = modelCurves;
		}

		@Override public HazardCurveSet apply(final List<HazardCurves> curvesList) {

			HazardCurveSet.Builder curveSetBuilder = HazardCurveSet.builder(sourceSet, modelCurves);

			for (HazardCurves curves : curvesList) {
				curveSetBuilder.addCurves(curves);
			}
			return curveSetBuilder.build();
		}
	}

	static final class CurveSetConsolidator implements Function<List<HazardCurveSet>, HazardResult> {

		private final Map<Imt, ArrayXY_Sequence> modelCurves;
		private final Site site;

		CurveSetConsolidator(final Map<Imt, ArrayXY_Sequence> modelCurves, final Site site) {
			this.modelCurves = modelCurves;
			this.site = site;
		}

		@Override public HazardResult apply(final List<HazardCurveSet> curveSetList) {

			HazardResult.Builder resultBuilder = HazardResult.builder(modelCurves, site);

			for (HazardCurveSet curves : curveSetList) {
				resultBuilder.addCurveSet(curves);
			}
			return resultBuilder.build();
		}
	}

	static final class ClusterSourceToInputs implements Function<ClusterSource, ClusterInputs> {

		private final SourceToInputs transform;

		ClusterSourceToInputs(final Site site) {
			transform = new SourceToInputs(site);
		}

		@Override public ClusterInputs apply(final ClusterSource clusterSource) {
			ClusterInputs clusterInputs = new ClusterInputs(clusterSource);
			for (FaultSource faultSource : clusterSource.faults()) {
				clusterInputs.add(transform.apply(faultSource));
			}
			return clusterInputs;
		}
	}

	static final class ClusterInputsToGroundMotions implements
			Function<ClusterInputs, ClusterGroundMotions> {

		private final InputsToGroundMotions transform;

		ClusterInputsToGroundMotions(final Table<Gmm, Imt, GroundMotionModel> gmmInstances) {
			transform = new InputsToGroundMotions(gmmInstances);
		}

		@Override public ClusterGroundMotions apply(final ClusterInputs clusterInputs) {
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
	static final class ClusterGroundMotionsToCurves implements
			Function<ClusterGroundMotions, ClusterCurves> {

		private final Map<Imt, ArrayXY_Sequence> modelCurves;
		private final ExceedanceModel sigmaModel;
		private final double truncLevel;

		ClusterGroundMotionsToCurves(
			final Map<Imt, ArrayXY_Sequence> modelCurves,
			final ExceedanceModel sigmaModel,
			final double truncLevel) {

			this.modelCurves = modelCurves;
			this.sigmaModel = sigmaModel;
			this.truncLevel = truncLevel;
		}

		@Override public ClusterCurves apply(final ClusterGroundMotions clusterGroundMotions) {

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
							// setProbExceed(means.get(i), sigmas.get(i),
							// utilCurve,
							// TRUNCATION_UPPER_ONLY, 3.0);
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

	static final class ClusterCurveConsolidator implements
			Function<List<ClusterCurves>, HazardCurveSet> {

		private final Map<Imt, ArrayXY_Sequence> modelCurves;
		private final ClusterSourceSet clusterSourceSet;

		ClusterCurveConsolidator(
			final ClusterSourceSet clusterSourceSet,
			final Map<Imt, ArrayXY_Sequence> modelCurves) {

			this.clusterSourceSet = clusterSourceSet;
			this.modelCurves = modelCurves;
		}

		@Override public HazardCurveSet apply(final List<ClusterCurves> curvesList) {

			HazardCurveSet.Builder curveSetBuilder = HazardCurveSet.builder(clusterSourceSet,
				modelCurves);

			for (ClusterCurves curves : curvesList) {
				curveSetBuilder.addCurves(curves);
			}
			return curveSetBuilder.build();
		}
	}

}
