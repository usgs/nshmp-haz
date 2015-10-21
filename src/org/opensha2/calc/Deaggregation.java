package org.opensha2.calc;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.opensha2.data.Data.checkInRange;
import static org.opensha2.eq.Magnitudes.checkMagnitude;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import org.opensha2.calc.CalcConfig.DeaggData;
import org.opensha2.data.DataTable;
import org.opensha2.data.DataTables;
import org.opensha2.data.DataVolume;
import org.opensha2.eq.Magnitudes;
import org.opensha2.eq.model.GmmSet;
import org.opensha2.eq.model.Source;
import org.opensha2.eq.model.SourceSet;
import org.opensha2.gmm.Gmm;
import org.opensha2.gmm.Imt;

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
final class Deaggregation {

	
	private final HazardResult hazard;
	private final double returnPeriod;
	
	private Deaggregation(HazardResult hazard, double returnPeriod) {
		this.hazard = hazard;
		this.returnPeriod = returnPeriod;
	}
	
//	public static DeaggResult process(HazardResult hazard, double returnPeriod) {
//		Deagg deagg = new Deagg(hazard, returnPeriod);
//		
//		for ()
//	}
	
	
	/*
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
	
	

	/* Create a new deaggregator. */
	private static Deaggregator of(HazardCurveSet hazard) {
		return new Deaggregator(hazard);
	}

	/* Builder pattern */
	private static class Deaggregator {

		private final HazardCurveSet hazard;
		private final SourceSet<? extends Source> sources;
		private final GmmSet gmmSet;

		/*
		 * Empty 'model' is used to initialize all builders; also used for
		 * distance and magnitude index lookups in processSource()
		 */
		private Dataset model;
		private Map<Gmm, Dataset.Builder> dataMap;

		private Imt imt;
		private double iml;
		private double totalRate;

		private Deaggregator(HazardCurveSet hazard) {
			this.hazard = hazard;
			this.sources = hazard.sourceSet;
			this.gmmSet = sources.groundMotionModels();
		}

		Deaggregator withDataModel(Dataset model) {
			this.model = checkNotNull(model);
			this.dataMap = createDataMap(gmmSet.gmms(), model);
			return this;
		}

		private static Map<Gmm, Dataset.Builder> createDataMap(Set<Gmm> gmms, Dataset model) {
			Map<Gmm, Dataset.Builder> map = Maps.newEnumMap(Gmm.class);
			for (Gmm gmm : gmms) {
				map.put(gmm, Dataset.builder(model));
			}
			return Maps.immutableEnumMap(map);
		}

		Deaggregator forImt(Imt imt) {
			// TODO check valid imt agains HCS
			this.imt = imt;
			return this;
		}

		Deaggregator atIml(double iml, double totalRate) {
			// TODO check valid iml agains curve x-range for imt??
			this.iml = iml;
			this.totalRate = totalRate;
			return this;
		}

		Map<Gmm, Dataset> deaggregate() {

			// TODO check required fields set; preflight

			for (GroundMotions gms : hazard.hazardGroundMotionsList) {
				InputList inputs = gms.inputs;
				Map<Gmm, List<Double>> μLists = gms.means.get(imt);
				Map<Gmm, List<Double>> σLists = gms.sigmas.get(imt);
				Map<Gmm, Double> gmms = gmmSet.gmmWeightMap(inputs.minDistance);
				processSource(inputs, gmms, μLists, σLists, EXCEEDANCE);
			}
			return null;
		}

		private double processSource(
				InputList inputs,
				Map<Gmm, Double> gmms,
				Map<Gmm, List<Double>> μLists,
				Map<Gmm, List<Double>> σLists,
				ExceedanceModel exceedanceModel) {

			double sourceRate = 0.0;

			// local key reference avoids repeat calls to keySet()
			final Set<Gmm> gmmKeys = EnumSet.copyOf(gmms.keySet());

			for (int i = 0; i < inputs.size(); i++) {

				HazardInput in = inputs.get(i);
				double rRup = in.rRup;
				double Mw = in.Mw;

				int rIndex = model.distanceIndex(rRup);
				int mIndex = model.magnitudeIndex(Mw);

				for (Gmm gmm : gmmKeys) {

					double gmmWeight = gmms.get(gmm);

					double μ = μLists.get(gmm).get(i);
					double σ = σLists.get(gmm).get(i);
					double ε = epsilon(μ, σ, iml);

					double probAtIml = exceedanceModel.exceedance(μ, σ, trunc, imt, iml);
					double rate = probAtIml * in.rate * sources.weight() * gmmWeight;
					sourceRate += rate;

					int εIndex = model.epsilonIndex(ε);

					dataMap.get(gmm).add(
						rIndex, mIndex, εIndex,
						rRup * rate, Mw * rate, ε * rate,
						rate);
				}
			}
			return sourceRate;
		}

		// TODO get us from CalcConfig
		private static final ExceedanceModel EXCEEDANCE = ExceedanceModel.TRUNCATION_UPPER_ONLY;
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

