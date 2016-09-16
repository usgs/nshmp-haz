package org.opensha2.calc;

import static java.lang.Math.exp;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.APPEND;

import static org.opensha2.internal.TextUtils.NEWLINE;

import org.opensha2.calc.CalcConfig.Deagg.Bins;
import org.opensha2.calc.DeaggContributor.SourceSetContributor;
import org.opensha2.calc.DeaggContributor.SystemContributor;
import org.opensha2.data.Data;
import org.opensha2.eq.model.SourceType;
import org.opensha2.internal.MathUtils;
import org.opensha2.internal.Parsing;
import org.opensha2.internal.Parsing.Delimiter;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.base.Strings;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Ordering;
import com.google.gson.annotations.SerializedName;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Deaggregation dataset exporter. This class handles String/Text and JSON
 * output of DeaggDatasets. JSON output is supported through the serialization
 * of custom data containers. String output is supported through the
 * {@code toString()} methods of those objects.
 *
 * @author Peter Powers
 */
final class DeaggExport {

  final transient DeaggDataset ddTotal;
  final transient DeaggDataset dd;
  final transient DeaggConfig dc;
  final transient String id;
  final transient EpsilonBins εBins;
  final transient String discretization;

  final DistanceMagnitudeData data;
  final SummaryElements summary;

  /*
   * All component DeaggDatasets require data from the final total DeaggDataset
   * to correctly calculate contributions and represent summary data that is not
   * specific to the component dataset.
   */

  DeaggExport(DeaggDataset ddTotal, DeaggDataset dd, DeaggConfig dc, String id) {
    this.ddTotal = ddTotal;
    this.dd = dd;
    this.dc = dc;
    this.id = id;
    εBins = createEpsilonBins(ddTotal.rmε.levels(), ddTotal.rmε.levelΔ());
    discretization = createDataDiscretization(dc);
    summary = createSummaryElements(ddTotal, dd, dc);
    data = createDistanceMagnitudeData(ddTotal, dd);
    // TODO need contributions to be JSON serializable
  }

  void write(Path dir, String site) throws IOException {

    Path dataPath = dir.resolve(site + "-data.csv");
    Files.write(dataPath, data.toString().getBytes(UTF_8));

    Path summaryPath = dir.resolve(site + "-summary.txt");
    String contribString = appendContributions(
        new StringBuilder(),
        ddTotal,
        dd,
        dc.contributorLimit).toString();

    String header = new StringBuilder()
        .append(NEWLINE)
        .append("Component: ")
        .append(id)
        .append(NEWLINE)
        .append(DATASET_SEPARATOR)
        .toString();

    Files.write(summaryPath, SECTION_SEPARATOR.getBytes(UTF_8), APPEND);
    Files.write(summaryPath, header.getBytes(UTF_8));
    Files.write(summaryPath, summary.toString().getBytes(UTF_8), APPEND);
    if (dd.binned > 0.0) {
      Files.write(summaryPath, discretization.toString().getBytes(UTF_8), APPEND);
      Files.write(summaryPath, εBins.toString().getBytes(UTF_8), APPEND);
    }
    Files.write(summaryPath, SECTION_SEPARATOR.getBytes(UTF_8), APPEND);
    Files.write(summaryPath, contribString.toString().getBytes(UTF_8), APPEND);
    Files.write(summaryPath, DATASET_SEPARATOR.getBytes(UTF_8), APPEND);
    // TODO system MFDs
    Files.write(summaryPath, SECTION_SEPARATOR.getBytes(UTF_8), APPEND);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder()
        .append(SECTION_SEPARATOR)
        .append("Component: ").append(id).append(NEWLINE)
        .append(DATASET_SEPARATOR)
        .append(summary);
    if (dd.binned > 0.0) {
      sb.append(discretization);
      sb.append(εBins);
    }
    sb.append(SECTION_SEPARATOR);
    appendData(sb, data, dd);
    sb.append(SECTION_SEPARATOR);
    appendContributions(sb, ddTotal, dd, dc.contributorLimit);
    sb.append(DATASET_SEPARATOR);
    appendSystemMfds(sb, ddTotal, dd, dc.contributorLimit);
    sb.append(SECTION_SEPARATOR);
    return sb.toString();
  }

