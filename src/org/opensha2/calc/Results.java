package org.opensha2.calc;

import static java.nio.charset.StandardCharsets.US_ASCII;

import static org.opensha2.data.XySequence.emptyCopyOf;

import org.opensha2.data.XySequence;
import org.opensha2.eq.model.Source;
import org.opensha2.eq.model.SourceSet;
import org.opensha2.eq.model.SourceType;
import org.opensha2.gmm.Gmm;
import org.opensha2.gmm.Imt;
import org.opensha2.mfd.Mfds;
import org.opensha2.util.Parsing;
import org.opensha2.util.Parsing.Delimiter;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.primitives.Doubles;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Factory class for reducing and exporting various result types.
 *
 * @author Peter Powers
 */
public class Results {

  private static final String CURVE_FILE_SUFFIX = ".csv";
  private static final String RATE_FMT = "%.8e";

  /*
   * Individual Hazard results only contain data relevant to the site of
   * interest (e.g. for the NSHM WUS models, hazard in San Fancisco is
   * influenced by slab sources whereas hazard in Los Angeles is not because it
   * is too far away). For consistency when outputting batches of results, files
   * are written for all source types and ground motion models supported by the
   * HazardModel being used. This yields curve sets that are consistent across
   * all locations in a batch, however, some of the curves may be empty.
   * Depending on the extents of a map or list of sites, some curve sets may
   * consist exclusively of zero-valued curves.
   */

  /*
   * TODO There is no good reason for this class to be public. It would be
   * better as a ResultsWriter, an instance of which would be obtained and
   * configured on a per-calculation basis to receive batches of results.
   * Although problems are unlikely, we're repeating a number of configuration
   * steps below and relying on the same config file coming with the first
   * result in each batch (below). We also wouldn't have to pass around
   * OpenOptions which are mildly confusing.
   */

