package org.opensha2.eq.fault.surface;

import static com.google.common.io.Resources.getResource;
import static com.google.common.io.Resources.readLines;
import static java.lang.Math.floor;
import static java.lang.Math.log10;
import static java.lang.Math.min;
import static java.lang.Math.pow;
import static java.lang.Math.round;
import static java.lang.Math.sqrt;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.opensha2.eq.fault.scaling.MagScalingRelationship;
import org.opensha2.mfd.IncrementalMfd;
import org.opensha2.mfd.Mfds;
import org.opensha2.util.Logging;
import org.opensha2.util.Parsing;

/**
 * Identifiers for different rupture dimension scaling models. Most are rooted
 * in specific {@link MagScalingRelationship}s, with this class providing more
 * direct access to how such relations are used in practice. All incorporate a
 * fixed rupture aspect ratio. As magnitudes increase, some models will preserve
 * area at the expense of aspect ratio; others may preserve length at the
 * expense of area.
 * 
 * <p>Some scaling models also internally provide corrected Joyner-Boore
 * distances that can be used to approximate average distances from a site to a
 * point source of unknown strike.</p>
 * 
 * <p>Models may also provide a range of {@link Dimensions} for a given
 * magnitude if {@link #dimensionsDistribution(double, double)} is requested.
 * This method considers any uncertainty associated with a model and returns a
 * ±2σ distribution of {@code Dimensions} discretized at 11 points.</p>
 * 
 * @author Peter Powers
 */
public enum RuptureScaling {

	/**
	 * Scaling used for most NSHM finite faults. Returns a magnitude-dependent
	 * length (Wells & Coppersmith, 1994) and {@code min(length, maxWidth)},
	 * thereby maintaining a minimum aspect ratio of 1.0. In practice,
	 * {@code maxWidth} is also a function of magnitude but is prescribed by a
	 * RuptureFloating model.
	 * 
	 * <p>The {@code pointSourceDistance(double, double)} implementation returns
	 * corrected distances for magnitudes in the closed range [6.0..8.6] and
	 * distances in the closed range [0..1000]</p>
	 * 
	 * @see RuptureFloating#NSHM
	 */
	NSHM_FAULT_WC94_LENGTH {
		@Override public Dimensions dimensions(double mag, double maxWidth) {
			double length = lengthWc94(mag);
			return new Dimensions(length, min(maxWidth, length));
		}

		@Override public Map<Dimensions, Double> dimensionsDistribution(double mag, double maxWidth) {
			throw new UnsupportedOperationException();
		}

		@Override public double pointSourceDistance(double mag, double distance) {
			throw new UnsupportedOperationException();
		}
	},

	/**
	 * Scaling used for NSHM finite faults in California in 2008 (UCERF2). This
	 * relation Returns a magnitude-dependent length and
	 * {@code min(length, maxWidth)}, thereby maintaining a minimum aspect ratio
	 * of 1.0. It is a hybrid relation that uses Wells & Coppersmith (1994)
	 * below M≈6.9 and Ellsworth-B (WGCEP, 2002) above.
	 */
	NSHM_FAULT_CA_ELLB_WC94_AREA {
		@Override public Dimensions dimensions(double mag, double maxWidth) {
			double length = lengthCa08(mag, maxWidth);
			return new Dimensions(length, min(maxWidth, length));
		}

		@Override public Map<Dimensions, Double> dimensionsDistribution(double mag, double maxWidth) {
			throw new UnsupportedOperationException();
		}

		@Override public double pointSourceDistance(double mag, double distance) {
			throw new UnsupportedOperationException();
		}
	},

	/**
	 * Scaling used for NSHM point sources. Maintains aspect ratio of 1.5 up to
	 * maximum width and then maintains length (Wells & Coppersmith, 1994)
	 * at the expense of aspect ratio.
	 */
	NSHM_POINT_WC94_LENGTH {
		/* Steve Harmsen likened 1.5 to the Golden Ratio, 1.618... */
		@Override public Dimensions dimensions(double mag, double maxWidth) {
			double length = lengthWc94(mag);
			return new Dimensions(length, min(maxWidth, length / 1.5));
		}

		@Override public Map<Dimensions, Double> dimensionsDistribution(double mag, double maxWidth) {
			throw new UnsupportedOperationException();
		}

		@Override public double pointSourceDistance(double mag, double distance) {
			return correctedRjb(mag, distance, RJB_DAT_WC94LENGTH);
		}
	},

