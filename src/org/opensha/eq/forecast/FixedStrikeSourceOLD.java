package org.opensha.eq.forecast;

import static com.google.common.base.Preconditions.checkPositionIndex;
import static java.lang.Math.ceil;
import static java.lang.Math.cos;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.sin;
import static java.lang.Math.sqrt;
import static java.lang.Math.tan;
import static org.opensha.eq.fault.FocalMech.NORMAL;
import static org.opensha.eq.fault.FocalMech.REVERSE;
import static org.opensha.eq.fault.FocalMech.STRIKE_SLIP;
import static org.opensha.geo.GeoTools.TO_RAD;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.opensha.eq.fault.FocalMech;
import org.opensha.eq.fault.scaling.MagLengthRelationship;
import org.opensha.eq.fault.scaling.impl.WC1994_MagLengthRelationship;
import org.opensha.eq.fault.surface.PtSrcDistCorr;
import org.opensha.eq.fault.surface.RuptureSurface;
import org.opensha.geo.Location;
import org.opensha.geo.LocationList;
import org.opensha.geo.Locations;
import org.opensha.mfd.IncrementalMFD;

/**
 * This is a custom Fixed-Strike Source (Point-Source variant) representation
 * used for the NSHMP. It was derived from the UCERF2
 * {@code Point2Vert_FaultPoisSource} and was initially created to provide built in approximations of distance and
 * hanging wall effects as well as to override getMinDistance to provide
 * consistency with distances determined during hazard calcs.
 * 
 * THis is kind of kludgy for now. In NSHMP calcs, we're not concered with dip
 * for fixed strike sources. THis is due to the fact that in the CEUS, gridded
 * finite faults are always strike slip, and in the West with NGA's, dip is
 * handled by reading the rupture rake to
 * 
 * TODO the eclosed ruptures can be much simpler than Frankel gridded surfaces
 * we're going to try settin gupper and lower seis depth to dtor
 * 
 * 
 * 
 * @author Peter Powers
 * @version $Id:$
 */
class FixedStrikeSourceOLD implements Source {
	
	private static final MagLengthRelationship WC94 = new WC1994_MagLengthRelationship();

	private final GridSourceSet parent;
	private final Location loc;
	private final IncrementalMFD mfd;
	private final Map<FocalMech, Double> mechWtMap;

	private int mechCount; // mechs with weight 1-3;
	private int ssIdx, revIdx; // normal not needed
	private int fwIdxLo, fwIdxHi;

	// number of mag-depth combinations per focal mech
	int magDepthCount;

	/**
	 * Constructs a new point earthquake source.
	 * @param loc <code>Location</code> of the point source
	 * @param mfd magnitude frequency distribution of the source
	 * @param magDepthMap specifies magnitude cutoffs and associated weights for
	 *        different depth-to-top-of-ruptures
	 * @param mechWtMap <code>Map</code> of focal mechanism weights
	 */
	FixedStrikeSourceOLD(GridSourceSet parent, Location loc, IncrementalMFD mfd,
		Map<FocalMech, Double> mechWtMap) {
		this.parent = parent;
		this.loc = loc;
		this.mfd = mfd;
		this.mechWtMap = mechWtMap;
		initSource();
	}

	@Override
	public String name() {
		return "FixedStrikeSourceOLD: " + loc;
	}

	/*
	 * NOTE: Getting a Rupture by index is deliberately inefficient to ensure
	 * thread safety. A new immutable Rupture and internal PointSurface are
	 * created on every call. Use Source.iterator() where possible.
	 */

	@Override
	public Rupture getRupture(int idx) {
		checkPositionIndex(idx, size());
		Rupture rupture = new Rupture();
		FixedStrikeSurface surface = new FixedStrikeSurface(loc);
		rupture.surface = surface;
		updateRupture(rupture, idx);
		return rupture;
	}

