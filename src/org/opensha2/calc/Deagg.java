package org.opensha2.calc;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Queue;
import java.util.Set;

import org.opensha2.calc.CalcConfig.DeaggData;
import org.opensha2.data.DataTable;
import org.opensha2.data.DataUtils;
import org.opensha2.data.DataVolume;
import org.opensha2.eq.Magnitudes;
import org.opensha2.eq.model.GmmSet;
import org.opensha2.eq.model.Rupture;
import org.opensha2.eq.model.Source;
import org.opensha2.eq.model.SourceSet;
import org.opensha2.eq.model.SourceType;
import org.opensha2.gmm.Gmm;
import org.opensha2.gmm.Imt;

import com.google.common.base.Function;
import com.google.common.collect.Maps;
import com.google.common.collect.MinMaxPriorityQueue;
import com.google.common.collect.Ordering;
import com.google.common.collect.Range;

/**
 * For one (or each Imt) One Deagg per source set and ground motion model these
 * are then combined for total deagg and also combined across each unique gmm
 * 
 * @author Peter Powers
 */
class Deagg {

	// private final DataModel model;
	// private final double[][][] data;
	// private final double mBar, rBar, εBar;

	/*
	 * Many deagg bins have no data so result is a sparse matrix
	 * 
	 * TODO track and report ranked source set contributions TODO track and
	 * report ranked sources; may have source with same name in different
	 * sourceSets
	 */

	// do we want to track the relative location in each distance bin:
	// i.e. the bin plots at the contribution weighted distance
	// private Comparator<ContributingRupture> comparator = Ordering.natural();

	private Queue<Contribution> contribQueue = MinMaxPriorityQueue
		.orderedBy(Ordering.natural())
		.maximumSize(20)
		.create();

	/* Wrapper class for a Rupture and it's contribution to hazard. */
	static class Contribution implements Comparable<Contribution> {

		final Source rupture = null;
		final Double value = null;

		@Override public int compareTo(Contribution o) {
			return value.compareTo(o.value);
		}
	}

	/*
	 * TODO need data table and volumne copyof constructors that point to same
	 * dimension arrays for streamlined validation when adding, multiplying etc.
	 */

	static Deaggregator of(HazardCurveSet hazard) {
		return new Deaggregator(hazard);
	}

	/* Builder pattern */
	static class Deaggregator {

		private final HazardCurveSet hazard;
		private final Set<Gmm> gmms;
		// private final SourceSet<? extends Source> sourceSet;
		// private final double sourceSetWeight;

		// private Model model;
		private Data.Model modelData;

		private Imt imt;
		private double iml;
		private double totalRate;

		private Map<Gmm, Data.Builder> dataMap;

		private Deaggregator(HazardCurveSet hazard) {
			this.hazard = hazard;
			this.gmms = hazard.sourceSet.groundMotionModels().gmms();
			// this.sourceSet = sourceSet
			// this.sourceSetWeight = hazard.sourceSet.weight();
		}

		Deaggregator withDataModel(Data.Model model) {
			this.modelData = checkNotNull(model);
			this.dataMap = createDataMap(gmms, model);
			return this;
		}

		// TODO these need to be build as copies of a base model
		private static Map<Gmm, Data.Builder> createDataMap(Set<Gmm> gmms, Data.Model model) {
			Map<Gmm, Data.Builder> map = Maps.newEnumMap(Gmm.class);
			for (Gmm gmm : gmms) {
				map.put(gmm, Data.builder(model));
			}
			return Maps.immutableEnumMap(map);
		}

		Deaggregator forImt(Imt imt) {
			// check valid imt agains HCS
			this.imt = imt;
			return this;
		}

		Deaggregator atIml(double iml, double totalRate) {
			// check valid iml agains curve x-range for imt??
			this.iml = iml;
			this.totalRate = totalRate;
			return this;
		}

		Map<Gmm, Data> deaggregate() {
			// TODO check required fields set

			for (GroundMotions gms : hazard.hazardGroundMotionsList) {

				InputList inputs = gms.inputs;
				Map<Gmm, List<Double>> means = gms.means.get(imt);
				Map<Gmm, List<Double>> sigmas = gms.sigmas.get(imt);

				double rKey = inputs.minDistance;
				Map<Gmm, Double> gmmMap = hazard.sourceSet.groundMotionModels().gmmWeightMap(rKey);

//				processSource(inputs, means, sigmas);
			}
			return null;
		}

