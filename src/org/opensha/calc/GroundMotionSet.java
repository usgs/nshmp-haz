package org.opensha.calc;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.StandardSystemProperty.LINE_SEPARATOR;

import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.opensha.gmm.Gmm;
import org.opensha.gmm.GmmInput;

import com.google.common.base.Supplier;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimaps;
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
	// mean and sigma lists with double[].

	public final GmmInputList inputs;
	public final ListMultimap<Gmm, Double> means;
	public final ListMultimap<Gmm, Double> sigmas;

	private GroundMotionSet(GmmInputList inputs, ListMultimap<Gmm, Double> means,
			ListMultimap<Gmm, Double> sigmas) {
		this.inputs = inputs;
		this.means = means;
		this.sigmas = sigmas;
	}
	
	@Override public String toString() {
		StringBuilder sb = new StringBuilder(getClass().getSimpleName());
		sb.append(" [").append(inputs.parent.name()).append("]");
		sb.append(": ").append(LINE_SEPARATOR.value());
		for (int i=0; i<inputs.size(); i++) {
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

	static Builder builder(GmmInputList inputs, Set<Gmm> gmms) {
		return new Builder(inputs, gmms);
	}

	static class Builder {

		private static final String ID = "ScalarGroundMotionSet.Builder";

		private final GmmInputList inputs;
//		private final Map<Gmm, List<Double>> means;
//		private final Map<Gmm, List<Double>> sigmas;
		private final ListMultimap<Gmm, Double> means;
		private final ListMultimap<Gmm, Double> sigmas;

		private boolean built = false;
		private final int size;
		private int addCount = 0;

		private Builder(GmmInputList inputs, Set<Gmm> gmms) {
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

//		static Map<Gmm, List<Double>> initValueMap(Set<Gmm> gmms, int size) {
//			Map<Gmm, List<Double>> map = Maps.newEnumMap(Gmm.class);
//			for (Gmm gmm : gmms) {
//				map.put(gmm, Doubles.asList(new double[size]));
//			}
//			return map;
//		}
		
		static ListMultimap<Gmm, Double> initValueMap(Set<Gmm> gmms,final int size) {
			Map<Gmm, Collection<Double>> map = Maps.newEnumMap(Gmm.class);
			Supplier<List<Double>> factory = new Supplier<List<Double>>() {
				@Override public List<Double> get() {
					return Doubles.asList(new double[size]);
				}
			};
			return Multimaps.newListMultimap(map , factory);
		}

	}
}
