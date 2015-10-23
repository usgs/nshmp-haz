package org.opensha2.data;

import static java.lang.Math.exp;
import static java.lang.Math.log;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Utility class to perform linear and log interpolations. The methods of this
 * class are designed to be fast and, as such, perform very little argument
 * checking for monotonicity and the like.
 * 
 * <p>Making some assumptions, interpolation is fairly straightforward. Most of
 * the methods implemented here are designed to support interpolation (or
 * derivation) of y-values keyed to monotonically increasing x-values. x-value
 * interpolation is somewhat thornier. Assumptions and behaviors:</p>
 * 
 * <ul><li>No error checking for null, empty, single-valued arrays; or arrays of
 * different lengths is performed. Buyer beware.</li>
 * 
 * <li>X-value arrays are always assumed to be strictly monotonically ascending
 * (no repeated values)</li>
 * 
 * <li>Internally, binary search is used for y-value interpolation; linear
 * search is used for x-value interpolation.</li>
 * 
 * <li>Y-value interpolation will always extrapolate off the ends a sequence;
 * this may change, or be configurable, in the future.</li>
 * 
 * <li>X-value interpolation is predicated on x-values representing some form of
 * cumulative distribution function, either increasing or decreasing
 * (complementary), and must be specified as such. X-values are assumed to be
 * increasing by default.</li>
 * 
 * <li>X-value interpolation never extrapolates off the ends of a sequence and
 * will always return 0 for out-of-range targets.</li></ul>
 * 
 * <p>Presently, only single value interpolation of x-values is supported. The
 * more common use case is to resample a sequence of y-values, which is
 * supported.</p>
 * 
 * <p>Two static methods, {@link #findX(double, double, double, double, double)}
 * and {@link #findY(double, double, double, double, double)}, are the basis for
 * all interpolation operations in this class. These two methods are point-order
 * agnostic.</p>
 * 
 * TODO example; explain array swapping techniques for x-interpolation
 * 
 * @author Peter Powers
 */
public abstract class Interpolator {

	/*
	 * Developer notes:
	 * 
	 * -------------------------------------------------------------------------
	 * Perhaps add extrapolation constraint (on/off) for y value interpolation
	 * -------------------------------------------------------------------------
	 */

	private Interpolator() {}

	/**
	 * Return an interpolated or extrapolated x-value corresponding to the
	 * supplied y-value. If any supplied value is {@code NaN}, returned value
	 * will also be {@code NaN}. Method does not perform any input validation
	 * such that if the supplied points are coincident or define a horizontal
	 * line, the method may return {@code Infinity}, {@code -Infinity}, or
	 * {@code NaN}.
	 * 
	 * @param x1 x-value of first point
	 * @param y1 y-value of first point
	 * @param x2 x-value of second point
	 * @param y2 y-value of second point
	 * @param y value at which to find x
	 * @return the interpolated x-value
	 */
	public static double findX(double x1, double y1, double x2, double y2, double y) {
		// pass through to findY() with rearranged args
		return findY(y1, x1, y2, x2, y);
	}

	/**
	 * Return an interpolated x-value corresponding to the supplied y-value in
	 * the supplied x- and y-value arrays.
	 * 
	 * @param xs x-values of a sequence
	 * @param ys y-values of a sequence
	 * @param y value at which to find x
	 * @return an interpolated x-value
	 */
	public abstract double findX(double[] xs, double[] ys, double y);

	/**
	 * Return an interpolated x-value corresponding to the supplied y-value in
	 * the supplied x- and y-value arrays.
	 * 
	 * @param xs x-values of a sequence
	 * @param ys y-values of a sequence
	 * @param y value at which to find x
	 * @return an interpolated x-value
	 */
	public abstract double findX(List<Double> xs, List<Double> ys, double y);

	/**
	 * Return an interpolated x-value corresponding to the supplied y-value in
	 * the supplied xy-sequence.
	 * 
	 * @param xys an xy-sequence
	 * @param y value at which to find x
	 * @return an interpolated x-value
	 */
	public abstract double findX(XySequence xys, double y);

	/**
	 * Return an interpolated or extrapolated y-value corresponding to the
	 * supplied x-value. If any supplied value is {@code NaN}, returned value
	 * will also be {@code NaN}. Method does not perform any input validation
	 * such that if the supplied points are coincident or define a vertical
	 * line, the method may return {@code Infinity}, {@code -Infinity}, or
	 * {@code NaN}.
	 * 
	 * @param x1 x-value of first point
	 * @param y1 y-value of first point
	 * @param x2 x-value of second point
	 * @param y2 y-value of second point
	 * @param x value at which to find y
	 * @return an interpolated y-value
	 */
	public static double findY(double x1, double y1, double x2, double y2, double x) {
		return y1 + (x - x1) * (y2 - y1) / (x2 - x1);
	}

	/**
	 * Return an interpolated or extrapolated y-value corresponding to the
	 * supplied x-value in the supplied x- and y-value arrays.
	 * 
	 * @param xs x-values of a sequence
	 * @param ys y-values of a sequence
	 * @param x value at which to find y
	 * @return an interpolated y-value
	 */
	public abstract double findY(double[] xs, double[] ys, double x);

	/**
	 * Return an interpolated or extrapolated y-value corresponding to the
	 * supplied x-value in the supplied x- and y-value arrays.
	 * 
	 * @param xs x-values of a sequence
	 * @param ys y-values of a sequence
	 * @param x value at which to find y
	 * @return an interpolated y-value
	 */
	public abstract double findY(List<Double> xs, List<Double> ys, double x);

	/**
	 * Return an interpolated or extrapolated y-value corresponding to the
	 * supplied x-value in the supplied xy-sequence.
	 * 
	 * @param xys an xy-sequence
	 * @param x value at which to find y
	 * @return an interpolated y-value
	 */
	public abstract double findY(XySequence xys, double x);

	/**
	 * Return interpolated or extrapolated y-values using the supplied x- and
	 * y-value arrays.
	 * 
	 * @param xs x-values of a sequence
	 * @param ys y-values of a sequence
	 * @param x values at which to find y-values
	 * @return interpolated y-values
	 */
	public abstract double[] findY(double[] xs, double[] ys, double[] x);

	/**
	 * Return interpolated or extrapolated y-values using the supplied x- and
	 * y-value arrays.
	 * 
	 * @param xs x-values of a sequence
	 * @param ys y-values of a sequence
	 * @param x values at which to find y-values
	 * @return interpolated y-values
	 */
	public abstract double[] findY(List<Double> xs, List<Double> ys, double[] x);

	/**
	 * Return interpolated or extrapolated y-values using the supplied x- and
	 * y-value arrays.
	 * 
	 * @param xys an xy-sequence
	 * @param x values at which to find y-values
	 * @return interpolated y-values
	 */
	public abstract double[] findY(XySequence xys, double[] x);

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {

		private Builder() {}

		boolean logx = false;
		boolean logy = false;
		boolean xIncreasing = true;

		/**
		 * Indicate that interpolation should be performed in y-value log space.
		 */
		public Builder logx() {
			this.logx = true;
			return this;
		}

		/**
		 * Indicate that interpolation should be performed in y-value log space.
		 */
		public Builder logy() {
			this.logy = true;
			return this;
		}

		/**
		 * Indicate if the x-values to be interpolated are decreasing. In the
		 * absence of calling this method, x-value are assumed to monotonically
		 * increasing. This setting has no effect on y-value interpolation.
		 */
		public Builder decreasingX() {
			this.xIncreasing = false;
			return this;
		}

		/**
		 * Return a newly created {@code Interpolator}.
		 */
		public Interpolator build() {
			return new RegularInterpolator(logx, logy, xIncreasing);
		}

	}

	private static final class RegularInterpolator extends Interpolator {

		private final InterpolateFn yFunction;
		private final InterpolateFn xFunction;
		private final boolean xIncreasing;

		private RegularInterpolator(boolean logx, boolean logy, boolean xIncreasing) {
			if (logx && logy) {
				xFunction = new XFn_LogX_LogY();
				yFunction = new YFn_LogX_LogY();
			} else if (logx) {
				xFunction = new XFn_LogX();
				yFunction = new YFn_LogX();
			} else if (logy) {
				xFunction = new XFn_LogY();
				yFunction = new YFn_LogY();
			} else {
				xFunction = new XFn();
				yFunction = new YFn();
			}
			this.xIncreasing = xIncreasing;
		}

		@Override public double findX(double[] xs, double[] ys, double y) {
			int i = linearIndex(ys, y, xIncreasing);
			if (i == -1) return 0;
			return xFunction.apply(xs[i], ys[i], xs[i + 1], ys[i + 1], y);
		}

		@Override public double findX(List<Double> xs, List<Double> ys, double y) {
			int i = linearIndex(ys, y, xIncreasing);
			if (i == -1) return 0;
			return xFunction.apply(xs.get(i), ys.get(i), xs.get(i + 1), ys.get(i + 1), y);
		}

		@Override public double findX(XySequence xys, double y) {
			// safe covariant cast
			ImmutableXySequence ixys = (ImmutableXySequence) xys;
			return findX(ixys.xs, ixys.ys, y);
		}

		@Override public double findY(double[] xs, double[] ys, double x) {
			int i = binaryIndex(xs, x);
			return yFunction.apply(xs[i], ys[i], xs[i + 1], ys[i + 1], x);
		}

		@Override public double findY(List<Double> xs, List<Double> ys, double x) {
			int i = binaryIndex(xs, x);
			return yFunction.apply(xs.get(i), ys.get(i), xs.get(i + 1), ys.get(i + 1), x);
		}

		@Override public double findY(XySequence xys, double x) {
			// safe covariant cast
			ImmutableXySequence ixys = (ImmutableXySequence) xys;
			return findY(ixys.xs, ixys.ys, x);
		}

		@Override public double[] findY(double[] xs, double[] ys, double[] x) {
			double[] y = new double[x.length];
			for (int i = 0; i < x.length; i++) {
				y[i] = findY(xs, ys, x[i]);
			}
			return y;
		}

		@Override public double[] findY(List<Double> xs, List<Double> ys, double[] x) {
			double[] y = new double[x.length];
			for (int i = 0; i < x.length; i++) {
				y[i] = findY(xs, ys, x[i]);
			}
			return y;
		}

		@Override public double[] findY(XySequence xys, double[] x) {
			// safe covariant cast
			ImmutableXySequence ixys = (ImmutableXySequence) xys;
			return findY(ixys.xs, ixys.ys, x);
		}
	}

	/* Interface for different interpolation functions */
	private static interface InterpolateFn {
		double apply(double x1, double x2, double y1, double y2, double value);
	}

	private static final class XFn implements InterpolateFn {
		@Override public double apply(double x1, double x2, double y1, double y2, double y) {
			return findX(x1, y1, x2, y2, y);
		}
	}

	private static final class XFn_LogX implements InterpolateFn {
		@Override public double apply(double x1, double x2, double y1, double y2, double y) {
			return exp(findY(log(x1), y1, log(x2), y2, y));
		}
	}

	private static final class XFn_LogY implements InterpolateFn {
		@Override public double apply(double x1, double x2, double y1, double y2, double y) {
			return findY(x1, log(y1), x2, log(y2), log(y));
		}
	}

	private static final class XFn_LogX_LogY implements InterpolateFn {
		@Override public double apply(double x1, double x2, double y1, double y2, double y) {
			return exp(findY(log(x1), log(y1), log(x2), log(y2), log(y)));
		}
	}

	private static final class YFn implements InterpolateFn {
		@Override public double apply(double x1, double x2, double y1, double y2, double x) {
			return findY(x1, y1, x2, y2, x);
		}
	}

	private static final class YFn_LogX implements InterpolateFn {
		@Override public double apply(double x1, double x2, double y1, double y2, double x) {
			return findY(log(x1), y1, log(x2), y2, log(x));
		}
	}

	private static final class YFn_LogY implements InterpolateFn {
		@Override public double apply(double x1, double x2, double y1, double y2, double x) {
			return exp(findY(x1, log(y1), x2, log(y2), x));
		}
	}

	private static final class YFn_LogX_LogY implements InterpolateFn {
		@Override public double apply(double x1, double x2, double y1, double y2, double x) {
			return exp(findY(log(x1), log(y1), log(x2), log(y2), log(x)));
		}
	}

	/*
	 * Used for x-value interpolation.
	 * 
	 * Constrained linear search. Returns -1 if target is out of range.
	 */
	private static int linearIndex(double[] sequence, double target, boolean increasing) {
		for (int i = 0; i < sequence.length - 1; i++) {
			double v1 = sequence[increasing ? i : i + 1];
			double v2 = sequence[increasing ? i + 1 : i];
			if (target >= v1 && target <= v2) return i;
		}
		return -1;
	}

	/* Same as above for a list. */
	private static int linearIndex(List<Double> sequence, double target, boolean increasing) {
		for (int i = 0; i < sequence.size() - 1; i++) {
			double v1 = sequence.get(increasing ? i : i + 1);
			double v2 = sequence.get(increasing ? i + 1 : i);
			if (target >= v1 && target <= v2) return i;
		}
		return -1;
	}

	/*
	 * Used for y-value interpolation.
	 * 
	 * Returns the lower index of the 'segment' bounding the target. If target
	 * is outside the range of the sequence, the lower index of the uppermost or
	 * lowermost segment is returned, whichever would need to be extrapolated.
	 */
	private static int binaryIndex(double[] sequence, double target) {
		int i = Arrays.binarySearch(sequence, target);
		return binarySearchResultToIndex(i, sequence.length);
	}

	/* Same as above for a list. */
	private static int binaryIndex(List<Double> sequence, double target) {
		int i = Collections.binarySearch(sequence, target);
		return binarySearchResultToIndex(i, sequence.size());
	}

	private static int binarySearchResultToIndex(int i, int size) {
		// adjust index for low value (-1) and in-sequence insertion pt
		i = (i == -1) ? 0 : (i < 0) ? -i - 2 : i;
		// adjust hi index to next to last index
		return (i >= size - 1) ? --i : i;
	}

}
