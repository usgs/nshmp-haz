package gov.usgs.earthquake.nshmp;

import static com.google.common.base.StandardSystemProperty.LINE_SEPARATOR;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import gov.usgs.earthquake.nshmp.gmm.Gmm;
import gov.usgs.earthquake.nshmp.gmm.GmmInput;
import gov.usgs.earthquake.nshmp.gmm.GroundMotionModel;
import gov.usgs.earthquake.nshmp.gmm.Imt;
import gov.usgs.earthquake.nshmp.gmm.ScalarGroundMotion;

/**
 * Entry point for computing deterministic response spectra.
 *
 * <p>In addition to a {@code main()} method, several utility methods are
 * provided for different target users. For instance
 * {@link #groundMotion(Gmm, Imt, GmmInput)} and
 * {@link #spectrum(Gmm, GmmInput)} are convenient for use within Matlab as they
 * return simple data container objects that are automatically converted to
 * Matlab structs and arrays. {@link #spectra(Set, GmmInput, boolean)} returns a
 * more complex result and is for use with web services.
 *
 * @author Peter Powers
 */
public class ResponseSpectra {

  private static final double PGA_PERIOD = 0.001;

  /**
   * Compute the median ground motion and its standard deviation for a specified
   * {@link GroundMotionModel}, intensity measure type ({@link Imt} ), and
   * source and site parameterization ({@link GmmInput}).
   *
   * <p>{@code enum} types are identified in matlab as e.g. {@link Gmm#ASK_14}.
   *
   * @param model to use
   * @param imt intensity measure type (e.g. {@code PGA}, {@code SA1P00})
   * @param source and site parameterization
   * @return a two-element double[] containing the natural log of the median
   *         ground motion and its standard deviation
   */
  public static double[] groundMotion(Gmm model, Imt imt, GmmInput source) {
    ScalarGroundMotion sgm = model.instance(imt).calc(source);
    return new double[] { sgm.mean(), sgm.sigma() };
  }

  /**
   * Compute a spectrum of ground motions and their standard deviations for a
   * specified {@link GroundMotionModel} and source and site parameterization (
   * {@link GmmInput}). All spectral periods supported by the model are
   * returned.
   *
   * <p>This method is intended for use with Matlab, which converts
   * {@code Result} to a struct automatically.
   *
   * <p>{@code enum} types are identified in matlab as e.g. {@link Gmm#ASK_14}.
   *
   * @param model to use
   * @param input source and site parameterization
   * @return a Result
   */
  public static Result spectrum(Gmm model, GmmInput input) {
    Set<Imt> imts = model.responseSpectrumIMTs();
    Result spectrum = new Result(imts.size());
    int i = 0;
    for (Imt imt : imts) {
      ScalarGroundMotion sgm = model.instance(imt).calc(input);
      spectrum.periods[i] = imt.period();
      spectrum.means[i] = sgm.mean();
      spectrum.sigmas[i] = sgm.sigma();
      i++;
    }
    return spectrum;
  }

  /** The result produced by calling {@link #spectrum(Gmm, GmmInput)}. */
  public static class Result {

    /** Spectral periods. */
    public final double[] periods;

    /** Ground motion means. */
    public final double[] means;

    /** Ground motion sigmas. */
    public final double[] sigmas;

    Result(int size) {
      periods = new double[size];
      means = new double[size];
      sigmas = new double[size];
    }
  }

  /**
   * Compute the spectra of ground motions and their standard deviations for
   * multiple models and a source. This method provides the option to compute
   * ground motion values either for the set of common spectral accelerations
   * supported by the {@link Gmm}s specified, or for every spectral acceleration
   * supported by each {@link Gmm}. PGA is included in the results with a
   * spectral period of 0.001s.
   *
   * @param gmms {@code GroundMotionModel}s to use
   * @param input source and site parameterization
   * @param commonImts {@code true} if only ground motions corresponding to the
   *        spectral accelerations common to all {@code gmms} should be
   *        computed; {@code false} if all spectral accelerations supported by
   *        each gmm should be used.
   * @return a {@link MultiResult} data container
   */
  public static MultiResult spectra(Set<Gmm> gmms, GmmInput input, boolean commonImts) {

    Map<Gmm, List<Double>> periodMap = Maps.newEnumMap(Gmm.class);
    Map<Gmm, List<Double>> meanMap = Maps.newEnumMap(Gmm.class);
    Map<Gmm, List<Double>> sigmaMap = Maps.newEnumMap(Gmm.class);

    /*
     * NOTE: At present, program assumes that all supplied Gmms support PGA.
     * Although most currently implemented models do, this may not be the case
     * in the future and program may produce unexpected results.
     */

    /* Common imts and periods; may not be used. */
    Set<Imt> saImts = Gmm.responseSpectrumIMTs(gmms);
    ImmutableList<Double> periods = ImmutableList.<Double> builder()
        .add(PGA_PERIOD)
        .addAll(Imt.periods(saImts))
        .build();

    for (Gmm gmm : gmms) {
      if (!commonImts) {
        saImts = gmm.responseSpectrumIMTs();
        periods = ImmutableList.<Double> builder()
            .add(PGA_PERIOD)
            .addAll(Imt.periods(saImts))
            .build();
      }
      ImmutableList.Builder<Double> means = ImmutableList.builder();
      ImmutableList.Builder<Double> sigmas = ImmutableList.builder();

      ScalarGroundMotion pgaGm = gmm.instance(Imt.PGA).calc(input);
      means.add(pgaGm.mean());
      sigmas.add(pgaGm.sigma());

      for (Imt imt : saImts) {
        ScalarGroundMotion sgm = gmm.instance(imt).calc(input);
        means.add(sgm.mean());
        sigmas.add(sgm.sigma());
      }
      periodMap.put(gmm, periods);
      meanMap.put(gmm, means.build());
      sigmaMap.put(gmm, sigmas.build());
    }

    return new MultiResult(
        Maps.immutableEnumMap(periodMap),
        Maps.immutableEnumMap(meanMap),
        Maps.immutableEnumMap(sigmaMap));
  }

