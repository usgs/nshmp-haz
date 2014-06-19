package org.opensha.eq.forecast;

import static com.google.common.base.Preconditions.checkPositionIndex;
import static java.lang.Math.ceil;
import static java.lang.Math.min;
import static java.lang.Math.sin;
import static java.lang.Math.sqrt;
import static org.opensha.eq.fault.FocalMech.NORMAL;
import static org.opensha.eq.fault.FocalMech.REVERSE;
import static org.opensha.eq.fault.FocalMech.STRIKE_SLIP;

import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

import org.opensha.eq.fault.FocalMech;
import org.opensha.eq.fault.surface.PtSrcDistCorr;
import org.opensha.eq.fault.surface.RuptureSurface;
import org.opensha.geo.Location;
import org.opensha.geo.Locations;
import org.opensha.mfd.IncrementalMfd;

/**
 * Point-source earthquake {@code Source} supplies the simplest possible
 * representation of point-source {@code Rupture}s. When iterating, a
 * {@code PointSource} will supply {@code Rupture}s that provide dips and rakes
 * corresponding to different {@link FocalMech} types, but all distance metrics
 * will be equivalent (rJB = rRup = rX).
 * 
 * <p><b>NOTE:</b> This source type should not be used in in conjunction with
 * ground motion models (GMMs) that consider hanging wall effects or require
 * more detailed distance metrics that are consistent with a {@code Rupture}'s
 * {@code FocalMech}, dip, and rake.</p>
 * 
 * <p><b>NOTE</b>:The {@link PointSource#getRupture(int)} method is thread safe,
 * however, it is inefficient in that it creates a new {@link Rupture} on every
 * call. Use of {@link Source#iterator()} is preferred, but {@code Rupture}
 * instances returned by the iterator should <i>not</i> be retained and an
 * iterator instance should <i>only</i> ever be used by a single thread.</p>
 * 
 * <p><b>NOTE</b>: {@code Source.size()} returns the absolute number of
 * {@code Rupture}s that can be created given the supplied source input
 * arguments; the iterator, however, <i>may</i> return fewer {@code Rupture}s as
 * some may have zero rates or zero weights.</p>
 * 
 * @author Peter Powers
 */
class PointSource implements Source {

	final GridSourceSet parent;
	final Location loc;
	final IncrementalMfd mfd;
	final Map<FocalMech, Double> mechWtMap;

	int rupCount;
	int magDepthCount;
	int ssIdx, revIdx;

	/**
	 * Constructs a new point earthquake source.
	 * @param loc <code>Location</code> of the point source
	 * @param mfd magnitude frequency distribution of the source
	 * @param magDepthMap specifies magnitude cutoffs and associated weights for
	 *        different depth-to-top-of-ruptures
	 * @param mechWtMap <code>Map</code> of focal mechanism weights
	 */
	PointSource(GridSourceSet parent, Location loc, IncrementalMfd mfd,
		Map<FocalMech, Double> mechWtMap) {
		this.parent = parent;
		this.loc = loc;
		this.mfd = mfd;
		this.mechWtMap = mechWtMap;
		init();
	}

	@Override
	public String name() {
		return "PointSource: " + loc;
	}

	@Override
	public int size() {
		return rupCount;
	}

	// TODO clean
	/*
	 * NOTE: Getting a Rupture by index is deliberately inefficient to ensure
	 * thread safety. A new immutable Rupture and internal PointSurface are
	 * created on every call. Use Source.iterator() where possible.
	 */

//	@Override
//	public Rupture getRupture(int idx) {
//		checkPositionIndex(idx, size());
//		Rupture rupture = new Rupture();
//		rupture.surface = new PointSurface(loc);
//		updateRupture(rupture, idx);
//		return rupture;
//	}