	/*
	 * NOTE/TODO: Although there should not be many instances where a
	 * FinitePointSourceOLD.rupture rate is reduced to zero (a mag-depth weight could be
	 * set to zero [this is not curently checked] of an MFD rate could be zero),
	 * in the cases where it is, we're doing a little more work than necessary
	 * below. We could alternatively short-circuit updateRupture() this method
	 * to return null reference, but would need to condsider getRUpture(int)
	 * implementation.
	 */

	private void updateRupture(Rupture rup, int idx) {

		int magDepthIdx = idx % magDepthCount;
		int magIdx = parent.magDepthIndices[magDepthIdx];
		double mag = mfd.getX(magIdx);
		double rate = mfd.getY(magIdx);

		double zTop = parent.magDepthDepths[magDepthIdx];
		double zTopWt = parent.magDepthWeights[magDepthIdx];

		FocalMech mech = mechForIndex(idx);
		double mechWt = mechWtMap.get(mech);
		if (mech != STRIKE_SLIP) mechWt *= 0.5;
		double dipRad = mech.dip() * TO_RAD;

		double widthDD = calcWidth(mag, zTop, dipRad);

		rup.mag = mag;
		rup.rake = mech.rake();
		rup.rate = rate * zTopWt * mechWt;

		FixedStrikeSurface pSurf = (FixedStrikeSurface) rup.surface;
		pSurf.mag = mag; // KLUDGY needed for distance correction
		pSurf.dip = mech.dip();
		pSurf.widthDD = widthDD;
		pSurf.widthH = widthDD * cos(dipRad);
		pSurf.zTop = zTop;
		pSurf.zBot = zTop + widthDD * sin(dipRad);
		pSurf.footwall = isOnFootwall(idx);

	}

	/*
	 * Overriden due to uncertainty on how getRuptureList() is constructed in
	 * parent. Looks clunky and uses cloning which can be error prone if
	 * implemented incorrectly. Was building custom NSHMP calculator using
	 * enhanced for-loops and was losing class information when iterating over
	 * sources and ruptures.
	 */
	@Override
	public List<Rupture> getRuptureList() {
		throw new UnsupportedOperationException(
			"A FinitePointSourceOLD does not allow access to the list of all possible ruptures.");
	}

	@Override
	public Iterator<Rupture> iterator() {
		// @formatter:off
		return new Iterator<Rupture>() {
			Rupture rupture = new Rupture();
			{ rupture.surface = new FixedStrikeSurface(loc); }
			int size = size();
			int caret = 0;
			@Override public boolean hasNext() {
				if (caret > size) return false;
				updateRupture(rupture, caret++);
				return (rupture.rate > 0.0) ? true : hasNext();
			}
			@Override public Rupture next() {
				if (!hasNext()) throw new NoSuchElementException();
				return rupture;
			}
			@Override public void remove() {
				throw new UnsupportedOperationException();
			}
		};
		// @formatter:on
	}

	@Override
	public RuptureSurface surface() {
		throw new UnsupportedOperationException(
			"FinitePointSourceOLD surfaces are only created as needed");
	}

	@Override
	public int size() {
		return magDepthCount * mechCount;
	}

	@Override
	public double getMinDistance(Location loc) {
		return Locations.horzDistanceFast(this.loc, loc);
	}

	private void initSource() {

		/* Get the total number of mag-depth combinations from parent */
		magDepthCount = parent.magDepthIndices.length;

		/*
		 * Init rupture indexing: SS-FW RV-FW RV-HW NR-FW NR-HW. Each category
		 * will have ruptures for every mag in 'mfd' and depth in 'magDepthMap'.
		 * Focal mech is determined using the max indices for each type of mech
		 * determined using the Math.ceil(wt) [scales to 1] * num_M.
		 */
		int ssCount = (int) ceil(mechWtMap.get(STRIKE_SLIP)) * magDepthCount;
		int revCount = (int) ceil(mechWtMap.get(REVERSE)) * magDepthCount * 2;
		int norCount = (int) ceil(mechWtMap.get(NORMAL)) * magDepthCount * 2;
		ssIdx = ssCount;
		revIdx = ssCount + revCount;
		fwIdxLo = ssCount + revCount / 2;
		fwIdxHi = ssCount + revCount + norCount / 2;

		/*
		 * Init focal mch counts: Total focal mech representations required,
		 * double counting reverse and normal mechs because they will have both
		 * hanging wall and footwall representations.
		 */
		mechCount = 0;
		for (FocalMech mech : mechWtMap.keySet()) {
			double wt = mechWtMap.get(mech);
			if (wt == 0.0) continue;
			mechCount += (mech == STRIKE_SLIP) ? 1 : 2;
		}

	}

