package org.opensha2.calc;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.StandardSystemProperty.LINE_SEPARATOR;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.opensha2.gmm.Gmm;
import org.opensha2.gmm.Imt;
import org.opensha2.gmm.ScalarGroundMotion;

import com.google.common.collect.Maps;
import com.google.common.primitives.Doubles;

/**
 * Container class for scalar ground motions associated with individual
 * {@code Source}s in a {@code SourceSet}.
 * 
 * @author Peter Powers
 */
final class GroundMotions {

	/*
	 * NOTE the inputList supplied to Builder is immutable but the mean and
	 * sigma lists it builds are not; builder backs mean and sigma lists with
	 * double[]. Nor are the mean and sigma maps immutable.
	 * 
	 * TODO It would be nice to have an immutable variant of a doubl[] backed
	 * list, but would require copying values on build().
	 * 
	 * TODO refactor to μLists σLists
	 */

	final InputList inputs;
	final Map<Imt, Map<Gmm, List<Double>>> means;
	final Map<Imt, Map<Gmm, List<Double>>> sigmas;

	private GroundMotions(InputList inputs, Map<Imt, Map<Gmm, List<Double>>> means,
			Map<Imt, Map<Gmm, List<Double>>> sigmas) {
		this.inputs = inputs;
		this.means = means;
		this.sigmas = sigmas;
	}

	@Override public String toString() {
		StringBuilder sb = new StringBuilder(getClass().getSimpleName());
		sb.append(" [").append(inputs.parentName()).append("]");
		sb.append(": ").append(LINE_SEPARATOR.value());
		for (int i = 0; i < inputs.size(); i++) {
			sb.append(inputs.get(i));
			sb.append(" ");
			for (Entry<Imt, Map<Gmm, List<Double>>> imtEntry : means.entrySet()) {
				Imt imt = imtEntry.getKey();
				sb.append(imt.name()).append(" [");
				for (Entry<Gmm, List<Double>> gmmEntry : imtEntry.getValue().entrySet()) {
					Gmm gmm = gmmEntry.getKey();
					sb.append(gmm.name()).append(" ");
					sb.append(String.format("μ=%.3f", gmmEntry.getValue().get(i))).append(" ");
					sb.append(String.format("σ=%.3f", sigmas.get(imt).get(gmm).get(i))).append(" ");
				}
				sb.append("] ");
			}
			sb.append(LINE_SEPARATOR.value());
		}
		return sb.toString();
	}

	static Builder builder(InputList inputs, Set<Imt> imts, Set<Gmm> gmms) {
		return new Builder(inputs, imts, gmms);
	}

	static class Builder {

		private static final String ID = "GroundMotions.Builder";
		private boolean built = false;
		private final int size;
		private int addCount = 0;

		private final InputList inputs;
		private final Map<Imt, Map<Gmm, List<Double>>> means;
		private final Map<Imt, Map<Gmm, List<Double>>> sigmas;

		private Builder(InputList inputs, Set<Imt> imts, Set<Gmm> gmms) {
			checkArgument(inputs.size() > 0);
			checkArgument(gmms.size() > 0);
			this.inputs = inputs;
			means = initValueMaps(imts, gmms, inputs.size());
			sigmas = initValueMaps(imts, gmms, inputs.size());
			size = imts.size() * gmms.size() * inputs.size();
		}

		Builder add(Imt imt, Gmm gmm, ScalarGroundMotion sgm, int index) {
			checkState(addCount < size, "This %s instance is already full", ID);
			means.get(imt).get(gmm).set(index, sgm.mean());
			sigmas.get(imt).get(gmm).set(index, sgm.sigma());
			addCount++;
			return this;
		}

		GroundMotions build() {
			checkState(!built, "This %s instance has already been used", ID);
			checkState(addCount == size, "Only %s of %s entries have been added", addCount, size);
			built = true;
			return new GroundMotions(inputs, means, sigmas);
		}

		static Map<Imt, Map<Gmm, List<Double>>> initValueMaps(
				Set<Imt> imts,
				Set<Gmm> gmms,
				int size) {
			
			Map<Imt, Map<Gmm, List<Double>>> imtMap = Maps.newEnumMap(Imt.class);
			for (Imt imt : imts) {
				Map<Gmm, List<Double>> gmmMap = Maps.newEnumMap(Gmm.class);
				for (Gmm gmm : gmms) {
					gmmMap.put(gmm, Doubles.asList(new double[size]));
				}
				imtMap.put(imt, gmmMap);
			}
			return imtMap;
		}

	}
}