  /*
   * Deaggregation data.
   * 
   * Create a container of distance-magnitude-epsilon data bins. This object
   * serializes to a JSON array of distance-magnitude (r-m) bins, each of which
   * contans an array of non-zero ε bins. The r-m bins are sorted ascending in
   * distance but descending in magnitude to facilitate rendering order in a
   * standard deaggregation 3D histogram.
   */
  private static DistanceMagnitudeData createDistanceMagnitudeData(
      DeaggDataset ddTotal,
      DeaggDataset dd) {

    ImmutableList.Builder<RmBin> rmBins = ImmutableList.builder();
    List<Double> distances = dd.rmε.rows();
    List<Double> magnitudes = dd.rmε.columns();
    double toPercent = percentScalar(ddTotal);

    // iterate distances ascending, magnitudes descending
    for (int ri = 0; ri < distances.size(); ri++) {
      double r = distances.get(ri);
      for (int mi = magnitudes.size() - 1; mi >= 0; mi--) {
        double rmBinWeight = dd.rmWeights.get(ri, mi);
        // skip empty bins
        if (rmBinWeight == 0.0) {
          continue;
        }
        double m = magnitudes.get(mi);
        double rBar = dd.rmrScaled.get(ri, mi) / rmBinWeight;
        double mBar = dd.rmmScaled.get(ri, mi) / rmBinWeight;
        // scale a mutable copy of epsilon values to percentage
        List<Double> εValues = new ArrayList<>(dd.rmε.column(ri, mi).yValues());
        Data.multiply(toPercent, εValues);
        ImmutableList.Builder<εData> εDataList = ImmutableList.builder();
        for (int i = 0; i < εValues.size(); i++) {
          double εValue = εValues.get(i);
          if (εValue <= 0.0) {
            continue;
          }
          εDataList.add(new εData(i, εValue));
        }
        RmBin rmBin = new RmBin(
            r, rBar,
            m, mBar,
            εDataList.build(),
            ImmutableList.copyOf(εValues));
        rmBins.add(rmBin);
      }
    }
    return new DistanceMagnitudeData(
        rmBins.build(),
        dd.rmε.levels().size());
  }

  /*
   * Distance-magnitude bin list.
   */
  private static final class DistanceMagnitudeData extends ListWrapper<RmBin> {

    final transient int εSize;

    DistanceMagnitudeData(List<RmBin> delegate, int εSize) {
      super(delegate);
      this.εSize = εSize;
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder(DEAGG_DATA_HEADER);
      sb.append(",").append(epsilonHeader(εSize));
      sb.append(NEWLINE);
      for (RmBin rmBin : RM_BIN_SORTER.immutableSortedCopy(this)) {
        sb.append(String.format(
            "%6.2f, %6.2f, %4.2f, %4.2f,",
            rmBin.r, rmBin.r̅, rmBin.m, rmBin.m̅));
        double total = Data.sum(rmBin.εValues);
        sb.append(EPSILON_FORMATTER.apply(total)).append(",");
        sb.append(formatEpsilonValues(rmBin.εValues));
        sb.append(NEWLINE);
      }
      return sb.toString();
    }

