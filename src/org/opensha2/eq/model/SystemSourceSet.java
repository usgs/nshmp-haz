package org.opensha2.eq.model;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static java.lang.Math.min;

import static org.opensha2.eq.Magnitudes.checkMagnitude;
import static org.opensha2.eq.fault.Faults.validateDepth;
import static org.opensha2.eq.fault.Faults.validateDip;
import static org.opensha2.eq.fault.Faults.validateRake;
import static org.opensha2.eq.fault.Faults.validateWidth;
import static org.opensha2.eq.model.SourceType.SYSTEM;
import static org.opensha2.geo.Locations.horzDistanceFast;

import org.opensha2.calc.HazardInput;
import org.opensha2.calc.InputList;
import org.opensha2.calc.Site;
import org.opensha2.calc.SystemInputList;
import org.opensha2.data.Data;
import org.opensha2.eq.fault.Faults;
import org.opensha2.eq.fault.surface.GriddedSurface;
import org.opensha2.geo.Location;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Ordering;
import com.google.common.primitives.Doubles;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Wrapper class for related {@link SystemSource}s.
 * 
 * @author Peter Powers
 */
public final class SystemSourceSet extends AbstractSourceSet<SystemSourceSet.SystemSource> {

  private final GriddedSurface[] sections;
  private final String[] sectionNames;
  private final BitSet[] bitsets;
  private final double[] mags;
  private final double[] rates;
  private final double[] depths;
  private final double[] dips;
  private final double[] widths;
  private final double[] rakes;

  private final Statistics stats;

  /*
   * TODO revisit the fact that BitSets are mutable and could potentially be
   * altered via a SystemSource.
   * 
   * TODO don't like the fact that original trace data for sections is lost;
   * same for other attributes
   */

  private SystemSourceSet(
      String name, int id, double weight,
      GmmSet gmmSet,
      GriddedSurface[] sections,
      String[] sectionNames,
      BitSet[] bitsets,
      double[] mags,
      double[] rates,
      double[] depths,
      double[] dips,
      double[] widths,
      double[] rakes,
      Statistics stats) {

    super(name, id, weight, gmmSet);

    this.sections = sections;
    this.sectionNames = sectionNames;
    this.bitsets = bitsets;
    this.mags = mags;
    this.rates = rates;
    this.depths = depths;
    this.dips = dips;
    this.widths = widths;
    this.rakes = rakes;

    this.stats = stats;
  }

  @Override
  public SourceType type() {
    return SYSTEM;
  }

  @Override
  public int size() {
    return bitsets.length;
  }

  @Override
  public Iterator<SystemSource> iterator() {
    return new Iterator<SystemSource>() {
      int caret = 0;

      @Override
      public boolean hasNext() {
        return caret < size();
      }

      @Override
      public SystemSource next() {
        return new SystemSource(caret++);
      }

      @Override
      public void remove() {
        throw new UnsupportedOperationException();
      }
    };
  }

  @Override
  public Predicate<SystemSource> distanceFilter(Location loc, double distance) {
    BitSet siteBitset = bitsetForLocation(loc, distance);
    return new BitsetFilter(siteBitset);
  }

  /**
   * The fault section surface corresponding to the supplied {@code index}.
   * 
   * <p>This method exists because system source sets are complex and commonly
   * encapsulate 100K+ sources. The results of a hazard calculation and
   * deaggregation are therefore better represented in the context of individual
   * fault sections, rather than on a per-source basis.
   *
   * @param index of fault section surface to retrieve
   */
  public GriddedSurface section(int index) {
    return sections[index];
  }

  /**
   * The name of the fault section corresponding to the supplied {@code index}.
   * 
   * @param index of fault section name to retrieve
   */
  public String sectionName(int index) {
    return sectionNames[index];
  }

  /**
   * A single source in a fault system. These sources do not currently support
   * rupture iteration.
   *
   * <p>We skip the notion of a {@code Rupture} for now. Aleatory uncertainty on
   * magnitude isn't required, but if it is, we'll alter this implementation to
   * return List<GmmInput> per source rather than one GmmInput.
   */
  public final class SystemSource implements Source {

    private final int index;

    private SystemSource(final int index) {
      this.index = index;
    }

    @Override
    public String name() {
      // TODO How to create name? SourceSet will need parent section names
      return "Unnamed fault system source";
    }

    @Override
    public int size() {
      return 1;
    }

    @Override
    public SourceType type() {
      return SourceType.SYSTEM;
    }

