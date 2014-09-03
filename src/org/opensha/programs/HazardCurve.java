package org.opensha.programs;

import static com.google.common.base.StandardSystemProperty.LINE_SEPARATOR;
import static java.nio.charset.StandardCharsets.US_ASCII;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.opensha.calc.Site;
import org.opensha.data.ArrayXY_Sequence;
import org.opensha.eq.model.HazardModel;
import org.opensha.geo.Location;
import org.opensha.gmm.Imt;
import org.opensha.util.Parsing;

import com.google.common.base.Charsets;

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

	/**
	 * Calculate a hazard curve at a {@link Site}.
	 * @param model to use
	 * @param imt intensity measure type
	 * @param site of interest
	 */
	public static Result calc(HazardModel model, Imt imt, Site site) {
		return null;
	}

	public static MultiResult calc(HazardModel model, Imt imt, List<Site> sites) {

		for (Site site : sites) {
			calc(model, imt, site);
		}
		return null;
	}

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
	 * measure type identifier, and lon, lat, and vs30 are numbers.</p>
	 * 
	 * <p>Computing multiple curves requires 3 {@code args}:
	 * {@code model.file Imt site.file}, where {@code model.file} is the path to
	 * a model zip file or directory, {@link Imt} is the intensity measure type
	 * identifier, and site.file is a file containing comma-delimited
	 * lon,lat[,vs30] values; lines starting with '#' are ignored.</p>
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		if (args.length < 3 || args.length > 5) {
			System.err.println(USAGE);
			System.exit(1);
		}
		try {
			try {
				// check if lon value is parseable and route to single calc
				Double.valueOf(args[2]);
				runSingle(args);
			} catch (NumberFormatException nfe) {
				// otherwise route to multi calc
				runMulti(args);
			}
		} catch (Exception e) {
			System.err.println("Error processing request; arguments: " + Arrays.toString(args));
			System.err.println();
			e.printStackTrace();
			System.err.println();
			System.err.println(USAGE);
		}
	}

	private static void runSingle(String[] args) {
		Path modelPath = Paths.get(args[0]);
		Imt imt = Imt.valueOf(args[1]);
		double lon = Double.valueOf(args[2]);
		double lat = Double.valueOf(args[3]);
		double vs30 = (args.length > 4) ? Double.valueOf(args[4]) : 760.0;
		Location loc = Location.create(lat, lon);
		Site site = Site.create(loc, vs30);

	}

	private static void runMulti(String[] args) {
		Path modelPath = Paths.get(args[0]);
		Imt imt = Imt.valueOf(args[1]);
		Path sitesPath = Paths.get(args[2]);

	}
	
	private static List<Site> readSitesFile(Path path) throws IOException {
		List<String> lines = Files.readAllLines(path, US_ASCII);
		List<Site> sites = new ArrayList<>();
		for (String line : lines) {
//			List<Double> values = Fluent Parsing.spl
		}
		return null;
	}

	private static final String USAGE = "HazardCurve usage:" +
		LINE_SEPARATOR.value() +
		LINE_SEPARATOR.value() +
		"command: java -cp nshmp-haz.jar org.opensha.programs.HazardCurve model.file Imt lon lat [vs30]" +
		LINE_SEPARATOR.value() +
		"example: java -cp nshmp-haz.jar org.opensha.programs.HazardCurve path/to/model.zip PGA -117.5 34.5 760.0" +
		LINE_SEPARATOR.value() +
		"  - or -" +
		LINE_SEPARATOR.value() +
		"command: java -cp nshmp-haz.jar org.opensha.programs.HazardCurve model.file Imt site.file" +
		LINE_SEPARATOR.value() +
		"example: java -cp nshmp-haz.jar org.opensha.programs.HazardCurve path/to/model.zip PGA sites.csv" +
		LINE_SEPARATOR.value() +
		LINE_SEPARATOR.value() +
		"  - model.file is the path to a model zip file or directory." +
		LINE_SEPARATOR.value() +
		"  - sites.file is a file containing comma-delimited lon,lat[,vs30] values; lines starting with '#' are ignored." +
		LINE_SEPARATOR.value() +
		"  - if no vs30 is specified 760.0 m/s is used." +
		LINE_SEPARATOR.value() +
		"  - For more details, see: http://usgs.github.io/nshmp-haz/docs/org/opensha/programs/HazardCurve.html";

}
