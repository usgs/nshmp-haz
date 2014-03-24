package org.opensha.calc;

import java.util.Iterator;
import java.util.List;

import com.google.common.collect.Lists;
import com.google.common.primitives.Doubles;

/**
 * Contains a set of hazard calculation results. For a site of interest, the
 * results should only span those ruptures that passed the distance filter
 * up the food chain.
 * 
 * This class does 
 * 
 * Hazard calculation pipeline
 * 
 * HazardCalc distance filtering
 * 		should applicable distance be provided by sources?
 * 		TRT based distance filtering
 *
 * 
 * @author Peter Powers
 * @version $Id:$
 */
public class HazardCalcResultSet implements Iterable<ScalarGroundMotion> {

	private List<Double> means;
	private List<Double> stds;
	//int end
	
	// carry rupture references along, or just a few fields that are decoupled
	// from source-rupture objects to limit possible object mutation
	
	/**
	 * Create a new result container.
	 */
	public void create() {
		means = Lists.newArrayList();
		stds = Lists.newArrayList();
	}
	
	/**
	 * Creates an optimized hazard result container.
	 * @param size
	 */
	public void create(int size) {
		means = Doubles.asList(new double[size]);
		means = Doubles.asList(new double[size]);
	}
	
	public void set(double mean, double std) {
		means.add(mean);
		stds.add(std);
	}
	
	@Override
	public Iterator<ScalarGroundMotion> iterator() {
		final ResultContainer result = new ResultContainer(this);
		return new Iterator<ScalarGroundMotion>() {
			//private sResultContainer result = new ResultContainer(this);
			private int caret = 0;
			private int size = means.size();
			@Override
			public boolean hasNext() {
				return caret < size;
			}
			@Override
			public ScalarGroundMotion next() {
				result.idx = caret++;
				return result;
			}
			@Override
			public void remove() {
				throw new UnsupportedOperationException();
			}
		};
	}

	private static class ResultContainer implements ScalarGroundMotion {
		private HazardCalcResultSet results;
		private int idx;
		private ResultContainer(HazardCalcResultSet results) {
			this.results = results;
		}
		@Override
		public double mean() { return results.means.get(idx); }
		@Override
		public double stdDev() { return results.stds.get(idx); }
	}
	
}
