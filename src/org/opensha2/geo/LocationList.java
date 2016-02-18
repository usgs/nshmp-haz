package org.opensha2.geo;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.opensha2.util.TextUtils.NEWLINE;

import java.util.Iterator;

import org.opensha2.util.Parsing;
import org.opensha2.util.Parsing.Delimiter;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.primitives.Doubles;

/**
 * An ordered collection of {@link Location}s.
 * 
 * <p>A {@code LocationList} must contain at least 1 {@code Location} and does
 * not permit any location to be {@code null}. {@code LocationList} instances
 * are immutable and calls to {@code remove()} when iterating will throw an
 * {@code UnsupportedOperationException}.
 * 
 * <p>Consider using a {@link LocationList.Builder} (via a call to
 * {@link #builder()}) if list is being compiled from numerous {@code Location}s
 * that are not known in advance.
 * 
 * @author Peter Powers
 */
public abstract class LocationList implements Iterable<Location> {

	/**
	 * Create a new {@code LocationList} containing all {@code Location}s in
	 * {@code locs}.
	 * 
	 * @param locs to populate list with
	 * @throws IllegalArgumentException if {@code locs} is empty
	 */
	public static LocationList create(Location... locs) {
		checkArgument(checkNotNull(locs).length > 0);
		return builder().add(locs).build();
	}

	/**
	 * Create a new {@code LocationList} containing all {@code Location}s in
	 * {@code locs}.
	 * 
	 * @param locs to populate list with
	 * @throws IllegalArgumentException if {@code locs} is empty
	 */
	public static LocationList create(Iterable<Location> locs) {
		checkArgument(checkNotNull(locs).iterator().hasNext());
		return builder().addAll(locs).build();
	}

	/**
	 * Create a new {@code LocationList} by resampling the supplied list with
	 * the desired maximum spacing. The actual spacing of the returned list will
	 * likely differ, as spacing is adjusted down to maintain uniform divisions.
	 * The original vertices are also not preserved such that some corners might
	 * be adversely clipped if {@code spacing} is too large. Buyer beware.
	 * 
	 * <p>If a singleton list is supplied, it is immediately returned.
	 * 
	 * @param locs to resample
	 * @param spacing resample interval
	 * @return a new {@code LocationList}
	 */
	public static LocationList resample(LocationList locs, double spacing) {
		if (checkNotNull(locs).size() == 1) return locs;
		checkArgument(
			Doubles.isFinite(spacing) && spacing > 0.0,
			"Spacing must be positive, real number");
		return resampled(locs, spacing);
	}

	/*
	 * Actual spacing is computed using ceil() and is consistent current OpenSHA
	 * practice when building gridded surfaces. This effectively sets the target
	 * spacing as a maximum value. TODO Should consider using rint() which will
	 * generally keep the actual spacing closer to the target spacing, albeit
	 * sometimes larger.
	 */
	private static LocationList resampled(LocationList locs, double spacing) {
		double length = locs.length();
		spacing = length / Math.ceil(length / spacing);
		LocationList.Builder builder = LocationList.builder();
		Location start = locs.first();
		builder.add(start);
		double walker = spacing;
		for (Location loc : Iterables.skip(locs, 1)) {
			LocationVector v = LocationVector.create(start, loc);
			double distance = Locations.horzDistanceFast(start, loc);
			while (walker < distance) {
				builder.add(Locations.location(start, v.azimuth(), walker));
				walker += spacing;
			}
			start = loc;
			walker -= distance;
		}
		if (walker < spacing / 2.0) builder.add(locs.last());
		return builder.build();
	}

	/**
	 * Create a new {@code LocationList} from the supplied {@code String}. This
	 * method assumes that {@code s} is formatted in space-delimited xyz tuples,
	 * each of which are comma-delimited with no spaces (e.g. KML style).
	 * 
	 * @param s {@code String} to read
	 * @return a new {@code LocationList}
	 */
	public static LocationList fromString(String s) {
		return create(Iterables.transform(
			Parsing.split(checkNotNull(s), Delimiter.SPACE),
			Location.fromStringFunction()));
	}

	/**
	 * Return the number of locations in this list.
	 */
	public abstract int size();

	/**
	 * Return the location at {@code index}.
	 * 
	 * @param index of {@code Location} to return
	 * @throws IndexOutOfBoundsException if the index is out of range (
	 *         {@code index < 0 || index >= size()})
	 */
	public abstract Location get(int index);

	/**
	 * Return the first {@code Location} in this list.
	 */
	public Location first() {
		return get(0);
	}

	/**
	 * Return the last {@code Location} in this list.
	 */
	public Location last() {
		return get(size() - 1);
	}

