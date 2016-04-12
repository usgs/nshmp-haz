package org.opensha2.eq.fault.surface;

import static java.lang.Math.sin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.opensha2.data.Data;
import org.opensha2.data.Interpolate;
import org.opensha2.eq.fault.surface.RuptureScaling.Dimensions;
import org.opensha2.eq.model.Rupture;
import org.opensha2.geo.Location;
import org.opensha2.geo.LocationList;

import com.google.common.collect.Range;

/**
 * Rupture floating models for gridded surfaces. Each provides the means to
 * create an immutable {@code List} of {@link Rupture}s from a
 * {@link GriddedSurface}, magnitude, rate, rake, and uncertainty flag.
 * 
 * <p>NOTE: Only {@code ON} currently recognizes and applies rupture area
 * uncertainty.</p>
 * 
 * @author Peter Powers
 */
public enum RuptureFloating {

	/** Do not float. */
	OFF {
		@Override public List<Rupture> createFloatingRuptures(DefaultGriddedSurface surface,
				RuptureScaling scaling, double mag, double rate, double rake, boolean uncertainty) {
			List<Rupture> floaters = new ArrayList<>();
			floaters.add(Rupture.create(mag, rate, rake, surface));
			return floaters;
		}
	},

	/** Float both down-dip and along-strike. */
	ON {
		@Override public List<Rupture> createFloatingRuptures(DefaultGriddedSurface surface,
				RuptureScaling scaling, double mag, double rate, double rake, boolean uncertainty) {

			double maxWidth = surface.width();

			if (uncertainty) {
				List<Rupture> floaters = new ArrayList<>();
				Map<Dimensions, Double> dimensionsMap = scaling.dimensionsDistribution(mag,
					maxWidth);
				for (Entry<Dimensions, Double> entry : dimensionsMap.entrySet()) {
					Dimensions d = entry.getKey();
					List<GriddedSurface> surfaces = createFloatingSurfaces(surface, d.length,
						d.width);
					double scaledRate = rate * entry.getValue();
					floaters.addAll(createFloaters(surfaces, mag, scaledRate, rake));
				}
				return floaters;
			}
			Dimensions d = scaling.dimensions(mag, maxWidth);
			List<GriddedSurface> surfaces = createFloatingSurfaces(surface, d.length, d.width);
			return createFloaters(surfaces, mag, rate, rake);
		}
	},

	/**
	 * Float along-strike only; floaters extend to full down-dip-width. This
	 * model currently ignores any rupture area {@code sigma}.
	 */
	STRIKE_ONLY {
		@Override public List<Rupture> createFloatingRuptures(DefaultGriddedSurface surface,
				RuptureScaling scaling, double mag, double rate, double rake, boolean uncertainty) {
			double maxWidth = surface.width();
			Dimensions d = scaling.dimensions(mag, maxWidth);
			List<GriddedSurface> surfaces = createFloatingSurfaces(surface, d.length, maxWidth);
			return createFloaters(surfaces, mag, rate, rake);
		}
	},

	/**
	 * NSHM floating model. This is an approximation to the NSHM fortran
	 * analytical floating rupture model where specific magnitude dependent
	 * rupture top depths are used. This model currently ignores any rupture
	 * area {@code sigma}.
	 */
	NSHM {
		@Override public List<Rupture> createFloatingRuptures(DefaultGriddedSurface surface,
				RuptureScaling scaling, double mag, double rate, double rake, boolean uncertainty) {
			List<GriddedSurface> surfaces = floatListNshm(surface, scaling, mag);
			return createFloaters(surfaces, mag, rate, rake);
		}
	},

	/**
	 * Triangular distribution of hypocenters. This model is motivated by the
	 * PEER PSHA validationtest cases. It is implemented to explicitely weight
	 * down-dip floating ruptures more hevily down to 10km, and then decrease
	 * their weight with depth below.
	 * 
	 * TODO add reference/link to PEER documentation and test cases in repo
	 */
	TRIANGULAR {
		@Override public List<Rupture> createFloatingRuptures(DefaultGriddedSurface surface,
				RuptureScaling scaling, double mag, double rate, double rake, boolean uncertainty) {

			double maxWidth = surface.width();
			Dimensions d = scaling.dimensions(mag, maxWidth);
			Map<GriddedSurface, Double> surfaces = createWeightedFloatingSurfaces(surface,
				d.length, d.width);
			return createFloaters(surfaces, mag, rate, rake);
		}
	};
	
