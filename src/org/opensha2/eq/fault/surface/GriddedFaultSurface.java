package org.opensha2.eq.fault.surface;

import static com.google.common.base.Preconditions.checkState;
import static org.opensha2.data.Data.checkInRange;
import static org.opensha2.eq.fault.Faults.validateDepth;
import static org.opensha2.eq.fault.Faults.validateDip;
import static org.opensha2.eq.fault.Faults.validateInterfaceWidth;
import static org.opensha2.eq.fault.Faults.validateStrike;
import static org.opensha2.eq.fault.Faults.validateTrace;
import static org.opensha2.geo.GeoTools.TO_RAD;

import org.opensha2.eq.fault.Faults;
import org.opensha2.geo.LocationGrid;
import org.opensha2.geo.LocationList;

import com.google.common.collect.Range;

/**
 * Add comments here
 *
 * @author Peter Powers
 */
class GriddedFaultSurface {

	private final LocationGrid grid;

	// TODO not sure these are really needed once surface is constructed
	private final double strikeSpacing;
	private final double dipSpacing;

	private GriddedFaultSurface(LocationGrid grid,
			double strikeSpacing,
			double dipSpacing) {

		this.grid = grid;
		this.strikeSpacing = strikeSpacing;
		this.dipSpacing = dipSpacing;
	}

	// public static GriddedFaultSurface create()

	/*
	 * Document that width, whether computed from top-bottom or assigned, is
	 * always then applied in the dip direction, either supplied or computed
	 * from trace.
	 */

	public Builder builder() {
		return new Builder();
	}

	/*
	 * TODO document builder which will almost certainly be part of a public API
	 * 
	 * TODO doc trace assumed to be at depth=0?
	 * 
	 * TODO do trace depths all need to be the same; condidtion used to be
	 * imposed in assertValidState
	 * 
	 * TODO surface is initialized with a dip direction in radians; this may be
	 * normal to Faults.strike(trace), but may not be; in any event, we do not
	 * want to recompute it internally.
	 * 
	 * TODO single-use builder
	 * 
	 * TODO right-hand-rule
	 * 
	 * TODO should surface only be a single row if width < dipSpacing/2
	 */
	public static class Builder {

		private static final Range<Double> SPACING_RANGE = Range.closed(0.01, 20.0);
		private static final String ID = "GriddedFaultSurface.Builder";

		private boolean built = false;

		// required
		private LocationList trace;
		private Double dipRad;
		private Double depth;

		// conditional (either but not both)
		private Double width;
		private Double lowerDepth;

		// optional - dipDir may not necessarily be normal to strike
		private Double dipDirRad;

		// optional with defualts
		private double strikeSpacing = 1.0;
		private double dipSpacing = 1.0;

		private Builder() {}

		public Builder trace(LocationList trace) {
			this.trace = validateTrace(trace);
			return this;
		}

		public Builder dip(double dip) {
			this.dipRad = validateDip(dip) * TO_RAD;
			return this;
		}

		public Builder dipDir(double dipDir) {
			this.dipDirRad = validateStrike(dipDir) * TO_RAD;
			return this;
		}

		public Builder depth(double depth) {
			this.depth = validateDepth(depth);
			return this;
		}

		public Builder lowerDepth(double lowerDepth) {
			checkState(width == null, "Either lower depth or width may be set, but not both");
			this.lowerDepth = validateDepth(lowerDepth);
			return this;
		}

		public Builder width(double width) {
			checkState(lowerDepth == null, "Either width or lower depth may be set, but not both");
			// we don't know what the surface may be used to represent
			// so we validate against the largest (interface) values
			this.width = validateInterfaceWidth(width);
			return this;
		}

		public Builder spacing(double spacing) {
			return spacing(spacing, spacing);
		}

		public Builder spacing(double strike, double dip) {
			this.strikeSpacing = checkInRange(SPACING_RANGE, "Strike Spacing", strike);
			this.dipSpacing = checkInRange(SPACING_RANGE, "Dip Spacing", dip);
			return this;
		}

		private void validateState(String id) {
			checkState(!built, "This %s instance as already been used", id);
			checkState(trace != null, "%s trace not set", id);
			checkState(dipRad != null, "%s dip not set", id);
			checkState(depth != null, "%s depth not set", id);

			checkState((width != null) ^ (lowerDepth != null), "%s width or lowerDepth not set", id);
			if (lowerDepth != null && lowerDepth <= depth) {
				throw new IllegalStateException("Lower depth is above upper depth");
			}
			built = true;
		}

		public DefaultGriddedSurface build() {
			validateState(ID);
			if (dipDirRad == null) dipDirRad = Faults.dipDirectionRad(trace);
			if (width == null) width = (lowerDepth - depth) / Math.sin(dipRad);
			return null;
			// new DefaultGriddedSurface(trace, dipRad, dipDirRad, depth, width,
			// strikeSpacing, dipSpacing);
		}

	}
}
