package org.opensha.eq.forecast;

import static com.google.common.base.Preconditions.checkArgument;
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
import org.opensha.eq.fault.surface.RuptureSurface;
import org.opensha.eq.fault.surface.SimpleFaultData;
import org.opensha.eq.fault.surface.StirlingGriddedSurface;
import org.opensha.geo.GeoTools;
import org.opensha.geo.Location;
import org.opensha.geo.LocationList;
import org.opensha.mfd.IncrementalMFD;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Range;

/**
 * This class is used to represent all fault sources in the 2008 NSHMP. Each
 * {@code FaultSource} wraps one or more {@code FloatingPoissonFaultSource}s.
 * There is a 1 to 1 mapping of mfds to wrapped sources.
 * 
 * <p>A fault source can not be created directly; it may only be created by
 * a private parser.</p>
 * 
 * We always want to use rake as it may be specified by a fault; different GMMs
 * use different cutoffs to define fault style.
 * 
 * @author Peter Powers
 */
public class FaultSource implements Source {
	
	private final String name;
	private final LocationList trace;
	private final double dip;
	private final double width;
	private final double rake;
	private final List<IncrementalMFD> mfds;
	private final MagScalingRelationship msr;
	private final double aspectRatio; // for floating ruptures
	private final double offset;      // of floating ruptures
	private final FloatStyle floatStyle;
		

	private int size = 0;
	private StirlingGriddedSurface surface;
	private List<List<Rupture>> ruptureLists; // 1:1 with MFDs
	private List<Integer> rupCount; // cumulative index list for iterating ruptures
	
	private FaultSource(String name, LocationList trace, double dip, double width, double rake,
		List<IncrementalMFD> mfds, MagScalingRelationship msr, double aspectRatio, double offset,
		FloatStyle floatStyle) {
		this.name = name;
		this.trace = trace;
		this.dip = dip;
		this.width = width;
		this.rake = rake;
		this.mfds = mfds;
		this.msr = msr;
		this.aspectRatio = aspectRatio;
		this.offset = offset;
		this.floatStyle = floatStyle;
		init();
	}
	
	void init() {
		double top = trace.first().depth();
		double lowerSeis = top + width * Math.sin(dip * GeoTools.TO_RAD);
		SimpleFaultData sfd = new SimpleFaultData(dip, lowerSeis, top, trace);
		surface = new StirlingGriddedSurface(sfd, 1.0, 1.0);
		ruptureLists = Lists.newArrayList();
		rupCount = Lists.newArrayList();
		rupCount.add(0);
		
		for (IncrementalMFD mfd : mfds) {
			List<Rupture> rupList = createRuptureList(mfd);
			ruptureLists.add(rupList);
			size += rupList.size();
			rupCount.add(size);
		}
	}

	@Override
	public double getMinDistance(Location loc) {
		throw new UnsupportedOperationException();
	}

	@Override
	public int size() {
		return size;
	}

	// for now, ruptures are nested in sources which we iterate over
	@Override
	public Rupture getRupture(int idx) {
		// TODO should never have a source with no ruptures; somewhere earlier
		// an IAE should have been thrown
		if (size() == 0) return null;
		// zero is built in to rupCount array; unless a negative idx is
		// supplied, if statement below should never be entered on first i
		for (int i = 0; i < rupCount.size(); i++) {
			if (idx < rupCount.get(i)) {
				return ruptureLists.get(i-1).get(idx - rupCount.get(i-1));
			}
		}
		return null; // shouldn't get here
	}
	
	@Override
	public List<Rupture> getRuptureList() {
		throw new UnsupportedOperationException(
			"A FaultSource does not allow access to the list of all possible sources.");
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
	public String name() {
		return name;
	}
	
	@Override
	public String toString() {
		// @formatter:off
		return new StringBuilder()
		.append("==========  Fault Source  ==========")
		.append(LINE_SEPARATOR.value())
		.append("   Fault name: ").append(name)
		.append(LINE_SEPARATOR.value())
		.append("         rake: ").append(rake)
		.append(LINE_SEPARATOR.value())
		.append("         mfds: ").append(mfds.size())
		.append(LINE_SEPARATOR.value())
		.append("          dip: ").append(dip)
		.append(LINE_SEPARATOR.value())
		.append("        width: ").append(width)
		.append(LINE_SEPARATOR.value())
		.append("          top: ").append(trace.first().depth())
		.append(LINE_SEPARATOR.value()).toString();
		// @formatter:on
	}

	/*
	 * FaultSource builder; build() may only be called once; uses Doubles
	 * to ensure fields are initially null.
	 */
	static class Builder {
		private static final Range<Double> ASPECT_RATIO_RANGE = Range.closed(1.0, 2.0);
		private static final Range<Double> OFFSET_RANGE = Range.closed(0.1, 20.0);
		
		private boolean built = false;
		
		// required
		private String name;
		private LocationList trace;
		private Double dip;
		private Double width;
		private Double rake;
		private List<IncrementalMFD> mfds = Lists.newArrayList();
		private MagScalingRelationship msr;
		
		// have defaults
		private double aspectRatio = 1.0;
		private double offset = 1.0;
		private FloatStyle floatStyle = FULL_DOWN_DIP;
		
		Builder name(String name) {
			checkArgument(!Strings.nullToEmpty(name).trim().isEmpty(),
				"'name' attribute may not be empty or null");
			this.name = name;
			return this;
		}

		Builder trace(LocationList trace) {
			checkArgument(
				checkNotNull(trace, "Trace may not be null").size() > 1,
				"LocationList for a fault trace must have at least 2 points");
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

		FaultSource build() {
			checkState(!built, "This FaultSource.Builder instance as already been used");
			
			checkState(name != null, "Source name not set");
			checkState(trace != null, "Source trace not set");
			checkState(dip != null, "Source dip not set");
			checkState(width != null, "Source width not set");
			checkState(rake != null, "Source rake not set");
			checkState(mfds.size() > 0, "Source has no MFDs");
			checkState(msr != null, "Mag-Scaling Relation not set");
			
			built = true;
			return new FaultSource(name, trace, dip, width, rake, ImmutableList.copyOf(mfds), msr,
				aspectRatio, offset, floatStyle);
		}
		
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

				// rupture count
				double numRup = (floatStyle != CENTERED) ?
					surface.getNumSubsetSurfaces(length, width, offset) :
					surface.getNumSubsetSurfacesAlongLength(length, offset);

				for (int r = 0; r < numRup; r++) {
					RuptureSurface floatingSurface = (floatStyle != CENTERED) ?
						surface.getNthSubsetSurface(length, width, offset, r) :
						surface.getNthSubsetSurfaceCenteredDownDip(length, width, offset, r);
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

}
