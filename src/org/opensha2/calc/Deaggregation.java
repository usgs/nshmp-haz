package org.opensha2.calc;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.opensha2.data.Data.checkInRange;
import static org.opensha2.eq.Magnitudes.checkMagnitude;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;

import org.opensha2.calc.CalcConfig.DeaggData;
import org.opensha2.calc.Deaggregation.Dataset.Builder;
import org.opensha2.data.Data;
import org.opensha2.data.DataTable;
import org.opensha2.data.DataTables;
import org.opensha2.data.DataVolume;
import org.opensha2.data.Interpolator;
import org.opensha2.data.XySequence;
import org.opensha2.eq.Magnitudes;
import org.opensha2.eq.model.GmmSet;
import org.opensha2.eq.model.Source;
import org.opensha2.eq.model.SourceSet;
import org.opensha2.gmm.Gmm;
import org.opensha2.gmm.Imt;

import com.google.common.base.Function;
import com.google.common.base.StandardSystemProperty;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.MinMaxPriorityQueue;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Ordering;
import com.google.common.collect.Range;

/**
 * For one (or each Imt) One Deagg per source set and ground motion model these
 * are then combined for total deagg and also combined across each unique gmm
 * 
 * @author Peter Powers
 */
public final class Deaggregation {

	// TODO get us from CalcConfig
	private static final ExceedanceModel EXCEEDANCE = ExceedanceModel.TRUNCATION_UPPER_ONLY;
	private static final double trunc = 3.0;

	/*
	 * TODO should we preflight against model; specifically, a hazard results
	 * will include sources out to whatever distance is specified by gmm.xml and
	 * mfds will be whatever has been specified; does it make sense to disallow
	 * deaggregation if M and R exceed that which the deagg is capable of
	 * handling?
	 * 
	 * really should have auto deagg scaling; how best to prescan contributing
	 * sources? Could loop source set curves, determine if total rate for ss is
	 * greater than some min cutoff and use remaining source set s to set r and
	 * m binning - also set some minimum? although this could be set on client
	 * 
	 * ss should be able to indicate a magnitude range; r range comes from gmms
	 * 
	 * I think we should screen contributing source sets and log warning if
	 * specified deagg config does not span hazard result range
	 */

	private final HazardResult hazard;
	private final double returnPeriod;
	private final Map<Imt, Deagg> deaggs;

	private Deaggregation(HazardResult hazard, double returnPeriod, Map<Imt, Deagg> deaggs) {
		this.hazard = hazard;
		this.returnPeriod = returnPeriod;
		this.deaggs = deaggs;
	}

	// all HazardResult curves are in x-log space already
	private static final Interpolator IML_INTERPOLATE = Interpolator.builder()
		.logy()
		.decreasingX()
		.build();

	// TODO return period is problematic if defined by an integer
	// number of years; for instance we'll not get the true 2%in50
	// for a return period of 2475, or whatever other, rate recovered
	public static Deaggregation create(HazardResult hazard, double returnPeriod) {

		Map<Imt, Deagg> imtDeaggMap = Maps.newEnumMap(Imt.class);
		for (Entry<Imt, XySequence> entry : hazard.totalCurves.entrySet()) {
			Imt imt = entry.getKey();
			double rate = 1.0 / returnPeriod;
			double iml = IML_INTERPOLATE.findX(entry.getValue(), rate);

			Dataset model = Dataset.builder(hazard.config).build();
			Deagg deagg = new Deagg(hazard, model, imt, rate, iml);
			imtDeaggMap.put(imt, deagg);
		}

		return new Deaggregation(
			hazard,
			returnPeriod,
			Maps.immutableEnumMap(imtDeaggMap));
	}

	// TODO need to provide string summary
	@Override public String toString() {
		// Entry<Imt, Deagg> entry = deaggs.entrySet().iterator().next();
		StringBuilder sb = new StringBuilder();
		for (Entry<Imt, Deagg> entry : deaggs.entrySet()) {
			sb.append("Deagg for IMT: ").append(entry.getKey()).append(NEWLINE);
			sb.append(entry.getValue());
		}
		return sb.toString();
	}

	/* One per Imt in supplied Hazard. */
	static class Deagg {

		// final HazardResult hazard;
		// final Imt imt;

		final Dataset totalDataset;
		final Map<Gmm, Dataset> gmmDatasets;

