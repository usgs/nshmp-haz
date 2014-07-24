package org.opensha.eq.forecast;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.StandardSystemProperty.LINE_SEPARATOR;

import java.util.Iterator;
import java.util.List;

import org.opensha.mfd.IncrementalMfd;

import com.google.common.collect.Lists;

/**
 * Cluster source representation. This class wraps a {@code FaultSourceSet} of
 * {@code FaultSource}s that occur as independent events but with a similar
 * rate. Cluster sources are calculated using the joint probabilities of ground
 * motions from the wrapped faults. They are handled internally by a separate
 * calculator and {@link ClusterSource#iterator()} therefore throws an
 * {@code UnsupportedOperationException}.
 * 
 * <p>A {@code ClusterSource} cannot be created directly; it may only be
 * created by a private parser.</p>
 * 
 * @author Peter Powers
 */
public class ClusterSource implements Source {

	// NOTE several methods delegate to internal FaultSourceSet
	
//	private final String name;
//	final double weight;
	final double rate; // from the default mfd xml
	final FaultSourceSet faults;

	ClusterSource(double rate, FaultSourceSet faults) {
//		this.name = name;
//		this.weight = weight;
		this.rate = rate;
		this.faults = faults;
	}

	/**
	 * Returns (1 / return-period) of this source in years.
	 * @return the cluster rate
	 */
	public double rate() {
		return rate;
	}

	/**
	 * Returns the weight that should be applied to this source.
	 * @return the source weight
	 */
	public double weight() {
		// TODO not sure this is necessary
		return faults.weight();
	}

	/**
	 * Returns the {@code FaultSourceSet} of all {@code FaultSource}s that
	 * participate in this cluster.
	 * @return a list of all {@code FaultSource}s
	 */
	public FaultSourceSet faults() {
		return faults;
	}

	@Override public int size() {
		return faults.size();
		// TODO clean
//		int count = 0;
//		for (FaultSource fs : faults) {
//			count += fs.size();
//		}
//		return count;
	}

	/**
	 * Overriden to throw an {@code UnsupportedOperationException}. Cluster
	 * sources are handled differently than other source types.
	 */
	@Override public Iterator<Rupture> iterator() {
		throw new UnsupportedOperationException();
	}

	@Override public String name() {
		return faults.name();
	}

	@Override public String toString() {
		// TODO use Joiner
		// @formatter:off
		StringBuilder sb = new StringBuilder();
		sb.append("=========  Cluster Source  =========");
		sb.append(LINE_SEPARATOR.value());
		sb.append(" Cluster name: ").append(name());
		sb.append(LINE_SEPARATOR.value());
		sb.append("  ret. period: ").append(rate).append(" yrs");
		sb.append(LINE_SEPARATOR.value());
		sb.append("       weight: ").append(weight());
		sb.append(LINE_SEPARATOR.value());
		for (FaultSource fs : faults) {
			sb.append(LINE_SEPARATOR.value());
			sb.append("        Fault: ").append(fs.name());
			sb.append(LINE_SEPARATOR.value());
			List<Double> mags = Lists.newArrayList();
			List<Double> wts = Lists.newArrayList();
			for (IncrementalMfd mfd : fs.mfds) {
				mags.add(mfd.getX(0));
				wts.add(mfd.getY(0) * rate);
			}
			sb.append("         mags: ").append(mags);
			sb.append(LINE_SEPARATOR.value());
			sb.append("      weights: ").append(wts);
			sb.append(LINE_SEPARATOR.value());
			sb.append("          dip: ").append(fs.dip);
			sb.append(LINE_SEPARATOR.value());
			sb.append("        width: ").append(fs.width);
			sb.append(LINE_SEPARATOR.value());
			sb.append("         rake: ").append(fs.rake);
			sb.append(LINE_SEPARATOR.value());
			sb.append("          top: ").append(fs.trace.first().depth());
			sb.append(LINE_SEPARATOR.value());
		}
		// @formatter:on
		return sb.toString();
	}

	static class Builder {

		// build() may only be called once
		// use Doubles to ensure fields are initially null

		static final String ID = "ClusterSource.Builder";
		boolean built = false;

//		String name;
//		Double weight;
		Double rate;
		FaultSourceSet faults;

//		Builder name(String name) {
//			this.name = validateName(name);
//			return this;
//		}
//
//		Builder weight(double weight) {
//			this.weight = validateWeight(weight);
//			return this;
//		}

		Builder rate(double rate) {
			// TODO what sort of value checking should be done for rate (<1 ??)
			this.rate = rate;
			return this;
		}

		Builder faults(FaultSourceSet faults) {
			checkState(checkNotNull(faults, "Fault source set is null").size() > 0,
				"Fault source set is empty");
			this.faults = faults;
			return this;
		}

		void validateState(String mssgID) {
			checkState(!built, "This %s instance as already been used", mssgID);
//			checkState(name != null, "%s name not set", mssgID);
//			checkState(weight != null, "%s weight not set", mssgID);
			checkState(rate != null, "%s rate not set", mssgID);
			checkState(faults != null, "%s has no fault sources", mssgID);
			built = true;
		}

		ClusterSource buildClusterSource() {
			validateState(ID);
			return new ClusterSource(rate, faults);
		}
	}

}
