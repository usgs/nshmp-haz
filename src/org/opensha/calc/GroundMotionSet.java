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
 * Container class for scalar ground motions associated individual
 * {@code Source}s.
 * 
 * @author Peter Powers
 */
public final class GroundMotionSet {

	//TODO make package private
	
	// NOTE the inputList supplied to Builder will be immutable
	// but the mean and sigma list maps are not; builder backs
	// means and sigmas with double[]

	final List<GmmInput> inputs;
	final Map<Gmm, List<Double>> means;
	final Map<Gmm, List<Double>> sigmas;

	private GroundMotionSet(List<GmmInput> inputs, Map<Gmm, List<Double>> means,
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

		private boolean built = false;
		private final int size;
		private int addCount = 0;

		private Builder(List<GmmInput> inputs, Set<Gmm> gmms) {
			checkArgument(checkNotNull(inputs).size() > 0);
			checkArgument(checkNotNull(gmms).size() > 0);
			this.inputs = inputs;
			means = initValueMap(gmms, inputs.size());
			sigmas = initValueMap(gmms, inputs.size());
			size = gmms.size() * inputs.size();
		}

		Builder add(Gmm gmm, ScalarGroundMotion sgm, int index) {
			checkState(addCount < size, "This %s instance is already full", ID);
			means.get(gmm).set(index, sgm.mean());
			sigmas.get(gmm).set(index, sgm.sigma());
			addCount++;
			return this;
		}

		GroundMotionSet build() {
			checkState(!built, "This %s instance has already been used", ID);
			checkState(addCount == size, "Only %s of %s entries have been added", addCount, size);
			built = true;
			return new GroundMotionSet(inputs, means, sigmas);
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