		Deagg(HazardResult hazard, Dataset model, Imt imt, double rate, double iml) {
			// this.hazard = hazard;
			// this.imt = imt;

			// may want to consider SetMultimap
			ListMultimap<Gmm, Dataset> datasets = MultimapBuilder
				.enumKeys(Gmm.class)
				.arrayListValues()
				.build();

			for (HazardCurveSet curveSet : hazard.sourceSetMap.values()) {

				System.out.println(curveSet.sourceSet.name());

				Map<Gmm, Dataset> sourceSetDatasets = Deaggregator.of(curveSet)
					.withDataModel(model)
					.forImt(imt)
					.atIml(rate, iml)
					.deaggregate();
				datasets.putAll(Multimaps.forMap(sourceSetDatasets));
			}

			gmmDatasets = Maps.immutableEnumMap(Maps.transformValues(
				Multimaps.asMap(datasets),
				DATASET_CONSOLIDATOR));

			totalDataset = DATASET_CONSOLIDATOR.apply(gmmDatasets.values());
		}

		@Override public String toString() {
			StringBuilder sb = new StringBuilder();
			int index = 0;
			double totalRate = 0.0;
			for (Contribution source : totalDataset.sources) {
				// sb.append(index++).append(":  ").append(source).append(NEWLINE);
				totalRate += (source.sourceRate + source.skipRate);
			}
			sb.append(NEWLINE);
			sb.append("TOTAL: " + totalRate).append(NEWLINE);
			sb.append(NEWLINE);
			sb.append(totalDataset.rmε);
			return sb.toString();
		}

	}

	private static final String NEWLINE = StandardSystemProperty.LINE_SEPARATOR.value();

	private static final Function<Collection<Dataset>, Dataset> DATASET_CONSOLIDATOR =
		new Function<Collection<Dataset>, Dataset>() {
			@Override public Dataset apply(Collection<Dataset> datasets) {
				Dataset.Builder builder = Dataset.builder(datasets.iterator().next());
				for (Dataset dataset : datasets) {
					builder.add(dataset);
				}
				return builder.build();
			}
		};

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

	/* Wrapper class for a Source and it's contribution to hazard. */
	static class Contribution implements Comparable<Contribution> {

		final String source;
		final double sourceRate;
		final double skipRate;

		private Contribution(String source, double sourceRate, double skipRate) {
			this.source = source;
			this.sourceRate = sourceRate;
			this.skipRate = skipRate;
		}

		@Override public int compareTo(Contribution other) {
			return Double.compare(sourceRate, other.sourceRate);
		}

		@Override public String toString() {
			StringBuilder sb = new StringBuilder();
			sb.append(sourceRate).append(" ");
			sb.append(skipRate).append(" ");
			sb.append(sourceRate + skipRate).append(" ");
			sb.append(source);
			return sb.toString();
		}
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
		private Map<Gmm, Dataset.Builder> datasetBuilders;

		private Imt imt;
		private Double rate;
		private Double iml;

		private Deaggregator(HazardCurveSet hazard) {
			this.hazard = hazard;
			this.sources = hazard.sourceSet;
			this.gmmSet = sources.groundMotionModels();
		}

		/* Create a new deaggregator. */
		static Deaggregator of(HazardCurveSet hazard) {
			return new Deaggregator(checkNotNull(hazard));
		}

		Deaggregator withDataModel(Dataset model) {
			this.model = model;
			this.datasetBuilders = initDataBuilders(gmmSet.gmms(), model);
			return this;
		}

		private static Map<Gmm, Dataset.Builder> initDataBuilders(Set<Gmm> gmms, Dataset model) {
			Map<Gmm, Dataset.Builder> map = Maps.newEnumMap(Gmm.class);
			for (Gmm gmm : gmms) {
				map.put(gmm, Dataset.builder(model));
			}
			return map;
		}

		Deaggregator forImt(Imt imt) {
			// TODO check valid imt agains HCS
			this.imt = imt;
			return this;
		}

		Deaggregator atIml(double rate, double iml) {
			// TODO check valid iml agains curve x-range for imt??
			this.rate = rate;
			this.iml = iml;
			return this;
		}

