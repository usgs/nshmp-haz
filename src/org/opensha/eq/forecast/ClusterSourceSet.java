package org.opensha.eq.forecast;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.opensha.data.DataUtils.validateWeight;
import static org.opensha.util.TextUtils.validateName;

import java.util.Iterator;
import java.util.List;

import org.opensha.eq.fault.scaling.MagScalingType;
import org.opensha.geo.Location;

import com.google.common.collect.Lists;

/**
 * Wrapper class for groups of related {@code ClusterSource}s.
 * 
 * @author Peter Powers
 * @see ClusterSource
 */
public class ClusterSourceSet extends AbstractSourceSet<ClusterSource> {

	private final List<ClusterSource> sources;

	ClusterSourceSet(String name, double weight, MagScalingType msrType, List<ClusterSource> sources) {
		super(name, weight, msrType);
		this.sources = sources;
	}

	@Override public Iterable<ClusterSource> locationIterable(Location loc) {
		// TODO
		return null;
	}

	@Override public Iterator<ClusterSource> iterator() {
		return sources.iterator();
	}

	@Override public int size() {
		return sources.size();
	}

	@Override public SourceType type() {
		return SourceType.FAULT;
	}

	static class Builder {

		// build() may only be called once
		// use Double to ensure fields are initially null

		static final String ID = "ClusterSourceSet.Builder";
		boolean built = false;

		String name;
		Double weight;
		MagScalingType magScaling;
		List<ClusterSource> sources = Lists.newArrayList();

		Builder name(String name) {
			this.name = validateName(name);
			return this;
		}

		Builder weight(double weight) {
			this.weight = validateWeight(weight);
			return this;
		}

		Builder magScaling(MagScalingType magScaling) {
			this.magScaling = checkNotNull(magScaling, "");
			return this;
		}

		Builder source(ClusterSource source) {
			sources.add(checkNotNull(source, "ClusterSource is null"));
			return this;
		}

		void validateState(String mssgID) {
			checkState(!built, "This %s instance as already been used", mssgID);
			checkState(name != null, "%s name not set", mssgID);
			checkState(weight != null, "%s weight not set", mssgID);
			checkState(magScaling != null, "%s mag-scaling relation not set", mssgID);
			built = true;
		}

		ClusterSourceSet buildClusterSet() {
			validateState(ID);
			return new ClusterSourceSet(name, weight, magScaling, sources);
		}
	}

}
