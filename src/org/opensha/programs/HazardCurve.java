package org.opensha.programs;

import static com.google.common.base.StandardSystemProperty.LINE_SEPARATOR;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.logging.Level.SEVERE;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;

import org.opensha.calc.Calcs;
import org.opensha.calc.CalcConfig;
import org.opensha.calc.HazardResult;
import org.opensha.calc.Site;
import org.opensha.calc.Utils;
import org.opensha.data.ArrayXY_Sequence;
import org.opensha.data.XY_Sequence;
import org.opensha.eq.model.HazardModel;
import org.opensha.geo.Location;
import org.opensha.gmm.Imt;
import org.opensha.mfd.Mfds;
import org.opensha.util.Logging;
import org.opensha.util.Parsing;
import org.opensha.util.Parsing.Delimiter;

import com.google.common.base.Converter;
import com.google.common.base.Stopwatch;
import com.google.common.base.Throwables;
import com.google.common.collect.ArrayTable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Table;

/**
 * Entry point for computing a hazard curve at a {@link Site} from a
 * {@link HazardModel}. The {@code main()} method of this class returns mean
 * hazard curves for the model and {@link Imt} specified. For more detailed
 * results at a {@code Site}, consider programmatically using the {@code calc()}
 * methods of this class.
 * 
 * @author Peter Powers
 * @see Result
 * @see MultiResult
 */
public class HazardCurve {

	private static final String LF = LINE_SEPARATOR.value();

	private static final double DEFAULT_VS_30 = 760.0;
	private static final ArrayXY_Sequence modelCurve = Utils.nshmpCurve();
	private static final String FILE_NAME = "curves.csv";
	private static final String HEADER_COMMENTS = "# HazardCurve results";

	/**
	 * Calculate a hazard curve at a {@link Site}.
	 * @param model to use
	 * @param imt intensity measure type
	 * @param site of interest
	 */
//	public static HazardResult calc(HazardModel model, Imt imt, Site site) {
//		try {
//			return Calcs.hazardCurve(model, imt, site, modelCurve);
//		} catch (ExecutionException | InterruptedException e) {
//			Throwables.propagate(e);
//			return null;
//		}
//	}
	
	public static Map<Site, HazardResult> calc(HazardModel model) {
		return calc(model, model.config());
	}
	
	// config associated with model will be ignored
	// TODO should this variant have had SiteSet replaced by data in sites.csv
	// if supplied upstream
	public static Map<Site, HazardResult> calc(HazardModel model, CalcConfig config) {
		
//		Logger log = Logger.getLogger(HazardCurve.class.getName());
//
//		log.info("");
//		log.info(config.toString());

		Builder<Site, HazardResult> resultTable = ImmutableMap.builder();
		for (Site site : config.sites) {
			System.out.println(site.name);
			HazardResult result = calc(model, config, site);
			resultTable.put(site, result);
		}
		
		
//		log.info("HazardCurve: loading model ...");
//		log.info("");

		return resultTable.build();
	}
	
	public static HazardResult calc(HazardModel model, CalcConfig config, Site site) {
		
		Logger log = Logger.getLogger(HazardCurve.class.getName());

		log.info("");
		log.info(config.toString());
		
		try {
			return Calcs.hazardCurve(model, config, site);
		} catch (ExecutionException | InterruptedException e) {
			Throwables.propagate(e);
			return null;
		}
	}
	
	/*
	 * Note that calculations are performed entirely in log space. We convert
	 * back here to linear space for output. Calculations are also performed 
	 * in rate space and the option is provided here to convert to Poisson
	 * probability.
	 */
	public static void writeResults(Path dir, Map<Imt, ArrayXY_Sequence> modelCurves,
			Map<Site, HazardResult> results, boolean poisson) throws IOException {
		for (Imt imt : modelCurves.keySet()) {
			List<String> lines = new ArrayList<>();
			lines.add(HEADER_COMMENTS);
			// x-values in linear space
			ArrayXY_Sequence modelCurve = modelCurves.get(imt);
			lines.add(createHeaderRow(modelCurve));
			for (Entry<Site, HazardResult> entry : results.entrySet()) {
				Site site = entry.getKey();
				HazardResult result = entry.getValue();
				// x-values in log space
				ArrayXY_Sequence calcCurve = result.curves().get(imt);
				if (poisson) {
					calcCurve = ArrayXY_Sequence.copyOf(calcCurve);
					calcCurve.transform(Mfds.annRateToPoissProbConverter());
				}
				List<Double> locData = Lists.newArrayList(
					site.location.lon(),
					site.location.lat());
				Iterable<Double> lineDat = Iterables.concat(locData, calcCurve.yValues());
				String line = Parsing.join(lineDat, Delimiter.COMMA);
				lines.add(line);
			}
			
			Files.createDirectories(dir);
			String filename = imt + "-" + FILE_NAME;
			Path outPath = dir.resolve(filename);
			Files.write(outPath, lines, StandardCharsets.UTF_8);
		}
	}
	