    /**
     * This method is not required for deaggregation and currently throws an
     * {@code UnsupportedOperationException}.
     */
    @Override
    public Location location(Location location) {
      // TODO for consistency, should we return something here?
      throw new UnsupportedOperationException();
    }

    @Override
    public Iterator<Rupture> iterator() {
      /*
       * Rupture iterator not currently supported but may be in future if
       * aleatory uncertainty is added to or required on source magnitudes.
       * Currently system source:rupture is 1:1.
       */
      throw new UnsupportedOperationException();
    }

    private final BitSet bitset() {
      return bitsets[index];
    }

    private final double magnitude() {
      return mags[index];
    }

    private final double rate() {
      return rates[index];
    }

    private final double depth() {
      return depths[index];
    }

    private final double dip() {
      return dips[index];
    }

    private final double width() {
      return widths[index];
    }

    private final double rake() {
      return rakes[index];
    }
  }

  /*
   * Container of summary data for this sytem source set.
   */
  private static final class Statistics {

    /* Currently used to build section participation MFDs for deagg. */
    final double mMin;
    final double mMax;

    Statistics(
        double mMin,
        double mMax) {

      this.mMin = mMin;
      this.mMax = mMax;
    }
  }

  /*
   * Single use builder. Quirky behavior: Note that sections() must be called
   * before any calls to indices(). All indices and data fields should be
   * repeatedly called in order to ensure correctly ordered fields when
   * iterating ruptures.
   */
  static class Builder extends AbstractSourceSet.Builder {

    // Unfiltered UCERF3: FM31 = 253,706 FM32 = 305,709
    static final int RUP_SET_SIZE = 306000;

    static final String ID = "SystemSourceSet.Builder";

    private List<GriddedSurface> sections;
    private List<String> sectionNames;
    private final List<BitSet> bitsets = new ArrayList<>(RUP_SET_SIZE);
    private final List<Double> mags = new ArrayList<>(RUP_SET_SIZE);
    private final List<Double> rates = new ArrayList<>(RUP_SET_SIZE);
    private final List<Double> depths = new ArrayList<>(RUP_SET_SIZE);
    private final List<Double> dips = new ArrayList<>(RUP_SET_SIZE);
    private final List<Double> widths = new ArrayList<>(RUP_SET_SIZE);
    private final List<Double> rakes = new ArrayList<>(RUP_SET_SIZE);

    private double mMin = Double.POSITIVE_INFINITY;
    private double mMax = Double.NEGATIVE_INFINITY;

    Builder sections(List<GriddedSurface> sections) {
      checkNotNull(sections, "Section surface list is null");
      checkArgument(sections.size() > 0, "Section surface list is empty");
      this.sections = sections;
      return this;
    }
    
    Builder sectionNames(List<String> names) {
      checkNotNull(names, "Section name list is null");
      checkArgument(names.size() > 0, "Section name list is empty");
      this.sectionNames = names;
      return this;
    }

    Builder indices(List<Integer> indices) {
      checkState(sections != null, "Indices may only be set after call to sections()");
      checkNotNull(indices, "Rupture index list is null");
      // NOTE we're doublechecking a UCERF3 rule that ruptures be composed
      // of at least 2 sections; this may not be the case in the future.
      checkArgument(indices.size() > 1, "Rupture index list must contain 2 or more values");
      bitsets.add(Data.indicesToBits(indices, sections.size()));
      return this;
    }

    Builder mag(double mag) {
      mags.add(checkMagnitude(mag));
      mMin = (mag < mMin) ? mag : mMin;
      mMax = (mag > mMax) ? mag : mMax;
      return this;
    }

    Builder rate(double rate) {
      checkArgument(Doubles.isFinite(rate), "Rate is not a finite value");
      rates.add(rate);
      return this;
    }

    Builder depth(double depth) {
      depths.add(validateDepth(depth));
      return this;
    }

    Builder dip(double dip) {
      dips.add(validateDip(dip));
      return this;
    }

    Builder width(double width) {
      widths.add(validateWidth(width));
      return this;
    }

    Builder rake(double rake) {
      rakes.add(validateRake(rake));
      return this;
    }

    @Override
    void validateState(String buildId) {
      super.validateState(buildId);

      checkState(sections.size() > 0, "%s no sections added", buildId);
      checkState(bitsets.size() > 0, "%s no index lists added", buildId);
      checkState(
          sections.size() == sectionNames.size(),
          "%s section list (%s) and name list (%s) are different sizes",
          buildId, sections.size(), sectionNames.size());

      int target = bitsets.size();
      checkSize(mags.size(), target, buildId, "magnitudes");
      checkSize(rates.size(), target, buildId, "rates");
      checkSize(depths.size(), target, buildId, "depths");
      checkSize(dips.size(), target, buildId, "dips");
      checkSize(widths.size(), target, buildId, "widths");
      checkSize(rakes.size(), target, buildId, "rakes");
    }

