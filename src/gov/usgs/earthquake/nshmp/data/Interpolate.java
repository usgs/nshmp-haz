package gov.usgs.earthquake.nshmp.data;

import static java.lang.Math.exp;
import static java.lang.Math.log;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Utility class to perform linear interpolations. Methods are also provided to
 * perform interpolations in log space. The methods of this class are designed
 * to be fast and, as such perform almost no argument checking.
 *
 * <p>NOTE: This class is designed to be used primarily with the OpenSHA
 * function classes and should probably be relocated to that package and given
 * default visibility due to the lack of error-checking. We could write public
 * methods that do thorough error checking.
 *
 * <p><strong>Warning:</strong> These methods do no error checking for
 * {@code null} , empty, or single valued arrays; arrays of different lengths;
 * nor does it check that the supplied x-values are monotonically increasing
 * (sorted). Internally the method uses binary search and it is up to the user
 * to supply valid data.
 *
 * @author Peter Powers
 */
@Deprecated
public final class Interpolate {

  // TODO refactor all methods to take extrapolate flag

  // TODO support Lists??

  // TODO high priority

  // TODO unchecked versions?

  // x-values always assumed to be monotonically increasing
  // y-values must be reversed (for hazard curves)

  private Interpolate() {}

  /**
   * Returns the interpolated or extrapolated x-value corresponding to the
   * supplied y-value. If any supplied value is {@code NaN}, returned value will
   * also be {@code NaN}. Method does not do any input validation such that if
   * the supplied points are coincident or define a horizontal line, the method
   * may return {@code Infinity}, {@code -Infinity}, or {@code NaN} .
   * @param x1 x-value of first point
   * @param y1 y-value of first point
   * @param x2 x-value of second point
   * @param y2 y-value of second point
   * @param y value at which to find x
   * @return the interpolated x-value
   */
  public static double findX(double x1, double y1, double x2, double y2,
      double y) {
    /*
     * pass through to findY with rearranged args, instead of:
     *
     * findX() = x1 + (y - y1) * (x2 - x1) / (y2 - y1)
     */
    return findY(y1, x1, y2, x2, y);
  }

  /**
   * Returns the interpolated or extrapolated y-value corresponding to the
   * supplied x-value. If any supplied value is {@code NaN}, returned value will
   * also be {@code NaN}. Method does not do any input validation such that if
   * the supplied points are coincident or define a vertical line, the method
   * may return {@code Infinity}, {@code -Infinity}, or {@code NaN}.
   *
   * @param x1 x-value of first point
   * @param y1 y-value of first point
   * @param x2 x-value of second point
   * @param y2 y-value of second point
   * @param x value at which to find y
   * @return the interpolated y-value
   */
  public static double findY(double x1, double y1, double x2, double y2,
      double x) {
    return y1 + (x - x1) * (y2 - y1) / (x2 - x1);
  }

  // /**
  // * Returns the interpolated or extrapolated y-value using the supplied x-
  // * and y-value arrays.
  // *
  // * @param xs x-values of some function
  // * @param ys y-values of some function
  // * @param x value at which to find y
  // * @return the interpolated y-value
  // */
  // public static double findX(double[] xs, double[] ys, double y) {
  // int i = dataIndex(xs, x);
  // return findX(xs[i], ys[i], xs[i + 1], ys[i + 1], x);
  // }

  /**
   * Returns the interpolated or extrapolated y-value using the supplied x- and
   * y-value arrays.
   *
   * @param xs x-values of some function
   * @param ys y-values of some function
   * @param x value at which to find y
   * @return the interpolated y-value
   */
  public static double findY(double[] xs, double[] ys, double x) {
    int i = dataIndex(xs, x);
    return findY(xs[i], ys[i], xs[i + 1], ys[i + 1], x);
  }

  public static double findY(List<Double> xs, List<Double> ys, double x) {
    int i = dataIndex(xs, x);
    return findY(xs.get(i), ys.get(i), xs.get(i + 1), ys.get(i + 1), x);
  }

  // /**
  // * Returns the log interpolated or extrapolated y-value using the
  // * supplied x- and y-value arrays.
  // *
  // * TODO needs unit test
  // *
  // * @param xs x-values of some function
  // * @param ys y-values of some function
  // * @param x value at which to find y
  // * @return the interpolated y-value
  // */
  // public static double findLogX(double[] xs, double[] ys, double y) {
  // int i = dataIndex(xs, x);
  // return exp(findY(xs[i], log(ys[i]), xs[i + 1], log(ys[i + 1]), x));
  // }

