package org.opensha.calc;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.opensha.gmm.Gmm;
import org.opensha.gmm.GmmInput;

import com.google.common.collect.Maps;
import com.google.common.primitives.Doubles;

/**
 * Add comments here
 *
 * @author Peter Powers
 */
public final class GroundMotionCalcResultSet {

	// TODO package privatize
	
	// TODO rename to ScalarGroundMotionSet
	
	// TODO the inputList supplied to Builder will be immutable
	// now that we're taking on large scale calculations, the mean and sigma map lists 
	// are not immutable (note in jdocs)
	
	List<GmmInput> inputs;
	Map<Gmm, List<Double>> means;
	Map<Gmm, List<Double>> sigmas;
	
	private GroundMotionCalcResultSet(List<GmmInput> inputs, Map<Gmm, List<Double>> means,
		Map<Gmm, List<Double>> sigmas) {
		this.inputs = inputs;
		this.means = means;
		this.sigmas = sigmas;
	}

	static Builder builder(List<GmmInput> inputs, Set<Gmm> gmms) {
		return new Builder(inputs, gmms);
	}
	
	static class Builder {
		
		private static final String ID = "ScalarGroundMotionSet.Builder";
		
		private final List<GmmInput> inputs;
		private final Map<Gmm, List<Double>> means;
		private final Map<Gmm, List<Double>> sigmas;
		
		boolean built = false;
		int addCount = 0;
		
		private Builder(List<GmmInput> inputs, Set<Gmm> gmms) {
			checkArgument(checkNotNull(inputs).size() > 0);
			checkArgument(checkNotNull(gmms).size() > 0);
			this.inputs = inputs;
			means = initValueMap(gmms, inputs.size());
			sigmas = initValueMap(gmms, inputs.size());
		}
		
		Builder add(Gmm gmm, double mean, double sigma) {
			checkState(addCount < inputs.size(), "This %s instance is already full", ID);
			means.get(gmm).set(addCount, mean);
			sigmas.get(gmm).set(addCount, sigma);
			addCount++;
			return this;
		}
		
		GroundMotionCalcResultSet build() {
			checkState(!built, "This %s instance has already been used", ID);
			checkState(addCount == inputs.size(), "Only %s of %s entries have been added",
				addCount, inputs.size());
			built = true;
			return new GroundMotionCalcResultSet(inputs, means, sigmas);
		}
		
		static Map<Gmm, List<Double>> initValueMap(Set<Gmm> gmms, int size) {
			Map<Gmm, List<Double>> map = Maps.newEnumMap(Gmm.class);
			for (Gmm gmm : gmms) {
				map.put(gmm, Doubles.asList(new double[size]));
			}
			return map;
		}
		
	}
}
