package gov.usgs.earthquake.nshmp.eq.model;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static gov.usgs.earthquake.nshmp.eq.Earthquakes.checkCrustalDepth;
import static gov.usgs.earthquake.nshmp.eq.Earthquakes.checkMagnitude;
import static gov.usgs.earthquake.nshmp.eq.Earthquakes.checkSlabDepth;
import static gov.usgs.earthquake.nshmp.eq.fault.Faults.checkStrike;
import static gov.usgs.earthquake.nshmp.eq.fault.FocalMech.NORMAL;
import static gov.usgs.earthquake.nshmp.eq.fault.FocalMech.REVERSE;
import static gov.usgs.earthquake.nshmp.eq.fault.FocalMech.STRIKE_SLIP;
import static gov.usgs.earthquake.nshmp.eq.model.PointSourceType.FIXED_STRIKE;
import static gov.usgs.earthquake.nshmp.eq.model.SourceType.GRID;

import java.util.function.Function;
import java.util.function.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.primitives.Doubles;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;

import gov.usgs.earthquake.nshmp.data.Data;
import gov.usgs.earthquake.nshmp.data.IntervalTable;
import gov.usgs.earthquake.nshmp.data.XySequence;
import gov.usgs.earthquake.nshmp.eq.Earthquakes;
import gov.usgs.earthquake.nshmp.eq.fault.FocalMech;
import gov.usgs.earthquake.nshmp.eq.fault.surface.RuptureScaling;
import gov.usgs.earthquake.nshmp.eq.model.PointSource.DepthModel;
import gov.usgs.earthquake.nshmp.geo.Location;
import gov.usgs.earthquake.nshmp.geo.Locations;
import gov.usgs.earthquake.nshmp.mfd.IncrementalMfd;

/**
 * A container class for related, evenly-spaced {@link PointSource}s with
 * varying magnitudes and/or rates derived from an {@link IncrementalMfd} at
 * each grid node.
 *
 * @author Peter Powers
 */
public class GridSourceSet extends AbstractSourceSet<PointSource> {

  private final List<Location> locs;
  private final List<XySequence> mfds;
  final RuptureScaling rupScaling;
  private final List<Map<FocalMech, Double>> mechMaps;
  private final boolean singularMechs;
  final DepthModel depthModel; // package exposure for parser logging
  private final double strike;
  private final PointSourceType sourceType;

  final boolean optimizable;
  final double[] magMaster;
  final double Δm;

  /*
   * Most grid sources have the same focal mech map everywhere; in these cases,
   * mechMaps will have been created using Collections.nCopies() with minimal
   * overhead.
   */

  private GridSourceSet(
      String name,
      int id,
      Double weight,
      GmmSet gmmSet,
      List<Location> locs,
      List<XySequence> mfds,
      List<Map<FocalMech, Double>> mechMaps,
      boolean singularMechs,
      NavigableMap<Double, Map<Double, Double>> magDepthMap,
      double maxDepth,
      double strike,
      RuptureScaling rupScaling,
      PointSourceType sourceType,
      double[] magMaster,
      double Δm) {

    super(name, id, weight, gmmSet);
    this.locs = locs;
    this.mfds = mfds;
    this.mechMaps = mechMaps;
    this.singularMechs = singularMechs;
    this.strike = strike;
    this.rupScaling = rupScaling;
    this.sourceType = sourceType;

    this.magMaster = magMaster;
    this.Δm = Δm;
    this.optimizable = !Double.isNaN(Δm);

    depthModel = DepthModel.create(magDepthMap, Doubles.asList(magMaster), maxDepth);
  }

  @Override
  public SourceType type() {
    return GRID;
  }

  /**
   * The {@link PointSource} representation used by this source set.
   */
  public PointSourceType sourceType() {
    return sourceType;
  }

  /**
   * Whether this source set is capable of being optimized. There are
   * circumstances under which a grid optimization table can not be built.
   */
  public boolean optimizable() {
    return optimizable;
  }

