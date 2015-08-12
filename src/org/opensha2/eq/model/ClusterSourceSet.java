package org.opensha2.eq.model;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.opensha2.data.DataUtils.validateWeight;
import static org.opensha2.util.TextUtils.validateName;

import java.util.Iterator;
import java.util.List;

import org.opensha2.eq.model.FaultSourceSet.Builder;
import org.opensha2.geo.Location;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

/**
 * Container class for related {@link ClusterSource}s.
 * 
 * @author Peter Powers
 * @see ClusterSource
 */
public class ClusterSourceSet extends AbstractSourceSet<ClusterSource> {

	private final List<ClusterSource> sources;

	ClusterSourceSet(
			String name,
			int id,
			double weight,
			List<ClusterSource> sources,
			GmmSet gmmSet) {

		super(name, id, weight, gmmSet);
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
	static class Builder extends AbstractSourceSet.Builder {

		private static final String ID = "ClusterSourceSet.Builder";

		private final List<ClusterSource> sources = Lists.newArrayList();

		Builder source(ClusterSource source) {
			sources.add(checkNotNull(source, "ClusterSource is null"));
			return this;
		}

		@Override void validateState(String buildId) {
			super.validateState(buildId);
			checkState(sources.size() > 0, "%s source list is empty", buildId);
		}

		ClusterSourceSet buildClusterSet() {
			validateState(ID);
			return new ClusterSourceSet(name, id, weight, sources, gmmSet);
		}
	}

}