		private void processSource(
				InputList inputs,
				Map<Gmm, Double> gmms,
				Map<Gmm, List<Double>> μLists,
				Map<Gmm, List<Double>> σLists,
				ExceedanceModel exceedanceModel,
				double sourceSetWeight) {

			// SourceInputList inputs = (SourceInputList) groundMotions.inputs;
			// String sourceName = inputs.parent.name();
			double sourceRate = 0.0;
			// int inputCount = inputs.size();

			for (int i = 0; i < inputs.size(); i++) {

				HazardInput in = inputs.get(i);

				// need to get first? builder
				// need reference to model
				int rIndex = -1;
				int mIndex = -1;

				double rRup = in.rRup;
				double Mw = in.Mw;

				for (Gmm gmm : gmms.keySet()) {

					double gmmWeight = gmms.get(gmm);

					double μ = μLists.get(gmm).get(i);
					double σ = σLists.get(gmm).get(i);
					double ε = epsilon(μ, σ, iml);

					double rateAtIml = exceedanceModel.exceedance(μ, σ, trunc, imt, iml);
					double rate = rateAtIml * in.rate * sourceSetWeight * gmmWeight;

					int εIndex = -1;

					dataMap.get(gmm).add(
						rIndex, mIndex, εIndex,
						rRup * rate, Mw * rate, ε * rate,
						rate);
				}

			}

			// for (Gmm gmm : groundMotions.means.get(imt).keySet()) {
			// List<Double> μList = groundMotions.means.get(imt).get(gmm);
			// List<Double> σList = groundMotions.sigmas.get(imt).get(gmm);
			//
			// // double distance = groundMotions.inputs.minDistance;
			// double gmmWeight = gmmSet.gmmWeightMap(distance).get(gmm);
			//
			// for (int i = 0; i < inputCount; i++) {
			// HazardInput in = groundMotions.inputs.get(i);
			//
			// double μ = μList.get(i);
			// double σ = σList.get(i);
			// double ε = epsilon(μ, σ, iml);
			//
			// double probAtIml = SIGMA.exceedance(μ, σ, trunc, imt, iml);
			// double rate = probAtIml * in.rate * sourceSetWeight * gmmWeight;
			// sourceRate += rate;
			// // addRupture(in.Mw, in.rRup, ε, rate);
			// }
			// }

		}

		// TODO get us from CalcConfig
		private static final ExceedanceModel SIGMA = ExceedanceModel.TRUNCATION_UPPER_ONLY;
		private static final double trunc = 3.0;

		private void processFaultSourceSet(HazardCurveSet curveSet, Imt imt, double iml) {

			double sourceSetWeight = curveSet.sourceSet.weight();
			GmmSet gmmSet = curveSet.sourceSet.groundMotionModels();

			for (GroundMotions groundMotions : curveSet.hazardGroundMotionsList) {
				processFaultSource(groundMotions, sourceSetWeight, gmmSet, imt, iml);
			}
		}

		/*
		 * loop gmms inside inputs so that correct weight map, and hence correct
		 * gmm deagg data, are added to; what is done when computing hazard?
		 * filter on source or rupture/input
		 */
		private void processFaultSource(
				GroundMotions groundMotions,
				double sourceSetWeight,
				GmmSet gmmSet,
				Imt imt,
				double iml) {

			SourceInputList inputs = (SourceInputList) groundMotions.inputs;
			String sourceName = inputs.parent.name();
			double sourceRate = 0.0;
			int inputCount = inputs.size();

			for (Gmm gmm : groundMotions.means.get(imt).keySet()) {
				List<Double> μList = groundMotions.means.get(imt).get(gmm);
				List<Double> σList = groundMotions.sigmas.get(imt).get(gmm);

				double distance = groundMotions.inputs.minDistance;
				double gmmWeight = gmmSet.gmmWeightMap(distance).get(gmm);

				for (int i = 0; i < inputCount; i++) {
					HazardInput in = groundMotions.inputs.get(i);

					double μ = μList.get(i);
					double σ = σList.get(i);
					double ε = epsilon(μ, σ, iml);

					double probAtIml = SIGMA.exceedance(μ, σ, trunc, imt, iml);
					double rate = probAtIml * in.rate * sourceSetWeight * gmmWeight;
					sourceRate += rate;
					// addRupture(in.Mw, in.rRup, ε, rate);
				}
			}
		}

	}

