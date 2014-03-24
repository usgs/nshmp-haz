package org.opensha.eq.forecast;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Map;

import org.opensha.eq.fault.FocalMech;
import org.opensha.geo.Location;
import org.opensha.mfd.IncrementalMFD;

/**
 * Source factory.
 * 
 * @author Peter Powers
 */
public final class Sources {

	private static final String NAME_MSSG = "Sources must have a name";

	/**
	 * Creates a new point earthquake source. The iterator of this
	 * {@code Source} should only ever be used by a single thread and the
	 * {@code Rupture}s it returns should <i>never</i> be retained as their
	 * state <i>will</i> change as iteration proceeds.
	 * 
	 * @param parent reference to parent GridSourceSet
	 * @param loc <code>Location</code> of the point source
	 * @param mfd magnitude frequency distribution of the source
	 * @param mechWtMap <code>Map</code> of focal mechanism weights
	 * @return a new point earthquake {@code Source}
	 */
	public static Source newPointSource(GridSourceSet parent, Location loc,
			IncrementalMFD mfd, Map<FocalMech, Double> mechWtMap) {
		// TODO other argument checking??
		return uncheckedPointSource(checkNotNull(parent), checkNotNull(loc),
			checkNotNull(mfd), checkNotNull(mechWtMap));
	}

	static Source uncheckedPointSource(GridSourceSet parent, Location loc,
			IncrementalMFD mfd, Map<FocalMech, Double> mechWtMap) {
		return new FinitePointSourceOLD(parent, loc, mfd, mechWtMap);
	}

	// public static FaultSource newFaultSource(String name) {
	// return new FaultSource(name)
	// checkArgument(!Strings.nullToEmpty(name).trim().isEmpty(), NAME_MSSG);
	// FaultSource fs = new FaultSource();
	// fs.type = FaultType.typeForID(readInt(fltDat, 0));
	// fs.mech = FocalMech.typeForID(readInt(fltDat, 1));
	// fs.nMag = readInt(fltDat, 2);
	// fs.name = StringUtils.join(fltDat, ' ', nameIdx, fltDat.length);
	// fs.mfds = Lists.newArrayList();
	//
	// }
}