  @Override
  public int size() {
    return locs.size();
  }

  /**
   * For internal use only. Public for access outside of package.
   */
  public static String sizeString(SourceSet<? extends Source> sourceSet, int size) {
    if (sourceSet instanceof Table) {
      Table t = (Table) sourceSet;
      return t.parentCount() + " (" + t.rowCount + " of " + t.maximumSize + ")";
    }
    return Integer.toString(size);
  }

  @Override
  public Predicate<PointSource> distanceFilter(final Location loc, final double distance) {
    return new DistanceFilter(loc, distance);
  }

  /* Not inlined for use by area sources */
  static final class DistanceFilter implements Predicate<PointSource> {
    private final Predicate<Location> filter;

    DistanceFilter(Location loc, double distance) {
      filter = Locations.distanceAndRectangleFilter(loc, distance);
    }

    @Override
    public boolean test(PointSource source) {
      return filter.test(source.loc);
    }

    @Override
    public String toString() {
      return "GridSourceSet.DistanceFilter[ " + filter.toString() + " ]";
    }
  }

  @Override
  public Iterator<PointSource> iterator() {
    return new Iterator<PointSource>() {
      int caret = 0;

      @Override
      public boolean hasNext() {
        return caret < locs.size();
      }

      @Override
      public PointSource next() {
        return getSource(caret++);
      }

      @Override
      public void remove() {
        throw new UnsupportedOperationException();
      }
    };
  }

  private PointSource getSource(int index) {

    /*
     * TODO Stricter rules regarding what sorts of default mfds can be used with
     * grid sources (in an individual grid source set) will allow parsers to
     * create XySequence based MFDs directly using copyOf so as to not create
     * zillions of mag arrays.
     */
    Location loc = locs.get(index);
    XySequence mfd = mfds.get(index);
    Map<FocalMech, Double> mechMap = mechMaps.get(index);

    switch (sourceType) {
      case POINT:
        return new PointSource(GRID, loc, mfd, mechMap, rupScaling, depthModel);

      case FINITE:
        return new PointSourceFinite(GRID, loc, mfd, mechMap, rupScaling, depthModel);

      case FIXED_STRIKE:
        return new PointSourceFixedStrike(GRID, loc, mfd, mechMap, rupScaling, depthModel, strike);

      default:
        throw new IllegalStateException("Unhandled point source type");
    }
  }

  // Builder accomodates overriding a default mechMap to support UC3
  // grid sources; may add others later TODO document

  /* Single use builder. */
  static class Builder extends AbstractSourceSet.Builder {

    private static final String ID = "GridSourceSet.Builder";

    private Double strike;
    private PointSourceType sourceType;
    private RuptureScaling rupScaling;
    private NavigableMap<Double, Map<Double, Double>> magDepthMap;
    private Double maxDepth;
    private Map<FocalMech, Double> mechMap;

    private List<Location> locs = Lists.newArrayList();
    private List<XySequence> mfds = Lists.newArrayList();
    private List<Map<FocalMech, Double>> mechMaps = Lists.newArrayList();
    private boolean singularMechs = true;

    private double[] magMaster;
    private Double mMin;
    private Double mMax;
    private Double Δm;

    Builder strike(double strike) {
      // unknown strike allowed for grid sources
      this.strike = Double.isNaN(strike) ? strike : checkStrike(strike);
      return this;
    }

    Builder sourceType(PointSourceType sourceType) {
      this.sourceType = checkNotNull(sourceType);
      return this;
    }

    Builder ruptureScaling(RuptureScaling rupScaling) {
      this.rupScaling = checkNotNull(rupScaling, "RupScaling is null");
      return this;
    }

