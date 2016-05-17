package org.opensha2.data;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkElementIndex;
import static org.opensha2.data.Data.flip;
import static org.opensha2.data.Data.uncheckedMultiply;

import java.util.Arrays;
import java.util.Iterator;

import org.opensha2.data.XySequence.Point;

import com.google.common.base.Function;

/**
 * Mutable variant of {@code XySequence}.
 *
 * @author Peter Powers
 */
final class MutableXySequence extends ImmutableXySequence {

  MutableXySequence(double[] xs, double[] ys) {
    super(xs, ys);
  }

  MutableXySequence(XySequence sequence, boolean clear) {
    super(sequence, clear);
  }

  @Override
  public Iterator<XyPoint> iterator() {
    return new XyIterator(true);
  }

  @Override
  public XyPoint min() {
    return new Point(0, true);
  }

  @Override
  public XyPoint max() {
    return new Point(size() - 1, true);
  }

  @Override
  public void set(int index, double value) {
    checkElementIndex(index, xs.length);
    ys[index] = value;
  }

  @Override
  public XySequence add(double term) {
    Data.add(term, ys);
    return this;
  }

  @Override
  public XySequence add(double[] ys) {
    Data.add(this.ys, ys);
    return this;
  }

  @Override
  public XySequence add(XySequence sequence) {
    // safe covariant cast
    Data.add(ys, validateSequence((ImmutableXySequence) sequence).ys);
    return this;
  }

  @Override
  public XySequence multiply(double scale) {
    Data.multiply(scale, ys);
    return this;
  }

  @Override
  public XySequence multiply(XySequence sequence) {
    // safe covariant cast
    uncheckedMultiply(ys, validateSequence((ImmutableXySequence) sequence).ys);
    return this;
  }

  @Override
  public XySequence complement() {
    Data.add(1, flip(ys));
    return this;
  }

  @Override
  public XySequence clear() {
    Arrays.fill(ys, 0.0);
    return this;
  }

  @Override
  public XySequence transform(Function<Double, Double> function) {
    Data.uncheckedTransform(function, ys);
    return this;
  }

}
