package org.opensha2.calc;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.StandardSystemProperty.LINE_SEPARATOR;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.opensha2.gmm.Gmm;
import org.opensha2.gmm.Imt;
import org.opensha2.gmm.ScalarGroundMotion;

import com.google.common.primitives.Doubles;

/**
 * Container class for scalar ground motions associated with individual
 * {@code Source}s in a {@code SourceSet}.
 * 
 * @author Peter Powers
 */
final class GroundMotions {

	/*
	 * NOTE the inputList supplied to Builder will be immutable but the mean and
	 * sigma list tables are not; builder backs mean and sigma lists with
	 * double[].
	 * 
	 * Can't use Multimaps.newListMultimap(map, factory) backed with
	 * Doubles.asList(double[]) because list must be empty to start with and
	 * growable.
	 * 
	 * http://code.google.com/p/guava-libraries/issues/detail?id=1827
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

	static Builder builder(InputList inputs, Set<Gmm> gmms, Set<Imt> imts) {
		return new Builder(inputs, gmms, imts);
	}

	static class Builder {

		private static final String ID = "HazardGroundMotions.Builder";
		private boolean built = false;
		private final int size;
		private int addCount = 0;

		private final InputList inputs;
		private final Map<Imt, Map<Gmm, List<Double>>> means;
		private final Map<Imt, Map<Gmm, List<Double>>> sigmas;

		private Builder(InputList inputs, Set<Gmm> gmms, Set<Imt> imts) {
			checkArgument(checkNotNull(inputs).size() > 0);
			checkArgument(checkNotNull(gmms).size() > 0);
			this.inputs = inputs;
			means = initValueTable(gmms, imts, inputs.size());
			sigmas = initValueTable(gmms, imts, inputs.size());
			size = gmms.size() * imts.size() * inputs.size();
		}

		Builder add(Gmm gmm, Imt imt, ScalarGroundMotion sgm, int index) {
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

		static Map<Imt, Map<Gmm, List<Double>>> initValueTable(Set<Gmm> gmms, Set<Imt> imts,
				int size) {
			Map<Imt, Map<Gmm, List<Double>>> imtMap = new EnumMap<>(Imt.class);
			for (Imt imt : imts) {
				Map<Gmm, List<Double>> gmmMap = new EnumMap<>(Gmm.class);
				for (Gmm gmm : gmms) {
					gmmMap.put(gmm, Doubles.asList(new double[size]));
				}
				imtMap.put(imt, gmmMap);
			}
			return imtMap;
		}

	}
}