    Builder depthMap(NavigableMap<Double, Map<Double, Double>> magDepthMap, SourceType type) {
      checkNotNull(magDepthMap, "MagDepthMap is null");
      checkArgument(magDepthMap.size() > 0, "MagDepthMap must have at least one entry");
      // the structure of the map and its weights will have been fully
      // validated by parser; still need to check that depths are
      // appropriate; 'type' indicates how to validate depths across
      // wrapper classes
      validateDepthMap(magDepthMap, type);
      // there must be at least one mag key that is >= MAX_MAG
      validateMagCutoffs(magDepthMap);
      this.magDepthMap = magDepthMap;
      return this;
    }

    Builder maxDepth(Double maxDepth, SourceType type) {
      this.maxDepth = checkNotNull(maxDepth, "Maximum depth is null");
      validateDepth(maxDepth, type);
      return this;
    }

    Builder mechs(Map<FocalMech, Double> mechMap) {
      // weights will have already been checked
      checkArgument(!checkNotNull(mechMap).isEmpty());
      checkArgument(mechMap.size() == 3);
      this.mechMap = mechMap;
      return this;
    }

    /*
     * magMaster mfd data
     *
     * we could require that this be set first and then all node mfds are
     * checked against this.
     */
    Builder mfdData(double mMin, double mMax, double Δm) {
      // TODO need better validation here
      checkArgument(checkMagnitude(mMin) <= checkMagnitude(mMax));
      this.mMin = mMin;
      this.mMax = mMax;
      this.Δm = Δm;
      return this;
    }

    Builder location(Location loc, XySequence mfd) {
      this.mfds.add(checkNotNull(mfd, "MFD is null"));
      this.locs.add(checkNotNull(loc, "Location is null"));
      return this;
    }

    Builder location(Location loc, XySequence mfd, Map<FocalMech, Double> mechMap) {
      this.mfds.add(checkNotNull(mfd, "MFD is null"));
      this.locs.add(checkNotNull(loc, "Location is null"));
      checkArgument(!checkNotNull(mechMap).isEmpty());
      checkArgument(mechMap.size() == 3);
      this.mechMaps.add(mechMap);
      return this;
    }

    static void validateDepthMap(Map<Double, Map<Double, Double>> magDepthMap,
        SourceType type) {
      for (Map<Double, Double> magMap : magDepthMap.values()) {
        for (double depth : magMap.keySet()) {
          validateDepth(depth, type);
        }
      }
    }

    static void validateDepth(double depth, SourceType type) {
      switch (type) {
        case GRID:
          checkCrustalDepth(depth);
          break;
        case SLAB:
          checkSlabDepth(depth);
          break;
        case AREA:
          checkCrustalDepth(depth);
          break;
        default:
          throw new IllegalStateException(type + " not a grid or related source type");
      }
    }

    static void validateMaxAndMapDepths(Map<Double, Map<Double, Double>> magDepthMap,
        double maxDepth, String id) {
      for (Map<Double, Double> magMap : magDepthMap.values()) {
        for (double depth : magMap.keySet()) {
          checkState(depth <= maxDepth, "%s mag-depth-weight map depth %s > %s", id,
              depth, maxDepth);
        }
      }
    }

    static void validateMagCutoffs(Map<Double, Map<Double, Double>> magDepthMap) {
      double mMax = Earthquakes.MAG_RANGE.upperEndpoint();
      for (double mag : magDepthMap.keySet()) {
        if (mag > mMax) {
          return;
        }
      }
      throw new IllegalStateException("MagDepthMap must contain at least one M > " + mMax);
    }

