package org.opensha.programs;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.StandardSystemProperty.LINE_SEPARATOR;

import org.opensha.calc.HazardResult;
import org.opensha.calc.Site;
import org.opensha.data.ArrayXY_Sequence;
import org.opensha.eq.forecast.Forecast;
import org.opensha.geo.Location;
import org.opensha.gmm.Imt;

/**
 * Entry point for computing a hazard curve at a {@link Site} from a {@link Forecast}.
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

	

	// hazCurve: year/model/imt/lon/lat/
	
	
	public static Result calc(Forecast forecast, Imt imt, Site site) {
		return null;
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
		checkArgument(args.length == 4, USAGE);

		// TODO do nothing

	}
	
	private static final String USAGE =
			"HazardCurve Usage:" + LINE_SEPARATOR.value() +
			"  java -cp nshmp-haz.jar HazardCurve /path/to/forecast.zip/or/directory PGA -117.5 34.5";

}