    // TODO consider ways to avoid repeating
    // this for multiple DeaggDatasets; it's
    // also the only reason we need εSize;
    // perhaps just carry the string with the model
    String epsilonHeader(int size) {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < size; i++) {
        if (i > 0) {
          sb.append(",");
        }
        sb.append(String.format("%6s", "ε" + i));
      }
      return sb.toString();
    }
  }

  /*
   * Data container for a single distance-magnitude (r-m) bin.
   * 
   * For JSON serialization, we are currently using the weighted r̅ and m̅ bin
   * positions, hence the use of @SerializedName and transient fields. This may
   * change in the future.
   * 
   * Although we have the minimum necessary data in εdata for JSON
   * serialization, we want the complete εValue array for string serialization,
   * hence we persist the transient εValues list.
   */
  @SuppressWarnings("unused")
  private static final class RmBin {

    @SerializedName("r")
    final double r̅;
    final transient double r;

    @SerializedName("m")
    final double m̅;
    final transient double m;

    final List<εData> εdata;
    final transient List<Double> εValues;

    private RmBin(
        double r,
        double r̅,
        double m,
        double m̅,
        List<εData> εdata,
        List<Double> εValues) {

      this.r = r;
      this.r̅ = r̅;
      this.m = m;
      this.m̅ = m̅;
      this.εdata = εdata;
      this.εValues = εValues;
    }
  }

  /*
   * Single ε value bin.
   */
  @SuppressWarnings("unused")
  private static final class εData {

    final int εbin;
    final double value;

    private εData(int εbin, double value) {
      this.εbin = εbin;
      this.value = value;
    }
  }

  /*
   * DistanceMagnitudeData string serialization helpers.
   */

  private static final String DEAGG_DATA_HEADER = String.format(
      "%6s, %7s, %4s, %5s, %5s",
      "r", "r̅", "m", "m̅", "Σε");

  private static final String E_TRACE = "     T";
  private static final String E_ZERO = "     0";
  private static final String E_FORMAT = " %5.2f";

  private static final double TRACE_LIMIT = 0.01;

  private static Function<Double, String> EPSILON_FORMATTER = new Function<Double, String>() {
    @Override
    public String apply(Double value) {
      return (value == 0.0) ? E_ZERO
          : (value < TRACE_LIMIT) ? E_TRACE : String.format(E_FORMAT, value);
    }
  };

  private static String formatEpsilonValues(List<Double> values) {
    return Delimiter.COMMA.joiner().join(Iterables.transform(values, EPSILON_FORMATTER));
  }

  private static Ordering<RmBin> RM_BIN_SORTER = new Ordering<RmBin>() {
    @Override
    public int compare(RmBin left, RmBin right) {
      return ComparisonChain.start().compare(left.r, right.r).compare(left.m, right.m).result();
    }
  };

  /*
   * Deaggregation summary.
   * 
   * Create a container of summary information for a DeaggDataset.
   */
  private static SummaryElements createSummaryElements(
      DeaggDataset total,
      DeaggDataset dd,
      DeaggConfig dc) {

    double toPercent = percentScalar(total);
    int ri, mi, εi;

    /* targets */
    double recoveredRate = total.binned + total.residual;
    double recoveredReturnPeriod = 1.0 / recoveredRate;

    /* totals */
    double ddTotal = dd.binned * toPercent;
    double ddResidual = dd.residual * toPercent;
    double ddTrace = traceContribution(dd);

    /* modes: largest r-m bin */
    int[] rmIndex = dd.rmWeights.maxIndex();
    ri = rmIndex[0];
    mi = rmIndex[1];
    double rmBinWeight = dd.rmWeights.get(ri, mi);
    double rmrMode = dd.rmrScaled.get(ri, mi) / rmBinWeight;
    double rmmMode = dd.rmmScaled.get(ri, mi) / rmBinWeight;
    double rmεMode = dd.rmεScaled.get(ri, mi) / rmBinWeight;
    double rmModeContrib = rmBinWeight * toPercent;

    /* modes: largest ε bin */
    int[] rmεIndex = dd.rmε.maxIndex();
    ri = rmεIndex[0];
    mi = rmεIndex[1];
    εi = rmεIndex[2];
    double εBinWeight = dd.rmε.get(ri, mi, εi);
    double εrMode = dd.rScaled.get(ri, mi, εi) / εBinWeight;
    double εmMode = dd.mScaled.get(ri, mi, εi) / εBinWeight;
    double εεMode = dd.εScaled.get(ri, mi, εi) / εBinWeight;
    double εModeContrib = εBinWeight * toPercent;

    ImmutableList.Builder<SummaryElement> summaryElements = ImmutableList.builder();
    summaryElements.add(

        new SummaryElement("Deaggregation targets", true, ImmutableList.of(
            new SummaryItem("Return period", dc.returnPeriod, "yrs"),
            new SummaryItem("Exceedance rate", dc.rate, "yr⁻¹"),
            new SummaryItem("Exceedance IML", exp(dc.iml), "g"))),

        new SummaryElement("Recovered targets", true, ImmutableList.of(
            new SummaryItem("Return period", recoveredReturnPeriod, "yrs"),
            new SummaryItem("Exceedance rate", recoveredRate, "yr⁻¹"))),

        new SummaryElement("Totals", true, ImmutableList.of(
            new SummaryItem("Binned", round(ddTotal, RME_ROUNDING), "%"),
            new SummaryItem("Residual", round(ddResidual, RME_ROUNDING), "%"),
            new SummaryItem("Trace", round(ddTrace, RME_ROUNDING), "%"))),

        new SummaryElement("Mean (for all sources)", true, ImmutableList.of(
            new SummaryItem("r", round(dd.rBar, RME_ROUNDING), "km"),
            new SummaryItem("m", round(dd.mBar, RME_ROUNDING), null),
            new SummaryItem("ε₀", round(dd.εBar, RME_ROUNDING), "σ"))),

        new SummaryElement("Mode (largest r-m bin)", true, ImmutableList.of(
            new SummaryItem("r", round(rmrMode, RME_ROUNDING), "km"),
            new SummaryItem("m", round(rmmMode, RME_ROUNDING), null),
            new SummaryItem("ε₀", round(rmεMode, RME_ROUNDING), "σ"),
            new SummaryItem("Contribution", round(rmModeContrib, RME_ROUNDING), "%"))),

        new SummaryElement("Mode (largest ε₀ bin)", true, ImmutableList.of(
            new SummaryItem("r", round(εrMode, RME_ROUNDING), "km"),
            new SummaryItem("m", round(εmMode, RME_ROUNDING), null),
            new SummaryItem("ε₀", round(εεMode, RME_ROUNDING), "σ"),
            new SummaryItem("Contribution", round(εModeContrib, RME_ROUNDING), "%"))));

    return new SummaryElements(summaryElements.build());
  }

  private static double traceContribution(DeaggDataset dd) {
    double toPercent = percentScalar(dd);
    double traceConribution = 0.0;
    for (int ri = 0; ri < dd.rmε.rows().size(); ri++) {
      for (int mi = 0; mi < dd.rmε.columns().size(); mi++) {
        if (dd.rmWeights.get(ri, mi) <= 0.0) {
          continue;
        }
        for (double εValue : dd.rmε.column(ri, mi).yValues()) {
          double rmεBinContribution = εValue * toPercent;
          if (rmεBinContribution < TRACE_LIMIT) {
            traceConribution += rmεBinContribution;
          }
        }
      }
    }
    return traceConribution;
  }

  private static double percentScalar(DeaggDataset dd) {
    return 100.0 / (dd.binned + dd.residual);
  }

  private static final int RME_ROUNDING = 2;
  private static final int SUMMARY_NAME_WIDTH = 22;

  /*
   * When all sources are out of range for a dataset (i.e. residual ≠ 0.0) the
   * rBar, mBar, and εBar fields are NaN as a result of dividing-by-zero rates,
   * as are other summary statistics. For the purpose of presentation and JSON
   * serialization we convert to a Double which may be null that can be handled
   * during serialization and in toString().
   */
  private static Double round(double value, int scale) {
    return Double.isNaN(value) ? null : MathUtils.round(value, scale);
  }

  /*
   * Summary data list.
   */
  private static final class SummaryElements extends ListWrapper<SummaryElement> {

    SummaryElements(List<SummaryElement> delegate) {
      super(delegate);
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder(NEWLINE);
      for (SummaryElement element : this) {
        sb.append(element.name).append(":").append(NEWLINE);
        for (SummaryItem item : element.data) {
          sb.append(Strings.padStart(item.name + ":  ", SUMMARY_NAME_WIDTH, ' '));
          if (item.value == null) {
            sb.append("no value");
          } else {
            sb.append(item.value);
            if (item.units != null) {
              sb.append(' ').append(item.units);
            }
          }
          sb.append(NEWLINE);
        }
        sb.append(NEWLINE);
      }
      return sb.toString();
    }
  }

  @SuppressWarnings("unused")
  private static final class SummaryElement {

    final String name;
    final boolean display;
    final List<SummaryItem> data;

    SummaryElement(String name, boolean display, List<SummaryItem> data) {
      this.name = name;
      this.display = display;
      this.data = data;
    }
  }

  private static final class SummaryItem {

    final String name;
    final Double value;
    final String units;

    SummaryItem(String name, Double value, String units) {
      this.name = name;
      this.value = value;
      this.units = units;
    }
  }

  private static final int OUTPUT_WIDTH = 105;
  private static final int NAME_WIDTH = 44;
  private static final int TYPE_WIDTH = 9;
  private static final String SRC_SET_NAME_FMT = "%-" + NAME_WIDTH + "s";
  private static final String SRC_NAME_FMT = "%-" + (NAME_WIDTH + TYPE_WIDTH) + "s";
  private static final String HEADER_LABEL_FMT = "%9s%9s%7s%7s%9s%8s%6s%8s";
  private static final String HEADER_LINE_FMT = "%9s%8s%6s%7s%9s%8s%6s%8s";
  private static final String SOURCE_COLUMN_FMT = "%8.2f%6.2f%7.2f%9.2f%8.2f%6.0f%8.2f";
  private static final String CONTRIB_HEADER_LABEL_FMT = SRC_SET_NAME_FMT + HEADER_LABEL_FMT;
  private static final String CONTRIB_HEADER_LINE_FMT = SRC_SET_NAME_FMT + HEADER_LINE_FMT;
  static final String CONTRIB_SOURCE_SET_FMT = SRC_SET_NAME_FMT + "%9s%52.2f";
  static final String CONTRIB_SOURCE_FMT = SRC_NAME_FMT + SOURCE_COLUMN_FMT;

  private static final String CONTRIBUTION_HEADER = new StringBuilder()
      .append(String.format(CONTRIB_HEADER_LABEL_FMT,
          "Source Set ↳ Source", "Type", "r̅", "m̅", "ε₀", "lon", "lat", "az", "%"))
      .append(NEWLINE)
      .append(String.format(CONTRIB_HEADER_LINE_FMT,
          "——————————————————————————————————————————", "—————————", "——————", "————", "—————",
          "———————", "——————", "————", "——————"))
      .append(NEWLINE)
      .toString();

  private static final String DATASET_SEPARATOR = Strings.repeat("—", OUTPUT_WIDTH) + NEWLINE;
  private static final String SECTION_SEPARATOR = Strings.repeat("·", OUTPUT_WIDTH) + NEWLINE;

  /* Data table string helper. */
  static StringBuilder appendData(
      StringBuilder sb,
      DistanceMagnitudeData data,
      DeaggDataset dd) {

    sb.append("Deaggregation data:");
    if (dd.binned == 0.0) {
      sb.append(" Suppressed (binned rate = 0.0).").append(NEWLINE);
    } else {
      sb.append(NEWLINE).append(NEWLINE).append(data);
    }
    return sb;
  }

  /* Source contribution string helper. */
  static StringBuilder appendContributions(
      StringBuilder sb,
      DeaggDataset ddTotal,
      DeaggDataset dd,
      double contributorLimit) {

    double toPercent = percentScalar(ddTotal);

    /*
     * Pre-determine whether any source set contributors will actually be
     * rendered and selectively print contributor table header.
     */
    boolean contributorsAboveLimit = false;
    for (DeaggContributor contributor : dd.contributors) {
      if (contributor.total() * toPercent >= contributorLimit) {
        contributorsAboveLimit = true;
        break;
      }
    }

    sb.append("Deaggregation contributors:");
    if (contributorsAboveLimit) {
      sb.append(NEWLINE).append(NEWLINE).append(CONTRIBUTION_HEADER);
      boolean firstPrinted = false;
      for (DeaggContributor contributor : dd.contributors) {
        if (contributor.total() * toPercent >= contributorLimit) {
          if (firstPrinted) {
            sb.append(NEWLINE);
          }
          firstPrinted = true;
        }
        contributor.appendTo(sb, toPercent, "", contributorLimit);
      }
    } else {
      sb.append(" Suppressed (all contributions < ")
          .append(contributorLimit)
          .append("%).")
          .append(NEWLINE);
    }
    return sb;
  }

  private static Predicate<SourceSetContributor> SYSTEM_FILTER =
      new Predicate<SourceSetContributor>() {
        @Override
        public boolean apply(SourceSetContributor contributor) {
          return contributor.sourceSet.type() == SourceType.SYSTEM;
        }
      };

  private static Function<DeaggContributor, SourceSetContributor> SOURCE_SET_CASTER =
      new Function<DeaggContributor, SourceSetContributor>() {
        @Override
        public SourceSetContributor apply(DeaggContributor contributor) {
          return (SourceSetContributor) contributor;
        }
      };

  private static final class ContributionFilter implements Predicate<DeaggContributor> {

    final double contributorLimit;
    final double toPercent;

    ContributionFilter(double contributorLimit, double toPercent) {
      this.contributorLimit = contributorLimit;
      this.toPercent = toPercent;
    }

    @Override
    public boolean apply(DeaggContributor contributor) {
      return contributor.total() * toPercent >= contributorLimit;
    }

  }

  static final String SYSTEM_MFD_FORMAT = "%5s, %48s,";


  static StringBuilder appendSystemMfds(
      StringBuilder sb,
      DeaggDataset ddTotal,
      DeaggDataset dd,
      double contributorLimit) {

    double toPercent = percentScalar(ddTotal);

    List<SourceSetContributor> systemSourceSetContributors = FluentIterable
        .from(dd.contributors)
        .transform(SOURCE_SET_CASTER)
        .filter(SYSTEM_FILTER)
        .filter(new ContributionFilter(contributorLimit, toPercent))
        .toList();

    for (SourceSetContributor ssc : systemSourceSetContributors) {
      // TODO this check isn't needed, children will always be present even
      // for a trace contribution and are only cut off only during rendering
      // clean this along with contributor toString cleanup
      if (ssc.children.size() == 0) {
        continue;
      }
      SystemContributor model = (SystemContributor) ssc.children.get(0);
      double modelContribution = model.total() * toPercent;
      if (modelContribution < contributorLimit) {
        continue;
      }
      sb.append("System section MFDs: " + ssc.sourceSet.name());
      sb.append(NEWLINE).append(NEWLINE);
      sb.append(String.format(SYSTEM_MFD_FORMAT, "Index", "Section"));
      sb.append(Parsing.toString(model.mfd.rows(), "%9.2f", ",", false, true));
      
      sb.append(NEWLINE);
      for (DeaggContributor child : ssc.children) {
        ((SystemContributor) child).appendMfd(sb, toPercent, contributorLimit);
      }
    }
    return sb;
  }

  /*
   * Metadata.
   * 
   * ε-bin bounds data is included in the metadata section of JSON output.
   */
  static EpsilonBins createEpsilonBins(List<Double> εLevels, double εDelta) {
    double εDeltaBy2 = εDelta / 2.0;
    ImmutableList.Builder<εBin> bins = ImmutableList.builder();
    for (int i = 0; i < εLevels.size(); i++) {
      Double min = (i == 0) ? null : εLevels.get(i) - εDeltaBy2;
      Double max = (i == εLevels.size() - 1) ? null : εLevels.get(i) + εDeltaBy2;
      bins.add(new εBin(i, min, max));
    }
    return new EpsilonBins(bins.build());
  }

  static class EpsilonBins extends ListWrapper<εBin> {

    EpsilonBins(List<εBin> delegate) {
      super(delegate);
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder("Epsilon keys:").append(NEWLINE);
      for (εBin bin : this) {
        sb.append(Strings.padStart("ε" + bin.id + ":  ", SUMMARY_NAME_WIDTH, ' '));
        sb.append("[");
        String min = (bin.min == null) ? "-∞" : Double.toString(bin.min);
        String max = (bin.max == null) ? "+∞" : Double.toString(bin.max);
        sb.append(min).append(" ‥ ").append(max);
        sb.append((bin.max == null) ? "]" : ")");
        sb.append(NEWLINE);
      }
      sb.append(NEWLINE);
      return sb.toString();
    }
  }

  private static final class εBin {

    final int id;
    final Double min;
    final Double max;

    εBin(int id, Double min, Double max) {
      this.id = id;
      this.min = min;
      this.max = max;
    }
  }

  /*
   * Create a string reflecting the r, m, and ε discretizations that were used
   * to intialize DeaggDatasets.
   */
  static String createDataDiscretization(DeaggConfig config) {
    StringBuilder sb = new StringBuilder();
    sb.append("Discretization:").append(NEWLINE);
    Bins bins = config.bins;
    sb.append(String.format(DISCRETIZATION_FMT, "r", bins.rMin, bins.rMax, bins.Δr));
    sb.append(String.format(DISCRETIZATION_FMT, "m", bins.mMin, bins.mMax, bins.Δm));
    sb.append(String.format(DISCRETIZATION_FMT, "ε", bins.εMin, bins.εMax, bins.Δε));
    sb.append(NEWLINE);
    return sb.toString();
  }

  private static final String DISCRETIZATION_FMT = "%" + (SUMMARY_NAME_WIDTH - 3) +
      "s:  min = %.1f, max = %.1f, Δ = %.1f" + NEWLINE;

  /*
   * List wrapper that preserves JSON serialization yet permits custom
   * toString() methods for other export formats.
   */
  private static class ListWrapper<T> extends AbstractList<T> {

    final transient List<T> delegate;

    ListWrapper(List<T> delegate) {
      this.delegate = delegate;
    }

    @Override
    public T get(int index) {
      return delegate.get(index);
    }

    @Override
    public int size() {
      return delegate.size();
    }

    @Override
    public Iterator<T> iterator() {
      return delegate.iterator();
    }
  }

}
