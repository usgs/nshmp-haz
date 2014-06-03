package org.opensha.eq.forecast;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkElementIndex;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.StandardSystemProperty.LINE_SEPARATOR;
import static org.opensha.eq.fault.Faults.validateDip;
import static org.opensha.eq.fault.Faults.validateRake;
import static org.opensha.eq.fault.Faults.validateWidth;
import static org.opensha.eq.forecast.FloatStyle.CENTERED;
import static org.opensha.eq.forecast.FloatStyle.FULL_DOWN_DIP;

import java.util.Iterator;
import java.util.List;

import org.opensha.data.DataUtils;
import org.opensha.eq.fault.scaling.MagAreaRelationship;
import org.opensha.eq.fault.scaling.MagLengthRelationship;
import org.opensha.eq.fault.scaling.MagScalingRelationship;
import org.opensha.eq.fault.surface.GriddedSurface;
import org.opensha.eq.fault.surface.RuptureSurface;
import org.opensha.eq.fault.surface.GriddedSurfaceWithSubsets;
import org.opensha.geo.GeoTools;
import org.opensha.geo.Location;
import org.opensha.geo.LocationList;
import org.opensha.mfd.IncrementalMFD;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Range;

/**
 * Fault source representation. This class wraps a model of a fault geometry and
 * a list of magnitude frequency distributions that characterize how the fault
 * might rupture (e.g. as one, single geometry-filling event, or as multiple
 * smaller events) during earthquakes. Smaller events are modeled as 'floating'
 * ruptures; they occur in multiple locations on the fault surface with
 * appropriately scaled rates.
 * 
 * <p>A fault source can not be created directly; it may only be created by a
 * private parser.</p>
 * 
 * @author Peter Powers
 */
public class FaultSource implements Source {
	
	// TODO revisit to determine which fields
	// should be obtained from surface
	
	final String name;
	final LocationList trace;
	final double dip;
	final double width;
	final double rake;
	final List<IncrementalMFD> mfds;
	final MagScalingRelationship msr;
	final double aspectRatio;  // for floating ruptures
	final double offset;       // of floating ruptures
	final FloatStyle floatStyle;
	final GriddedSurface surface;
	
	private final List<List<Rupture>> ruptureLists; // 1:1 with MFDs
	private final List<Integer> rupCount;           // cumulative index list for iterating ruptures
	private int size = 0;
	
	FaultSource(String name, LocationList trace, double dip, double width,
		GriddedSurface surface, double rake, List<IncrementalMFD> mfds,
		MagScalingRelationship msr, double aspectRatio, double offset, FloatStyle floatStyle) {
		
		this.name = name;
		this.trace = trace;
		this.dip = dip;
		this.width = width;
		this.surface = surface;
		this.rake = rake;
		this.mfds = mfds;
		this.msr = msr;
		this.aspectRatio = aspectRatio;
		this.offset = offset;
		this.floatStyle = floatStyle;
		
		ruptureLists = Lists.newArrayList();
		rupCount = Lists.newArrayList();
		rupCount.add(0);
		initRuptures();
	}
	
	private void initRuptures() {
		for (IncrementalMFD mfd : mfds) {
			List<Rupture> rupList = createRuptureList(mfd);
			ruptureLists.add(rupList);
			size += rupList.size();
			rupCount.add(size);
			
		}
		checkState(size > 0, "FaultSource has no ruptures");
	}

	@Override
	public double getMinDistance(Location loc) {
		throw new UnsupportedOperationException();
	}

	@Override
	public int size() {
		return size;
	}

	@Override
	public Rupture getRupture(int idx) {
		checkElementIndex(idx, rupCount.get(rupCount.size()));
		// zero is built in to rupCount array; unless a negative idx is
		// supplied, if statement below should never be entered on first i
		for (int i = 0; i < rupCount.size(); i++) {
			if (idx < rupCount.get(i)) {
				return ruptureLists.get(i-1).get(idx - rupCount.get(i-1));
			}
		}
		//TODO test
		throw new IllegalStateException("We shouldn't be here... ever.");
	}
	
	@Override
	public Iterator<Rupture> iterator() {
		// @formatter:off
		return new Iterator<Rupture>() {
			int size = size();
			int caret = 0;
			@Override public boolean hasNext() { return caret < size; }
			@Override public Rupture next() { return getRupture(caret++); }
			@Override public void remove() { throw new UnsupportedOperationException(); }
		};
		// @formatter:on
	}
	
	@Override
	public String name() {
		return name;
	}
	
