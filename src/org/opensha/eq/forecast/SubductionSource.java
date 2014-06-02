package org.opensha.eq.forecast;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.StandardSystemProperty.LINE_SEPARATOR;
import static org.opensha.eq.fault.Faults.*;
import static org.opensha.eq.forecast.FloatStyle.FULL_DOWN_DIP;

import java.util.Iterator;
import java.util.List;

import org.opensha.eq.fault.scaling.MagScalingRelationship;
import org.opensha.eq.fault.scaling.impl.GeoMat_MagLenthRelationship;
import org.opensha.eq.fault.surface.ApproxEvenlyGriddedSurface;
import org.opensha.eq.fault.surface.EvenlyGriddedSurface;
import org.opensha.geo.Location;
import org.opensha.geo.LocationList;
import org.opensha.mfd.IncrementalMFD;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

/**
 * Subduction source representation. This class wraps a model of a subduction interface geometry and
 * a list of magnitude frequency distributions that characterize how the fault
 * might rupture (e.g. as one, single geometry-filling event, or as multiple
 * smaller events) during earthquakes. Smaller events are modeled as 'floating'
 * ruptures; they occur in multiple locations on the fault surface with
 * appropriately scaled rates.
 * 
 * <p>A subduction source can not be created directly; it may only be created by a
 * private parser.</p>
 * 
 * @author Peter Powers
 */
public class SubductionSource extends FaultSource {

	final LocationList lowerTrace;
	ApproxEvenlyGriddedSurface surface;

	private SubductionSource(String name, LocationList upperTrace, LocationList lowerTrace,
		double dip, double width, double rake, List<IncrementalMFD> mfds,
		MagScalingRelationship msr, double aspectRatio, double offset, FloatStyle floatStyle) {
		
		super(name, upperTrace, dip, width, rake, mfds, msr, aspectRatio, offset, floatStyle);
		this.lowerTrace = lowerTrace;
		
		// TODO this is all messed up; Stirling surface in parent is being used
		// to create ruptures; need to create surface in advance via builder
	}

	void init() {
		surface = new ApproxEvenlyGriddedSurface(trace, lowerTrace, 5.0);
		
	}


	@Override
	public double getMinDistance(Location loc) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Iterator<Rupture> iterator() {
		// @formatter:off
		return new Iterator<Rupture>() {
			int size = size();
			int caret = 0;
			@Override public boolean hasNext() {
				return caret < size;
			}
			@Override public Rupture next() {
				return getRupture(caret++);
			}
			@Override public void remove() {
				throw new UnsupportedOperationException();
			}
		};
		// @formatter:on
	}
	
	@Override
	public String toString() {
		// TODO use joiner
		// @formatter:off
		return new StringBuilder()
		.append("==========  Subduction Source  ==========")
		.append(LINE_SEPARATOR.value())
		.append("  Source name: ").append(name())
		.append(LINE_SEPARATOR.value())
		.append("          dip: ").append(dip)
		.append(LINE_SEPARATOR.value())
		.append("        width: ").append(width)
		.append(LINE_SEPARATOR.value())
		.append("         rake: ").append(rake)
//		.append(LINE_SEPARATOR.value())
//		.append("         mags: ").append(nMag)
		.append(LINE_SEPARATOR.value())
		.append("         mfds: ").append(mfds.size())
		.append(LINE_SEPARATOR.value())
		.append("          top: ").append(trace.first().depth())
		.append(LINE_SEPARATOR.value()).toString();
		// @formatter:on
	}

	/**
	 * Returns the list of magnitude frequency distributions that this source
	 * represents
	 * @return the source MFD's
	 */
	public List<IncrementalMFD> getMFDs() {
		return mfds;
	}

	static class Builder extends FaultSource.Builder {
		
		private static final String ID = "SubductionSource.Builder";
		
		// reuired
		private LocationList lowerTrace;

		// have defaults
		double aspectRatio = 1.0;
		double offset = 5.0;
		FloatStyle floatStyle = FULL_DOWN_DIP;

		Builder lowerTrace(LocationList trace) {
			checkArgument(
				checkNotNull(trace, "Trace may not be null").size() > 1,
				"Trace must have at least 2 points");
			this.lowerTrace = trace;
			return this;
		}
		
		SubductionSource buildSubductionSource() {
			checkState(trace != null, "%s trace not set", ID);
			validateState(ID);
			return new SubductionSource(name, trace, lowerTrace, dip, width, rake,
				ImmutableList.copyOf(mfds), msr, aspectRatio, offset, floatStyle);
		}

	}

}
