package org.opensha2.eq.model;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.opensha2.data.Data.checkWeight;
import static org.opensha2.util.TextUtils.validateName;

import java.util.Iterator;
import java.util.List;

import org.opensha2.geo.Location;
import org.opensha2.geo.Locations;

import com.google.common.base.Predicate;
import com.google.common.collect.Lists;

/**
 * A container class for related {@link AreaSource}s.
 * 
 * @author Peter Powers
 */
public class AreaSourceSet extends AbstractSourceSet<AreaSource> {

	final private List<AreaSource> sources;

	private AreaSourceSet(
			String name,
			int id,
			double weight,
			List<AreaSource> sources,
			GmmSet gmmSet) {

		super(name, id, weight, gmmSet);
		this.sources = sources;
	}

	@Override public Iterator<AreaSource> iterator() {
		return sources.iterator();
	}

	@Override public int size() {
		return sources.size();
	}

	@Override public SourceType type() {
		return SourceType.AREA;
	}

	@Override public Predicate<AreaSource> distanceFilter(final Location loc, final double distance) {
		return new Predicate<AreaSource>() {
			@Override public boolean apply(AreaSource source) {
				return Locations.minDistanceToLocations(loc, source.border()) <= distance;
			}
		};
	}

	/* Single use builder. */
	static class Builder {

		private static final String ID = "AreaSourceSet.Builder";
		private boolean built = false;

		String name;
		Integer id;
		Double weight;
		GmmSet gmmSet;
		List<AreaSource> sources = Lists.newArrayList();

		Builder name(String name) {
			this.name = validateName(name);
			return this;
		}

		Builder id(int id) {
			this.id = id;
			return this;
		}

		Builder weight(double weight) {
			this.weight = checkWeight(weight);
			return this;
		}

		Builder gmms(GmmSet gmmSet) {
			this.gmmSet = checkNotNull(gmmSet);
			return this;
		}

		Builder source(AreaSource source) {
			sources.add(checkNotNull(source, "AreaSource is null"));
			return this;
		}

		void validateState(String buildId) {
			checkState(!built, "This %s instance as already been used", buildId);
			checkState(name != null, "%s name not set", buildId);
			checkState(id != null, "%s id not set", buildId);
			checkState(weight != null, "%s weight not set", buildId);
			checkState(gmmSet != null, "%s ground motion models not set", buildId);
			built = true;
		}

		AreaSourceSet build() {
			validateState(ID);
			return new AreaSourceSet(name, id, weight, sources, gmmSet);
		}
	}

}