    @Override
    void validateState(String buildId) {
      super.validateState(buildId);
      checkState(strike != null, "%s strike not set", buildId);
      checkState(sourceType != null, "%s source type not set", buildId);
      checkState(!locs.isEmpty(), "%s has no locations", buildId);
      checkState(!mfds.isEmpty(), "%s has no Mfds", buildId);
      checkState(rupScaling != null, "%s has no rupture-scaling relation set", buildId);
      checkState(magDepthMap != null, "%s mag-depth-weight map not set", buildId);
      checkState(maxDepth != null, "%s max depth not set", buildId);
      checkState(mechMap != null, "%s focal mech map not set", buildId);

      checkState(mMin != null, "%s min mag not set", buildId);
      checkState(mMax != null, "%s max mag not set", buildId);
      checkState(Δm != null, "%s delta mag not set", buildId);

      /*
       * TODO there are too many assumptions built into this; whose to say ones
       * bin spacing should be only be in the hundredths?
       *
       * Where did this come from anyway? Are mag deltas really all that strange
       *
       * We should read precision of supplied mMin and mMax and delta and use
       * largest for formatting
       *
       * TODO in the case of single combined/flattened MFDs, mags may not be
       * uniformly spaced. Can this be refactored
       */
      if (Double.isNaN(Δm)) {
        magMaster = Doubles.toArray(mfds.get(0).xValues());
      } else {
        double cleanDelta = Double.valueOf(String.format("%.2f", Δm));
        magMaster = Data.buildCleanSequence(mMin, mMax, cleanDelta, true, 2);
      }

      /*
       * Validate size of mechMaps; size could get out of sync if mixed calls to
       * location(...) were made; one can imagine a future use case where a
       * default is required with an override in a few locations; for now, if
       * custom mechMaps are required, there must be one for each node. If no
       * custom maps supplied populate mechMaps with nCopies (singleton list
       * with multiple elements)
       */
      if (mechMaps.isEmpty()) {
        mechMaps = Collections.nCopies(locs.size(), mechMap);
      } else {
        checkState(
            mechMaps.size() == locs.size(),
            "%s only %s of %s focal mech maps were added", ID,
            mechMaps.size(), locs.size());
        singularMechs = false;
      }

      /*
       * Validate depths. depths will already have been checked for consistency
       * with allowable depths for different source types. Must also ensure that
       * all depths (zTop) in the magDepthMap are < maxDepth.
       */
      validateMaxAndMapDepths(magDepthMap, maxDepth, ID);

      /*
       * Validate type agreement. If strike != NaN, type must be FIXED_STRIKE.
       */
      if (!Double.isNaN(strike)) {
        checkState(sourceType == FIXED_STRIKE,
            "Source type must be FIXED_STRIKE for strike [%s]", strike);
      } else {
        checkState(sourceType != FIXED_STRIKE,
            "Source type FIXED_STRIKE invalid for strike [%s]", strike);
      }
    }

    GridSourceSet build() {
      validateState(ID);
      return new GridSourceSet(
          name, id, weight,
          gmmSet, locs, mfds,
          mechMaps, singularMechs,
          magDepthMap, maxDepth,
          strike, rupScaling, sourceType,
          magMaster, Δm);
    }

  }

  /*
   * Notes on refactoring GridSourceSet xml.
   *
   * The existing implementation permits one mfd per node and that maps to the
   * default of the same 'type'. This was fine assuming mfds were collapsed
   * (CEUS 2008) or MFDs were explicitly one type or another as required for CA
   * 2008 grids (GR or INCR, the latter to handle the M>6.5 rate reduction).
   *
   * However, we want to be able to track mMax and other logic tree branches.
   *
   * Given gridded sources where rate or a-value at each node is consistent for
   * all applicable MFDs:
   *
   * - each node will map to all defaults
   *
   * - defaults must have unique 'id' (logic tree branch id), but can be of
   * similar 'type'; throw error if field doesn't match
   *
   * - CA 2008 will need to be converted to only INCR MFDs with explicit rates
   * for all nodes.
   *
   * - refactor IncrementalMfd to Mfd in XML
   *
   * - refactor Node to Source
   *
   * - usually the MFD 'type' is consistent across branches for a given grid
   * source set so collapsing MFD's isn't too problematic.
   *
   * Grid optimizations:
   *
   * - create GridSourceSet subclass, one for each default MFD (siblings)
   *
   * - also create a subclass that uses a collapsed MFD, or perhaps this is just
   * the original.
   */

