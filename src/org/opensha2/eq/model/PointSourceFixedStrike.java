package org.opensha2.eq.model;

import static java.lang.Math.cos;
import static java.lang.Math.min;
import static java.lang.Math.sin;
import static java.lang.Math.sqrt;
import static java.lang.Math.tan;
import static org.opensha2.eq.fault.FocalMech.STRIKE_SLIP;
import static org.opensha2.geo.GeoTools.TO_RAD;
import static org.opensha2.util.MathUtils.hypot;

import java.util.Iterator;
import java.util.Map;

import org.opensha2.eq.fault.Faults;
import org.opensha2.eq.fault.FocalMech;
import org.opensha2.eq.fault.scaling.MagLengthRelationship;
import org.opensha2.eq.fault.surface.RuptureScaling;
import org.opensha2.eq.fault.surface.RuptureScaling.Dimensions;
import org.opensha2.eq.model.PointSource.DepthModel;
import org.opensha2.geo.Location;
import org.opensha2.geo.LocationVector;
import org.opensha2.geo.Locations;
import org.opensha2.mfd.IncrementalMfd;

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

	// TODO store this natively in radians
	private final double strike;

	/**
	 * Constructs a new point earthquake source.
	 * @param loc <code>Location</code> of the point source
	 * @param mfd magnitude frequency distribution of the source
	 * @param magDepthMap specifies magnitude cutoffs and associated weights for
	 *        different depth-to-top-of-ruptures
	 * @param mechWtMap <code>Map</code> of focal mechanism weights
	 */
	PointSourceFixedStrike(Location loc, IncrementalMfd mfd, Map<FocalMech, Double> mechWtMap,
		RuptureScaling rupScaling, DepthModel depthModel, double strike) {
		super(loc, mfd, mechWtMap, rupScaling, depthModel);
		this.strike = strike;
	}

	@Override public String name() {
		return "PointSourceFixedStrike: " + loc;
	}

	/*
	 * NOTE/TODO: Although there should not be many instances where a
	 * PointSourceFixedStrike rupture rate is reduced to zero (a mag-depth
	 * weight could be set to zero [this is not curently checked] of an MFD rate
	 * could be zero), in the cases where it is, we're doing a little more work
	 * than necessary below. We could alternatively short-circuit
	 * updateRupture() this method to return null reference, but would need to
	 * condsider getRUpture(int) implementation.
	 */

	private void updateRupture(Rupture rup, int idx) {

		int magDepthIdx = idx % magDepthSize;
		int magIdx = depthModel.magDepthIndices.get(magDepthIdx);
		double mag = mfd.getX(magIdx);
		double rate = mfd.getY(magIdx);

		double zTop = depthModel.magDepthDepths.get(magDepthIdx);
		double zTopWt = depthModel.magDepthWeights.get(magDepthIdx);

		FocalMech mech = mechForIndex(idx);
		double mechWt = mechWtMap.get(mech);
		if (mech != STRIKE_SLIP) mechWt *= 0.5;
		double dipRad = mech.dip() * TO_RAD;
		double strikeRad = strike * TO_RAD;

		double maxWidthDD = (depthModel.maxDepth - zTop) / sin(dipRad);
		Dimensions dimensions = rupScaling.dimensions(mag, maxWidthDD);
		double widthDD = dimensions.width;
		double widthH = widthDD * cos(dipRad);
		double zBot = zTop + widthDD * sin(dipRad);

		rup.mag = mag;
		rup.rake = mech.rake();
		rup.rate = rate * zTopWt * mechWt;

		FixedStrikeSurface fsSurf = (FixedStrikeSurface) rup.surface;
		fsSurf.mag = mag; // KLUDGY needed for distance correction
		fsSurf.dipRad = dipRad;
		fsSurf.widthDD = widthDD;
		fsSurf.widthH = widthH;
		fsSurf.zTop = zTop;
		fsSurf.zBot = zBot;
		fsSurf.footwall = isOnFootwall(idx);

		double distToEndpoint = dimensions.length / 2;
		Location locWithDepth = Location.create(loc.lat(), loc.lon(), zTop);
		LocationVector v1 = LocationVector.create(strikeRad, distToEndpoint, 0.0);
		LocationVector v2 = LocationVector.reverseOf(v1);

		Location p1 = Locations.location(locWithDepth, v1);
		Location p2 = Locations.location(locWithDepth, v2);

		if (fsSurf.footwall) {
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
				// TODO We shouldn't ever get here as footwall should be true
				// for all STRIKE_SLIP
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

	@Override public Iterator<Rupture> iterator() {
		return new Iterator<Rupture>() {
			Rupture rupture = new Rupture();
			{
				rupture.surface = new FixedStrikeSurface(loc, rupScaling);
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

	static class FixedStrikeSurface extends FiniteSurface {

		// Four corners of rupture:
		// top trace: p1 --> p2
		// bot trace: p4 <-- p3
		Location p1, p2, p3, p4;

		// ignores 'widthDD' and 'mag' fields in parent

		FixedStrikeSurface(Location loc, RuptureScaling rupScaling) {
			super(loc, rupScaling);
		}

		/*
		 * NOTE the footwall flag in parent can not be used here. In parent,
		 * there's not strict relation between the site location and the
		 * geometry of the fault, we just need to have two pseudo
		 * representations for dipping faults.
		 * 
		 * Here, dipping faults will have two real representations (defined by
		 * corner Locations) and one will be on the footwall and one won't. When
		 * initializing the surface (above), the footwall flag is used to create
		 * the two mirror image surfaces, but which one is actually the footwall
		 * representation relative to the site location is unknown until distance
		 * calculations are started.
		 */

		@Override public Distance distanceTo(Location loc) {
			// NOTE no NSHMP style distance corrections here

			double rX = Locations.distanceToLineFast(p1, p2, loc);
			double rSeg = Locations.distanceToSegmentFast(p1, p2, loc);

			// simple footwall case
			boolean isVertical = (dipRad == 90.0 * TO_RAD);
			if (rX <= 0.0 || isVertical) return Distance.create(rSeg, hypot(rSeg, zTop), rX);

			// otherwise, we're on the hanging wall...

			// compute rRup as though we're between endpoints
			double rCutTop = tan(dipRad) * zTop;
			double rCutBot = tan(dipRad) * zBot + widthH;
			double rRup = (rX > rCutBot) ? hypot(rX - widthH, zBot) : (rX < rCutTop) ? hypot(rX,
				zTop) : hypot(rCutTop, zTop) + (rX - rCutTop) * sin(dipRad);

			// test if we're normal to trace or past its endpoints
			boolean offEnd = DoubleMath.fuzzyCompare(rSeg, rX, 0.00001) > 0;

			if (offEnd) {
				// distance from surface projection of ends/caps of fault
				double rJB = min(Locations.distanceToSegmentFast(p1, p4, loc),
					Locations.distanceToSegmentFast(p2, p3, loc));
				double rY = sqrt(rSeg * rSeg - rX * rX);
				// rRup is the hypoteneuse of rRup (above) and rY
				return Distance.create(rJB, hypot(rRup, rY), rX);
			}

			double rJB = (rX > widthH) ? rX - widthH : 0.0;
			return Distance.create(rJB, rRup, rX);
		}

		// @formatter:off
		@Override public double width() { return widthDD; }
		
	}
	
}
