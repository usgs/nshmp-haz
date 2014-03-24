package org.opensha.eq.forecast;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.StandardSystemProperty.LINE_SEPARATOR;

import static org.opensha.eq.fault.Faults.*;

import java.util.Iterator;
import java.util.List;

import org.opensha.eq.fault.Faults;
import org.opensha.eq.fault.scaling.MagScalingRelationship;
import org.opensha.eq.fault.scaling.impl.WC1994_MagLengthRelationship;
import org.opensha.eq.fault.scaling.impl.CA_MagAreaRelationship;
import org.opensha.eq.fault.surface.EvenlyGriddedSurface;
import org.opensha.eq.fault.surface.SimpleFaultData;
import org.opensha.eq.fault.surface.StirlingGriddedSurface;
import org.opensha.geo.GeoTools;
import org.opensha.geo.Location;
import org.opensha.geo.LocationList;
import org.opensha.mfd.IncrementalMFD;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

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
 * @version $Id:$
 */
public class FaultSource implements Source {

	private static final MagScalingRelationship WCLmsr; // W&C mag-length
	private static final MagScalingRelationship CAFmsr; // CA floater
	
	// TODO move these out of here
	
	static {
		WCLmsr = new WC1994_MagLengthRelationship();
		CAFmsr = new CA_MagAreaRelationship();
	}
	
	private String name;
	private LocationList trace;
	List<IncrementalMFD> mfds;
	// or rake
	double rake;
	
	SourceType type; // TODO this shouldn't be needed or should be type() in Source interface
	//SourceRegion region; // TODO this MUST go; temporarily in place for
	// compilation of MagScaling selection; NSHMP specific, this source used to
	// be used for all regions but magScaling should be specified in input
	// file xml; this is currently a reverse dependency back to org.usgs packages
	
	boolean floats; //hmmm this is MFD dependent TODO
	double dip;
	double width;
//	double top;

	int size = 0;
	// TODO this should not be using abstract impl
	StirlingGriddedSurface surface;

	List<FloatingPoissonFaultSource> sources;
	List<Integer> rupCount; // cumulative index list for iterating ruptures

	@Override
	public Location centroid() {
		throw new UnsupportedOperationException("");
	}
	

	// no instantiation, must use builder
	private FaultSource() {}
	
	public static class Builder {
		// use Doubles to ensure initial null state
		
		private String name;
		private LocationList trace;
		private Double dip;
//		private Double dipUnc;
//		private Double dipWt;
		private Double width;
		private Double rake;
		private List<IncrementalMFD> mfds = Lists.newArrayList();
		
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
		
//		Builder dipVariants(double[] dips, double[] weights) {
//			checkArgument(dips.length == weights.length, "Dips and weights must be the smae length");
//			checkArgument(dips.length)
//		}
		
		Builder width(double width) {
			// TODO appropriate width validation
			this.width = width;
			return this; 
		}
		
		Builder rake(double rake) {
			this.rake = validateRake(rake);
			return this;
		}
		
		Builder mfds(List<IncrementalMFD> mfds) {
			checkNotNull(mfds, "MFDs may not be null");
			this.mfds.addAll(mfds);
			return this;
		}
		
		FaultSource build() {
			FaultSource fs = new FaultSource();
			
			// state consistency checking
			//		-- all required fields set
			//		-- mech-rake consistency; may do away with mech option 
			//		-- need to figure out way to reconcile dip variations with
			//		   varying a-values: vertical slip rates are used to
			//		   determine event rates on normal faults:
			//				WCL --> M --> MoRt --> /area ==> event rate
			
			checkState(name != null, "Source name not set");
			fs.name = name;
			checkState(trace != null, "Source trace not set");
			fs.trace = trace;
			checkState(dip != null, "Source dip not set");
			fs.dip = dip;
			checkState(width != null, "Source width not set");
			fs.width = width;
			checkState(rake != null, "Source rake not set");
			fs.rake = rake;
			checkState(mfds.size() > 0, "Source has no MFDs");
			fs.mfds = ImmutableList.copyOf(mfds);
			// TODO individual MFDs should be immutable as well
			
			fs.init();
			
			return fs;
		}
		
	}