		Map<Gmm, Dataset> deaggregate() {
			checkState();

			List<Contribution> sources = new ArrayList<>();

			for (GroundMotions gms : hazard.hazardGroundMotionsList) {
				InputList inputs = gms.inputs;
				double minDistance = inputs.minDistance;
				Map<Gmm, List<Double>> μLists = gms.means.get(imt);
				Map<Gmm, List<Double>> σLists = gms.sigmas.get(imt);
				Map<Gmm, Double> gmms = gmmSet.gmmWeightMap(minDistance);
				Contribution source = processSource(inputs, gmms, μLists, σLists, EXCEEDANCE);
				sources.add(source);
			}

			return createDataMap();
		}

		private void checkState() {
			String clazz = getClass().getSimpleName();
			checkNotNull(model, "%s: data model not set", clazz);
			checkNotNull(imt, "%s: IMT not set", clazz);
			checkNotNull(iml, "%s: target IML not set", clazz);
		}

		private Map<Gmm, Dataset> createDataMap() {
			return Maps.immutableEnumMap(
				Maps.transformValues(
					datasetBuilders,
					new Function<Dataset.Builder, Dataset>() {
						@Override public Dataset apply(Builder builder) {
							return builder.build();
						}
					}));
		}

		private Contribution processSource(
				InputList inputs,
				Map<Gmm, Double> gmms,
				Map<Gmm, List<Double>> μLists,
				Map<Gmm, List<Double>> σLists,
				ExceedanceModel exceedanceModel) {

			/* Local EnumSet based keys. */
			final Set<Gmm> gmmKeys = EnumSet.copyOf(gmms.keySet());

			/* Per-gmm rates for the source being processed. */
			Map<Gmm, Double> gmmSourceRates = createRateMap(gmmKeys);
			Map<Gmm, Double> gmmSkipRates = createRateMap(gmmKeys);

			/* Rate across all gmms. */
			double sourceRate = 0.0;

			/* Possibly skipped rate portion. */
			double skipRate = 0.0;

			/* Add rupture data to builders */
			for (int i = 0; i < inputs.size(); i++) {

				HazardInput in = inputs.get(i);
				double rRup = in.rRup;
				double Mw = in.Mw;

				int rIndex = model.distanceIndex(rRup);
				int mIndex = model.magnitudeIndex(Mw);
				boolean skipRupture = (rIndex == -1 || mIndex == -1);

				for (Gmm gmm : gmmKeys) {

					double gmmWeight = gmms.get(gmm);

					double μ = μLists.get(gmm).get(i);
					double σ = σLists.get(gmm).get(i);
					double ε = epsilon(μ, σ, iml);

					double probAtIml = exceedanceModel.exceedance(μ, σ, trunc, imt, iml);
					double rate = probAtIml * in.rate * sources.weight() * gmmWeight;

					if (skipRupture) {
						gmmSkipRates.put(gmm, gmmSkipRates.get(gmm) + rate);
						skipRate += rate;
						continue;
					}
					gmmSourceRates.put(gmm, gmmSourceRates.get(gmm) + rate);
					sourceRate += rate;

					// System.out.println(μ + " " + σ + " " + iml);
					// System.out.println("ε: " + ε);
					int εIndex = model.epsilonIndex(ε);

					datasetBuilders.get(gmm).add(
						rIndex, mIndex, εIndex,
						rRup * rate, Mw * rate, ε * rate,
						rate);
				}
			}

			/* Add sources/contributors to builders. */
			for (Gmm gmm : gmmKeys) {
				Contribution source = new Contribution(
					inputs.parentName(),
					gmmSourceRates.get(gmm),
					gmmSkipRates.get(gmm));
				datasetBuilders.get(gmm).add(source);
			}

			return new Contribution(inputs.parentName(), sourceRate, skipRate);
		}

		private static Map<Gmm, Double> createRateMap(Set<Gmm> gmms) {
			Map<Gmm, Double> rateMap = Maps.newEnumMap(Gmm.class);
			for (Gmm gmm : gmms) {
				rateMap.put(gmm, 0.0);
			}
			return rateMap;
		}

	}