  /** The result of calling {@link #spectra(Set, GmmInput, boolean)}. */
  public static class MultiResult {

    /** Spectral periods. */
    public final Map<Gmm, List<Double>> periods;

    /** Map of ground motion means. */
    public final Map<Gmm, List<Double>> means;

    /** Map of ground motion sigmas. */
    public final Map<Gmm, List<Double>> sigmas;

    MultiResult(
        Map<Gmm, List<Double>> periods,
        Map<Gmm, List<Double>> means,
        Map<Gmm, List<Double>> sigmas) {
      this.periods = periods;
      this.means = means;
      this.sigmas = sigmas;
    }
  }

  /**
   * Entry point for computing deterministic response spectra from the command
   * line. Quite a few arguments are required to specify the GroundMotionModel
   * to use and parameterize the earthquake source and site of interest. Example
   * usage:
   *
   * <pre>
   * java -cp nshmp-haz.jar org.opensha.programs.DeterministicSpectra ...
   *   ... ASK_14 6.5 10.0 10.3 10.0 90.0 14.0 0.5 7.5 0.0 760.0 true NaN NaN
   * </pre>
   *
   * @param args
   *        {@code [Gmm  mag rJB  rRup rX dip width zTop zHyp rake vs30 vsInf z1p0 z2p5]}
   */
  public static void main(String[] args) {
    String result = calcMain(args);
    System.out.println(result);
    System.exit(0);
  }

  private static String calcMain(String[] args) {
    if (args.length != 14) {
      System.err.println(USAGE);
      System.exit(1);
    }
    Gmm gmm = Gmm.valueOf(args[0]);
    GmmInput input = GmmInput.builder()
        .mag(Double.valueOf(args[1]))
        .rJB(Double.valueOf(args[2]))
        .rRup(Double.valueOf(args[3]))
        .rX(Double.valueOf(args[4]))
        .dip(Double.valueOf(args[5]))
        .width(Double.valueOf(args[6]))
        .zTop(Double.valueOf(args[7]))
        .zHyp(Double.valueOf(args[8]))
        .rake(Double.valueOf(args[9]))
        .vs30(Double.valueOf(args[10]))
        .vsInf(Boolean.valueOf(args[11]))
        .z1p0(Double.valueOf(args[12]))
        .z2p5(Double.valueOf(args[13]))
        .build();
    Result result = spectrum(gmm, input);
    StringBuilder sb = new StringBuilder();
    sb.append("periods=").append(Arrays.toString(result.periods));
    sb.append(LINE_SEPARATOR.value());
    sb.append("means=").append(Arrays.toString(result.means));
    sb.append(LINE_SEPARATOR.value());
    sb.append("sigmas=").append(Arrays.toString(result.sigmas));
    return sb.toString();
  }

  private static final String USAGE = "DeterministicSpectra usage:" +
      LINE_SEPARATOR.value() +
      LINE_SEPARATOR.value() +
      "command: java -cp nshmp-haz.jar org.opensha.programs.DeterministicSpectra Gmm mag rJB rRup rX dip width zTop zHyp rake vs30 vsInf z1p0 z2p5" +
      LINE_SEPARATOR.value() +
      "example: java -cp nshmp-haz.jar org.opensha.programs.DeterministicSpectra ASK_14 6.5 10.0 10.3 10.0 90.0 14.0 0.5 7.5 0.0 760.0 true NaN NaN" +
      LINE_SEPARATOR.value() +
      LINE_SEPARATOR.value() +
      "  - For more details, see: http://usgs.github.io/nshmp-haz/docs/org/opensha2/programs/DeterministicSpectra.html";

}
