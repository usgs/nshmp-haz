package org.opensha2.calc;

import static com.google.common.base.Preconditions.checkArgument;

import org.opensha2.calc.CalcConfig.Rate.Bins;
import org.opensha2.data.IntervalArray;
import org.opensha2.data.IntervalArray.Builder;
import org.opensha2.data.XySequence;
import org.opensha2.eq.model.ClusterSource;
import org.opensha2.eq.model.ClusterSourceSet;
import org.opensha2.eq.model.Distance;
import org.opensha2.eq.model.HazardModel;
import org.opensha2.eq.model.Rupture;
import org.opensha2.eq.model.Source;
import org.opensha2.eq.model.SourceSet;
import org.opensha2.eq.model.SourceType;
import org.opensha2.eq.model.SystemSourceSet;
import org.opensha2.geo.Location;
import org.opensha2.mfd.Mfds;
import org.opensha2.util.Maths;

import com.google.common.base.Converter;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

import java.util.EnumMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;

/**
 * General purpose earthquake rate and probability data container. This class
 * makes no distinction between incremental or cumulative MFDs, or whether
 * values are stored as annual-rate or poisson probability.
 *
 * @author Peter Powers
 */
public class EqRate {

  /** The site of interest. */
  public final Site site;

  /** The total MFD of interest. */
  public final XySequence totalMfd;

  /** The MFDs for each contributing source type. */
  public final Map<SourceType, XySequence> typeMfds;

  private EqRate(
      Site site,
      XySequence totalMfd,
      Map<SourceType, XySequence> typeMfds) {

    this.site = site;
    this.totalMfd = totalMfd;
    this.typeMfds = typeMfds;
  }

  /**
   * Create a new earthquake rate data container.
   * 
   * @param model to process
   * @param config calculation configuration
   * @param site of interest
   */
  public static EqRate create(
      HazardModel model,
      CalcConfig config,
      Site site) {

    CalcConfig.Rate rateConfig = config.rate;

    Bins mBins = rateConfig.bins;
    IntervalArray modelMfd = IntervalArray.Builder
        .withRows(
            mBins.mMin,
            mBins.mMax,
            mBins.Δm)
        .build();

    EqRate rates = createIncremental(model, site, rateConfig.distance, modelMfd);
    if (rateConfig.distributionFormat == DistributionFormat.CUMULATIVE) {
      rates = toCumulative(rates);
    }
    if (rateConfig.valueFormat == ValueFormat.POISSON_PROBABILITY) {
      rates = toPoissonProbability(rates, rateConfig.timespan);
    }
    return rates;
  }

  /**
   * Wraps {@link #create(HazardModel, CalcConfig, Site)} in a {@link Callable}
   * for processing multiple sites concurrently.
   * 
   * @param model to process
   * @param config calculation configuration
   * @param site of interest
   */
  public static Callable<EqRate> callable(
      HazardModel model,
      CalcConfig config,
      Site site) {

    return new RateTask(model, config, site);
  }

  private static EqRate createIncremental(
      HazardModel model,
      Site site,
      double distance,
      IntervalArray modelMfd) {

    /* Initialize SourceType mfd builders. */
    Map<SourceType, IntervalArray.Builder> typeMfdBuilders = new EnumMap<>(SourceType.class);
    for (SourceType type : model.types()) {
      typeMfdBuilders.put(type, IntervalArray.Builder.fromModel(modelMfd));
    }

    /* Populate builders. */
    for (SourceSet<? extends Source> sourceSet : model) {
      IntervalArray sourceSetMfd = mfd(sourceSet, site.location, distance, modelMfd);
      typeMfdBuilders.get(sourceSet.type()).add(sourceSetMfd);
    }

    /* Compute total and convert to sequences. */
    IntervalArray.Builder totalMfd = IntervalArray.Builder.fromModel(modelMfd);
    ImmutableMap.Builder<SourceType, XySequence> typeMfds = ImmutableMap.builder();
    for (Entry<SourceType, IntervalArray.Builder> entry : typeMfdBuilders.entrySet()) {
      IntervalArray typeMfd = entry.getValue().build();
      typeMfds.put(entry.getKey(), typeMfd.values());
      totalMfd.add(typeMfd);
    }

    return new EqRate(
        site,
        totalMfd.build().values(),
        typeMfds.build());
  }

  /**
   * Create a new earthquake rate container with cumulative values.
   * 
   * @param incremental rate source to convert
   */
  public static EqRate toCumulative(EqRate incremental) {

    XySequence cumulativeTotal = Mfds.toCumulative(incremental.totalMfd);
    ImmutableMap.Builder<SourceType, XySequence> cumulativeTypes = ImmutableMap.builder();
    for (Entry<SourceType, XySequence> entry : incremental.typeMfds.entrySet()) {
      cumulativeTypes.put(
          entry.getKey(),
          Mfds.toCumulative(entry.getValue()));
    }
    return new EqRate(
        incremental.site,
        cumulativeTotal,
        cumulativeTypes.build());
  }

  /**
   * Create a new earthquake rate container with Poisson probability values.
   * 
   * @param annualRates source to convert
   * @param timespan of interest for annual rate to Poisson probability
   *        conversion
   */
  public static EqRate toPoissonProbability(EqRate annualRates, double timespan) {
    Converter<Double, Double> converter =
        Mfds.annualRateToProbabilityConverter(timespan)
            .andThen(Maths.decimalToProbabilityConverter(2));
    XySequence totalMfd = XySequence
        .copyOf(annualRates.totalMfd)
        .transform(converter);
    EnumMap<SourceType, XySequence> typeMfds = new EnumMap<>(SourceType.class);
    for (Entry<SourceType, XySequence> entry : annualRates.typeMfds.entrySet()) {
      typeMfds.put(
          entry.getKey(),
          XySequence
              .copyOf(entry.getValue())
              .transform(converter));
    }
    return new EqRate(
        annualRates.site,
        XySequence.immutableCopyOf(totalMfd),
        Maps.immutableEnumMap(typeMfds));
  }

