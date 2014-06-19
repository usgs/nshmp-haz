package org.opensha.eq.forecast;

import static com.google.common.base.Preconditions.checkPositionIndex;
import static com.google.common.base.Preconditions.checkState;
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

import java.util.EnumMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

import org.opensha.eq.fault.Faults;
import org.opensha.eq.fault.FocalMech;
import org.opensha.eq.fault.scaling.MagLengthRelationship;
import org.opensha.eq.fault.scaling.impl.WC1994_MagLengthRelationship;
import org.opensha.eq.fault.surface.PtSrcDistCorr;
import org.opensha.eq.forecast.PointSourceFinite.FiniteSurface;
import org.opensha.geo.Location;
import org.opensha.geo.LocationList;
import org.opensha.geo.LocationVector;
import org.opensha.geo.Locations;
import org.opensha.mfd.IncrementalMfd;
import org.opensha.mfd.Mfds;

import com.google.common.collect.ImmutableMap;
import com.google.common.math.DoubleMath;

/**
 * Fixed-strike point-source earthquake implementation. As with parent class,
 * all magnitudes are represented as finite faults and any normal or reverse
 * sources are represented with two possible geometries. However in these
 * representations, the trace is always located at the point {@code Location}
 * (TODO: add illustration or link).
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

	private final double strike;
	
	/**
	 * Constructs a new point earthquake source.
	 * @param loc <code>Location</code> of the point source
	 * @param mfd magnitude frequency distribution of the source
	 * @param magDepthMap specifies magnitude cutoffs and associated weights for
	 *        different depth-to-top-of-ruptures
	 * @param mechWtMap <code>Map</code> of focal mechanism weights
	 */
	PointSourceFixedStrike(GridSourceSet parent, Location loc,
		IncrementalMfd mfd, Map<FocalMech, Double> mechWtMap, double strike) {
		super(parent, loc, mfd, mechWtMap);
		this.strike = strike;
	}

	@Override
	public String name() {
		return "PointSourceFixedStrike: " + loc;
	}

	// TODO clean
	/*
	 * NOTE: Getting a Rupture by index is deliberately inefficient to ensure
	 * thread safety. A new immutable Rupture and internal FinitePointSurface
	 * are created on every call. Use Source.iterator() where possible.
	 */