	/*
	 * NOTE/TODO: Although there should not be many instances where a
	 * PointSource.rupture rate is reduced to zero (a mag-depth weight
	 * could be set to zero [this is not currently checked] of an MFD rate could
	 * be zero), in the cases where it is, we're doing a little more work than
	 * necessary below. We could alternatively short-circuit updateRupture()
	 * this method to return null reference, but would need to condsider
	 * getRupture(int) implementation.
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

		rup.mag = mag;
		rup.rake = mech.rake();
		rup.rate = rate * zTopWt * mechWt;

		PointSurface pSurf = (PointSurface) rup.surface;
		pSurf.mag = mag; // KLUDGY needed for distance correction
		pSurf.dip = mech.dip();
		pSurf.zTop = zTop;

	}

	@Override
	public Iterator<Rupture> iterator() {
		// @formatter:off
		return new Iterator<Rupture>() {
			Rupture rupture = new Rupture();
			{ rupture.surface = new PointSurface(loc); }
			int caret = 0;
			@Override public boolean hasNext() {
				if (caret > rupCount) return false;
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

		/* Get the total number of mag-depth combinations from parent */
		magDepthCount = parent.magDepthIndices.length;

		/*
		 * Init rupture indexing: SS RV NR. Each category will have ruptures for
		 * every mag in 'mfd' and depth in parent 'magDepthMap'.
		 */
		int ssCount = (int) ceil(mechWtMap.get(STRIKE_SLIP)) * magDepthCount;
		int revCount = (int) ceil(mechWtMap.get(REVERSE)) * magDepthCount;
		int norCount = (int) ceil(mechWtMap.get(NORMAL)) * magDepthCount;
		ssIdx = ssCount;
		revIdx = ssCount + revCount;

		rupCount = ssCount + ssCount + norCount;
		
		// TODO clean
		// below wasn't correct; this simple implementation doesn't consider
		// hanging wall vs footwall representations on the assumption that
		// in 2008 the gird source hw effect approximations were used for CB and CY
		
		/*
		 * Init focal mech counts: Total focal mech representations required,
		 * double counting reverse and normal mechs because they will have both
		 * hanging wall and footwall representations.
		 */
//		int mechCount = 0;
//		for (FocalMech mech : mechWtMap.keySet()) {
//			double wt = mechWtMap.get(mech);
//			if (wt == 0.0) continue;
//			mechCount += (mech == STRIKE_SLIP) ? 1 : 2;
//		}
//		rupCount = magDepthCount * mechCount;

	}

	/*
	 * Returns the focal mechanism of the rupture at the supplied index.
	 */
	FocalMech mechForIndex(int idx) {
		// iteration order is always SS -> REV -> NOR
		return (idx < ssIdx) ? STRIKE_SLIP : (idx < revIdx) ? REVERSE : NORMAL;
	}
	
	/*
	 * Returns the minimum of the aspect ratio width and the allowable down-dip
	 * width. Utility for use by subclasses.
	 */
	double calcWidth(double mag, double depth, double dipRad) {
		double length = parent.mlr.getMedianLength(mag);
		double aspectWidth = length / 1.5;
		double ddWidth = (14.0 - depth) / sin(dipRad);
		return min(aspectWidth, ddWidth);
	}

	/*
	 * Same as {@code Math.hypot()} without regard to under/over flow.
	 */
	static final double hypot2(double v1, double v2) {
		return sqrt(v1 * v1 + v2 * v2);
	}


	static class PointSurface implements RuptureSurface {

		Location loc;
		double mag;
		double dip;
		double zTop;

		PointSurface(Location loc) {
			this.loc = loc;
		}

		@Override
		public Distances distanceTo(Location loc) {
			double r = Locations.horzDistanceFast(this.loc, loc);
			r *= PtSrcDistCorr.getCorrection(r, mag, PtSrcDistCorr.Type.NSHMP08);
			return Distances.create(r, r, r);
		}

		// @formatter:off
		@Override public double strike() { throw new UnsupportedOperationException(exMessage("strike")); }
		@Override public double dip() { return dip; }
		@Override public double dipDirection() { throw new UnsupportedOperationException(exMessage("dipDirection")); }
		@Override public double length() { throw new UnsupportedOperationException(exMessage("length")); }
		@Override public double width() { throw new UnsupportedOperationException(exMessage("width")); }
		@Override public double area() { throw new UnsupportedOperationException(exMessage("area")); }
		@Override public double depth() { return zTop; }
		// TODO should this be the true centroid of the surface
		// representation or is the grid node location ok?
		@Override public Location centroid() { return loc; } 
		
		private static String exMessage(String field) {
			return "No '" + field + "' for PointSource surface";
		}
		
	}
	
}
