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
import java.util.Map;
import java.util.NoSuchElementException;

import org.opensha.eq.fault.FocalMech;
import org.opensha.eq.fault.scaling.MagLengthRelationship;
import org.opensha.eq.fault.scaling.impl.WC1994_MagLengthRelationship;
import org.opensha.eq.fault.surface.PtSrcDistCorr;
import org.opensha.geo.Location;
import org.opensha.geo.Locations;
import org.opensha.mfd.IncrementalMFD;

/**
 * Fixed-strike point-source earthquake implementation. As with parent class,
 * all magnitudes are represented as finite faults and any normal or reverse
 * sources are represented with two possible geometries, one dipping towards the
 * observer and one dipping away. However in these representations, the trace is
 * always located at the point {@code Location} (TODO: add illustration or
 * link).
 * 
 * <p>The {@link PointSourceFixedStrike#getRupture(int)} method is thread safe,
 * however, it is inefficient in that it creates a new {@link Rupture} on every
 * call. Use of {@link Source#iterator()} is preferred, but {@code Rupture}
 * instances returned by the iterator should <i>not</i> be retained and an
 * iterator instance should only ever be used by a single thread.</p>
 * 
 * <p><b>NOTE</b>: See {@link PointSource} description for notes on thread
 * safety and {@code Rupture} creation and iteration.</p>
 * 
 * @author Peter Powers
 */
class PointSourceFixedStrike extends PointSourceFinite {

	// TODO a similar implementation in which the centroids of the finite faults
	// are coincident with the source location should be considered

	/**
	 * Constructs a new point earthquake source.
	 * @param loc <code>Location</code> of the point source
	 * @param mfd magnitude frequency distribution of the source
	 * @param magDepthMap specifies magnitude cutoffs and associated weights for
	 *        different depth-to-top-of-ruptures
	 * @param mechWtMap <code>Map</code> of focal mechanism weights
	 */
	PointSourceFixedStrike(GridSourceSet parent, Location loc,
		IncrementalMFD mfd, Map<FocalMech, Double> mechWtMap) {
		super(parent, loc, mfd, mechWtMap);
	}

	@Override
	public String name() {
		return "PointSourceFixedStrike: " + loc;
	}

	/*
	 * NOTE: Getting a Rupture by index is deliberately inefficient to ensure
	 * thread safety. A new immutable Rupture and internal FinitePointSurface
	 * are created on every call. Use Source.iterator() where possible.
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

		FixedStrikeSurface fpSurf = (FixedStrikeSurface) rup.surface;
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
	 * Returns whether the rupture at index should be on the footwall (i.e. have
	 * its rX value set negative). Strike-slip mechs are marked as footwall to
	 * potentially short circuit GMPE calcs. Because the index order is SS-FW
	 * RV-FW RV-HW NR-FW NR-HW
	 */
	private boolean isOnFootwall(int idx) {
		return (idx < fwIdxLo) ? true : (idx < revIdx) ? false
			: (idx < fwIdxHi) ? true : false;
	}

	static class FixedStrikeSurface extends FiniteSurface {

		Location p1;
		Location p2;
		
		// TODO see RectangularSurface
		
		FixedStrikeSurface(Location loc) {
			super(loc);
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
		@Override public double width() { return widthDD; }
		
	}
	
}
