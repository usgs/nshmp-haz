package org.opensha2.geo;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import static org.opensha2.util.TextUtils.NEWLINE;

import org.opensha2.util.Parsing;
import org.opensha2.util.Parsing.Delimiter;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.primitives.Doubles;

import java.util.Iterator;
import java.util.List;

/**
 * An immutable, ordered collection of {@link Location}s.
 *
 * <p>A {@code LocationList} must contain at least 1 {@code Location} and does
 * not permit {@code null} elements. {@code LocationList} instances are
 * immutable and calls to {@code remove()} when iterating will throw an
 * {@code UnsupportedOperationException}. A variety of methods exist to simplify
 * the creation of new lists via modification (e.g. {@link #resample(double)} ,
 * {@link #reverse()}, and {@link #translate(LocationVector)}.
 *
 * <p>Consider using a {@link LocationList.Builder} (via {@link #builder()}) if
 * a list is being compiled from numerous {@code Location}s that are not known
 * in advance.
 *
 * <p><b>Note:</b> Although this class is not final, it cannot be subclassed
 * outside its package as it has no public or protected constructors. Thus,
 * instances of this type are guaranteed to be immutable.
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
    checkArgument(locs.length > 0);
    return builder().add(locs).build();
  }

  /**
   * Create a new {@code LocationList} containing all {@code Location}s in
   * {@code locs}. This method avoids copying the supplied iterable if it is
   * safe to do so.
   *
   * @param locs to populate list with
   * @throws IllegalArgumentException if {@code locs} is empty
   */
  public static LocationList create(Iterable<Location> locs) {
    checkArgument(locs.iterator().hasNext());
    if (locs instanceof LocationList) {
      return (LocationList) locs;
    }
    if (locs instanceof ImmutableList) {
      return new RegularLocationList((ImmutableList<Location>) locs);
    }
    return builder().addAll(locs).build();
  }

  /**
   * Create a new {@code LocationList} from the supplied {@code String}. This
   * method assumes that {@code s} is formatted as one or more space-delimited
   * {@code [lat,lon,depth]} tuples (comma-delimited); see
   * {@link Location#fromString(String)}.
   *
   * @param s {@code String} to read
   * @return a new {@code LocationList}
   * @see Location#fromString(String)
   */
  public static LocationList fromString(String s) {
    return create(Iterables.transform(
        Parsing.split(checkNotNull(s), Delimiter.SPACE),
        Location.stringConverter().reverse()));
  }

  LocationList() {}

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
   * Lazily compute the horizontal length of this {@code LocationList} in km.
   * Method uses the {@link Locations#horzDistanceFast(Location, Location)}
   * algorithm and ignores depth variation between locations. That is, it
   * computes length as though all locations in the list have a depth of 0.0 km.
   * Repeat calls to this method will recalculate the length each time.
   *
   * @return the length of the line connecting all {@code Location}s in this
   *         list, ignoring depth variations, or 0.0 if list only contains 1
   *         location
   */
  public double length() {
    double sum = 0.0;
    if (size() == 1) {
      return sum;
    }
    Location prev = first();
    for (Location loc : Iterables.skip(this, 1)) {
      sum += Locations.horzDistanceFast(prev, loc);
      prev = loc;
    }
    return sum;
  }

  /**
   * Lazily compute the average depth of the {@code Location}s in this list.
   */
  public double depth() {
    double depth = 0.0;
    for (Location loc : this) {
      depth += loc.depth();
    }
    return depth / size();
  }

  /**
   * Lazily compute the bounds of the {@code Location}s in this list. Method
   * delegates to {@link Locations#bounds(Iterable)}.
   */
  public Bounds bounds() {
    return Locations.bounds(this);
  }

  @Override
  public String toString() {
    return NEWLINE + Joiner.on(NEWLINE).join(this) + NEWLINE;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (!(obj instanceof LocationList)) {
      return false;
    }
    LocationList other = (LocationList) obj;
    if (this.size() != other.size()) {
      return false;
    }
    return Iterators.elementsEqual(this.iterator(), other.iterator());
  }

  @Override
  public int hashCode() {
    return Lists.newArrayList(this).hashCode();
  }

  /**
   * Return a new {@code LocationList} created by resampling this list with the
   * desired maximum spacing. The actual spacing of the returned list will
   * likely differ, as spacing is adjusted down to maintain uniform divisions.
   * The original vertices are also not preserved such that some corners might
   * be adversely clipped if {@code spacing} is large. Buyer beware.
   *
   * <p>For a singleton list, this method immediately returns this list.
   *
   * @param spacing resample interval
   */
  public LocationList resample(double spacing) {
    if (size() == 1) {
      return this;
    }
    checkArgument(
        Doubles.isFinite(spacing) && spacing > 0.0,
        "Spacing must be positive, real number");

    /*
     * TODO Consider using rint() which will keep the actual spacing closer to
     * the target spacing, albeit sometimes larger.
     */

    double length = this.length();
    spacing = length / Math.ceil(length / spacing);
    List<Location> resampled = Lists.newArrayList();
    Location start = this.first();
    resampled.add(start);
    double walker = spacing;
    for (Location loc : Iterables.skip(this, 1)) {
      LocationVector v = LocationVector.create(start, loc);
      double distance = Locations.horzDistanceFast(start, loc);
      while (walker <= distance) {
        resampled.add(Locations.location(start, v.azimuth(), walker));
        walker += spacing;
      }
      start = loc;
      walker -= distance;
    }
    // replace last point to be exact
    resampled.set(resampled.size() - 1, this.last());
    return LocationList.create(resampled);
  }

  /**
   * Return a new {@code LocationList} with {@code Location}s in reverse order.
   */
  public LocationList reverse() {
    if (this instanceof RegularLocationList) {
      return new RegularLocationList(((RegularLocationList) this).locs.reverse());
    }
    return create(ImmutableList.copyOf(this).reverse());
  }

  /**
   * Return a new {@code LocationList} with each {@code Location} translated
   * according to the supplied vector.
   *
   * @param vector to translate list by
   */
  public LocationList translate(final LocationVector vector) {
    return builder().addAll(Iterables.transform(this, new Function<Location, Location>() {
      @Override
      public Location apply(Location loc) {
        return Locations.location(loc, vector);
      }
    })).build();
  }

  /* The default implementation that delegates to an ImmutableList. */
  @VisibleForTesting
  static class RegularLocationList extends LocationList {

    final ImmutableList<Location> locs;

    private RegularLocationList(ImmutableList<Location> locs) {
      this.locs = locs;
    }

    @Override
    public int size() {
      return locs.size();
    }

    @Override
    public Location get(int index) {
      return locs.get(index);
    }

    @Override
    public Iterator<Location> iterator() {
      return locs.iterator();
    }
  }

  /**
   * Return a new {@code LocationList} builder.
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * A reusable builder of {@code LocationList}s. Repeat calls to
   * {@code build()} will return multiple lists in series with each new list
   * containing all the {@code Location}s of the one before it. Builders do not
   * permit the addition of {@code null} elements.
   *
   * <p>Use {@link LocationList#builder()} to create new builder instances.
   */
  public static final class Builder {

    @VisibleForTesting
    ImmutableList.Builder<Location> builder;

    private Builder() {
      builder = ImmutableList.builder();
    }

    /**
     * Add a {@code Location} to the {@code LocationList}.
     *
     * @param loc to add
     * @return this {@code Builder}
     */
    public Builder add(Location loc) {
      builder.add(loc);
      return this;
    }

    /**
     * Add a new {@code Location} specified by the supplied latitude and
     * longitude and a depth of 0 km to the {@code LocationList}.
     *
     * @param lat latitude in decimal degrees
     * @param lon longitude in decimal degrees
     * @return this {@code Builder}
     * @throws IllegalArgumentException if any values are out of range
     * @see GeoTools
     */
    public Builder add(double lat, double lon) {
      builder.add(Location.create(lat, lon));
      return this;
    }

    /**
     * Add a new {@code Location} specified by the supplied latitude, longitude,
     * and depth to the {@code LocationList}.
     *
     * @param lat latitude in decimal degrees
     * @param lon longitude in decimal degrees
     * @param depth in km (positive down)
     * @return this {@code Builder}
     * @throws IllegalArgumentException if any values are out of range
     * @see GeoTools
     */
    public Builder add(double lat, double lon, double depth) {
      builder.add(Location.create(lat, lon, depth));
      return this;
    }

    /**
     * Add each {@code Location} in {@code locs} to the {@code LocationList} .
     *
     * @param locs to add
     * @return this {@code Builder}
     */
    public Builder add(Location... locs) {
      builder.add(locs);
      return this;
    }

    /**
     * Add each {@code Location} in {@code locs} to the {@code LocationList} .
     *
     * @param locs to add
     * @return this {@code Builder}
     */
    public Builder addAll(Iterable<Location> locs) {
      builder.addAll(locs);
      return this;
    }

    /**
     * Return a newly created {@code LocationList}.
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
