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
import com.google.common.collect.MinMaxPriorityQueue;
import com.google.common.collect.Ordering;
import com.google.common.collect.Range;

/**
 * For one (or each Imt) One Deagg per source set and ground motion model these
 * are then combined for total deagg and also combined across each unique gmm
 *
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

		static Builder builder(Model model) {
			return new Builder(model);
		}
		
		static class Builder {
			
			private DataVolume.Builder rmε;

			/* Weighted mean contributions */
			private double rBar, mBar, εBar;
			private double barWeight;

			/* Weighted r and m position data */
			private DataTable.Builder rPositions;
			private DataTable.Builder mPositions;
			private DataTable.Builder positionWeights;

			// private Map<SourceSet<Source>, Collection<Source>> topContributors;
	
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
			
			/*
			 * Populate Data object with rupture data. Supply DataTable and
			 * DataVolume indices, weighted (by rate) distance, magnitude, and
			 * epsilon, and the rate of the rupture.
			 * 
			 * Although we could work with the raw distance, magnitude and epsilon
			 * values, deaggregation is being performed across each Gmm, so
			 * precomputing indices and weighted values in the calling method brings
			 * some efficiency.
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

//			Builder add(Data data) {
//				// DataUtils.add(this.mrεMatrix, data.mrεMatrix);
//				this.mBar += data.mBar;
//				this.rBar += data.rBar;
//				this.εBar += data.εBar;
//				this.barWeight += barWeight;
//				DataUtils.add(this.mPosValues, data.mPosValues);
//				DataUtils.add(this.rPosValues, data.rPosValues);
//				DataUtils.add(this.mrPosWeights, data.mrPosWeights);
//				topContributors.putAll(data.topContributors);
//			}
//
//			Data build() {
//				
//			}
		}
		
		
		

	}

	static Deaggregator of(HazardCurveSet hazard) {
		return new Deaggregator(hazard);
	}

	static class Deaggregator {

		private HazardCurveSet hazard;
		private Model model;
		private Imt imt;
		private double iml;

		private double[][][] data; // [R][M][ε]

		private double mBar, rBar, εBar; // these are total
		private double totalRate; // TODO compare to orignal PoE
		private double totalRateWithinRange;

		// wieghted m and r position data
		private double[][] mValues;
		private double[][] rValues;
		private double[][] mrWeights;

		private Deaggregator(HazardCurveSet hazard) {
			this.hazard = hazard;
		}

		Deaggregator withDataModel(Model model) {
			this.model = checkNotNull(model);
			return this;
		}

		Deaggregator andTotalRate(double totalRate) {
			this.totalRate = totalRate;
			return this;
		}

		Deaggregator forImt(Imt imt) {
			// check valid imt agains HCS
			this.imt = imt;
			return this;
		}

		Deaggregator atIml(double iml) {
			// check valid iml agains curve x-range for imt??
			this.iml = iml;
			return this;
		}

		Map<Gmm, Data> deaggregate() {

			for (GroundMotions gms : hazard.hazardGroundMotionsList) {
				InputList inputs = gms.inputs;
				Map<Gmm, List<Double>> means = gms.means.get(imt);
				Map<Gmm, List<Double>> sigmas = gms.sigmas.get(imt);
				processSource(inputs, means, sigmas);
			}
			return null;
		}

		private void processSource(
				InputList inputs,
				Map<Gmm, List<Double>> means,
				Map<Gmm, List<Double>> sigmas) {

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
					addRupture(in.Mw, in.rRup, ε, rate);
				}
			}
		}

		private void addRupture(double m, double r, double ε, double rate) {

			double mr = m * rate;
			double rr = r * rate;
			double εr = ε * rate;

			int im = index(model.mMin, model.Δm, m);
			int ir = index(model.rMin, model.Δr, r);
			int iε = index(model.εMin, model.Δε, ε);

			mBar += mr;
			rBar += rr;
			εBar += εr;
			totalRate += rate;

			data[im][ir][iε] += rate;

			mValues[im][ir] += mr;
			rValues[im][ir] += rr;
			mrWeights[im][ir] += rate;
		}

		/*
		 * Populate Data object with rupture data. Supply DataTable and
		 * DataVolume indices, weighted (by rate) distance, magnitude, and
		 * epsilon, and the rate of the rupture.
		 * 
		 * Although we could work with the raw distance, magnitude and epsilon
		 * values, deaggregation is being performed across each Gmm, so
		 * precomputing indices and weighted values in the calling method brings
		 * some efficiency.
		 */
//		private static void addRupture(
//				Data data,
//				int ri, int mi, int εi,
//				double rw, double mw, double εw,
//				double rate) {
//
//			data.rmε.set(ri, mi, εi, rate);
//			
//			data.rBar += rw;
//			data.mBar += mw;
//			data.εBar += εw;
//			data.barWeight += rate;
//
//			data.rPositions.add(ri, mi, rw);
//			data.mPositions.add(ri, mi, mw);
//			data.positionWeights.add(ri, mi, rate);
//		}
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

	public static class Model {

		private final double rMin, rMax, Δr;
		private final double mMin, mMax, Δm;
		private final double εMin, εMax, Δε;

		private final int rSize, mSize, εSize;

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

			// TODO needed?
			rSize = size(rMin, rMax, Δr);
			mSize = size(mMin, mMax, Δm);
			εSize = size(εMin, εMax, Δε);
		}

		/**
		 * Create a deaggregation data model from the supplied
		 * {@code CalcConfig}.
		 * @param c {@code CalcConfig} to process
		 */
		public static Model fromConfig(CalcConfig c) {
			DeaggData d = c.deagg;
			return create(
				d.rMin, d.rMax, d.Δr,
				d.mMin, d.mMax, d.Δm,
				d.εMin, d.εMax, d.Δε);
		}

		/**
		 * Create a deaggregation data model. Deaggregation data bins are
		 * anchored on the {@code min} values supplied. {@code max} values may
		 * not correspond to final upper edge of uppermost bins if
		 * {@code max - min} is not evenly divisible by {@code Δ}.
		 * 
		 * @param rMin lower edge of lowest distance bin
		 * @param rMax
		 * @param Δr
		 * @param mMin lower edge of lowest magnitude bin
		 * @param mMax maximum magnitude
		 * @param Δm
		 * @param εMin lower edge of lowest epsilon bin
		 * @param εMax
		 * @param Δε
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

			private static final String ID = "Deagg.DataModel.Builder";
			private boolean built = false;

			private Double rMin, rMax, Δr;
			private Double mMin, mMax, Δm;
			private Double εMin, εMax, Δε;

			private Builder distanceDiscretization(double min, double max, double Δ) {
				rMin = DataUtils.validate(rRange, "Min distance", min);
				rMax = DataUtils.validate(rRange, "Max distance", max);
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
				εMin = DataUtils.validate(εRange, "Min epsilon", min);
				εMax = DataUtils.validate(εRange, "Max epsilon", max);
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