  /**
   * Write a {@code batch} of {@code HazardResult}s to files in the specified
   * directory, one for each {@link Imt} in the {@code batch}. See
   * {@link Files#write(Path, Iterable, java.nio.charset.Charset, OpenOption...)}
   * for details on {@code options}. If no {@code options} are specified, the
   * default behavior is to (over)write a new file. In this case a header row
   * will be written as well. Files are encoded as
   * {@link StandardCharsets#US_ASCII}, lat and lon values are formatted to 2
   * decimal places, and curve values are formatted to 8 significant figures.
   *
   * @param dir to write to
   * @param batch of results to write
   * @param options specifying how the file is opened
   * @throws IOException if a problem is encountered
   * @see Files#write(Path, Iterable, java.nio.charset.Charset, OpenOption...)
   */
  public static void writeResults(
      Path dir,
      List<Hazard> batch,
      OpenOption... options) throws IOException {

    Hazard demo = batch.get(0);
    boolean newFile = options.length == 0;
    boolean namedSites = demo.site.name != Site.NO_NAME;
    boolean gmmCurves = demo.config.output.curveTypes.contains(CurveType.GMM);
    boolean sourceCurves = demo.config.output.curveTypes.contains(CurveType.SOURCE);

    Function<Double, String> formatter = Parsing.formatDoubleFunction(RATE_FMT);
    if (demo.config.curve.valueType == CurveValue.POISSON_PROBABILITY) {
      formatter = Functions.compose(
          formatter,
          Mfds.annualRateToProbabilityConverter());
    }

    Map<Imt, List<String>> totalLineMap = Maps.newEnumMap(Imt.class);
    Map<Imt, Map<SourceType, List<String>>> sourceLineMap = Maps.newEnumMap(Imt.class);
    Map<Imt, Map<Gmm, List<String>>> gmmLineMap = Maps.newEnumMap(Imt.class);

    /* Initialize line maps for all types and gmms referenced by a model */
    for (Imt imt : demo.totalCurves.keySet()) {
      List<String> lines = new ArrayList<>();
      if (newFile) {
        Iterable<?> header = Iterables.concat(
            Lists.newArrayList(namedSites ? "name" : null, "lon", "lat"),
            demo.config.curve.modelCurves().get(imt).xValues());
        lines.add(Parsing.join(header, Delimiter.COMMA));
      }
      totalLineMap.put(imt, lines);

      if (sourceCurves) {
        Map<SourceType, List<String>> typeLines = Maps.newEnumMap(SourceType.class);
        for (SourceType type : demo.model.types()) {
          typeLines.put(type, Lists.newArrayList(lines));
        }
        sourceLineMap.put(imt, typeLines);
      }

      if (gmmCurves) {
        Map<Gmm, List<String>> gmmLines = Maps.newEnumMap(Gmm.class);
        for (Gmm gmm : gmmSet(demo.model)) {
          gmmLines.put(gmm, Lists.newArrayList(lines));
        }
        gmmLineMap.put(imt, gmmLines);
      }
    }

    /* Process batch */
    for (Hazard hazard : batch) {

      String name = namedSites ? hazard.site.name : null;
      List<String> locData = Lists.newArrayList(
          name,
          String.format("%.5f", hazard.site.location.lon()),
          String.format("%.5f", hazard.site.location.lat()));

      Map<Imt, Map<SourceType, XySequence>> curvesBySource =
          sourceCurves ? curvesBySource(hazard) : null;
      Map<Imt, Map<Gmm, XySequence>> curvesByGmm = gmmCurves ? curvesByGmm(hazard) : null;

      for (Entry<Imt, XySequence> imtEntry : hazard.totalCurves.entrySet()) {
        Imt imt = imtEntry.getKey();

        XySequence totalCurve = imtEntry.getValue();
        Iterable<Double> emptyValues = Doubles.asList(new double[totalCurve.size()]);
        String emptyLine = toLine(locData, emptyValues, formatter);

        totalLineMap.get(imt).add(toLine(
            locData,
            imtEntry.getValue().yValues(),
            formatter));

        if (sourceCurves) {
          Map<SourceType, XySequence> sourceCurveMap = curvesBySource.get(imt);
          for (Entry<SourceType, List<String>> typeEntry : sourceLineMap.get(imt)
              .entrySet()) {
            SourceType type = typeEntry.getKey();
            String typeLine = sourceCurveMap.containsKey(type)
                ? toLine(locData, sourceCurveMap.get(type).yValues(), formatter)
                : emptyLine;
            typeEntry.getValue().add(typeLine);
          }
        }

        if (gmmCurves) {
          Map<Gmm, XySequence> gmmCurveMap = curvesByGmm.get(imt);
          for (Entry<Gmm, List<String>> gmmEntry : gmmLineMap.get(imt).entrySet()) {
            Gmm gmm = gmmEntry.getKey();
            String gmmLine = gmmCurveMap.containsKey(gmm)
                ? toLine(locData, gmmCurveMap.get(gmm).yValues(), formatter)
                : emptyLine;
            gmmEntry.getValue().add(gmmLine);
          }
        }
      }
    }

    /* write/append */
    for (Entry<Imt, List<String>> totalEntry : totalLineMap.entrySet()) {
      Imt imt = totalEntry.getKey();

      Path imtDir = dir.resolve(imt.name());
      Files.createDirectories(imtDir);
      Path totalFile = imtDir.resolve("total" + CURVE_FILE_SUFFIX);
      Files.write(totalFile, totalEntry.getValue(), US_ASCII, options);

      if (sourceCurves) {
        Path typeDir = imtDir.resolve("source");
        Files.createDirectories(typeDir);
        for (Entry<SourceType, List<String>> typeEntry : sourceLineMap.get(imt)
            .entrySet()) {
          Path typeFile = typeDir.resolve(
              typeEntry.getKey().toString() + CURVE_FILE_SUFFIX);
          Files.write(typeFile, typeEntry.getValue(), US_ASCII, options);
        }
      }

      if (gmmCurves) {
        Path gmmDir = imtDir.resolve("gmm");
        Files.createDirectories(gmmDir);
        for (Entry<Gmm, List<String>> gmmEntry : gmmLineMap.get(imt).entrySet()) {
          Path gmmFile = gmmDir.resolve(gmmEntry.getKey().name() + CURVE_FILE_SUFFIX);
          Files.write(gmmFile, gmmEntry.getValue(), US_ASCII, options);
        }
      }
    }
  }