	/**
	 * Return a view of this list in reverse order.
	 */
	public abstract LocationList reverse();

	/**
	 * Lazily compute the horizontal length of this {@code LocationList} in km.
	 * Method uses the {@link Locations#horzDistanceFast(Location, Location)}
	 * algorithm and ignores depth variation between locations. That is, it
	 * computes length as though all locations in the list have a depth of 0.0
	 * km. Repeat calls to this method will recalculate the length each time.
	 * 
	 * @return the length of the line connecting all {@code Location}s in this
	 *         list, ignoring depth variations, or 0.0 if list only contains 1
	 *         location
	 */
	public double length() {
		if (size() == 1) return 0.0;
		double sum = 0.0;
		Location prev = first();
		for (Location loc : Iterables.skip(this, 1)) {
			sum += Locations.horzDistanceFast(prev, loc);
			prev = loc;
		}
		return sum;
	}

	/**
	 * Lazily computes the average depth of the {@code Location}s in this list.
	 */
	public double depth() {
		double depth = 0.0;
		for (Location loc : this) {
			depth += loc.depth();
		}
		return depth / size();
	}

	@Override public String toString() {
		return NEWLINE + Joiner.on(NEWLINE).join(this) + NEWLINE;
	}

	/*
	 * The default implementation that delegates to an ImmutableList.
	 */
	private static class RegularLocationList extends LocationList {

		final ImmutableList<Location> locs;

		private RegularLocationList(ImmutableList<Location> locs) {
			this.locs = locs;
		}

		@Override public int size() {
			return locs.size();
		}

		@Override public Location get(int index) {
			return locs.get(index);
		}

		@Override public Iterator<Location> iterator() {
			return locs.iterator();
		}

		@Override public LocationList reverse() {
			return new RegularLocationList(locs.reverse());
		}

		@Override public int hashCode() {
			return locs.hashCode();
		}

		@Override public boolean equals(Object obj) {
			return locs.equals(obj);
		}
	}

	/**
	 * Return a new builder.
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * A reusable builder of {@code LocationList}s. Repeat calls to
	 * {@code build()} will return multiple lists in series with each new list
	 * containing all the {@code Location}s of the one before it.
	 */
	public static class Builder {

		ImmutableList.Builder<Location> builder;

		private Builder() {
			builder = ImmutableList.builder();
		}

		/**
		 * Adds a {@code Location} to the {@code LocationList}.
		 * 
		 * @param loc to add
		 * @return a reference to this {@code Builder}
		 */
		public Builder add(Location loc) {
			builder.add(checkNotNull(loc));
			return this;
		}

		/**
		 * Adds a new {@code Location} specified by the supplied latitude and
		 * longitude and a depth of 0 km to the {@code LocationList}.
		 * 
		 * @param lat latitude in decimal degrees
		 * @param lon longitude in decimal degrees
		 * @return a reference to this {@code Builder}
		 * @throws IllegalArgumentException if any values are out of range
		 * @see GeoTools
		 */
		public Builder add(double lat, double lon) {
			builder.add(Location.create(lat, lon));
			return this;
		}

		/**
		 * Adds a new {@code Location} specified by the supplied latitude,
		 * longitude, and depth to the {@code LocationList}.
		 * 
		 * @param lat latitude in decimal degrees
		 * @param lon longitude in decimal degrees
		 * @param depth in km (positive down)
		 * @return a reference to this {@code Builder}
		 * @throws IllegalArgumentException if any values are out of range
		 * @see GeoTools
		 */
		public Builder add(double lat, double lon, double depth) {
			builder.add(Location.create(lat, lon, depth));
			return this;
		}

		/**
		 * Adds each {@code Location} in {@code locs} to the
		 * {@code LocationList}.
		 * 
		 * @param locs to add
		 * @return a reference to this {@code Builder}
		 */
		public Builder add(Location... locs) {
			builder.add(locs);
			return this;
		}

		/**
		 * Adds each {@code Location} in {@code locs} to the
		 * {@code LocationList}.
		 * 
		 * @param locs to add
		 * @return a reference to this {@code Builder}
		 */
		public Builder addAll(Iterable<Location> locs) {
			builder.addAll(locs);
			return this;
		}

		/**
		 * Returns a newly created {@code LocationList}.
		 * 
		 * @return a new ordered collection of {@code Location}s
		 * @throws IllegalStateException if the list to be returned is empty
		 */
		public LocationList build() {
			ImmutableList<Location> locs = builder.build();
			checkState(locs.size() > 0, "LocationList is empty");
			return new RegularLocationList(locs);
		}
	}
}
