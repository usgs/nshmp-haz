package org.opensha.programs;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.StandardSystemProperty.LINE_SEPARATOR;

import java.util.List;

import org.opensha.calc.HazardResult;
import org.opensha.calc.Site;
import org.opensha.data.ArrayXY_Sequence;
import org.opensha.eq.model.HazardModel;
import org.opensha.geo.Location;
import org.opensha.gmm.Imt;

/**
 * Entry point for computing a hazard curve at a {@link Site} from a
 * {@link HazardModel}.
 * 
 * @author Peter Powers
 */
public class HazardCurve {

	// TODO hold on to this for other services
	// Iterator<String> args =
	// Parsing.splitOnSlash(request.getQueryString()).iterator();
	// Iterable<Gmm> gmms =
	// Iterables.transform(Parsing.splitOnCommas(args.next()),
	// Enums.stringConverter(Gmm.class));
	// Iterable

	// hazCurve service: year/model/imt/lon/lat/

	// for docs, the results provided by these classes provide mean hazard
	// curves for the model specified,
	// for more detailed results at a site consider using ...

	public static Result calc(HazardModel forecast, Imt imt, Site site) {
		return null;
	}

	public static Result calc(HazardModel forecast, Imt imt, List<Site> sites) {
		
		for (Site site : sites) {
			calc(forecast, imt, site);
		}
		return null;
	}

	public static class MultiResult {

	}

	public static class Result {
		final ArrayXY_Sequence curve;

		Result(ArrayXY_Sequence curve) {
			this.curve = curve;
		}
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		System.out.println(USAGE);
		// if (args.length != 14) {
		// System.err.println(USAGE);
		// System.exit(1);
		// }
		// checkArgument(args.length == 4, USAGE);

	}

	private static final String USAGE = "HazardCurve usage:" +
		LINE_SEPARATOR.value() +
		LINE_SEPARATOR.value() +
		"command: java -cp nshmp-haz.jar org.opensha.programs.HazardCurve model.file Imt lon lat vs30" +
		LINE_SEPARATOR.value() +
		"example: java -cp nshmp-haz.jar org.opensha.programs.HazardCurve path/to/model.zip PGA -117.5 34.5 vs30" +
		LINE_SEPARATOR.value() +
		"  - or -" +
		LINE_SEPARATOR.value() +
		"command: java -cp nshmp-haz.jar org.opensha.programs.HazardCurve model.file Imt site.file out.file" +
		LINE_SEPARATOR.value() +
		"example: java -cp nshmp-haz.jar org.opensha.programs.HazardCurve path/to/model.zip PGA sites.csv curves.csv" +
		LINE_SEPARATOR.value() +
		LINE_SEPARATOR.value() +
		"  - model.file is the path to a model zip file or directory." +
		LINE_SEPARATOR.value() +
		"  - sites.file is a file containing comma-delimited [lon,lat,vs30] values; lines starting with '#' are ignored." +
		LINE_SEPARATOR.value() +
		"  - For more details, see: http://usgs.github.io/nshmp-haz/docs/org/opensha/programs/HazardCurve.html";

}