	private static int index(double min, double binWidth, double value) {
		return (int) Math.floor((value - min) / binWidth);
	}

	// should all be in log space
	private static double epsilon(double μ, double σ, double iml) {
		return (iml - μ) / σ;
	}

	private static final Range<Double> rRange = Range.closed(0.0, 1000.0);
	private static final Range<Double> εRange = Range.closed(-3.0, 3.0);

	private static int size(double min, double max, double Δ) {
		return (int) Math.rint((max - min) / Δ);
	}

	/**
	 * Deaggregation data container. This class is used to store deaggregation
	 * results of individual SourceSets and Gmms. Data containers may be
	 * recombined via add().
	 */
	static class Data {

		private final DataVolume rmε;

		/* Weighted mean contributions */
		private final double rBar, mBar, εBar;
		private final double barWeight;

		/* Weighted r and m position data */
		private final DataTable rPositions;
		private final DataTable mPositions;
		private final DataTable positionWeights;

		// private Map<SourceSet<Source>, Collection<Source>> topContributors;

		private Data(
				DataVolume rmε,
				double rBar, double mBar, double εBar,
				double barWeight,
				DataTable rPositions,
				DataTable mPositions,
				DataTable positionWeights) {

			this.rmε = rmε;

			this.rBar = rBar;
			this.mBar = mBar;
			this.εBar = εBar;
			this.barWeight = barWeight;

			this.rPositions = rPositions;
			this.mPositions = mPositions;
			this.positionWeights = positionWeights;
		}

		/**
		 * Initialize a builder using a data model that will likely have been
		 * specified cia a calculation configuration.
		 */
		static Builder builder(Data.Model model) {
			return new Builder(model);
		}

		/**
		 * Initialize a builder using an existing data object whose immutable
		 * structural properties will be shared (e.g. row and column arrays of
		 * data tables).
		 */
		static Builder builder(Data model) {
			return null;
		}

		static class Builder {

			private static final String ID = "Deagg.Data.Builder";

			private DataVolume.Builder rmε;

			/* Weighted mean contributions */
			private double rBar, mBar, εBar;
			private double barWeight;

			/* Weighted r and m position data */
			private DataTable.Builder rPositions;
			private DataTable.Builder mPositions;
			private DataTable.Builder positionWeights;

			// private Map<SourceSet<Source>, Collection<Source>>
			// topContributors;

			private Builder(Model model) {
				rmε = DataVolume.Builder.create()
					.rows(model.rMin, model.rMax, model.Δr)
					.columns(model.mMin, model.mMax, model.Δm)
					.levels(model.εMin, model.εMax, model.Δε);
				rPositions = DataTable.Builder.create()
					.rows(model.rMin, model.rMax, model.Δr)
					.columns(model.mMin, model.mMax, model.Δm);
				mPositions = DataTable.Builder.create()
					.rows(model.rMin, model.rMax, model.Δr)
					.columns(model.mMin, model.mMax, model.Δm);
				positionWeights = DataTable.Builder.create()
					.rows(model.rMin, model.rMax, model.Δr)
					.columns(model.mMin, model.mMax, model.Δm);
			}
			
			private Builder(Data model) {
				rmε = DataVolume.Builder.fromModel(model.rmε);
				rPositions = DataTable.Builder.fromModel(model.rPositions);
				mPositions = DataTable.Builder.fromModel(model.mPositions);
				positionWeights = DataTable.Builder.fromModel(model.positionWeights);
			}

			/*
			 * Populate Data object with rupture data. Supply DataTable and
			 * DataVolume indices, distance, magnitude, and epsilon (weighted by
			 * rate), and the rate of the rupture.
			 * 
			 * Although we could work with the raw distance, magnitude and
			 * epsilon values, deaggregation is being performed across each Gmm,
			 * so precomputing indices and weighted values in the calling method
			 * brings some efficiency.
			 */
			Builder add(
					int ri, int mi, int εi,
					double rw, double mw, double εw,
					double rate) {

				rmε.set(ri, mi, εi, rate);

				rBar += rw;
				mBar += mw;
				εBar += εw;
				barWeight += rate;

				rPositions.add(ri, mi, rw);
				mPositions.add(ri, mi, mw);
				positionWeights.add(ri, mi, rate);

				return this;
			}

