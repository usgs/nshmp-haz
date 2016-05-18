package org.opensha2.eq.model;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static java.lang.Math.ceil;

import static org.opensha2.eq.fault.Faults.validateStrike;
import static org.opensha2.eq.fault.FocalMech.NORMAL;
import static org.opensha2.eq.fault.FocalMech.REVERSE;
import static org.opensha2.eq.fault.FocalMech.STRIKE_SLIP;
import static org.opensha2.geo.BorderType.MERCATOR_LINEAR;
import static org.opensha2.geo.GriddedRegion.ANCHOR_0_0;
import static org.opensha2.util.TextUtils.validateName;

import org.opensha2.data.XySequence;
import org.opensha2.eq.fault.FocalMech;
import org.opensha2.eq.fault.surface.RuptureScaling;
import org.opensha2.eq.model.PointSource.DepthModel;
import org.opensha2.geo.GriddedRegion;
import org.opensha2.geo.Location;
import org.opensha2.geo.LocationList;
import org.opensha2.geo.Locations;
import org.opensha2.geo.Regions;
import org.opensha2.mfd.IncrementalMfd;
import org.opensha2.mfd.Mfds;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;

/**
 * Area source representation. An {@code AreaSource} represents a region over
 * which there is a equal likelihood of earthquake occurrence, as specified by a
 * single {@link IncrementalMfd}.
 *
 * <p>Internally, an {@code AreaSource} distributes ruptures over a grid of
 * point {@link Source}s; the resolution of the grid may scale with distance
 * from a site according to the assigned {@link GridScaling}. An
 * {@code AreaSource} therefore supplies an
 * {@link #iterableForLocation(Location)}. If no location-dependent scaling is
 * specified, this custom {@code Iterable}'s {@code iterator()} behaves the same
 * as the built-in {@link #iterator()}. If the specified scaling <em>is</em>
 * location-dependent, the standard {@link #iterator()} returns ruptures derived
 * from 0.1° spaced sources.</p>
 *
 * @author Peter Powers
 */
public class AreaSource implements Source {

  /*
   * Regardless of Scaling, source GriddedRegions are stored in a list. The
   * index of the default grid is supplied by GridScaling.
   */

  private final String name;
  private final IncrementalMfd mfd;
  private final GridScaling gridScaling;
  private final List<GriddedRegion> sourceGrids;
  private final Map<FocalMech, Double> mechMap;
  final DepthModel depthModel; // package exposure for parser logging
  private final double strike;
  private final RuptureScaling rupScaling;
  private final PointSourceType sourceType;

  // TODO need singleton source grid for border representation
  // which side of an area a site is on is important; either point grids
  // need to be created at runtime or need to be located at the centroid of
  // area

  AreaSource(String name, IncrementalMfd mfd, GridScaling gridScaling,
      List<GriddedRegion> sourceGrids, Map<FocalMech, Double> mechMap,
      DepthModel depthModel, double strike, RuptureScaling rupScaling,
      PointSourceType sourceType) {
    this.name = name;
    this.mfd = mfd;
    this.gridScaling = gridScaling;
    this.sourceGrids = sourceGrids;
    this.mechMap = mechMap;
    this.depthModel = depthModel;
    this.strike = strike;
    this.rupScaling = rupScaling;
    this.sourceType = sourceType;
  }

  @Override
  public String name() {
    return name;
  }

  /**
   * The number of {@code Rupture}s for an {@code AreaSource} will vary if
   * {@code GridScaling} is non-uniform. In such cases, this method returns the
   * number of ruptures that would be expected from 0.1° spaced point sources.
   */
  @Override
  public int size() {

    // TODO revisit
    // what type of point sources are being used?
    // create example point source and get rupCount??

    int mechCount = mechCount(mechMap, sourceType);
    int magCount = mfd.getNum(); // TODO this assumes no zero rate bins
    int sourceCount = sourceGrids.get(gridScaling.defaultIndex).size();

    return sourceCount * magCount * mechCount;
  }

  private static int mechCount(Map<FocalMech, Double> mechWtMap, PointSourceType type) {
    int ssCount = (int) ceil(mechWtMap.get(STRIKE_SLIP));
    int revCount = (int) ceil(mechWtMap.get(REVERSE));
    int norCount = (int) ceil(mechWtMap.get(NORMAL));
    switch (type) {
      case POINT:
        return ssCount + revCount + norCount;
      default:
        return ssCount + revCount * 2 + norCount * 2;
    }
  }

  /**
   * Return the border of this {@code AreaSource}.
   */
  public LocationList border() {
    return sourceGrids.get(0).border();
  }

