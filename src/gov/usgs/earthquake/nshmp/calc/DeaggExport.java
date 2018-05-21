package gov.usgs.earthquake.nshmp.calc;

import static gov.usgs.earthquake.nshmp.calc.HazardExport.WRITE;
import static gov.usgs.earthquake.nshmp.internal.TextUtils.NEWLINE;
import static java.lang.Math.exp;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import com.google.common.base.Strings;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Ordering;
import com.google.gson.annotations.SerializedName;

import gov.usgs.earthquake.nshmp.calc.CalcConfig.Deagg.Bins;
import gov.usgs.earthquake.nshmp.calc.DeaggContributor.JsonContributor;
import gov.usgs.earthquake.nshmp.calc.DeaggContributor.SourceSetContributor;
import gov.usgs.earthquake.nshmp.calc.DeaggContributor.SystemContributor;
import gov.usgs.earthquake.nshmp.data.Data;
import gov.usgs.earthquake.nshmp.eq.model.SourceType;
import gov.usgs.earthquake.nshmp.gmm.Imt;
import gov.usgs.earthquake.nshmp.internal.Parsing;
import gov.usgs.earthquake.nshmp.internal.Parsing.Delimiter;
import gov.usgs.earthquake.nshmp.util.Maths;

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

  @SerializedName("component")
  final String id;
  final DistanceMagnitudeData data;
  final SummaryElements summary;
  final List<JsonContributor> sources;

  private static final String DEAGG_DATA = "data.csv";
  private static final String DEAGG_SUMMARY = "summary.txt";

  /*
   * All component DeaggDatasets require data from the final total DeaggDataset
   * to correctly calculate contributions and represent summary data that is not
   * specific to the component dataset.
   */

  DeaggExport(
      DeaggDataset ddTotal,
      DeaggDataset dd,
      DeaggConfig dc,
      String id,
      boolean json) {

    this.ddTotal = ddTotal;
    this.dd = dd;
    this.dc = dc;
    this.id = id;

    summary = createSummaryElements(ddTotal, dd, dc);
    data = createDistanceMagnitudeData(ddTotal, dd);
    sources = json ? createJsonContributorList(ddTotal, dd, dc.contributorLimit) : null;
  }

  void toFile(Path dir, String site) throws IOException {
    Path siteDir = dir.resolve(site);
    Files.createDirectories(siteDir);
    Path dataPath = siteDir.resolve(DEAGG_DATA);
    Files.write(
        dataPath,
        data.toString().getBytes(UTF_8),
        WRITE);
    Path summaryPath = siteDir.resolve(DEAGG_SUMMARY);
    String summaryString = summaryStringBuilder()
        .append(DATASET_SEPARATOR)
        .toString();
    Files.write(
        summaryPath,
        summaryString.getBytes(UTF_8),
        WRITE);
  }

  @Override
  public String toString() {
    StringBuilder sb = summaryStringBuilder();
    sb.append(SECTION_SEPARATOR);
    appendData(sb, data, dd);
    sb.append(DATASET_SEPARATOR);
    return sb.toString();
  }

  private StringBuilder summaryStringBuilder() {
    StringBuilder sb = new StringBuilder()
        .append("Component: ")
        .append(id)
        .append(NEWLINE)
        .append(SECTION_SEPARATOR)
        .append(summary);
    sb.append(SECTION_SEPARATOR);
    appendContributions(sb, ddTotal, dd, dc.contributorLimit);
    return sb;
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
            rmBin.r, rmBin.rBar, rmBin.m, rmBin.mBar));
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
    final transient double rBar;
    final double r;

    @SerializedName("m")
    final transient double mBar;
    final double m;

    final List<εData> εdata;
    final transient List<Double> εValues;

    private RmBin(
        double r,
        double rBar,
        double m,
        double mBar,
        List<εData> εdata,
        List<Double> εValues) {

      this.r = r;
      this.rBar = rBar;
      this.m = m;
      this.mBar = mBar;
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
    return Delimiter.COMMA.joiner().join(Iterables.transform(
        values, 
        EPSILON_FORMATTER::apply));
  }

  private static Ordering<RmBin> RM_BIN_SORTER = new Ordering<RmBin>() {
    @Override
    public int compare(RmBin left, RmBin right) {
      return ComparisonChain.start().compare(left.r, right.r).compare(left.m, right.m).result();
    }
  };

  private static final String DISCRETIZATION_FMT = "min = %.1f, max = %.1f, Δ = %.1f";

  /*
   * Deaggregation summary.
   * 
   * TODO consider ways to not repeat building elements/items that are not
   * unique to a dataset; perhaps only include some items in the total component
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

    /* discretization */
    Bins b = dc.bins;
    String rDiscr = String.format(DISCRETIZATION_FMT, b.rMin, b.rMax, b.Δr);
    String mDiscr = String.format(DISCRETIZATION_FMT, b.mMin, b.mMax, b.Δm);
    String εDiscr = String.format(DISCRETIZATION_FMT, b.εMin, b.εMax, b.Δε);

    /* custom imt formatting */
    Imt imt = dc.imt;
    String imlLabel = (imt.isSA() ? imt.period() + " s SA" : imt.name()) + " ground motion";

    ImmutableList.Builder<SummaryElement> summaryElements = ImmutableList.builder();
    summaryElements.add(

        new SummaryElement("Deaggregation targets", true, ImmutableList.of(
            new SummaryItem("Return period", dc.returnPeriod, "yrs"),
            new SummaryItem("Exceedance rate", dc.rate, "yr⁻¹"),
            new SummaryItem(imlLabel, exp(dc.iml), dc.imt.units()))),

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
            new SummaryItem("Contribution", round(εModeContrib, RME_ROUNDING), "%"))),

        new SummaryElement("Discretization", true, ImmutableList.of(
            new SummaryItem("r", rDiscr, "km"),
            new SummaryItem("m", mDiscr, null),
            new SummaryItem("ε", εDiscr, "σ"))),

        new SummaryElement("Epsilon keys", true, dc.εBins.toSummaryItems()));

    return new SummaryElements(summaryElements.build());
  }

  private static double traceContribution(DeaggDataset dd) {
    double toPercent = percentScalar(dd);
    double traceContribution = 0.0;
    for (int ri = 0; ri < dd.rmε.rows().size(); ri++) {
      for (int mi = 0; mi < dd.rmε.columns().size(); mi++) {
        if (dd.rmWeights.get(ri, mi) <= 0.0) {
          continue;
        }
        for (double εValue : dd.rmε.column(ri, mi).yValues()) {
          double rmεBinContribution = εValue * toPercent;
          if (rmεBinContribution < TRACE_LIMIT) {
            traceContribution += rmεBinContribution;
          }
        }
      }
    }
    return traceContribution;
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
    return Double.isNaN(value) ? null : Maths.round(value, scale);
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
    final Object value;
    final String units;

    SummaryItem(String name, Object value, String units) {
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

    ContributionFilter contributionFilter = new ContributionFilter(
        contributorLimit,
        percentScalar(ddTotal));

    /*
     * Could use a FluentIterable generated list below, but because we know the
     * contributors are sorted descending by total contribution, we short
     * circuit an iterator instead.
     */
    List<DeaggContributor> sourceSetContributors = new ArrayList<>();
    for (DeaggContributor contributor : dd.contributors) {
      if (contributionFilter.test(contributor)) {
        sourceSetContributors.add(contributor);
        continue;
      }
      break;
    }

    sb.append("Deaggregation contributors:");
    if (sourceSetContributors.size() > 0) {
      sb.append(NEWLINE).append(NEWLINE).append(CONTRIBUTION_HEADER);
      boolean firstContributor = true;
      for (DeaggContributor contributor : sourceSetContributors) {
        sb.append(firstContributor ? "" : NEWLINE);
        firstContributor = false;
        contributor.appendTo(sb, "", contributionFilter);
      }
    } else {
      sb.append(" Suppressed (all contributions < ")
          .append(contributorLimit)
          .append("%).")
          .append(NEWLINE);
    }

    /* This will append content only if system sources contribute. */
    appendSystemMfds(sb, dd, contributionFilter);

    return sb;
  }

  private static Predicate<SourceSetContributor> SYSTEM_FILTER =
      new Predicate<SourceSetContributor>() {
        @Override
        public boolean test(SourceSetContributor contributor) {
          return contributor.sourceSet.type() == SourceType.SYSTEM;
        }
      };

  private static Function<DeaggContributor, SourceSetContributor> TO_SOURCE_SET_CONTRIBUTOR =
      new Function<DeaggContributor, SourceSetContributor>() {
        @Override
        public SourceSetContributor apply(DeaggContributor contributor) {
          return (SourceSetContributor) contributor;
        }
      };

  /* Filters out contributors whose contribution is below supplied limit. */
  static final class ContributionFilter implements Predicate<DeaggContributor> {

    private final double limit;
    private final double toPercent;

    ContributionFilter(double limit, double toPercent) {
      this.limit = limit;
      this.toPercent = toPercent;
    }

    double toPercent(double rate) {
      return rate * toPercent;
    }

    @Override
    public boolean test(DeaggContributor contributor) {
      return contributor.total() * toPercent >= limit;
    }
  }

  private List<JsonContributor> createJsonContributorList(
      DeaggDataset ddTotal,
      DeaggDataset dd,
      double contributorLimit) {

    ContributionFilter contributionFilter = new ContributionFilter(
        contributorLimit,
        percentScalar(ddTotal));

    ImmutableList.Builder<JsonContributor> jsonContributors = ImmutableList.builder();
    for (DeaggContributor contributor : dd.contributors) {
      if (contributionFilter.test(contributor)) {
        jsonContributors.addAll(contributor.toJson(contributionFilter));
        continue;
      }
      break;
    }

    return jsonContributors.build();
  }

  static final String SYSTEM_MFD_FORMAT = "%5s, %48s,";

  static StringBuilder appendSystemMfds(
      StringBuilder sb,
      DeaggDataset dd,
      ContributionFilter contributionFilter) {

    List<SourceSetContributor> systemSourceSetContributors = FluentIterable
        .from(dd.contributors)
        .transform(TO_SOURCE_SET_CONTRIBUTOR::apply)
        .filter(SYSTEM_FILTER::test)
        .filter(contributionFilter::test)
        .toList();

    boolean first = true;
    for (SourceSetContributor ssc : systemSourceSetContributors) {
      SystemContributor model = (SystemContributor) ssc.children.get(0);
      if (!contributionFilter.test(model)) {
        continue;
      }
      sb.append(first ? SECTION_SEPARATOR : NEWLINE)
          .append("System section MFDs: " + ssc.sourceSet.name())
          .append(NEWLINE).append(NEWLINE)
          .append(String.format(SYSTEM_MFD_FORMAT, "Index", "Section"))
          .append(Parsing.toString(model.mfd.rows(), "%9.2f", ",", false, true))
          .append(NEWLINE);
      for (DeaggContributor child : ssc.children) {
        if (contributionFilter.test(child)) {
          ((SystemContributor) child).appendMfd(sb);
          continue;
        }
        break;
      }
      first = false;
    }
    return sb;
  }

  /* Epsilon metadata. */
  static class EpsilonBins extends ListWrapper<εBin> {

    EpsilonBins(List<εBin> delegate) {
      super(delegate);
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder("Epsilon keys:").append(NEWLINE);
      for (εBin bin : this) {
        sb.append(Strings.padStart("ε" + bin.id + ":  ", SUMMARY_NAME_WIDTH, ' '));
        sb.append(bin)
            .append(NEWLINE);
      }
      sb.append(NEWLINE);
      return sb.toString();
    }

    List<SummaryItem> toSummaryItems() {
      return FluentIterable.from(this)
          .transform(BIN_TO_SUMMARY_ITEM::apply)
          .toList();
    }
  }

  private static final Function<εBin, SummaryItem> BIN_TO_SUMMARY_ITEM =
      new Function<εBin, SummaryItem>() {
        @Override
        public SummaryItem apply(εBin bin) {
          return new SummaryItem("ε" + bin.id, bin.toString(), null);
        }
      };

  static final class εBin {

    final int id;
    final Double min;
    final Double max;

    εBin(int id, Double min, Double max) {
      this.id = id;
      this.min = min;
      this.max = max;
    }

    @Override
    public String toString() {
      return new StringBuilder("[")
          .append((min == null) ? "-∞" : Double.toString(min))
          .append(" ‥ ")
          .append((max == null) ? "+∞" : Double.toString(max))
          .append((max == null) ? "]" : ")")
          .toString();
    }
  }

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
