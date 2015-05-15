package org.opensha.geo;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.opensha.geo.Locations.distanceToSegmentFast;

import java.awt.geom.Path2D;
import java.util.Iterator;
import java.util.List;

import org.opensha.util.Parsing;
import org.opensha.util.Parsing.Delimiter;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

/**
 * An ordered collection of {@link Location}s.
 * 
 * <p>A {@code LocationList} must contain at least 1 {@code Location}.
 * {@code LocationList} instances are immutable and calls to {@code remove()}
 * when iterating will throw an {@code UnsupportedOperationException}. Consider
 * using a {@link LocationList.Builder} if list is being compiled from numerous
 * {@code Location}s that are not known in advance.</p>
 * 
 * @author Peter Powers
 */
public final class LocationList implements Iterable<Location> {

	// TODO should LocationLists really provide the facility to be used as keys
	// TODO remove reliance on AWT classes; remove Path method elsewhere?
	// TODO track down awkward uses of create() and replace with Builder

	private static final String LF = System.getProperty("line.separator");
	private static final Joiner JOIN = Joiner.on(LF);

	// package private for internal access only
	List<Location> locs = Lists.newArrayList();

	private LocationList() {};

	/**
	 * Creates a new {@code LocationList} from the supplied {@code Location}s.
	 * @param locs to populate list with
	 * @return a new {@code LocationList}
	 * @throws NullPointerException if {@code locs} is {@code null}
	 * @throws IllegalArgumentException if {@code locs} is empty
	 */
	public static LocationList create(Location... locs) {
		checkArgument(checkNotNull(locs).length > 0);
		LocationList list = new LocationList();
		// Locations are immutable; no need for defensive copies
		for (Location loc : locs) {
			list.locs.add(loc);
		}
		return list;
	}

	/**
	 * Creates a new {@code LocationList} from the supplied {@code Iterable}.
	 * @param locs to populate list with
	 * @return a new {@code LocationList}
	 */
	public static LocationList create(Iterable<Location> locs) {
		checkArgument(checkNotNull(locs).iterator().hasNext());
		LocationList list = new LocationList();
		// Locations are immutable; no need for defensive copies
		for (Location loc : locs) {
			list.locs.add(loc);
		}
		return list;
	}

	/**
	 * Creates a new {@code LocationList} that is an exact copy of the supplied
	 * {@code LocationList}. Because {@code Location}s and {@code LocationList}s
	 * are immutable, the returned copy references the same {@code Location}s as
	 * the supplied list.
	 * @param locs to populate list with
	 * @return a new {@code LocationList}
	 */
	public static LocationList copyOf(LocationList locs) {
		// TODO given the immutability of Location and LocationList, no one
		// should ever have need of an exact copy, right?
		LocationList copy = new LocationList();
		copy.locs = checkNotNull(locs).locs;
		return copy;
	}

	/**
	 * Creates a new {@code LocationList} that is an exact copy of the supplied
	 * {@code LocationList} but with reversed iteration order.
	 * @param locs to populate list with
	 * @return a new {@code LocationList}
	 */
	public static LocationList reverseOf(LocationList locs) {
		// uses a reversed view, doesn't actually reverse source Locations
		LocationList copy = new LocationList();
		copy.locs = Lists.reverse(locs.locs);
		return copy;
	}

	/**
	 * Creates a new {@code LocationList} by resampling the supplied list with
	 * the desired spacing. The actual spacing of the returned list will likely
	 * differ, as spacing is adjusted up or down to be closest to the desired
	 * value and maintain uniform divisions. The original vertices are also not
	 * preserved such that some corners might be adversely clipped if
	 * {@code spacing} is too large. Buyer beware.
	 * 
	 * @param locs to resample
	 * @param spacing resample interval
	 * @return a new {@code LocationList}
	 */
	public static LocationList resampledFrom(LocationList locs, double spacing) {
		double length = locs.length(); // lazily created and not cached
		spacing = length / Math.rint(length / spacing);
		LocationList.Builder builder = LocationList.builder();
		Location start = locs.first();
		builder.add(locs.first());
		double walker = spacing;
		for (Location loc : Iterables.skip(locs, 1)) {
			LocationVector v = LocationVector.create(start, loc);
			double distance = Locations.horzDistanceFast(start, loc);
			while (walker < distance) {
				builder.add(Locations.location(start, v));
				walker += spacing;
			}
			start = loc;
			walker -= distance;
		}
		if (walker < spacing / 2.0) builder.add(locs.last());
		return builder.build();
	}
	