    private static void checkSize(int size, int target, String classId, String dataId) {
      checkState(size == target, "%s too few %s [%s of %s]", classId, dataId, size, target);
    }

    SystemSourceSet build() {
      validateState(ID);
      Statistics stats = new Statistics(mMin, mMax);

      return new SystemSourceSet(
          name,
          id,
          weight,
          gmmSet,
          sections.toArray(new GriddedSurface[] {}),
          sectionNames.toArray(new String[] {}),
          bitsets.toArray(new BitSet[] {}),
          Doubles.toArray(mags),
          Doubles.toArray(rates),
          Doubles.toArray(depths),
          Doubles.toArray(dips),
          Doubles.toArray(widths),
          Doubles.toArray(rakes),
          stats);
    }
  }

  /*
   * System source calculation pipeline.
   *
   * Rather than expose highly mutable bitsets and attendant logic that is used
   * to generate HazardInputs from SystemSourceSets, we opt to locate transform
   * Functions and related classes here.
   *
   * System sources (e.g. UCERF3 ruptures) are composed of multiple small (~7km
   * long x ~15km wide) adjacent fault sections in a large and dense fault
   * network. Each source is defined by the indices of the participating fault
   * sections, magnitude, average width, average rake, and average dip, among
   * other parameters.
   *
   * In order to filter ruptures and calculate distance parameters when doing a
   * hazard calculation, one can treat each source as a finite fault. This
   * ultimately requires careful handling of site-to-source calculations when
   * trying to reduce redundant calculations because many sources share the same
   * fault sections and sources will be handled one-at-a-time, in sequence.
   *
   * Alternatively, one can approach the problem from an indexing standpoint,
   * precomuting that data which will be required, and then mining it on a
   * per-source basis, as follows:
   *
   * 1) For each source, create a BitSet with size = nSections. Set the bits for
   * each section that the source uses. [sourceBitSet]
   *
   * 2) Create another BitSet with size = nSections. Set the bits for each
   * section within the distance cutoff for a Site. Do this quickly using only
   * the centroid of each fault section. [siteBitSet]
   *
   * 3) Create and populate a Map<SectionIndex, double[rJB, rRup, rX]> of
   * distance metrics for each section in the siteBitSet. This is created
   * pre-sorted ascending on rRup (the closest sections to a site come first).
   *
   * 4) For each sourceBitSet, 'siteBitSet.intersects(sourceBitSet)' returns
   * whether a source is close enough to the site to be considered.
   *
   * 5) For each considered source, 'sourceBitSet AND siteBitSet' yields a
   * BitSet that only includes set bits with indices in the distance metric
   * table.
   *
   * 6) For each source, loop the ascending indices, checking whether the bit at
   * 'index' is set in the sources bitset. The first hit will be the closest
   * section in a source, relative to a site. (the rX value used is keyed to the
   * minimum rRup).
   *
   * 7) Build GmmInputs and proceed with hazard calculation.
   *
   * Note on the above. Although one could argue that only rRup or rJb be
   * calculated first, there are geometries for which min(rRup) != min(rJB);
   * e.g. location on hanging wall of dipping fault that abuts a vertical
   * fault... vertical might yield min(rRup) but min(rJB) would be 0 (over
   * dipping fault). While checking the bits in a source, we therefore look at
   * the three closest sections.
   */

  /*
   * Concurrency
   *
   * The use case assumed here is that 1 or 2 fault systems (e.g. UCERF3
   * branch-averaged solutions) will most commonly be run when supporting
   * web-services. We therefore compute hazard by first creating a (large) input
   * list and then distribute the more time consuming curve calculation.
   * However, if many fault systems were to be run, it probably makes sense to
   * farm each onto an independent thread.
   */

  /**
   * Return an instance of a {@code Function} that converts a
   * {@code SystemSourceSet} to a ground motion model {@code InputList}.
   *
   * @param site with which to initialize instance.
   */
  public static Function<SystemSourceSet, InputList> toInputsFunction(Site site) {
    return new ToInputs(site);
  }

  private static final class ToInputs implements Function<SystemSourceSet, InputList> {

    private final Site site;

    ToInputs(final Site site) {
      this.site = site;
    }