	private static double epsilon(double μ, double σ, double iml) {
		return (μ - iml) / σ;
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

		/* Total rate for a dataset and summed weight for *Bar fields */
		private final double barWeight;

		/* Weighted r and m position data */
		private final DataTable rPositions;
		private final DataTable mPositions;
		private final DataTable positionWeights;

		/* Contributors */
		private final List<Contribution> sources;

		private Dataset(
				DataVolume rmε,
				double rBar, double mBar, double εBar,
				double barWeight,
				DataTable rPositions,
				DataTable mPositions,
				DataTable positionWeights,
				List<Contribution> sources) {

			this.rmε = rmε;

			this.rBar = rBar;
			this.mBar = mBar;
			this.εBar = εBar;
			this.barWeight = barWeight;

			this.rPositions = rPositions;
			this.mPositions = mPositions;
			this.positionWeights = positionWeights;

			this.sources = sources;
		}

		/*
		 * Index methods delegate to the same method used to initialize internal
		 * data tables and volumes.
		 */

		/**
		 * Return the internal bin index of the supplied distance, {@code r}, or
		 * {@code -1} if {@code r} is outside the range specified for the
		 * deaggregation underway.
		 * 
		 * @param r distance for which to compute index
		 */
		int distanceIndex(double r) {
			try {
				return DataTables.indexOf(rmε.rowMin(), rmε.rowΔ(), r, rmε.rowCount());
			} catch (IndexOutOfBoundsException e) {
				return -1;
			}
		}

		/**
		 * Return the internal bin index of the supplied magnitude, {@code m},
		 * or {@code -1} if {@code m} is outside the range specified for the
		 * deaggregation underway.
		 * 
		 * @param m magnitude for which to compute index
		 */
		int magnitudeIndex(double m) {
			try {
				return DataTables.indexOf(rmε.columnMin(), rmε.columnΔ(), m, rmε.columnCount());
			} catch (IndexOutOfBoundsException e) {
				return -1;
			}
		}

		/**
		 * Return the internal bin index of the supplied epsilon, {@code ε}.
		 * Epsilon indexing behaves differently than distance and magnitude
		 * indexing. Whereas distance and magnitudes, if out of range of a
		 * deaggregation, return -1, the lowermost and uppermost epsilon bins
		 * are open ended and are used to collect all values less than or
		 * greater than the upper and lower edges of those bins, respectively.
		 * 
		 * @param ε epsilon for which to compute index
		 */
		int epsilonIndex(double ε) {
			return (ε < rmε.levelMin()) ? 0 :
				(ε >= rmε.levelMax()) ? rmε.levelCount() - 1 :
					DataTables.indexOf(rmε.levelMin(), rmε.levelΔ(), ε, rmε.levelCount());
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

			private static final String ID = "Deaggregation.Dataset.Builder";

			private DataVolume.Builder rmε;

			/* Weighted mean contributions */
			private double rBar, mBar, εBar;
			private double barWeight;

			/* Weighted r and m position data */
			private DataTable.Builder rPositions;
			private DataTable.Builder mPositions;
			private DataTable.Builder positionWeights;

			private List<Contribution> sources;

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

				rPositions = DataTable.Builder.create()
					.rows(rMin, rMax, Δr)
					.columns(mMin, mMax, Δm);
				mPositions = DataTable.Builder.create()
					.rows(rMin, rMax, Δr)
					.columns(mMin, mMax, Δm);
				positionWeights = DataTable.Builder.create()
					.rows(rMin, rMax, Δr)
					.columns(mMin, mMax, Δm);

				sources = new ArrayList<>();
			}

			private Builder(Dataset model) {
				rmε = DataVolume.Builder.fromModel(model.rmε);
				rPositions = DataTable.Builder.fromModel(model.rPositions);
				mPositions = DataTable.Builder.fromModel(model.mPositions);
				positionWeights = DataTable.Builder.fromModel(model.positionWeights);
				sources = new ArrayList<>();
			}

			/*
			 * Populate dataset with rupture data. Supply DataTable and
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

			/* Add a contributing source to a dataset. */
			Builder add(Contribution source) {
				sources.add(source);
				return this;
			}

			/* Combine values */
			Builder add(Dataset other) {

				rmε.add(other.rmε);

				rBar += other.rBar;
				mBar += other.mBar;
				εBar += other.εBar;
				barWeight += barWeight;

				rPositions.add(other.rPositions);
				mPositions.add(other.mPositions);
				positionWeights.add(other.positionWeights);

				sources.addAll(other.sources);

				return this;
			}

			Dataset build() {
				return new Dataset(
					rmε.build(),
					rBar, mBar, εBar,
					barWeight,
					rPositions.build(),
					mPositions.build(),
					positionWeights.build(),
					ImmutableList.copyOf(sources));
			}
		}

	}

}
