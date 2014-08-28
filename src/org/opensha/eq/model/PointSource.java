package org.opensha.eq.model;

import static java.lang.Math.ceil;
import static java.lang.Math.min;
import static java.lang.Math.sin;
import static org.opensha.eq.fault.FocalMech.NORMAL;
import static org.opensha.eq.fault.FocalMech.REVERSE;
import static org.opensha.eq.fault.FocalMech.STRIKE_SLIP;

import java.util.Iterator;
import java.util.Map;

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
 * <p><b>NOTE:</b> This source type should <i>not</i> be used in in conjunction
 * with ground motion models (GMMs) that consider hanging wall effects or
 * require more detailed distance metrics that are consistent with a
 * {@code Rupture}'s {@code FocalMech}, dip, and rake.</p>
 * 
 * <p><b>NOTE</b>: {@code PointSource}s are thread safe, however the
 * {@code Rupture}s returned by {@link Source#iterator()} are not.</p>
 * 
 * <p><b>NOTE</b>: {@link #size()} returns the absolute number of
 * {@code Rupture}s that can be created given the supplied source input
 * arguments; the iterator, however, <i>may</i> return fewer {@code Rupture}s as
 * some may have zero rates.</p>
 * 
 * @author Peter Powers
 */
class PointSource implements Source {

	final GridSourceSet parent;
	final Location loc;
	final IncrementalMfd mfd;
	final Map<FocalMech, Double> mechWtMap;

	int rupCount;
	int magDepthSize;
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

	@Override public String name() {
		return "PointSource: " + loc;
	}

	@Override public int size() {
		return rupCount;
	}

	/*
	 * NOTE/TODO: Should not be many instances where a PointSource.rupture rate
	 * is reduced to zero; not checked below such that rupture is skipped
	 */

	private void updateRupture(Rupture rup, int idx) {

		int magDepthIdx = idx % magDepthSize;
		int magIdx = parent.magDepthIndices.get(magDepthIdx);
		double mag = mfd.getX(magIdx);
		double rate = mfd.getY(magIdx);

		double zTop = parent.magDepthDepths.get(magDepthIdx);
		double zTopWt = parent.magDepthWeights.get(magDepthIdx);

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

	@Override public Iterator<Rupture> iterator() {
		return new Iterator<Rupture>() {
			Rupture rupture = new Rupture();
			{
				rupture.surface = new PointSurface(loc);
			}
			int size = size();
			int caret = 0;

			@Override public boolean hasNext() {
				if (caret >= size) return false;
				updateRupture(rupture, caret++);
				return (rupture.rate > 0.0) ? true : hasNext();
			}

			@Override public Rupture next() {
				return rupture;
			}

			@Override public void remove() {
				throw new UnsupportedOperationException();
			}
		};
	}

	void init() {

		/*
		 * Get the number of mag-depth iterations required to get to mMax. See
		 * explanation in GridSourceSet for how magDepthIndices is set up
		 */
		magDepthSize = parent.magDepthIndices.lastIndexOf(mfd.getNum() - 1) + 1;

		/*
		 * Init rupture indexing: SS RV NR. Each category will have ruptures for
		 * every mag in 'mfd' and depth in parent 'magDepthMap'.
		 */
		int ssCount = (int) ceil(mechWtMap.get(STRIKE_SLIP)) * magDepthSize;
		int revCount = (int) ceil(mechWtMap.get(REVERSE)) * magDepthSize;
		int norCount = (int) ceil(mechWtMap.get(NORMAL)) * magDepthSize;
		ssIdx = ssCount;
		revIdx = ssCount + revCount;

		rupCount = ssCount + revCount + norCount;
	}

	/*
	 * Returns the focal mechanism of the rupture at the supplied index.
	 */
	FocalMech mechForIndex(int idx) {
		// iteration order is always SS -> REV -> NOR
		return (idx < ssIdx) ? STRIKE_SLIP : (idx < revIdx) ? REVERSE : NORMAL;
	}

	private static final double MAX_DEPTH = 14.0;
	private static final double GENERIC_WIDTH = 8.0;

	/*
	 * TODO: revisit. This is very clunky. Point sources are used by crustal and
	 * slab sources. The default implementation here had assumed a maximum
	 * crustal eq depth of 14km, which is really only appropriate for active
	 * continental crust such as that in California. This yielded negative
	 * widths and unreasonable zHyp values when zTop > 14. That said, most Gmm's
	 * used for slab or stable continental crust ignore width and zHyp so there
	 * probably wouldn't be a problem.
	 * 
	 * As a stopgap, any supplied depth > 14km will return a width of 8km, a
	 * reasonable value for an earthquake nested in subducting oceanic crust.
	 * Otherwise, method returns the minimum of the aspect ratio width and the
	 * allowable down-dip width. Utility for use by subclasses.
	 */
	double calcWidth(double mag, double depth, double dipRad) {
		if (depth > MAX_DEPTH) return GENERIC_WIDTH;
		double length = parent.mlr.getMedianLength(mag);
		double aspectWidth = length / 1.5;
		double ddWidth = (14.0 - depth) / sin(dipRad);
		return min(aspectWidth, ddWidth);
	}

	static class PointSurface implements RuptureSurface {

		Location loc;
		double mag;
		double dip;
		double zTop;

		PointSurface(Location loc) {
			this.loc = loc;
		}

		@Override public Distances distanceTo(Location loc) {
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