  /**
   * For non-uniform {@code GridScaling}, this iterator returns ruptures derived
   * from 0.1° spaced sources.
   */
  @Override
  public Iterator<Rupture> iterator() {
    GriddedRegion sourceGrid = sourceGrids.get(gridScaling.defaultIndex);
    return sourceGridIterable(sourceGrid).iterator();
  }

  /**
   * Return a {@code Rupture} iterator over distributed point {@code Source}s
   * whose spacing is a function of the distance from the supplied Location.
   *
   * @param loc Location of interest
   */
  public Iterable<Rupture> iterableForLocation(Location loc) {
    LocationList border = sourceGrids.get(0).border();
    double distance = Locations.minDistanceToLine(loc, border);
    int gridIndex = gridScaling.indexForDistance(distance);
    GriddedRegion sourceGrid = sourceGrids.get(gridIndex);
    return sourceGridIterable(sourceGrid);
  }

  private Iterable<Rupture> sourceGridIterable(GriddedRegion gr) {
    IncrementalMfd scaledMfd = IncrementalMfd.copyOf(mfd);
    scaledMfd.scale(1.0 / gr.size());
    XySequence xyMfd = Mfds.toSequence(scaledMfd);

    List<Iterable<Rupture>> sourceRupturesList = new ArrayList<>();
    for (Location loc : gr) {
      sourceRupturesList.add(createSource(loc, xyMfd));
    }

    // TODO ideally, the returned iterable creates PointSources as
    // needed (lazily) as opposed to prebuilding list of sources.
    // - would rather avoid custom handler for area sources
    // that would likely be required to spread sources over
    // independent threads
    // TODO examine and comment thread safety

    return Iterables.concat(sourceRupturesList);
  }

  private PointSource createSource(Location loc, XySequence mfd) {
    switch (sourceType) {
      case POINT:
        return new PointSource(loc, mfd, mechMap, rupScaling, depthModel);
      case FINITE:
        return new PointSourceFinite(loc, mfd, mechMap, rupScaling, depthModel);
      case FIXED_STRIKE:
        return new PointSourceFixedStrike(loc, mfd, mechMap, rupScaling, depthModel,
            strike);
      default:
        throw new IllegalStateException("Unhandled point source type");
    }
  }

  /**
   * Point source discretization scaling. {@code UNIFORM_*} variants provide
   * approximately 1 km (0.01°), 5 km (0.05°), and 10 km (0.1°) source spacing.
   *
   * <p>{@code SCALED_SM} provides fine source spacing of 0.02° from 0 to 20km,
   * 0.05° from 20 to 50 km, 0.1° from 50 to 100 km, 0.2 from 100 to 200 km, 0.5
   * for 200 to 400 km, and uses a single source at the closest point on the
   * area border beyond 400 km.</p>
   *
   * <p>{@code SCALED_LG} provides coarser source spacing of 0.1° from 0 to 100
   * km, 0.2 from 100 to 200 km, 0.5 for 200 to 400 km, and uses a single source
   * at the closest point on the area border beyond 400 km.</p>
   */
  @SuppressWarnings("javadoc")
  public enum GridScaling {
    UNIFORM_0P005(0, new double[] { 0.005 }),
    UNIFORM_0P01(0, new double[] { 0.01 }),
    UNIFORM_0P02(0, new double[] { 0.02 }),
    UNIFORM_0P05(0, new double[] { 0.05 }),
    UNIFORM_0P1(0, new double[] { 0.1 }),
    UNIFORM_0P5(0, new double[] { 0.5 }),
    SCALED_SMALL(2, new double[] { 0.02, 0.05, 0.1, 0.2, 0.5 }) {
      @Override
      int indexForDistance(double d) {
        return d < 20.0 ? 0 : d < 50.0 ? 1 : d < 100.0 ? 2 : d < 200.0 ? 3 : d < 400.0 ? 4
            : 5;
      }
    },
    SCALED_LARGE(0, new double[] { 0.1, 0.2, 0.5 }) {
      @Override
      int indexForDistance(double d) {
        return d < 100.0 ? 0 : d < 200.0 ? 1 : d < 400.0 ? 2 : 3;
      }
    };

    final int defaultIndex;
    final double[] resolutions;

    private GridScaling(int defaultIndex, double[] resolutions) {
      this.defaultIndex = defaultIndex;
      this.resolutions = resolutions;
    }

    /* distance is used when method is overridden, above */
    int indexForDistance(@SuppressWarnings("unused") double distance) {
      return defaultIndex;
    };

  }

  static class Builder {

    private static final String ID = "AreaSource.Builder";
    private boolean built = false;

    private String name;
    private Integer id;
    private LocationList border;
    private IncrementalMfd mfd;
    private Double strike;
    private GridScaling gridScaling;
    private RuptureScaling rupScaling;
    private Map<FocalMech, Double> mechMap;
    private NavigableMap<Double, Map<Double, Double>> magDepthMap;
    private Double maxDepth;
    private PointSourceType sourceType;

