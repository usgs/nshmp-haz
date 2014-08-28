package org.opensha.eq.forecast;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.opensha.data.DataUtils.validateWeight;
import static org.opensha.util.TextUtils.validateName;

import java.util.Iterator;
import java.util.List;

import org.opensha.eq.fault.scaling.MagScalingType;
import org.opensha.eq.forecast.FaultSourceSet.Builder;
import org.opensha.geo.Location;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

/**
 * Wrapper class for related {@link ClusterSource}s.
 * 
 * @author Peter Powers
 * @see ClusterSource
 */
public class ClusterSourceSet extends AbstractSourceSet<ClusterSource> {

	private final List<ClusterSource> sources;

	ClusterSourceSet(String name, double weight, MagScalingType msr, List<ClusterSource> sources,
		GmmSet gmmSet) {
		super(name, weight, msr, gmmSet);
		this.sources = sources;
	}

	@Override public Iterator<ClusterSource> iterator() {
		return sources.iterator();
	}

	@Override public int size() {
		return sources.size();
	}

	@Override public SourceType type() {
		return SourceType.CLUSTER;
	}

	@Override public Predicate<ClusterSource> distanceFilter(final Location loc,
			final double distance) {
		return new Predicate<ClusterSource>() {

			private final Predicate<FaultSource> filter = new FaultSourceSet.DistanceFilter(loc,
				distance);

			@Override public boolean apply(ClusterSource cs) {
				return Iterables.any(cs.faults, filter);
			}

			@Override public String toString() {
				return "ClusterSourceSet.DistanceFilter [ " + filter.toString() + " ]";
			}
		};
	}

	/* Single use builder */
	static class Builder {

		private static final String ID = "ClusterSourceSet.Builder";
		private boolean built = false;

		private String name;
		private Double weight;
		private MagScalingType magScaling;
		private GmmSet gmmSet;
		private List<ClusterSource> sources = Lists.newArrayList();

		Builder name(String name) {
			this.name = validateName(name);
			return this;
		}

		Builder weight(double weight) {
			this.weight = validateWeight(weight);
			return this;
		}

		Builder gmms(GmmSet gmmSet) {
			this.gmmSet = checkNotNull(gmmSet);
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

		void validateState(String id) {
			checkState(!built, "This %s instance as already been used", id);
			checkState(name != null, "%s name not set", id);
			checkState(weight != null, "%s weight not set", id);
			checkState(magScaling != null, "%s mag-scaling relation not set", id);
			checkState(gmmSet != null, "%s ground motion models not set", id);
			built = true;
		}

		ClusterSourceSet buildClusterSet() {
			validateState(ID);
			return new ClusterSourceSet(name, weight, magScaling, sources, gmmSet);
		}
	}

}