  private static String toLine(
      Iterable<String> location,
      Iterable<Double> values,
      Function<Double, String> formatter) {

    return Parsing.join(
        FluentIterable.from(location).append(Iterables.transform(values, formatter)),
        Delimiter.COMMA);
  }

  /**
   * Derive maps of curves by source type for each Imt in a {@code Hazard}
   * result.
   */
  public static Map<Imt, Map<SourceType, XySequence>> curvesBySource(Hazard hazard) {

    EnumMap<Imt, Map<SourceType, XySequence>> imtMap = Maps.newEnumMap(Imt.class);

    // initialize receiver
    Set<SourceType> types = hazard.sourceSetCurves.keySet();
    for (Entry<Imt, XySequence> entry : hazard.curves().entrySet()) {
      imtMap.put(entry.getKey(), initCurves(types, entry.getValue()));
    }

    for (Entry<SourceType, HazardCurveSet> curveSet : hazard.sourceSetCurves.entries()) {
      for (Entry<Imt, XySequence> typeTotals : curveSet.getValue().totalCurves.entrySet()) {
        imtMap.get(typeTotals.getKey())
            .get(curveSet.getKey())
            .add(typeTotals.getValue());
      }
    }
    return Maps.immutableEnumMap(imtMap);
  }

  /**
   * Derive maps of curves by groudn motion model for each Imt in a
   * {@code Hazard} result.
   */
  public static Map<Imt, Map<Gmm, XySequence>> curvesByGmm(Hazard hazard) {

    EnumMap<Imt, Map<Gmm, XySequence>> imtMap = Maps.newEnumMap(Imt.class);

    // initialize receiver
    Iterable<SourceSet<? extends Source>> sources = Iterables.transform(
        hazard.sourceSetCurves.values(),
        CURVE_SET_TO_SOURCE_SET);
    Set<Gmm> gmms = gmmSet(sources);
    for (Entry<Imt, XySequence> entry : hazard.curves().entrySet()) {
      imtMap.put(entry.getKey(), initCurves(gmms, entry.getValue()));
    }

    for (HazardCurveSet curveSet : hazard.sourceSetCurves.values()) {
      for (Entry<Imt, Map<Gmm, XySequence>> imtEntry : curveSet.curveMap.entrySet()) {
        for (Entry<Gmm, XySequence> gmmEntry : imtEntry.getValue().entrySet()) {
          imtMap.get(imtEntry.getKey()).get(gmmEntry.getKey()).add(gmmEntry.getValue());
        }
      }
    }
    return Maps.immutableEnumMap(imtMap);
  }

  /* Scan the supplied source sets for the set of all GMMs used. */
  private static Set<Gmm> gmmSet(final Iterable<SourceSet<? extends Source>> sourceSets) {
    return Sets.immutableEnumSet(
        FluentIterable.from(sourceSets).transformAndConcat(
            new Function<SourceSet<? extends Source>, Set<Gmm>>() {
              @Override
              public Set<Gmm> apply(SourceSet<? extends Source> sourceSet) {
                return sourceSet.groundMotionModels().gmms();
              }
            }));
  }

  /*
   * Initalize a map of curves, one entry for each of the supplied enum keys.
   */
  private static <K extends Enum<K>> Map<K, XySequence> initCurves(
      final Set<K> keys,
      final XySequence model) {
    return Maps.immutableEnumMap(
        FluentIterable.from(keys).toMap(
            new Function<K, XySequence>() {
              @Override
              public XySequence apply(K key) {
                return emptyCopyOf(model);
              }
            }));
  }

  private static final Function<HazardCurveSet, SourceSet<? extends Source>> CURVE_SET_TO_SOURCE_SET =
      new Function<HazardCurveSet, SourceSet<? extends Source>>() {
        @Override
        public SourceSet<? extends Source> apply(HazardCurveSet curves) {
          return curves.sourceSet;
        }
      };

}