	/* 
	 * TODO note that the triangular PEER test case 2-4b has 1 more rupture than
	 * the regular floater; why is this? (this was observed by setting surface
	 * spacing to 1km and outputting Transforms.sourceToInput
	 */

	private static List<Rupture> createFloaters(List<GriddedSurface> floatingSurfaces, double mag,
			double rate, double rake) {
		List<Rupture> floaters = new ArrayList<>();
		double scaledRate = rate / floatingSurfaces.size();
		for (GriddedSurface floatingSurface : floatingSurfaces) {
			floaters.add(Rupture.create(mag, scaledRate, rake, floatingSurface));
		}
		return floaters;
	}

	private static List<Rupture> createFloaters(Map<GriddedSurface, Double> surfaceMap, double mag,
			double rate, double rake) {
		List<Rupture> floaters = new ArrayList<>();
		for (Entry<GriddedSurface, Double> entry : surfaceMap.entrySet()) {
			floaters.add(Rupture.create(mag, entry.getValue() * rate, rake, entry.getKey()));
		}
		return floaters;
	}

	// TODO why is this taking DefaultGriddedSurface instead of GriddedSurface
	/**
	 * Create a {@code List} of floating ruptures
	 * @param surface (gridded) from which floaters are derived
	 * @param scaling the rupture scaling model used to determine floater
	 *        dimensions
	 * @param mag the magnitude of interest
	 */
	public abstract List<Rupture> createFloatingRuptures(DefaultGriddedSurface surface,
			RuptureScaling scaling, double mag, double rate, double rake, boolean uncertainty);

