package org.opensha2.calc;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.opensha2.data.XySequence.copyOf;
import static org.opensha2.data.XySequence.immutableCopyOf;
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

import org.opensha2.data.XySequence;
import org.opensha2.eq.model.SourceType;
import org.opensha2.geo.Location;
import org.opensha2.gmm.Imt;
import org.opensha2.mfd.Mfds;
import org.opensha2.util.Parsing;
import org.opensha2.util.Parsing.Delimiter;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;

/**
 * Factory class for reducing and exporting various result types.
 *
 * @author Peter Powers
 */
public class Results {

	private static final String CURVE_FILE_SUFFIX = "-curves.csv";
	private static final String RATE_FMT = "%.8e";

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
	public static void writeResults(Path dir, List<HazardResult> batch, OpenOption... options)
			throws IOException {

		Function<Double, String> locFmtFunc = Parsing.formatDoubleFunction(Location.FORMAT);
		Function<Double, String> rateFmtFunc = Parsing.formatDoubleFunction(RATE_FMT);

		HazardResult demo = batch.get(0);
		boolean newFile = options.length == 0;
		boolean namedSites = demo.site.name != Site.NO_NAME;

		Map<Imt, List<String>> lineMap = new EnumMap<>(Imt.class);
		for (Imt imt : demo.totalCurves.keySet()) {
			List<String> lineList = new ArrayList<>();
			// write header
			if (newFile) {
				List<String> headings = new ArrayList<>();
				if (namedSites) headings.add("name");
				headings.add("lon");
				headings.add("lat");
				Iterable<? extends Object> header = Iterables.concat(
					headings,
					demo.config.modelCurves.get(imt).xValues());
				lineList.add(Parsing.join(header, Delimiter.COMMA));
			}
			lineMap.put(imt, lineList);
		}

		for (HazardResult result : batch) {
			Iterable<String> locData = Iterables.transform(
				Lists.newArrayList(
					result.site.location.lon(),
					result.site.location.lat()),
				locFmtFunc);
			String name = result.site.name;
			for (Entry<Imt, ? extends XySequence> entry : result.totalCurves.entrySet()) {

				// enable to output poisson probability - used when running
				// PEER test cases - TODO should be configurable
//				Function<Double, String> valueFunction = Functions.compose(
//					rateFmtFunc,
//					Mfds.rateToProbConverter());

				// enable to output annual rate
				Function<Double, String> valueFunction = rateFmtFunc;

				Iterable<String> lineData = Iterables.concat(
					locData,
					Iterables.transform(
						entry.getValue().yValues(),
						valueFunction));

				String line = Parsing.join(lineData, Delimiter.COMMA);
				if (namedSites) line = name + "," + line;
				lineMap.get(entry.getKey()).add(line);
			}
		}

		for (Entry<Imt, List<String>> entry : lineMap.entrySet()) {
			String filename = entry.getKey().name() + CURVE_FILE_SUFFIX;
			Path file = dir.resolve(filename);
			Files.write(file, entry.getValue(), US_ASCII, options);
		}
	}

	public static Map<Imt, Map<SourceType, XySequence>> totalsByType(HazardResult result) {

		ImmutableMap.Builder<Imt, Map<SourceType, XySequence>> imtMapBuilder =
			ImmutableMap.builder();

		Map<Imt, XySequence> curves = result.curves();
		Set<Imt> imts = curves.keySet();

		for (Imt imt : imts) {

			XySequence modelCurve = copyOf(curves.get(imt)).clear();
			Map<SourceType, XySequence> typeCurves = new EnumMap<>(SourceType.class);

			Multimap<SourceType, HazardCurveSet> curveSets = result.sourceSetMap;
			for (SourceType type : curveSets.keySet()) {
				XySequence typeCurve = copyOf(modelCurve);
				for (HazardCurveSet curveSet : curveSets.get(type)) {
					typeCurve.add(curveSet.totalCurves.get(imt));
				}
				typeCurves.put(type, immutableCopyOf(typeCurve));
			}
			imtMapBuilder.put(imt, Maps.immutableEnumMap(typeCurves));
		}

		return imtMapBuilder.build();
	}

}