	/*
	 * Creates a header row for a csv files that will contain data/curves matching the
	 * supplied sequence.
	 */
	private static String createHeaderRow(XY_Sequence sequence) {
		return "lon,lat," + Parsing.join(sequence.xValues(), Delimiter.COMMA);
	}
	
	// TODO rename to HazardCurves (already have this object as data container)
	//   or HazardCalc or just Hazard and handle single and map based calculations
	//
	// cinfig override could perhaps have sites.csv set as SiteSet
	
	// TODO always output to current directory?
	
	// TODO HazardCurve implementations/arguments:
	//  - model (look for config.json in model root - config must have site data)
	//  - model config.json (config must have site data)
	//  - model config.json sites.csv (sites.csv will override sites in config, if any)
	//
	//  - model Imt lon lat [[[vs30] vsInf] z1p0 z2p5]
	
//	public static MultiResult calc(HazardModel model, Imt imt, List<Site> sites) {
//
//		for (Site site : sites) {
//			calc(model, imt, site);
//		}
//		return null;
//	}

	
	// I guess these should be used to reduce one or more HazardResults down to just curves
	/**
	 * Multiple hazard curve wrapper.
	 */
	public static class MultiResult {

	}

	/**
	 * Single hazard curve wrapper.
	 */
	public static class Result {
		final ArrayXY_Sequence curve;

		Result(ArrayXY_Sequence curve) {
			this.curve = curve;
		}
	}

	/**
	 * Hazard curve calculator.
	 * 
	 * <p>Computing a single curve requires 4 or 5 {@code args}:
	 * {@code model.file Imt lon lat [vs30]}, where {@code model.file} is the
	 * path to a model zip file or directory, {@link Imt} is the intensity
	 * measure type identifier, and lon, lat, and vs30 are numbers. This
	 * approach</p>
	 * 
	 * <p>Computing multiple curves requires 4 {@code args}:
	 * {@code model.file Imt site.file, out.file}, where {@code model.file} is
	 * the path to a model zip file or directory, {@link Imt} is the intensity
	 * measure type identifier, and site.file is a file containing
	 * comma-delimited lon,lat[,vs30] values; lines starting with '#' are
	 * ignored.</p>
	 * 
	 * @param args
	 */
//	public static void main(String[] args) {
//		if (args.length < 4 || args.length > 5) {
//			System.err.println(USAGE);
//			System.exit(1);
//		}
//
//		Logging.init();
//		Logger log = Logger.getLogger(HazardCurve.class.getName());
//
//		try {
//			try {
//				// check if lon value is parseable and route to single calc
//				Double.valueOf(args[2]);
//				runSingle(args, log);
//			} catch (NumberFormatException nfe) {
//				// otherwise route to multi calc
//				runMulti(args, log);
//			}
//		} catch (Exception e) {
//			System.err.println("Error processing request; arguments: " + Arrays.toString(args));
//			System.err.println();
//			e.printStackTrace();
//			System.err.println();
//			System.err.println(USAGE);
//		}
//	}

//	/*
//	 * test args: ../nshmp-model-dev/models/2008/Western\ US PGA -118.25 34.05
//	 */
//	private static void runSingle(String[] args, Logger log) {
//		Path modelPath = Paths.get(args[0]);
//		Imt imt = Imt.valueOf(args[1]);
//		double lon = Double.valueOf(args[2]);
//		double lat = Double.valueOf(args[3]);
//		double vs30 = (args.length > 4) ? Double.valueOf(args[4]) : DEFAULT_VS_30;
//
//		Location loc = Location.create(lat, lon);
//		Site site = Site.builder().location(loc).vs30(vs30).build();
//
//		try {
//			log.info("");
//			log.info("HazardCurve: loading model ...");
//			log.info("");
//			HazardModel model = HazardModel.load(modelPath, modelPath.getFileName().toString());
//
//			log.info("");
//			log.info("HazardCurve: calculating curve ...");
//			Stopwatch sw = Stopwatch.createStarted();
//			HazardResult result = Calcs.hazardCurve(model, imt, site, modelCurve);
//			sw.stop();
//			log.info("HazardCurve: complete (" + sw.elapsed(MILLISECONDS) + "ms)");
//			System.out.println(Parsing.join(result.curve().yValues(), Delimiter.COMMA));
//			System.exit(0);
//		} catch (Exception e) {
//			StringBuilder sb = new StringBuilder(LF);
//			sb.append("** Calculation error: ").append(e.getMessage()).append(LF);
//			sb.append("** Exiting **").append(LF).append(LF);
//			log.log(SEVERE, sb.toString(), e);
//			System.exit(1);
//		}
//	}

//	/*
//	 * test args: ../nshmp-model-dev/models/2008/Western\ US PGA tmp/test/sites.csv tmp/test/curves.csv
//	 */
//	private static void runMulti(String[] args, Logger log) throws IOException {
//		Path modelPath = Paths.get(args[0]);
//		Imt imt = Imt.valueOf(args[1]);
//		Path sitesPath = Paths.get(args[2]);
//		Path outPath = Paths.get(args[3]);
//
//		List<Site> sites = readSitesFile(sitesPath);
//
//		log.info("");
//		log.info("HazardCurve: loading model ...");
//		log.info("");
//		HazardModel model = HazardModel.load(modelPath, modelPath.getFileName().toString());
//		List<HazardResult> results = new ArrayList<>();
//
//		try {
//			log.info("");
//			log.info("HazardCurve: calculating curves ...");
//			int count = 0;
//			Stopwatch sw = Stopwatch.createUnstarted();
//			for (Site site : sites) {
//				sw.start();
//				results.add(Calcs.hazardCurve(model, imt, site, modelCurve));
//				log.info("  " + count + " complete (" + sw.elapsed(MILLISECONDS) + "ms)");
//				sw.reset();
//				count++;
//			}
//			log.info("HazardCurve: writing curves ...");
//			writeCurvesFile(outPath, sites, results);
//			log.info("HazardCurve: complete");
//			System.exit(0);
//		} catch (Exception e) {
//			StringBuilder sb = new StringBuilder(LF);
//			sb.append("** Calculation error: ").append(e.getMessage()).append(LF);
//			sb.append("** Exiting **").append(LF).append(LF);
//			log.log(SEVERE, sb.toString(), e);
//			System.exit(1);
//		}
//	}

//	private static void writeCurvesFile(Path out, List<Site> sites, List<HazardResult> results)
//			throws IOException {
//		List<String> lines = Lists.newArrayList(HEADER1, HEADER2);
//		for (int i = 0; i < sites.size(); i++) {
//			StringBuilder sb = new StringBuilder();
//			Location loc = sites.get(i).location;
//			sb.append(loc.lon()).append(',').append(loc.lat()).append(',');
//			sb.append(Parsing.join(results.get(i).curve().yValues(), Delimiter.COMMA));
//			lines.add(sb.toString());
//		}
//		Files.write(out, lines, US_ASCII);
//	}