  /*
   * If, for a basic HazardResult, we want to be able to give a per-source-set
   * decomposition by ground motion model, or just a decomposition of the total
   * curve, we'll need to have a table of the curves for every model.
   *
   * If not necessary, then can have table of total curves and table of mean
   * (and sigma?) for each model. Just mean is necessary for deaggeregation
   * epsilon
   *
   * OK... so...
   *
   * Preliminary implementations of grid source optimizations modeled after the
   * NSHMP Fortran codes porecomputed median curves in distance and magnitude
   * (using a weighted combination of Gmms) and then performed lookups for each
   * source, aggregating a total curve along the way. This approach is lossy in
   * that data for individual Gmms is lost, and it was never extended to support
   * deaggregation where ground motion mean and sigma are required.
   *
   * Further consideration of these issues suggests that, rather than
   * aggregating curves along the way, we should build a separate table in
   * magnitude and distance of rates while looping over sources. In the end,
   * curves could be computed once for each distance and magnitude bin. Although
   * full curves for each Gmm could be precomputed, the time to loop over the
   * rate table may not be significant enough to warrant the memory overhead
   * (bear in mind that's a lot of curves when considering large logic trees of
   * Gmms and numerous periods).
   *
   * There is also the additional issue of additional epistemic uncertinaty on
   * ground motions, which does not need to be considered here if building
   * magnitude-distance rate tables.
   *
   * There is the additional issue of different focal mechanisms. For NGAW2 and
   * the WUS, we would need to have 5 curves per gmm and r/m bin: 2 reverse, 2
   * normal 1 strike slip
   *
   * Also assumes uniform vs30 or bsain terms; these will likely be spatially
   * varying in the future; this would also be incompatible with gmm tables.
   *
   * Precomputed curves may still be warranted for map calculations where Gmm
   * specific data and deaggregation are irrelevant.
   */

  /*
   * Why, you say?
   *
   * Simply put, speed. In the 2014 CEUS NSHM, 1000km from a site nets about 30k
   * sources, each of which has an associated MFD with up to 33 values (and that
   * assumes the different mMax models have been collapsed together). So 990k
   * curve calculations per GMM. However, if the rates of those sources are
   * first aggregated into a matrix of distance (300) and magnitude (33) bins,
   * then only 900 chazard curve calculations need be performed per GMM. Ha!
   */

  /*
   * need to specify magnitude and distance discretization
   */

  /**
   * Create a {@code Function} for a location the condenses a
   * {@code GridSourceSet} into tabular form (distance, magnitude and azimuth
   * bins) for speedier iteration.
   *
   * @param loc reference point for table
   */
  public static Function<GridSourceSet, SourceSet<? extends Source>> optimizer(Location loc) {
    return new Optimizer(loc);
  }

  private static class Optimizer implements Function<GridSourceSet, SourceSet<? extends Source>> {
    private final Location loc;

    Optimizer(Location loc) {
      this.loc = loc;
    }

    @Override
    public Table apply(GridSourceSet sources) {
      return new Table(sources, loc);
    }
  }

  /*
   * Notes on dealing with mixedMech situations (e.g. UC3)
   *
   * Need 3 tables (SS, R, N)
   *
   * Rate in each R-M bin gets partitioned across three tables.
   *
   * When building sources we could normalize the rates to create a mechMap on
   * the fly and sum back to the total rate.
   *
   * Alternatively, and preferably, we reconsolidate partitioned rates into a
   * list
   */

  /*
   * TODO upgrade this to DataVolume to handle azimuth bins?? TODO split over
   * focal mechs? required for UC3 grids
   */

  /**
   * Tabular implementation of a {@code GridSourceSet}. This class consolidates
   * the point sources that influence hazard at a site using a
   * magnitude-distance-rate {@code DataTable}, from which a list of sources is
   * generated. A {@code Table} is created on a per-calculation basis and is
   * unique to a location.
   *
   * @see GridSourceSet#optimizer(Location)
   */
  private static final class Table extends AbstractSourceSet<PointSource> {