	/**
	 * Initialize intrnal fault sources.
	 */
	public void init() {
		// init fault surface
		double top = trace.first().depth();
		double lowerSeis = top + width * Math.sin(dip * GeoTools.TO_RAD);
//		LocationList traceIn = FaultTrace.create("Unnammed trace", trace);
		SimpleFaultData sfd = new SimpleFaultData(dip, lowerSeis, top, trace);
		surface = new StirlingGriddedSurface(sfd, 1.0, 1.0);
//		surface = new StirlingGriddedSurface(trace, dip, top, lowerSeis, 1);
		// create a floating poisson source for each mfd
		if (mfds.size() == 0) return; // TODO I don't like this, thow exception?
		// do we really have sources initialized with no MFDs? I don't think you can/should
		// get past builder with such a setup.
		sources = Lists.newArrayList();
		rupCount = Lists.newArrayList();
		rupCount.add(0);
		size = 0;
		
		// TODO need to specify MSR in XML
//		MagScalingRelationship msr = ((type == SourceType.FAULT) &&
//			(region == SourceRegion.CA) && floats) ? CAFmsr : WCLmsr;
		MagScalingRelationship msr = null;
		
		
		FloatingPoissonFaultSource source;
		for (IncrementalMFD mfd : mfds) {
			source = new FloatingPoissonFaultSource(
				mfd, // IncrementalMFD
				surface, // EvenlyGriddedSurface
				msr, // MagScalingRelationship
				0d, // sigma of the mag-scaling relationship
				1d, // floating rupture aspect ratio (length/width)
				1d, // floating rupture offset
				rake, // average rake of the ruptures
				1d, // duration of forecast
				0d, // minimum mag considered
				0, // type of floater (0 = full DDW, 1 = both, 2= centered)
				floats ? 10d : 0d); // mag above which full rup
			sources.add(source);
			int rups = source.getNumRuptures();
			size += rups;
			rupCount.add(size);
		}
	}

	@Override
	public EvenlyGriddedSurface surface() {
		return surface;
	}

	@Override
	public double getMinDistance(Location loc) {
		// TODO this should never happen; why are these checks here?
		if (sources == null || sources.size() == 0) return Double.NaN;
		return sources.get(0).getMinDistance(loc);
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
				return sources.get(i-1).getRupture(idx - rupCount.get(i-1));
			}
		}
		return null; // shouldn't get here
	}
	
	/*
	 * Overriden due to uncertainty on how getRuptureList() is constructed in
	 * parent. Looks clucky and uses cloning which can be error prone if
	 * implemented incorrectly. Was building custom NSHMP calculator
	 * using enhanced for-loops and was losing class information when iterating
	 * over sources and ruptures.
	 * 
	 * TODO is this necessary?
	 */
	@Override
	public List<Rupture> getRuptureList() {
		throw new UnsupportedOperationException(
			"A FaultSource does not allow access to the list "
				+ "of all possible sources.");
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
//		.append(LINE_SEPARATOR.value())
//		.append("         type: ").append(type)
		.append(LINE_SEPARATOR.value())
		.append("         rake: ").append(rake)
//		.append(LINE_SEPARATOR.value())
//		.append("         mags: ").append(nMag)
		.append(LINE_SEPARATOR.value())
		.append("         mfds: ").append(mfds.size())
		.append(LINE_SEPARATOR.value())
		.append("       floats: ").append(floats)
		.append(LINE_SEPARATOR.value())
		.append("          dip: ").append(dip)
		.append(LINE_SEPARATOR.value())
		.append("        width: ").append(width)
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

}
