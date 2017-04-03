package org.opensha2.calc;

import org.opensha2.data.IntervalArray;
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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

import java.util.EnumMap;
import java.util.Map;
import java.util.Map.Entry;

/**
 * General purpose magnitude-frequency distribution data container. CUrrent;y
 * implemented for annual rate only.
 *
 * @author Peter Powers
 */
public class EqRate {

  final XySequence totalMfd;
  final Map<SourceType, XySequence> typeMfds;

  private EqRate(XySequence totalMfd, Map<SourceType, XySequence> typeMfds) {
    this.totalMfd = totalMfd;
    this.typeMfds = typeMfds;
  }
  
  /*
   * Developer notes:
   * 
   * TODO the receiver MFD needs to be built by querying hazard model for min-max
   * magnitudes; currently it is built using fixed, known values from the 2014
   * COUS model.
   */

  public static EqRate createIncremental(
      HazardModel model,
      Location location,
      double distance) {

    IntervalArray modelMfd = IntervalArray.Builder
        .withRows(4.7, 9.4, 0.1)
        .build();

    /* Initialize SourceType mfd builders. */
    Map<SourceType, IntervalArray.Builder> typeMfdBuilders = new EnumMap<>(SourceType.class);
    for (SourceType type : model.types()) {
      typeMfdBuilders.put(type, IntervalArray.Builder.fromModel(modelMfd));
    }

    /* Populate builders. */
    for (SourceSet<? extends Source> sourceSet : model) {
      IntervalArray sourceSetMfd = mfd(sourceSet, location, distance, modelMfd);
      typeMfdBuilders.get(sourceSet.type()).add(sourceSetMfd);
    }

    /* Compute total and convert to sequences. */
    IntervalArray.Builder totalMfd = IntervalArray.Builder.fromModel(modelMfd);
    ImmutableMap.Builder<SourceType, XySequence> typeMfds = ImmutableMap.builder();
    for (Entry<SourceType, IntervalArray.Builder> entry : typeMfdBuilders.entrySet()) {
      IntervalArray typeMfd = entry.getValue().build();
      totalMfd.add(typeMfd);
    }

    return new EqRate(
        totalMfd.build().values(),
        typeMfds.build());
  }

  /**
   * Get the total 
   * @param model
   * @param location
   * @param distance
   * @return
   */
  public static EqRate createCumulative(
      HazardModel model,
      Location location,
      double distance) {

    EqRate incremental = createIncremental(model, location, distance);
    XySequence cumulativeTotal = Mfds.toCumulative(incremental.totalMfd);
    ImmutableMap.Builder<SourceType, XySequence> cumulativeTypes = ImmutableMap.builder();
    for (Entry<SourceType, XySequence> entry : incremental.typeMfds.entrySet()) {
      cumulativeTypes.put(
          entry.getKey(),
          Mfds.toCumulative(entry.getValue()));
    }
    return new EqRate(
        cumulativeTotal,
        cumulativeTypes.build());
  }

  static EqRate combine(EqRate... eqRates) {
    // validate xs; or not if only for internal use where we know the receiver
    // mfds are all sources from the same model

    XySequence totalMfd = XySequence.emptyCopyOf(eqRates[0].totalMfd);
    EnumMap<SourceType, XySequence> typeMfds = new EnumMap<>(SourceType.class);
    for (EqRate eqRate : eqRates) {
      totalMfd.add(eqRate.totalMfd);
      for (Entry<SourceType, XySequence> entry : eqRate.typeMfds.entrySet()) {
        SourceType type = entry.getKey();
        if (!typeMfds.containsKey(type)) {
          XySequence typeMfd = XySequence.emptyCopyOf(totalMfd);
          typeMfds.put(type, typeMfd);
        }
        typeMfds.get(type).add(entry.getValue());
      }
    }
    return new EqRate(
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
    return sourceSetMfd.build();
  }

  /*
   * Special case ClusterSourceSet.
   */
  private static IntervalArray clusterMfd(
      ClusterSourceSet sourceSet,
      Location location,
      double distance,
      IntervalArray modelMfd) {

    IntervalArray.Builder sourceSetMfd = IntervalArray.Builder.fromModel(modelMfd);
    for (Source source : sourceSet.iterableForLocation(location, distance)) {
      ClusterSource clusterSource = (ClusterSource) source;
      sourceSetMfd.add(faultMfd(
          clusterSource.faults(),
          location,
          distance,
          modelMfd));
    }
    return sourceSetMfd.build();
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
    return sourceSetMfd.build();
  }

}