  /**
   * Returns the log interpolated or extrapolated y-value using the supplied x-
   * and y-value arrays.
   *
   * TODO needs unit test
   *
   * @param xs x-values of some function
   * @param ys y-values of some function
   * @param x value at which to find y
   * @return the interpolated y-value
   */
  public static double findLogY(double[] xs, double[] ys, double x) {
    int i = dataIndex(xs, x);
    return exp(findY(xs[i], log(ys[i]), xs[i + 1], log(ys[i + 1]), x));
  }

  /**
   * Returns the log-log interpolated or extrapolated y-value using the supplied
   * x- and y-value arrays.
   *
   * @param xs x-values of some function
   * @param ys y-values of some function
   * @param x value at which to find y
   * @return the log-log interpolated y-value
   */
  public static double findLogLogY(double[] xs, double[] ys, double x) {
    int i = dataIndex(xs, x);
    return exp(findY(log(xs[i]), log(ys[i]), log(xs[i + 1]), log(ys[i + 1]), log(x)));
  }

  /**
   * Returns interpolated or extrapolated y-values using the supplied x- and
   * y-value arrays.
   *
   * @param xs x-values of some function
   * @param ys y-values of some function
   * @param x value at which to find y
   * @return the interpolated y-values
   */
  public static double[] findY(double[] xs, double[] ys, double[] x) {
    double[] y = new double[x.length];
    int i = 0;
    for (double xVal : x) {
      y[i++] = findY(xs, ys, xVal);
    }
    return y;
  }

  public static double[] findY(List<Double> xs, List<Double> ys, double[] x) {
    double[] y = new double[x.length];
    int i = 0;
    for (double xVal : x) {
      y[i++] = findY(xs, ys, xVal);
    }
    return y;
  }

  /**
   * Returns the log interpolated or extrapolated y-values using the supplied x-
   * and y-value arrays.
   *
   * @param xs x-values of some function
   * @param ys y-values of some function
   * @param x value at which to find y
   * @return the log interpolated y-values
   */
  public static double[] findLogY(double[] xs, double[] ys, double[] x) {
    double[] y = new double[x.length];
    int i = 0;
    for (double xVal : x) {
      y[i++] = findLogY(xs, ys, xVal);
    }
    return y;
  }

  /**
   * Returns the log-log interpolated or extrapolated y-values using the
   * supplied x- and y-value arrays.
   *
   * @param xs x-values of some function
   * @param ys y-values of some function
   * @param x value at which to find y
   * @return the log-log interpolated y-values
   */
  public static double[] findLogLogY(double[] xs, double[] ys, double[] x) {
    double[] y = new double[x.length];
    int i = 0;
    for (double xVal : x) {
      y[i++] = findLogLogY(xs, ys, xVal);
    }
    return y;
  }

  // TODO clean

  // TODO its highly likely that given the average (small) size of things
  // like hazard curves, simply walking up an array or list is faster than
  // binary searching.

  // TODO move to DataUtils as some 'unchecked' flavor

  private static int dataIndex(double[] data, double value) {
    int i = Arrays.binarySearch(data, value);
    return binarySearchResultToIndex(i, data.length);
    // // adjust index for low value (-1) and in-sequence insertion pt
    // i = (i == -1) ? 0 : (i < 0) ? -i - 2 : i;
    // // adjust hi index to next to last index
    // return (i >= data.length - 1) ? --i : i;
  }

  private static int dataIndex(List<Double> data, double value) {
    int i = Collections.binarySearch(data, value);
    return binarySearchResultToIndex(i, data.size());
    // // adjust index for low value (-1) and in-sequence insertion pt
    // i = (i == -1) ? 0 : (i < 0) ? -i - 2 : i;
    // // adjust hi index to next to last index
    // return (i >= data.size() - 1) ? --i : i;
  }

  private static int binarySearchResultToIndex(int i, int size) {
    // adjust index for low value (-1) and in-sequence insertion pt
    i = (i == -1) ? 0 : (i < 0) ? -i - 2 : i;
    // adjust hi index to next to last index
    return (i >= size - 1) ? --i : i;
  }

}