	private static List<Site> readSitesFile(Path path) throws IOException {
		List<String> lines = Files.readAllLines(path, US_ASCII);
		List<Site> sites = new ArrayList<>();
		for (String line : lines) {
			if (line.startsWith("#")) continue;
			List<Double> values = Parsing.splitToDoubleList(line, Delimiter.COMMA);
			Location loc = Location.create(values.get(1), values.get(0));
			double vs30 = (values.size() == 3) ? values.get(2) : DEFAULT_VS_30;
			sites.add(Site.builder().location(loc).vs30(vs30).build());
		}
		return sites;
	}
	
	// @formatter:off
	private static final String USAGE = new StringBuilder("HazardCurve usage:")
		.append(LF).append(LF)
		.append("command: java -cp nshmp-haz.jar org.opensha.programs.HazardCurve model.file Imt lon lat [vs30]")
		.append(LF)
		.append("example: java -cp nshmp-haz.jar org.opensha.programs.HazardCurve path/to/model.zip PGA -117.5 34.5 760.0")
		.append(LF)
		.append("  - or -")
		.append(LF)
		.append("command: java -cp nshmp-haz.jar org.opensha.programs.HazardCurve model.file Imt site.file out.file")
		.append(LF)
		.append("example: java -cp nshmp-haz.jar org.opensha.programs.HazardCurve path/to/model.zip PGA sites.csv curves.csv")
		.append(LF)
		.append(LF)
		.append("  - model.file is the path to a model zip file or directory.")
		.append(LF)
		.append("  - sites.file is a file containing comma-delimited lon,lat[,vs30] values; lines starting with '#' are ignored.")
		.append(LF)
		.append("  - if no vs30 is specified 760.0 m/s is used.")
		.append(LF)
		.append("  - For more details, see: http://usgs.github.io/nshmp-haz/docs/org/opensha/programs/HazardCurve.html")
		.toString();
}
