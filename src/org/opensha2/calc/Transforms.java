package org.opensha2.calc;

import static com.google.common.util.concurrent.Futures.allAsList;
import static com.google.common.util.concurrent.Futures.getUnchecked;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static com.google.common.util.concurrent.Futures.transform;

import static org.opensha2.gmm.Gmm.instances;

import org.opensha2.calc.ClusterCurves.Builder;
import org.opensha2.data.XySequence;
import org.opensha2.eq.fault.Faults;
import org.opensha2.eq.fault.surface.RuptureSurface;
import org.opensha2.eq.model.ClusterSource;
import org.opensha2.eq.model.ClusterSourceSet;
import org.opensha2.eq.model.Distance;
import org.opensha2.eq.model.FaultSource;
import org.opensha2.eq.model.GmmSet;
import org.opensha2.eq.model.HazardModel;
import org.opensha2.eq.model.Rupture;
import org.opensha2.eq.model.Source;
import org.opensha2.eq.model.SourceSet;
import org.opensha2.eq.model.SystemSourceSet;
import org.opensha2.gmm.Gmm;
import org.opensha2.gmm.GmmInput;
import org.opensha2.gmm.GroundMotionModel;
import org.opensha2.gmm.Imt;
import org.opensha2.util.Site;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * Data transform {@link Function}s. These are called exclusively from
 * {@link CalcFactory}.
 *
 * <p>These transforms perform all the work of computing probabilistic seismic
 * hazard and facilitate the reduction of calculations into component tasks that
 * may be executed concurrently.
 *
 * <p>For convenience, some functions coalesce multiple tasks. For example,
 * {@link SourceToCurves} merges the work of {@link SourceToInputs},
 * {@link InputsToGroundMotions}, and {@link GroundMotionsToCurves}. There are
 * also custom functions to handle special case cluster and system
 * {@code Source}s.
 *
 * @author Peter Powers
 * @see CalcFactory
 */
final class Transforms {

  /*
   * Source --> InputList
   *
   * Create a list of ground motion inputs from a source.
   */
  static final class SourceToInputs implements Function<Source, InputList> {

    private final Site site;

    SourceToInputs(Site site) {
      this.site = site;
    }

    @Override
    public SourceInputList apply(Source source) {
      SourceInputList hazardInputs = new SourceInputList(source);

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
            site.z1p0,
            site.z2p5);
        hazardInputs.add(input);
      }

