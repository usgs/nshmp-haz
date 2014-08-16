package org.opensha.calc;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.StandardSystemProperty.LINE_SEPARATOR;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.opensha.gmm.Gmm;

import com.google.common.collect.Maps;
import com.google.common.primitives.Doubles;

/**
 * Container class for scalar ground motions associated individual
 * {@code Source}s in a {@code SourceSet}.
 * 
 * @author Peter Powers
 */
final class HazardGroundMotions {

	// TODO make package private

	/*
	 * NOTE the inputList supplied to Builder will be immutable but the mean and
	 * sigma list maps are not; builder backs mean and sigma lists with
	 * double[].
	 * 
	 * Can't use Multimaps.newListMultimap(map, factory) backed with
	 * Doubles.asList(double[]) because list must be empty to start with.
	 * 
	 * http://code.google.com/p/guava-libraries/issues/detail?id=1827
	 */

	final HazardInputs inputs;
	final Map<Gmm, List<Double>> means;
	final Map<Gmm, List<Double>> sigmas;

	private HazardGroundMotions(HazardInputs inputs, Map<Gmm, List<Double>> means,
		Map<Gmm, List<Double>> sigmas) {
		this.inputs = inputs;
		this.means = means;
		this.sigmas = sigmas;
	}

	@Override public String toString() {
		StringBuilder sb = new StringBuilder(getClass().getSimpleName());
		sb.append(" [").append(inputs.parent.name()).append("]");
		sb.append(": ").append(LINE_SEPARATOR.value());
		for (int i = 0; i < inputs.size(); i++) {
			sb.append(inputs.get(i));
			sb.append(" ");
			for (Gmm gmm : means.keySet()) {
				sb.append(gmm.name()).append(" ");
				sb.append(String.format("%.3f", means.get(gmm).get(i))).append(" ");
				sb.append(String.format("%.3f", sigmas.get(gmm).get(i))).append(" ");
			}
			sb.append(LINE_SEPARATOR.value());
		}
		return sb.toString();
	}

	static Builder builder(HazardInputs inputs, Set<Gmm> gmms) {
		return new Builder(inputs, gmms);
	}

	static class Builder {

		private static final String ID = "HazardGroundMotions.Builder";
		private boolean built = false;
		private final int size;
		private int addCount = 0;

		private final HazardInputs inputs;
		private final Map<Gmm, List<Double>> means;
		private final Map<Gmm, List<Double>> sigmas;

		private Builder(HazardInputs inputs, Set<Gmm> gmms) {
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

		HazardGroundMotions build() {
			checkState(!built, "This %s instance has already been used", ID);
			checkState(addCount == size, "Only %s of %s entries have been added", addCount, size);
			built = true;
			return new HazardGroundMotions(inputs, means, sigmas);
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
