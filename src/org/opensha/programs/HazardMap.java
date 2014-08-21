package org.opensha.programs;

import static com.google.common.base.StandardSystemProperty.*;
import static com.google.common.base.Preconditions.checkArgument;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.opensha.calc.HazardResult;
import org.opensha.calc.Site;
import org.opensha.data.ArrayXY_Sequence;
import org.opensha.eq.forecast.Forecast;
import org.opensha.geo.GriddedRegion;
import org.opensha.geo.Location;
import org.opensha.geo.Regions;
import org.opensha.gmm.Imt;
import org.opensha.util.Parsing;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.StandardSystemProperty;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.primitives.Doubles;

/**
 * Entry point for computing a hazard curve at a {@link Site} from a {@link Forecast}.
 *
 * @author Peter Powers
 */
public class HazardMap {
	
	// TODO expose
	private static final double SPACING = 0.1;

	// hazCurve: year/model/lon/lat/
	public static Result create(Forecast forecast, Imt imt, Path locPath) {
		
		return null;
	}
	
	public static Result create(Forecast forecast, Imt imt, Location p1, Location p2) {
		GriddedRegion mapRegion = Regions.createRectangularGridded(
			forecast.name() + " Map", p1, p2, SPACING, SPACING, GriddedRegion.ANCHOR_0_0);
		
		return null;
	}
	
	static Result create(Forecast forecast, Imt imt, Iterable<Site> sites) {
		Builder<Site> siteBuilder = ImmutableList.builder();
		Builder<ArrayXY_Sequence> curveBuilder = ImmutableList.builder();
		for (Site site : sites) {
			siteBuilder.add(site);
			curveBuilder.add(HazardCurve.calc(forecast, imt, site).curve);
		}
		return new Result(siteBuilder.build(), curveBuilder.build());
	}

	/**
	 * The result of a HazardMap calculation.
	 */
	public static class Result {
		
		/** The {@code List} of {@code Location}s */
		public final List<Site> sites;
		
		/** A {@code List} of {@code Location}s */
		public final List<ArrayXY_Sequence> curves;
		
		Result(List<Site> sites, List<ArrayXY_Sequence> curves) {
			this.sites = sites;
			this.curves = curves;
		}
		
		void export(Path path) throws IOException {
			Joiner joiner = Joiner.on(',');
			List<String> lines = new ArrayList<>();
			List<Object> header = Lists.newArrayList();
			header.add("lon");
			header.add("lat");
			header.addAll(curves.get(0).xValues());
			lines.add(joiner.join(header));
			for (int i=0; i<sites.size(); i++) {
				Site s = sites.get(i);
				List<Double> line = Lists.newArrayList(s.loc.lon(), s.loc.lat());
				line.addAll(curves.get(i).yValues());
			}
			Files.write(path, lines, Charset.forName("UTF-8"));
		}
		
	}
	
	// file of coords [vs30]
	// 2 Locations ofr rect region
	
	// output dir
	
	// java -cp nshmp-haz.jar HazardMap /path/to/Forecast.zip/or/directory PGA -120.0 33.0 -115.0 36.0
	// java -cp nshmp-haz.jar HazardMap /path/to/Forecast.zip/or/directory PGA /path/to/loc.dat 
	
	// all supported Imts
	// java -cp nshmp-haz.jar ResponseSpectra /path/to/Forecast.zip/or/directory -117.5 34.5
	
	// other
	// java -cp nshmp-haz.jar DetermResponseSpectra args[]
	
	
	
	
	
	// loc.dat
	// #
	// lon,lat[,vs30]
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		checkArgument(args.length == 7 || args.length == 4, USAGE);
		try {
			Forecast forecast = Forecast.load(args[0], args[0]);
			Imt imt = Imt.parseImt(args[1]);
			Path locsPath = null;
			Location loc1 = null;
			Location loc2 = null;
			Path outPath = null;
			Result map = null;
			if (args.length == 3) {
				locsPath = Paths.get(args[2]);
				outPath = Paths.get(args[3]);
				map = create(forecast, imt, locsPath);
			} else {
				loc1 = Location.create(Double.valueOf(args[3]), Double.valueOf(args[2]));
				loc2 = Location.create(Double.valueOf(args[5]), Double.valueOf(args[4]));
				outPath = Paths.get(args[6]);
				map = create(forecast, imt, loc1, loc2);
			}
			map.export(outPath);
			return;
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private static final String USAGE =
			"HazardMap Usage:" + LINE_SEPARATOR.value() +
			"  java -cp nshmp-haz.jar HazardMap /path/to/forecast.zip/or/directory PGA -120.0 33.0 -115.0 36.0" + LINE_SEPARATOR.value() +
			"  java -cp nshmp-haz.jar HazardMap /path/to/forecast.zip/or/directory PGA /path/to/loc.dat";

	static class LocationToSite implements Function<Location, Site> {
		@Override public Site apply(Location loc) {
			return Site.create(loc);
		}
	}

	static class StringToSite implements Function<String, Site> {
		// @formatter:off
		@Override public Site apply(String locDat) {
			List<Double> parts =  FluentIterable
					.from(Parsing.splitOnCommas(locDat))
					.transform(Doubles.stringConverter())
					.toList();
			Location loc = Location.create(parts.get(1), parts.get(0));
			double vs30 = (parts.size() > 2) ? parts.get(2) : 760.0;
			return Site.create(loc, vs30);
		}
		// @formatter:on
	}

}