	private static List<GriddedSurface> floatListNshm(DefaultGriddedSurface parent,
			RuptureScaling scaling, double mag) {

		// zTop > 1, no down-dip variants
		// M>7 [zTop]
		// M>6.75 [zTop, +2]
		// M>6.5 [zTop, +2, +4]
		// else [zTop, +2, +4, +6]

		double zTop = parent.depth();
		int downDipCount = (zTop > 1.0 || mag > 7.0) ? 1 :
			(mag > 6.75) ? 2 :
				(mag > 6.5) ? 3 : 4;
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

	/* Create a List of floating surfaces. */
	private static List<GriddedSurface> createFloatingSurfaces(AbstractGriddedSurface parent,
			double floatLength, double floatWidth) {

		List<GriddedSurface> floaterList = new ArrayList<>();

		// along-strike size & count
		int floaterColSize = (int) Math.rint(floatLength / parent.strikeSpacing + 1);
		int alongCount = parent.getNumCols() - floaterColSize + 1;
		if (alongCount <= 1) {
			alongCount = 1;
			floaterColSize = parent.getNumCols();
		}

		// down-dip size & count
		int floaterRowSize = (int) Math.rint(floatWidth / parent.dipSpacing + 1);
		int downCount = parent.getNumRows() - floaterRowSize + 1;
		if (downCount <= 1) {
			downCount = 1;
			floaterRowSize = parent.getNumRows();
		}

		for (int startCol = 0; startCol < alongCount; startCol++) {
			for (int startRow = 0; startRow < downCount; startRow++) {
				GriddedSubsetSurface gss = new GriddedSubsetSurface(floaterRowSize, floaterColSize,
					startRow, startCol, parent);
				floaterList.add(gss);
			}
		}

		return floaterList;
	}

	/*
	 * Create a Map of floating surfaces and associated weights derived from a
	 * "triangular" down dip distribution of hypocenters. This model is
	 * motivated by the PEER test cases and apparantly is in use in stable
	 * continental regions. The model used in the test case is for a planar,
	 * vertical, 30 km wide fault. The distribution (pdf) of hypocenters
	 * increases linearly from 0.0 at 0km depth to 0.0667 km⁻¹ (or 1/15 km⁻¹) at
	 * 10km depth. It then decreases linearly back to 0.0 at 30km depth. This
	 * model generalizes the above to generate a pdf of weights that peaks at a
	 * depth of 1/3 the parent surface width with weight such that the integral
	 * over the distribution is 1.
	 * 
	 * Generally this should only be used with wide faults in stable continental
	 * crust.
	 */
	private static Map<GriddedSurface, Double> createWeightedFloatingSurfaces(
			AbstractGriddedSurface parent, double floatLength, double floatWidth) {

		Map<GriddedSurface, Double> floaterMap = new HashMap<>();

		// along-strike size & count
		int floaterColSize = (int) Math.rint(floatLength / parent.strikeSpacing + 1);
		int alongCount = parent.getNumCols() - floaterColSize + 1;
		if (alongCount <= 1) {
			alongCount = 1;
			floaterColSize = parent.getNumCols();
		}

		// down-dip size & count
		int floaterRowSize = (int) Math.rint(floatWidth / parent.dipSpacing + 1);
		int downCount = parent.getNumRows() - floaterRowSize + 1;
		if (downCount <= 1) {
			downCount = 1;
			floaterRowSize = parent.getNumRows();
		}

		// generate depth weight array
		double[] hypoDepths = new double[downCount];
		double halfDepth = floaterRowSize * parent.dipSpacing * sin(parent.dipRad()) / 2.0;
		for (int startRow = 0; startRow < downCount; startRow++) {
			hypoDepths[startRow] = parent.get(startRow, 0).depth() + halfDepth;
		}
		double zTop = parent.depth();
		double zBot = zTop + parent.width() * sin(parent.dipRad());
		double[] depthWeights = generateTriangularWeights(zTop, zBot, hypoDepths);

		// scale weights to consider along-strike uniform weights
		double horizScale = 1.0 / alongCount;
		Data.multiply(horizScale, depthWeights);

		for (int startCol = 0; startCol < alongCount; startCol++) {
			for (int startRow = 0; startRow < downCount; startRow++) {
				GriddedSubsetSurface gss = new GriddedSubsetSurface(floaterRowSize, floaterColSize,
					startRow, startCol, parent);
				floaterMap.put(gss, depthWeights[startRow]);
			}
		}
		System.out.println(Data.sum(floaterMap.values()));
		System.out.println(floaterMap.size());

		return floaterMap;
	}

	/*
	 * Create a list of normalized weights for a triangular distribution that
	 * peaks at 0.0667
	 */
	private static double[] generateTriangularWeights(double zTop, double zBot, double[] depths) {
		Range<Double> depthRange = Range.closed(zTop, zBot);
		Data.checkInRange(depthRange, "Depth", depths);

		// create PDF
		double xPeak = (zBot - zTop) / 3.0;
		double yPeak = 2.0 / (3.0 * xPeak);
		double[] xs = { zTop, xPeak, zBot };
		double[] ys = { 0.0, yPeak, 0.0 };

		// interpolate and normalize
		double[] weights = Interpolate.findY(xs, ys, depths);
		Data.normalize(weights);

		return weights;
	}

	// TODO clean
	public static void main(String[] args) {
		// double zTop = 0.0;
		// double zBot = 30.0;
		// double[] depths = { 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26,
		// 28 };
		// double[] weights = generateTriangularWeights(zTop, zBot, depths);
		// System.out.println(Arrays.toString(weights));
		// System.out.println(DataUtils.sum(weights));

		DefaultGriddedSurface surf = DefaultGriddedSurface.builder()
			.trace(LocationList.create(
				Location.create(33.0, -118.0),
				Location.create(33.5, -118.0)))
			.depth(0.0)
			.width(30.0)
			.dip(90.0)
			.build();

		Dimensions d = RuptureScaling.PEER.dimensions(7.0, 30.0);
		System.out.println(d);

		Map<GriddedSurface, Double> map = createWeightedFloatingSurfaces(surf, d.length, d.width);
		System.out.println(Data.sum(map.values()));
		System.out.println(map.size());

	}

}