    Builder name(String name) {
      this.name = validateName(name);
      return this;
    }

    Builder id(int id) {
      this.id = id;
      return this;
    }

    Builder border(LocationList border) {
      this.border = validateBorder(border);
      return this;
    }

    Builder mfd(IncrementalMfd mfd) {
      this.mfd = checkNotNull(mfd, "MFD is null");
      return this;
    }

    Builder strike(double strike) {
      // unknown strike allowed for area sources
      this.strike = Double.isNaN(strike) ? strike : validateStrike(strike);
      return this;
    }

    Builder gridScaling(GridScaling gridScaling) {
      this.gridScaling = checkNotNull(gridScaling, "Grid scaling is null");
      return this;
    }

    Builder ruptureScaling(RuptureScaling rupScaling) {
      this.rupScaling = checkNotNull(rupScaling, "Rupture-Scaling is null");
      return this;
    }

    Builder mechs(Map<FocalMech, Double> mechMap) {
      // weights will have already been checked
      checkArgument(!checkNotNull(mechMap).isEmpty());
      checkArgument(mechMap.size() == 3);
      this.mechMap = mechMap;
      return this;
    }

    Builder depthMap(NavigableMap<Double, Map<Double, Double>> magDepthMap, SourceType type) {
      checkNotNull(magDepthMap, "MagDepthMap is null");
      checkArgument(magDepthMap.size() > 0, "MagDepthMap must have at least one entry");
      // the structure of the map and its weights will have been fully
      // validated by parser; still need to check that depths are
      // appropriate; 'type' indicates how to validate depths across
      // wrapper classes
      GridSourceSet.Builder.validateDepthMap(magDepthMap, type);
      // there must be at least one mag key that is >= MAX_MAG
      GridSourceSet.Builder.validateMagCutoffs(magDepthMap);
      this.magDepthMap = magDepthMap;
      return this;
    }

    Builder maxDepth(Double maxDepth, SourceType type) {
      this.maxDepth = checkNotNull(maxDepth, "Maximum depth is null");
      GridSourceSet.Builder.validateDepth(maxDepth, type);
      return this;
    }

    Builder sourceType(PointSourceType sourceType) {
      this.sourceType = checkNotNull(sourceType);
      return this;
    }

    private void validateState(String buildId) {
      checkState(!built, "This %s instance as already been used", buildId);
      checkState(name != null, "%s name not set", buildId);
      checkState(id != null, "%s id not set", buildId);
      checkState(border != null, "%s border not set", buildId);
      checkState(mfd != null, "%s MFD not set", buildId);
      checkState(strike != null, "%s strike not set", buildId);
      checkState(sourceType != null, "%s source type not set", buildId);
      checkState(gridScaling != null, "%s grid scaling not set", buildId);
      checkState(rupScaling != null, "%s rupture-scaling relation not set", buildId);
      checkState(mechMap != null, "%s focal mech map not set", buildId);
      checkState(magDepthMap != null, "%s mag-depth-weight map not set", buildId);
      checkState(maxDepth != null, "%s maximum depth not set", buildId);

      /*
       * Validate depths. depths will already have been checked for consistency
       * with allowable depths for different source types. Must also ensure that
       * all depths (zTop) in the magDepthMap are <= maxDepth.
       */
      GridSourceSet.Builder.validateMaxAndMapDepths(magDepthMap, maxDepth, buildId);

      built = true;
    }

    AreaSource build() {
      validateState(ID);
      List<GriddedRegion> sourceGrids = buildSourceGrids(border, gridScaling);
      DepthModel depthModel = DepthModel.create(magDepthMap, mfd.xValues(), maxDepth);
      return new AreaSource(name, mfd, gridScaling, sourceGrids, mechMap,
          depthModel, strike, rupScaling, sourceType);
    }

    private static List<GriddedRegion> buildSourceGrids(LocationList border,
        GridScaling scaling) {
      ImmutableList.Builder<GriddedRegion> gridBuilder = ImmutableList.builder();
      for (double resolution : scaling.resolutions) {
        String name = "Area source grid [" + resolution + "° spacing]";
        GriddedRegion grid = Regions.createGridded(name, border, MERCATOR_LINEAR,
            resolution, resolution, ANCHOR_0_0);
        // TODO revisit; small areas and coarse grids will likely cause
        // problems and we'll need to substitute single node grids
        checkState(grid.size() > 0, "Grid is empty");
        gridBuilder.add(grid);
      }
      return gridBuilder.build();
    }

    private LocationList validateBorder(LocationList border) {
      checkNotNull(border, "Border is null");
      checkArgument(border.size() > 2, "Border contains fewer than 3 points");
      return border;
    }

  }

}
