package org.opensha2.data;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkElementIndex;

import java.util.Arrays;

/**
 * The default, immutable, array-backed implementation of {@code XySequence}.
 *
 * @author Peter Powers
 */
class ImmutableXySequence extends XySequence {

  final double[] xs;
  final double[] ys;

  ImmutableXySequence(double[] xs, double[] ys) {
    this.xs = xs;
    this.ys = ys;
  }

  ImmutableXySequence(XySequence sequence, boolean clear) {
    /*
     * This constructor provides the option to 'clear' (or zero-out) the
     * y-values when copying, however, in practice, it is only ever used when
     * creating mutable covariants.
     *
     * The covariant cast below is safe as all implementations descend from this
     * class.
     */
    ImmutableXySequence s = (ImmutableXySequence) sequence;
    xs = s.xs;
    ys = clear ? new double[xs.length] : Arrays.copyOf(s.ys, s.ys.length);
  }

  @Override
  public final double x(int index) {
    checkElementIndex(index, xs.length);
    return xUnchecked(index);
  }

  @Override
  final double xUnchecked(int index) {
    return xs[index];
  }

  @Override
  public final double y(int index) {
    checkElementIndex(index, ys.length);
    return yUnchecked(index);
  }

  @Override
  final double yUnchecked(int index) {
    return ys[index];
  }

  @Override
  public final int size() {
    return xs.length;
  }

  @Override
  public final boolean isClear() {
    return Data.areZeroValued(ys);
  }

  @Override
  public final boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof ImmutableXySequence)) {
      return false;
    }
    ImmutableXySequence that = (ImmutableXySequence) obj;
    return Arrays.equals(this.xs, that.xs) && Arrays.equals(this.ys, that.ys);
  }

  @Override
  public final int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + Arrays.hashCode(xs);
    result = prime * result + Arrays.hashCode(ys);
    return result;
  }

  /*
   * Check the x-value object references; if mismatched, compare values.
   */
  ImmutableXySequence validateSequence(ImmutableXySequence that) {
    checkArgument(this.xs.hashCode() == that.xs.hashCode() ||
        Arrays.equals(this.xs, that.xs));
    return that;
  }

}
