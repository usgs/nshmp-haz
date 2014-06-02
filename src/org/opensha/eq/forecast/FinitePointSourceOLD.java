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
import org.opensha.geo.Locations;
import org.opensha.mfd.IncrementalMFD;

/**
 * Point-source earthquake implementation in which all magnitudes are
 * represented as finite faults and any normal or reverse sources are
 * represented with two possible geometries, one dipping towards the observer
 * and one dipping away. In both cases the leading edge of the finite source
 * representation is located at the point {@code Location} of the source itself
 * (TODO: illustration or link).
 * 
 * <p>This is the point earthquake source representation used for the 2014
 * NSHMP. It was created to provide support for weighted magnitude-depth
 * distributions and improved approximations of hanging wall terms vis-a-vis
 * self-consistent distance calculations.</p>
 * 
 * <p>The {@link FinitePointSourceOLD#getRupture(int)} method is thread safe,
 * however, it is inefficient in that it creates a new {@link Rupture} on every
 * call. Use of {@link Source#iterator()} is preferred, but {@code Rupture}
 * instances returned by the iterator should <i>not</i> be retained and an
 * iterator instance should only ever be used by a single thread.</p>
 * 
 * <b>NOTE</b>: {@code Source.size()} returns the absolute number of
 * {@code Rupture}s that can be created given the supplied source input
 * arguments; the iterator, however, <i>may</i> return fewer {@code Rupture}s as
 * some may have zero rates or zero weights.
 * 
 * @author Peter Powers
 */
class FinitePointSourceOLD implements Source {
	
	// TODO a similar implementation in which the centroids of the finite faults
	// are coincident with the source location should be considered

	private static final MagLengthRelationship WC94 = new WC1994_MagLengthRelationship();

	private final GridSourceSet parent;
	private final Location loc;
	private final IncrementalMFD mfd;
	private final Map<FocalMech, Double> mechWtMap;

	private int rupCount;
	private int magDepthCount;
	private int ssIdx, revIdx;
	private int fwIdxLo, fwIdxHi;

	/**
	 * Constructs a new point earthquake source.
	 * @param loc <code>Location</code> of the point source
	 * @param mfd magnitude frequency distribution of the source
	 * @param magDepthMap specifies magnitude cutoffs and associated weights for
	 *        different depth-to-top-of-ruptures
	 * @param mechWtMap <code>Map</code> of focal mechanism weights
	 */
	FinitePointSourceOLD(GridSourceSet parent, Location loc, IncrementalMFD mfd,
		Map<FocalMech, Double> mechWtMap) {
		this.parent = parent;
		this.loc = loc;
		this.mfd = mfd;
		this.mechWtMap = mechWtMap;
		initSource();
	}

	@Override
	public String name() {
		return "FinitePointSourceOLD: " + loc;
	}

	/*
	 * NOTE: Getting a Rupture by index is deliberately inefficient to ensure
	 * thread safety. A new immutable Rupture and internal FinitePointSurface are
	 * created on every call. Use Source.iterator() where possible.
	 */

