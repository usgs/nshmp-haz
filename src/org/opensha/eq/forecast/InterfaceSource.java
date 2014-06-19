package org.opensha.eq.forecast;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.StandardSystemProperty.LINE_SEPARATOR;
import static org.opensha.eq.fault.Faults.validateTrace;
import static org.opensha.eq.forecast.FloatStyle.FULL_DOWN_DIP;

import java.util.List;

import org.opensha.eq.fault.scaling.MagScalingRelationship;
import org.opensha.eq.fault.surface.ApproxGriddedSurface;
import org.opensha.eq.fault.surface.GriddedSurface;
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
 * <p>A {@code InterfaceSource} can not be created directly; it may only be
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

		this.lowerTrace = lowerTrace;
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

	static class Builder extends FaultSource.Builder {

		private static final String ID = "InterfaceSource.Builder";

		// required
		private LocationList lowerTrace;

		// have defaults
		double aspectRatio = 1.0;
		double offset = 5.0;
		FloatStyle floatStyle = FULL_DOWN_DIP;

		Builder lowerTrace(LocationList trace) {
			checkNotNull(this.trace, "Upper trace must be set first");
			validateTrace(trace);
			checkArgument(this.trace.size() == trace.size(),
				"Upper and lower trace must be the same size");
			this.lowerTrace = trace;
			return this;
		}

		InterfaceSource buildSubductionSource() {

			// depth, dip and width will be computed lazily by subduction
			// surface implementation; set to NaN to satisfy builder
			this.depth = Double.NaN;
			this.dip = Double.NaN;
			this.width = Double.NaN;

			mfds = mfdsBuilder.build();

			checkState(lowerTrace != null, "%s lower trace not set", ID);
			validateState(ID);

			ApproxGriddedSurface surface = new ApproxGriddedSurface(trace, lowerTrace, offset);

			return new InterfaceSource(name, trace, lowerTrace, dip, width, surface, rake,
				ImmutableList.copyOf(mfds), msr, aspectRatio, offset, floatStyle);
		}

	}

}
