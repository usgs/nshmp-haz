package org.opensha2.eq.model;

import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.opensha2.calc.GridCalc;
import org.opensha2.calc.Site;
import org.opensha2.data.Data2D;
import org.opensha2.geo.Location;
import org.opensha2.geo.Locations;
import org.opensha2.util.Parsing;

import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.primitives.Doubles;

/**
 * Add comments here
 *
 * @author Peter Powers
 */
public class GridSourceSetTable  {

	/*
	 * These tables are created on a per-calculation basis and are unique to a site.
	 * 
	 * This class reduces point source iteration down to a table of distances and magnitudes
	 * 
	 * We are going to want to upgrade this to Data3D to handle azimuth bins
	 */
	
	private int parentSourcesUsed;
	/*
	 * Given a GridSourceSet, transpose it to a ... 
	 * 
	 * loop all sources for site, combine mfds into mfd, one for each distance bin
	 * 
	 * or put them in Data2D
	 * 
	 */
	
	public static void main(String[] args) {
		double[] dd = new double[] {-1.0000345, 5.67823, 2.5678901e-8};
		System.out.println(Arrays.toString(dd));
		System.out.println(Parsing.toString(Doubles.asList(dd), "%s"));
		String format = "%8.3E";
		System.out.println(Parsing.toString(Doubles.asList(dd), format, ", ", true));
		
		String tmp = "[  180.50] [0.00e+00, 0.00e+00, 0.00e+00, 9.64e-05, 8.01e-05, 6.67e-05, 5.54e-05, 4.61e-05, 3.84e-05, 3.19e-05, 2.65e-05, 2.21e-05, 1.84e-05, 1.53e-05, 1.27e-05, 1.06e-05, 8.79e-06, 7.31e-06, 9.90e-07, 8.24e-07, 6.85e-07, 5.70e-07, 4.74e-07, 0.00e+00, 0.00e+00, 0.00e+00, 0.00e+00, 0.00e+00, 0.00e+00, 0.00e+00, 0.00e+00, 0.00e+00, 0.00e+00]";
		System.out.println(tmp);
		tmp.replace("0.00e+00", "zz");
		System.out.println(tmp);
	}
	
//	/* To support indenting of multidimensional arrays */
//	private String toString2(double[] data) {
//		StringBuilder sb = new StringBuilder("[");
//		retrParsing.toString(Doubles.asList(data), "%s");
//		String values = Joiner.on(", ").join(data)
//		for (int i = 0; i < data.length; i++) {
//			sb.append(data[i])
////			if (i > 0) sb.append(",").append(NEWLINE).append(repeat(" ", indent));
//			sb.append(Arrays.toString(data[i]));
//		}
//		sb.append("]");
//		return sb.toString();
//	}

	
	
	public static Data2D toSourceTable(GridSourceSet sources, Location loc) {
		
		double rMax = sources.groundMotionModels().maxDistance();
		Data2D.Builder tableBuilder = GridCalc.createGridBuilder(rMax);
		
//		System.out.println(rMax);
//		System.out.println(sources.name());
//		System.out.println(Arrays.toString(table.rows()));
//		System.out.println(Arrays.toString(table.columns()));
//		
		
		
		for (PointSource source : sources.iterableForLocation(loc)) {
			double r = Locations.horzDistanceFast(loc, source.loc);
			double mMin = source.mfd.getMinX();
			List<Double> rates = source.mfd.yValues();
			tableBuilder.add(r, mMin,rates);
		}
		
		Data2D table = tableBuilder.build();
		
		return table;
	}
	
//	private Predicate

//	@Override public SourceType type() {
//		return null;
//		// TODO do nothing
//		
//	}
//
//	@Override public int size() {
//		return 0;
//		// TODO do nothing
//		
//	}
//
//	@Override public Predicate<PointSource> distanceFilter(Location loc, double distance) {
//		return null;
//		// TODO do nothing
//		
//	}
//
//	@Override public Iterator<PointSource> iterator() {
//		return null;
//		// TODO do nothing
//		
//	}
}