      return hazardInputs;
    }
  }

  /*
   * InputList --> GroundMotions
   *
   * Calculate ground motions for a list of ground motion inputs.
   */
  static final class InputsToGroundMotions implements Function<InputList, GroundMotions> {

    private final Map<Imt, Map<Gmm, GroundMotionModel>> gmmTable;

    InputsToGroundMotions(Map<Imt, Map<Gmm, GroundMotionModel>> gmmTable) {
      this.gmmTable = gmmTable;
    }

    @Override
    public GroundMotions apply(InputList inputs) {

      Set<Imt> imtKeys = gmmTable.keySet();
      Set<Gmm> gmmKeys = gmmTable.get(imtKeys.iterator().next()).keySet();
      GroundMotions.Builder builder = GroundMotions.builder(
          inputs,
          imtKeys,
          gmmKeys);

      for (Imt imt : imtKeys) {
        for (Gmm gmm : gmmKeys) {
          GroundMotionModel model = gmmTable.get(imt).get(gmm);
          int inputIndex = 0;
          for (GmmInput gmmInput : inputs) {
            builder.add(imt, gmm, model.calc(gmmInput), inputIndex++);
          }
        }
      }
      return builder.build();
    }
  }

  /*
   * GroundMotions --> HazardCurves
   *
   * Derive hazard curves for a set of ground motions.
   */
  static final class GroundMotionsToCurves implements Function<GroundMotions, HazardCurves> {

    private final Map<Imt, XySequence> modelCurves;
    private final ExceedanceModel exceedanceModel;
    private final double truncationLevel;

    GroundMotionsToCurves(CalcConfig config) {
      this.modelCurves = config.curve.logModelCurves();
      this.exceedanceModel = config.curve.exceedanceModel;
      this.truncationLevel = config.curve.truncationLevel;
    }

    @Override
    public HazardCurves apply(GroundMotions groundMotions) {

      HazardCurves.Builder curveBuilder = HazardCurves.builder(groundMotions);

      for (Entry<Imt, XySequence> entry : modelCurves.entrySet()) {

        XySequence modelCurve = entry.getValue();
        Imt imt = entry.getKey();

        XySequence utilCurve = XySequence.copyOf(modelCurve);
        XySequence gmmCurve = XySequence.copyOf(modelCurve);

        for (Gmm gmm : groundMotions.μLists.get(imt).keySet()) {

          gmmCurve.clear();

          List<Double> means = groundMotions.μLists.get(imt).get(gmm);
          List<Double> sigmas = groundMotions.σLists.get(imt).get(gmm);

          for (int i = 0; i < means.size(); i++) {
            exceedanceModel.exceedance(
                means.get(i),
                sigmas.get(i),
                truncationLevel,
                imt,
                utilCurve);
            utilCurve.multiply(groundMotions.inputs.get(i).rate);
            gmmCurve.add(utilCurve);
          }
          curveBuilder.addCurve(imt, gmm, gmmCurve);
        }
      }
      return curveBuilder.build();
    }
  }

  /*
   * GroundMotions --> HazardCurves
   *
   * Derive hazard curves for a set of ground motions considering an additional
   * epistemic uncertinaty model.
   */
  static final class GroundMotionsToCurvesWithUncertainty implements
      Function<GroundMotions, HazardCurves> {

    private final GmmSet gmmSet;
    private final Map<Imt, XySequence> modelCurves;
    private final ExceedanceModel exceedanceModel;
    private final double truncationLevel;

    GroundMotionsToCurvesWithUncertainty(GmmSet gmmSet, CalcConfig config) {
      this.gmmSet = gmmSet;
      this.modelCurves = config.curve.logModelCurves();
      this.exceedanceModel = config.curve.exceedanceModel;
      this.truncationLevel = config.curve.truncationLevel;
    }

    @Override
    public HazardCurves apply(GroundMotions groundMotions) {

      HazardCurves.Builder curveBuilder = HazardCurves.builder(groundMotions);

      // initialize uncertainty for each input
      InputList inputs = groundMotions.inputs;
      double[] uncertainties = new double[inputs.size()];
      double[] rates = new double[inputs.size()];
      for (int i = 0; i < inputs.size(); i++) {
        HazardInput input = inputs.get(i);
        rates[i] = input.rate;
        uncertainties[i] = gmmSet.epiValue(input.Mw, input.rJB);
      }

      for (Entry<Imt, XySequence> entry : modelCurves.entrySet()) {

        XySequence modelCurve = entry.getValue();
        Imt imt = entry.getKey();

        XySequence utilCurve = XySequence.copyOf(modelCurve);
        XySequence gmmCurve = XySequence.copyOf(modelCurve);

        for (Gmm gmm : groundMotions.μLists.get(imt).keySet()) {

          gmmCurve.clear();

          List<Double> means = groundMotions.μLists.get(imt).get(gmm);
          List<Double> sigmas = groundMotions.σLists.get(imt).get(gmm);

          for (int i = 0; i < means.size(); i++) {
            double mean = means.get(i);
            double epi = uncertainties[i];
            double[] epiMeans = new double[] { mean - epi, mean, mean + epi };
            exceedanceCurve(
                epiMeans,
                sigmas.get(i),
                imt,
                utilCurve);
            utilCurve.multiply(groundMotions.inputs.get(i).rate);
            gmmCurve.add(utilCurve);
          }
          curveBuilder.addCurve(imt, gmm, gmmCurve);
        }
      }
      return curveBuilder.build();
    }

    /*
     * Construct an exceedance curve considering uncertain ground motions.
     */
    private XySequence exceedanceCurve(
        final double[] means,
        final double sigma,
        final Imt imt,
        final XySequence curve) {

      XySequence utilCurve = XySequence.emptyCopyOf(curve);
      double[] weights = gmmSet.epiWeights();
      for (int i = 0; i < means.length; i++) {
        exceedanceModel.exceedance(
            means[i],
            sigma,
            truncationLevel,
            imt,
            utilCurve);
        utilCurve.multiply(weights[i]);
        curve.add(utilCurve);
      }
      return curve;
    }
  }

  /*
   * Source --> HazardCurves
   *
   * Compute hazard curves for a source. This function coalesces the four
   * preceeding functions into one.
   */
  static final class SourceToCurves implements Function<Source, HazardCurves> {

    private final Function<Source, InputList> sourceToInputs;
    private final Function<InputList, GroundMotions> inputsToGroundMotions;
    private final Function<GroundMotions, HazardCurves> groundMotionsToCurves;

    SourceToCurves(
        SourceSet<? extends Source> sources,
        CalcConfig config,
        Site site) {

      GmmSet gmmSet = sources.groundMotionModels();
      Map<Imt, Map<Gmm, GroundMotionModel>> gmmTable = instances(
          config.curve.imts,
          gmmSet.gmms());

      this.sourceToInputs = new SourceToInputs(site);
      this.inputsToGroundMotions = new InputsToGroundMotions(gmmTable);
      this.groundMotionsToCurves = config.curve.gmmUncertainty && gmmSet.epiUncertainty()
          ? new GroundMotionsToCurvesWithUncertainty(gmmSet, config)
          : new GroundMotionsToCurves(config);
    }

    @Override
    public HazardCurves apply(Source source) {
      return groundMotionsToCurves.apply(
          inputsToGroundMotions.apply(
              sourceToInputs.apply(source)));
    }
  }

  /*
   * List<HazardCurves> --> HazardCurveSet
   *
   * Reduce multiple source curves.
   */
  static final class CurveConsolidator implements Function<List<HazardCurves>, HazardCurveSet> {

    private final SourceSet<? extends Source> sources;
    private final Map<Imt, XySequence> modelCurves;

    CurveConsolidator(
        SourceSet<? extends Source> sources,
        CalcConfig config) {

      this.sources = sources;
      this.modelCurves = config.curve.logModelCurves();
    }

    @Override
    public HazardCurveSet apply(List<HazardCurves> curvesList) {

      if (curvesList.isEmpty()) {
        return HazardCurveSet.empty(sources);
      }

      HazardCurveSet.Builder curveSetBuilder = HazardCurveSet.builder(
          sources,
          modelCurves);

      for (HazardCurves curves : curvesList) {
        curveSetBuilder.addCurves(curves);
      }
      return curveSetBuilder.build();
    }
  }

  /*
   * A note on system sources:
   *
   * SystemSourceSets contain many single sources and the functions here handle
   * them collectively. Rather than creating lists of input lists for each
   * source, one large input list is created. This may change if it is decided
   * to apply a magnitude distribution on each system source. This motivated
   * reordering of the SourceType enum such that SystemSourceSets are processed
   * first and granted a thread early in the calculation process.
   */

  /*
   * SYSTEM: SystemSourceSet --> HazardCurveSet
   *
   * Compute hazard curves for system sources. This function derives all inputs
   * for an entire SystemSourceSet before being composing them with standard
   * ground motion and hazard curve functions.
   */
  static final class SystemToCurves implements Function<SystemSourceSet, HazardCurveSet> {

    private final Site site;
    private final CalcConfig config;

    SystemToCurves(CalcConfig config, Site site) {
      this.site = site;
      this.config = config;
    }

    @Override
    public HazardCurveSet apply(SystemSourceSet sources) {

      InputList inputs = SystemSourceSet.toInputsFunction(site).apply(sources);
      if (inputs.isEmpty()) {
        return HazardCurveSet.empty(sources);
      }

      GmmSet gmmSet = sources.groundMotionModels();
      Map<Imt, Map<Gmm, GroundMotionModel>> gmmTable = instances(
          config.curve.imts,
          gmmSet.gmms());

      InputsToGroundMotions inputsToGm = new InputsToGroundMotions(gmmTable);
      GroundMotions gms = inputsToGm.apply(inputs);

      Function<GroundMotions, HazardCurves> gmToCurves =
          config.curve.gmmUncertainty && gmmSet.epiUncertainty()
              ? new GroundMotionsToCurvesWithUncertainty(gmmSet, config)
              : new GroundMotionsToCurves(config);
      HazardCurves curves = gmToCurves.apply(gms);

      CurveConsolidator consolidator = new CurveConsolidator(sources, config);
      return consolidator.apply(ImmutableList.of(curves));
    }
  }

  /*
   * SYSTEM: SystemSourceSet --> HazardCurveSet
   *
   * Compute hazard curves for system sources concurrently. This function
   * derives all inputs for an entire SystemSourceSet and partitions them before
   * composing them with standard ground motion and hazard curve functions.
   */
  static final class ParallelSystemToCurves implements Function<SystemSourceSet, HazardCurveSet> {

    private final Site site;
    private final Executor ex;
    private final CalcConfig config;

    ParallelSystemToCurves(
        Site site,
        CalcConfig config,
        Executor ex) {

      this.site = site;
      this.ex = ex;
      this.config = config;
    }

    @Override
    public HazardCurveSet apply(SystemSourceSet sources) {

      // create input list
      InputList master = SystemSourceSet.toInputsFunction(site).apply(sources);
      if (master.isEmpty()) {
        return HazardCurveSet.empty(sources);
      }

      // calculate curves from list in parallel
      InputsToCurves inputsToCurves = new InputsToCurves(sources, config);
      AsyncList<HazardCurves> asyncCurvesList = AsyncList.create();
      int size = config.performance.systemPartition;
      for (InputList partition : master.partition(size)) {
        asyncCurvesList.add(transform(
            immediateFuture(partition),
            inputsToCurves,
            ex));
      }
      List<HazardCurves> curvesList = getUnchecked(allAsList(asyncCurvesList));

      // combine and consolidate
      HazardCurves hazardCurves = HazardCurves.combine(master, curvesList);
      CurveConsolidator consolidator = new CurveConsolidator(sources, config);

      return consolidator.apply(ImmutableList.of(hazardCurves));
    }
  }

  /*
   * SYSTEM: InputList --> HazardCurves
   *
   * Compute hazard curves from an input list. Although this function is
   * generally applicable to all source types, it is presently only used to
   * process partitioned input lists derived from a system source.
   */
  static final class InputsToCurves implements Function<InputList, HazardCurves> {

    private final Function<InputList, GroundMotions> inputsToGroundMotions;
    private final Function<GroundMotions, HazardCurves> groundMotionsToCurves;

    InputsToCurves(
        SourceSet<? extends Source> sources,
        CalcConfig config) {

      GmmSet gmmSet = sources.groundMotionModels();
      Map<Imt, Map<Gmm, GroundMotionModel>> gmmTable = instances(
          config.curve.imts,
          gmmSet.gmms());

      this.inputsToGroundMotions = new InputsToGroundMotions(gmmTable);
      this.groundMotionsToCurves = config.curve.gmmUncertainty && gmmSet.epiUncertainty()
          ? new GroundMotionsToCurvesWithUncertainty(gmmSet, config)
          : new GroundMotionsToCurves(config);
    }

    @Override
    public HazardCurves apply(InputList inputs) {
      return groundMotionsToCurves.apply(inputsToGroundMotions.apply(inputs));
    }
  }

  /*
   * CLUSTER: ClusterSource --> ClusterInputs
   *
   * Create a list of ground motion inputs from a cluster source.
   */
  static final class ClusterSourceToInputs implements Function<ClusterSource, ClusterInputs> {

    private final SourceToInputs transform;

    ClusterSourceToInputs(Site site) {
      transform = new SourceToInputs(site);
    }

    @Override
    public ClusterInputs apply(ClusterSource clusterSource) {
      ClusterInputs clusterInputs = new ClusterInputs(clusterSource);
      for (FaultSource faultSource : clusterSource.faults()) {
        clusterInputs.add(transform.apply(faultSource));
      }
      return clusterInputs;
    }
  }

  /*
   * CLUSTER: ClusterInputs --> ClusterGroundMotions
   *
   * Calculate ground motions for a list of cluster ground motion inputs.
   */
  static final class ClusterInputsToGroundMotions implements
      Function<ClusterInputs, ClusterGroundMotions> {

    private final InputsToGroundMotions transform;

    ClusterInputsToGroundMotions(Map<Imt, Map<Gmm, GroundMotionModel>> gmmTable) {
      transform = new InputsToGroundMotions(gmmTable);
    }

    @Override
    public ClusterGroundMotions apply(ClusterInputs clusterInputs) {
      ClusterGroundMotions clusterGroundMotions = new ClusterGroundMotions(clusterInputs.parent);
      for (SourceInputList hazardInputs : clusterInputs) {
        clusterGroundMotions.add(transform.apply(hazardInputs));
      }
      return clusterGroundMotions;
    }
  }

  /*
   * CLUSTER: ClusterGroundMotions --> ClusterCurves
   *
   * Collapse magnitude variants and compute the joint probability of exceedence
   * for sources in a cluster. Note that this is only to be used with cluster
   * sources as the weight of each magnitude variant is stored in the
   * HazardInput.rate field, which is kinda KLUDGY, but works.
   */
  static final class ClusterGroundMotionsToCurves implements
      Function<ClusterGroundMotions, ClusterCurves> {

    private final Map<Imt, XySequence> logModelCurves;
    private final ExceedanceModel exceedanceModel;
    private final double truncationLevel;

    ClusterGroundMotionsToCurves(CalcConfig config) {
      this.logModelCurves = config.curve.logModelCurves();
      this.exceedanceModel = config.curve.exceedanceModel;
      this.truncationLevel = config.curve.truncationLevel;
    }

    @Override
    public ClusterCurves apply(ClusterGroundMotions clusterGroundMotions) {

      Builder builder = ClusterCurves.builder(clusterGroundMotions);

      for (Entry<Imt, XySequence> entry : logModelCurves.entrySet()) {

        XySequence modelCurve = entry.getValue();
        Imt imt = entry.getKey();

        // aggregator of curves for each fault in a cluster
        ListMultimap<Gmm, XySequence> faultCurves = MultimapBuilder
            .enumKeys(Gmm.class)
            .arrayListValues(clusterGroundMotions.size())
            .build();
        XySequence utilCurve = XySequence.copyOf(modelCurve);

        for (GroundMotions groundMotions : clusterGroundMotions) {

          Map<Gmm, List<Double>> gmmMeans = groundMotions.μLists.get(imt);
          Map<Gmm, List<Double>> gmmSigmas = groundMotions.σLists.get(imt);

          for (Gmm gmm : gmmMeans.keySet()) {
            XySequence magVarCurve = XySequence.copyOf(modelCurve);
            List<Double> means = gmmMeans.get(gmm);
            List<Double> sigmas = gmmSigmas.get(gmm);
            for (int i = 0; i < groundMotions.inputs.size(); i++) {
              exceedanceModel.exceedance(
                  means.get(i),
                  sigmas.get(i),
                  truncationLevel,
                  imt,
                  utilCurve);
              utilCurve.multiply(groundMotions.inputs.get(i).rate);
              magVarCurve.add(utilCurve);
            }
            faultCurves.put(gmm, magVarCurve);
          }
        }

        double rate = clusterGroundMotions.parent.rate();
        for (Gmm gmm : faultCurves.keySet()) {
          XySequence clusterCurve = ExceedanceModel.clusterExceedance(faultCurves.get(gmm));
          builder.addCurve(imt, gmm, clusterCurve.multiply(rate));
        }
      }

      return builder.build();
    }
  }

  /*
   * CLUSTER: ClusterSource --> ClusterCurves
   *
   * Compute hazard curves for a cluster source. This function coalesces the
   * three preceeding functions into one.
   */
  static final class ClusterToCurves implements Function<ClusterSource, ClusterCurves> {

    private final Function<ClusterSource, ClusterInputs> sourceToInputs;
    private final Function<ClusterInputs, ClusterGroundMotions> inputsToGroundMotions;
    private final Function<ClusterGroundMotions, ClusterCurves> groundMotionsToCurves;

    ClusterToCurves(
        ClusterSourceSet sources,
        CalcConfig config,
        Site site) {

      Set<Gmm> gmms = sources.groundMotionModels().gmms();
      Map<Imt, Map<Gmm, GroundMotionModel>> gmmTable = instances(config.curve.imts, gmms);

      this.sourceToInputs = new ClusterSourceToInputs(site);
      this.inputsToGroundMotions = new ClusterInputsToGroundMotions(gmmTable);
      this.groundMotionsToCurves = new ClusterGroundMotionsToCurves(config);
    }

    @Override
    public ClusterCurves apply(ClusterSource source) {
      return groundMotionsToCurves.apply(
          inputsToGroundMotions.apply(
              sourceToInputs.apply(source)));
    }
  }

  /*
   * CLUSTER: List<ClusterCurves> --> HazardCurveSet
   *
   * Reduce multiple cluster source curves.
   */
  static final class ClusterCurveConsolidator implements
      Function<List<ClusterCurves>, HazardCurveSet> {

    private final ClusterSourceSet sources;
    private final Map<Imt, XySequence> modelCurves;

    ClusterCurveConsolidator(
        ClusterSourceSet sources,
        CalcConfig config) {

      this.sources = sources;
      this.modelCurves = config.curve.logModelCurves();
    }

    @Override
    public HazardCurveSet apply(List<ClusterCurves> curvesList) {

      if (curvesList.isEmpty()) {
        return HazardCurveSet.empty(sources);
      }

      HazardCurveSet.Builder curveSetBuilder = HazardCurveSet.builder(
          sources,
          modelCurves);

      for (ClusterCurves curves : curvesList) {
        curveSetBuilder.addCurves(curves);
      }
      return curveSetBuilder.build();
    }
  }

  /*
   * ALL: List<HazardCurveSet> --> HazardResult
   *
   * Final 'fan-in' consolidator function used for all source types.
   */
  static final class CurveSetConsolidator implements Function<List<HazardCurveSet>, Hazard> {

    private final HazardModel model;
    private final CalcConfig config;
    private final Site site;

    CurveSetConsolidator(
        HazardModel model,
        CalcConfig config,
        Site site) {

      this.model = model;
      this.config = config;
      this.site = site;
    }

    @Override
    public Hazard apply(List<HazardCurveSet> curveSetList) {

      Hazard.Builder resultBuilder = Hazard.builder(config)
          .model(model)
          .site(site);

      for (HazardCurveSet curveSet : curveSetList) {
        if (curveSet.isEmpty()) {
          continue;
        }
        resultBuilder.addCurveSet(curveSet);
      }
      return resultBuilder.build();
    }
  }

}
