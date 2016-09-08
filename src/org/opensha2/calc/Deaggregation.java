package org.opensha2.calc;

import static org.opensha2.calc.DeaggDataset.SOURCE_CONSOLIDATOR;
import static org.opensha2.calc.DeaggDataset.SOURCE_SET_CONSOLIDATOR;
import static org.opensha2.internal.TextUtils.NEWLINE;

import org.opensha2.data.Interpolator;
import org.opensha2.data.XySequence;
import org.opensha2.gmm.Gmm;
import org.opensha2.gmm.Imt;

import com.google.common.collect.ListMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Multimaps;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Hazard deaggregation. Given a {@link Hazard} result, this class will
 * deaggregate the results at all spectral periods supplied in the result at an
 * intensity measure level or return period of interest.
 *
 * @author Peter Powers
 */
public final class Deaggregation {

  /*
   * Developer notes and TODO
   * -------------------------------------------------------------------------
   * Auto-scaling of results (dataset bounds) based on hazard model.
   * -------------------------------------------------------------------------
   * Warnings if config does not span source set range.
   * -------------------------------------------------------------------------
   * Deaggregate on probability of occurrence instead of exceedance.
   * -------------------------------------------------------------------------
   * Revisit precision issues associated with integer based return period;
   * 2%in50 years os really 0.00040405414, not 1/2475 = 0.0004040404
   * -------------------------------------------------------------------------
   * -------------------------------------------------------------------------
   * One of the difficulties with deaggregation is deciding how to specify
   * magnitude and distance ranges, and respective discretizations, over which
   * to deaggregate, given the broad ranges of distances and magnitudes
   * supported by various models (e.g. 300km in the WUS NSHMP vs 1000km in the
   * CEUS). Invariably, situations will arise where some sources are outside a
   * user-specified range and the sum of contributions in a deaggregation data
   * matrix will not equal the target rate specified at the outset of a
   * calculation. One could query the model(s) being used (set broad limits) or
   * create lists of sources and their contributions in advance before building
   * deaggregation datasets (set calculation specific limits). The former may
   * make sense as a default setting in the absence of any user specified
   * settings, the latter complicates the code considerably.
   *
   * For the time being we require user-specified ranges and encourage high
   * resolution deaggregation data bins that can be preserved if the
   * contributing sources span only a small part of a deaggregation result
   * matrix. If the deaggregation result matrix is heavily populated, bins could
   * be consolidated prior to output.
   *
   * For data outside the defined ranges, we track the 'un-binned' or residual
   * rate. This is needed to compute mean r, m, and ε.
   *
   * Note that in a webservice environment, only relevant data will be returned
   * (zero-contribution bins are omitted) and the client will render plots based
   * on the data supplied, not based on the ranges specified for the calculation
   * itelf.
   * -------------------------------------------------------------------------
   * Issues related to deaggreagtion targets.
   *
   * Because hazard is computed at specific intensity measure levels, only when
   * a deaggregation is computed at one of those levels will the contributions
   * of the relevant sources equal the target rate specified by the total mean
   * hazard curve. Because of the convexity of the hazard curve in log space,
   * the 'true' total as derived from the relevant sources will be slightly
   * higher.
   */

  final Map<Imt, ImtDeagg> deaggs;
  final Site site;

  private Deaggregation(Map<Imt, ImtDeagg> deaggs, Site site) {
    this.deaggs = deaggs;
    this.site = site;
  }

  /**
   * Deaggregate {@code hazard} at the intensity measure level corresponding to
   * the supplied {@code returnPeriod}.
   *
   * @param hazard to deaggregate.
   * @param returnPeriod at which to deaggregate {@code hazard}
   */
  public static Deaggregation atReturnPeriod(Hazard hazard, double returnPeriod) {
    Map<Imt, ImtDeagg> imtDeaggMap = Maps.newEnumMap(Imt.class);
    DeaggConfig.Builder cb = DeaggConfig.builder(hazard);
    double rate = 1.0 / returnPeriod;

    for (Entry<Imt, XySequence> entry : hazard.totalCurves.entrySet()) {
      Imt imt = entry.getKey();
      double iml = IML_INTERPOLATER.findX(entry.getValue(), rate);
      DeaggConfig config = cb.imt(imt).iml(iml, rate, returnPeriod).build();
      ImtDeagg imtDeagg = new ImtDeagg(hazard, config);
      imtDeaggMap.put(imt, imtDeagg);
    }

    return new Deaggregation(
        Maps.immutableEnumMap(imtDeaggMap),
        hazard.site);
  }