	/**
	 * Scaling used for NSHM subduction sources. Returns a magnitude-dependent
	 * length (Geomatrix Consultants, 1995) and {@code maxWidth}. This relation
	 * is used when floating ruptures along strike and {@code maxWidth} will
	 * always be specified as the full down-dip width of the rupture.
	 */
	NSHM_SUB_GEOMAT_LENGTH {

		@Override public Dimensions dimensions(double mag, double maxWidth) {
			return new Dimensions(pow(10.0, (mag - 4.94) / 1.39), maxWidth);
		}

		@Override public Map<Dimensions, Double> dimensionsDistribution(double mag, double maxWidth) {
			throw new UnsupportedOperationException();
		}

		@Override public double pointSourceDistance(double mag, double distance) {
			return correctedRjb(mag, distance, RJB_DAT_GEOMATRIX);
		}
	},

	// @formatter:off
	/**
	 * Peer PSHA test scaling. Maintains aspect ratio of 2.0 up to maximum
	 * width, then increases length. Conservation of area at the expense
	 * of aspect ratio. The uncertainty in area for this model is 0.25.
	 * 
	 * <ul>
	 * 	<li>Log (A) = M – 4</li>
	 * 	<li>Log (W) = 0.5 * M - 2.15</li>
	 * 	<li>Log (L) = 0.5 * M - 1.85</li>
	 * </ul>
	 */
	PEER {
		private final IncrementalMfd normal2s = Mfds.newGaussianMFD(0.0, 0.25, 11, 1.0);

		@Override public Dimensions dimensions(double mag, double maxWidth) {
			
			double width = pow(10, (0.5 * mag - 2.15));
			return (width < maxWidth) ? 
				new Dimensions(width * 2.0, width) :
				new Dimensions(pow(10, (mag - 4.0)) / maxWidth, maxWidth);
		}

		@Override public Map<Dimensions, Double> dimensionsDistribution(double mag, double maxWidth) {
			double area = pow(10, (mag - 4.0));
			Map<Dimensions, Double> dimensionsMap = new LinkedHashMap<>();
			for (int i=0; i<normal2s.getNum(); i++) {
				double scaledArea = area * pow(10, normal2s.getX(i));
				dimensionsMap.put(dimCalc(scaledArea, maxWidth), normal2s.getY(i));
			}
			return dimensionsMap;
		}

		@Override public double pointSourceDistance(double mag, double distance) {
			throw new UnsupportedOperationException();
		}
		
		private Dimensions dimCalc(double area, double maxWidth) {
			double width = sqrt(area / 2.0);
			return (width < maxWidth) ? 
				new Dimensions(width * 2.0, width) :
				new Dimensions(area / maxWidth, maxWidth);
		}
	},

	/**
	 * Scaling used for 2014 CEUS derived from Somerville et al. (2001). In the
	 * 2014 NSHM, this relation is only used for point source distance
	 * corrections. The {@code dimensions()} implementation follows that of the
	 * CEUS-SSC, maintaining an aspect ratio of 1 until the maximum width is
	 * attained and then increasing length as necessary.
	 */
	NSHM_SOMERVILLE {
		@Override public Dimensions dimensions(double mag, double maxWidth) {
			double area = pow(10, mag - 4.366);
			double width = sqrt(area);
			return (width < maxWidth) ?
				new Dimensions(width, width) :
				new Dimensions(area / maxWidth, maxWidth);
		}

		@Override public Map<Dimensions, Double> dimensionsDistribution(double mag, double maxWidth) {
			throw new UnsupportedOperationException();
		}

		@Override public double pointSourceDistance(double mag, double distance) {
			return correctedRjb(mag, distance, RJB_DAT_SOMERVILLE);
		}
	},
	// @formatter:on

	/**
	 * Placeholder for no rupture scaling model. This may be used when rupture
	 * geometry is fully specified but an identifier is required, or to imply no
	 * point source distance corrections should be applied. This {code
	 * #pointSourceDistance()} implementation simply returns the distance
	 * supplied.
	 */
	NONE {
		@Override public Dimensions dimensions(double mag, double maxWidth) {
			throw new UnsupportedOperationException();
		}

		@Override public Map<Dimensions, Double> dimensionsDistribution(double mag, double maxWidth) {
			throw new UnsupportedOperationException();
		}

		@Override public double pointSourceDistance(double mag, double distance) {
			return distance;
		}
	};

