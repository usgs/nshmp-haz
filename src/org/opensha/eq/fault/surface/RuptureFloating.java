package org.opensha.eq.fault.surface;

import static java.lang.Math.*;
import static org.opensha.eq.model.FloatStyle.CENTERED;
import static org.opensha.eq.model.FloatStyle.FULL_DOWN_DIP;
import static org.opensha.eq.fault.surface.Surfaces.*;

import java.util.ArrayList;
import java.util.List;

import org.opensha.eq.fault.surface.RuptureScaling.Dimensions;
import org.opensha.eq.model.Rupture;
import org.opensha.mfd.IncrementalMfd;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

/**
 * Gridded surface floating model identifiers. Each provides the means to create
 * a List of {@link GriddedSubsetSurface}s from a RuptureScaling
 * {@link DefaultGriddedSurface} to an immutable list of {@link Rupture}s.
 *
 * @author Peter Powers
 */
public enum RuptureFloating {

	/** Do not float. */
	OFF {
		@Override public List<GriddedSurface> createFloaters(DefaultGriddedSurface surface,
				RuptureScaling scaling, double mag) {
			List<GriddedSurface> floaters = new ArrayList<>();
			floaters.add(surface);
			return floaters;
		}
	},

	/** Float both down-dip and along-strike. */
	ON {
		@Override public List<GriddedSurface> createFloaters(DefaultGriddedSurface surface,
				RuptureScaling scaling, double mag) {
			return createSurfaces(surface, scaling, mag, false);
		}
	},

	/** Float along-strike only; floaters extend to full down-dip-width. */
	STRIKE_ONLY {
		@Override public List<GriddedSurface> createFloaters(DefaultGriddedSurface surface,
				RuptureScaling scaling, double mag) {
			return createSurfaces(surface, scaling, mag, true);
		}
	},

	/**
	 * NSHM floating model. This is an approximation to the NSHM fortran analytical
	 * floating rupture model where specific magnitude dependent rupture top depths
	 * are used.
	 */
	NSHM {
		@Override public List<GriddedSurface> createFloaters(DefaultGriddedSurface surface,
				RuptureScaling scaling, double mag) {
			return floatListNshm(surface, scaling, mag);
		}
	};

	// TODO this should return RuptureSurface, perhaps; do we need grid-specific
	// info after this point?
	public abstract List<GriddedSurface> createFloaters(DefaultGriddedSurface surface,
			RuptureScaling scaling, double mag);

	private static List<GriddedSurface> createSurfaces(DefaultGriddedSurface surface,
			RuptureScaling scaling, double mag, boolean fullDownDip) {

		// rupture dimensions
		double maxWidth = surface.width();
		Dimensions d = scaling.dimensions(mag, maxWidth);

		double width = fullDownDip ? maxWidth : d.width;
		return createFloatingSurfaceList(surface, d.length, width);

	}

	private static List<GriddedSurface> floatListNshm(DefaultGriddedSurface parent,
			RuptureScaling scaling, double mag) {

		// zTop > 1, no down-dip variants
		// M>7 [zTop]
		// M>6.75 [zTop, +2]
		// M>6.5 [zTop, +2, +4]
		// else [zTop, +2, +4, +6]

		double zTop = parent.depth();
		// @formatter:off
		int downDipCount = 
				(zTop > 1.0) ? 1 :
				(mag > 7.0) ? 1 :
				(mag > 6.75) ? 2 :
				(mag > 6.5) ? 3 : 4;
		// @formatter:on
		List<Double> zTopWidths = new ArrayList<>();

		for (int i = 0; i < downDipCount; i++) {
			double zWidthDelta = 2.0 / sin(parent.dipRad());
			zTopWidths.add(0.0 + i * zWidthDelta);
		}

		List<GriddedSurface> floaterList = new ArrayList<>();

		// compute row start index and rowCount for each depth
		for (double zTopWidth : zTopWidths) {

			Dimensions d = scaling.dimensions(mag, parent.width() - zTopWidth);

			// row start and
			int startRow = (int) Math.rint(zTopWidth / parent.dipSpacing);
			int floaterRowSize = (int) Math.rint(d.width / parent.dipSpacing + 1);

			// along-strike size & count
			int floaterColSize = (int) Math.rint(d.length / parent.strikeSpacing + 1);
			int alongCount = parent.getNumCols() - floaterColSize + 1;
			if (alongCount <= 1) {
				alongCount = 1;
				floaterColSize = parent.getNumCols();
			}

			for (int startCol = 0; startCol < alongCount; startCol++) {
				GriddedSubsetSurface gss = new GriddedSubsetSurface(floaterRowSize, floaterColSize,
					startRow, startCol, parent);
				floaterList.add(gss);
			}
		}
		return floaterList;
	}

}