	@Override
	public Rupture getRupture(int idx) {
		checkPositionIndex(idx, size());
		Rupture rupture = new Rupture();
		FinitePointSurface surface = new FinitePointSurface(loc);
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

		FinitePointSurface pSurf = (FinitePointSurface) rup.surface;
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
//	@Override
//	public List<Rupture> getRuptureList() {
//		throw new UnsupportedOperationException(
//			"A FinitePointSourceOLD does not allow access to the list of all possible ruptures.");
//	}

	@Override
	public Iterator<Rupture> iterator() {
		// @formatter:off
		return new Iterator<Rupture>() {
			Rupture rupture = new Rupture();
			{ rupture.surface = new FinitePointSurface(loc); }
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

//	@Override
//	public RuptureSurface surface() {
//		throw new UnsupportedOperationException(
//			"FinitePointSourceOLD surfaces are only created as needed");
//	}

	@Override
	public int size() {
		return rupCount;
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
		 * will have ruptures for every mag in 'mfd' and depth in parent
		 * 'magDepthMap'.
		 */
		int ssCount = (int) ceil(mechWtMap.get(STRIKE_SLIP)) * magDepthCount;
		int revCount = (int) ceil(mechWtMap.get(REVERSE)) * magDepthCount * 2;
		int norCount = (int) ceil(mechWtMap.get(NORMAL)) * magDepthCount * 2;
		ssIdx = ssCount;
		revIdx = ssCount + revCount;
		// normal not needed
		fwIdxLo = ssCount + revCount / 2;
		fwIdxHi = ssCount + revCount + norCount / 2;

		/*
		 * Init focal mch counts: Total focal mech representations required,
		 * double counting reverse and normal mechs because they will have both
		 * hanging wall and footwall representations.
		 */
		int mechCount = 0;
		for (FocalMech mech : mechWtMap.keySet()) {
			double wt = mechWtMap.get(mech);
			if (wt == 0.0) continue;
			mechCount += (mech == STRIKE_SLIP) ? 1 : 2;
		}
		rupCount = magDepthCount * mechCount;

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

//	@Override
//	public Location centroid() {
//		throw new UnsupportedOperationException("");
//	}

	private static class FinitePointSurface implements RuptureSurface {

		private Location loc;
		private double mag;
		private double dip;
		private double zTop;
		private double zBot; // base of rupture; may be less than 14km
		private double widthH; // horizontal width (surface projection)
		private double widthDD; // down-dip width
		private boolean footwall;

		private FinitePointSurface(Location loc) {
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
	
	public static void main(String[] args) {
		
//		System.out.println(NSHMP_Util.getMeanRJB(6.05, 1.0));
//		double dist = 6.5;
//		double xmag = 6.05;
//		
//		double dr_rjb = 1.0; // historic context; could be dropped
//		double dm_rjb = 0.1;
//		double xmmin_rjb = 6.05;
//		
//	    int irjb = (int) (dist/dr_rjb+1);
//	     
//	    int m_ind = 1 + Math.max(0,(int) Math.rint((xmag-xmmin_rjb)/dm_rjb));
//	    m_ind= Math.min(26,m_ind);
//	    System.out.println("m_ind: " + m_ind);
//	    System.out.println("irjb: " + irjb);
//	    
//	    System.out.println("====");
//	    double mCorr = Math.round(xmag/0.05)*0.05;
//		double r = NSHMP_Util.getMeanRJB(mCorr, dist);
//		System.out.println(r);
		
		
//		double Mw = 7.45;
//		SingleMagFreqDist mfd = new SingleMagFreqDist(Mw, 1, 0.1, Mw, 1);
//		Location srcLoc = new Location(31.6, -117.1);
//		Location siteLoc = new Location(31.6, -117.105);
//		double[] depths = new double[] {5.0, 1.0};
//		
//		Map<FocalMech, Double> mechMap = Maps.newHashMap();
//		mechMap.put(FocalMech.STRIKE_SLIP, 0.0);
//		mechMap.put(FocalMech.REVERSE, 0.0);
//		mechMap.put(FocalMech.NORMAL, 1.0);
//
//		
//		PointSource13b ptSrc = new PointSource13b(srcLoc, mfd, 1.0, depths, mechMap);
//		Joiner J = Joiner.on(" ");
//		for (ProbEqkRupture rup : ptSrc) {
//			FinitePointSurface surf = (FinitePointSurface) rup.getRuptureSurface();
//			List<Double> attr = Lists.newArrayList(
//				rup.getMag(),
//				rup.getAveRake(),
//				surf.getAveDip(),
//				surf.zTop,
//				surf.zBot,
//				surf.widthH,
//				surf.widthDD,
//				surf.getDistanceJB(siteLoc),
//				surf.getDistanceRup(siteLoc),
//				surf.getDistanceX(siteLoc));
//				
//			System.out.println(J.join(attr) + " " + surf.footwall);
//		}
//		
	}

}