	@Override
	public String toString() {
		// TODO use joiner
		// @formatter:off
		return new StringBuilder()
		.append("==========  Fault Source  ==========")
		.append(LINE_SEPARATOR.value())
		.append("  Source name: ").append(name)
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

	private List<Rupture> createRuptureList(IncrementalMFD mfd) {
		List<Rupture> ruptures = Lists.newArrayList();
		
		// @formatter:off
		for (int i = 0; i < mfd.getNum(); ++i) {
			double mag = mfd.getX(i);
			double rate = mfd.getY(i);

			if (rate < 1e-14) continue; // shortcut low rates

			if (mfd.floats()) {

				// rupture dimensions
				double maxWidth = surface.width();
				double length = computeRuptureLength(msr, mag, maxWidth, aspectRatio);
				double width = Math.min(length / aspectRatio, maxWidth);

				// 2x width ensures full down-dip rupture
				if (floatStyle == FULL_DOWN_DIP) {
					width = 2 * maxWidth;
				}

				GriddedSurfaceWithSubsets surf = (GriddedSurfaceWithSubsets) surface;
				
				// rupture count
				double numRup = (floatStyle != CENTERED) ?
					surf.getNumSubsetSurfaces(length, width, offset) :
					surf.getNumSubsetSurfacesAlongLength(length, offset);

				for (int r = 0; r < numRup; r++) {
					RuptureSurface floatingSurface = (floatStyle != CENTERED) ?
						surf.getNthSubsetSurface(length, width, offset, r) :
						surf.getNthSubsetSurfaceCenteredDownDip(length, width, offset, r);
					double rupRate = rate / numRup;
					Rupture rup = Rupture.create(mag, rake, rupRate, floatingSurface);
					ruptures.add(rup);
				}
			} else {
				Rupture probEqkRupture = Rupture.create(mag, rate, rake, surface);
				ruptures.add(probEqkRupture);
			}
		}
		// @formatter:on
		return ruptures;
	}
	
	private static double computeRuptureLength(
			MagScalingRelationship msr,
			double mag,
			double maxWidth,
			double aspectRatio) {
		
		if (msr instanceof MagLengthRelationship) {
			return msr.getMedianScale(mag);
		} else if (msr instanceof MagAreaRelationship) {
			double area = msr.getMedianScale(mag);
			double width = Math.sqrt(area / aspectRatio);
			return area / ((width > maxWidth) ? maxWidth : width);
		} else {
			throw new IllegalArgumentException("Unsupported MagScalingRelation: " +
				msr.getClass());
		}
	}
	
	
	/*
	 * FaultSource builder; build() may only be called once; uses Doubles
	 * to ensure fields are initially null.
	 */
	static class Builder {
		static final Range<Double> ASPECT_RATIO_RANGE = Range.closed(1.0, 2.0);
		static final Range<Double> OFFSET_RANGE = Range.closed(0.1, 20.0);
		
		static final String ID = "FaultSource.Builder";
		boolean built = false;
		
		// required
		String name;
		LocationList trace;
		Double dip;
		Double width;
		Double rake;
		List<IncrementalMFD> mfds = Lists.newArrayList();
		MagScalingRelationship msr;
		
		// have defaults
		double aspectRatio = 1.0;
		double offset = 1.0;
		FloatStyle floatStyle = FULL_DOWN_DIP;
		
		Builder name(String name) {
			checkArgument(!Strings.nullToEmpty(name).trim().isEmpty(),
				"%s name may not be empty or null");
			this.name = name;
			return this;
		}

		Builder trace(LocationList trace) {
			checkArgument(checkNotNull(trace, "Trace may not be null").size() > 1,
				"Trace must have at least 2 points");
			this.trace = trace;
			return this;
		}

		Builder dip(double dip) {
			this.dip = validateDip(dip);
			return this;
		}
		
		Builder width(double width) {
			this.width = validateWidth(width);
			return this; 
		}
		
		Builder rake(double rake) {
			this.rake = validateRake(rake);
			return this;
		}
		
		Builder mfd(IncrementalMFD mfd) {
			checkNotNull(mfd, "MFD is null");
			this.mfds.add(mfd);
			return this;
		}
		
		Builder mfds(List<IncrementalMFD> mfds) {
			checkNotNull(mfds, "MFD list is null");
			checkArgument(mfds.size() > 0, "MFD list is empty");
			this.mfds.addAll(mfds);
			return this;
		}
		
		Builder magScaling(MagScalingRelationship msr) {
			this.msr = checkNotNull(msr, "Mag-Scaling Relation is null");
			return this;
		}
		
		Builder aspectRatio(double aspectRatio) {
			this.aspectRatio = DataUtils.validate(ASPECT_RATIO_RANGE, "Aspect Ratio", aspectRatio);
			return this;
		}

		Builder offset(double offset) {
			this.offset = DataUtils.validate(OFFSET_RANGE, "Floater Offset", offset);
			return this;
		}
		
		Builder floatStyle(FloatStyle floatStyle) {
			this.floatStyle = checkNotNull(floatStyle, "Floater style is null");
			return this;
		}

		void validateState(String mssgID) {
			checkState(!built, "This %s instance as already been used", mssgID);
			checkState(name != null, "%s name not set", mssgID);
			checkState(trace != null, "%s trace not set", mssgID);
			checkState(dip != null, "%s dip not set", mssgID);
			checkState(width != null, "%s width not set", mssgID);
			checkState(rake != null, "%s rake not set", mssgID);
			checkState(mfds.size() > 0, "%s has no MFDs", mssgID);
			checkState(msr != null, "%s mag-scaling relation not set", mssgID);
			built = true;
		}
		
		FaultSource buildFaultSource() {
			validateState(ID);

			// create surface
			double top = trace.first().depth();
			double bottom = top + width * Math.sin(dip * GeoTools.TO_RAD);
			GriddedSurfaceWithSubsets surface = new GriddedSurfaceWithSubsets(trace, dip, top,
				bottom, offset, offset);

			return new FaultSource(name, trace, dip, width, surface, rake,
				ImmutableList.copyOf(mfds), msr, aspectRatio, offset, floatStyle);
		}
	}


}
