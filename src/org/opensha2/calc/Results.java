package org.opensha2.calc;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.file.StandardOpenOption.APPEND;
import static org.opensha2.data.ArrayXY_Sequence.copyOf;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.opensha2.data.ArrayXY_Sequence;
import org.opensha2.data.XY_Sequence;
import org.opensha2.eq.model.SourceType;
import org.opensha2.gmm.Imt;
import org.opensha2.util.Parsing;
import org.opensha2.util.Parsing.Delimiter;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

/**
 * Factory class for reducing and exporting various result types.
 *
 * @author Peter Powers
 */
public class Results {

	private static final String CURVE_FILE_SUFFIX = "-curves.csv";

	/**
	 * Write a {@code batch} of {@code HazardResult}s to files in the specified
	 * directory, one for each {@link Imt} in the {@code batch}. See
	 * {@link Files#write(Path, Iterable, java.nio.charset.Charset, OpenOption...)}
	 * for details on {@code options}. If no {@code options} are specified, the
	 * default behavior is to (over)write a new file. In this case a header row
	 * will be written as well. Files are encoded as
	 * {@link StandardCharsets#US_ASCII}.
	 * 
	 * @param dir to write to
	 * @param batch of results to write
	 * @param options specifying how the file is opened
	 * @throws IOException if a problem is encountered
	 * @see Files#write(Path, Iterable, java.nio.charset.Charset, OpenOption...)
	 */
	public static void writeResults(Path dir, List<HazardResult> batch, OpenOption... options)
			throws IOException {
		
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
					demo.totalCurves.get(imt).xValues());
				lineList.add(Parsing.join(header, Delimiter.COMMA));
			}
			lineMap.put(imt, lineList);
		}

		for (HazardResult result : batch) {
			List<Double> locData = Lists.newArrayList(
				result.site.location.lon(),
				result.site.location.lat());
			String name = result.site.name;
			for (Entry<Imt, ? extends XY_Sequence> entry : result.totalCurves.entrySet()) {
				Iterable<Double> lineData = Iterables.concat(
					locData,
					entry.getValue().yValues());
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

	public static Map<Imt, Map<SourceType, ArrayXY_Sequence>> totalsByType(HazardResult result) {

		ImmutableMap.Builder<Imt, Map<SourceType, ArrayXY_Sequence>> imtMapBuilder =
			ImmutableMap.builder();

		Map<Imt, ArrayXY_Sequence> curves = result.curves();
		Set<Imt> imts = curves.keySet();

		for (Imt imt : imts) {

			ArrayXY_Sequence modelCurve = copyOf(curves.get(imt)).clear();
			Map<SourceType, ArrayXY_Sequence> typeCurves = new EnumMap<>(SourceType.class);

			Multimap<SourceType, HazardCurveSet> curveSets = result.sourceSetMap;
			for (SourceType type : curveSets.keySet()) {
				ArrayXY_Sequence typeCurve = copyOf(modelCurve);
				for (HazardCurveSet curveSet : curveSets.get(type)) {
					ArrayXY_Sequence curve = curveSet.totalCurves.get(imt);
					typeCurve.add(curve);
				}
				typeCurves.put(type, typeCurve);
			}
			imtMapBuilder.put(imt, Maps.immutableEnumMap(typeCurves));
		}

		return imtMapBuilder.build();
	}
	
}
