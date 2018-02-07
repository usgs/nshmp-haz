package gov.usgs.earthquake.nshmp.calc;

import static gov.usgs.earthquake.nshmp.calc.DeaggDataset.SOURCE_CONSOLIDATOR;
import static gov.usgs.earthquake.nshmp.calc.DeaggDataset.SOURCE_SET_CONSOLIDATOR;
import static gov.usgs.earthquake.nshmp.internal.TextUtils.NEWLINE;

import com.google.common.collect.ListMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Multimaps;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;

import gov.usgs.earthquake.nshmp.data.Interpolator;
import gov.usgs.earthquake.nshmp.data.XySequence;
import gov.usgs.earthquake.nshmp.eq.model.SourceType;
import gov.usgs.earthquake.nshmp.gmm.Gmm;
import gov.usgs.earthquake.nshmp.gmm.Imt;

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
   * 2%in50 years is really 0.00040405414, not 1/2475 = 0.0004040404
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
   * the supplied {@code returnPeriod}. Only a single {@code Imt} will be processed if
   * supplied.
   *
   * @param hazard to deaggregate.
   * @param returnPeriod at which to deaggregate {@code hazard}
   * @param deaggImt to deaggregate; deaggregate all if {@code empty()}
   */
  public static Deaggregation atReturnPeriod(
      Hazard hazard,
      double returnPeriod,
      Optional<Imt> deaggImt) {

    Map<Imt, ImtDeagg> imtDeaggMap = Maps.newEnumMap(Imt.class);
    DeaggConfig.Builder cb = DeaggConfig.builder(hazard);
    double rate = 1.0 / returnPeriod;

    Set<Imt> imtsToDeagg = deaggImt.isPresent()
        ? EnumSet.of(deaggImt.get())
        : hazard.totalCurves.keySet();

    for (Imt imt : imtsToDeagg) {
      double iml = IML_INTERPOLATER.findX(hazard.totalCurves.get(imt), rate);
      DeaggConfig config = cb.imt(imt).iml(iml, rate, returnPeriod).build();
      ImtDeagg imtDeagg = new ImtDeagg(hazard, config);
      imtDeaggMap.put(imt, imtDeagg);
    }

    return new Deaggregation(
        Maps.immutableEnumMap(imtDeaggMap),
        hazard.site);
  }

  /**
   * Deaggregate {@code hazard} at the supplied intensity measure level. Only a
   * single {@code Imt} will be processed if supplied.
   *
   * @param hazard to deaggregate.
   * @param iml intensity measure level at which to deaggregate {@code hazard}
   * @param deaggImt to deaggregate; deaggregate all if {@code empty()}
   */
  public static Deaggregation atIml(
      Hazard hazard, 
      double iml,
      Optional<Imt> deaggImt) {

    Map<Imt, ImtDeagg> imtDeaggMap = Maps.newEnumMap(Imt.class);
    DeaggConfig.Builder cb = DeaggConfig.builder(hazard);

    Set<Imt> imtsToDeagg = deaggImt.isPresent()
        ? EnumSet.of(deaggImt.get())
        : hazard.totalCurves.keySet();
        
    for (Imt imt : imtsToDeagg) {
      double rate = RATE_INTERPOLATER.findY(hazard.totalCurves.get(imt), iml);
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

  /**
   * Returns an object constining deaggregation results that is suitable for
   * JSON serialization.
   * 
   * @param imt of the deaggregation to retrieve.
   */
  public Object toJson(Imt imt) {
    return deaggs.get(imt).toJson();
  }

  /**
   * Returns an object containing epsilon bin data suitable for JSON
   * serialization. This is exposed independent of JSON serialization of as web
   * services may need this metadata independent of deaggregation results.
   */
  public Object εBins() {
    return deaggs.values().iterator().next().config.εBins;
  }

  /* One per Imt in supplied Hazard. */
  static class ImtDeagg {

    final DeaggConfig config;
    final DeaggDataset totalDataset;
    final Map<Gmm, DeaggDataset> gmmDatasets;
    final Map<SourceType, DeaggDataset> typeDatasets;

    ImtDeagg(Hazard hazard, DeaggConfig config) {
      this.config = config;

      /*
       * Datasets are combined as follows:
       * 
       * For each HazardCurveSet (SourceSet), deaggregation is performed across
       * all relevant Gmms. These are preserved in ListMultimaps for output of
       * deaggregation by Gmm and SourceType. It's too much work to consolidate
       * ListMultimaps on the fly and keep track of all the nested
       * DeaggContributors, so lists are maintained of Gmm and SourceType
       * datasets, and the total across all Gmms that result from each call to
       * deaggregate(). The combination of multiple datasets for single
       * SourceSets is then straightforward via static consolidators in
       * DeaggDataset.
       */

      int sourceSetCount = hazard.sourceSetCurves.size();
      ListMultimap<Gmm, DeaggDataset> gmmDatasetLists = MultimapBuilder
          .enumKeys(Gmm.class)
          .arrayListValues(sourceSetCount)
          .build();
      ListMultimap<SourceType, DeaggDataset> typeDatasetLists = MultimapBuilder
          .enumKeys(SourceType.class)
          .arrayListValues(sourceSetCount)
          .build();

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
        DeaggDataset sourceSetTotal = SOURCE_CONSOLIDATOR.apply(sourceSetDatasets.values());
        typeDatasetLists.put(curveSet.sourceSet.type(), sourceSetTotal);
      }

      /* Combine SourceSets across Gmms. */
      gmmDatasets = Maps.immutableEnumMap(Maps.transformValues(
          Multimaps.asMap(gmmDatasetLists),
          SOURCE_SET_CONSOLIDATOR));

      /* Combine SourceSets across SourceTypes. */
      typeDatasets = Maps.immutableEnumMap(Maps.transformValues(
          Multimaps.asMap(typeDatasetLists),
          SOURCE_SET_CONSOLIDATOR));

      /* Combine SourceSet totals. */
      totalDataset = SOURCE_SET_CONSOLIDATOR.apply(typeDatasets.values());
    }

    private static final String TOTAL_COMPONENT = "Total";
    private static final String GMM_COMPONENT = "GMM: ";
    private static final String TYPE_COMPONENT = "Source Type: ";

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append(NEWLINE);
      for (DeaggExport export : buildExports(false)) {
        sb.append(export.toString());
        sb.append(NEWLINE);
      }
      return sb.toString();
    }

    /*
     * Method does not return a JSON String, but rather an appropriately
     * structured object that may be serialized directly or added to some other
     * object prior to serialization.
     */
    Object toJson() {
      return buildExports(true);
    }

    private List<DeaggExport> buildExports(boolean json) {
      List<DeaggExport> exports = new ArrayList<>();
      DeaggExport total = new DeaggExport(
          totalDataset,
          totalDataset,
          config,
          TOTAL_COMPONENT,
          json);
      exports.add(total);
      for (Entry<Gmm, DeaggDataset> gmmEntry : gmmDatasets.entrySet()) {
        DeaggExport gmm = new DeaggExport(
            totalDataset,
            gmmEntry.getValue(),
            config,
            GMM_COMPONENT + gmmEntry.getKey().toString(),
            json);
        exports.add(gmm);
      }
      for (Entry<SourceType, DeaggDataset> typeEntry : typeDatasets.entrySet()) {
        DeaggExport type = new DeaggExport(
            totalDataset,
            typeEntry.getValue(),
            config,
            TYPE_COMPONENT + typeEntry.getKey().toString(),
            json);
        exports.add(type);
      }
      return exports;
    }

  }

}
