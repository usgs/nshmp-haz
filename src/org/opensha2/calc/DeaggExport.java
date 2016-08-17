package org.opensha2.calc;

import static org.opensha2.internal.TextUtils.NEWLINE;
import static org.opensha2.internal.MathUtils.round;

import org.opensha2.data.Data;
import org.opensha2.internal.Parsing.Delimiter;

import com.google.common.base.Function;
import com.google.common.base.Strings;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Ordering;
import com.google.gson.annotations.SerializedName;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Deaggregation exporter. This class handles String/Text and JSON output of
 * DeaggDatasets. JSON output is supported through the serialization of custom
 * data containers. String output is supported through the {@code toString()}
 * methods of those objects.
 *
 * @author Peter Powers
 */
final class DeaggExport {

  final transient DeaggDataset dd;
  final transient DeaggConfig dc;
  final transient EpsilonBins εBins;

  final DistanceMagnitudeData data;
  final SummaryElements summary;

  DeaggExport(DeaggDataset dd, DeaggConfig dc) {
    this.dd = dd;
    this.dc = dc;
    εBins = createEpsilonBins(dd.rmε.levels(), dd.rmε.levelΔ());
    summary = createSummaryElements(dd, dc);
    data = createDistanceMagnitudeData(dd);
  }

  @Deprecated
  public String toString() {
    return "" + data + NEWLINE + summary + NEWLINE + εBins;
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
  private static DistanceMagnitudeData createDistanceMagnitudeData(DeaggDataset dd) {
    ImmutableList.Builder<RmBin> rmBins = ImmutableList.builder();
    List<Double> distances = dd.rmε.rows();
    List<Double> magnitudes = dd.rmε.columns();
    double toPercent = percentScalar(dd);

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
            "%7.2f, %6.2f, %4.2f, %4.2f,",
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
      "%7s, %7s, %4s, %5s, %5s",
      "r", "r̅", "m", "m̅", "Σε");

  private static final String E_TRACE = "     T";
  private static final String E_ZERO = "     0";
  private static final String E_FORMAT = " %5.2f";

  private static final double TRACE_LIMIT = 0.01;

  private static Function<Double, String> EPSILON_FORMATTER = new Function<Double, String>() {
    @Override
    public String apply(Double value) {
      return (value == 0.0) ? E_ZERO
          : (value < TRACE_LIMIT) ? E_TRACE
              : String.format(E_FORMAT, value);
    }
  };

  private static String formatEpsilonValues(List<Double> values) {
    return Delimiter.COMMA.joiner().join(Iterables.transform(values, EPSILON_FORMATTER));
  }

  private static Ordering<RmBin> RM_BIN_SORTER = new Ordering<RmBin>() {
    @Override
    public int compare(RmBin left, RmBin right) {
      return ComparisonChain.start()
          .compare(left.r, right.r)
          .compare(left.m, right.m)
          .result();
    }
  };

  /*
   * Deaggregation summary.
   * 
   * Create a container of summary information for a DeaggDataset.
   */
  private static SummaryElements createSummaryElements(DeaggDataset dd, DeaggConfig dc) {

    double toPercent = percentScalar(dd);
    int ri, mi, εi;
    
    /* targets */
    double recoveredRate = dd.binned + dd.residual;
    double recoveredReturnPeriod = 1.0 / recoveredRate;

    /* totals */
    double total = dd.binned * toPercent;
    double residual = dd.residual * toPercent;
    double trace = traceContribution(dd);

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
            new SummaryItem("Exceedance IML", dc.iml, "g"))),

        new SummaryElement("Recovered targets", true, ImmutableList.of(
            new SummaryItem("Return period", recoveredReturnPeriod, "yrs"),
            new SummaryItem("Exceedance rate", recoveredRate, "yr⁻¹"))),

        new SummaryElement("Totals", true, ImmutableList.of(
            new SummaryItem("Binned", round(total, RME_ROUNDING), "%"),
            new SummaryItem("Residual", round(residual, RME_ROUNDING), "%"),
            new SummaryItem("Trace", round(trace, RME_ROUNDING), "%"))),

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
   * Summary data list.
   */
  private static final class SummaryElements extends ListWrapper<SummaryElement> {

    SummaryElements(List<SummaryElement> delegate) {
      super(delegate);
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      for (SummaryElement element : this) {
        sb.append(element.name).append(":").append(NEWLINE);
        for (SummaryItem item : element.data) {
          sb.append(Strings.padStart(item.name + ":  ", SUMMARY_NAME_WIDTH, ' '));
          sb.append(item.value);
          if (item.units != null) {
            sb.append(' ').append(item.units);
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
    final double value;
    final String units;

    SummaryItem(String name, double value, String units) {
      this.name = name;
      this.value = value;
      this.units = units;
    }
  }

  /*
   * Metadata.
   * 
   * ε-bin bounds data is included in the metadata section of JSON output.
   * 
   * TODO should pass in parent metadata object to Deaggregation.class when
   * exporting, otherwise these objects/methods will have to be accessible
   * outside this package
   * 
   * TODO can this be private??
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