					double probAtIml = EXCEEDANCE.exceedance(μ, σ, trunc, imt, iml);
					double rate = probAtIml * in.rate * sourceSetWeight * gmmWeight;
					sourceRate += rate;
					// addRupture(in.Mw, in.rRup, ε, rate);
				}
			}
		}

	}

	private static double epsilon(double μ, double σ, double iml) {
		return (iml - μ) / σ;
	}

	private static final Range<Double> rRange = Range.closed(0.0, 1000.0);
	private static final Range<Double> εRange = Range.closed(-3.0, 3.0);

	/*
	 * Deaggregation dataset that stores deaggregation results of individual
	 * SourceSets and Gmms. Datasets may be recombined via add().
	 */
	static class Dataset {

		private final DataVolume rmε;

		/* Weighted mean contributions */
		private final double rBar, mBar, εBar;
		private final double barWeight;

		/* Weighted r and m position data */
		private final DataTable rPositions;
		private final DataTable mPositions;
		private final DataTable positionWeights;

		// private Map<SourceSet<Source>, Collection<Source>> topContributors;

		private Dataset(
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

		/*
		 * Index methods delegate to the same method used to initialize internal
		 * data tables and volumes.
		 */

		/**
		 * Return the internal bin index of the supplied distance, {@code r}.
		 * @param r distance for which to compute index
		 * @throws IllegalArgumentException if {@code r} is out of deaggregation
		 *         bounds
		 */
		int distanceIndex(double r) {
			return DataTables.indexOf(rmε.rowMin(), rmε.rowΔ(), r, rmε.rowCount());
		}

		/**
		 * Return the internal bin index of the supplied magnitude, {@code m}.
		 * @param m magnitude for which to compute index
		 * @throws IllegalArgumentException if {@code m} is out of deaggregation
		 *         bounds
		 */
		int magnitudeIndex(double m) {
			return DataTables.indexOf(rmε.columnMin(), rmε.columnΔ(), m, rmε.columnCount());
		}

		/**
		 * Return the internal bin index of the supplied epsilon, {@code ε}.
		 * @param ε epsilon for which to compute index
		 * @throws IllegalArgumentException if {@code ε} is out of deaggregation
		 *         bounds
		 */
		int epsilonIndex(double ε) {
			return DataTables.indexOf(rmε.levelMin(), rmε.levelΔ(), ε, rmε.levelCount());
		}

		/**
		 * Initialize a deaggregation dataset builder using an existing dataset
		 * whose immutable structural properties will be shared (e.g. row and
		 * column arrays of data tables).
		 * 
		 * @param model to mirror
		 */
		static Builder builder(Dataset model) {
			return new Builder(model);
		}

		/**
		 * Initialize a deaggregation dataset builder from the settings in a
		 * calculation configuration. Method delegates to
		 * {@link #builder(double, double, double, double, double, double, double, double, double)}
		 * .
		 * 
		 * @param config to process
		 * @see CalcConfig
		 */
		static Builder builder(CalcConfig config) {
			DeaggData d = config.deagg;
			return builder(
				d.rMin, d.rMax, d.Δr,
				d.mMin, d.mMax, d.Δm,
				d.εMin, d.εMax, d.Δε);
		}

		/**
		 * Initialize a deaggregation dataset builder.
		 * 
		 * @param rMin lower edge of lowermost distance bin
		 * @param rMax upper edge of uppermost distance bin
		 * @param Δr distance bin discretization
		 * @param mMin lower edge of lowermost magnitude bin
		 * @param mMax upper edge of uppermost magnitude bin
		 * @param Δm magnitude bin discretization
		 * @param εMin lower edge of lowermost epsilon bin
		 * @param εMax upper edge of uppermost epsilon bin
		 * @param Δε epsilon bin discretization
		 */
		static Builder builder(
				double rMin, double rMax, double Δr,
				double mMin, double mMax, double Δm,
				double εMin, double εMax, double Δε) {

			/*
			 * Dataset fields (data tables and volumes) validate deltas relative
			 * to min and max supplied; we only check ranges here.
			 */
			return new Builder(
				checkInRange(rRange, "Min distance", rMin),
				checkInRange(rRange, "Min distance", rMax),
				Δr,
				checkMagnitude(mMin),
				checkMagnitude(mMax),
				Δm,
				checkInRange(εRange, "Min epsilon", εMin),
				checkInRange(εRange, "Max epsilon", εMax),
				Δε);
		}

		static class Builder {

			private static final String ID = "Deagg.Dataset.Builder";

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

			private Builder(
					double rMin, double rMax, double Δr,
					double mMin, double mMax, double Δm,
					double εMin, double εMax, double Δε) {

				rmε = DataVolume.Builder.create()
					.rows(
						checkInRange(rRange, "Min distance", rMin),
						checkInRange(rRange, "Max distance", rMax),
						Δr)
					.columns(
						Magnitudes.checkMagnitude(mMin),
						Magnitudes.checkMagnitude(mMax),
						Δm)
					.levels(
						checkInRange(εRange, "Min epsilon", εMin),
						checkInRange(εRange, "Max epsilon", εMax),
						Δε);

				rmε = DataVolume.Builder.create()
					.rows(rMin, rMax, Δr)
					.columns(mMin, mMax, Δm)
					.levels(εMin, εMax, Δε);
				rPositions = DataTable.Builder.create()
					.rows(rMin, rMax, Δr)
					.columns(mMin, mMax, Δm);
				mPositions = DataTable.Builder.create()
					.rows(rMin, rMax, Δr)
					.columns(mMin, mMax, Δm);
				positionWeights = DataTable.Builder.create()
					.rows(rMin, rMax, Δr)
					.columns(mMin, mMax, Δm);
			}

			private Builder(Dataset model) {
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
			Builder add(Dataset data) {

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

			Dataset build() {
				return new Dataset(
					rmε.build(), rBar, mBar, εBar, barWeight,
					rPositions.build(), mPositions.build(), positionWeights.build());
			}
		}

	}

}
