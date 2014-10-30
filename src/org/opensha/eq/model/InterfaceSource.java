package org.opensha.eq.model;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.StandardSystemProperty.LINE_SEPARATOR;
import static org.opensha.eq.fault.Faults.validateInterfaceDepth;
import static org.opensha.eq.fault.Faults.validateTrace;
import static org.opensha.eq.fault.Faults.validateInterfaceWidth;
import static org.opensha.eq.model.FloatStyle.FULL_DOWN_DIP;

import java.util.List;

import org.opensha.eq.fault.scaling.MagScalingRelationship;
import org.opensha.eq.fault.surface.ApproxGriddedSurface;
import org.opensha.eq.fault.surface.GriddedSurface;
import org.opensha.eq.fault.surface.GriddedSurfaceWithSubsets;
import org.opensha.geo.LocationList;
import org.opensha.mfd.IncrementalMfd;

import com.google.common.collect.ImmutableList;

/**
 * Subduction source representation. This class wraps a model of a subduction
 * interface geometry and a list of magnitude frequency distributions that
 * characterize how the fault might rupture (e.g. as one, single
 * geometry-filling event, or as multiple smaller events) during earthquakes.
 * Smaller events are modeled as 'floating' ruptures; they occur in multiple
 * locations on the fault surface with appropriately scaled rates.
 * 
 * <p>An {@code InterfaceSource} cannot be created directly; it may only be
 * created by a private parser.</p>
 * 
 * @author Peter Powers
 */
public class InterfaceSource extends FaultSource {

	final LocationList lowerTrace;

	private InterfaceSource(String name, LocationList upperTrace, LocationList lowerTrace,
		double dip, double width, GriddedSurface surface, double rake, List<IncrementalMfd> mfds,
		MagScalingRelationship msr, double aspectRatio, double offset, FloatStyle floatStyle) {

		super(name, upperTrace, dip, width, surface, rake, mfds, msr, aspectRatio, offset,
			floatStyle);

		this.lowerTrace = (lowerTrace == null) ? surface.getEvenlyDiscritizedLowerEdge()
			: lowerTrace;
		
		// TODO lowerTrace may be null and this is bad bad; lowerTrace
		// is referenced in InterfaceSourceSet distanceFilter and
		// we should populate this even if the original source only 
		// specified an upper trace. This highlights another shortcoming
		// of Container2D and GriddedSurface: why is there no getRow(int)
		// of getBottomRow() given that there is a getUpperEdge(),
		// acknowledging that the latter may not be the same as the trace
		// due to seismogenic depth constraints. For now, we are ignoring
		// lower trace in distance filter, but given large width of interface
		// sources TODO clean up Container2D methods
		
	}

	@Override public String toString() {
		// TODO use joiner
		// @formatter:off
		return new StringBuilder()
		.append("==========  Interface Source  ==========")
		.append(LINE_SEPARATOR.value())
		.append("  Source name: ").append(name())
		.append(LINE_SEPARATOR.value())
		.append("          dip: ").append(dip)
		.append(LINE_SEPARATOR.value())
		.append("        width: ").append(width)
		.append(LINE_SEPARATOR.value())
		.append("         rake: ").append(rake)
		.append(LINE_SEPARATOR.value())
		.append("         mfds: ").append(mfds.size())
		.append(LINE_SEPARATOR.value())
		.append("          top: ").append(trace.first().depth())
		.append(LINE_SEPARATOR.value()).toString();
		// @formatter:on
	}

	/* Single use builder. */
	static class Builder extends FaultSource.Builder {

		private static final String ID = "InterfaceSource.Builder";

		// required
		private LocationList lowerTrace;

		// have defaults
		double aspectRatio = 1.0;
		double offset = 5.0;
		FloatStyle floatStyle = FULL_DOWN_DIP;

		@Override
		Builder depth(double depth) {
			this.depth = validateInterfaceDepth(depth);
			return this;
		}
		
		@Override
		Builder width(double width) {
			this.width = validateInterfaceWidth(width);
			return this;
		}

		Builder lowerTrace(LocationList trace) {
			checkNotNull(this.trace, "Upper trace must be set first");
			validateTrace(trace);
			checkArgument(this.trace.size() == trace.size(),
				"Upper and lower trace must be the same size");
			this.lowerTrace = trace;
			return this;
		}

		InterfaceSource buildSubductionSource() {

			/*
			 * Either upper and lower trace must be set, or upper trace depth,
			 * dip, and width. If upper and lower are set, depth, dip and width
			 * (if also set) will ultimately be ignored and computed lazily by
			 * the subduction surface implementation; otherwise the three fields
			 * are set to NaN to satisfy parent builder.
			 */

			mfds = mfdsBuilder.build();

			GriddedSurface surface = null;

			if (lowerTrace != null) {

				// if going dual-trace route, satisfy parent builder with NaNs
				this.depth = Double.NaN;
				this.dip = Double.NaN;
				this.width = Double.NaN;
				validateState(ID);
				surface = new ApproxGriddedSurface(trace, lowerTrace, offset);

			} else {

				// otherwise build a basic fault source @formatter:off
				validateState(ID);
				surface = GriddedSurfaceWithSubsets.builder()
					.trace(trace)
					.depth(depth)
					.dip(dip)
					.width(width)
					.spacing(offset)
					.build();

			}

			return new InterfaceSource(name, trace, lowerTrace, dip, width, surface, rake,
				ImmutableList.copyOf(mfds), msr, aspectRatio, offset, floatStyle);
		}

	}

}
