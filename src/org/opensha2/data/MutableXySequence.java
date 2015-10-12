package org.opensha2.data;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkElementIndex;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.opensha2.data.DataUtils.uncheckedAdd;
import static org.opensha2.data.DataUtils.uncheckedFlip;
import static org.opensha2.data.DataUtils.uncheckedMultiply;

import java.util.Arrays;
import java.util.Iterator;

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

	@Override public Iterator<XyPoint> iterator() {
		return new XyIterator(true);
	}

	@Override public void set(int index, double value) {
		checkElementIndex(index, xs.length);
		ys[index] = value;
	}

	@Override public XySequence add(double term) {
		uncheckedAdd(term, ys);
		return this;
	}

	@Override public XySequence add(XySequence sequence) {
		// safe covariant cast
		uncheckedAdd(ys, validateSequence((ImmutableXySequence) sequence).ys);
		return this;
	}

	@Override public XySequence multiply(double scale) {
		uncheckedMultiply(scale, ys);
		return this;
	}

	@Override public XySequence multiply(XySequence sequence) {
		// safe covariant cast
		uncheckedMultiply(ys, validateSequence((ImmutableXySequence) sequence).ys);
		return this;
	}

	@Override public XySequence complement() {
		uncheckedAdd(1, uncheckedFlip(ys));
		return this;
	}

	@Override public XySequence clear() {
		Arrays.fill(ys, 0.0);
		return this;
	}

	@Override public XySequence transform(Function<Double, Double> function) {
		DataUtils.uncheckedTransform(function, ys);
		return this;
	}

	/*
	 * The common use case is for only the x-value hash codes to be compared as
	 * a result of having used a copyOf(XySequence) constructor.
	 */
	private ImmutableXySequence validateSequence(ImmutableXySequence that) {
		checkArgument(this.xs.hashCode() == checkNotNull(that).xs.hashCode() ||
			Arrays.equals(this.xs, that.xs));
		return that;
	}

}