	/**
	 * Creates a new {@code LocationList} from the supplied {@code String}. This
	 * method assumes that {@code s} is formatted in space-delimited xyz tuples,
	 * each of which are comma-delimited with no spaces (e.g. KML style).
	 * @param s {@code String} to read
	 * @return a new {@code LocationList}
	 */
	public static LocationList fromString(String s) {
		return create(Iterables.transform(
			Parsing.split(checkNotNull(s), Delimiter.SPACE),
			Location.fromStringFunction()));
	}

	/**
	 * Returns the size of this list size.
	 * @return the number of locations in the list
	 */
	public int size() {
		return locs.size();
	}

	/**
	 * Return the location at {@code index}.
	 * @param index of {@code Location} to return
	 * @return the {@code Location} at {@code index}
	 * @throws IndexOutOfBoundsException if the index is out of range (
	 *         {@code index < 0 || index >= size()})
	 */
	public Location get(int index) {
		return locs.get(index);
	}

	/**
	 * Returns the first {@code Location} in this list.
	 * @return the first {@code Location}
	 */
	public Location first() {
		return get(0);
	}

	/**
	 * Returns the last {@code Location} in this list.
	 * @return the last {@code Location}
	 */
	public Location last() {
		return get(size() - 1);
	}

	/**
	 * Returns an immutable {@code List} view of the {@code Location}s in the
	 * list that preserves iteration order.
	 * @return a {@code List} view of this {@code LocationList}
	 */
	public List<Location> asList() {
		// TODO is this really needed
		return ImmutableList.copyOf(locs);
	}

	/**
	 * Returns the length of this {@code LocationList} in km. Method uses
	 * {@link Locations#horzDistanceFast(Location, Location)} algorithm. Method is lazy and repeat
	 * calls to this method will recalculate the length each time.
	 * @return the length of a line that connects all {@code Location}s in this list
	 */
	public double length() {
		// TODO perhaps this should be sensitive to depth variations
		if (size() == 1) return 0.0;
		double sum = 0.0;
		Location prev = first();
		for (Location loc : Iterables.skip(locs, 1)) {
			sum += Locations.horzDistanceFast(prev, loc);
			prev = loc;
		}
		return sum;
	}
	
	/**
	 * Lazily returns the average depth of the {@code Location}s in this list.
	 */
	public double averageDepth() {
		double depth = 0.0;
		for (Location loc : this) {
			depth += loc.depth();
		}
		return depth / size();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (!(obj instanceof LocationList)) return false;
		LocationList ll = (LocationList) obj;
		if (size() != ll.size()) return false;
		for (int i = 0; i < size(); i++) {
			if (!(get(i).equals(ll.get(i)))) return false;
		}
		return true;
	}

	@Override
	public int hashCode() {
		return locs.hashCode();
	}

	@Override
	public String toString() {
		return LF + JOIN.join(this) + LF;
	}

	@Override
	public Iterator<Location> iterator() {
		// @formatter:off
		return new Iterator<Location>() {
			int caret = 0;
			int size = size();
			@Override public boolean hasNext() { return caret < size; }
			@Override public Location next() { return locs.get(caret++); }
			@Override public void remove() {
				throw new UnsupportedOperationException();
			}
		};
		// @formatter:on
	}

	/**
	 * Returns a closed, stright-line {@link Path2D} representation of this list
	 * @return a path representation of {@code this}
	 */
	public Path2D toPath() {
		Path2D path = new Path2D.Double(Path2D.WIND_EVEN_ODD, size());
		boolean starting = true;
		for (Location loc : this) {
			double lat = loc.lat();
			double lon = loc.lon();
			// if just starting, then moveTo
			if (starting) {
				path.moveTo(lon, lat);
				starting = false;
				continue;
			}
			path.lineTo(lon, lat);
		}
		path.closePath();
		return path;
	}

	/**
	 * Returns a new {@code LocationList.Builder}.
	 * @return a new builder
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * A builder for creating {@code LocationList}s. Note that builders are not
	 * reusable and subsequent calls to {@code build()} will throw an
	 * {@code IllegalStateException}. Use {@link LocationList#builder()} to
	 * create new {@code Builder} instances.
	 */
	public static class Builder {