	/**
	 * Given a magnitude and distance from a site to a point source, return the
	 * average distance for a finite fault of unkown strike.
	 * 
	 * @param mag of a rupture
	 * @param distance to the centroid of a point source
	 */
	public abstract double pointSourceDistance(double mag, double distance);

	private static final String MAG_ID = "#Mag";
	private static final String COMMENT_ID = "#";
	private static final int RJB_M_SIZE = 26;
	private static final int RJB_R_SIZE = 1001;
	private static final double[][] RJB_DAT_WC94LENGTH = readRjb("etc/rjb_wc94length.dat");
	private static final double[][] RJB_DAT_GEOMATRIX = readRjb("etc/rjb_geomatrix.dat");
	private static final double[][] RJB_DAT_SOMERVILLE = readRjb("etc/rjb_somerville.dat");

	/* package visibility for testing */
	static double[][] readRjb(String resource) {
		double[][] rjbs = new double[RJB_M_SIZE][RJB_R_SIZE];
		URL url = getResource(RuptureScaling.class, resource);
		List<String> lines = null;
		try {
			lines = readLines(url, UTF_8);
		} catch (IOException ioe) {
			Logging.handleResourceError(RuptureScaling.class, ioe);
		}
		int magIndex = -1;
		int rIndex = 0;
		for (String line : lines) {
			if (line.trim().isEmpty()) continue;
			if (line.startsWith(MAG_ID)) {
				magIndex++;
				rIndex = 0;
				continue;
			}
			if (line.startsWith(COMMENT_ID)) continue;
			rjbs[magIndex][rIndex++] = Parsing.readDouble(line, 1);
		}
		return rjbs;
	}

	/*
	 * The rjb lookup tables span the magnitude range [6.05..8.55] and distance
	 * range [0..1000] km. For M<6 and distances > 1000, lookups return the
	 * supplied distance. For M>8.6, lookups return the corrected distance for
	 * M=8.55. NOTE that no NaN or ±INFINITY checking is done in this class.
	 * This would have to be added for a public api, but we are operating on the
	 * assumption that data from mfds and upstream distance calulations and
	 * dimensioning will have already been checked for odd values.
	 */

	private static double correctedRjb(double m, double r, double[][] rjb) {
		if (m < 6.0) return r;
		int mIndex = min((int) round((m - 6.05) / 0.1), 25);
		int rIndex = (int) floor(r);
		return (rIndex <= 1000) ? rjb[mIndex][rIndex] : r;
	}

	/**
	 * Return the dimensions of a magnitude-dependent and width-constrained
	 * rupture.
	 * 
	 * @param mag scaling basis magnitude
	 * @param maxWidth of parent source
	 */
	public abstract Dimensions dimensions(double mag, double maxWidth);

	/**
	 * Return a ±2σ distribution of {@code Dimensions} and associated
	 * weights. The distribution is discretized at 11 points.
	 * 
	 * @param mag scaling basis magnitude
	 * @param maxWidth of parent source
	 */
	public abstract Map<Dimensions, Double> dimensionsDistribution(double mag, double maxWidth);

	private static double lengthWc94(double mag) {
		return pow(10.0, -3.22 + 0.69 * mag);
	}

	private static final double MAG_CUT = log10(500.0) + 4.2; // ≈6.9

	private static double lengthCa08(double mag, double width) {
		// loM : Wells & Coppersmith '94 mag-area relation
		// hiM : EllsworthB (WGCEP, 2002) mag-area relation
		// note that this inverts the WC94 M(area) relation
		// instead of using the direct area(M) relation
		double area = (mag >= MAG_CUT) ? pow(10.0, mag - 4.2) : pow(10.0, (mag - 4.07) / 0.98);
		return area / width;
	}

	@SuppressWarnings("javadoc")
	public final static class Dimensions {

		public final double length;
		public final double width;

		private Dimensions(double length, double width) {
			this.length = length;
			this.width = width;
		}

		@Override public String toString() {
			return String.format("RuptureScaling.Dimensions [%.3f (l) x %.3f (w)]", length, width);
		}
	}
}
