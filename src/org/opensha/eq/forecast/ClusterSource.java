package org.opensha.eq.forecast;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.StandardSystemProperty.LINE_SEPARATOR;
import static org.opensha.data.DataUtils.validateWeight;
import static org.opensha.util.TextUtils.validateName;

import java.util.Iterator;
import java.util.List;

import org.opensha.geo.Location;
import org.opensha.mfd.IncrementalMfd;

import com.google.common.collect.Lists;

/**
 * Cluster source representation. This class wraps multiple {@code FaultSource}s
 * that occur as independent event but with a similar rate. Cluster sources are
 * calculated using the joint probabilities of ground motions from the wrapped
 * faults. They are handled internally by a separate calculator and
 * {@link ClusterSource#iterator()} and {@link ClusterSource#getRupture(int)}
 * therefore throw an {@code UnsupportedOperationException}.
 * 
 * This class wraps a model of a fault geometry and a list of magnitude
 * frequency distributions that characterize how the fault might rupture (e.g.
 * as one, single geometry-filling event, or as multiple smaller events) during
 * earthquakes. Smaller events are modeled as 'floating' ruptures; they occur in
 * multiple locations on the fault surface with appropriately scaled rates.
 * 
 * <p>A {@code ClusterSource} can not be created directly; it may only be
 * created by a private parser.</p>
 * 
 * @author Peter Powers
 */
public class ClusterSource implements Source {

	// TODO check how different fault models are being handled in each
	// ClusterSource
	// wrt to distance cutoffs

	private final String name;
	final double weight;
	final double rate; // this is in the default mfd xml
	final List<FaultSource> faults;

	ClusterSource(String name, double weight, double rate, List<FaultSource> faults) {
		this.name = name;
		this.weight = weight;
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
		return weight;
	}

	/**
	 * Return all the faults that participate in this cluster.
	 * @return a list of all {@code FaultSource}s
	 */
	public List<FaultSource> faults() {
		return faults;
	}

	@Override public double getMinDistance(Location loc) {
		double d = Double.MAX_VALUE;
		for (FaultSource fs : faults) {
			d = Math.min(d, fs.getMinDistance(loc));
		}
		return d;
	}

	@Override public int size() {
		int count = 0;
		for (FaultSource fs : faults) {
			count += fs.size();
		}
		return count;
	}

	/**
	 * Overriden to throw an {@code UnsupportedOperationException}. Cluster
	 * sources are handled differently than other source types.
	 */
	@Override public Rupture getRupture(int idx) {
		throw new UnsupportedOperationException();
	}

	/**
	 * Overriden to throw an {@code UnsupportedOperationException}. Cluster
	 * sources are handled differently than other source types.
	 */
	@Override public Iterator<Rupture> iterator() {
		throw new UnsupportedOperationException();
	}

	@Override public String name() {
		return name;
	}

	@Override public String toString() {
		// TODO use Joiner
		// @formatter:off
		StringBuilder sb = new StringBuilder();
		sb.append("=========  Cluster Source  =========");
		sb.append(LINE_SEPARATOR.value());
		sb.append(" Cluster name: ").append(name);
		sb.append(LINE_SEPARATOR.value());
		sb.append("  ret. period: ").append(rate).append(" yrs");
		sb.append(LINE_SEPARATOR.value());
		sb.append("       weight: ").append(weight);
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

		String name;
		Double weight;
		Double rate;
		List<FaultSource> faults = Lists.newArrayList();

		Builder name(String name) {
			this.name = validateName(name);
			return this;
		}

		Builder weight(double weight) {
			this.weight = validateWeight(weight);
			return this;
		}

		Builder rate(double rate) {
			// TODO what sort of value checking should be done for rate (<1 ??)
			this.rate = rate;
			return this;
		}

		Builder fault(FaultSource fault) {
			faults.add(checkNotNull(fault, "Fault is null"));
			return this;
		}

		void validateState(String mssgID) {
			checkState(!built, "This %s instance as already been used", mssgID);
			checkState(name != null, "%s name not set", mssgID);
			checkState(weight != null, "%s weight not set", mssgID);
			checkState(rate != null, "%s rate not set", mssgID);
			checkState(faults.size() > 0, "%s has no fault sources", mssgID);
			built = true;
		}

		ClusterSource buildClusterSource() {
			validateState(ID);
			return new ClusterSource(name, weight, rate, faults);
		}
	}

}
