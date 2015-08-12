package org.opensha2.eq.model;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.StandardSystemProperty.LINE_SEPARATOR;

import java.util.Iterator;
import java.util.Map;

import org.opensha2.eq.model.FaultSource.Builder;
import org.opensha2.mfd.IncrementalMfd;

import com.google.common.collect.ImmutableMap;

/**
 * Cluster source representation. This class wraps a {@code FaultSourceSet} of
 * {@code FaultSource}s that occur as independent events but with a similar
 * rate. Cluster sources are calculated using the joint probabilities of ground
 * motions from the wrapped faults. They are handled internally by a separate
 * calculator and {@link ClusterSource#iterator()} therefore throws an
 * {@code UnsupportedOperationException}.
 * 
 * <p>Unlike other {@code Source}s whose weights are carried exclusively with their
 * associated {@link IncrementalMfd}, {@code ClusterSource}s carry an additional
 * {@link #weight()} value.</p>
 * 
 * <p>A {@code ClusterSource} cannot be created directly; it may only be created
 * by a private parser.</p>
 * 
 * @author Peter Powers
 */
public class ClusterSource implements Source {

	final double rate; // from the default mfd xml
	final FaultSourceSet faults;

	ClusterSource(double rate, FaultSourceSet faults) {
		this.rate = rate;
		this.faults = faults;
	}

	/**
	 * {@code (1 / return period)} of this source in years.
	 * @return the cluster rate
	 */
	public double rate() {
		return rate;
	}

	/**
	 * The weight applicable to this {@code ClusterSource}.
	 */
	public double weight() {
		return faults.weight();
	}

	/**
	 * The {@code FaultSourceSet} of all {@code FaultSource}s that participate
	 * in this cluster.
	 */
	public FaultSourceSet faults() {
		return faults;
	}

	@Override public int size() {
		return faults.size();
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
		// @formatter:off
		Map<Object, Object> data = ImmutableMap.builder()
				.put("name", name())
				.put("rate", rate())
				.put("weight", weight())
				.build();
		StringBuilder sb = new StringBuilder()
				.append(getClass().getSimpleName())
				.append(" ")
				.append(data)
				.append(LINE_SEPARATOR.value());
		for (FaultSource fs : faults) {
			sb.append("  ")
			.append(fs.toString())
			.append(LINE_SEPARATOR.value());
		}
		return sb.toString();
		// @formatter:on
	}

	/* Single use builder */
	static class Builder {

		static final String ID = "ClusterSource.Builder";
		boolean built = false;

		Double rate;
		FaultSourceSet faults;

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

		void validateState(String source) {
			checkState(!built, "This %s instance as already been used", source);
			checkState(rate != null, "%s rate not set", source);
			checkState(faults != null, "%s has no fault sources", source);
			built = true;
		}

		ClusterSource buildClusterSource() {
			validateState(ID);
			return new ClusterSource(rate, faults);
		}
	}

}
