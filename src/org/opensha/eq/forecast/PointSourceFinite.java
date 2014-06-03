package org.opensha.eq.forecast;

import static com.google.common.base.Preconditions.checkPositionIndex;
import static java.lang.Math.ceil;
import static java.lang.Math.cos;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.sin;
import static java.lang.Math.tan;
import static org.opensha.eq.fault.FocalMech.NORMAL;
import static org.opensha.eq.fault.FocalMech.REVERSE;
import static org.opensha.eq.fault.FocalMech.STRIKE_SLIP;
import static org.opensha.geo.GeoTools.TO_RAD;
import static org.opensha.util.MathUtils.hypot;

import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

import org.opensha.eq.fault.FocalMech;
import org.opensha.eq.fault.surface.PtSrcDistCorr;
import org.opensha.geo.Location;
import org.opensha.geo.Locations;
import org.opensha.mfd.IncrementalMFD;

/**
 * Point-source earthquake implementation in which all magnitudes are
 * represented as finite faults and any normal or reverse sources are
 * represented with two possible geometries, one dipping towards the observer
 * and one dipping away. In both cases the leading edge of the finite source
 * representation is located at the point {@code Location} of the source itself
 * (in one representation the bottom trace is at the point {@code Location} and
 * the fault dips towards the observer, in its complement the top trace is at
 * the point {@code Location} and the fault dips away from the observer; TODO
 * add illustration or link).
 * 
 * <p>This is the generalized point earthquake source representation used for
 * the 2014 NSHMP. It was created to provide support for weighted
 * magnitude-depth distributions and improved approximations of hanging wall
 * terms vis-a-vis self-consistent distance calculations.</p>
 * 
 * <p><b>NOTE</b>: See {@link PointSource} description for notes on thread
 * safety and {@code Rupture} creation and iteration.</p>
 * 
 * @author Peter Powers
 */
class PointSourceFinite extends PointSource {

	int fwIdxLo, fwIdxHi;

	/**
	 * Constructs a new point earthquake source.
	 * @param loc <code>Location</code> of the point source
	 * @param mfd magnitude frequency distribution of the source
	 * @param magDepthMap specifies magnitude cutoffs and associated weights for
	 *        different depth-to-top-of-ruptures
	 * @param mechWtMap <code>Map</code> of focal mechanism weights
	 */
	PointSourceFinite(GridSourceSet parent, Location loc, IncrementalMFD mfd,
		Map<FocalMech, Double> mechWtMap) {
		super(parent, loc, mfd, mechWtMap);
		init();
	}

	@Override
	public String name() {
		return "PointSourceFinite: " + loc;
	}

	/*
	 * NOTE: Getting a Rupture by index is deliberately inefficient to ensure
	 * thread safety. A new immutable Rupture and internal FiniteSurface are
	 * created on every call. Use Source.iterator() where possible.
	 */

	@Override
	public Rupture getRupture(int idx) {
		checkPositionIndex(idx, size());
		Rupture rupture = new Rupture();
		rupture.surface = new FiniteSurface(loc);
		updateRupture(rupture, idx);
		return rupture;
	}

	/*
	 * NOTE/TODO: Although there should not be many instances where a
	 * FinitePointSourceOLD.rupture rate is reduced to zero (a mag-depth weight
	 * could be set to zero [this is not curently checked] of an MFD rate could
	 * be zero), in the cases where it is, we're doing a little more work than
	 * necessary below. We could alternatively short-circuit updateRupture()
	 * this method to return null reference, but would need to condsider
	 * getRUpture(int) implementation.
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

		FiniteSurface fpSurf = (FiniteSurface) rup.surface;
		fpSurf.mag = mag; // KLUDGY needed for distance correction
		fpSurf.dip = mech.dip();
		fpSurf.widthDD = widthDD;
		fpSurf.widthH = widthDD * cos(dipRad);
		fpSurf.zTop = zTop;
		fpSurf.zBot = zTop + widthDD * sin(dipRad);
		fpSurf.footwall = isOnFootwall(idx);

	}

	@Override
	public Iterator<Rupture> iterator() {
		// @formatter:off
		return new Iterator<Rupture>() {
			Rupture rupture = new Rupture();
			{ rupture.surface = new FiniteSurface(loc); }
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

	private void init() {

		/*
		 * Need to override indexing set by parent.
		 * 
		 * Init rupture indexing: SS-FW RV-FW RV-HW NR-FW NR-HW. Each category
		 * will have ruptures for every mag in 'mfd' and depth in parent
		 * 'magDepthMap'.
		 */
		int ssCount = (int) ceil(mechWtMap.get(STRIKE_SLIP)) * magDepthCount;
		int revCount = (int) ceil(mechWtMap.get(REVERSE)) * magDepthCount * 2;
		int norCount = (int) ceil(mechWtMap.get(NORMAL)) * magDepthCount * 2;
		ssIdx = ssCount;
		revIdx = ssCount + revCount;
		fwIdxLo = ssCount + revCount / 2;
		fwIdxHi = ssCount + revCount + norCount / 2;

		rupCount = ssCount + revCount + norCount;
	}

	/*
	 * Returns whether the rupture at index should be on the footwall (i.e. have
	 * its rX value set negative). Strike-slip mechs are marked as footwall to
	 * potentially short circuit GMPE calcs. Because the index order is SS-FW
	 * RV-FW RV-HW NR-FW NR-HW
	 */
	boolean isOnFootwall(int idx) {
		return (idx < fwIdxLo) ? true : (idx < revIdx) ? false
			: (idx < fwIdxHi) ? true : false;
	}

	static class FiniteSurface extends PointSurface {

		double zBot; // base of rupture; may be less than 14km
		double widthH; // horizontal width (surface projection)
		double widthDD; // down-dip width
		boolean footwall;

		FiniteSurface(Location loc) {
			super(loc);
		}

		@Override
		public Distances distanceTo(Location loc) {
			// TODO 0.5 is WUS specific and based on discretization of distances
			// in grid source GMM lookup tables

			// because we're not using table lookup optimizations, we push the
			// minimum rJB out to 0.5 (half the table bin-width)
			double rJB = Locations.horzDistanceFast(this.loc, loc);
			rJB *= PtSrcDistCorr.getCorrection(rJB, mag, PtSrcDistCorr.Type.NSHMP08);
			rJB = max(0.5, rJB);
			double rX = footwall ? -rJB : rJB + widthH;

			if (footwall) return Distances.create(rJB, hypot(rJB, zTop), rX);

			double dipRad = dip * TO_RAD;
			double rCut = zBot * tan(dipRad);

			if (rJB > rCut)
				return Distances.create(rJB, hypot(rJB, zBot), rX);

			// rRup when rJB is 0 -- we take the minimum the site-to-top-edge
			// and site-to-normal of rupture for the site being directly over
			// the down-dip edge of the rupture
			double rRup0 = min(hypot(widthH, zTop), zBot * cos(dipRad));
			// rRup at cutoff rJB
			double rRupC = zBot / cos(dipRad);
			// scale linearly with rJB distance
			double rRup = (rRupC - rRup0) * rJB / rCut + rRup0;

			return Distances.create(rJB, rRup, rX);
		}

		// @formatter:off
		@Override public double width() { return widthDD; }
		
	}
	
}