	/**
	 * Returns the minimum of the aspect ratio width (based on WC94) length and
	 * the allowable down-dip width.
	 * 
	 * @param mag
	 * @param depth
	 * @param dipRad (in radians)
	 * @return
	 */
	private double calcWidth(double mag, double depth, double dipRad) {
		double length = WC94.getMedianLength(mag);
		double aspectWidth = length / 1.5;
		double ddWidth = (14.0 - depth) / sin(dipRad);
		return min(aspectWidth, ddWidth);
	}

	/**
	 * Returns the focal mechanism of the rupture at the supplied index.
	 * @param idx of the rupture of interest
	 * @return the associated focal mechanism
	 */
	private FocalMech mechForIndex(int idx) {
		// iteration order is always SS -> REV -> NOR
		return (idx < ssIdx) ? STRIKE_SLIP : (idx < revIdx) ? REVERSE : NORMAL;
	}

	/**
	 * Returns whether the rupture at index should be on the footwall (i.e. have
	 * its rX value set negative). Strike-slip mechs are marked as footwall to
	 * potentially short circuit GMPE calcs. Because the index order is SS-FW
	 * RV-FW RV-HW NR-FW NR-HW
	 */
	private boolean isOnFootwall(int idx) {
		return (idx < fwIdxLo) ? true : (idx < revIdx) ? false
			: (idx < fwIdxHi) ? true : false;
	}

	@Override
	public Location centroid() {
		throw new UnsupportedOperationException("");
	}

	private static class FixedStrikeSurface implements RuptureSurface {

		private Location loc;
		private double mag;
		private double dip;
		private double zTop;
		private double zBot; // base of rupture; may be less than 14km
		private double widthH; // horizontal width (surface projection)
		private double widthDD; // down-dip width
		private boolean footwall;

		private FixedStrikeSurface(Location loc) {
			this.loc = loc;
		}

		@Override
		public Distances distanceTo(Location loc) {
			// TODO 0.5 is WUS specific and based on discretization of distances
			// in grid source GMM lookup tables

			// because we're not using table lookup optimizations, we push the
			// minimum rJB out to 0.5 (half the table bin-width)
			double rJB = Locations.horzDistanceFast(this.loc, loc);
			rJB *= PtSrcDistCorr.getCorrection(rJB, mag,
				PtSrcDistCorr.Type.NSHMP08);
			rJB = max(0.5, rJB);
			double rX = footwall ? -rJB : rJB + widthH;

			if (footwall) return Distances.create(rJB, hypot2(rJB, zTop), rX);

			double dipRad = dip * TO_RAD;
			double rCut = zBot * tan(dipRad);

			if (rJB > rCut)
				return Distances.create(rJB, hypot2(rJB, zBot), rX);

			// rRup when rJB is 0 -- we take the minimum the site-to-top-edge
			// and site-to-normal of rupture for the site being directly over
			// the down-dip edge of the rupture
			double rRup0 = min(hypot2(widthH, zTop), zBot * cos(dipRad));
			// rRup at cutoff rJB
			double rRupC = zBot / cos(dipRad);
			// scale linearly with rJB distance
			double rRup = (rRupC - rRup0) * rJB / rCut + rRup0;

			return Distances.create(rJB, rRup, rX);
		}

		/**
		 * Same as {@code Math.hypot()} without regard to under/over flow.
		 */
		private static final double hypot2(double v1, double v2) {
			return sqrt(v1 * v1 + v2 * v2);
		}