  /**
   * Deaggregate {@code hazard} at the supplied intensity measure level.
   *
   * @param hazard to deaggregate.
   * @param iml intensity measure level at which to deaggregate {@code hazard}
   */
  public static Deaggregation atIml(Hazard hazard, double iml) {

    Map<Imt, ImtDeagg> imtDeaggMap = Maps.newEnumMap(Imt.class);
    DeaggConfig.Builder cb = DeaggConfig.builder(hazard);

    for (Entry<Imt, XySequence> entry : hazard.totalCurves.entrySet()) {
      Imt imt = entry.getKey();
      double rate = RATE_INTERPOLATER.findY(entry.getValue(), iml);
      double returnPeriod = 1.0 / rate;
      DeaggConfig config = cb.imt(imt).iml(iml, rate, returnPeriod).build();
      ImtDeagg imtDeagg = new ImtDeagg(hazard, config);
      imtDeaggMap.put(imt, imtDeagg);
    }

    return new Deaggregation(
        Maps.immutableEnumMap(imtDeaggMap),
        hazard.site);
  }

  /* Hazard curves are already in log-x space. */
  static final Interpolator IML_INTERPOLATER = Interpolator.builder()
      .logy()
      .decreasingX()
      .build();

  /* Hazard curves are already in log-x space. */
  static final Interpolator RATE_INTERPOLATER = Interpolator.builder()
      .logy()
      .build();

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    for (Imt imt : deaggs.keySet()) {
      sb.append(deaggs.get(imt));
    }
    return sb.toString();
  }

  /* One per Imt in supplied Hazard. */
  static class ImtDeagg {

    final DeaggConfig config;
    final DeaggDataset totalDataset;
    final Map<Gmm, DeaggDataset> gmmDatasets;

    ImtDeagg(Hazard hazard, DeaggConfig config) {
      this.config = config;

      /*
       * Datasets are combined as follows: For each HazardCurveSet/SourceSet
       * deaggregation is performed across all relevant Gmms. These are
       * preserved in a ListMultimap for output of deaggregation by Gmm. It's
       * too much work to consolidate the ListMultimap and keep track of all the
       * nested DeaggContributors, so a list is maintained of datasets per
       * SourceSet, the total across all Gmms that result from each call to
       * deaggregate(). The combination of multiple datasets for single
       * SourceSets is straightforward.
       */

      int sourceSetCount = hazard.sourceSetCurves.size();
      ListMultimap<Gmm, DeaggDataset> gmmDatasetLists = MultimapBuilder
          .enumKeys(Gmm.class)
          .arrayListValues(sourceSetCount)
          .build();
      List<DeaggDataset> totalDatasetList = new ArrayList<>(sourceSetCount);

      for (HazardCurveSet curveSet : hazard.sourceSetCurves.values()) {
        XySequence sourceSetCurve = curveSet.totalCurves.get(config.imt);
        double sourceSetRate = RATE_INTERPOLATER.findY(sourceSetCurve, config.iml);
        if (Double.isNaN(sourceSetRate) || sourceSetRate == 0.0) {
          // TODO log me instead FINER??
          // System.out.println("Skipping: " + curveSet.sourceSet.name());
          continue;
        }
        Map<Gmm, DeaggDataset> sourceSetDatasets = Deaggregator.deaggregate(
            curveSet,
            config,
            hazard.site);
        gmmDatasetLists.putAll(Multimaps.forMap(sourceSetDatasets));
        totalDatasetList.add(SOURCE_CONSOLIDATOR.apply(sourceSetDatasets.values()));
      }

      /* Combine SourceSets across Gmms. */
      gmmDatasets = Maps.immutableEnumMap(Maps.transformValues(
          Multimaps.asMap(gmmDatasetLists),
          SOURCE_SET_CONSOLIDATOR));

      /* Combine SourceSet totals. */
      totalDataset = SOURCE_SET_CONSOLIDATOR.apply(totalDatasetList);
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append(NEWLINE);
      DeaggExport export = new DeaggExport(
          totalDataset,
          totalDataset,
          config,
          "Total");
      sb.append(export.toString());
      sb.append(NEWLINE);
      for (Entry<Gmm, DeaggDataset> ddEntry : gmmDatasets.entrySet()) {
        export = new DeaggExport(
            totalDataset,
            ddEntry.getValue(),
            config,
            ddEntry.getKey().toString());
        sb.append(export.toString());
        sb.append(NEWLINE);
      }
      return sb.toString();
    }
  }

}