    private final GridSourceSet parent;
    private final Location origin;
    private final List<PointSource> sources;

    /*
     * Row count reflects the number of rows used in a DataTable when building
     * sources. ALthough this will likely be the same as sources.size(), it may
     * not be. For example, when using multi-mechs many more sources are created
     * because the different focal mechs arce (can) not be combined given
     * bpossibly varying rates across different magnitudes.
     */
    private int rowCount;
    private int maximumSize;
    private int parentCount;

    private Table(GridSourceSet parent, Location origin) {
      super(parent.name(), parent.id(), parent.weight(), parent.groundMotionModels());
      this.parent = parent;
      this.origin = origin;
      this.sources = parent.singularMechs ? initSources() : initMultiMechSources();
    }

    /**
     * The number of sources drawn from a parent {@code GridSourceSet} during
     * initialization.
     */
    public int parentCount() {
      return parentCount;
    }

    @Override
    public String name() {
      return parent.name() + " (opt)";
    }

    @Override
    public SourceType type() {
      return parent.type();
    }

    @Override
    public int size() {
      return parent.size();
    }

    @Override
    public Predicate<PointSource> distanceFilter(Location loc, double distance) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Iterator<PointSource> iterator() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Iterable<PointSource> iterableForLocation(Location loc) {
      /*
       * Ignore location; simply iterate over the list of sources. Source list
       * will be empty if mfdTable is empty (all zeros).
       */
      return sources;
    }

    private static final double SRC_TO_SITE_AZIMUTH = 0.0;

    /* creates the type of point source specified in the parent */
    private List<PointSource> initSources() {

      // table keys are specified as lowermost and uppermost bin edges
      double Δm = parent.Δm;
      double ΔmBy2 = Δm / 2.0;
      double mMin = parent.magMaster[0] - ΔmBy2;
      double mMax = parent.magMaster[parent.magMaster.length - 1] + ΔmBy2;
      double rMax = parent.groundMotionModels().maxDistance();

      IntervalTable.Builder tableBuilder = new IntervalTable.Builder()
          .rows(0.0, rMax, distanceDiscretization(rMax))
          .columns(mMin, mMax, Δm);

      for (PointSource source : parent.iterableForLocation(origin)) {
        double r = Locations.horzDistanceFast(origin, source.loc);
        tableBuilder.add(r, source.mfd);
        parentCount++;
      }

      IntervalTable mfdTable = tableBuilder.build();

      // System.out.println(parent.name());
      // System.out.println(mfdTable);

      List<Double> distances = mfdTable.rows();
      maximumSize = distances.size();
      ImmutableList.Builder<PointSource> b = ImmutableList.builder();
      for (double r : distances) {
        XySequence mfd = mfdTable.row(r);
        if (mfd.isClear()) {
          continue;
        }
        Location loc = Locations.location(origin, SRC_TO_SITE_AZIMUTH, r);

        b.add(PointSources.pointSource(
            parent.type(),
            parent.sourceType,
            loc,
            mfd,
            parent.mechMaps.get(0),
            parent.rupScaling,
            parent.depthModel));
        rowCount++;
      }
      return b.build();
    }