		// @formatter:off
		@Override public double strike() { throw new UnsupportedOperationException(exMessage("strike")); }
		@Override public double dip() { return dip; }
		@Override public double dipDirection() { throw new UnsupportedOperationException(exMessage("dipDirection")); }
		@Override public double length() { throw new UnsupportedOperationException(exMessage("length")); }
		@Override public double width() { return widthDD; }
		@Override public double area() { throw new UnsupportedOperationException(exMessage("area")); }
		@Override public double depth() { return zTop; }
		// TODO should this be the true centroid of the finite surface
		// representation or is the grid node location ok?
		@Override public Location centroid() { return loc; } 
		
		
		private static String exMessage(String field) {
			return "No '" + field + "' for FinitePointSourceOLD surface";
		}
	}
		

}

//public class FixedStrikeSourceOLD implements Source {
//
//	private static final String NAME = "NSHMP Fixed Strike Source";
//	private static final String RUP_NAME = "NSHMP Fixed Strike Fault";
//
//	private MagLengthRelationship mlr;
//	private double strike = 0.0;
////	private FrankelGriddedSurface surface;
//
//	/**
//	 * Constructs a new fixed-strike earthquake source. This is a variant of a
//	 * {@link FinitePointSourceOLD} where ruptures are always represented as finite
//	 * faults with a fixed strike. Fault length is computed using
//	 * {@link WC1994_MagLengthRelationship}.
//	 * 
//	 * @param loc <code>Location</code> of the point source
//	 * @param mfd magnitude frequency distribution of the source
//	 * @param mlr magnitude length relationship to use
//	 * @param duration of the parent forecast
//	 * @param depths 2 element array of rupture top depths;
//	 *        <code>depths[0]</code> used for M&lt;6.5, <code>depths[1]</code>
//	 *        used for M&ge;6.5
//	 * @param mechWtMap <code>Map</code> of focal mechanism weights
//	 * @param strike of the source
//	 */
//	public FixedStrikeSourceOLD(Location loc, IncrementalMFD mfd,
//		MagLengthRelationship mlr, double duration, double[] depths,
//		Map<FocalMech, Double> mechWtMap, double strike) {
//
//		super(loc, mfd, duration, depths, mechWtMap);
//		name = NAME;
//		this.mlr = mlr;
//		this.strike = strike;
//	}
//
//	/*
//	 * NOTE Don't need to override initRupture(). Most fixed strike sources are
//	 * relatively small so the extra baggage of a point surface (in parent) that
//	 * may not be used on occasion is inconsequential.
//	 * 
//	 * The NSHMP uses the point location to decide if a source is in or out so
//	 * no need to override getMinDistance(Site)
//	 */
//
//	@Override
//	protected void updateRupture(double mag, double dip, double rake,
//			double depth, double width, boolean footwall) {
//		if (mag >= M_FINITE_CUT) {
//			// finite rupture
//			double halfLen = mlr.getMedianLength(mag) / 2;
////			System.out.println("HL: " + halfLen);
//			Location faultEnd1 = Locations.location(getLocation(),
//				new LocationVector(strike, halfLen, 0));
//			LocationVector faultVec = LocationUtils.vector(faultEnd1,
//				getLocation());
//			faultVec.setHorzDistance(halfLen * 2);
//			Location faultEnd2 = LocationUtils.location(faultEnd1, faultVec);
////			FaultTrace fault = new FaultTrace(RUP_NAME);
////			fault.add(faultEnd1);
////			System.out.println("FV1: " + faultEnd1);
////			fault.add(faultEnd2);
////			System.out.println("FV2: " + faultEnd2 + " dip: " + dip);
//			LocationList fault = LocationList.create(faultEnd1, faultEnd2);
//			surface = new FrankelGriddedSurface(fault, dip, depth, depth + 0.01,
//				1.0);
//			probEqkRupture.setMag(mag);
//			probEqkRupture.setAveRake(rake);
//			probEqkRupture.setRuptureSurface(surface);
//		} else {
//			// point rupture
//			super.updateRupture(mag, dip, rake, depth, width, footwall);
//		}
//	}
//
//}
