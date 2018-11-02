package gov.usgs.earthquake.nshmp.calc;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.util.concurrent.Futures.allAsList;
import static com.google.common.util.concurrent.Futures.getUnchecked;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static com.google.common.util.concurrent.Futures.transform;
import static gov.usgs.earthquake.nshmp.gmm.Gmm.instances;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;

import gov.usgs.earthquake.nshmp.calc.ClusterCurves.Builder;
import gov.usgs.earthquake.nshmp.data.Data;
import gov.usgs.earthquake.nshmp.data.XySequence;
import gov.usgs.earthquake.nshmp.eq.fault.Faults;
import gov.usgs.earthquake.nshmp.eq.fault.surface.RuptureSurface;
import gov.usgs.earthquake.nshmp.eq.model.ClusterSource;
import gov.usgs.earthquake.nshmp.eq.model.ClusterSourceSet;
import gov.usgs.earthquake.nshmp.eq.model.Distance;
import gov.usgs.earthquake.nshmp.eq.model.FaultSource;
import gov.usgs.earthquake.nshmp.eq.model.GmmSet;
import gov.usgs.earthquake.nshmp.eq.model.HazardModel;
import gov.usgs.earthquake.nshmp.eq.model.Rupture;
import gov.usgs.earthquake.nshmp.eq.model.Source;
import gov.usgs.earthquake.nshmp.eq.model.SourceSet;
import gov.usgs.earthquake.nshmp.eq.model.SystemSourceSet;
import gov.usgs.earthquake.nshmp.gmm.Gmm;
import gov.usgs.earthquake.nshmp.gmm.GmmInput;
import gov.usgs.earthquake.nshmp.gmm.GroundMotionModel;
import gov.usgs.earthquake.nshmp.gmm.Imt;
import gov.usgs.earthquake.nshmp.gmm.MultiScalarGroundMotion;
import gov.usgs.earthquake.nshmp.gmm.ScalarGroundMotion;

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

    private final GmmProcessor gmmProcessor;
    private final Map<Imt, Map<Gmm, GroundMotionModel>> gmmTable;

    InputsToGroundMotions(
        CalcConfig config,
        Map<Imt, Map<Gmm, GroundMotionModel>> gmmTable) {
      this.gmmProcessor = GmmProcessor.instance(config);
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
          for (GmmInput gmmInput : inputs) {
            builder.add(
                imt,
                gmm,
                gmmProcessor.apply(model, gmmInput, imt, gmm));
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
      this.modelCurves = config.hazard.logModelCurves();
      this.exceedanceModel = config.hazard.exceedanceModel;
      this.truncationLevel = config.hazard.truncationLevel;
    }

    @Override
    public HazardCurves apply(GroundMotions gms) {

      HazardCurves.Builder curveBuilder = HazardCurves.builder(gms);

      for (Entry<Imt, Map<Gmm, List<ScalarGroundMotion>>> imtEntry : gms.gmMap.entrySet()) {

        Imt imt = imtEntry.getKey();
        XySequence modelCurve = modelCurves.get(imt);
        XySequence gmmCurve = XySequence.copyOf(modelCurve);

        XySequence utilCurve = XySequence.emptyCopyOf(modelCurve);

        /*
         * Exceedance methods always put result in supplied curve and are
         * responsible for 'clearing' it before use if needed.
         */

        for (Entry<Gmm, List<ScalarGroundMotion>> gmmEntry : imtEntry.getValue().entrySet()) {
          gmmCurve.clear();
          int i = 0;
          for (ScalarGroundMotion sgm : gmmEntry.getValue()) {

            double rate = gms.inputs.get(i++).rate;

            if (sgm instanceof MultiScalarGroundMotion) {

              exceedanceModel.treeExceedanceCombined(
                  (MultiScalarGroundMotion) sgm,
                  truncationLevel,
                  imt,
                  utilCurve);

            } else {

              exceedanceModel.exceedance(
                  sgm.mean(),
                  sgm.sigma(),
                  truncationLevel,
                  imt,
                  utilCurve);
            }

            utilCurve.multiply(rate);
            gmmCurve.add(utilCurve);

          }
          curveBuilder.addCurve(imt, gmmEntry.getKey(), gmmCurve);
        }
      }
      return curveBuilder.build();
    }
  }

  /*
   * GroundMotions --> HazardCurves (+epi)
   *
   * Derive hazard curves for a set of ground motions considering an additional
   * epistemic uncertainty model.
   */
  static final class GroundMotionsToCurvesWithUncertainty implements
      Function<GroundMotions, HazardCurves> {

    private final GmmSet gmmSet;
    private final Map<Imt, XySequence> modelCurves;
    private final ExceedanceModel exceedanceModel;
    private final double truncationLevel;

    GroundMotionsToCurvesWithUncertainty(GmmSet gmmSet, CalcConfig config) {
      this.gmmSet = gmmSet;
      this.modelCurves = config.hazard.logModelCurves();
      this.exceedanceModel = config.hazard.exceedanceModel;
      this.truncationLevel = config.hazard.truncationLevel;
    }

    @Override
    public HazardCurves apply(GroundMotions gms) {

      HazardCurves.Builder curveBuilder = HazardCurves.builder(gms);

      // initialize uncertainty for each input
      InputList inputs = gms.inputs;
      double[] uncertainties = new double[inputs.size()];
      double[] rates = new double[inputs.size()];
      for (int i = 0; i < inputs.size(); i++) {
        HazardInput input = inputs.get(i);
        rates[i] = input.rate;
        uncertainties[i] = gmmSet.epiValue(input.Mw, input.rJB);
      }

      for (Entry<Imt, Map<Gmm, List<ScalarGroundMotion>>> imtEntry : gms.gmMap.entrySet()) {

        Imt imt = imtEntry.getKey();
        XySequence modelCurve = modelCurves.get(imt);
        XySequence utilCurve = XySequence.copyOf(modelCurve);
        XySequence gmmCurve = XySequence.copyOf(modelCurve);

        for (Entry<Gmm, List<ScalarGroundMotion>> gmmEntry : imtEntry.getValue().entrySet()) {
          gmmCurve.clear();
          int i = 0;
          for (ScalarGroundMotion gm : gmmEntry.getValue()) {
            double mean = gm.mean();
            double epi = uncertainties[i];
            double[] epiMeans = new double[] { mean - epi, mean, mean + epi };
            exceedanceCurve(
                epiMeans,
                gm.sigma(),
                imt,
                utilCurve.clear());
            utilCurve.multiply(inputs.get(i++).rate);
            gmmCurve.add(utilCurve);
          }
          curveBuilder.addCurve(imt, gmmEntry.getKey(), gmmCurve);
        }
      }
      return curveBuilder.build();
    }

    /*
     * Construct an exceedance curve considering uncertain ground motions.
     * 
     * TODO this has not yet been refactored to accomodate a ScalarGroundMotion
     * object as we know this is only used in WUS whereas sgm refactoring was
     * done (experimentally) to handle NGA-East in the CEUS.
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
          config.hazard.imts,
          gmmSet.gmms());

      this.sourceToInputs = new SourceToInputs(site);
      this.inputsToGroundMotions = new InputsToGroundMotions(config, gmmTable);
      this.groundMotionsToCurves = config.hazard.gmmUncertainty && gmmSet.epiUncertainty()
          ? new GroundMotionsToCurvesWithUncertainty(gmmSet, config)
          : new GroundMotionsToCurves(config);
    }

    @Override
    public HazardCurves apply(Source source) {
      return sourceToInputs
          .andThen(inputsToGroundMotions)
          .andThen(groundMotionsToCurves)
          .apply(source);
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
      this.modelCurves = config.hazard.logModelCurves();
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
          config.hazard.imts,
          gmmSet.gmms());

      InputsToGroundMotions inputsToGm = new InputsToGroundMotions(config, gmmTable);
      GroundMotions gms = inputsToGm.apply(inputs);

      Function<GroundMotions, HazardCurves> gmToCurves =
          config.hazard.gmmUncertainty && gmmSet.epiUncertainty()
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
            inputsToCurves::apply,
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
          config.hazard.imts,
          gmmSet.gmms());

      this.inputsToGroundMotions = new InputsToGroundMotions(config, gmmTable);
      this.groundMotionsToCurves = config.hazard.gmmUncertainty && gmmSet.epiUncertainty()
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

    ClusterInputsToGroundMotions(
        CalcConfig config,
        Map<Imt, Map<Gmm, GroundMotionModel>> gmmTable) {
      transform = new InputsToGroundMotions(config, gmmTable);
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
      this.logModelCurves = config.hazard.logModelCurves();
      this.exceedanceModel = config.hazard.exceedanceModel;
      this.truncationLevel = config.hazard.truncationLevel;
    }

    @Override
    public ClusterCurves apply(ClusterGroundMotions clusterGroundMotions) {

      Builder builder = ClusterCurves.builder(clusterGroundMotions);

      for (Entry<Imt, XySequence> entry : logModelCurves.entrySet()) {

        XySequence modelCurve = entry.getValue();
        Imt imt = entry.getKey();

        /*
         * TODO: this is klunky; we need to get and store the gmm branch weights
         * so that they can be applied AFTER the cluster calculations have been
         * done for each gmm branch. Is there a better way? SHould Gmms that
         * return MultiScalarGroundMotions be able to supply period dependent
         * weights?
         */
        ScalarGroundMotion sgmModel = clusterGroundMotions.get(0).gmMap
            .values().iterator().next() // first IMT entry
            .values().iterator().next() // first GMM entry
            .get(0);

        if (sgmModel instanceof MultiScalarGroundMotion) {

          /* Aggregator of curves for each fault in a cluster. */
          ListMultimap<Gmm, List<XySequence>> faultCurves = MultimapBuilder
              .enumKeys(Gmm.class)
              .arrayListValues(clusterGroundMotions.size())
              .build();
          XySequence utilCurve = XySequence.emptyCopyOf(modelCurve);

          MultiScalarGroundMotion msgmModel = (MultiScalarGroundMotion) sgmModel;
          double[] gmmBranchWeights = weightList(
              msgmModel.meanWeights(),
              msgmModel.sigmaWeights());

          for (GroundMotions groundMotions : clusterGroundMotions) {

            Map<Gmm, List<ScalarGroundMotion>> gmmGmMap = groundMotions.gmMap.get(imt);

            for (Gmm gmm : gmmGmMap.keySet()) {

              /* Gmm branch lists of magnitude variants. */
              List<ScalarGroundMotion> sgms = gmmGmMap.get(gmm);
              List<List<XySequence>> magCurves = new ArrayList<>(sgms.size());
              for (int i = 0; i < sgms.size(); i++) { // Fault mag variants

                /* Gmm tree of exceedance curves for each magnitude variant. */
                MultiScalarGroundMotion msgm = (MultiScalarGroundMotion) sgms.get(i);
                List<XySequence> magTreeCurves = exceedanceModel.treeExceedance(
                    msgm,
                    truncationLevel,
                    imt,
                    utilCurve);
                
                /* Scale by magnitude weight and agreggate. */
                double magWt = groundMotions.inputs.get(i).rate;
                magTreeCurves.forEach(xy -> xy.multiply(magWt));
                magCurves.add(magTreeCurves);
              }
              
              /* Combine magnitude variants and collect. */
              List<XySequence> faultTreeCurves = reduce(magCurves, Transforms::sum);
              faultCurves.put(gmm, faultTreeCurves);
            }
          }

          /* Cluster exceedance */
          double rate = clusterGroundMotions.parent.rate();
          for (Gmm gmm : faultCurves.keySet()) {

            /* Combine cluster fault exceedances on each gmm branch. */

            List<XySequence> clusterTreeCurves = reduce(
                faultCurves.get(gmm),
                ExceedanceModel::clusterExceedance);

            /* Scale gmm branches by weight and combine. */
            XySequence clusterCurve = weightedSum(clusterTreeCurves, gmmBranchWeights);
            
            /* Scale to cluster rate and save. */
            clusterCurve.multiply(rate);
            builder.addCurve(imt, gmm, clusterCurve);
          }

        } else {

          /* Aggregator of curves for each fault in a cluster */
          ListMultimap<Gmm, XySequence> faultCurves = MultimapBuilder
              .enumKeys(Gmm.class)
              .arrayListValues(clusterGroundMotions.size())
              .build();
          XySequence utilCurve = XySequence.emptyCopyOf(modelCurve);

          for (GroundMotions groundMotions : clusterGroundMotions) {

            Map<Gmm, List<ScalarGroundMotion>> gmmGmMap = groundMotions.gmMap.get(imt);

            for (Gmm gmm : gmmGmMap.keySet()) {
              XySequence magVarCurve = XySequence.emptyCopyOf(modelCurve);
              List<ScalarGroundMotion> sgms = gmmGmMap.get(gmm);
              for (int i = 0; i < sgms.size(); i++) {
                ScalarGroundMotion sgm = sgms.get(i);
                exceedanceModel.exceedance(
                    sgm.mean(),
                    sgm.sigma(),
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
      }
      return builder.build();
    }
  }

  /* Iteration order must match that in ExceedanceModel.treeExceedance() */
  private static double[] weightList(double[] μWts, double[] σWts) {
    double[] wts = new double[μWts.length * σWts.length];
    for (int i = 0; i < μWts.length; i++) {
      for (int j = 0; j < σWts.length; j++) {
        wts[σWts.length * i + j] = μWts[i] * σWts[j];
      }
    }
    return wts;
  }

  /*
   * Reduce sequences across indices of nested lists. It is assumed that the
   * nested lists are all the same length and are not empty.
   */
  static List<XySequence> reduce(
      List<List<XySequence>> lists,
      Function<List<XySequence>, XySequence> reducer) {

    List<List<XySequence>> transposed = transpose(lists);
    List<XySequence> reduced = new ArrayList<>(transposed.size());
    for (List<XySequence> list : transposed) {
      reduced.add(reducer.apply(list));
    }
    return reduced;
  }

  /* Transpose the supplied lists. */
  static <T> List<List<T>> transpose(List<List<T>> lists) {
    int transSize = lists.get(0).size();
    ArrayList<List<T>> transposed = new ArrayList<>(transSize);
    for (int i = 0; i < transSize; i++) {
      ArrayList<T> nested = new ArrayList<>(lists.size());
      for (int j = 0; j < lists.size(); j++) {
        nested.add(lists.get(j).get(i));
      }
      transposed.add(nested);
    }
    return transposed;
  }

  /*
   * Returns a new sequence with the sum of the supplied sequences. Method
   * assumes all sequences are the same size and not empty.
   */
  static XySequence sum(List<XySequence> sequences) {
    XySequence reduced = XySequence.emptyCopyOf(sequences.get(0));
    sequences.forEach(reduced::add);
    return reduced;
  }

  static XySequence weightedSum(List<XySequence> sequences, double[] weights) {
    checkArgument(sequences.size() == weights.length);
    XySequence combined = XySequence.emptyCopyOf(sequences.get(0));
    for (int i = 0; i < sequences.size(); i++) {
      combined.add(sequences.get(i).multiply(weights[i]));
    }
    return combined;
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
      Map<Imt, Map<Gmm, GroundMotionModel>> gmmTable = instances(config.hazard.imts, gmms);

      this.sourceToInputs = new ClusterSourceToInputs(site);
      this.inputsToGroundMotions = new ClusterInputsToGroundMotions(config, gmmTable);
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
      this.modelCurves = config.hazard.logModelCurves();
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
