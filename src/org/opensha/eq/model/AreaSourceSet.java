package org.opensha.eq.model;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.opensha.data.DataUtils.validateWeight;
import static org.opensha.util.TextUtils.validateName;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;

import org.opensha.geo.Location;

import com.google.common.base.Predicate;
import com.google.common.collect.Lists;

/**
 * A container class for related {@link AreaSource}s.
 * 
 * @author Peter Powers
 */
public class AreaSourceSet extends AbstractSourceSet<AreaSource> {

	final private List<AreaSource> sources;

	private AreaSourceSet(String name, double weight, List<AreaSource> sources, GmmSet gmmSet) {
		super(name, weight, gmmSet);
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
				return source.border().minDistToLocation(loc) <= distance;
			}
		};
	}

	/* Single use builder. */
	static class Builder {

		private static final String ID = "AreaSourceSet.Builder";
		private boolean built = false;

		String name;
		Double weight;
		GmmSet gmmSet;
		List<AreaSource> sources = Lists.newArrayList();

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

		Builder source(AreaSource source) {
			sources.add(checkNotNull(source, "AreaSource is null"));
			return this;
		}

		void validateState(String id) {
			checkState(!built, "This %s instance as already been used", id);
			checkState(name != null, "%s name not set", id);
			checkState(weight != null, "%s weight not set", id);
			checkState(gmmSet != null, "%s ground motion models not set", id);
			built = true;
		}

		AreaSourceSet build() {
			validateState(ID);
			return new AreaSourceSet(name, weight, sources, gmmSet);
		}
	}

}