//	@Override
//	public Rupture getRupture(int idx) {
//		checkPositionIndex(idx, size());
//		Rupture rupture = new Rupture();
//		rupture.surface = new FixedStrikeSurface(loc);
//		updateRupture(rupture, idx);
//		return rupture;
//	}

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
		double strikeRad = strike * TO_RAD;

		double widthDD = calcWidth(mag, zTop, dipRad);
		double widthH = widthDD * cos(dipRad);
		double zBot = zTop + widthDD * sin(dipRad);

		rup.mag = mag;
		rup.rake = mech.rake();
		rup.rate = rate * zTopWt * mechWt;

		FixedStrikeSurface fsSurf = (FixedStrikeSurface) rup.surface;
		fsSurf.mag = mag; // KLUDGY needed for distance correction
		fsSurf.dip = mech.dip();
		fsSurf.widthDD = widthDD;
		fsSurf.widthH = widthH;
		fsSurf.zTop = zTop;
		fsSurf.zBot = zBot;
		fsSurf.footwall = isOnFootwall(idx);
		
		double distToPoint = parent.mlr.getMedianLength(mag) / 2;
		Location locWithDepth = Location.create(loc.lat(), loc.lon(), zTop);
		LocationVector v1 = LocationVector.create(strikeRad, distToPoint, 0.0);
		LocationVector v2 = LocationVector.reverseOf(v1);

		Location p1 = Locations.location(locWithDepth, v1);
		Location p2 = Locations.location(locWithDepth, v2);
		
		// we don't know what the footwall is relative to loc, all that
		// matters is that the two representations be mirror images of
		// each other; isOnFootwall is ignored in surface implementation
		
		if (isOnFootwall(idx)) {
			fsSurf.p1 = p1;
			fsSurf.p2 = p2;
			if (mech == STRIKE_SLIP) {
				fsSurf.p3 = Location.create(p2.lat(), p2.lon(), zBot);
				fsSurf.p4 = Location.create(p1.lat(), p1.lon(), zBot);
			} else {
				double dipDirRad = Faults.dipDirectionRad(p1, p2);
				LocationVector vDownDip = LocationVector.create(dipDirRad, widthH, zBot - zTop);
				fsSurf.p3 = Locations.location(p2, vDownDip);
				fsSurf.p4 = Locations.location(p1, vDownDip);
			}
		} else {
			fsSurf.p1 = p2;
			fsSurf.p2 = p1;
			if (mech == STRIKE_SLIP) {
				fsSurf.p3 = Location.create(p1.lat(), p1.lon(), zBot);
				fsSurf.p4 = Location.create(p2.lat(), p2.lon(), zBot);
			} else {
				double dipDirRad = Faults.dipDirectionRad(p2, p1);
				LocationVector vDownDip = LocationVector.create(dipDirRad, widthH, zBot - zTop);
				fsSurf.p3 = Locations.location(p1, vDownDip);
				fsSurf.p4 = Locations.location(p2, vDownDip);
			}
		}
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

	static class FixedStrikeSurface extends FiniteSurface {

		// Four corners of rupture:
		// top trace: p1 --> p2
		// bot trace: p4 <-- p3
		Location p1, p2, p3, p4;
		
		// ignores 'widthDD' and 'mag' fields in parent
		
		FixedStrikeSurface(Location loc) {
			super(loc);
		}

		@Override
		public Distances distanceTo(Location loc) {
			// NOTE no NSHMP style distance corrections here
			
			double rX = Locations.distanceToLineFast(p1, p2, loc);
			double rSeg = Locations.distanceToSegmentFast(p1, p2, loc);
			
			// simple footwall case
			if (rX <= 0.0) return Distances.create(rSeg, hypot2(rSeg, zTop), rX);
			
			// otherwise, we're on the hanging wall...
			
			// compute rRup as though we're between endpoints
			double dipRad = dip * TO_RAD;
			double rCutTop = tan(dipRad) * zTop;
			double rCutBot = tan(dipRad) * zBot + widthH;
			double rRup = (rX > rCutBot) ? hypot2(rX - widthH, zBot) :
				          (rX < rCutTop) ? hypot2(rX, zTop) : 
				        	  (rX - rCutTop) / sin(dipRad);
			
			// test if we're normal to trace or past its endpoints
			boolean offEnd = DoubleMath.fuzzyCompare(rSeg, rX, 0.00001) > 0;
			
			if (offEnd) {
				// distance from surface projection of ends/caps of fault
				double rJB = min(
					Locations.distanceToSegmentFast(p1, p4, loc),
					Locations.distanceToSegmentFast(p2, p3, loc));
				double rY = sqrt(rSeg * rSeg + rX * rX);
				// rRup is the hypoteneuse of rRup (above) and rY
				return Distances.create(rJB, hypot2(rRup, rY), rX);
			}
			
			double rJB = (rX > widthH) ? rX - widthH : 0.0;
			return Distances.create(rJB, rRup, rX);
		}

		// @formatter:off
		@Override public double width() { return widthDD; }
		
	}
	
	public static void main(String[] args) {
		IncrementalMfd mfd = Mfds.newGutenbergRichterMFD(5.0, 0.5, 7, 1.0, 1.0);
		Location loc = Location.create(34.0, -118.0, 5.0);
		Map<FocalMech, Double> mechMap = ImmutableMap.of(STRIKE_SLIP, 1.0, NORMAL, 0.0, REVERSE, 0.0);
		
		
	}
}