    @Override
    public InputList apply(final SystemSourceSet sourceSet) {
      // TODO is try-catch needed?

      try {

        /* Create Site BitSet. */
        double maxDistance = sourceSet.groundMotionModels().maxDistance();
        BitSet siteBitset = sourceSet.bitsetForLocation(site.location, maxDistance);
        if (siteBitset.isEmpty()) {
          return SystemInputList.empty(sourceSet);
        }

        /* Create and fill distance map. */
        int[] siteIndices = Data.bitsToIndices(siteBitset);
        ImmutableMap.Builder<Integer, double[]> rMapBuilder =
            ImmutableMap.<Integer, double[]> builder()
                .orderEntriesByValue(new DistanceTypeSorter(R_RUP_INDEX));
        for (int i : siteIndices) {
          Distance r = sourceSet.sections[i].distanceTo(site.location);
          rMapBuilder.put(i, new double[] { r.rJB, r.rRup, r.rX });
        }

        /* Create inputs. */
        Map<Integer, double[]> rMap = rMapBuilder.build();
        Function<SystemSource, HazardInput> inputGenerator = new InputGenerator(
            rMap,
            site);
        Predicate<SystemSource> rFilter = new BitsetFilter(siteBitset);
        Iterable<SystemSource> sources = Iterables.filter(sourceSet, rFilter);

        /* Fill input list. */
        SystemInputList inputs = new SystemInputList(sourceSet, rMap.keySet());
        for (SystemSource source : sources) {
          inputs.add(inputGenerator.apply(source));
          // for deagg
          inputs.addBitset(source.bitset());
        }

        return inputs;

      } catch (Exception e) {
        Throwables.propagate(e);
        return null;
      }
    }
  }

  private static final class DistanceTypeSorter extends Ordering<double[]> {

    final int rTypeIndex;

    DistanceTypeSorter(int rTypeIndex) {
      this.rTypeIndex = rTypeIndex;
    }

    @Override
    public int compare(double[] left, double[] right) {
      return Double.compare(left[rTypeIndex], right[rTypeIndex]);
    }
  }

  private static class BitsetFilter implements Predicate<SystemSource> {

    private static final String ID = "BitsetFilter";
    private final BitSet bitset;

    BitsetFilter(BitSet bitset) {
      this.bitset = bitset;
    }

    @Override
    public boolean apply(SystemSource source) {
      return bitset.intersects(source.bitset());
    }

    @Override
    public String toString() {
      return ID + " " + bitset;
    }
  }

  private static final int R_JB_INDEX = 0;
  private static final int R_RUP_INDEX = 1;
  private static final int R_X_INDEX = 2;

  private static final int R_HIT_LIMIT = 3;

  private static final class InputGenerator implements Function<SystemSource, HazardInput> {

    private final Map<Integer, double[]> rMap;
    private final Site site;

    InputGenerator(
        final Map<Integer, double[]> rMap,
        final Site site) {

      this.rMap = rMap;
      this.site = site;
    }

    @Override
    public HazardInput apply(SystemSource source) {

      /* Find r minima. */
      BitSet sectionBitset = source.bitset();
      double rJB = Double.MAX_VALUE;
      double rRup = Double.MAX_VALUE;
      double rX = Double.MAX_VALUE;
      int hitCount = 0;
      for (int sectionIndex : rMap.keySet()) {
        if (sectionBitset.get(sectionIndex)) {
          double[] distances = rMap.get(sectionIndex);
          rJB = min(rJB, distances[R_JB_INDEX]);
          double rRupNew = distances[R_RUP_INDEX];
          if (rRupNew < rRup) {
            rRup = rRupNew;
            rX = distances[R_X_INDEX];
          }
          if (++hitCount > R_HIT_LIMIT) {
            break;
          }
        }
      }

      double dip = source.dip();
      double width = source.width();
      double zTop = source.depth();
      double zHyp = Faults.hypocentralDepth(dip, width, zTop);

      return new HazardInput(
          source.rate(),
          source.magnitude(),
          rJB,
          rRup,
          rX,
          dip,
          width,
          zTop,
          zHyp,
          source.rake(),
          site.vs30,
          site.vsInferred,
          site.z1p0,
          site.z2p5);
    }
  }

  private final BitSet bitsetForLocation(final Location loc, final double r) {
    BitSet bits = new BitSet(sections.length);
    int count = 0;
    for (GriddedSurface surface : sections) {
      bits.set(count++, horzDistanceFast(loc, surface.centroid()) <= r);
    }
    return bits;
  }

}