    /* always creates finite point sources */
    private List<PointSource> initMultiMechSources() {
      double Δm = parent.Δm;
      double ΔmBy2 = Δm / 2.0;
      double mMin = parent.magMaster[0] - ΔmBy2;
      double mMax = parent.magMaster[parent.magMaster.length - 1] + ΔmBy2;
      double rMax = parent.groundMotionModels().maxDistance();

      IntervalTable.Builder ssTableBuilder = new IntervalTable.Builder()
          .rows(0.0, rMax, distanceDiscretization(rMax))
          .columns(mMin, mMax, Δm);

      IntervalTable.Builder rTableBuilder = new IntervalTable.Builder()
          .rows(0.0, rMax, distanceDiscretization(rMax))
          .columns(mMin, mMax, Δm);

      IntervalTable.Builder nTableBuilder = new IntervalTable.Builder()
          .rows(0.0, rMax, distanceDiscretization(rMax))
          .columns(mMin, mMax, Δm);

      // XySequence srcMfdSum = null;

      for (PointSource source : parent.iterableForLocation(origin)) {
        // if (srcMfdSum == null) {
        // srcMfdSum = XySequence.emptyCopyOf(source.mfd);
        // }
        // srcMfdSum.add(source.mfd);

        double r = Locations.horzDistanceFast(origin, source.loc);
        ssTableBuilder.add(r, XySequence.copyOf(source.mfd)
            .multiply(source.mechWtMap.get(STRIKE_SLIP)));
        rTableBuilder.add(r, XySequence.copyOf(source.mfd)
            .multiply(source.mechWtMap.get(REVERSE)));
        nTableBuilder.add(r, XySequence.copyOf(source.mfd)
            .multiply(source.mechWtMap.get(NORMAL)));
        parentCount++;
      }

      IntervalTable ssTable = ssTableBuilder.build();
      // System.out.println("SS Table:" + TextUtils.NEWLINE + ssTable);
      IntervalTable rTable = rTableBuilder.build();
      // System.out.println("R Table:" + TextUtils.NEWLINE + rTable);
      IntervalTable nTable = nTableBuilder.build();
      // System.out.println("N Table:" + TextUtils.NEWLINE + nTable);

      // DataTable tableSum = DataTable.Builder.fromModel(ssTable)
      // .add(ssTable)
      // .add(rTable)
      // .add(nTable)
      // .build();
      //
      // XySequence tableMfdSum =
      // XySequence.emptyCopyOf(tableSum.row(0.1));
      // for (double row : tableSum.rows()) {
      // tableMfdSum.add(tableSum.row(row));
      // }
      // System.out.println("sourcesMfd:");
      // System.out.println(srcMfdSum);
      //
      // System.out.println("tableMfd:");
      // System.out.println(tableMfdSum);

      List<Double> distances = ssTable.rows();
      maximumSize = distances.size();
      ImmutableList.Builder<PointSource> b = ImmutableList.builder();
      for (double r : distances) {
        Location loc = Locations.location(origin, SRC_TO_SITE_AZIMUTH, r);
        boolean tableRowUsed = false;

        XySequence ssMfd = ssTable.row(r);
        if (ssMfd.isClear()) {
          continue;
        }
        b.add(PointSources.pointSource(
            parent.type(),
            PointSourceType.FINITE,
            loc,
            ssMfd,
            parent.mechMaps.get(0),
            parent.rupScaling,
            parent.depthModel));
        tableRowUsed = true;

        XySequence rMfd = rTable.row(r);
        if (rMfd.isClear()) {
          continue;
        }
        b.add(PointSources.pointSource(
            parent.type(),
            PointSourceType.FINITE,
            loc,
            rMfd,
            parent.mechMaps.get(0),
            parent.rupScaling,
            parent.depthModel));
        tableRowUsed = true;

        XySequence nMfd = nTable.row(r);
        if (nMfd.isClear()) {
          continue;
        }
        b.add(PointSources.pointSource(
            parent.type(),
            PointSourceType.FINITE,
            loc,
            nMfd,
            parent.mechMaps.get(0),
            parent.rupScaling,
            parent.depthModel));
        tableRowUsed = true;

        if (tableRowUsed) {
          rowCount++;
        }
      }
      return b.build();
    }

    /*
     * Return a distance dependent discretization. Currently this is fixed at
     * 1km for r<400km and 5km for r>= 400km
     */
    private static double distanceDiscretization(double r) {
      return r < 400.0 ? 1.0 : 5.0;
    }
  }

}