  /**
   * Create a new earthquake rate container with the sum of the supplied
   * {@code rates}.
   * 
   * <p><b>NOTE:</b> This operation is additive and will produce meaningless
   * results if {@code rates} have already been converted to
   * {@link #toPoissonProbability(EqRate, double) probabilities}, or are not all
   * of {@link DistributionFormat#INCREMENTAL} or
   * {@link DistributionFormat#CUMULATIVE} distribution format.
   * 
   * <p>Buyer beware.
   * 
   * @param rates to combine
   */
  public static EqRate combine(EqRate... rates) {
    Site referenceSite = rates[0].site;
    XySequence totalMfd = XySequence.emptyCopyOf(rates[0].totalMfd);
    EnumMap<SourceType, XySequence> typeMfds = new EnumMap<>(SourceType.class);
    for (EqRate rate : rates) {
      checkArgument(
          rate.site.location.equals(referenceSite.location),
          "Site locations are not the same:\n\ts1: %s\n\ts2: %s",
          referenceSite, rate.site);
      totalMfd.add(rate.totalMfd);
      for (Entry<SourceType, XySequence> entry : rate.typeMfds.entrySet()) {
        entry.getValue().addToMap(entry.getKey(), typeMfds);
      }
    }
    return new EqRate(
        referenceSite,
        XySequence.immutableCopyOf(totalMfd),
        Maps.immutableEnumMap(typeMfds));
  }

  private static IntervalArray mfd(
      SourceSet<? extends Source> sourceSet,
      Location location,
      double distance,
      IntervalArray modelMfd) {

    switch (sourceSet.type()) {
      case GRID:
        return gridMfd(sourceSet, location, distance, modelMfd);
      case SLAB:
        return gridMfd(sourceSet, location, distance, modelMfd);
      case CLUSTER:
        return clusterMfd((ClusterSourceSet) sourceSet, location, distance, modelMfd);
      case SYSTEM:
        return systemMfd((SystemSourceSet) sourceSet, location, distance, modelMfd);
      default:
        return faultMfd(sourceSet, location, distance, modelMfd);
      // TODO AREA?
    }
  }

  /*
   * Short-circuit GridSourceSet by summing the relevant node mfds. Handles both
   * GRID and SLAB types.
   */
  private static IntervalArray gridMfd(
      SourceSet<? extends Source> sourceSet,
      Location location,
      double distance,
      IntervalArray modelMfd) {

    IntervalArray.Builder sourceSetMfd = IntervalArray.Builder.fromModel(modelMfd);
    for (Source source : sourceSet.iterableForLocation(location, distance)) {
      for (XySequence mfd : source.mfds()) {
        sourceSetMfd.addEach(mfd);
      }
    }
    return sourceSetMfd.multiply(sourceSet.weight()).build();
  }

  /*
   * Special case ClusterSourceSet.
   * 
   * Nested fault rates are in fact weights that need to be scaled by the
   * cluster rate.
   */
  private static IntervalArray clusterMfd(
      ClusterSourceSet sourceSet,
      Location location,
      double distance,
      IntervalArray modelMfd) {

    IntervalArray.Builder sourceSetMfd = IntervalArray.Builder.fromModel(modelMfd);
    for (Source source : sourceSet.iterableForLocation(location, distance)) {
      ClusterSource clusterSource = (ClusterSource) source;
      IntervalArray.Builder faultMfd = Builder
          .copyOf(faultMfd(
              clusterSource.faults(),
              location,
              distance,
              modelMfd))
          .multiply(clusterSource.rate());
      sourceSetMfd.add(faultMfd.build());
    }
    return sourceSetMfd.multiply(sourceSet.weight()).build();
  }

  /*
   * Special case and delegate to SystemSourceSet.
   */
  static IntervalArray systemMfd(
      SystemSourceSet sourceSet,
      Location location,
      double distance,
      IntervalArray modelMfd) {

    return SystemSourceSet
        .toRatesFunction(location, distance, modelMfd)
        .apply(sourceSet);
  }

  /*
   * Default approach: distance filter on ruptures.
   */
  private static IntervalArray faultMfd(
      SourceSet<? extends Source> sourceSet,
      Location location,
      double distance,
      IntervalArray modelMfd) {

    IntervalArray.Builder sourceSetMfd = IntervalArray.Builder.fromModel(modelMfd);
    for (Source source : sourceSet.iterableForLocation(location, distance)) {
      for (Rupture rupture : source) {
        Distance d = rupture.surface().distanceTo(location);
        if (d.rJB <= distance) {
          sourceSetMfd.add(rupture.mag(), rupture.rate());
        }
      }
    }
    return sourceSetMfd.multiply(sourceSet.weight()).build();
  }

  private static final class RateTask implements Callable<EqRate> {

    private final HazardModel model;
    private final CalcConfig config;
    private final Site site;

    RateTask(
        HazardModel model,
        CalcConfig config,
        Site site) {

      this.model = model;
      this.config = config;
      this.site = site;
    }

    @Override
    public EqRate call() throws Exception {
      return create(model, config, site);
    }
  }

}