			/* Combine values */
			Builder add(Data data) {

				rmε.add(data.rmε);

				rBar += data.rBar;
				mBar += data.mBar;
				εBar += data.εBar;
				barWeight += barWeight;

				rPositions.add(data.rPositions);
				mPositions.add(data.mPositions);
				positionWeights.add(data.positionWeights);

				return this;
			}

			Data build() {
				return new Data(
					rmε.build(), rBar, mBar, εBar, barWeight,
					rPositions.build(), mPositions.build(), positionWeights.build());
			}
		}

		/**
		 * Data model class specifies the binning structure to be used for a
		 * deaggregation.
		 */
		public static class Model {

			// TODO could this carry other deagg calc properties along with it ?
			// ostensibly new Data objects will be crated on every calculation

			private final double rMin, rMax, Δr;
			private final double mMin, mMax, Δm;
			private final double εMin, εMax, Δε;

			private Model(
					double rMin, double rMax, double Δr,
					double mMin, double mMax, double Δm,
					double εMin, double εMax, double Δε) {

				this.rMin = rMin;
				this.rMax = rMax;
				this.Δr = Δr;

				this.mMin = mMin;
				this.mMax = mMax;
				this.Δm = Δm;

				this.εMin = εMin;
				this.εMax = εMax;
				this.Δε = Δε;
			}

			/**
			 * Create a deaggregation data model from the supplied
			 * {@code CalcConfig}.
			 * 
			 * @param config to process
			 */
			public static Model fromConfig(CalcConfig config) {
				DeaggData d = config.deagg;
				return create(
					d.rMin, d.rMax, d.Δr,
					d.mMin, d.mMax, d.Δm,
					d.εMin, d.εMax, d.Δε);
			}

			/**
			 * Create a deaggregation data model. Deaggregation data bins are
			 * anchored on the {@code min} values supplied. {@code max} values
			 * may not correspond to final upper edge of uppermost bins if
			 * {@code max - min} is not evenly divisible by {@code Δ}.
			 * 
			 * @param rMin lower edge of lowest distance bin
			 * @param rMax upper edge of highest distance bin
			 * @param Δr distance bin discretization
			 * @param mMin lower edge of lowest magnitude bin
			 * @param mMax upper edge of highest magnitude bin
			 * @param Δm magnitude bin discretization
			 * @param εMin lower edge of lowest epsilon bin
			 * @param εMax upper edge of highest epsilon bin
			 * @param Δε epsilon bin discretization
			 */
			public static Model create(
					double rMin, double rMax, double Δr,
					double mMin, double mMax, double Δm,
					double εMin, double εMax, double Δε) {

				return new Builder()
					.distanceDiscretization(rMin, rMax, Δr)
					.magnitudeDiscretization(mMin, mMax, Δm)
					.epsilonDiscretization(εMin, εMax, Δε)
					.build();
			}

			private static class Builder {

				private static final String ID = "Deagg.Data.Model.Builder";
				private boolean built = false;

				private Double rMin, rMax, Δr;
				private Double mMin, mMax, Δm;
				private Double εMin, εMax, Δε;

				private Builder distanceDiscretization(double min, double max, double Δ) {
					rMin = DataUtils.checkInRange(rRange, "Min distance", min);
					rMax = DataUtils.checkInRange(rRange, "Max distance", max);
					Δr = DataUtils.validateDelta(min, max, Δ);
					return this;
				}

				private Builder magnitudeDiscretization(double min, double max, double Δ) {
					mMin = Magnitudes.validateMag(min);
					mMax = Magnitudes.validateMag(max);
					Δm = DataUtils.validateDelta(min, max, Δ);
					return this;
				}

				private Builder epsilonDiscretization(double min, double max, double Δ) {
					εMin = DataUtils.checkInRange(εRange, "Min epsilon", min);
					εMax = DataUtils.checkInRange(εRange, "Max epsilon", max);
					Δε = DataUtils.validateDelta(min, max, Δ);
					return this;
				}

				private Model build() {
					validateState(ID);
					return new Model(rMin, rMax, Δr, mMin, mMax, Δm, εMin, εMax, Δε);
				}

				private void validateState(String id) {
					checkState(!built, "This %s instance as already been used", id);
					checkState(rMin != null, "%s distance discretization not set", id);
					checkState(mMin != null, "%s magnitude discretization not set", id);
					checkState(εMin != null, "%s epsilon discretization not set", id);
					built = true;
				}
			}
		}

	}

}