		List<Location> locs;

		private Builder() {
			locs = Lists.newArrayList();
		}

		/**
		 * Adds the supplied {@code Location}.
		 * @param loc to add
		 * @return a reference to this {@code Builder}
		 */
		public Builder add(Location loc) {
			locs.add(checkNotNull(loc));
			return this;
		}

		/**
		 * Adds a new {@code Location} specified by the supplied latitude and
		 * longitude and a depth of 0 km.
		 * @param lat latitude in decimal degrees
		 * @param lon longitude in decimal degrees
		 * @return a new {@code Location}
		 * @throws IllegalArgumentException if any supplied values are out of
		 *         range
		 * @see GeoTools
		 */
		public Builder add(double lat, double lon) {
			locs.add(Location.create(lat, lon));
			return this;
		}

		/**
		 * Adds a new {@code Location} specified by the supplied latitude,
		 * longitude, and depth.
		 * @param lat latitude in decimal degrees
		 * @param lon longitude in decimal degrees
		 * @param depth in km (positive down)
		 * @return a new {@code Location}
		 * @throws IllegalArgumentException if any supplied values are out of
		 *         range
		 * @see GeoTools
		 */
		public Builder add(double lat, double lon, double depth) {
			locs.add(Location.create(lat, lon, depth));
			return this;
		}
		
		/**
		 * Adds the supplied {@code LocationList}.
		 * @param locs to add
		 * @return a reference to this {@code Builder}
		 */
		public Builder add(LocationList locs) {
			this.locs.addAll(checkNotNull(locs).locs);
			return this;
		}

		/**
		 * Returns a newly created {@code LocationList}.
		 * @return a new LocationList
		 * @throws IllegalStateException if {@code build()} has already been
		 *         called on this {@code Builder} or no {@code Location}s were
		 *         ever added subsequent to creating this {@code Builder}.
		 */
		public LocationList build() {
			checkState(locs != null, "build() has already been called");
			checkState(locs.size() > 0, "Builder is empty");
			LocationList list = new LocationList();
			list.locs = locs;
			return list;
		}
	}

	// TODO move these to Locations (formerly LocationUtils)
	/**
	 * Computes the horizontal surface distance (in km) to the closest point in
	 * this list from the supplied {@code Location}. This method uses
	 * {@link Locations#horzDistanceFast(Location, Location)} to compute the
	 * distance.
	 * 
	 * @param loc {@code Location} of interest
	 * @return the distance to the closest point in this {@code LocationList}
	 * @see Locations#horzDistanceFast(Location, Location)
	 */
	public double minDistToLocation(Location loc) {
		double min = Double.MAX_VALUE;
		double dist = 0;
		for (Location p : this) {
			dist = Locations.horzDistanceFast(loc, p);
			if (dist < min) min = dist;
		}
		return min;
	}

	/**
	 * Computes the shortest horizontal distance (in km) from the supplied
	 * {@code Location} to the line defined by connecting the points in this
	 * {@code LocationList}. This method uses
	 * {@link Locations#distanceToSegmentFast(Location, Location, Location)} and
	 * is inappropriate for for use at large separations (e.g. >200 km).
	 * 
	 * @param loc {@code Location} of interest
	 * @return the shortest distance to the line defined by this
	 *         {@code LocationList}
	 */
	public double minDistToLine(Location loc) {
		double min = Double.MAX_VALUE;
		for (int i = 0; i < size() - 1; i++) {
			min = Math.min(min, distanceToSegmentFast(get(i), get(i + 1), loc));
		}
		return min;		
	}

	/**
	 * Computes the segment index that is closest to the supplied
	 * {@code Location}. There are {@code LocationList.size() - 1} segment
	 * indices. The endpoints of the returned segment index are {@code [n, n+1]}.
	 * 
	 * @param loc {@code Location} of interest
	 * @return the index of the closest segment
	 */
	public int minDistIndex(Location loc) {
		double min = Double.MAX_VALUE;
		int minIdx = -1;
		for (int i = 0; i < size() - 1; i++) {
			double dist = distanceToSegmentFast(get(i), get(i + 1), loc);
			if (dist < min) {
				min = dist;
				minIdx = i;
			}
		}
		return minIdx;
	}

}
