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
import org.opensha2.data.DataUtils;
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
 * Add comments here
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

	/*
	 * Doing single threaded for now THis class may be suitable for using Java 8
	 * DoubleAdder/Accumulators
	 * 
	 * Ignoring Gmm deagg
	 */

	// do we want to track the relative location in each distance bin:
	// i.e. the bin plots at the contribution weighted distance
	// private Comparator<ContributingRupture> comparator = Ordering.natural();

	private Queue<Contribution> contribQueue = MinMaxPriorityQueue.orderedBy(Ordering.natural())
		.maximumSize(20).create();

	/* Wrapper class for a Rupture and it's contribution to hazard. */
	static class Contribution implements Comparable<Contribution> {

		final Source rupture = null;
		final Double value = null;

		@Override public int compareTo(Contribution o) {
			return value.compareTo(o.value);
		}
	}

	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Deaggregation data container. This class is used to store deaggregation
	 * results of individual SourceSets. Data objects may be recombined via
	 * add().
	 */
	static class Data {
		
		private double[][][] mrεMatrix; // [M][R][ε]

		private double mBar, rBar, εBar; // these are total
		private double totalRate; // TODO compare to orignal PoE

		// wieghted m and r position data
		private double[][] mPosValues;
		private double[][] rPosValues;
		private double[][] mrPosWeights;
		
		private Map<SourceSet<Source>, Collection<Source>> topContributors;

		private void add(Data data) {
			DataUtils.add(this.mrεMatrix, data.mrεMatrix);
			this.mBar += data.mBar;
			this.rBar += data.rBar;
			this.εBar += data.εBar;
			this.totalRate += totalRate;
			DataUtils.add(this.mPosValues, data.mPosValues);
			DataUtils.add(this.rPosValues, data.rPosValues);
			DataUtils.add(this.mrPosWeights, data.mrPosWeights);
			topContributors.putAll(data.topContributors);
		}

	}
	
	static class Builder {

		private Model model;
		private HazardResult hazard;
		private double targetIml;

		private double[][][] data; // [M][R][ε]

		private double mBar, rBar, εBar; // these are total
		private double totalRate; // TODO compare to orignal PoE
		private double totalRateWithinRange;

		// wieghted m and r position data
		private double[][] mValues;
		private double[][] rValues;
		private double[][] mrWeights;

		Builder withModel(Model model) {
			this.model = checkNotNull(model);
			return this;
		}

		Builder forHazard(HazardResult hazard) {
			this.hazard = checkNotNull(hazard);
			return this;
		}

		// // TODO need PoE enum
		// Builder targetExceedance(double exceedance) {
		// // TODO convert to iml
		// }
		//
		// Builder targetRate(double rate) {
		// // TODO convert to iml
		// }
		//
		// Builder targetIml(double iml) {
		// // TODO validate iml against curve range
		// // this
		// }

		Deagg build() {
			// run deaggregation
			// build final statistics
			return null;
		}

		private void process() {

			// TODO need IML for target rate or PoE

			// data is buried in maps by Imt, need Imt

			for (SourceType type : hazard.sourceSetMap.keySet()) {
				Set<HazardCurveSet> hazardCurveSets = hazard.sourceSetMap.get(type);
				switch (type) {
					case FAULT:
						// processFaultSources(hazardCurveSets);
				}
			}
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

		private final double mMin, mMax, Δm;
		private final double rMin, rMax, Δr;
		private final double εMin, εMax, Δε;

		private final int mSize, rSize, εSize;

		private Model(double mMin, double mMax, double Δm, double rMin, double rMax, double Δr,
				double εMin, double εMax, double Δε) {

			this.mMin = mMin;
			this.mMax = mMax;
			this.Δm = Δm;
			this.rMin = rMin;
			this.rMax = rMax;
			this.Δr = Δr;
			this.εMin = εMin;
			this.εMax = εMax;
			this.Δε = Δε;

			mSize = size(mMin, mMax, Δm);
			rSize = size(rMin, rMax, Δr);
			εSize = size(εMin, εMax, Δε);
		}

		/**
		 * Create a deaggregation data model from the supplied
		 * {@code CalcConfig}.
		 * @param c {@code CalcConfig} to process
		 */
		public static Model fromConfig(CalcConfig c) {
			DeaggData d = c.deagg;
			return create(d.mMin, d.mMax, d.Δm, d.rMin, d.rMax, d.Δr, d.εMin, d.εMax, d.Δε);
		}

		/**
		 * Create a deaggregation data model. Deaggregation data bins are
		 * anchored on the {@code min} values supplied. {@code max} values may
		 * not correspond to final upper edge of uppermost bins if
		 * {@code max - min} is not evenly divisible by {@code Δ}.
		 * 
		 * @param mMin lower edge of lowest magnitude bin
		 * @param mMax maximum magnitude
		 * @param Δm
		 * @param rMin lower edge of lowest distance bin
		 * @param rMax
		 * @param Δr
		 * @param εMin lower edge of lowest epsilon bin
		 * @param εMax
		 * @param Δε
		 */
		public static Model create(double mMin, double mMax, double Δm, double rMin,
				double rMax, double Δr, double εMin, double εMax, double Δε) {

			return new Builder()
				.magnitudeDiscretization(mMin, mMax, Δm)
				.distanceDiscretization(rMin, rMax, Δr)
				.epsilonDiscretization(εMin, εMax, Δε)
				.build();
		}

		private static class Builder {

			private static final String ID = "Deagg.DataModel.Builder";
			private boolean built = false;

			private Double mMin, mMax, Δm;
			private Double rMin, rMax, Δr;
			private Double εMin, εMax, Δε;

			private Builder magnitudeDiscretization(double min, double max, double Δ) {
				mMin = Magnitudes.validateMag(min);
				mMax = Magnitudes.validateMag(max);
				Δm = DataUtils.validateDelta(min, max, Δ);
				return this;
			}

			private Builder distanceDiscretization(double min, double max, double Δ) {
				rMin = DataUtils.validate(rRange, "Min distance", min);
				rMax = DataUtils.validate(rRange, "Max distance", max);
				Δr = DataUtils.validateDelta(min, max, Δ);
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
				return new Model(mMin, mMax, Δm, rMin, rMax, Δr, εMin, εMax, Δε);
			}

			private void validateState(String id) {
				checkState(!built, "This %s instance as already been used", id);
				checkState(mMin != null, "%s magnitude discretization not set", id);
				checkState(rMin != null, "%s distance discretization not set", id);
				checkState(εMin != null, "%s epsilon discretization not set", id);
				built = true;
			}
		}
	}
}
